package aicollab;

// ============================================================
// Main.java — AI Collaboration Platform v0.3
// ============================================================
//
// WHAT THIS FILE DOES:
// 1. Asks you to type a question
// 2. Sends it to Claude, GPT, and Gemini independently
// 3. Each model reacts to the other two models' responses
// 4. Claude synthesizes everything into a structured report
//
// This is the complete debate cycle in one file.
// No frameworks, no libraries, no magic.
//
// HOW TO RUN:
//   1. javac src/aicollab/Main.java
//   2. java -cp src aicollab.Main
//   3. On first run, the program will ask for your API keys
//      and save them locally (they won't be asked again).
//
// PREREQUISITES:
//   - Java 21 installed
//   - API keys for Anthropic, OpenAI, and Google Gemini
//     (the program will tell you where to get them)
// ============================================================


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import java.util.Scanner;


public class Main {

    // ============================================================
    // API CONFIGURATION
    //
    // Each AI provider has three things:
    //   1. A URL (where their API lives on the internet)
    //   2. An API key (your personal password)
    //   3. A model name (which specific AI to use)
    //
    // URLs and model names are NOT secrets — they're the same for
    // everyone and are part of the code's logic, so they stay here.
    //
    // Keys ARE secrets — they're loaded from config.properties,
    // which is gitignored so it never enters version control.
    // On first run, the program prompts you for your keys and
    // saves them automatically. See loadOrCreateConfig() below.
    // ============================================================

    // --- ANTHROPIC (Claude) ---
    private static final String CLAUDE_URL = "https://api.anthropic.com/v1/messages";
    private static final String CLAUDE_KEY;  // loaded from config.properties
    private static final String CLAUDE_MODEL = "claude-sonnet-4-20250514";

    // --- OPENAI (GPT) ---
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String OPENAI_KEY;  // loaded from config.properties
    private static final String OPENAI_MODEL = "gpt-4o";

    // --- GOOGLE (Gemini) ---
    // NOTE: Gemini is different — the API key goes in the URL, not in a header.
    // The URL gets built dynamically in the callGemini() method.
    private static final String GEMINI_KEY;  // loaded from config.properties
    // "gemini-2.0-flash" was retired for new users in 2026.
    // "gemini-2.5-flash" is the current stable text model.
    private static final String GEMINI_MODEL = "gemini-2.5-flash";

    // ============================================================
    // CONFIGURATION LOADING
    //
    // WHY NOT JUST HARDCODE THE KEYS?
    // API keys are secrets — like passwords. If they end up in Git,
    // anyone who clones the repo gets free access to our paid APIs.
    // By loading them from a file that Git ignores, we keep secrets
    // out of version control forever.
    //
    // HOW IT WORKS:
    // On first run, the program notices config.properties doesn't
    // exist, prompts you for each key, and saves them to that file.
    // On every run after that, it loads the keys silently.
    //
    // Java's Properties class reads key=value pairs from a text
    // file — it's built into Java, no external libraries needed.
    // ============================================================
    private static final String CONFIG_FILE = "config.properties";

    static {
        Properties props = loadOrCreateConfig(CONFIG_FILE);
        CLAUDE_KEY = props.getProperty("claude.api.key");
        OPENAI_KEY = props.getProperty("openai.api.key");
        GEMINI_KEY = props.getProperty("gemini.api.key");
    }

    // ============================================================
    // loadOrCreateConfig() — Loads API keys from file, or prompts
    // the user to enter them on first run.
    //
    // WHY THIS EXISTS:
    // New teammates shouldn't have to create config files by hand.
    // They just run the program, paste their keys when asked, and
    // everything is saved automatically. On future runs, the keys
    // load silently from the saved file.
    //
    // HOW Java Properties WORKS:
    // Properties is a built-in Java class (java.util.Properties).
    // It reads lines like "key=value" from a text file and stores
    // them in a map. You retrieve values with getProperty("key").
    // You save with store(). No external libraries needed.
    // ============================================================
    private static Properties loadOrCreateConfig(String filename) {
        Properties props = new Properties();
        File configFile = new File(filename);

        if (configFile.exists()) {
            // Config file found — load keys from it silently.
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            } catch (IOException e) {
                System.err.println("ERROR: Could not read " + filename);
                System.err.println("Try deleting " + filename + " and running again.");
                System.exit(1);
            }

            // Make sure all three keys are actually present and not placeholders.
            String[] required = {"claude.api.key", "openai.api.key", "gemini.api.key"};
            for (String key : required) {
                String value = props.getProperty(key);
                if (value == null || value.isBlank() || value.startsWith("YOUR_")) {
                    System.err.println("ERROR: Missing or placeholder value for '" + key + "' in " + filename);
                    System.err.println("Delete " + filename + " and run again to re-enter your keys.");
                    System.exit(1);
                }
            }

        } else {
            // No config file — this is a first run. Walk the user through setup.
            System.out.println();
            System.out.println("╔══════════════════════════════════════╗");
            System.out.println("║   First-Time API Key Setup           ║");
            System.out.println("╚══════════════════════════════════════╝");
            System.out.println();
            System.out.println("No API keys found. Let's set them up!");
            System.out.println("You'll only need to do this once — keys are saved locally.");
            System.out.println();
            System.out.println("Where to get keys:");
            System.out.println("  Anthropic (Claude): https://console.anthropic.com/settings/keys");
            System.out.println("  OpenAI (GPT):       https://platform.openai.com/api-keys");
            System.out.println("  Google (Gemini):    https://aistudio.google.com/apikey");
            System.out.println();

            Scanner setupScanner = new Scanner(System.in);

            String claudeKey = promptForKey(setupScanner, "Anthropic (Claude)");
            String openaiKey = promptForKey(setupScanner, "OpenAI (GPT)");
            String geminiKey = promptForKey(setupScanner, "Google (Gemini)");

            props.setProperty("claude.api.key", claudeKey);
            props.setProperty("openai.api.key", openaiKey);
            props.setProperty("gemini.api.key", geminiKey);

            // Save the keys so the user is never asked again.
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                props.store(fos, "API keys — this file is gitignored, NEVER commit it.");
            } catch (IOException e) {
                System.err.println("ERROR: Could not save " + filename);
                System.err.println(e.getMessage());
                System.exit(1);
            }

            System.out.println();
            System.out.println("Keys saved to " + filename + " — you won't be asked again.");
            System.out.println("(To change keys later, just delete " + filename + " and re-run.)");
            System.out.println();
        }

        return props;
    }

    // ============================================================
    // promptForKey() — Asks the user to paste one API key.
    //
    // Keeps asking until they enter something non-blank.
    // This prevents saving an empty key that would cause a
    // confusing 401 error from the API later.
    // ============================================================
    private static String promptForKey(Scanner scanner, String providerName) {
        while (true) {
            System.out.print("Enter your " + providerName + " API key: ");
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println("  (Key cannot be blank — please try again.)");
        }
    }

    // Reusable HttpClient — created once, used for ALL API calls.
    // Creating a new one each time wastes resources.
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    // ============================================================
    // AGENT PROFILES — The AI models' identities on the advisory panel.
    //
    // WHY THIS MATTERS:
    // Without an assigned identity, each AI responds as a generic
    // assistant. By giving each model a specific ROLE, PRIORITIES,
    // and PERSPECTIVE, we get genuinely different viewpoints instead
    // of three slightly different versions of the same answer.
    //
    // Think of it like assembling a real advisory board:
    //   - You wouldn't put three generalists in a room.
    //   - You'd pick a strategist, a risk analyst, and an innovator.
    //
    // THE ONION MODEL (layered context):
    //   Outer layer:  WHO IS ASKING  (stakeholder profile)
    //   Middle layer: TEAM CONTEXT   (what this advisory panel does)
    //   Inner layer:  THIS AGENT     (specific model's role + priorities)
    //
    // Each API call gets all three layers prepended to the prompt.
    // The model reads them as context before answering.
    // ============================================================
 
 // --- TEAM CONTEXT (shared by all agents) ---
    // This is the "middle layer" of the onion — it tells every model
    // what kind of team they're part of and what the project is.
    private static final String TEAM_CONTEXT =
            "=== COLLABORATION CONTEXT ===\n"
          + "You are one of three AI collaborators helping a team of four university students\n"
          + "build an AI collaboration platform as their final project (5-week deadline).\n"
          + "The panel consists of Claude (Anthropic), GPT (OpenAI), and Gemini (Google).\n"
          + "You are EQUAL PARTNERS — no one agent leads or outranks the others.\n"
          + "Your job is to think together, challenge each other constructively,\n"
          + "and help the students build the best possible solution.\n"
          + "You will first respond independently, then react to the other agents' positions,\n"
          + "and finally a synthesis report will be produced.\n"
          + "Stay true to your assigned perspective below, but remain collaborative.\n\n";
 
    // --- INDIVIDUAL AGENT PROFILES ---
    // Each model gets a different perspective. These are designed so
    // the three viewpoints naturally complement and tension each other.
    // Roles are starting points — the agents may propose reorganizing
    // responsibilities as the project evolves.
 
    private static final String CLAUDE_AGENT_PROFILE =
            "=== YOUR AGENT IDENTITY ===\n"
          + "Agent name: Claude\n"
          + "Perspective: Architecture & Quality\n"
          + "Your lens on every problem:\n"
          + "  1. Is the design clean, maintainable, and well-structured?\n"
          + "  2. What are the tradeoffs and second-order consequences?\n"
          + "  3. Where are the risks — what could break, confuse, or scale poorly?\n"
          + "  4. Is this teachable? Can every team member explain what it does?\n"
          + "Style: You value clarity over cleverness, simplicity over features.\n"
          + "You push back when something is over-engineered for the scope.\n"
          + "When you disagree, you explain WHY with specific reasoning.\n\n";
 
    private static final String GPT_AGENT_PROFILE =
            "=== YOUR AGENT IDENTITY ===\n"
          + "Agent name: GPT\n"
          + "Perspective: Ideas & Possibilities\n"
          + "Your lens on every problem:\n"
          + "  1. What creative approaches haven't been considered yet?\n"
          + "  2. How could this feature delight users or impress a professor?\n"
          + "  3. What patterns from other projects or industries apply here?\n"
          + "  4. Where can we be more ambitious without blowing the deadline?\n"
          + "Style: You look for what's possible, not just what's safe.\n"
          + "You bring energy and fresh thinking to the team.\n"
          + "When you disagree, you propose ALTERNATIVES, not just objections.\n\n";
 
    private static final String GEMINI_AGENT_PROFILE =
            "=== YOUR AGENT IDENTITY ===\n"
          + "Agent name: Gemini\n"
          + "Perspective: Execution & Delivery\n"
          + "Your lens on every problem:\n"
          + "  1. Can we actually build this in the time we have?\n"
          + "  2. What are the concrete steps, dependencies, and blockers?\n"
          + "  3. Who on the team should own this, and what do they need to learn?\n"
          + "  4. What's the minimum viable version we can ship and iterate on?\n"
          + "Style: You turn ideas into task lists and catch scope creep early.\n"
          + "You keep the team grounded in what's achievable.\n"
          + "When you disagree, you show the TIMELINE REALITY behind your position.\n\n";
 
 
    // ============================================================
    // STUDENT PROFILES — The humans who prompt the panel.
    //
    // These represent the four team members building this project.
    // Before each prompt, the user selects who they are (the "hotseat").
    // This tells the AI panel who's asking and what that person's
    // current focus and responsibilities are.
    //
    // WHY THIS MATTERS:
    // Grant asking "how should we structure the GUI?" gets different
    // advice than Lisiana asking the same question — because Grant
    // is leading the project and needs architectural guidance, while
    // Lisiana might need more hands-on implementation guidance
    // depending on her comfort level with the codebase.
    //
    // FORMAT: Each profile is a String array with 7 elements:
    //   [0] Name
    //   [1] Role on team
    //   [2] Focus area
    //   [3] Works with
    //   [4] Current responsibilities
    //   [5] What they're evaluated on (for the final project)
    //   [6] Background / notes
    //
    // UPDATE THESE as the project evolves and people take on tasks.
    // ============================================================
 
    private static final String[][] STAKEHOLDER_PROFILES = {
        {
            "Grant",
            "Project Lead & Prompter-in-Chief",
            "Architecture, AI integration, prompt engineering",
            "Xavier, Lisiana, Arizbeth",
            "Owns overall design and orchestration logic. Drives the prompt strategy. Primary liaison with AI collaborators. Sets priorities and unblocks teammates.",
            "Working platform, clean architecture, team understanding of codebase, final presentation quality",
            "Started the project and built v0.1-v0.3 solo. Strongest current understanding of the codebase. Rusty on Java but learning fast through building. Thinks strategically about the product vision."
        },
        {
            "Xavier",
            "Team Member (role TBD)",
            "To be assigned",
            "Grant, Lisiana, Arizbeth",
            "Currently onboarding to the project. Needs to clone the repo, run Main.java locally, and read through the code with comments. Will be assigned a component after Week 1 orientation.",
            "Code contributions, understanding of their assigned component, ability to explain their work",
            "Currently not active on the project. Joining from break. Skill level and interests to be assessed during onboarding."
        },
        {
            "Lisiana",
            "Team Member (role TBD)",
            "To be assigned",
            "Grant, Xavier, Arizbeth",
            "Currently onboarding to the project. Needs to clone the repo, run Main.java locally, and read through the code with comments. Will be assigned a component after Week 1 orientation.",
            "Code contributions, understanding of their assigned component, ability to explain their work",
            "Currently not active on the project. Joining from break. Skill level and interests to be assessed during onboarding."
        },
        {
            "Arizbeth",
            "Team Member (role TBD)",
            "To be assigned",
            "Grant, Xavier, Lisiana",
            "Currently onboarding to the project. Needs to clone the repo, run Main.java locally, and read through the code with comments. Will be assigned a component after Week 1 orientation.",
            "Code contributions, understanding of their assigned component, ability to explain their work",
            "Currently not active on the project. Joining from break. Skill level and interests to be assessed during onboarding."
        }
    };
 
    // This variable tracks which stakeholder is currently in the "hotseat."
    // It's an index into the STAKEHOLDER_PROFILES array above.
    // 'static' because it's shared across all methods in this class.
    private static int activeStakeholder = 0;

 
    // ============================================================
    // main() — Entry point. Runs the conversation loop.
    // ============================================================
 
    public static void main(String[] args) {
 
        Scanner scanner = new Scanner(System.in);
 
        // Track how many debate cycles we've run this session.
        // Each cycle = 7 API calls (3 initial + 3 reactions + 1 synthesis).
        // This counter helps the user stay aware of usage.
        int cycleCount = 0;
 
        System.out.println("========================================");
        System.out.println("  AI Collaboration Platform v0.3");
        System.out.println("  Full Debate Cycle: 3 Models + Synthesis");
        System.out.println("  Each cycle makes 7 API calls.");
        System.out.println("  Type 'quit' to exit.");
        System.out.println("========================================");
 
        // ============================================================
        // STAKEHOLDER SELECTION (the "hotseat")
        //
        // Before any prompts are sent, the user picks which stakeholder
        // they're acting as. This profile gets sent to every AI model
        // so the panel knows WHO they're advising.
        //
        // The user can switch profiles between debate cycles by typing
        // 'switch' instead of a prompt.
        // ============================================================
 
        selectStakeholder(scanner);  // show menu, let user pick
 
        while (true) {
 
            // Show who's in the hotseat and get their prompt
            System.out.println();
            String name = STAKEHOLDER_PROFILES[activeStakeholder][0];
            String title = STAKEHOLDER_PROFILES[activeStakeholder][1];
            System.out.println("[" + name + " — " + title + "]");
            System.out.println("(Commands: 'switch', 'quit')");
            System.out.println("Enter your prompt (type SEND on its own line to submit):");
 
            // ============================================================
            // MULTI-LINE INPUT COLLECTION
            //
            // WHY NOT JUST scanner.nextLine()?
            // Terminal input reads one line at a time. If the user pastes
            // a multi-paragraph prompt, each line fires separately. With
            // the old design, blank lines between paragraphs would trigger
            // the confirmation step, and the next line of text would be
            // read as the y/n answer — causing the infinite cancel loop.
            //
            // THE FIX: We collect lines into a StringBuilder until the
            // user types "SEND" on its own line. This means:
            //   - Pasting multi-line text works perfectly
            //   - Blank lines within the prompt are preserved
            //   - Only an explicit "SEND" triggers the debate cycle
            //
            // LEARNING POINT: This is a common pattern for multi-line
            // terminal input. Chat apps, email composers, and database
            // CLIs all use a "terminator" line (like "." or ";" or "/")
            // to signal "I'm done typing."
            // ============================================================
 
            StringBuilder promptBuilder = new StringBuilder();
            while (true) {
                String line = scanner.nextLine();
 
                // Check for commands ONLY on the first line (before any
                // content has been entered). This prevents "quit" or
                // "switch" inside a paragraph from triggering a command.
                if (promptBuilder.isEmpty()) {
                    if (line.trim().equalsIgnoreCase("quit")) {
                        // Reuse the line variable to signal quit to outer loop
                        promptBuilder.append("__QUIT__");
                        break;
                    }
                    if (line.trim().equalsIgnoreCase("switch")) {
                        promptBuilder.append("__SWITCH__");
                        break;
                    }
                }
 
                // "SEND" on its own line = done collecting input
                if (line.trim().equalsIgnoreCase("SEND")) {
                    break;
                }
 
                // Add this line to the prompt (with a newline between lines)
                if (promptBuilder.length() > 0) {
                    promptBuilder.append("\n");
                }
                promptBuilder.append(line);
            }
 
            String userPrompt = promptBuilder.toString().trim();
 
            // Handle the special command signals
            if (userPrompt.equals("__QUIT__")) {
                System.out.println("Goodbye! Ran " + cycleCount + " debate cycle(s) this session.");
                break;
            }
 
            if (userPrompt.equals("__SWITCH__")) {
                selectStakeholder(scanner);
                continue;
            }
 
            if (userPrompt.isEmpty()) {
                System.out.println("(Empty prompt — type something, then SEND)");
                continue;
            }
 
            // ============================================================
            // COST SAFEGUARD: Confirm before running
            //
            // Now that the prompt is fully collected, we show a preview
            // and ask for confirmation. No more blank-line interference.
            // ============================================================
 
            // Show a preview of the prompt (first 100 chars) so the user
            // can verify they're sending what they intended.
            String preview = userPrompt.length() > 100
                    ? userPrompt.substring(0, 100) + "..."
                    : userPrompt;
            System.out.println();
            System.out.println("Stakeholder: " + name + " (" + title + ")");
            System.out.println("Prompt preview: " + preview);
            System.out.println("This will run a full debate cycle (7 API calls).");
            System.out.print("Proceed? (y/n): ");
            String confirm = scanner.nextLine().trim().toLowerCase();
 
            if (!confirm.equals("y") && !confirm.equals("yes")) {
                System.out.println("Cancelled. Enter a new prompt or 'quit' to exit.");
                continue;
            }
 
            // Run the debate
            runDebateCycle(userPrompt);
            cycleCount++;
 
            // ============================================================
            // POST-CYCLE PAUSE
            //
            // After a cycle completes, we show a clear separator and
            // the running total. This prevents the user from accidentally
            // falling into another cycle by pressing Enter too quickly.
            // The program waits for an explicit "Enter to continue"
            // before showing the next prompt.
            // ============================================================
 
            System.out.println();
            System.out.println("════════════════════════════════════════");
            System.out.println("  Cycle complete. Total cycles run: " + cycleCount);
            System.out.println("  Estimated API calls this session: " + (cycleCount * 7));
            System.out.println("════════════════════════════════════════");
            System.out.println();
            System.out.print("Press Enter to continue (or type 'quit'): ");
            String afterCycle = scanner.nextLine().trim().toLowerCase();
 
            if (afterCycle.equalsIgnoreCase("quit")) {
                System.out.println("Goodbye! Ran " + cycleCount + " debate cycle(s) this session.");
                break;
            }
        }
 
        scanner.close();
    }
 
 
    // ============================================================
    // selectStakeholder() — Displays the profile menu and lets
    // the user pick which stakeholder is in the "hotseat."
    //
    // This modifies the class-level 'activeStakeholder' variable.
    // Every subsequent debate cycle will use that stakeholder's
    // profile as context for all API calls.
    //
    // LEARNING POINT: This is a simple menu pattern. The user sees
    // a numbered list, types a number, and we validate the input.
    // In a web version, this would be a dropdown or user login.
    // ============================================================
 
    private static void selectStakeholder(Scanner scanner) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   Select Active Stakeholder          ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println();
 
        // Loop through the profiles array and print each one.
        // 'i' is the index; we show it as (i+1) so users see 1-4 instead of 0-3.
        for (int i = 0; i < STAKEHOLDER_PROFILES.length; i++) {
            String[] profile = STAKEHOLDER_PROFILES[i];
            System.out.println("  " + (i + 1) + ". " + profile[0] + " — " + profile[1]
                    + " (" + profile[2] + ")");
        }
 
        System.out.println();
        System.out.print("Enter number (1-" + STAKEHOLDER_PROFILES.length + "): ");
        String input = scanner.nextLine().trim();
 
        try {
            // Integer.parseInt() converts a String like "2" into the int 2.
            // If the user types letters or garbage, it throws NumberFormatException.
            int choice = Integer.parseInt(input) - 1;  // subtract 1 to get array index
 
            if (choice >= 0 && choice < STAKEHOLDER_PROFILES.length) {
                activeStakeholder = choice;
                String[] selected = STAKEHOLDER_PROFILES[activeStakeholder];
                System.out.println();
                System.out.println("Active stakeholder: " + selected[0]
                        + " — " + selected[1]);
                System.out.println("KPIs: " + selected[5]);
                System.out.println("Authority: " + selected[4]);
            } else {
                System.out.println("Invalid choice. Keeping current: "
                        + STAKEHOLDER_PROFILES[activeStakeholder][0]);
            }
        } catch (NumberFormatException e) {
            // User typed something that isn't a number
            System.out.println("Invalid input. Keeping current: "
                    + STAKEHOLDER_PROFILES[activeStakeholder][0]);
        }
    }
 
 
    // ============================================================
    // formatStakeholderBriefing() — Converts the active stakeholder's
    // profile into a plain-English block for the AI models.
    //
    // This is the "outer layer" of the onion — it tells the model
    // WHO is asking the question and what they care about.
    // ============================================================
 
    private static String formatStakeholderBriefing() {
        String[] p = STAKEHOLDER_PROFILES[activeStakeholder];
 
        return "=== ACTIVE STAKEHOLDER (who is asking this question) ===\n"
             + "Name: " + p[0] + "\n"
             + "Title: " + p[1] + "\n"
             + "Department: " + p[2] + "\n"
             + "Reports to: " + p[3] + "\n"
             + "Decision authority: " + p[4] + "\n"
             + "KPIs: " + p[5] + "\n"
             + "Background: " + p[6] + "\n"
             + "=== END STAKEHOLDER PROFILE ===\n\n"
             + "Tailor your analysis to this stakeholder's role, authority, "
             + "and KPIs. Address what THEY specifically need to know.\n\n";
    }
 
 
    // ============================================================
    // buildAgentPrompt() — Assembles the full "onion" prompt.
    //
    // Wraps the user's raw prompt with all three context layers:
    //   1. Team context (what kind of panel this is)
    //   2. Agent identity (this model's specific role)
    //   3. Stakeholder briefing (who's asking)
    //   4. The actual prompt
    //
    // Every API call in Phase 1 uses this method so each model
    // enters the conversation with full context.
    //
    // PARAMETERS:
    //   agentProfile = the specific model's identity (e.g., CLAUDE_AGENT_PROFILE)
    //   rawPrompt    = the user's original question
    //
    // RETURNS: the complete prompt string ready to send to the API
    // ============================================================
 
    private static String buildAgentPrompt(String agentProfile, String rawPrompt) {
        return TEAM_CONTEXT
             + agentProfile
             + formatStakeholderBriefing()
             + "=== STAKEHOLDER'S QUESTION ===\n"
             + rawPrompt;
    }
 
 
    // ============================================================
    // runDebateCycle() — The brain of the platform.
    //
    // This is where the 3-phase debate happens:
    //   Phase 1: Each model answers independently
    //   Phase 2: Each model reacts to the other two
    //   Phase 3: Claude synthesizes everything
    //
    // IMPORTANT CONCEPT: Each phase is just an API call with a
    // different prompt. The "debate" isn't special technology —
    // it's just passing text between models strategically.
    // ============================================================
 
    private static void runDebateCycle(String userPrompt) {
 
        // ==========================
        // PHASE 1: INDEPENDENT RESPONSES
        // ==========================
        // Each model sees the user's question PLUS its agent identity
        // PLUS the active stakeholder's profile. They respond from
        // their assigned perspective, tailored to who's asking.
 
        System.out.println();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   PHASE 1: Independent Responses     ║");
        System.out.println("╚══════════════════════════════════════╝");
 
        // buildAgentPrompt() wraps the raw prompt with the full onion:
        // team context + agent identity + stakeholder profile + question
        System.out.println("\n[Calling Claude (Strategy & Risk)...]");
        String claudeResponse = callClaude(buildAgentPrompt(CLAUDE_AGENT_PROFILE, userPrompt));
        System.out.println("Claude responded. ✓");
 
        System.out.println("\n[Calling GPT (Innovation & Opportunity)...]");
        String gptResponse = callOpenAi(buildAgentPrompt(GPT_AGENT_PROFILE, userPrompt));
        System.out.println("GPT responded. ✓");
 
        System.out.println("\n[Calling Gemini (Technical Feasibility)...]");
        String geminiResponse = callGemini(buildAgentPrompt(GEMINI_AGENT_PROFILE, userPrompt));
        System.out.println("Gemini responded. ✓");
 
        // Print Phase 1 results so the user can see what each model said
        System.out.println("\n--- Claude's Response ---");
        System.out.println(claudeResponse);
        System.out.println("\n--- GPT's Response ---");
        System.out.println(gptResponse);
        System.out.println("\n--- Gemini's Response ---");
        System.out.println(geminiResponse);
 
 
        // ==========================
        // PHASE 2: CROSS-REACTION
        // ==========================
        // Now each model sees what the OTHER TWO said.
        // We build a new, longer prompt that includes the original
        // question PLUS the other models' responses.
        //
        // THIS IS THE KEY INSIGHT: We're not using any special
        // "debate API." We're just sending a bigger prompt that
        // says "here's what others think — now react."
 
        System.out.println();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   PHASE 2: Cross-Reaction            ║");
        System.out.println("╚══════════════════════════════════════╝");
 
        // buildReactionPrompt() constructs the prompt that shows
        // a model what its peers said. See the method below.
 
        String claudeReactionPrompt = buildReactionPrompt(
                CLAUDE_AGENT_PROFILE, userPrompt, "Claude",
                "GPT", gptResponse,
                "Gemini", geminiResponse
        );
 
        String gptReactionPrompt = buildReactionPrompt(
                GPT_AGENT_PROFILE, userPrompt, "GPT",
                "Claude", claudeResponse,
                "Gemini", geminiResponse
        );
 
        String geminiReactionPrompt = buildReactionPrompt(
                GEMINI_AGENT_PROFILE, userPrompt, "Gemini",
                "Claude", claudeResponse,
                "GPT", gptResponse
        );
 
        System.out.println("\n[Claude reacting to GPT and Gemini...]");
        String claudeReaction = callClaude(claudeReactionPrompt);
        System.out.println("Claude reacted. ✓");
 
        System.out.println("\n[GPT reacting to Claude and Gemini...]");
        String gptReaction = callOpenAi(gptReactionPrompt);
        System.out.println("GPT reacted. ✓");
 
        System.out.println("\n[Gemini reacting to Claude and GPT...]");
        String geminiReaction = callGemini(geminiReactionPrompt);
        System.out.println("Gemini reacted. ✓");
 
        // Print Phase 2 results
        System.out.println("\n--- Claude's Reaction ---");
        System.out.println(claudeReaction);
        System.out.println("\n--- GPT's Reaction ---");
        System.out.println(gptReaction);
        System.out.println("\n--- Gemini's Reaction ---");
        System.out.println(geminiReaction);
 
 
        // ==========================
        // PHASE 3: SYNTHESIS
        // ==========================
        // Claude acts as the orchestrator. It receives ALL SIX outputs
        // (3 initial responses + 3 reactions) and produces a structured
        // report identifying agreement, disagreement, and a recommendation.
 
        System.out.println();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   PHASE 3: Synthesis Report           ║");
        System.out.println("╚══════════════════════════════════════╝");
 
        String synthesisPrompt = buildSynthesisPrompt(
                userPrompt,
                claudeResponse, gptResponse, geminiResponse,
                claudeReaction, gptReaction, geminiReaction
        );
 
        System.out.println("\n[Claude synthesizing all perspectives...]");
        String synthesis = callClaude(synthesisPrompt);
 
        System.out.println("\n" + synthesis);
    }
 
 
    // ============================================================
    // buildReactionPrompt() — Constructs the Phase 2 prompt.
    //
    // This method wraps the cross-reaction with full context:
    //   - The model's agent identity (so it stays in character)
    //   - The stakeholder briefing (so it remembers who it's advising)
    //   - The original question
    //   - The other two models' responses
    //   - Instructions for how to react
    //
    // PARAMETERS:
    //   agentProfile  = this model's agent identity string
    //   userPrompt    = the original question
    //   myName        = this model's name
    //   peerAName     = first peer's name
    //   peerAResponse = first peer's Phase 1 response
    //   peerBName     = second peer's name
    //   peerBResponse = second peer's Phase 1 response
    //
    // RETURNS: a single String that becomes the new API prompt
    // ============================================================
 
    private static String buildReactionPrompt(String agentProfile,
                                              String userPrompt,
                                              String myName,
                                              String peerAName, String peerAResponse,
                                              String peerBName, String peerBResponse) {
 
        StringBuilder sb = new StringBuilder();
 
        // Layer 1: Agent identity (stay in character during reaction)
        sb.append(TEAM_CONTEXT);
        sb.append(agentProfile);
 
        // Layer 2: Stakeholder context (remember who you're advising)
        sb.append(formatStakeholderBriefing());
 
        // Layer 3: The cross-reaction task
        sb.append("THE ORIGINAL QUESTION:\n");
        sb.append(userPrompt).append("\n\n");
 
        sb.append("You are ").append(myName).append(". You already provided your initial response.\n");
        sb.append("Now, the other two panel members have also responded. ");
        sb.append("Review their perspectives below, keeping YOUR assigned role in mind.\n\n");
 
        sb.append("--- ").append(peerAName).append("'s Response ---\n");
        sb.append(peerAResponse).append("\n\n");
 
        sb.append("--- ").append(peerBName).append("'s Response ---\n");
        sb.append(peerBResponse).append("\n\n");
 
        sb.append("React FROM YOUR ASSIGNED PERSPECTIVE. Specifically:\n");
        sb.append("1. Where do you AGREE with the other panel members? Why?\n");
        sb.append("2. Where do you DISAGREE? What did they get wrong from YOUR perspective?\n");
        sb.append("3. What important points did they MISS given the stakeholder's role and KPIs?\n");
        sb.append("4. Has seeing their responses changed or refined YOUR position?\n");
        sb.append("\nBe specific. Reference their actual arguments, not vague generalities.");
 
        return sb.toString();
    }
 
 
    // ============================================================
    // buildSynthesisPrompt() — Constructs the Phase 3 prompt.
    //
    // This is the most important prompt in the system. It tells
    // Claude (as orchestrator) to analyze ALL six outputs and
    // produce the structured report.
    //
    // The quality of this prompt directly determines the quality
    // of the final output. Prompt engineering matters here.
    // ============================================================
 
    private static String buildSynthesisPrompt(String userPrompt,
                                               String claudeInitial, String gptInitial, String geminiInitial,
                                               String claudeReaction, String gptReaction, String geminiReaction) {
 
        StringBuilder sb = new StringBuilder();
 
        sb.append("You are the orchestrator of a multi-AI advisory panel.\n\n");
 
        // Include stakeholder context so the synthesis is tailored
        sb.append(formatStakeholderBriefing());
 
        sb.append("=== PANEL COMPOSITION ===\n");
        sb.append("Claude: Chief Strategy & Risk Analyst\n");
        sb.append("GPT: Innovation & Opportunity Analyst\n");
        sb.append("Gemini: Technical Feasibility & Implementation Lead\n\n");
 
        sb.append("The panel debated the following question from the stakeholder above.\n\n");
 
        sb.append("=== ORIGINAL QUESTION ===\n");
        sb.append(userPrompt).append("\n\n");
 
        sb.append("=== PHASE 1: INITIAL RESPONSES ===\n\n");
        sb.append("--- Claude (Strategy & Risk) ---\n").append(claudeInitial).append("\n\n");
        sb.append("--- GPT (Innovation & Opportunity) ---\n").append(gptInitial).append("\n\n");
        sb.append("--- Gemini (Technical Feasibility) ---\n").append(geminiInitial).append("\n\n");
 
        sb.append("=== PHASE 2: CROSS-REACTIONS ===\n\n");
        sb.append("--- Claude's Reaction ---\n").append(claudeReaction).append("\n\n");
        sb.append("--- GPT's Reaction ---\n").append(gptReaction).append("\n\n");
        sb.append("--- Gemini's Reaction ---\n").append(geminiReaction).append("\n\n");
 
        sb.append("=== YOUR TASK ===\n");
        sb.append("Produce a SYNTHESIS REPORT with the following sections.\n");
        sb.append("Tailor the report to the active stakeholder's role, KPIs, ");
        sb.append("and decision authority.\n\n");
 
        sb.append("1. AREAS OF AGREEMENT\n");
        sb.append("   What conclusions do all three panel members converge on? ");
        sb.append("These are the highest-confidence findings.\n\n");
 
        sb.append("2. AREAS OF DISAGREEMENT\n");
        sb.append("   Where do panel members conflict? Identify which ROLE ");
        sb.append("(strategy vs innovation vs technical) drives each position.\n\n");
 
        sb.append("3. KEY INSIGHTS\n");
        sb.append("   What emerged from the cross-reaction that wasn't in ");
        sb.append("the initial responses? What changed when perspectives collided?\n\n");
 
        sb.append("4. RECOMMENDATION FOR THIS STAKEHOLDER\n");
        sb.append("   Given the stakeholder's specific KPIs, authority, and role, ");
        sb.append("what should THEY specifically do? What's actionable for THEM?\n\n");
 
        sb.append("Be thorough but concise. Reference specific arguments from each panel member.");
 
        return sb.toString();
    }
 
 
    // ============================================================
    //
    //   API CALL METHODS
    //
    //   Each method below follows the SAME three-step pattern:
    //     1. Build the JSON body (different format per provider)
    //     2. Build and send the HTTP request (different URL/headers)
    //     3. Extract the text from the JSON response
    //
    //   LEARNING POINT: Notice how similar these methods are.
    //   The pattern is identical — only the details differ.
    //   This repetition is why we'll eventually refactor into
    //   separate classes with a shared interface (LlmClient).
    //   For now, seeing the repetition teaches you the pattern.
    //
    // ============================================================
 
 
    // ============================================================
    // callClaude() — Anthropic API
    //
    // UNIQUE DETAILS:
    //   - Auth: "x-api-key" header
    //   - Requires "anthropic-version" header
    //   - Response path: content[0].text
    // ============================================================
 
    private static String callClaude(String prompt) {
 
        String escapedPrompt = escapeForJson(prompt);
 
        String requestBody = """
                {
                    "model": "%s",
                    "max_tokens": 1024,
                    "messages": [
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ]
                }
                """.formatted(CLAUDE_MODEL, escapedPrompt);
 
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CLAUDE_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", CLAUDE_KEY)                 // Anthropic uses x-api-key
                    .header("anthropic-version", "2023-06-01")       // Required version header
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
 
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
 
            if (response.statusCode() != 200) {
                return "[Claude ERROR " + response.statusCode() + "] " + response.body();
            }
 
            // Claude's response JSON: { "content": [{ "type": "text", "text": "answer" }] }
            return extractField(response.body(), "text");
 
        } catch (Exception e) {
            return "[Claude ERROR] " + e.getMessage();
        }
    }
 
 
    // ============================================================
    // callOpenAi() — OpenAI API
    //
    // UNIQUE DETAILS:
    //   - Auth: "Authorization: Bearer ..." header (industry standard)
    //   - Response path: choices[0].message.content
    //   - JSON body is VERY similar to Claude — both use "messages"
    //     with "role" and "content". This isn't a coincidence;
    //     OpenAI popularized this format and others adopted it.
    // ============================================================
 
    private static String callOpenAi(String prompt) {
 
        String escapedPrompt = escapeForJson(prompt);
 
        // Notice: almost identical JSON to Claude.
        // "messages" array with "role" and "content" — same pattern.
        // NOTE: OpenAI recently renamed "max_tokens" to "max_completion_tokens"
        // for newer models like gpt-4o. Same concept, different field name.
        String requestBody = """
                {
                    "model": "%s",
                    "max_completion_tokens": 1024,
                    "messages": [
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ]
                }
                """.formatted(OPENAI_MODEL, escapedPrompt);
 
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + OPENAI_KEY)  // OpenAI uses Bearer token
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
 
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
 
            if (response.statusCode() != 200) {
                return "[GPT ERROR " + response.statusCode() + "] " + response.body();
            }
 
            // GPT's response JSON: { "choices": [{ "message": { "content": "answer" } }] }
            // The answer lives under "content" inside "message" inside "choices".
            // We search for "content" but skip the first occurrence (which is in the
            // request echo), so we look for it after "message".
            return extractField(response.body(), "content");
 
        } catch (Exception e) {
            return "[GPT ERROR] " + e.getMessage();
        }
    }
 
 
    // ============================================================
    // callGemini() — Google Gemini API
    //
    // UNIQUE DETAILS:
    //   - Auth: API key goes IN THE URL (not in a header!)
    //     This is an older pattern. Google is the odd one out.
    //   - JSON body uses "contents" and "parts" (not "messages")
    //   - No "role" field needed for a simple single-turn prompt
    //   - Response path: candidates[0].content.parts[0].text
    // ============================================================
 
    private static String callGemini(String prompt) {
 
        String escapedPrompt = escapeForJson(prompt);
 
        // Gemini's URL includes the model name AND the API key.
        // This is different from Claude and GPT where the key goes in headers.
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + GEMINI_MODEL + ":generateContent?key=" + GEMINI_KEY;
 
        // Gemini uses a DIFFERENT JSON structure.
        // Instead of "messages" with "role"/"content", it uses
        // "contents" with "parts" containing "text".
        // Every API has its own dialect — this is normal.
        String requestBody = """
                {
                    "contents": [
                        {
                            "parts": [
                                {
                                    "text": "%s"
                                }
                            ]
                        }
                    ]
                }
                """.formatted(escapedPrompt);
 
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    // No auth header — the key is already in the URL above
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
 
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
 
            if (response.statusCode() != 200) {
                return "[Gemini ERROR " + response.statusCode() + "] " + response.body();
            }
 
            // Gemini's response JSON:
            // { "candidates": [{ "content": { "parts": [{ "text": "answer" }] } }] }
            return extractField(response.body(), "text");
 
        } catch (Exception e) {
            return "[Gemini ERROR] " + e.getMessage();
        }
    }
 
 
    // ============================================================
    //
    //   UTILITY METHODS
    //
    //   Helper methods used by the API call methods above.
    //   They handle the messy details so the main logic stays clean.
    //
    // ============================================================
 
 
    // ============================================================
    // escapeForJson() — Makes user input safe to embed in JSON.
    //
    // WHY THIS IS NECESSARY:
    // JSON uses double quotes to wrap strings. If the user types:
    //   He said "hello"
    // And we naively insert that into JSON, it becomes:
    //   { "content": "He said "hello"" }
    //                          ^ JSON breaks here
    //
    // Escaping adds a backslash before special characters:
    //   { "content": "He said \"hello\"" }
    //                          ^ now JSON knows this quote is literal
    //
    // ORDER MATTERS: We escape backslashes FIRST because the other
    // replacements ADD backslashes. If we did backslashes last,
    // we'd double-escape the ones we just added.
    // ============================================================
 
    private static String escapeForJson(String input) {
        return input
                .replace("\\", "\\\\")   // backslash → \\  (must be first!)
                .replace("\"", "\\\"")   // double quote → \"
                .replace("\n", "\\n")    // newline → \n
                .replace("\r", "\\r")    // carriage return → \r
                .replace("\t", "\\t");   // tab → \t
    }
 
 
    // ============================================================
    // extractField() — Pulls a specific value from JSON by field name.
    //
    // This is our simple JSON "parser." It finds a field like "text"
    // and extracts the string value after it.
    //
    // HOW IT WORKS:
    //   1. Search for the pattern  "fieldName": "
    //   2. Everything after that quote until the next unescaped quote
    //      is our value.
    //
    // WHY WE SEARCH FROM THE END (lastIndexOf):
    // OpenAI's response contains "content" twice — once in the
    // request echo and once in the actual answer. The LAST occurrence
    // is the one we want. So we use lastIndexOf() instead of indexOf().
    //
    // LIMITATION: This won't work for nested objects or arrays.
    // That's fine for now — all three APIs put the answer in a
    // simple string field. When we add Gson later, this goes away.
    // ============================================================
 
    private static String extractField(String json, String fieldName) {
 
        // Build the pattern we're looking for: "fieldName": "
        // We try both with and without a space after the colon
        // because APIs format JSON inconsistently.
        String marker = "\"" + fieldName + "\": \"";
        String markerAlt = "\"" + fieldName + "\":\"";
 
        // lastIndexOf searches from the END of the string backward.
        // This is important for OpenAI where "content" appears multiple times.
        int startIndex = json.lastIndexOf(marker);
        int markerLength = marker.length();
 
        if (startIndex == -1) {
            startIndex = json.lastIndexOf(markerAlt);
            markerLength = markerAlt.length();
        }
 
        if (startIndex == -1) {
            return "[PARSE ERROR] Could not find field '" + fieldName
                    + "' in response.\nRaw: " + json;
        }
 
        // Move past the marker to where the actual value starts
        startIndex += markerLength;
 
        // Find the closing quote (handling escaped quotes)
        int endIndex = startIndex;
        while (endIndex < json.length()) {
            char current = json.charAt(endIndex);
 
            if (current == '"') {
                // Count backslashes before this quote
                int backslashCount = 0;
                int checkIndex = endIndex - 1;
                while (checkIndex >= startIndex && json.charAt(checkIndex) == '\\') {
                    backslashCount++;
                    checkIndex--;
                }
                // Even number of backslashes = real quote (end of value)
                // Odd number = escaped quote (part of the value)
                if (backslashCount % 2 == 0) {
                    break;
                }
            }
            endIndex++;
        }
 
        // Extract and unescape the JSON string
        String extracted = json.substring(startIndex, endIndex);
        extracted = extracted.replace("\\n", "\n");
        extracted = extracted.replace("\\t", "\t");
        extracted = extracted.replace("\\\"", "\"");
        extracted = extracted.replace("\\\\", "\\");
 
        return extracted;
    }
}
 