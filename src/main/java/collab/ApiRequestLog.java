package collab;

// ============================================================
// ApiRequestLog.java — Append-only JSONL log of every API
// request payload sent to any LLM provider.
//
// WHY THIS EXISTS:
// The debate flow embeds peer panelist responses in the user
// prompt during Phase 2 "reactions." Users previously had no
// visibility into what text was actually shipped to each model —
// they only saw final outputs. This log captures the exact
// system instruction + messages + max-tokens shape of every
// request so the user can audit why a panelist said what it did.
//
// Structure mirrors ContextChangeLog: one JSON line per record,
// tolerant of malformed lines, lazily read on demand.
// ============================================================

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ApiRequestLog {

    private static final String FILE_NAME = "api_request_log.jsonl";
    private static final Gson GSON = new GsonBuilder().create();

    private final Path filePath;

    public ApiRequestLog() {
        this(Path.of(FILE_NAME));
    }

    public ApiRequestLog(Path filePath) {
        this.filePath = filePath;
    }

    /** Appends a request record to the log. */
    public void append(RequestRecord record) {
        try {
            String line = GSON.toJson(record) + "\n";
            Files.writeString(filePath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[ApiRequestLog] Failed to append: " + e.getMessage());
        }
    }

    /** Reads all records from the log file. */
    public List<RequestRecord> readAll() {
        List<RequestRecord> records = new ArrayList<>();
        if (!Files.exists(filePath)) return records;
        try {
            List<String> lines = Files.readAllLines(filePath);
            for (String line : lines) {
                if (line.isBlank()) continue;
                try {
                    RequestRecord record = GSON.fromJson(line, RequestRecord.class);
                    if (record != null) records.add(record);
                } catch (Exception e) {
                    // skip malformed lines
                }
            }
        } catch (IOException e) {
            System.err.println("[ApiRequestLog] Failed to read: " + e.getMessage());
        }
        return records;
    }

    /** Returns the N most recent records (newest last). */
    public List<RequestRecord> getRecent(int n) {
        List<RequestRecord> all = readAll();
        if (all.size() <= n) return all;
        return all.subList(all.size() - n, all.size());
    }

    /** Returns only records for a given cycle number. */
    public List<RequestRecord> getForCycle(int cycle) {
        List<RequestRecord> result = new ArrayList<>();
        for (RequestRecord r : readAll()) {
            if (r.cycle() == cycle) result.add(r);
        }
        return result;
    }

    /**
     * A single request record.
     *
     * @param timestamp          ISO-8601 instant the request was assembled
     * @param cycle              debate cycle this call belongs to
     * @param phase              "phase1" | "phase2-round-N" | "phase3"
     * @param model              display name of the target model ("Claude", "GPT", "Gemini")
     * @param provider           provider family ("anthropic", "openai", "google")
     * @param systemInstruction  the system instruction sent (may be null for multi-turn follow-ups)
     * @param messages           the ordered message list sent (role + content)
     * @param maxTokens          the max_tokens value on the request
     * @param stateId            provider state id used for stateful chaining, if any
     */
    public record RequestRecord(
            String timestamp,
            int cycle,
            String phase,
            String model,
            String provider,
            String systemInstruction,
            List<ChatMessage> messages,
            int maxTokens,
            String stateId
    ) {
        public static RequestRecord from(int cycle, String phase, String model, String provider,
                                         LlmRequest req, String stateId) {
            return new RequestRecord(
                    Instant.now().toString(),
                    cycle,
                    phase,
                    model,
                    provider,
                    req.systemInstruction(),
                    req.messages(),
                    req.maxTokens(),
                    stateId
            );
        }
    }
}
