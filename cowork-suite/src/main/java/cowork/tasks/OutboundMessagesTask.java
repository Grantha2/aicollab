package cowork.tasks;

import cowork.context.ContextChangeLog;
import cowork.data.OperationalFeedItem;
import cowork.data.OperationalFeedStore;
import cowork.data.Relationship;
import cowork.data.RelationshipStore;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.util.List;

/**
 * "Outbound Messages" routine: one Claude call that reads org context, relationships and
 * the operational feed and drafts the messages the leader should send today. Each run also
 * leaves an "outbound" entry in the change log so the recommendation engine can see that
 * outreach was recently generated.
 */
public class OutboundMessagesTask implements AgenticTask {

    private static final int SIGNAL_PREVIEW_CHARS = 120;

    private final OperationalFeedStore feedStore;
    private final RelationshipStore relationshipStore;

    public OutboundMessagesTask(OperationalFeedStore feedStore, RelationshipStore relationshipStore) {
        this.feedStore = feedStore;
        this.relationshipStore = relationshipStore;
    }

    @Override public String getId()          { return "outbound-messages"; }
    @Override public String getName()        { return "Outbound Messages"; }
    @Override public String getDescription() { return "Identify and draft outbound communications needed today"; }
    @Override public String getCategory()    { return "Daily"; }
    @Override public boolean isAvailable()   { return true; }

    @Override
    public void execute(AgenticTaskContext ctx) {
        String additionalContext = askForFocus();
        if (additionalContext == null) return;

        ctx.output().setStatus("Identifying outbound communications: analyzing context for needed communications...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                String response = ctx.clients().claude().sendMessage(buildPrompt(ctx, additionalContext));
                recordOutboundSignal(ctx, response);
                return response;
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    ctx.output().showOutput("Outbound Messages", response);
                    ctx.output().setStatus("Outbound messages identified.");
                } catch (Exception e) {
                    ctx.output().showOutput("Error", "Failed: " + e.getMessage());
                    ctx.output().setStatus("Error: " + e.getMessage());
                }
            }
        }.execute();
    }

    /** The focus prompt is optional, so a headless run simply proceeds on context alone. */
    private static String askForFocus() {
        if (TaskDialogs.headless()) return "";
        return JOptionPane.showInputDialog(
            TaskDialogs.owner(),
            "Any specific communications on your mind?\n"
                + "(Leave blank to let AI identify what's needed based on your context.)",
            "Outbound Messages",
            JOptionPane.QUESTION_MESSAGE);
    }

    private static void recordOutboundSignal(AgenticTaskContext ctx, String response) {
        if (response == null || response.startsWith("[ERROR]")) return;
        String preview = response.length() > SIGNAL_PREVIEW_CHARS
            ? response.substring(0, SIGNAL_PREVIEW_CHARS) : response;
        ctx.changeLog().append(ContextChangeLog.ChangeRecord.of(
            "outbound", "", preview, "outbound-messages", "generated"));
    }

    private String buildPrompt(AgenticTaskContext ctx, String additionalContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
            You are an executive communications advisor. Your job is to identify outbound \
            messages the leader should send today and provide draft content for each.

            Analyze the organization context, relationships, upcoming events, and recent \
            changes to recommend specific communications. Consider:
            - Follow-ups needed after recent meetings or events
            - Upcoming deadlines that require reminders or status requests
            - Relationships that haven't been engaged recently
            - Pending decisions that need input from others
            - Thank-you or acknowledgment messages owed
            - Pre-meeting outreach to attendees
            - Status updates stakeholders are expecting
            - Introductions or connections that should be made

            For each recommended message, provide:
            1. RECIPIENT — who to contact
            2. CHANNEL — email, Teams message, text, phone call, etc.
            3. PURPOSE — why this message is needed now
            4. URGENCY — high / medium / low
            5. DRAFT — a ready-to-send draft (the leader can edit before sending)

            Prioritize by urgency. Be specific — use real names and context from the data provided.
            If there are no urgent communications needed, say so and suggest proactive outreach.

            """);

        prompt.append("=== ORGANIZATION CONTEXT ===\n");
        prompt.append(ctx.orgContext().buildContextBlock()).append("\n");

        List<Relationship> relationships = relationshipStore.getAll();
        if (!relationships.isEmpty()) {
            prompt.append("=== RELATIONSHIPS ===\n");
            for (Relationship rel : relationships) {
                prompt.append("- ").append(rel.toSummary());
                if (rel.getLastInteraction() != null && !rel.getLastInteraction().isBlank())
                    prompt.append(" | Last interaction: ").append(rel.getLastInteraction());
                if (rel.getNextSteps() != null && !rel.getNextSteps().isBlank())
                    prompt.append(" | Next steps: ").append(rel.getNextSteps());
                prompt.append("\n");
            }
        }

        List<OperationalFeedItem> upcoming = feedStore.getUpcoming(3);
        List<OperationalFeedItem> overdue = feedStore.getOverdue();
        if (!upcoming.isEmpty() || !overdue.isEmpty()) {
            prompt.append("\n=== UPCOMING EVENTS & DEADLINES ===\n");
            for (OperationalFeedItem item : overdue) {
                prompt.append("OVERDUE: ").append(item.toDisplayString());
                if (item.getAttendees() != null) prompt.append(" | Attendees: ").append(item.getAttendees());
                prompt.append("\n");
            }
            for (OperationalFeedItem item : upcoming) {
                if (!item.isOverdue()) {
                    prompt.append(item.toDisplayString());
                    if (item.getAttendees() != null) prompt.append(" | Attendees: ").append(item.getAttendees());
                    prompt.append("\n");
                }
            }
        }

        if (additionalContext != null && !additionalContext.isBlank()) {
            prompt.append("\n=== ADDITIONAL CONTEXT FROM LEADER ===\n");
            prompt.append(additionalContext).append("\n");
        }

        return prompt.toString();
    }
}
