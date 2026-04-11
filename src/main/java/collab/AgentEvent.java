package collab;

/**
 * One streamed event from a Managed Agents session.
 *
 * Emitted by {@link ManagedAgentClient#sendEvent} as the agent thinks,
 * calls tools, and produces output. The panel renders each event as it
 * arrives.
 *
 * @param type     event kind: "text", "tool_use", "tool_result", "status", "file"
 * @param content  human-readable body (text chunk, tool name, status message, etc.)
 * @param fileId   non-null only when {@code type} is "file" — the Files API id
 *                 of an output the app can download with
 *                 {@link ManagedAgentClient#downloadFile}
 */
public record AgentEvent(String type, String content, String fileId) {

    public static AgentEvent text(String content) {
        return new AgentEvent("text", content, null);
    }

    public static AgentEvent toolUse(String content) {
        return new AgentEvent("tool_use", content, null);
    }

    public static AgentEvent toolResult(String content) {
        return new AgentEvent("tool_result", content, null);
    }

    public static AgentEvent status(String content) {
        return new AgentEvent("status", content, null);
    }

    public static AgentEvent file(String fileId, String content) {
        return new AgentEvent("file", content, fileId);
    }
}
