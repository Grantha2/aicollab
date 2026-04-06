package collab;

// ============================================================
// ContextChangeLog.java — Append-only JSONL log of every
// context mutation (field update, approval, rejection).
//
// WHAT THIS CLASS DOES (one sentence):
// Records every change to organization context as a JSON line
// in context_changelog.jsonl for audit trail and history.
// ============================================================

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ContextChangeLog {

    private static final String FILE_NAME = "context_changelog.jsonl";
    private static final Gson GSON = new GsonBuilder().create();

    private final Path filePath;

    public ContextChangeLog() {
        this(Path.of(FILE_NAME));
    }

    public ContextChangeLog(Path filePath) {
        this.filePath = filePath;
    }

    /** Appends a change record to the log. */
    public void append(ChangeRecord record) {
        try {
            String line = GSON.toJson(record) + "\n";
            Files.writeString(filePath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[ContextChangeLog] Failed to append: " + e.getMessage());
        }
    }

    /** Reads the most recent N records from the log (newest last). */
    public List<ChangeRecord> getRecent(int n) {
        List<ChangeRecord> all = readAll();
        if (all.size() <= n) return all;
        return all.subList(all.size() - n, all.size());
    }

    /** Reads all records since a given timestamp. */
    public List<ChangeRecord> getChangesSince(Instant since) {
        List<ChangeRecord> result = new ArrayList<>();
        for (ChangeRecord record : readAll()) {
            try {
                Instant ts = Instant.parse(record.timestamp());
                if (!ts.isBefore(since)) {
                    result.add(record);
                }
            } catch (Exception e) {
                // skip malformed timestamps
            }
        }
        return result;
    }

    /** Reads all records from the log file. */
    public List<ChangeRecord> readAll() {
        List<ChangeRecord> records = new ArrayList<>();
        if (!Files.exists(filePath)) return records;
        try {
            List<String> lines = Files.readAllLines(filePath);
            for (String line : lines) {
                if (line.isBlank()) continue;
                try {
                    ChangeRecord record = GSON.fromJson(line, ChangeRecord.class);
                    if (record != null) records.add(record);
                } catch (Exception e) {
                    // skip malformed lines
                }
            }
        } catch (IOException e) {
            System.err.println("[ContextChangeLog] Failed to read: " + e.getMessage());
        }
        return records;
    }

    /**
     * A single change record in the log.
     *
     * @param timestamp ISO-8601 instant
     * @param field     the field name that changed (e.g., "topPriorities")
     * @param oldValue  previous value (may be empty)
     * @param newValue  new value
     * @param source    who/what made the change ("user_edit", "daily_update", "reconciliation")
     * @param action    what happened ("update", "approve", "reject", "auto_apply")
     */
    public record ChangeRecord(
        String timestamp,
        String field,
        String oldValue,
        String newValue,
        String source,
        String action
    ) {
        public static ChangeRecord of(String field, String oldValue, String newValue, String source, String action) {
            return new ChangeRecord(Instant.now().toString(), field, oldValue, newValue, source, action);
        }
    }
}
