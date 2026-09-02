package cowork.llm;

// ApiRequestLogTest — log lands under cowork.home, keys are masked on disk and
// on read-back, taskId/step round-trip, malformed lines are skipped, and an
// oversized file is rotated to api_request_log.1.jsonl on construction.

import cowork.llm.ApiRequestLog.RequestRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiRequestLogTest {

    private static final String KEY = "sk-ant-abcdefghijklmnop";

    @TempDir
    Path tmp;

    @BeforeEach
    void pointHomeAtTempDir() {
        System.setProperty("cowork.home", tmp.toString());
    }

    @AfterEach
    void clearHome() {
        System.clearProperty("cowork.home");
    }

    private static RequestRecord record(String taskId, String step, String userText) {
        LlmRequest req = new LlmRequest("System prompt mentioning " + KEY,
                List.of(new ChatMessage("user", userText)), 900,
                List.of(ToolSchema.noArgs("t1", "d"), ToolSchema.noArgs("t2", "d")));
        return RequestRecord.from(taskId, step, "Claude", "anthropic", req, "state-1");
    }

    @Test
    void appendRedactsKeysAndRoundTripsTaskIdAndStep() throws IOException {
        ApiRequestLog log = new ApiRequestLog();
        assertEquals(tmp.toAbsolutePath().normalize().resolve("api_request_log.jsonl"), log.file());

        log.append(record("weekly-report", "draft", "my key is " + KEY + " ok"));

        List<RequestRecord> all = log.readAll();
        assertEquals(1, all.size());
        RequestRecord r = all.get(0);
        assertEquals("weekly-report", r.taskId());
        assertEquals("draft", r.step());
        assertEquals("Claude", r.model());
        assertEquals("anthropic", r.provider());
        assertEquals(900, r.maxTokens());
        assertEquals("state-1", r.stateId());
        assertEquals("t1, t2", r.toolsSummary());
        assertNotNull(r.timestamp());
        assertEquals("user", r.messages().get(0).role());
        assertEquals("my key is sk-*** ok", r.messages().get(0).content());
        assertEquals("System prompt mentioning sk-***", r.systemInstruction());

        String raw = Files.readString(log.file());
        assertFalse(raw.contains(KEY));
        assertEquals(1, raw.strip().split("\n").length);
    }

    @Test
    void filtersByTaskAndReturnsRecentInOrder() {
        ApiRequestLog log = new ApiRequestLog();
        log.append(record("a", "s1", "one"));
        log.append(record("b", "s1", "two"));
        log.append(record("a", "s2", "three"));

        assertEquals(List.of("s1", "s2"), log.getForTask("a").stream().map(RequestRecord::step).toList());
        assertEquals(List.of("two", "three"),
                log.getRecent(2).stream().map(r -> r.messages().get(0).content()).toList());
        assertEquals(3, log.getRecent(10).size());
    }

    @Test
    void skipsMalformedLines() throws IOException {
        ApiRequestLog log = new ApiRequestLog();
        log.append(record("a", "s1", "one"));
        Files.writeString(log.file(), "{not json\n\n", StandardOpenOption.APPEND);
        log.append(record("a", "s2", "two"));
        assertEquals(2, log.readAll().size());
    }

    @Test
    void rotatesOversizedFileOnConstruction() throws IOException {
        Path file = tmp.resolve("api_request_log.jsonl");
        Path rotated = tmp.resolve("api_request_log.1.jsonl");
        byte[] big = new byte[(int) ApiRequestLog.MAX_BYTES + 1];
        Arrays.fill(big, (byte) '\n');
        Files.write(file, big);
        Files.writeString(rotated, "stale\n");

        ApiRequestLog log = new ApiRequestLog();
        assertFalse(Files.exists(file));
        assertEquals(big.length, Files.size(rotated), "rotation overwrites the previous .1 file");

        log.append(record("a", "s1", "fresh"));
        assertEquals(1, log.readAll().size());

        // A small file is left alone.
        new ApiRequestLog();
        assertEquals(1, log.readAll().size());
    }
}
