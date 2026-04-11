package collab;

// ============================================================
// ContextRefreshTask.java — Built-in agentic task that refreshes
// stale organization context fields via per-field dialog + AI.
//
// WHAT THIS CLASS DOES (one sentence):
// Shows a per-field input dialog, sends updates to Claude, and
// feeds proposed changes through the reconciliation service.
// ============================================================

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ContextRefreshTask implements AgenticTask {

    private final DailyContextUpdateFunction updateFn;

    public ContextRefreshTask(DailyContextUpdateFunction updateFn) {
        this.updateFn = updateFn;
    }

    @Override public String getId()          { return "context-refresh"; }
    @Override public String getName()        { return "Refresh Context"; }
    @Override public String getDescription() { return "Update stale organization context fields with AI assistance"; }
    @Override public String getCategory()    { return "Context"; }
    @Override public boolean isAvailable()   { return true; }

    @Override
    public void execute(AgenticTaskContext ctx) {
        // When run without target fields, target all stale fields
        Map<String, Freshness> report = ctx.orgContext().getFreshnessReport();
        List<String> staleFields = new ArrayList<>();
        for (var entry : report.entrySet()) {
            if (entry.getValue() != Freshness.FRESH) {
                staleFields.add(entry.getKey());
            }
        }
        if (staleFields.isEmpty()) {
            ctx.panel().showFunctionOutput("Context Refresh",
                "All context fields are fresh. Nothing to update.");
            return;
        }
        execute(ctx, staleFields);
    }

    @Override
    public void execute(AgenticTaskContext ctx, List<String> targetFields) {
        if (targetFields == null || targetFields.isEmpty()) {
            execute(ctx);
            return;
        }

        // Show per-field input dialog
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(ctx.panel());
        ContextUpdateDialog dialog = new ContextUpdateDialog(owner, targetFields, ctx.orgContext());
        dialog.setVisible(true);

        if (dialog.wasCancelled()) return;

        Map<String, String> perFieldInput = dialog.getPerFieldInput();

        // Run in background
        ctx.panel().setStatus("Running Context Refresh...");
        ctx.panel().showLoading("Analyzing context and generating update proposals...");

        new SwingWorker<ReconciliationService.ReconciliationResult, Void>() {
            @Override
            protected ReconciliationService.ReconciliationResult doInBackground() {
                java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                int maxTokens = ctx.config().getMaxResponseTokens();
                LlmClient client = new AnthropicClient(httpClient, ctx.config().getClaudeUrl(),
                        ctx.config().getClaudeKey(), ctx.config().getClaudeModel(), maxTokens);
                return updateFn.execute(client, targetFields, perFieldInput);
            }

            @Override
            protected void done() {
                try {
                    ReconciliationService.ReconciliationResult result = get();
                    SwingUtilities.invokeLater(() -> ctx.panel().handleReconciliationResult(result));
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        ctx.panel().showFunctionOutput("Error",
                            "Context refresh failed: " + e.getMessage());
                        ctx.panel().setStatus("Error: " + e.getMessage());
                    });
                }
            }
        }.execute();
    }
}
