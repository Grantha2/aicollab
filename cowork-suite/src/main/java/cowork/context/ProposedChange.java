package cowork.context;

/**
 * A change to one OrganizationContext field proposed by an agentic task.
 * currentValue is the value seen at proposal time; source names the producer
 * ("daily_update", "workflow_output", ...); confidence is 0.0-1.0.
 */
public record ProposedChange(
    String fieldName,
    String currentValue,
    String proposedValue,
    String source,
    double confidence
) {}
