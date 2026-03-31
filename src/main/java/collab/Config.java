package collab;

// ============================================================
// Config.java — Loads API keys and settings from config.properties.
//
// WHAT THIS CLASS DOES (one sentence):
// Reads API keys, model names, and tuning parameters from a file
// so nothing secret is ever hardcoded in source code.
//
// HOW IT FITS THE ARCHITECTURE:
// Config is the first thing Main.java creates. It reads
// config.properties and provides getter methods that the rest
// of the program uses. If the config file doesn't exist, it
// walks the user through an interactive setup — paste your keys,
// they get saved, and you never have to enter them again.
//
// WHY NOT JUST HARDCODE THE KEYS?
// API keys are secrets — like passwords. If they end up in Git,
// anyone who clones the repo gets free access to our paid APIs.
// Even deleting them in a later commit doesn't remove them from
// Git history. By loading from a gitignored file, secrets stay
// on YOUR machine only.
//
// HOW Java Properties WORKS:
// Properties is a built-in Java class (java.util.Properties).
// It reads lines like "key=value" from a text file and stores
// them in a map. You retrieve values with getProperty("key").
// You save with store(). No external libraries needed.
// ============================================================

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Scanner;

public class Config {

    // The Properties object holds all key-value pairs from the file.
    // Think of it as a simple map: "claude.key" -> "sk-ant-..."
    private final Properties props;
    private final String filename;

    // ============================================================
    // Constructor — loads or creates the config file.
    //
    // If config.properties exists: loads it and validates all keys.
    // If it doesn't exist: prompts the user interactively, saves.
    //
    // PARAMETER:
    //   filename — path to the config file (usually "config.properties")
    // ============================================================
    public Config(String filename) {
        this.filename = filename;
        this.props = loadOrCreateConfig(filename);
    }

    // --- API KEY GETTERS ---
    // Each provider needs a key (secret), a model name, and a URL.
    // The URL for Gemini is built dynamically in GeminiClient,
    // so we don't store a full URL for it here.

    public String getClaudeKey()   { return props.getProperty("claude.key"); }
    public String getClaudeModel() { return props.getProperty("claude.model", "claude-opus-4-6"); }
    public String getClaudeUrl()   { return props.getProperty("claude.url", "https://api.anthropic.com/v1/messages"); }

    public String getOpenAiKey()   { return props.getProperty("openai.key"); }
    public String getOpenAiModel() { return props.getProperty("openai.model", "gpt-5.4-mini"); }
    public String getOpenAiUrl()   { return props.getProperty("openai.url", "https://api.openai.com/v1/chat/completions"); }

    public String getGeminiKey()   { return props.getProperty("gemini.key"); }
    public String getGeminiModel() { return props.getProperty("gemini.model", "gemini-3.1-pro-preview"); }
    
   

    // --- TUNING PARAMETERS ---
    // These control how the debate works. Defaults are sensible;
    // advanced users can override them in config.properties.

    /** Maximum tokens each model can return per API call. */
    public int getMaxResponseTokens() {
        return Integer.parseInt(props.getProperty("max.response.tokens", "8192"));
    }

    /** How many rounds of cross-reaction in Phase 2. Default 1. */
    public int getDebateRounds() {
        return Integer.parseInt(props.getProperty("debate.rounds", "1"));
    }

    /** Max characters of conversation history to include in prompts.
     *  Prevents prompts from growing too large and hitting token limits. */
    public int getMaxHistoryChars() {
        return Integer.parseInt(props.getProperty("max.history.chars", "24000"));
    }


    public void setProperty(String key, String value) {
        props.setProperty(key, value);
    }

    public void save() throws IOException {
        props.store(new FileOutputStream(new File(filename)),
                "API keys — this file is gitignored, NEVER commit it.");
    }

    public Properties getProperties() {
        Properties copy = new Properties();
        copy.putAll(props);
        return copy;
    }

    // ============================================================
    // loadOrCreateConfig() — Loads config from file, or walks the
    // user through first-time setup if the file doesn't exist.
    //
    // WHY INTERACTIVE SETUP:
    // New teammates shouldn't have to create config files by hand.
    // They just run the program, paste their keys when asked, and
    // everything is saved automatically. On future runs, the keys
    // load silently from the saved file.
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
            String[] required = {"claude.key", "openai.key", "gemini.key"};
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
            System.out.println("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
            System.out.println("\u2551   First-Time API Key Setup           \u2551");
            System.out.println("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");
            System.out.println();
            System.out.println("No API keys found. Let's set them up!");
            System.out.println("You'll only need to do this once \u2014 keys are saved locally.");
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

            props.setProperty("claude.key", claudeKey);
            props.setProperty("openai.key", openaiKey);
            props.setProperty("gemini.key", geminiKey);

            // Set default model names so the config file is self-documenting
            props.setProperty("claude.model", "claude-opus-4-6");
            props.setProperty("openai.model", "gpt-5.4-mini");
            props.setProperty("gemini.model", "gemini-3.1-pro-preview");
            props.setProperty("max.response.tokens", "8192");
            props.setProperty("debate.rounds", "1");
            props.setProperty("max.history.chars", "24000");

            // Save the keys so the user is never asked again.
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                props.store(fos, "API keys \u2014 this file is gitignored, NEVER commit it.");
            } catch (IOException e) {
                System.err.println("ERROR: Could not save " + filename);
                System.err.println(e.getMessage());
                System.exit(1);
            }

            System.out.println();
            System.out.println("Keys saved to " + filename + " \u2014 you won't be asked again.");
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
            System.out.println("  (Key cannot be blank \u2014 please try again.)");
        }
    }
}
