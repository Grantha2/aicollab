package cowork.config;

// ConfigTest — missing-file message, placeholder detection, defaults and the
// save()/reload round trip, all against a temp directory.

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @TempDir
    Path tmp;

    private Path write(String name, String content) throws IOException {
        return Files.writeString(tmp.resolve(name), content);
    }

    @Test
    void missingFileThrowsWithExactMessage() {
        Path missing = tmp.resolve("config.properties");
        IOException ex = assertThrows(IOException.class, () -> new Config(missing));
        assertEquals(missing + " not found. Copy config.properties.example to config.properties "
                + "and add your Anthropic API key.", ex.getMessage());
        assertThrows(IOException.class, () -> Config.load(missing));
    }

    @Test
    void placeholderOrBlankKeyIsNotAKey() throws IOException {
        assertFalse(new Config(write("a.properties", "claude.key=YOUR_ANTHROPIC_KEY_HERE\n")).hasClaudeKey());
        assertFalse(new Config(write("b.properties", "claude.key=   \n")).hasClaudeKey());
        assertFalse(new Config(write("c.properties", "claude.model=x\n")).hasClaudeKey());
        assertTrue(new Config(write("d.properties", "claude.key=sk-ant-realkey1234567890 \n")).hasClaudeKey());
    }

    @Test
    void defaultsApplyWhenKeysAbsentBlankOrMalformed() throws IOException {
        Config c = new Config(write("e.properties",
                "claude.key=sk-x\nclaude.model=\nmax.response.tokens=abc\n"));
        assertEquals("claude-opus-5", c.getClaudeModel());
        assertEquals("https://api.anthropic.com/v1/messages", c.getClaudeUrl());
        assertEquals(16000, c.getMaxResponseTokens());
        assertEquals("fixture", c.getProperty("room.availability.mode", "fixture"));
        assertEquals("sk-x", c.getClaudeKey());
    }

    @Test
    void saveThenReloadRoundTrips() throws IOException {
        Path f = write("f.properties", "claude.key=sk-ant-abcdefghijklmnop\n");
        Config c = new Config(f);
        c.setProperty("claude.model", "claude-sonnet-5");
        c.setProperty("max.response.tokens", "4096");
        c.setProperty("pdf.output.dir", "/tmp/out");
        c.save();

        Config again = Config.load(f);
        assertEquals(f, again.file());
        assertEquals("claude-sonnet-5", again.getClaudeModel());
        assertEquals(4096, again.getMaxResponseTokens());
        assertEquals("sk-ant-abcdefghijklmnop", again.getClaudeKey());
        assertEquals("/tmp/out", again.getProperty("pdf.output.dir", "x"));
        assertEquals("claude-sonnet-5", again.getProperties().getProperty("claude.model"));
    }

    @Test
    void defaultPathFollowsAppPaths() {
        System.setProperty("cowork.home", tmp.toString());
        try {
            assertEquals(tmp.toAbsolutePath().normalize().resolve("config.properties"), Config.defaultPath());
        } finally {
            System.clearProperty("cowork.home");
        }
    }
}
