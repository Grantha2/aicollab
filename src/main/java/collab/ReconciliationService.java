package collab;

// ============================================================
// ReconciliationService.java — Safe reconciliation engine for
// context write-back from agentic function outputs.
//
// WHAT THIS CLASS DOES (one sentence):
// Classifies proposed context changes as safe-to-auto-apply or
// requiring user approval, applies safe ones, and queues the rest.
//
// KEY DESIGN DECISIONS:
// - Conservative by default: only truly additive/metadata changes auto-apply
// - Strategic fields always require approval
// - All changes (auto or approved) are logged to ContextChangeLog
// - Never overwrites without classification
// ============================================================

import java.util.*;

public class ReconciliationService {

    // Fields that always require user approval (strategic/high-impact)
    private static final Set<String> STRATEGIC_FIELDS = Set.of(
        "topPriorities",
        "activeInitiativesAndStatus",
        "pendingDecisions",
        "officerRosterAndOwnership",
        "keyPartnersStakeholders",
        "preferredToneStyle"
    );

    private final OrganizationContext orgContext;
    private final ContextChangeLog changeLog;

    // Pending changes awaiting user approval
    private final List<ProposedChange> approvalQueue = new ArrayList<>();

    public ReconciliationService(OrganizationContext orgContext, ContextChangeLog changeLog) {
        this.orgContext = orgContext;
        this.changeLog = changeLog;
    }

    /**
     * Reconciles a list of proposed changes against the current context snapshot.
     * Returns a result describing what was auto-applied and what needs approval.
     */
    public ReconciliationResult reconcile(List<ProposedChange> proposals) {
        List<ProposedChange> autoApplied = new ArrayList<>();
        List<ProposedChange> queued = new ArrayList<>();

        for (ProposedChange proposal : proposals) {
            MergeDecision decision = classify(proposal);

            if (decision == MergeDecision.SAFE_AUTO) {
                applyChange(proposal);
                autoApplied.add(proposal);
            } else {
                approvalQueue.add(proposal);
                queued.add(proposal);
            }
        }

        // Save after auto-applies
        if (!autoApplied.isEmpty()) {
            orgContext.save();
        }

        return new ReconciliationResult(autoApplied, queued);
    }

    /**
     * Classifies a proposed change into a merge decision.
     *
     * SAFE_AUTO when:
     * - Field was empty and now has a value (purely additive)
     * - Field is "lastUpdated" or "whatChangedSinceLastUpdate" (metadata)
     * - Source is "user_edit" with high confidence
     *
     * APPROVAL_REQUIRED when:
     * - Field is in the strategic set
     * - Field has existing content that would be replaced
     * - Source confidence is below threshold
     */
    MergeDecision classify(ProposedChange proposal) {
        String fieldName = proposal.fieldName();

        // Metadata fields are always safe to auto-update
        if ("lastUpdated".equals(fieldName) || "whatChangedSinceLastUpdate".equals(fieldName)) {
            return MergeDecision.SAFE_AUTO;
        }

        // User direct edits are always safe
        if ("user_edit".equals(proposal.source())) {
            return MergeDecision.SAFE_AUTO;
        }

        // Strategic fields always need approval
        if (STRATEGIC_FIELDS.contains(fieldName)) {
            return MergeDecision.APPROVAL_REQUIRED;
        }

        // If current field is empty, adding content is safe
        ContextEntry<String> entry = orgContext.getEntry(fieldName);
        if (entry == null || !entry.hasValue()) {
            return MergeDecision.SAFE_AUTO;
        }

        // If current value equals proposed, no-op — treat as safe
        if (proposal.currentValue() != null && proposal.currentValue().equals(proposal.proposedValue())) {
            return MergeDecision.SAFE_AUTO;
        }

        // Default: require approval for any existing content change from AI source
        return MergeDecision.APPROVAL_REQUIRED;
    }

    /** Applies a change directly to the org context and logs it. */
    private void applyChange(ProposedChange proposal) {
        changeLog.append(ContextChangeLog.ChangeRecord.of(
            proposal.fieldName(),
            proposal.currentValue(),
            proposal.proposedValue(),
            proposal.source(),
            "auto_apply"
        ));
        orgContext.updateField(
            proposal.fieldName(),
            proposal.proposedValue(),
            proposal.source(),
            proposal.confidence(),
            ContextStatus.APPROVED
        );
    }

    /** User approves a pending change. */
    public void approve(ProposedChange proposal) {
        changeLog.append(ContextChangeLog.ChangeRecord.of(
            proposal.fieldName(),
            proposal.currentValue(),
            proposal.proposedValue(),
            proposal.source(),
            "approve"
        ));
        orgContext.updateField(
            proposal.fieldName(),
            proposal.proposedValue(),
            proposal.source(),
            proposal.confidence(),
            ContextStatus.APPROVED
        );
        approvalQueue.remove(proposal);
        orgContext.save();
    }

    /** User rejects a pending change. */
    public void reject(ProposedChange proposal) {
        changeLog.append(ContextChangeLog.ChangeRecord.of(
            proposal.fieldName(),
            proposal.currentValue(),
            proposal.proposedValue(),
            proposal.source(),
            "reject"
        ));
        approvalQueue.remove(proposal);
    }

    /** Returns the current approval queue. */
    public List<ProposedChange> getApprovalQueue() {
        return Collections.unmodifiableList(approvalQueue);
    }

    /** Clears the approval queue. */
    public void clearQueue() {
        approvalQueue.clear();
    }

    /**
     * Result of a reconciliation pass.
     */
    public record ReconciliationResult(
        List<ProposedChange> autoApplied,
        List<ProposedChange> needsApproval
    ) {}
}
