package cowork.llm;

// ToolResult — the outcome of one ToolCall. Errors are data, not exceptions:
// `isError=true` plus a message lets the model see the failure and recover
// instead of aborting the whole turn.

public record ToolResult(String callId,
                         String content,
                         boolean isError) {

    public ToolResult {
        if (callId == null) callId = "";
        if (content == null) content = "";
    }

    public static ToolResult ok(String callId, String content) {
        return new ToolResult(callId, content, false);
    }

    public static ToolResult error(String callId, String message) {
        return new ToolResult(callId, message, true);
    }
}
