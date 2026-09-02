package cowork.tasks;

import java.util.List;

/**
 * A registered agentic routine runnable from the Agentic Routines view. Each task owns its
 * identity, availability and execution: it opens its own input dialogs, does the work off
 * the EDT, and reports through {@link TaskOutput}.
 */
public interface AgenticTask {

    String getId();

    String getName();

    String getDescription();

    String getCategory();

    boolean isAvailable();

    void execute(AgenticTaskContext ctx);

    /** Execute against pre-selected target fields; tasks without field targeting ignore them. */
    default void execute(AgenticTaskContext ctx, List<String> targetFields) {
        execute(ctx);
    }
}
