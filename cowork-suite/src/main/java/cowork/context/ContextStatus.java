package cowork.context;

// Approval status of a context entry: APPROVED (user-confirmed or auto-applied as safe),
// PROVISIONAL (applied, awaiting confirmation), PENDING_REVIEW (queued, not applied), ARCHIVED (history only).
public enum ContextStatus {
    APPROVED,
    PROVISIONAL,
    PENDING_REVIEW,
    ARCHIVED
}
