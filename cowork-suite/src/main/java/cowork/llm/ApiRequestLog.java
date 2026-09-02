package cowork.llm;

// ApiRequestLog — append-only JSONL audit trail of every request shape
// (system, messages, max_tokens, tools) a task ships to Claude, so users can
// see exactly what context produced an answer. One JSON object per line,
// malformed lines skipped on read, key-redacted on write, rotated at 5 MB.

import com.google.gson.Gson;

import cowork.config.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ApiRequestLog {

    static final String FILE_NAME = "api_request_log.jsonl";
    static final String ROTATED_FILE_NAME = "api_request_log.1.jsonl";
    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Gson GSON = new Gson();

    private final Path filePath;

    public ApiRequestLog() {
        this(AppPaths.data(FILE_NAME));
    }

    public ApiRequestLog(Path filePath) {
        this.filePath = filePath;
        rotateIfOversized();
    }

    public Path file() {
        return filePath;
    }

    private void rotateIfOversized() {
        try {
            if (Files.exists(filePath) && Files.size(filePath) > MAX_BYTES) {
                Files.move(filePath, filePath.resolveSibling(ROTATED_FILE_NAME),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[ApiRequestLog] Failed to rotate: " + e.getMessage());
        }
    }

    /** Appends one record; every free-text field is key-redacted first. */
    public void append(RequestRecord record) {
        try {
            Path parent = filePath.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(filePath, GSON.toJson(record.redacted()) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[ApiRequestLog] Failed to append: " + e.getMessage());
        }
    }

    public List<RequestRecord> readAll() {
        List<RequestRecord> records = new ArrayList<>();
        if (!Files.exists(filePath)) return records;
        try {
            for (String line : Files.readAllLines(filePath)) {
                if (line.isBlank()) continue;
                try {
                    RequestRecord record = GSON.fromJson(line, RequestRecord.class);
                    if (record != null) records.add(record);
                } catch (RuntimeException e) {
                    // skip malformed lines
                }
            }
        } catch (IOException e) {
            System.err.println("[ApiRequestLog] Failed to read: " + e.getMessage());
        }
        return records;
    }

    /** The N most recent records, oldest first. */
    public List<RequestRecord> getRecent(int n) {
        List<RequestRecord> all = readAll();
        return all.size() <= n ? all : all.subList(all.size() - n, all.size());
    }

    public List<RequestRecord> getForTask(String taskId) {
        List<RequestRecord> result = new ArrayList<>();
        for (RequestRecord r : readAll()) {
            if (Objects.equals(r.taskId(), taskId)) result.add(r);
        }
        return result;
    }

    /**
     * One logged request. `taskId` names the agentic task or button, `step`
     * the stage within it (e.g. "draft", "tool-loop"), `toolsSummary` the
     * comma-separated tool names offered on the request ("" when none).
     */
    public record RequestRecord(
            String timestamp,
            String taskId,
            String step,
            String model,
            String provider,
            String systemInstruction,
            List<ChatMessage> messages,
            int maxTokens,
            String stateId,
            String toolsSummary
    ) {
        public static RequestRecord from(String taskId, String step, String model, String provider,
                                         LlmRequest req, String stateId) {
            return new RequestRecord(Instant.now().toString(), taskId, step, model, provider,
                    req.systemInstruction(), req.messages(), req.maxTokens(), stateId,
                    summariseTools(req));
        }

        RequestRecord redacted() {
            List<ChatMessage> safe = new ArrayList<>();
            if (messages != null) {
                for (ChatMessage m : messages) {
                    safe.add(new ChatMessage(m.role(), AnthropicClient.redact(m.content())));
                }
            }
            return new RequestRecord(timestamp, taskId, step, model, provider,
                    AnthropicClient.redact(systemInstruction), safe, maxTokens, stateId, toolsSummary);
        }

        private static String summariseTools(LlmRequest req) {
            StringBuilder sb = new StringBuilder();
            for (ToolSchema t : req.tools()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(t.name());
            }
            return sb.toString();
        }
    }
}
