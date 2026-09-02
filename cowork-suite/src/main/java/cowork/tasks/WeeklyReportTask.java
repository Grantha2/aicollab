package cowork.tasks;

import cowork.context.ContextChangeLog;
import cowork.context.OrganizationContext;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * "Weekly Report" routine: the user picks an audience, then one Claude call synthesises org
 * context plus the past seven days of change-log entries into a structured weekly report.
 */
public class WeeklyReportTask implements AgenticTask {

    private static final String[] AUDIENCES = {"Team", "Board / Advisors", "Officer Corps", "General Membership"};

    @Override public String getId()          { return "weekly-report"; }
    @Override public String getName()        { return "Weekly Report"; }
    @Override public String getDescription() { return "Synthesize the week's activity into a structured report"; }
    @Override public String getCategory()    { return "Reports"; }
    @Override public boolean isAvailable()   { return true; }

    @Override
    public void execute(AgenticTaskContext ctx) {
        String audience = (String) JOptionPane.showInputDialog(TaskDialogs.owner(),
            "Who is this weekly report for?", "Weekly Report",
            JOptionPane.QUESTION_MESSAGE, null, AUDIENCES, AUDIENCES[0]);
        if (audience == null) return;

        ctx.output().setStatus("Compiling weekly report: synthesizing this week's activity...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return ctx.clients().claude().sendMessage(buildPrompt(ctx, audience));
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    ctx.output().showOutput("Weekly Report — " + audience, response);
                    ctx.output().setStatus("Weekly report complete.");
                } catch (Exception e) {
                    ctx.output().showOutput("Error", "Failed: " + e.getMessage());
                    ctx.output().setStatus("Error: " + e.getMessage());
                }
            }
        }.execute();
    }

    private String buildPrompt(AgenticTaskContext ctx, String audience) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an executive assistant compiling a weekly report for an organizational leader.\n");
        prompt.append("The audience for this report is: ").append(audience).append("\n\n");
        prompt.append("""
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
