package collab;

import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        configureHiDpi();

        boolean cliMode = Arrays.stream(args).anyMatch("--cli"::equals);
        if (!cliMode) {
            MainGui.launch();
            return;
        }

        Config config = new Config("config.properties");
        HttpClient httpClient = HttpClient.newHttpClient();
        int maxTokens = config.getMaxResponseTokens();

        List<LlmClient> clients = List.of(
            new AnthropicClient(httpClient, config.getClaudeUrl(), config.getClaudeKey(),
                    config.getClaudeModel(), maxTokens),
            new OpenAiClient(httpClient, config.getOpenAiUrl(), config.getOpenAiKey(),
                    config.getOpenAiModel(), maxTokens),
            new GeminiClient(httpClient, config.getGeminiKey(),
                    config.getGeminiModel(), maxTokens)
        );

        Profiles profiles = Profiles.loadOrDefault();
        List<Profiles.Agent> agents = profiles.agents();
        List<Profiles.Stakeholder> stakeholders = profiles.stakeholders();
        if (agents.size() < 3) {
            System.err.println("profiles.json needs at least 3 agents; using defaults.");
            agents = Profiles.defaults().agents();
        }
        // The CLI only runs with the first 3 agents to match the 3 clients.
        agents = agents.subList(0, 3);

        ConversationContext context = new ConversationContext(config.getMaxHistoryChars());
        Scanner scanner = new Scanner(System.in);
        SessionStore sessionStore = selectSessionStore(scanner, context);
        ContextController ctxController = new ContextController();
        PromptBuilder promptBuilder = new PromptBuilder(context, ctxController, profiles.teamContext());

        Maestro maestro = new Maestro(clients, agents, promptBuilder, context,
                config.getDebateRounds(), sessionStore);

        int cycleCount = 0;
        int activeIdx = 0;

        System.out.println("==========================================");
        System.out.println("  AI Collaboration Platform v0.5");
        System.out.println("  Each cycle = " + maestro.getApiCallCount() + " API calls.");
        System.out.println("  Session: " + sessionStore.getSessionFile());
        System.out.println("==========================================");

        activeIdx = selectStakeholder(scanner, stakeholders, activeIdx);

        while (true) {
            Profiles.Stakeholder active = stakeholders.get(activeIdx);
            System.out.println();
            System.out.println("[" + active.name() + " \u2014 " + active.role() + "]");
            if (context.getCycleCount() > 0) {
                System.out.println("(Panel context: " + context.getCycleCount() + " previous cycle"
                        + (context.getCycleCount() == 1 ? "" : "s") + ")");
            }
            System.out.println("(Commands: 'switch', 'quit')");
            System.out.println("Enter your prompt (type SEND on its own line to submit):");

            StringBuilder buf = new StringBuilder();
            while (true) {
                String line = scanner.nextLine();
                if (buf.isEmpty()) {
                    if (line.trim().equalsIgnoreCase("quit")) { buf.append("__QUIT__"); break; }
                    if (line.trim().equalsIgnoreCase("switch")) { buf.append("__SWITCH__"); break; }
                }
                if (line.trim().equalsIgnoreCase("SEND")) break;
                if (buf.length() > 0) buf.append("\n");
                buf.append(line);
            }

            String prompt = buf.toString().trim();
            if ("__QUIT__".equals(prompt)) {
                System.out.println("Bye. Ran " + cycleCount + " cycles.");
                break;
            }
            if ("__SWITCH__".equals(prompt)) {
                activeIdx = selectStakeholder(scanner, stakeholders, activeIdx);
                continue;
            }
            if (prompt.isEmpty()) {
                System.out.println("(Empty prompt.)");
                continue;
            }

            System.out.print("Run debate (" + maestro.getApiCallCount() + " API calls)? (y/n): ");
            String confirm = scanner.nextLine().trim().toLowerCase();
            if (!confirm.equals("y") && !confirm.equals("yes")) {
                System.out.println("Cancelled.");
                continue;
            }

            maestro.runDebate(prompt, active);
            cycleCount++;

            System.out.println();
            System.out.println("Cycle " + cycleCount + " complete.");
            System.out.print("Press Enter to continue (or 'quit'): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("quit")) break;
        }
        scanner.close();
    }

    private static SessionStore selectSessionStore(Scanner scanner, ConversationContext context) {
        while (true) {
            System.out.println();
            System.out.println("Session: 1) new   2) resume existing");
            System.out.print("Choose: ");
            String choice = scanner.nextLine().trim();
            if ("1".equals(choice)) {
                SessionStore s = SessionStore.createNew();
                System.out.println("New session: " + s.getSessionFile());
                return s;
            }
            if ("2".equals(choice)) {
                var files = SessionStore.listSessionFiles();
                if (files.isEmpty()) {
                    System.out.println("No sessions found, starting new.");
                    SessionStore s = SessionStore.createNew();
                    System.out.println("New session: " + s.getSessionFile());
                    return s;
                }
                for (int i = 0; i < files.size(); i++) {
                    System.out.println("  " + (i + 1) + ") " + files.get(i).getFileName());
                }
                System.out.print("Session number: ");
                try {
                    int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
                    if (idx < 0 || idx >= files.size()) { System.out.println("Invalid."); continue; }
                    SessionStore s = new SessionStore(files.get(idx));
                    for (String syn : s.loadSyntheses()) context.addSynthesis(syn);
                    System.out.println("Resumed: " + files.get(idx));
                    return s;
                } catch (NumberFormatException e) {
                    System.out.println("Not a number.");
                }
            }
        }
    }

    private static int selectStakeholder(Scanner scanner,
                                         List<Profiles.Stakeholder> stakeholders,
                                         int current) {
        System.out.println();
        System.out.println("--- Select Active Stakeholder ---");
        for (int i = 0; i < stakeholders.size(); i++) {
            Profiles.Stakeholder p = stakeholders.get(i);
            System.out.println("  " + (i + 1) + ". " + p.name() + " \u2014 " + p.role()
                    + " (" + p.focusArea() + ")");
        }
        System.out.print("Enter number (1-" + stakeholders.size() + "): ");
        try {
            int choice = Integer.parseInt(scanner.nextLine().trim()) - 1;
            if (choice >= 0 && choice < stakeholders.size()) {
                Profiles.Stakeholder sel = stakeholders.get(choice);
                System.out.println("Active: " + sel.name() + " \u2014 " + sel.role());
                return choice;
            }
        } catch (NumberFormatException e) {
            // fallthrough
        }
        System.out.println("Keeping current: " + stakeholders.get(current).name());
        return current;
    }

    private static void configureHiDpi() {
        setIfAbsent("sun.java2d.uiScale.enabled", "true");
        setIfAbsent("sun.java2d.dpiaware", "true");
        setIfAbsent("awt.useSystemAAFontSettings", "on");
        setIfAbsent("swing.aatext", "true");
    }

    private static void setIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) System.setProperty(key, value);
    }
}
