package collab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

// API keys + tuning from config.properties, plus load/save helpers for
// context.txt (the single free-form context blob).
public class Config {

    private static final Path CONTEXT = Path.of("context.txt");
    private static final String[] REQUIRED = {"claude.key", "openai.key", "gemini.key"};

    private final Properties props = new Properties();

    public Config(String filename) throws IOException {
        File f = new File(filename);
        if (!f.exists()) {
            throw new IOException(filename + " not found. "
                    + "Copy config.properties.example to " + filename + " and fill in API keys.");
        }
        try (var fis = new FileInputStream(f)) { props.load(fis); }
    }

    public String claudeKey()   { return props.getProperty("claude.key"); }
    public String claudeModel() { return props.getProperty("claude.model", "claude-opus-4-6"); }
    public String claudeUrl()   { return props.getProperty("claude.url", "https://api.anthropic.com/v1/messages"); }
    public String openaiKey()   { return props.getProperty("openai.key"); }
    public String openaiModel() { return props.getProperty("openai.model", "gpt-5.4-mini"); }
    public String openaiUrl()   { return props.getProperty("openai.url", "https://api.openai.com/v1/chat/completions"); }
    public String geminiKey()   { return props.getProperty("gemini.key"); }
    public String geminiModel() { return props.getProperty("gemini.model", "gemini-3.1-pro-preview"); }
    public int maxTokens()      { return Integer.parseInt(props.getProperty("max.response.tokens", "8192")); }
    public int debateRounds()   { return Integer.parseInt(props.getProperty("debate.rounds", "1")); }

    public boolean hasAllKeys() {
        for (String k : REQUIRED) {
            String v = props.getProperty(k);
            if (v == null || v.isBlank() || v.startsWith("YOUR_")) return false;
        }
        return true;
    }

    public static String loadContext() {
        try { return Files.exists(CONTEXT) ? Files.readString(CONTEXT) : ""; }
        catch (IOException e) { return ""; }
    }

    public static void saveContext(String text) {
        try { Files.writeString(CONTEXT, text == null ? "" : text); }
        catch (IOException e) { System.err.println("[Config] saveContext: " + e.getMessage()); }
    }
}
