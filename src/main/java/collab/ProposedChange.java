package collab;

/**
 * A proposed change to a context field, produced by an agentic function.
 *
 * @param fieldName     the OrganizationContext field name (e.g., "topPriorities")
 * @param currentValue  the current value at time of proposal
 * @param proposedValue the new value being suggested
 * @param source        what produced this proposal ("daily_update", "workflow_output", etc.)
 * @param confidence    how confident the source is (0.0–1.0)
 */
public record ProposedChange(
    String fieldName,
    String currentValue,
    String proposedValue,
    String source,
    double confidence
) {}
