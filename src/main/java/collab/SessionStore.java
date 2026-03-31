package collab;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class SessionStore {

    private static final String FILE_PREFIX = "session-";
    private static final String FILE_SUFFIX = ".jsonl";

    private final Path sessionFile;

    public SessionStore(Path sessionFile) {
        this.sessionFile = sessionFile;
        ensureParentDirectoryExists();
        ensureFileExists();
    }

    public static Path defaultSessionsDir() {
        return Path.of("sessions");
    }

    public static SessionStore createNewDefaultSession() {
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                .replace(":", "-");
        Path file = defaultSessionsDir().resolve(FILE_PREFIX + timestamp + FILE_SUFFIX);
        return new SessionStore(file);
    }

    public static List<Path> listSessionFiles(Path sessionsDir) {
        if (!Files.isDirectory(sessionsDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(sessionsDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX);
                    })
                    .sorted(Comparator.reverseOrder())
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list session files in " + sessionsDir, e);
        }
    }

    public Path getSessionFile() {
        return sessionFile;
    }

    public void appendTurn(ConversationTurn turn) {
        String json = "{\"type\":\"turn\","
                + "\"cycle\":" + turn.cycle() + ","
                + "\"phase\":\"" + escapeForJson(turn.phase()) + "\","
                + "\"model\":\"" + escapeForJson(turn.model()) + "\","
                + "\"role\":\"" + escapeForJson(turn.role()) + "\","
                + "\"content\":\"" + escapeForJson(turn.content()) + "\","
                + "\"epochMillis\":" + turn.epochMillis()
                + "}";
        appendLine(json);
    }

    public void appendSynthesis(int cycle, String synthesis) {
        String json = "{\"type\":\"synthesis\","
                + "\"cycle\":" + cycle + ","
                + "\"synthesis\":\"" + escapeForJson(synthesis) + "\","
                + "\"epochMillis\":" + System.currentTimeMillis()
                + "}";
        appendLine(json);
    }

    public List<ConversationTurn> loadTurns(Path file) {
        List<ConversationTurn> turns = new ArrayList<>();
        for (String line : readAllLines(file)) {
            try {
                if (!"turn".equals(extractJsonString(line, "type"))) {
                    continue;
                }
                turns.add(new ConversationTurn(
                        (int) extractJsonLong(line, "cycle"),
                        extractJsonString(line, "phase"),
                        extractJsonString(line, "model"),
                        extractJsonString(line, "role"),
                        extractJsonString(line, "content"),
                        extractJsonLong(line, "epochMillis")
                ));
            } catch (RuntimeException parseError) {
                System.out.println("[SessionStore] Skipping malformed turn line: " + parseError.getMessage());
            }
        }
        return turns;
    }

    public List<String> loadSyntheses(Path file) {
        List<String> syntheses = new ArrayList<>();
        for (String line : readAllLines(file)) {
            try {
                if (!"synthesis".equals(extractJsonString(line, "type"))) {
                    continue;
                }
                syntheses.add(extractJsonString(line, "synthesis"));
            } catch (RuntimeException parseError) {
                System.out.println("[SessionStore] Skipping malformed synthesis line: " + parseError.getMessage());
            }
        }
        return syntheses;
    }

    private List<String> readAllLines(Path file) {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed reading session file " + file, e);
        }
    }

    private void appendLine(String line) {
        try (BufferedWriter writer = Files.newBufferedWriter(
                sessionFile,
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND)) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException("Failed writing session event to " + sessionFile, e);
        }
    }

    private void ensureParentDirectoryExists() {
        try {
            Path parent = sessionFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create session directory for " + sessionFile, e);
        }
    }

    private void ensureFileExists() {
        try {
            if (!Files.exists(sessionFile)) {
                Files.createFile(sessionFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create session file " + sessionFile, e);
        }
    }

    private static String escapeForJson(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJsonString(String input) {
        StringBuilder out = new StringBuilder(input.length());
        int i = 0;
        while (i < input.length()) {
            char ch = input.charAt(i);
            if (ch != '\\' || i + 1 >= input.length()) {
                out.append(ch);
                i++;
                continue;
            }

            char next = input.charAt(i + 1);
            switch (next) {
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'n' -> out.append('\n');
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                default -> {
                    out.append('\\');
                    out.append(next);
                }
            }
            i += 2;
        }
        return out.toString();
    }

    private static String extractJsonString(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            String markerWithSpace = "\"" + fieldName + "\": \"";
            start = json.indexOf(markerWithSpace);
            if (start < 0) {
                throw new IllegalArgumentException("Field '" + fieldName + "' not found in " + json);
            }
            start += markerWithSpace.length();
        } else {
            start += marker.length();
        }

        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"') {
                int backslashes = 0;
                int check = end - 1;
                while (check >= start && json.charAt(check) == '\\') {
                    backslashes++;
                    check--;
                }
                if (backslashes % 2 == 0) {
                    break;
                }
            }
            end++;
        }
        return unescapeJsonString(json.substring(start, end));
    }

    private static long extractJsonLong(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("Field '" + fieldName + "' not found in " + json);
        }
        start += marker.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }
}
