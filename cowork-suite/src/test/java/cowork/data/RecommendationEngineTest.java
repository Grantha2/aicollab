package cowork.data;

import cowork.context.ContextChangeLog;
import cowork.context.ContextChangeLog.ChangeRecord;
import cowork.context.OrganizationContext;
import cowork.data.OperationalFeedItem.FeedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Stale-field rule, the 7-day outbound rule (contract 8), overdue ordering, and the top-5 cap. */
class RecommendationEngineTest {

    @TempDir Path tempDir;

    private OrganizationContext ctx;
    private OperationalFeedStore feeds;
    private ContextChangeLog log;
    private RecommendationEngine engine;

    @BeforeEach
    void setUp() {
        System.setProperty("cowork.home", tempDir.toString());
        ctx = new OrganizationContext();
        feeds = new OperationalFeedStore(tempDir.resolve("operational_feeds.json"));
        log = new ContextChangeLog(tempDir.resolve("context_changelog.jsonl"));
        engine = new RecommendationEngine(ctx, feeds, log);
    }

    private List<Recommendation> recsFor(String taskId) {
        return engine.getRecommendations().stream().filter(r -> taskId.equals(r.linkedTaskId())).toList();
    }

    private static ChangeRecord outboundRun(Duration age) {
        return new ChangeRecord(Instant.now().minus(age).toString(),
            "outbound", "", "", "outbound-messages", "generated");
    }

    @Test
    void staleFieldYieldsContextRefreshRecommendation() {
        assertTrue(recsFor("context-refresh").isEmpty(), "fresh context needs no refresh");

        ctx.getEntry("topPriorities").setLastUpdated(Instant.now().minus(Duration.ofDays(20)).toString());

        List<Recommendation> refresh = recsFor("context-refresh");
        assertEquals(1, refresh.size());
        assertEquals("Update 1 stale field(s)", refresh.get(0).title());
        assertEquals("MEDIUM", refresh.get(0).urgency());
    }

    @Test
    void threeOrMoreStaleFieldsEscalateToHigh() {
        String old = Instant.now().minus(Duration.ofDays(40)).toString();
        for (String f : List.of("topPriorities", "currentMetrics", "pendingDecisions")) {
            ctx.getEntry(f).setLastUpdated(old);
        }
        assertEquals("HIGH", recsFor("context-refresh").get(0).urgency());
    }

    @Test
    void outboundRuleFiresWithEmptyChangelog() {
        List<Recommendation> outbound = recsFor("outbound-messages");
        assertEquals(1, outbound.size());
        assertEquals("Review outbound communications", outbound.get(0).title());
    }

    @Test
    void outboundRuleIsSilentAfterRecentOutboundRun() {
        log.append(ChangeRecord.of("outbound", "", "", "outbound-messages", "generated"));
        assertTrue(recsFor("outbound-messages").isEmpty());

        log.append(outboundRun(Duration.ofDays(6)));
        assertTrue(recsFor("outbound-messages").isEmpty(), "a run 6 days ago is still inside the window");
    }

    @Test
    void outboundRuleFiresAgainOnceLastRunIsOlderThanSevenDays() {
        log.append(outboundRun(Duration.ofDays(8)));
        // A non-outbound entry from today must not suppress the rule.
        log.append(ChangeRecord.of("currentMetrics", "", "42", "daily_update", "auto_apply"));
        assertEquals(1, recsFor("outbound-messages").size());
    }

    @Test
    void overdueItemIsHighUrgencyAndSortsFirst() {
        feeds.addItem(new OperationalFeedItem("late", "Grant report", FeedType.DEADLINE,
            LocalDate.now().minusDays(2).toString()));

        List<Recommendation> recs = engine.getRecommendations();

        assertEquals("HIGH", recs.get(0).urgency());
        assertEquals("OVERDUE: Grant report", recs.get(0).title());
        assertEquals("start-your-day", recs.get(0).linkedTaskId());
    }

    @Test
    void emptyChangelogPromptsStartYourDayAndResultIsCappedAtFive() {
        assertFalse(recsFor("start-your-day").isEmpty());
        assertTrue(engine.getRecommendations().size() <= 5);

        // Stack every rule at once: stale fields, overdue, upcoming deadline, meeting, outbound.
        String old = Instant.now().minus(Duration.ofDays(40)).toString();
        for (String f : OrganizationContext.getFieldNames()) ctx.getEntry(f).setLastUpdated(old);
        LocalDate today = LocalDate.now();
        feeds.addItem(new OperationalFeedItem("a", "Late", FeedType.DEADLINE, today.minusDays(1).toString()));
        feeds.addItem(new OperationalFeedItem("b", "Due", FeedType.DEADLINE, today.plusDays(1).toString()));
        feeds.addItem(new OperationalFeedItem("c", "Sync", FeedType.MEETING, today.toString()));

        List<Recommendation> recs = engine.getRecommendations();
        assertEquals(5, recs.size());
        assertTrue(recs.stream().limit(4).allMatch(r -> "HIGH".equals(r.urgency())));
    }
}
