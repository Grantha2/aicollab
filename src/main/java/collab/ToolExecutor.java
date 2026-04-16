package collab;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// ============================================================
// ToolExecutor.java — Registers tool handlers and dispatches calls.
//
// WHAT THIS CLASS DOES (one sentence):
// Holds a name-keyed registry of ToolSchema + handler pairs and,
// given a list of ToolCalls produced by a model, invokes each
// handler and returns the corresponding ToolResults.
//
// HOW IT FITS THE ARCHITECTURE:
// Each LlmClient, after parsing a tool-use response, calls
// ToolExecutor.executeAll(calls) to get back a parallel list of
// ToolResults. The client then serialises the results into the
// provider-specific tool_result block and continues the chained
// turn. Tool execution is synchronous and in-process — MCP tools are
// dispatched through here too, but the handler for those is a thin
// proxy to McpHost.
//
// ITERATION CAP:
// Tool-call loops are capped at maxIterationsPerTurn (default 5) to
// prevent a model from oscillating indefinitely between tool calls
// and text. The cap is enforced by each client in its turn loop,
// not here — ToolExecutor just runs one batch at a time. Keeping the
// cap in the client lets a stubborn model surface a "max tool calls
// exceeded" message through the normal response channel.
//
// THREAD MODEL:
// ToolExecutor itself is thread-safe for registration (ConcurrentHashMap
// would be overkill; registration happens once at startup and never
// during a debate). Handlers may or may not be thread-safe — that is
// each handler's responsibility. A single debate runs turns sequentially,
// so there is no practical concurrency inside the tool loop today.
// ============================================================
public final class ToolExecutor {

    // The default iteration cap. Each client's turn loop aborts after
    // this many tool batches and returns whatever text the model
    // produced last, plus a "max tool calls exceeded" marker.
    public static final int DEFAULT_MAX_ITERATIONS = 5;

    // Per-name registry. LinkedHashMap preserves registration order
    // when we serialise the full tool list onto each request, which
    // keeps request payloads stable across runs (helpful for audit
    // diffs in the API Request Viewer).
    private final Map<String, Registration> registry = new LinkedHashMap<>();

    private record Registration(ToolSchema schema, Function<ToolCall, ToolResult> handler) {}

    /**
     * Registers a tool. Throws if a tool with the same name is
     * already registered — ambiguity in tool names is a programmer
     * error, not a runtime condition to recover from.
     */
    public void register(ToolSchema schema, Function<ToolCall, ToolResult> handler) {
        if (schema == null || handler == null) {
            throw new IllegalArgumentException("schema and handler required");
        }
        if (registry.containsKey(schema.name())) {
            throw new IllegalStateException("tool already registered: " + schema.name());
        }
        registry.put(schema.name(), new Registration(schema, handler));
    }

    /**
     * Returns the schemas for all registered tools, in registration
     * order. Clients use this to populate the per-provider tools
     * array on outgoing LlmRequests.
     */
    public List<ToolSchema> schemas() {
        List<ToolSchema> out = new ArrayList<>(registry.size());
        for (Registration r : registry.values()) out.add(r.schema());
        return List.copyOf(out);
    }

    /**
     * Runs one batch of tool calls synchronously, in the order the
     * model emitted them, and returns results in the same order. If
     * a call references an unknown tool, the result is an error; if
     * a handler throws, the thrown message becomes an error result.
     * We never let tool-handler exceptions propagate into the client's
     * turn loop — the model has to be allowed to recover.
     */
    public List<ToolResult> executeAll(List<ToolCall> calls) {
        List<ToolResult> out = new ArrayList<>(calls == null ? 0 : calls.size());
        if (calls == null) return out;
        for (ToolCall call : calls) {
            Registration r = registry.get(call.name());
            if (r == null) {
                out.add(ToolResult.error(call.id(),
                        "no handler registered for tool: " + call.name()));
                continue;
            }
            try {
                ToolResult result = r.handler().apply(call);
                if (result == null) {
                    out.add(ToolResult.error(call.id(),
                            "tool handler returned null: " + call.name()));
                } else {
                    out.add(result);
                }
            } catch (Exception e) {
                out.add(ToolResult.error(call.id(),
                        "tool threw " + e.getClass().getSimpleName()
                                + ": " + e.getMessage()));
            }
        }
        return out;
    }

    /**
     * Returns true if any tool is registered. Clients can skip
     * assembling the tools array on LlmRequests when this is false,
     * which keeps request bodies small for debates that don't use
     * tools.
     */
    public boolean hasAny() {
        return !registry.isEmpty();
    }
}
