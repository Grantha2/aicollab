package collab;

/**
 * Classification of how a proposed change should be handled.
 */
public enum MergeDecision {
    SAFE_AUTO,          // auto-apply: additive, low-risk, or metadata-only
    APPROVAL_REQUIRED   // queue for user decision: strategic field or significant change
}
