package collab;

// ============================================================
// ToolResult.java — The result of executing a ToolCall.
//
// WHAT THIS RECORD DOES (one sentence):
// Bundles the textual output of a tool handler with the originating
// call id and an error flag, ready to be serialised into whichever
// provider's tool_result / tool_response block is appropriate.
//
// WHY `isError` INSTEAD OF THROWING:
// Tool errors are data, not control-flow. When a tool fails, we still
// want the LLM to see the failure so it can recover (retry with
// different args, apologise to the user, etc.) rather than having
// the whole debate abort. `isError=true` plus an explanatory `content`
// string is the universal "the tool failed but the turn continues"
// signal every provider supports.
//
// WHY content IS A PLAIN STRING:
// All three providers accept text results. Richer result types
// (images, attachments, tool-result structs) are possible but out of
// scope for Phase 1 of tool-calling. If we later need binary tool
// output, we add an optional byte[] / MIME sibling; today, string is
// the minimum viable contract.
// ============================================================
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
