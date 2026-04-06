package collab;

/**
 * Shared services available to any agentic task during execution.
 *
 * @param orgContext       the organization context to read/write
 * @param reconciliation   reconciliation service for safe context write-back
 * @param changeLog        append-only audit trail
 * @param config           API keys and tuning parameters
 * @param panel            the agentic panel for UI callbacks (show output, approvals, refresh)
 */
public record AgenticTaskContext(
    OrganizationContext orgContext,
    ReconciliationService reconciliation,
    ContextChangeLog changeLog,
    Config config,
    AgenticRoutinesPanel panel
) {}
