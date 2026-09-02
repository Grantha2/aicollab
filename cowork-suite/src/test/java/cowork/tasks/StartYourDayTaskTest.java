package cowork.tasks;

import cowork.context.ContextChangeLog;
import cowork.context.OrganizationContext;
import cowork.context.ReconciliationService;
import cowork.data.OperationalFeedStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** StartYourDayTask end to end with a scripted client: one prompt, stale field named, one output card. */
class StartYourDayTaskTest {

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
    void sendsOnePromptNamingTheStaleFieldAndShowsOneOutput() throws Exception {
        // topPriorities has a 14-day TTL; 20 days old is NEEDS_CONFIRMATION, everything else stays FRESH.
        org.getEntry("topPriorities").setLastUpdated(Instant.now().minus(20, ChronoUnit.DAYS).toString());

        FakeLlmClient fake = new FakeLlmClient("Good morning. Focus on recruiting today.");
        RecordingOutput out = new RecordingOutput();
        AgenticTaskContext ctx = new AgenticTaskContext(org, reconciliation, changeLog, null, fake.asFactory(), out);

        new StartYourDayTask(new OperationalFeedStore()).execute(ctx);

        assertTrue(out.await(o -> o.statuses().contains("Morning brief complete."), Duration.ofSeconds(10)),
            "task did not finish; statuses=" + out.statuses() + " outputs=" + out.outputs());

        assertEquals(1, fake.prompts().size());
        String prompt = fake.prompts().get(0);
        assertTrue(prompt.contains("=== CONTEXT FRESHNESS ==="));
        assertTrue(prompt.contains("Top Priorities: NEEDS_CONFIRMATION"), prompt);

        assertEquals(1, out.outputs().size());
        assertEquals("Morning Brief", out.outputs().get(0).title());
        assertEquals("Good morning. Focus on recruiting today.", out.outputs().get(0).body());
        assertTrue(org.getWhatChangedSinceLastUpdate().startsWith("Daily standup completed at"));
    }
}
