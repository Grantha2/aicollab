package collab;

// ============================================================
// Main.java — Entry point for the AI Collaboration Platform.
//
// WHAT THIS FILE DOES (one sentence):
// Reads user input, manages the hotseat menu, and calls the
// Orchestrator — it's the front door, not the brain.
//
// HOW IT FITS THE ARCHITECTURE:
// Main.java is the first file a new team member should read.
// It shows how all the pieces connect:
//
//   Config          → loads API keys and settings
//   LlmClient       → interface that every AI model implements
//   AnthropicClient → Claude API (implements LlmClient)
//   OpenAiClient    → GPT API (implements LlmClient)
//   GeminiClient    → Gemini API (implements LlmClient)
//   AgentProfile    → each model's identity and perspective
//   StakeholderProfile → each human team member's profile
//   ConversationContext → memory across debate cycles
//   PromptBuilder   → assembles the layered "onion" prompts
//   Orchestrator    → runs the 3-phase debate cycle
//
// Main.java creates all of these, wires them together, and then
// runs a loop: pick a stakeholder, type a prompt, confirm, debate.
//
// HOW TO RUN:
//   Option A (Maven):  mvn compile exec:java
//   Option B (javac):  javac -d target src/main/java/collab/*.java
//                      java -cp target collab.Main
//
// On first run, the program will ask for your API keys and save
// them locally (they won't be asked again).
// ============================================================

import java.net.http.HttpClient;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        // ==========================
        // STEP 1: LOAD CONFIGURATION
        // ==========================
        // Config reads API keys and settings from config.properties.
        // If the file doesn't exist, it walks you through first-time setup.
        Config config = new Config("config.properties");

        // ==========================
        // STEP 2: CREATE SHARED HTTP CLIENT
        // ==========================
        // One HttpClient instance, shared by all three API clients.
        // Creating a new one each time wastes resources (connection pools,
        // thread pools). This is a standard Java pattern.
        HttpClient httpClient = HttpClient.newHttpClient();

        // ==========================
        // STEP 3: CREATE THE THREE AI CLIENTS
        // ==========================
        // Each client implements LlmClient (the interface). The Orchestrator
        // doesn't know or care which provider is behind each one — it just
        // calls sendMessage(). This is the power of the interface pattern.
        int maxTokens = config.getMaxResponseTokens();

        LlmClient claudeClient = new AnthropicClient(
                httpClient, config.getClaudeUrl(), config.getClaudeKey(),
                config.getClaudeModel(), maxTokens);

        LlmClient gptClient = new OpenAiClient(
                httpClient, config.getOpenAiUrl(), config.getOpenAiKey(),
                config.getOpenAiModel(), maxTokens);

        LlmClient geminiClient = new GeminiClient(
                httpClient, config.getGeminiKey(),
                config.getGeminiModel(), maxTokens);

        // ==========================
        // STEP 4: CREATE AGENT PROFILES
        // ==========================
        // Each AI model gets a distinct identity: Claude focuses on
        // architecture & quality, GPT on ideas & possibilities, Gemini
        // on execution & delivery. These shape how each model responds.
        List<AgentProfile> agents = AgentProfile.getDefaults();
        AgentProfile claudeAgent = agents.get(0);
        AgentProfile gptAgent    = agents.get(1);
        AgentProfile geminiAgent = agents.get(2);

        // ==========================
        // STEP 5: LOAD STAKEHOLDER PROFILES
        // ==========================
        // The four team members. The user picks one before each cycle
        // (the "hotseat"), and that profile gets injected into every prompt.
        List<StakeholderProfile> stakeholders = StakeholderProfile.getDefaults();

        // ==========================
        // STEP 6: CREATE MEMORY AND PROMPT BUILDER
        // ==========================
        // ConversationContext stores synthesis reports from past cycles.
        // PromptBuilder uses it to include history in future prompts.
        ConversationContext context = new ConversationContext(config.getMaxHistoryChars());
        PromptBuilder promptBuilder = new PromptBuilder(context);

        // ==========================
        // STEP 7: CREATE THE ORCHESTRATOR
        // ==========================
        // The Orchestrator is the brain — it runs the 3-phase debate.
        // We pass it everything it needs: clients, agents, prompt builder,
        // memory, and how many debate rounds to run.
        Orchestrator orchestrator = new Orchestrator(
                claudeClient, gptClient, geminiClient,
                claudeAgent, gptAgent, geminiAgent,
                promptBuilder, context,
                config.getDebateRounds());

        // ==========================
        // STEP 8: RUN THE CLI LOOP
        // ==========================
        Scanner scanner = new Scanner(System.in);
        int cycleCount = 0;
        int activeStakeholderIndex = 0;

        System.out.println("========================================");
        System.out.println("  AI Collaboration Platform v0.4");
        System.out.println("  Full Debate Cycle: 3 Models + Synthesis");
        System.out.println("  Each cycle makes " + orchestrator.getApiCallCount() + " API calls.");
        System.out.println("  Type 'quit' to exit.");
        System.out.println("========================================");

        // --- STAKEHOLDER SELECTION (the "hotseat") ---
        // Before any prompts, the user picks which team member they are.
        activeStakeholderIndex = selectStakeholder(scanner, stakeholders, activeStakeholderIndex);

        while (true) {

            // Show who's in the hotseat and get their prompt
            StakeholderProfile active = stakeholders.get(activeStakeholderIndex);
            System.out.println();
            System.out.println("[" + active.getName() + " \u2014 " + active.getRole() + "]");

            // Show memory status if the panel has context from previous cycles
            if (context.getCycleCount() > 0) {
                System.out.println("(Panel has context from " + context.getCycleCount() + " previous cycle"
                        + (context.getCycleCount() == 1 ? "" : "s") + ")");
            }

            System.out.println("(Commands: 'switch', 'quit')");
            System.out.println("Enter your prompt (type SEND on its own line to submit):");

            // ============================================================
            // MULTI-LINE INPUT COLLECTION
            //
            // WHY NOT JUST scanner.nextLine()?
            // Terminal input reads one line at a time. If the user pastes
            // a multi-paragraph prompt, each line fires separately. We
            // collect lines into a StringBuilder until the user types
            // "SEND" on its own line. This means:
            //   - Pasting multi-line text works perfectly
            //   - Blank lines within the prompt are preserved
            //   - Only an explicit "SEND" triggers the debate cycle
            // ============================================================

            StringBuilder inputBuilder = new StringBuilder();
            while (true) {
                String line = scanner.nextLine();

                // Check for commands ONLY on the first line (before any
                // content has been entered).
                if (inputBuilder.isEmpty()) {
                    if (line.trim().equalsIgnoreCase("quit")) {
                        inputBuilder.append("__QUIT__");
                        break;
                    }
                    if (line.trim().equalsIgnoreCase("switch")) {
                        inputBuilder.append("__SWITCH__");
                        break;
                    }
                }

                // "SEND" on its own line = done collecting input
                if (line.trim().equalsIgnoreCase("SEND")) {
                    break;
                }

                if (inputBuilder.length() > 0) {
                    inputBuilder.append("\n");
                }
                inputBuilder.append(line);
            }

            String userPrompt = inputBuilder.toString().trim();

            // Handle special command signals
            if (userPrompt.equals("__QUIT__")) {
                System.out.println("Goodbye! Ran " + cycleCount + " debate cycle(s) this session.");
                break;
            }

            if (userPrompt.equals("__SWITCH__")) {
                activeStakeholderIndex = selectStakeholder(scanner, stakeholders, activeStakeholderIndex);
                continue;
            }

            if (userPrompt.isEmpty()) {
                System.out.println("(Empty prompt \u2014 type something, then SEND)");
                continue;
            }

            // ============================================================
            // COST SAFEGUARD: Confirm before running
            // ============================================================
            String preview = userPrompt.length() > 100
                    ? userPrompt.substring(0, 100) + "..."
                    : userPrompt;
            System.out.println();
            System.out.println("Stakeholder: " + active.getName() + " (" + active.getRole() + ")");
            System.out.println("Prompt preview: " + preview);
            System.out.println("This will run a full debate cycle (" + orchestrator.getApiCallCount() + " API calls).");
            System.out.print("Proceed? (y/n): ");
            String confirm = scanner.nextLine().trim().toLowerCase();

            if (!confirm.equals("y") && !confirm.equals("yes")) {
                System.out.println("Cancelled. Enter a new prompt or 'quit' to exit.");
                continue;
            }

            // Run the debate!
            orchestrator.runDebate(userPrompt, active);
            cycleCount++;

            // ============================================================
            // POST-CYCLE PAUSE
            //
            // After a cycle completes, show a clear separator and the
            // running total. Wait for explicit "Enter" before continuing.
            // ============================================================
            System.out.println();
            System.out.println("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
            System.out.println("  Cycle complete. Total cycles run: " + cycleCount);
            System.out.println("  Estimated API calls this session: " + (cycleCount * orchestrator.getApiCallCount()));
            System.out.println("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
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
    // LEARNING POINT: This is a simple menu pattern. The user sees
    // a numbered list, types a number, and we validate the input.
    // In a web version, this would be a dropdown or user login.
    //
    // PARAMETERS:
    //   scanner      — for reading user input
    //   stakeholders — the list of available profiles
    //   current      — the current selection index (kept if input is invalid)
    //
    // RETURNS: the new active stakeholder index
    // ============================================================
    private static int selectStakeholder(Scanner scanner,
                                         List<StakeholderProfile> stakeholders,
                                         int current) {
        System.out.println();
        System.out.println("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
        System.out.println("\u2551   Select Active Stakeholder          \u2551");
        System.out.println("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");
        System.out.println();

        for (int i = 0; i < stakeholders.size(); i++) {
            StakeholderProfile p = stakeholders.get(i);
            System.out.println("  " + (i + 1) + ". " + p.getName() + " \u2014 " + p.getRole()
                    + " (" + p.getFocusArea() + ")");
        }

        System.out.println();
        System.out.print("Enter number (1-" + stakeholders.size() + "): ");
        String input = scanner.nextLine().trim();

        try {
            int choice = Integer.parseInt(input) - 1;

            if (choice >= 0 && choice < stakeholders.size()) {
                StakeholderProfile selected = stakeholders.get(choice);
                System.out.println();
                System.out.println("Active stakeholder: " + selected.getName()
                        + " \u2014 " + selected.getRole());
                System.out.println("KPIs: " + selected.getEvaluatedOn());
                System.out.println("Authority: " + selected.getResponsibilities());
                return choice;
            } else {
                System.out.println("Invalid choice. Keeping current: "
                        + stakeholders.get(current).getName());
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Keeping current: "
                    + stakeholders.get(current).getName());
        }

        return current;
    }
}
