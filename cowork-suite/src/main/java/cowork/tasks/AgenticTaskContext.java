package cowork.tasks;

import cowork.config.ClientFactory;
import cowork.config.Config;
import cowork.context.ContextChangeLog;
import cowork.context.OrganizationContext;
import cowork.context.ReconciliationService;

/**
 * Shared services handed to every agentic task. {@code clients} is the only way a task
 * obtains an LLM client; {@code output} replaces the legacy direct reference to the
 * routines panel so tasks stay UI-agnostic.
 */
public record AgenticTaskContext(
    OrganizationContext orgContext,
    ReconciliationService reconciliation,
    ContextChangeLog changeLog,
    Config config,
    ClientFactory clients,
    TaskOutput output
) {}
