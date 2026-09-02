package cowork.context;

/**
 * How a ProposedChange is handled: SAFE_AUTO is applied immediately (additive, low-risk or
 * metadata-only); APPROVAL_REQUIRED is queued for the user (strategic field or an overwrite).
 */
public enum MergeDecision {
    SAFE_AUTO,
    APPROVAL_REQUIRED
}
