package collab;

/**
 * A registered agentic routine that can be executed from the Agentic Tab.
 *
 * Examples: "Refresh Context", "Create Teams Task", "Weekly Report", etc.
 * Each task defines its identity, availability, and execution logic.
 */
public interface AgenticTask {

    /** Unique identifier (e.g., "context-refresh", "teams-create-task"). */
    String getId();

    /** Display name shown in sidebar (e.g., "Refresh Context"). */
    String getName();

    /** Short description for tooltip or subtitle. */
    String getDescription();

    /** Category for sidebar grouping (e.g., "Context", "Integrations", "Reports"). */
    String getCategory();

    /**
     * Whether this task can currently run.
     * Return false if prerequisites are missing (e.g., API not configured).
     */
    boolean isAvailable();

    /**
     * Execute the task. Called from a SwingWorker or directly on EDT
     * depending on the task's needs. The task is responsible for showing
     * its own input dialogs, running background work, and calling back
     * to the panel for output display.
     *
     * @param ctx shared services for the task
     */
    void execute(AgenticTaskContext ctx);

    /**
     * Execute the task with pre-selected target fields.
     * Default implementation ignores the fields and calls execute(ctx).
     * Override in tasks that support field-level targeting (like context refresh).
     */
    default void execute(AgenticTaskContext ctx, java.util.List<String> targetFields) {
        execute(ctx);
    }
}
