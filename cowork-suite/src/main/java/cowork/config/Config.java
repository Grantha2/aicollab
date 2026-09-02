package cowork.config;

// Config — typed view over config.properties: Claude key/model/url, the
// response-token budget, and ad-hoc workflow keys via getProperty().
// Pure file I/O: a missing file is an IOException the GUI turns into a
// first-launch dialog. No console prompts, no System.exit.

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Config {

    public static final String DEFAULT_MODEL = "claude-opus-5";
    public static final String DEFAULT_URL = "https://api.anthropic.com/v1/messages";
    public static final int DEFAULT_MAX_TOKENS = 16000;

    private final Properties props = new Properties();
    private final Path file;

    public Config(Path file) throws IOException {
        this.file = file;
        if (!Files.isRegularFile(file)) {
            throw new IOException(file + " not found. Copy config.properties.example to "
                    + "config.properties and add your Anthropic API key.");
        }
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
    }

    public static Config load(Path file) throws IOException { return new Config(file); }

    public static Path defaultPath() { return AppPaths.data("config.properties"); }

    public Path file() { return file; }

    public String getClaudeKey() {
        String key = props.getProperty("claude.key");
        return key == null ? null : key.trim();
    }

    public String getClaudeModel() { return getProperty("claude.model", DEFAULT_MODEL); }

    public String getClaudeUrl() { return getProperty("claude.url", DEFAULT_URL); }

    public int getMaxResponseTokens() {
        try {
            return Integer.parseInt(getProperty("max.response.tokens", "").trim());
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_TOKENS;
        }
    }

    /** True when a real key is configured — not blank, not the YOUR_… placeholder. */
    public boolean hasClaudeKey() {
        String key = getClaudeKey();
        return key != null && !key.isBlank() && !key.startsWith("YOUR_");
    }

    /** Returns the default when the key is missing or blank, so callers never null-check. */
    public String getProperty(String key, String defaultValue) {
        String v = props.getProperty(key);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    public void setProperty(String key, String value) { props.setProperty(key, value); }

    /** Defensive copy for editors that render every key. */
    public Properties getProperties() {
        Properties copy = new Properties();
        copy.putAll(props);
        return copy;
    }

    public void save() throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "Cowork Suite settings - contains your API key, never commit this file.");
        }
    }
}
