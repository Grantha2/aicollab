package collab;

/**
 * Shared services available to any agentic task during execution.
 *
 * @param orgContext          the organization context to read/write
 * @param reconciliation      reconciliation service for safe context write-back
 * @param changeLog           append-only audit trail
 * @param config              API keys and tuning parameters
 * @param panel               the agentic panel for UI callbacks (show output, approvals, refresh)
 * @param managedAgentClient  optional client for tasks that drive Claude Managed Agents;
 *                            null when the managed-agents subsystem isn't configured
 */
public record AgenticTaskContext(
    OrganizationContext orgContext,
    ReconciliationService reconciliation,
    ContextChangeLog changeLog,
    Config config,
    AgenticRoutinesPanel panel,
    ManagedAgentClient managedAgentClient
) {
    /** Back-compat constructor for tasks that don't need the managed agent client. */
    public AgenticTaskContext(OrganizationContext orgContext,
                              ReconciliationService reconciliation,
                              ContextChangeLog changeLog,
                              Config config,
                              AgenticRoutinesPanel panel) {
        this(orgContext, reconciliation, changeLog, config, panel, null);
    }
}
