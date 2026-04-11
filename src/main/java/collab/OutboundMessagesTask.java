package collab;

// ============================================================
// OutboundMessagesTask.java — Agentic task that identifies
// outbound communications the leader should send today.
//
// WHAT THIS CLASS DOES (one sentence):
// Analyzes org context, relationships, upcoming events, and
// recent changes to recommend specific messages the leader
// should send, with draft content for each.
// ============================================================

import javax.swing.*;
import java.util.*;
import java.util.List;

public class OutboundMessagesTask implements AgenticTask {

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
        // Optional: ask for additional context
        String additionalContext = JOptionPane.showInputDialog(
            SwingUtilities.getWindowAncestor(ctx.panel()),
            "Any specific communications on your mind?\n" +
            "(Leave blank to let AI identify what's needed based on your context.)",
            "Outbound Messages",
            JOptionPane.QUESTION_MESSAGE
        );
        if (additionalContext == null) return; // cancelled

        ctx.panel().setStatus("Identifying outbound communications...");
        ctx.panel().showLoading("Analyzing context for needed communications...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                LlmClient client = new AnthropicClient(httpClient, ctx.config().getClaudeUrl(),
                        ctx.config().getClaudeKey(), ctx.config().getClaudeModel(),
                        ctx.config().getMaxResponseTokens());
                return client.sendMessage(buildPrompt(ctx, additionalContext));
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    SwingUtilities.invokeLater(() -> {
                        ctx.panel().showFunctionOutput("Outbound Messages", response);
                        ctx.panel().setStatus("Outbound messages identified.");
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        ctx.panel().showFunctionOutput("Error", "Failed: " + e.getMessage());
                        ctx.panel().setStatus("Error: " + e.getMessage());
                    });
                }
            }
        }.execute();
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

        // Relationships
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

        // Upcoming events (next 3 days)
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

        // Additional user context
        if (additionalContext != null && !additionalContext.isBlank()) {
            prompt.append("\n=== ADDITIONAL CONTEXT FROM LEADER ===\n");
            prompt.append(additionalContext).append("\n");
        }

        return prompt.toString();
    }
}
