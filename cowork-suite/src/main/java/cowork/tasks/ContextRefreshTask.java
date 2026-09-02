package cowork.tasks;

import cowork.context.Freshness;
import cowork.context.OrganizationContext;
import cowork.context.ProposedChange;
import cowork.context.ReconciliationService;

import javax.swing.SwingWorker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * "Refresh Context" routine: collects per-field notes in ContextUpdateDialog, runs
 * DailyContextUpdateFunction (one Claude call) and presents the reconciliation outcome:
 * auto-applied changes as a summary card, the rest as approval proposals.
 */
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
        Map<String, Freshness> report = ctx.orgContext().getFreshnessReport();
        List<String> staleFields = new ArrayList<>();
        for (var entry : report.entrySet()) {
            if (entry.getValue() != Freshness.FRESH) {
                staleFields.add(entry.getKey());
            }
        }
        if (staleFields.isEmpty()) {
            ctx.output().showOutput("Context Refresh", "All context fields are fresh. Nothing to update.");
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

        ContextUpdateDialog dialog = new ContextUpdateDialog(TaskDialogs.owner(), targetFields, ctx.orgContext());
        dialog.setVisible(true);
        if (dialog.wasCancelled()) return;
        Map<String, String> perFieldInput = dialog.getPerFieldInput();

        ctx.output().setStatus("Running Context Refresh: analyzing context and generating update proposals...");

        new SwingWorker<ReconciliationService.ReconciliationResult, Void>() {
            @Override
            protected ReconciliationService.ReconciliationResult doInBackground() {
                return updateFn.execute(ctx.clients().claude(), targetFields, perFieldInput);
            }

            @Override
            protected void done() {
                try {
                    present(ctx, get());
                } catch (Exception e) {
                    ctx.output().showOutput("Error", "Context refresh failed: " + e.getMessage());
                    ctx.output().setStatus("Error: " + e.getMessage());
                }
            }
        }.execute();
    }

    /** Same presentation the legacy panel did in handleReconciliationResult, via TaskOutput. */
    private void present(AgenticTaskContext ctx, ReconciliationService.ReconciliationResult result) {
        if (result == null) {
            String raw = updateFn.lastRawResponse();
            if (raw == null || raw.isBlank() || "[]".equals(raw.strip())) {
                ctx.output().showOutput("Complete", "No updates needed — all context appears current.");
                ctx.output().setStatus("No updates needed.");
            } else {
                ctx.output().showOutput("Context Refresh",
                    "Claude returned no structured proposals. Raw response:\n\n" + raw);
                ctx.output().setStatus("No structured proposals returned.");
            }
            return;
        }

        int autoCount = result.autoApplied().size();
        int pendingCount = result.needsApproval().size();

        StringBuilder summary = new StringBuilder();
        if (autoCount > 0) {
            summary.append(autoCount).append(" change(s) auto-applied (low-risk/additive).\n");
            for (ProposedChange c : result.autoApplied()) {
                summary.append("  ✓ ").append(OrganizationContext.getFieldLabel(c.fieldName())).append("\n");
            }
        }
        if (pendingCount > 0) {
            summary.append(pendingCount).append(" change(s) need your approval below.");
        }
        if (autoCount == 0 && pendingCount == 0) {
            summary.append("No changes proposed.");
        }

        ctx.output().showOutput("Results", summary.toString());
        ctx.output().showProposals(ctx.reconciliation().getApprovalQueue());
        ctx.output().setStatus("Complete. " + autoCount + " auto-applied, " + pendingCount + " pending.");
    }
}
