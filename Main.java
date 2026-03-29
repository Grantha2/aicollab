// ============================================================
// Main.java — AI Collaboration Platform v0.2
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
//   javac Main.java
//   java Main
//
// PREREQUISITES:
//   - Java 21 installed
//   - API keys for Anthropic, OpenAI, and Google Gemini
// ============================================================


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    // SECURITY NOTE: In a real project, these keys would be stored
    // in environment variables or a config file, NOT in source code.
    // We're hardcoding them here for simplicity while learning.
    // NEVER commit real keys to Git.
    // ============================================================

    // --- ANTHROPIC (Claude) ---
    private static final String CLAUDE_URL = "https://api.anthropic.com/v1/messages";
    private static final String CLAUDE_KEY = "YOUR_CLAUDE_KEY_HERE";
    private static final String CLAUDE_MODEL = "claude-sonnet-4-20250514";

    // --- OPENAI (GPT) ---
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String OPENAI_KEY = "YOUR_OPENAI_KEY_HERE";
    private static final String OPENAI_MODEL = "gpt-4o";

    // --- GOOGLE (Gemini) ---
    // NOTE: Gemini is different — the API key goes in the URL, not in a header.
    // The URL gets built dynamically in the callGemini() method.
    private static final String GEMINI_KEY = "YOUR_GEMINI_KEY_HERE";
    // "gemini-2.0-flash" was retired for new users in 2026.
    // "gemini-2.5-flash" is the current stable text model.
    private static final String GEMINI_MODEL = "gemini-2.5-flash";

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
    // what kind of team they're part of.
    private static final String TEAM_CONTEXT =
            "=== ADVISORY PANEL CONTEXT ===\n"
          + "You are one member of a 3-agent AI advisory panel.\n"
          + "The panel consists of Claude (Anthropic), GPT (OpenAI), and Gemini (Google).\n"
          + "Your role is to provide expert analysis from your assigned perspective.\n"
          + "You will first respond independently, then react to the other agents' positions,\n"
          + "and finally a synthesis report will be produced.\n"
          + "Always stay in character with your assigned role below.\n\n";

    // --- INDIVIDUAL AGENT PROFILES ---
    // Each model gets a different "inner layer" that defines their
    // unique lens on every problem. These are designed to produce
    // genuinely different perspectives, not just stylistic variation.

    private static final String CLAUDE_AGENT_PROFILE =
            "=== YOUR AGENT IDENTITY ===\n"
          + "Agent name: Claude\n"
          + "Role on panel: Chief Strategy & Risk Analyst\n"
          + "Your priorities:\n"
          + "  1. Identify strategic tradeoffs and second-order consequences\n"
          + "  2. Stress-test assumptions — find what could go wrong\n"
          + "  3. Evaluate feasibility and implementation risk\n"
          + "  4. Consider organizational and political dynamics\n"
          + "Perspective: You think like a seasoned executive advisor.\n"
          + "You value clarity, honesty, and practical wisdom over hype.\n"
          + "When you disagree, you explain WHY with specific reasoning.\n\n";

    private static final String GPT_AGENT_PROFILE =
            "=== YOUR AGENT IDENTITY ===\n"
          + "Agent name: GPT\n"
          + "Role on panel: Innovation & Opportunity Analyst\n"
          + "Your priorities:\n"
          + "  1. Identify creative solutions and untapped opportunities\n"
          + "  2. Bring diverse perspectives and cross-industry insights\n"
          + "  3. Challenge conventional thinking with fresh approaches\n"
          + "  4. Evaluate potential upside and growth possibilities\n"
          + "Perspective: You think like a venture-minded innovator.\n"
          + "You look for what's possible, not just what's safe.\n"
          + "When you disagree, you propose ALTERNATIVES, not just objections.\n\n";

    private static final String GEMINI_AGENT_PROFILE =
            "=== YOUR AGENT IDENTITY ===\n"
          + "Agent name: Gemini\n"
          + "Role on panel: Technical Feasibility & Implementation Lead\n"
          + "Your priorities:\n"
          + "  1. Evaluate technical architecture and implementation paths\n"
          + "  2. Identify resource requirements, timelines, and dependencies\n"
          + "  3. Ground abstract ideas in concrete execution plans\n"
          + "  4. Assess scalability, maintainability, and technical debt\n"
          + "Perspective: You think like a senior technical lead.\n"
          + "You turn vision into blueprints and catch impractical ideas early.\n"
          + "When you disagree, you show the ENGINEERING REALITY behind your position.\n\n";


    // ============================================================
    // STAKEHOLDER PROFILES — The humans who prompt the panel.
    //
    // These are predefined profiles that represent different people
    // in an organization. Before each prompt, the user selects which
    // stakeholder they're acting as (the "hotseat").
    //
    // WHY THIS MATTERS:
    // A CEO asking "should we expand to Europe?" needs different
    // analysis than a CFO asking the same question. The CEO cares
    // about market opportunity; the CFO cares about cash flow risk.
    // The AI panel adjusts its analysis based on who's asking.
    //
    // FORMAT: Each profile is a String array with 7 elements:
    //   [0] Name
    //   [1] Title
    //   [2] Department
    //   [3] Reports to
    //   [4] Decision authority
    //   [5] KPIs they're measured on
    //   [6] Relevant background
    //
    // HOW TO CUSTOMIZE: Change these to match your actual scenario.
    // For your final project, you could load these from a file
    // or let users create profiles at runtime.
    // ============================================================

    private static final String[][] STAKEHOLDER_PROFILES = {
        {
            "Alex Chen",
            "Chief Executive Officer",
            "Executive Leadership",
            "Board of Directors",
            "Final authority on strategic direction, partnerships, and major investments over $1M",
            "Revenue growth, market share, customer retention, team satisfaction",
            "Founded the company 5 years ago. Background in product management at Google. Focused on scaling from Series B to profitability."
        },
        {
            "Maria Santos",
            "Chief Technology Officer",
            "Engineering",
            "CEO (Alex Chen)",
            "Final say on technology stack, architecture decisions, and engineering hiring. Budget authority up to $500K.",
            "System uptime (99.9%), deployment frequency, tech debt ratio, engineering velocity",
            "Led cloud migration at previous company. Strong opinions on microservices vs monolith. Prioritizes developer experience."
        },
        {
            "James O'Brien",
            "Chief Financial Officer",
            "Finance",
            "CEO (Alex Chen)",
            "Controls budget allocation, financial planning, and vendor contracts. Must approve any spend over $100K.",
            "Burn rate, runway months, gross margin, CAC/LTV ratio, revenue per employee",
            "Former investment banker. Conservative with capital. Pushes for unit economics proof before scaling."
        },
        {
            "Priya Patel",
            "VP of Product",
            "Product Management",
            "CEO (Alex Chen)",
            "Owns product roadmap, feature prioritization, and go-to-market timing. Influences but doesn't control engineering resources.",
            "NPS score, feature adoption rate, time-to-market, customer churn reduction",
            "Joined from a competitor 2 years ago. Strong user research background. Advocates for customer-driven development."
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
            System.out.println("(Type 'switch' to change stakeholder, 'quit' to exit)");
            System.out.print("Prompt: ");
            String userPrompt = scanner.nextLine();

            if (userPrompt.trim().isEmpty()) {
                System.out.println("(Empty prompt — type something or 'quit' to exit)");
                continue;
            }

            if (userPrompt.trim().equalsIgnoreCase("quit")) {
                System.out.println("Goodbye! Ran " + cycleCount + " debate cycle(s) this session.");
                break;
            }

            // 'switch' lets the user change which stakeholder is in the hotseat
            // without exiting the program. The new profile will be sent to all
            // models in the next debate cycle.
            if (userPrompt.trim().equalsIgnoreCase("switch")) {
                selectStakeholder(scanner);
                continue;  // go back to the top of the loop with new profile
            }

            // ============================================================
            // COST SAFEGUARD: Confirm before running
            // ============================================================

            System.out.println();
            System.out.println("Stakeholder: " + name + " (" + title + ")");
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
