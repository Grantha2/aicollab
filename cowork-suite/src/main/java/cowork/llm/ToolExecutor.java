package cowork.llm;

// ToolExecutor — name-keyed registry of ToolSchema + handler pairs. The client
// hands it one batch of ToolCalls per model turn and gets back parallel
// ToolResults; handler exceptions become error results so the model can
// recover. The iteration cap is enforced by the client's turn loop, not here.

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ToolExecutor {

    public static final int DEFAULT_MAX_ITERATIONS = 5;

    // Insertion order keeps the serialised tools array stable across runs.
    private final Map<String, Registration> registry = new LinkedHashMap<>();

    private record Registration(ToolSchema schema, Function<ToolCall, ToolResult> handler) {}

    /** Duplicate names are a programmer error, hence the throw. */
    public void register(ToolSchema schema, Function<ToolCall, ToolResult> handler) {
        if (schema == null || handler == null) {
            throw new IllegalArgumentException("schema and handler required");
        }
        if (registry.containsKey(schema.name())) {
            throw new IllegalStateException("tool already registered: " + schema.name());
        }
        registry.put(schema.name(), new Registration(schema, handler));
    }

    public List<ToolSchema> schemas() {
        List<ToolSchema> out = new ArrayList<>(registry.size());
        for (Registration r : registry.values()) out.add(r.schema());
        return List.copyOf(out);
    }

    /** Runs one batch in order; unknown tools, null returns and thrown exceptions become error results. */
    public List<ToolResult> executeAll(List<ToolCall> calls) {
        List<ToolResult> out = new ArrayList<>(calls == null ? 0 : calls.size());
        if (calls == null) return out;
        for (ToolCall call : calls) {
            Registration r = registry.get(call.name());
            if (r == null) {
                out.add(ToolResult.error(call.id(), "no handler registered for tool: " + call.name()));
                continue;
            }
            try {
                ToolResult result = r.handler().apply(call);
                out.add(result != null ? result
                        : ToolResult.error(call.id(), "tool handler returned null: " + call.name()));
            } catch (Exception e) {
                out.add(ToolResult.error(call.id(),
                        "tool threw " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return out;
    }

    public boolean hasAny() {
        return !registry.isEmpty();
    }
}
