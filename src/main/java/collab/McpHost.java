package collab;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

// ============================================================
// McpHost.java — Lifecycle owner for all MCP sessions in a debate.
//
// WHAT THIS CLASS DOES (one sentence):
// Starts an McpClientSession for each McpServerAttachment, loads
// each server's advertised tools (filtered by the attachment's
// allowedTools whitelist), and registers them as proxy handlers on
// a ToolExecutor so panelists can invoke them like any other tool.
//
// HOW IT FITS THE ARCHITECTURE:
// Maestro (or in the future AgenticRoutinesPanel for workflow-scope
// hosts) constructs an McpHost at the start of a debate from the
// active ProfileSet's attachments, calls start() to spawn processes
// and enumerate tools, and then tears it down via close() when the
// debate finishes. The ToolExecutor passed in grows by one registered
// handler per server tool — panelist clients then see every MCP
// tool alongside any in-process tools.
//
// WHY SEPARATE FROM McpClientSession:
// A session is one conversation with one server. The host manages
// many sessions at once, routes tool calls to the right session by
// tool name, and handles graceful degradation when a session fails
// at start (server never came up, bad transport, schema mismatch).
// The split lets us unit-test the session logic independently of
// the multi-server bookkeeping.
//
// FAILURE MODES:
//   - Process fails to spawn → logged, debate continues without
//     that server's tools.
//   - Initialize handshake fails → same as above.
//   - A single tool call fails → the call returns an error ToolResult;
//     the debate and other tools keep working.
// ============================================================
public final class McpHost implements AutoCloseable {

    private final HttpClient http;
    private final Map<String, McpClientSession> sessionsByTool = new LinkedHashMap<>();
    private final List<McpClientSession> allSessions = new ArrayList<>();
    private final Gson gson = new Gson();

    public McpHost(HttpClient http) {
        this.http = http;
    }

    /**
     * Opens sessions for every McpServerAttachment in the list and
     * registers each server's whitelisted tools on the given
     * executor. Returns the count of successfully-registered tools.
     * Call once at debate start; call close() once at debate end.
     */
    public int start(List<? extends ContextAttachment> attachments, ToolExecutor executor) {
        int registered = 0;
        if (attachments == null) return 0;
        for (ContextAttachment att : attachments) {
            if (!(att instanceof McpServerAttachment mcp)) continue;
            try {
                McpClientSession session = new McpClientSession(mcp, http);
                session.initialize();
                JsonArray tools = session.listTools();
                int count = registerTools(session, mcp, tools, executor);
                if (count > 0) {
                    allSessions.add(session);
                    registered += count;
                } else {
                    // No tools matched the whitelist — server is useless
                    // for this debate; close it rather than hold the process.
                    session.close();
                }
            } catch (IOException ioe) {
                System.err.println("[McpHost] Failed to start server \""
                        + mcp.name() + "\": " + ioe.getMessage());
            }
        }
        return registered;
    }

    private int registerTools(McpClientSession session,
                              McpServerAttachment mcp,
                              JsonArray advertised,
                              ToolExecutor executor) {
        int count = 0;
        for (JsonElement el : advertised) {
            if (!el.isJsonObject()) continue;
            JsonObject toolDesc = el.getAsJsonObject();
            if (!toolDesc.has("name")) continue;
            String toolName = toolDesc.get("name").getAsString();
            if (!mcp.allowedTools().isEmpty() && !mcp.allowedTools().contains(toolName)) {
                continue;
            }
            // Namespace the tool so two servers advertising the same
            // tool name don't collide in the executor. The prefix
            // makes per-server traces easy to read in the viewer.
            String scopedName = mcp.name() + "." + toolName;
            String description = toolDesc.has("description")
                    ? toolDesc.get("description").getAsString() : "";
            Map<String, Object> parameterSchema = toolDesc.has("inputSchema")
                    ? jsonObjectToMap(toolDesc.getAsJsonObject("inputSchema"))
                    : Map.of("type", "object", "properties", Map.of());
            ToolSchema schema = new ToolSchema(scopedName, description, parameterSchema);

            McpClientSession boundSession = session;
            String boundToolName = toolName;
            executor.register(schema, call -> {
                try {
                    String output = boundSession.callTool(boundToolName, call.arguments());
                    return ToolResult.ok(call.id(), output);
                } catch (IOException ioe) {
                    return ToolResult.error(call.id(),
                            "MCP tool \"" + scopedName + "\" failed: " + ioe.getMessage());
                }
            });

            sessionsByTool.put(scopedName, session);
            count++;
        }
        return count;
    }

    /**
     * Recursive JsonObject -> Map<String,Object> conversion so the
     * ToolSchema carries a pure-Java tree that each provider's Gson
     * can re-serialise into its native tools payload. We walk the
     * tree ourselves rather than use gson.fromJson(..., Map.class)
     * because the latter loses JSON number/int distinctions in ways
     * that confuse some providers' schema validators.
     */
    private Map<String, Object> jsonObjectToMap(JsonObject obj) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            out.put(e.getKey(), jsonElementToJava(e.getValue()));
        }
        return out;
    }

    private Object jsonElementToJava(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) {
            var p = el.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsNumber();
            return p.getAsString();
        }
        if (el.isJsonArray()) {
            List<Object> arr = new ArrayList<>();
            for (JsonElement x : el.getAsJsonArray()) arr.add(jsonElementToJava(x));
            return arr;
        }
        if (el.isJsonObject()) {
            return jsonObjectToMap(el.getAsJsonObject());
        }
        return null;
    }

    @Override
    public void close() {
        for (McpClientSession s : allSessions) {
            try { s.close(); } catch (Exception ignored) {}
        }
        allSessions.clear();
        sessionsByTool.clear();
    }

    public int activeSessionCount() {
        return allSessions.size();
    }
}
