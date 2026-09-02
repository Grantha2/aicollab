package cowork.context;

import com.google.gson.Gson;
import cowork.config.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only JSONL audit trail of every context mutation (update, approve, reject,
 * auto_apply) and of task-generated signals such as the outbound-messages run marker.
 * One Gson-encoded ChangeRecord per line in context_changelog.jsonl; malformed lines are
 * skipped on read so one bad line never hides the rest of the history.
 */
public class ContextChangeLog {

    private static final String FILE_NAME = "context_changelog.jsonl";
    private static final Gson GSON = new Gson();

    private final Path filePath;

    public ContextChangeLog() {
        this(AppPaths.data(FILE_NAME));
    }

    public ContextChangeLog(Path filePath) {
        this.filePath = filePath;
    }

    public void append(ChangeRecord record) {
        try {
            if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, GSON.toJson(record) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[ContextChangeLog] Failed to append: " + e.getMessage());
        }
    }

    /** The most recent n records, oldest first. */
    public List<ChangeRecord> getRecent(int n) {
        List<ChangeRecord> all = readAll();
        return all.size() <= n ? all : all.subList(all.size() - n, all.size());
    }

    /** Records whose timestamp is at or after the given instant. */
    public List<ChangeRecord> getChangesSince(Instant since) {
        List<ChangeRecord> result = new ArrayList<>();
        for (ChangeRecord record : readAll()) {
            try {
                if (!Instant.parse(record.timestamp()).isBefore(since)) result.add(record);
            } catch (Exception e) {
                // unparseable timestamp: leave the record out
            }
        }
        return result;
    }

    public List<ChangeRecord> readAll() {
        List<ChangeRecord> records = new ArrayList<>();
        if (!Files.exists(filePath)) return records;
        try {
            for (String line : Files.readAllLines(filePath)) {
                if (line.isBlank()) continue;
                try {
                    ChangeRecord record = GSON.fromJson(line, ChangeRecord.class);
                    if (record != null) records.add(record);
                } catch (Exception e) {
                    // malformed line: skip it
                }
            }
        } catch (IOException e) {
            System.err.println("[ContextChangeLog] Failed to read: " + e.getMessage());
        }
        return records;
    }

    /**
     * One log line. source is who or what made the change ("user_edit", "daily_update",
     * "reconciliation", "outbound-messages"); action is what happened ("update", "approve",
     * "reject", "auto_apply", "generated").
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
