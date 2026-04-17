package collab;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

// Appends one JSON object per line to sessions/session-<ts>.jsonl.
// Stores synthesis entries and (optionally) per-phase turns.
public class SessionStore {

    private static final String PREFIX = "session-";
    private static final String SUFFIX = ".jsonl";

    private final Path sessionFile;

    public SessionStore(Path sessionFile) {
        this.sessionFile = sessionFile;
        try {
            Path parent = sessionFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            if (!Files.exists(sessionFile)) Files.createFile(sessionFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare " + sessionFile, e);
        }
    }

    public static Path defaultSessionsDir() {
        return Path.of("sessions");
    }

    public static SessionStore createNew() {
        String ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
        return new SessionStore(defaultSessionsDir().resolve(PREFIX + ts + SUFFIX));
    }

    public static List<Path> listSessionFiles() {
        Path dir = defaultSessionsDir();
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(PREFIX) && name.endsWith(SUFFIX);
                    })
                    .sorted(Comparator.reverseOrder())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public Path getSessionFile() {
        return sessionFile;
    }

    public void appendTurn(int cycle, String phase, String model, String content) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "turn");
        obj.addProperty("cycle", cycle);
        obj.addProperty("phase", phase);
        obj.addProperty("model", model);
        obj.addProperty("content", content);
        obj.addProperty("epochMillis", System.currentTimeMillis());
        appendLine(obj.toString());
    }

    public void appendSynthesis(int cycle, String synthesis) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "synthesis");
        obj.addProperty("cycle", cycle);
        obj.addProperty("synthesis", synthesis);
        obj.addProperty("epochMillis", System.currentTimeMillis());
        appendLine(obj.toString());
    }

    public List<String> loadSyntheses() {
        List<String> out = new ArrayList<>();
        if (!Files.exists(sessionFile)) return out;
        try {
            for (String line : Files.readAllLines(sessionFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                if (obj.has("type") && "synthesis".equals(obj.get("type").getAsString())) {
                    out.add(obj.get("synthesis").getAsString());
                }
            }
        } catch (IOException e) {
            System.err.println("[SessionStore] Failed to read " + sessionFile + ": " + e.getMessage());
        }
        return out;
    }

    private void appendLine(String line) {
        try (BufferedWriter w = Files.newBufferedWriter(
                sessionFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(line);
            w.newLine();
        } catch (IOException e) {
            throw new RuntimeException("Failed writing to " + sessionFile, e);
        }
    }
}
