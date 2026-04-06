package collab;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class InitiativeReviewTask implements AgenticTask {

    private final InitiativeStore initiativeStore;

    public InitiativeReviewTask(InitiativeStore initiativeStore) {
        this.initiativeStore = initiativeStore;
    }

    @Override public String getId()          { return "initiative-review"; }
    @Override public String getName()        { return "Initiative Review"; }
    @Override public String getDescription() { return "Review active initiatives against timelines and flag risks"; }
    @Override public String getCategory()    { return "Context"; }
    @Override public boolean isAvailable()   { return true; }

    @Override
    public void execute(AgenticTaskContext ctx) {
        ctx.panel().setStatus("Running initiative review...");
        ctx.panel().showLoading("Analyzing initiatives against timelines...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                LlmClient client = new AnthropicClient(httpClient, ctx.config().getClaudeUrl(),
                        ctx.config().getClaudeKey(), ctx.config().getClaudeModel(),
                        ctx.config().getMaxResponseTokens());
                return client.sendMessage(buildPrompt(ctx));
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    SwingUtilities.invokeLater(() -> {
                        ctx.panel().showFunctionOutput("Initiative Review", response);
                        ctx.panel().setStatus("Initiative review complete.");
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

    private String buildPrompt(AgenticTaskContext ctx) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
            You are a strategic advisor reviewing organizational initiatives.
            Produce a structured review for each initiative:

            For each initiative, provide:
            1. STATUS ASSESSMENT — on-track / at-risk / blocked / completed
            2. TIMELINE CHECK — is the deadline achievable given current progress?
            3. BLOCKERS — what's preventing progress?
            4. RECOMMENDED ACTIONS — specific next steps
            5. ESCALATION NEEDED? — yes/no and to whom

            Then provide an OVERALL SUMMARY with the portfolio health and top 3 priorities.

            """);

        prompt.append("=== ORGANIZATION CONTEXT ===\n");
        prompt.append(ctx.orgContext().buildContextBlock()).append("\n");

        // Structured initiatives if available
        List<Initiative> initiatives = initiativeStore.getAll();
        if (!initiatives.isEmpty()) {
            prompt.append("=== STRUCTURED INITIATIVES ===\n");
            for (Initiative init : initiatives) {
                prompt.append("- ").append(init.toSummary()).append("\n");
                if (init.getNextActions() != null && !init.getNextActions().isBlank())
                    prompt.append("  Next Actions: ").append(init.getNextActions()).append("\n");
                if (init.getSuccessMetric() != null && !init.getSuccessMetric().isBlank())
                    prompt.append("  Success Metric: ").append(init.getSuccessMetric()).append("\n");
            }
        } else {
            prompt.append("(No structured initiatives yet. Review the 'Active Initiatives and Status' field from org context above.)\n");
        }

        return prompt.toString();
    }
}
