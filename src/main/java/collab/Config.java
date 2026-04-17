package collab;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Scanner;

// Loads API keys and tuning parameters from config.properties.
// On first run (CLI), prompts the user for keys; for GUI first-run,
// a missing key throws and the user is shown a setup message.
public class Config {

    private final Properties props;
    private final String filename;

    public Config(String filename) {
        this.filename = filename;
        this.props = loadOrCreateConfig(filename, true);
    }

    public Config(String filename, boolean interactive) {
        this.filename = filename;
        this.props = loadOrCreateConfig(filename, interactive);
    }

    public String getClaudeKey()   { return props.getProperty("claude.key"); }
    public String getClaudeModel() { return props.getProperty("claude.model", "claude-opus-4-6"); }
    public String getClaudeUrl()   { return props.getProperty("claude.url", "https://api.anthropic.com/v1/messages"); }

    public String getOpenAiKey()   { return props.getProperty("openai.key"); }
    public String getOpenAiModel() { return props.getProperty("openai.model", "gpt-5.4-mini"); }
    public String getOpenAiUrl()   { return props.getProperty("openai.url", "https://api.openai.com/v1/chat/completions"); }

    public String getGeminiKey()   { return props.getProperty("gemini.key"); }
    public String getGeminiModel() { return props.getProperty("gemini.model", "gemini-3.1-pro-preview"); }

    public int getMaxResponseTokens() {
        return Integer.parseInt(props.getProperty("max.response.tokens", "8192"));
    }

    public int getDebateRounds() {
        return Integer.parseInt(props.getProperty("debate.rounds", "1"));
    }

    public int getMaxHistoryChars() {
        return Integer.parseInt(props.getProperty("max.history.chars", "24000"));
    }

    public boolean hasAllKeys() {
        return nonBlank(getClaudeKey()) && nonBlank(getOpenAiKey()) && nonBlank(getGeminiKey());
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank() && !s.startsWith("YOUR_");
    }

    public void setProperty(String key, String value) {
        props.setProperty(key, value);
    }

    public void save() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(new File(filename))) {
            props.store(fos, "API keys \u2014 this file is gitignored, NEVER commit it.");
        }
    }

    private static Properties loadOrCreateConfig(String filename, boolean interactive) {
        Properties props = new Properties();
        File configFile = new File(filename);

        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            } catch (IOException e) {
                System.err.println("ERROR: Could not read " + filename + ": " + e.getMessage());
                System.exit(1);
            }
            return props;
        }

        if (!interactive) {
            return props;
        }

        System.out.println();
        System.out.println("No config found. Paste your API keys:");
        System.out.println("  Anthropic:  https://console.anthropic.com/settings/keys");
        System.out.println("  OpenAI:     https://platform.openai.com/api-keys");
        System.out.println("  Google AI:  https://aistudio.google.com/apikey");
        System.out.println();

        Scanner scanner = new Scanner(System.in);
        props.setProperty("claude.key", promptForKey(scanner, "Anthropic (Claude)"));
        props.setProperty("openai.key", promptForKey(scanner, "OpenAI (GPT)"));
        props.setProperty("gemini.key", promptForKey(scanner, "Google (Gemini)"));
        props.setProperty("claude.model", "claude-opus-4-6");
        props.setProperty("openai.model", "gpt-5.4-mini");
        props.setProperty("gemini.model", "gemini-3.1-pro-preview");
        props.setProperty("max.response.tokens", "8192");
        props.setProperty("debate.rounds", "1");
        props.setProperty("max.history.chars", "24000");

        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            props.store(fos, "API keys \u2014 this file is gitignored, NEVER commit it.");
        } catch (IOException e) {
            System.err.println("ERROR: Could not save " + filename + ": " + e.getMessage());
            System.exit(1);
        }
        System.out.println("Keys saved to " + filename);
        return props;
    }

    private static String promptForKey(Scanner scanner, String providerName) {
        while (true) {
            System.out.print("Enter your " + providerName + " API key: ");
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) return input;
            System.out.println("  (Key cannot be blank.)");
        }
    }
}
