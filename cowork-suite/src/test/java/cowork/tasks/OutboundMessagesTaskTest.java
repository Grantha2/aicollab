package cowork.tasks;

import cowork.context.ContextChangeLog;
import cowork.context.OrganizationContext;
import cowork.context.ReconciliationService;
import cowork.data.OperationalFeedStore;
import cowork.data.RelationshipStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OutboundMessagesTask leaves the "outbound" signal in the change log the recommendation engine keys on. */
class OutboundMessagesTaskTest {

    static { System.setProperty("java.awt.headless", "true"); }

    @TempDir Path tempDir;

    private OrganizationContext org;
    private ContextChangeLog changeLog;
    private ReconciliationService reconciliation;

    @BeforeEach
    void setUp() {
        System.setProperty("cowork.home", tempDir.toString());
        org = new OrganizationContext();
        changeLog = new ContextChangeLog();
        reconciliation = new ReconciliationService(org, changeLog);
    }

    @Test
    void appendsOutboundChangelogEntryAfterGeneratingMessages() throws Exception {
        String reply = "1. RECIPIENT — Dean's office\nCHANNEL — email\n" + "x".repeat(200);
        FakeLlmClient fake = new FakeLlmClient(reply);
        RecordingOutput out = new RecordingOutput();
        AgenticTaskContext ctx = new AgenticTaskContext(org, reconciliation, changeLog, null, fake.asFactory(), out);

        new OutboundMessagesTask(new OperationalFeedStore(), new RelationshipStore()).execute(ctx);

        assertTrue(out.await(o -> o.statuses().contains("Outbound messages identified."), Duration.ofSeconds(10)),
            "task did not finish; statuses=" + out.statuses() + " outputs=" + out.outputs());

        assertEquals(1, fake.prompts().size());
        assertEquals(1, out.outputs().size());
        assertEquals("Outbound Messages", out.outputs().get(0).title());

        List<ContextChangeLog.ChangeRecord> records = changeLog.readAll();
        ContextChangeLog.ChangeRecord signal = records.stream()
            .filter(r -> "outbound-messages".equals(r.source()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no outbound-messages entry in " + records));
        assertEquals("outbound", signal.field());
        assertEquals("", signal.oldValue());
        assertEquals("generated", signal.action());
        assertEquals(reply.substring(0, 120), signal.newValue());
    }
}
