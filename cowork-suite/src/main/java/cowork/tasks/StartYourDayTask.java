package cowork.tasks;

import cowork.context.ContextChangeLog;
import cowork.context.ContextStatus;
import cowork.context.Freshness;
import cowork.context.OrganizationContext;
import cowork.data.OperationalFeedItem;
import cowork.data.OperationalFeedStore;

import javax.swing.SwingWorker;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * "Start Your Day" routine: one Claude call that turns org context, field freshness, the
 * operational feed and the last 24h of context changes into a morning brief. Completing the
 * brief stamps whatChangedSinceLastUpdate so the context reflects the standup.
 */
public class StartYourDayTask implements AgenticTask {

    private final OperationalFeedStore feedStore;

    public StartYourDayTask(OperationalFeedStore feedStore) {
        this.feedStore = feedStore;
    }

    @Override public String getId()          { return "start-your-day"; }
    @Override public String getName()        { return "Start Your Day"; }
    @Override public String getDescription() { return "Morning standup: upcoming items, stale context, recommended actions"; }
    @Override public String getCategory()    { return "Daily"; }
    @Override public boolean isAvailable()   { return true; }

    @Override
    public void execute(AgenticTaskContext ctx) {
        ctx.output().setStatus("Preparing your morning brief: analyzing context, schedule, and recent changes...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return ctx.clients().claude().sendMessage(buildPrompt(ctx));
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    ctx.output().showOutput("Morning Brief", response);
                    ctx.orgContext().updateField("whatChangedSinceLastUpdate",
                        "Daily standup completed at " + Instant.now().toString().substring(0, 16),
                        "daily_standup", 1.0, ContextStatus.APPROVED);
                    ctx.orgContext().save();
                    ctx.output().setStatus("Morning brief complete.");
                } catch (Exception e) {
                    ctx.output().showOutput("Error", "Failed: " + e.getMessage());
                    ctx.output().setStatus("Error: " + e.getMessage());
                }
            }
        }.execute();
    }

    private String buildPrompt(AgenticTaskContext ctx) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
            You are an executive assistant preparing a morning brief for an organizational leader.
            Produce a concise, actionable morning standup report.

            Structure your response as:
            1. TODAY'S FOCUS — what the leader should prioritize today (2-3 items)
            2. UPCOMING — events/meetings/deadlines in the next 3 days
            3. CONTEXT ALERTS — which organizational context areas are stale and need attention
            4. RECOMMENDED ACTIONS — 2-4 specific next steps based on all of the above

            Keep it crisp and executive-level. No fluff.

            """);

        prompt.append("=== ORGANIZATION CONTEXT ===\n");
        prompt.append(ctx.orgContext().buildContextBlock()).append("\n");

        prompt.append("=== CONTEXT FRESHNESS ===\n");
        Map<String, Freshness> report = ctx.orgContext().getFreshnessReport();
        for (var entry : report.entrySet()) {
            if (entry.getValue() != Freshness.FRESH) {
                prompt.append("- ").append(OrganizationContext.getFieldLabel(entry.getKey()))
                      .append(": ").append(entry.getValue()).append("\n");
            }
        }

        List<OperationalFeedItem> upcoming = feedStore.getUpcoming(3);
        List<OperationalFeedItem> overdue = feedStore.getOverdue();
        prompt.append("\n=== UPCOMING ITEMS (next 3 days) ===\n");
        if (upcoming.isEmpty() && overdue.isEmpty()) {
            prompt.append("(No items scheduled)\n");
        } else {
            for (OperationalFeedItem item : overdue) {
                prompt.append("OVERDUE: ").append(item.toDisplayString());
                if (item.getNotes() != null && !item.getNotes().isBlank())
                    prompt.append(" | Notes: ").append(item.getNotes());
                prompt.append("\n");
            }
            for (OperationalFeedItem item : upcoming) {
                prompt.append(item.toDisplayString());
                if (item.getAttendees() != null && !item.getAttendees().isBlank())
                    prompt.append(" | Attendees: ").append(item.getAttendees());
                if (item.getNotes() != null && !item.getNotes().isBlank())
                    prompt.append(" | Notes: ").append(item.getNotes());
                prompt.append("\n");
            }
        }

        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        List<ContextChangeLog.ChangeRecord> recentChanges = ctx.changeLog().getChangesSince(since);
        if (!recentChanges.isEmpty()) {
            prompt.append("\n=== RECENT CONTEXT CHANGES (past 24h) ===\n");
            for (var change : recentChanges) {
                prompt.append("- ").append(change.field()).append(": ").append(change.action()).append("\n");
            }
        }

        return prompt.toString();
    }
}
