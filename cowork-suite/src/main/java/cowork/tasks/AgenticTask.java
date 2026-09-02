package cowork.tasks;

import java.util.List;

/**
 * A registered agentic routine runnable from the Agentic Routines view. Each task owns its
 * identity, availability and execution: it opens its own input dialogs, does the work off
 * the EDT, and reports through {@link TaskOutput}.
 */
public interface AgenticTask {

    /** Unique identifier (e.g. "context-refresh"). */
    String getId();

    /** Display name shown in the sidebar. */
    String getName();

    /** Short description for tooltip or subtitle. */
    String getDescription();

    /** Category for sidebar grouping (e.g. "Context", "Reports"). */
    String getCategory();

    /** False when prerequisites are missing. */
    boolean isAvailable();

    void execute(AgenticTaskContext ctx);

    /** Execute against pre-selected target fields; tasks without field targeting ignore them. */
    default void execute(AgenticTaskContext ctx, List<String> targetFields) {
        execute(ctx);
    }
}
