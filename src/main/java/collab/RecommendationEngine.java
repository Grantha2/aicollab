package collab;

// ============================================================
// RecommendationEngine.java — Next-best-action recommendation engine.
//
// WHAT THIS CLASS DOES (one sentence):
// Analyzes org context freshness, operational feeds, and change log
// to generate prioritized recommendations for what the leader should
// do next, each linked to an agentic task.
// ============================================================

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class RecommendationEngine {

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

        // Sort by urgency: HIGH first, then MEDIUM, then LOW
        recs.sort((a, b) -> urgencyRank(b.urgency()) - urgencyRank(a.urgency()));

        // Return top 5
        return recs.size() > 5 ? recs.subList(0, 5) : recs;
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
                "context-refresh"
            ));
        }
    }

    private void checkUpcomingDeadlines(List<Recommendation> recs) {
        List<OperationalFeedItem> upcoming = feedStore.getUpcoming(3);
        List<OperationalFeedItem> overdue = feedStore.getOverdue();

        if (!overdue.isEmpty()) {
            OperationalFeedItem first = overdue.get(0);
            recs.add(new Recommendation(
                "OVERDUE: " + first.getTitle(),
                overdue.size() + " overdue item(s) need attention.",
                "HIGH",
                "start-your-day"
            ));
        }

        for (OperationalFeedItem item : upcoming) {
            if (item.getFeedType() == OperationalFeedItem.FeedType.DEADLINE) {
                recs.add(new Recommendation(
                    item.getTitle() + " — deadline approaching",
                    "Due within 3 days. Review status and send reminders if needed.",
                    "HIGH",
                    "initiative-review"
                ));
                break; // only show the most urgent one
            }
        }
    }

    private void checkUpcomingMeetings(List<Recommendation> recs) {
        List<OperationalFeedItem> meetings = feedStore.getUpcomingMeetings(1);
        if (!meetings.isEmpty()) {
            OperationalFeedItem next = meetings.get(0);
            recs.add(new Recommendation(
                "Meeting soon: " + next.getTitle(),
                "Meeting within 24 hours. Prepare briefing and talking points.",
                "HIGH",
                "meeting-prep"
            ));
        }
    }

    private void checkOutboundMessages(List<Recommendation> recs) {
        // Recommend outbound messages review (always useful as a daily check)
        String todayStr = LocalDate.now().toString();
        List<ContextChangeLog.ChangeRecord> recent = changeLog.getRecent(20);
        boolean sentOutboundToday = recent.stream()
            .anyMatch(r -> r.source() != null && r.source().contains("outbound")
                && r.timestamp() != null
                && r.timestamp().startsWith(todayStr));

        if (!sentOutboundToday) {
            recs.add(new Recommendation(
                "Review outbound communications",
                "No outbound message task run today. Check if messages need to go out.",
                "MEDIUM",
                "outbound-messages"
            ));
        }
    }

    private void checkWeeklyReport(List<Recommendation> recs) {
        DayOfWeek dow = LocalDate.now().getDayOfWeek();
        if (dow == DayOfWeek.FRIDAY) {
            recs.add(new Recommendation(
                "Weekly report due",
                "It's Friday — generate and distribute your weekly status report.",
                "MEDIUM",
                "weekly-report"
            ));
        }
    }

    private void checkContextStaleness(List<Recommendation> recs) {
        List<ContextChangeLog.ChangeRecord> recent = changeLog.getRecent(1);
        if (recent.isEmpty()) {
            recs.add(new Recommendation(
                "No recent context updates",
                "Change log is empty. Start your day to bring context up to date.",
                "MEDIUM",
                "start-your-day"
            ));
            return;
        }

        String lastTimestamp = recent.get(0).timestamp();
        if (lastTimestamp != null) {
            try {
                Instant lastChange = Instant.parse(lastTimestamp);
                if (lastChange.isBefore(Instant.now().minus(Duration.ofDays(3)))) {
                    long daysSince = Duration.between(lastChange, Instant.now()).toDays();
                    recs.add(new Recommendation(
                        "No context updates in " + daysSince + " days",
                        "Context may be drifting. Run a daily check-in to stay current.",
                        "MEDIUM",
                        "start-your-day"
                    ));
                }
            } catch (Exception ignored) {
                // timestamp format not parseable — skip this check
            }
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
