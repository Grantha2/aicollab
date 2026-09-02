package cowork.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Classifies AI-proposed context changes as safe to auto-apply or needing user approval,
 * applies the safe ones, queues the rest, and logs every outcome to ContextChangeLog.
 * Conservative by design: strategic fields and overwrites of existing content always queue.
 * reconcile() runs on a worker thread while approve()/reject()/getApprovalQueue() run on the
 * EDT, so every queue-touching method is synchronised and the queue getter returns a snapshot.
 */
public class ReconciliationService {

    private static final Set<String> STRATEGIC_FIELDS = Set.of(
        "topPriorities", "activeInitiativesAndStatus", "pendingDecisions", "preferredToneStyle");

    private final OrganizationContext orgContext;
    private final ContextChangeLog changeLog;
    private final List<ProposedChange> approvalQueue = new ArrayList<>();

    public ReconciliationService(OrganizationContext orgContext, ContextChangeLog changeLog) {
        this.orgContext = orgContext;
        this.changeLog = changeLog;
    }

    /** Applies SAFE_AUTO proposals (saving once at the end) and queues the rest. */
    public synchronized ReconciliationResult reconcile(List<ProposedChange> proposals) {
        List<ProposedChange> autoApplied = new ArrayList<>();
        List<ProposedChange> queued = new ArrayList<>();
        for (ProposedChange proposal : proposals) {
            if (classify(proposal) == MergeDecision.SAFE_AUTO) {
                apply(proposal, "auto_apply");
                autoApplied.add(proposal);
            } else {
                approvalQueue.add(proposal);
                queued.add(proposal);
            }
        }
        if (!autoApplied.isEmpty()) orgContext.save();
        return new ReconciliationResult(autoApplied, queued);
    }

    /**
     * SAFE_AUTO: metadata fields, direct user edits, filling an empty field, or a no-op.
     * APPROVAL_REQUIRED: strategic fields, or any AI-sourced overwrite of existing content.
     */
    MergeDecision classify(ProposedChange proposal) {
        String fieldName = proposal.fieldName();
        if ("lastUpdated".equals(fieldName) || "whatChangedSinceLastUpdate".equals(fieldName)) {
            return MergeDecision.SAFE_AUTO;
        }
        if ("user_edit".equals(proposal.source())) return MergeDecision.SAFE_AUTO;
        if (STRATEGIC_FIELDS.contains(fieldName)) return MergeDecision.APPROVAL_REQUIRED;
        ContextEntry<String> entry = orgContext.getEntry(fieldName);
        if (entry == null || !entry.hasValue()) return MergeDecision.SAFE_AUTO;
        if (proposal.currentValue() != null && proposal.currentValue().equals(proposal.proposedValue())) {
            return MergeDecision.SAFE_AUTO;
        }
        return MergeDecision.APPROVAL_REQUIRED;
    }

    public synchronized void approve(ProposedChange proposal) {
        apply(proposal, "approve");
        approvalQueue.remove(proposal);
        orgContext.save();
    }

    public synchronized void reject(ProposedChange proposal) {
        log(proposal, "reject");
        approvalQueue.remove(proposal);
    }

    /** Snapshot of the pending queue; safe to iterate while reconcile() runs on another thread. */
    public synchronized List<ProposedChange> getApprovalQueue() {
        return List.copyOf(approvalQueue);
    }

    public synchronized void clearQueue() {
        approvalQueue.clear();
    }

    private void apply(ProposedChange proposal, String action) {
        log(proposal, action);
        orgContext.updateField(proposal.fieldName(), proposal.proposedValue(),
            proposal.source(), proposal.confidence(), ContextStatus.APPROVED);
    }

    private void log(ProposedChange proposal, String action) {
        changeLog.append(ContextChangeLog.ChangeRecord.of(proposal.fieldName(),
            proposal.currentValue(), proposal.proposedValue(), proposal.source(), action));
    }

    public record ReconciliationResult(List<ProposedChange> autoApplied, List<ProposedChange> needsApproval) {}
}
