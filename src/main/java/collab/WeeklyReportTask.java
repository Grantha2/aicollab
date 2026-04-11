package collab;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;

public class WeeklyReportTask implements AgenticTask {

    @Override public String getId()          { return "weekly-report"; }
    @Override public String getName()        { return "Weekly Report"; }
    @Override public String getDescription() { return "Synthesize the week's activity into a structured report"; }
    @Override public String getCategory()    { return "Reports"; }
    @Override public boolean isAvailable()   { return true; }

    @Override
    public void execute(AgenticTaskContext ctx) {
        // Input dialog: audience selection
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(ctx.panel());
        String[] audiences = {"Team", "Board / Advisors", "Officer Corps", "General Membership"};
        String audience = (String) JOptionPane.showInputDialog(owner,
            "Who is this weekly report for?", "Weekly Report",
            JOptionPane.QUESTION_MESSAGE, null, audiences, audiences[0]);
        if (audience == null) return;

        ctx.panel().setStatus("Compiling weekly report...");
        ctx.panel().showLoading("Synthesizing this week's activity...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                LlmClient client = new AnthropicClient(httpClient, ctx.config().getClaudeUrl(),
                        ctx.config().getClaudeKey(), ctx.config().getClaudeModel(),
                        ctx.config().getMaxResponseTokens());
                return client.sendMessage(buildPrompt(ctx, audience));
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    SwingUtilities.invokeLater(() -> {
                        ctx.panel().showFunctionOutput("Weekly Report — " + audience, response);
                        ctx.panel().setStatus("Weekly report complete.");
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

    private String buildPrompt(AgenticTaskContext ctx, String audience) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
            You are an executive assistant compiling a weekly report for an organizational leader.
            The audience for this report is: """ + audience + """


            Structure the report as:
            1. HIGHLIGHTS — top 3-5 accomplishments/wins this week
            2. DECISIONS MADE — key decisions and their rationale
            3. BLOCKERS & RISKS — current impediments and risk items
            4. METRICS UPDATE — any KPI movement or data changes
            5. UPCOMING PRIORITIES — what's on deck for next week
            6. ACTION ITEMS — specific follow-ups with owners

            Tailor the tone and detail level to the audience. Board reports should be more strategic;
            team reports should be more operational.

            """);

        prompt.append("=== ORGANIZATION CONTEXT ===\n");
        prompt.append(ctx.orgContext().buildContextBlock()).append("\n");

        // Change log from past 7 days
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<ContextChangeLog.ChangeRecord> changes = ctx.changeLog().getChangesSince(weekAgo);
        prompt.append("=== CONTEXT CHANGES THIS WEEK ===\n");
        if (changes.isEmpty()) {
            prompt.append("(No recorded changes this week)\n");
        } else {
            for (var change : changes) {
                prompt.append("- ").append(change.timestamp(), 0, Math.min(10, change.timestamp().length()));
                prompt.append(" | ").append(OrganizationContext.getFieldLabel(change.field()));
                prompt.append(" | ").append(change.action());
                if (change.newValue() != null && !change.newValue().isBlank()) {
                    String preview = change.newValue().length() > 80
                        ? change.newValue().substring(0, 80) + "..." : change.newValue();
                    prompt.append(" | ").append(preview);
                }
                prompt.append("\n");
            }
        }

        return prompt.toString();
    }
}
