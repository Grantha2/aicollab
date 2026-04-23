package collab;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws IOException {
        for (String k : new String[]{"sun.java2d.uiScale.enabled", "sun.java2d.dpiaware",
                                      "awt.useSystemAAFontSettings", "swing.aatext"}) {
            if (System.getProperty(k) == null) System.setProperty(k, "true");
        }
        if (!Arrays.asList(args).contains("--cli")) { MainGui.launch(); return; }

        Config config = new Config("config.properties");
        if (!config.hasAllKeys()) {
            System.err.println("config.properties missing keys — fill them in and re-run.");
            return;
        }
        Maestro maestro = buildMaestro(config);
        maestro.setListener((kind, model, perspective, text) -> {
            if ("status".equals(kind)) System.out.println("[" + text + "]");
            else System.out.println("\n--- " + model
                    + (perspective.isEmpty() ? "" : " (" + perspective + ")") + " ---\n" + text);
        });
        System.out.println("AI Collaboration Platform (CLI). "
                + maestro.apiCallCount() + " API calls per cycle. Type 'SEND' to submit, 'quit' to exit.");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\nEnter prompt:");
            StringBuilder buf = new StringBuilder();
            while (true) {
                String line = scanner.nextLine();
                if (buf.isEmpty() && line.trim().equalsIgnoreCase("quit")) return;
                if (line.trim().equalsIgnoreCase("SEND")) break;
                if (buf.length() > 0) buf.append("\n");
                buf.append(line);
            }
            String prompt = buf.toString().trim();
            if (!prompt.isEmpty()) maestro.runDebate(prompt);
        }
    }

    static Maestro buildMaestro(Config config) {
        HttpClient http = HttpClient.newHttpClient();
        int max = config.maxTokens();
        List<LlmClient> clients = List.of(
            new AnthropicClient(http, config.claudeUrl(), config.claudeKey(), config.claudeModel(), max),
            new OpenAiClient(http, config.openaiUrl(), config.openaiKey(), config.openaiModel(), max),
            new GeminiClient(http, config.geminiKey(), config.geminiModel(), max)
        );
        List<Agent> agents = Agent.loadAll();
        if (agents.size() > 3) agents = agents.subList(0, 3);
        if (agents.size() < 3) agents = Agent.defaults();
        return new Maestro(clients, agents, Config.loadContext(), config.debateRounds());
    }
}
