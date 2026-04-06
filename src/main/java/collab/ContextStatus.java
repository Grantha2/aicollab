package collab;

/**
 * Approval status of a context entry.
 */
public enum ContextStatus {
    APPROVED,       // confirmed by user or auto-applied as safe
    PROVISIONAL,    // applied but awaiting user confirmation
    PENDING_REVIEW, // queued, not yet applied
    ARCHIVED        // no longer active, kept for history
}
