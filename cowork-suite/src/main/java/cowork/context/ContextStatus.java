package cowork.context;

/**
 * Approval status of a context entry: APPROVED (confirmed by the user or auto-applied as
 * safe), PROVISIONAL (applied, awaiting confirmation), PENDING_REVIEW (queued, not yet
 * applied), ARCHIVED (inactive, kept for history).
 */
public enum ContextStatus {
    APPROVED,
    PROVISIONAL,
    PENDING_REVIEW,
    ARCHIVED
}
