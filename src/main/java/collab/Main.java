package collab;

// ============================================================
// Main.java — Entry point for the AI Collaboration Platform.
//
// WHAT THIS FILE DOES (one sentence):
// Handles user login (name + role selection), displays the
// role-filtered functional area menu, reads prompts, and calls
// the Maestro — it's the front door, not the brain.
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
//   OfficerRole     → IDSSO officer positions + permissions
//   FunctionalArea  → the "buttons" each role can access
//   UserSession     → logged-in user identity + role
//   Contribution    → tagged context record (who, what, when)
//   SessionStore    → JSONL persistence for sessions
//   ConversationContext → memory across debate cycles
//   PromptBuilder   → assembles the layered context prompts
//   Maestro         → runs the 3-phase debate cycle
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
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        boolean cliMode = java.util.Arrays.stream(args).anyMatch("--cli"::equals);
        if (!cliMode) {
            MainGui.launch();
            return;
        }

        // ==========================
        // STEP 1: LOAD CONFIGURATION
        // ==========================
        Config config = new Config("config.properties");

        // ==========================
        // STEP 2: CREATE SHARED HTTP CLIENT
        // ==========================
        HttpClient httpClient = HttpClient.newHttpClient();

        // ==========================
        // STEP 3: CREATE THE THREE AI CLIENTS
        // ==========================
        // Each client implements LlmClient (the interface). The Maestro
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
        List<AgentProfile> agents = AgentProfile.getDefaults();
        AgentProfile claudeAgent = agents.get(0);
        AgentProfile gptAgent    = agents.get(1);
        AgentProfile geminiAgent = agents.get(2);

        // ==========================
        // STEP 5: CREATE MEMORY, PERSISTENCE, AND PROMPT BUILDER
        // ==========================
        ConversationContext context = new ConversationContext(config.getMaxHistoryChars());
        Scanner scanner = new Scanner(System.in);
        SessionStore sessionStore = selectSessionStore(scanner, context);
        PromptBuilder promptBuilder = new PromptBuilder(context);

        // ==========================
        // STEP 6: CREATE THE MAESTRO
        // ==========================
        Maestro maestro = new Maestro(
                claudeClient, gptClient, geminiClient,
                claudeAgent, gptAgent, geminiAgent,
                promptBuilder, context,
                config.getDebateRounds(), maxTokens, sessionStore);

        // ==========================
        // STEP 7: RUN THE CLI LOOP
        // ==========================
        int cycleCount = 0;

        System.out.println("========================================");
        System.out.println("  AI Collaboration Platform v0.5");
        System.out.println("  IDSSO Role-Based Access Control");
        System.out.println("  Full Debate Cycle: 3 Models + Synthesis");
        System.out.println("  Each cycle makes " + maestro.getApiCallCount() + " API calls.");
        System.out.println("  Session file: " + sessionStore.getSessionFile());
        System.out.println("  Type 'quit' to exit.");
        System.out.println("========================================");

        // --- USER LOGIN ---
        // User identifies themselves by name and selects their IDSSO role.
        // This determines which functional areas ("buttons") they can access.
        UserSession session = login(scanner);
        maestro.setActiveSession(session);

        while (true) {

            // Show who's logged in
            System.out.println();
            System.out.println("[" + session.getUserName() + " \u2014 "
                    + session.getRole().getDisplayName() + "]");

            // Show memory status if the panel has context from previous cycles
            if (context.getCycleCount() > 0) {
                System.out.println("(Panel has context from " + context.getCycleCount()
                        + " previous cycle"
                        + (context.getCycleCount() == 1 ? "" : "s") + ")");
            }

            System.out.println("(Commands: 'switch', 'quit')");

            // --- FUNCTIONAL AREA SELECTION ---
            // Show only the areas this role can access, grouped by category.
            FunctionalArea selectedArea = selectFunctionalArea(scanner, session);
            if (selectedArea == null) {
                // User typed 'switch' during area selection — re-run login
                session = login(scanner);
                maestro.setActiveSession(session);
                continue;
            }

            System.out.println("Selected area: " + selectedArea.getDisplayName());
            System.out.println("Enter your prompt (type SEND on its own line to submit):");

            // ============================================================
            // MULTI-LINE INPUT COLLECTION
            // ============================================================
            StringBuilder inputBuilder = new StringBuilder();
            while (true) {
                String line = scanner.nextLine();

                // Check for commands ONLY on the first line
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
                session = login(scanner);
                maestro.setActiveSession(session);
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
            System.out.println("User: " + session.getUserName()
                    + " (" + session.getRole().getDisplayName() + ")");
            System.out.println("Area: " + selectedArea.getDisplayName());
            System.out.println("Prompt preview: " + preview);
            System.out.println("This will run a full debate cycle (" + maestro.getApiCallCount() + " API calls).");
            System.out.print("Proceed? (y/n): ");
            String confirm = scanner.nextLine().trim().toLowerCase();

            if (!confirm.equals("y") && !confirm.equals("yes")) {
                System.out.println("Cancelled. Enter a new prompt or 'quit' to exit.");
                continue;
            }

            // --- TAG THE CONTRIBUTION ---
            // Record WHO is providing this context, WHAT area it's about,
            // and WHEN it was submitted. This feeds into the centralized
            // conversation history with full attribution.
            Contribution contribution = new Contribution(
                    session.getUserName(),
                    session.getRole().getDisplayName(),
                    selectedArea,
                    userPrompt,
                    System.currentTimeMillis()
            );
            context.addContribution(contribution);
            sessionStore.appendContribution(contribution);

            // --- RUN THE DEBATE ---
            // The StakeholderProfile is generated from the UserSession,
            // bridging the RBAC system to the existing prompt pipeline.
            StakeholderProfile activeProfile = session.toStakeholderProfile();
            maestro.runDebate(userPrompt, activeProfile);
            cycleCount++;

            // ============================================================
            // POST-CYCLE PAUSE
            // ============================================================
            System.out.println();
            System.out.println("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
            System.out.println("  Cycle complete. Total cycles run: " + cycleCount);
            System.out.println("  Estimated API calls this session: " + (cycleCount * maestro.getApiCallCount()));
            System.out.println("  Contribution by: " + session.getUserName()
                    + " (" + session.getRole().getDisplayName() + ")");
            System.out.println("  Area: " + selectedArea.getDisplayName());
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
    // selectSessionStore() — Lets the user start a new session or
    // resume an existing one from the sessions/ directory.
    // ============================================================
    private static SessionStore selectSessionStore(Scanner scanner, ConversationContext context) {
        while (true) {
            System.out.println();
            System.out.println("Session options:");
            System.out.println("  1) Start new session");
            System.out.println("  2) Resume existing session");
            System.out.print("Choose 1 or 2: ");
            String choice = scanner.nextLine().trim();

            if ("1".equals(choice)) {
                SessionStore store = SessionStore.createNewDefaultSession();
                System.out.println("Created new session: " + store.getSessionFile());
                return store;
            }

            if ("2".equals(choice)) {
                List<Path> files = SessionStore.listSessionFiles(SessionStore.defaultSessionsDir());
                if (files.isEmpty()) {
                    System.out.println("No session files found in " + SessionStore.defaultSessionsDir()
                            + ". Starting a new one instead.");
                    SessionStore store = SessionStore.createNewDefaultSession();
                    System.out.println("Created new session: " + store.getSessionFile());
                    return store;
                }

                System.out.println("Available sessions:");
                for (int i = 0; i < files.size(); i++) {
                    System.out.println("  " + (i + 1) + ") " + files.get(i).getFileName());
                }
                System.out.print("Enter session number to resume: ");
                String selected = scanner.nextLine().trim();
                try {
                    int idx = Integer.parseInt(selected) - 1;
                    if (idx < 0 || idx >= files.size()) {
                        System.out.println("Invalid session number.");
                        continue;
                    }
                    Path selectedFile = files.get(idx);
                    SessionStore store = new SessionStore(selectedFile);
                    List<ConversationTurn> turns = store.loadTurns(selectedFile);
                    for (ConversationTurn turn : turns) {
                        context.addTurn(turn);
                    }
                    List<String> syntheses = store.loadSyntheses(selectedFile);
                    for (String synthesis : syntheses) {
                        context.addSynthesis(synthesis);
                    }
                    System.out.println("Resumed: " + selectedFile);
                    System.out.println("Loaded " + turns.size() + " turns and "
                            + syntheses.size() + " synthesis entries.");
                    return store;
                } catch (NumberFormatException e) {
                    System.out.println("Please enter a valid number.");
                }
                continue;
            }

            System.out.println("Please enter 1 or 2.");
        }
    }


    // ============================================================
    // login() — Prompts the user for their name and IDSSO role.
    //
    // This creates a UserSession that determines which functional
    // areas the user can access and tags all their contributions.
    //
    // In a web version, this would be a login form. For now, it's
    // a simple name entry + numbered role menu. No password — this
    // is honest attribution for a university project, not security.
    //
    // RETURNS: a new UserSession with the selected identity and role
    // ============================================================
    private static UserSession login(Scanner scanner) {
        System.out.println();
        System.out.println("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
        System.out.println("\u2551   IDSSO Officer Login                \u2551");
        System.out.println("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");
        System.out.println();

        System.out.print("Enter your name: ");
        String name = scanner.nextLine().trim();
        while (name.isEmpty()) {
            System.out.print("Name cannot be empty. Enter your name: ");
            name = scanner.nextLine().trim();
        }

        System.out.println();
        System.out.println("Select your IDSSO role:");
        OfficerRole[] roles = OfficerRole.values();
        for (int i = 0; i < roles.length; i++) {
            System.out.println("  " + (i + 1) + ". " + roles[i].getDisplayName());
        }

        System.out.println();
        System.out.print("Enter number (1-" + roles.length + "): ");

        OfficerRole selectedRole = null;
        while (selectedRole == null) {
            String input = scanner.nextLine().trim();
            try {
                int choice = Integer.parseInt(input) - 1;
                if (choice >= 0 && choice < roles.length) {
                    selectedRole = roles[choice];
                } else {
                    System.out.print("Invalid choice. Enter number (1-" + roles.length + "): ");
                }
            } catch (NumberFormatException e) {
                System.out.print("Invalid input. Enter number (1-" + roles.length + "): ");
            }
        }

        UserSession session = new UserSession(name, selectedRole);
        System.out.println();
        System.out.println("Logged in as: " + name + " \u2014 " + selectedRole.getDisplayName());
        System.out.println("You have access to " + session.getAccessibleAreas().size()
                + " functional area(s).");

        return session;
    }


    // ============================================================
    // selectFunctionalArea() — Displays the role-filtered menu of
    // functional areas and lets the user pick one.
    //
    // Only shows areas the current user's role can access, grouped
    // by category (Internal Administration, Membership, etc.).
    //
    // RETURNS: the selected FunctionalArea, or null if the user
    //          typed 'switch' (signal to Main loop to re-run login)
    // ============================================================
    private static FunctionalArea selectFunctionalArea(Scanner scanner,
                                                       UserSession session) {
        List<FunctionalArea> areas = session.getAccessibleAreas();

        System.out.println();
        System.out.println("Select a functional area:");

        // Group by category for clean display
        String currentCategory = "";
        for (int i = 0; i < areas.size(); i++) {
            FunctionalArea area = areas.get(i);
            if (!area.getCategory().equals(currentCategory)) {
                currentCategory = area.getCategory();
                System.out.println("  " + currentCategory + ":");
            }
            System.out.println("    " + (i + 1) + ". " + area.getDisplayName());
        }

        System.out.println();
        System.out.print("Enter number (1-" + areas.size() + "): ");
        String input = scanner.nextLine().trim();

        // Allow commands during area selection
        if (input.equalsIgnoreCase("quit")) {
            System.out.println("Goodbye!");
            System.exit(0);
        }
        if (input.equalsIgnoreCase("switch")) {
            return null;  // signal to Main loop to re-run login
        }

        try {
            int choice = Integer.parseInt(input) - 1;
            if (choice >= 0 && choice < areas.size()) {
                return areas.get(choice);
            } else {
                System.out.println("Invalid choice. Please try again.");
                return selectFunctionalArea(scanner, session);
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please try again.");
            return selectFunctionalArea(scanner, session);
        }
    }
}
