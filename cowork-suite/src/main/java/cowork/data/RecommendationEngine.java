package cowork.data;

import cowork.context.ContextChangeLog;
import cowork.context.Freshness;
import cowork.context.OrganizationContext;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Next-best-action engine: reads org-context freshness, the operational feed and the change
 * log, and returns up to five recommendations (HIGH urgency first), each linked to the
 * agentic task id that fulfils it.
 */
public class RecommendationEngine {

    private static final int MAX_RECOMMENDATIONS = 5;
    private static final Duration OUTBOUND_QUIET_PERIOD = Duration.ofDays(7);

    private final OrganizationContext orgContext;
    private final OperationalFeedStore feedStore;
    private final ContextChangeLog changeLog;

    public RecommendationEngine(OrganizationContext orgContext,
                                OperationalFeedStore feedStore,
                                ContextChangeLog changeLog) {
        this.orgContext = orgContext;
        this.feedStore = feedStore;
        this.changeLog = changeLog;
    }

    public List<Recommendation> getRecommendations() {
        List<Recommendation> recs = new ArrayList<>();
        checkStaleFields(recs);
        checkUpcomingDeadlines(recs);
        checkUpcomingMeetings(recs);
        checkOutboundMessages(recs);
        checkWeeklyReport(recs);
        checkContextStaleness(recs);
        recs.sort((a, b) -> urgencyRank(b.urgency()) - urgencyRank(a.urgency()));
        return recs.size() > MAX_RECOMMENDATIONS ? recs.subList(0, MAX_RECOMMENDATIONS) : recs;
    }

    private void checkStaleFields(List<Recommendation> recs) {
        Map<String, Freshness> report = orgContext.getFreshnessReport();
        long staleCount = report.values().stream()
            .filter(f -> f == Freshness.STALE || f == Freshness.NEEDS_CONFIRMATION)
            .count();
        if (staleCount > 0) {
            recs.add(new Recommendation(
                "Update " + staleCount + " stale field(s)",
                "Context fields are out of date and may produce inaccurate AI outputs.",
                staleCount >= 3 ? "HIGH" : "MEDIUM",
                "context-refresh"));
        }
    }

    private void checkUpcomingDeadlines(List<Recommendation> recs) {
        List<OperationalFeedItem> overdue = feedStore.getOverdue();
        if (!overdue.isEmpty()) {
            recs.add(new Recommendation(
                "OVERDUE: " + overdue.get(0).getTitle(),
                overdue.size() + " overdue item(s) need attention.",
                "HIGH",
                "start-your-day"));
        }
        // Only the nearest deadline is surfaced; the rest would crowd out other recommendations.
        for (OperationalFeedItem item : feedStore.getUpcoming(3)) {
            if (item.getFeedType() == OperationalFeedItem.FeedType.DEADLINE) {
                recs.add(new Recommendation(
                    item.getTitle() + " — deadline approaching",
                    "Due within 3 days. Review status and send reminders if needed.",
                    "HIGH",
                    "initiative-review"));
                break;
            }
        }
    }

    private void checkUpcomingMeetings(List<Recommendation> recs) {
        List<OperationalFeedItem> meetings = feedStore.getUpcomingMeetings(1);
        if (!meetings.isEmpty()) {
            recs.add(new Recommendation(
                "Meeting soon: " + meetings.get(0).getTitle(),
                "Meeting within 24 hours. Prepare briefing and talking points.",
                "HIGH",
                "meeting-prep"));
        }
    }

    // OutboundMessagesTask logs source "outbound-messages" on every run; only nag when it has
    // been silent for the whole quiet period.
    private void checkOutboundMessages(List<Recommendation> recs) {
        Instant since = Instant.now().minus(OUTBOUND_QUIET_PERIOD);
        boolean ranRecently = changeLog.getChangesSince(since).stream()
            .anyMatch(r -> r.source() != null && r.source().contains("outbound"));
        if (!ranRecently) {
            recs.add(new Recommendation(
                "Review outbound communications",
                "No outbound message task run in the last 7 days. Check if messages need to go out.",
                "MEDIUM",
                "outbound-messages"));
        }
    }

    private void checkWeeklyReport(List<Recommendation> recs) {
        if (LocalDate.now().getDayOfWeek() == DayOfWeek.FRIDAY) {
            recs.add(new Recommendation(
                "Weekly report due",
                "It's Friday — generate and distribute your weekly status report.",
                "MEDIUM",
                "weekly-report"));
        }
    }

    private void checkContextStaleness(List<Recommendation> recs) {
        List<ContextChangeLog.ChangeRecord> recent = changeLog.getRecent(1);
        if (recent.isEmpty()) {
            recs.add(new Recommendation(
                "No recent context updates",
                "Change log is empty. Start your day to bring context up to date.",
                "MEDIUM",
                "start-your-day"));
            return;
        }
        String lastTimestamp = recent.get(0).timestamp();
        if (lastTimestamp == null) return;
        try {
            Instant lastChange = Instant.parse(lastTimestamp);
            if (lastChange.isBefore(Instant.now().minus(Duration.ofDays(3)))) {
                long daysSince = Duration.between(lastChange, Instant.now()).toDays();
                recs.add(new Recommendation(
                    "No context updates in " + daysSince + " days",
                    "Context may be drifting. Run a daily check-in to stay current.",
                    "MEDIUM",
                    "start-your-day"));
            }
        } catch (Exception ignored) {
            // unparseable timestamp: skip this check
        }
    }

    private static int urgencyRank(String urgency) {
        return switch (urgency) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }
}
