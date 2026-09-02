package cowork.llm;

// LlmClient — the provider-neutral contract every task codes against.
// sendMessage(String) is the only abstract method; the richer entry points
// default to flatten-and-delegate so a mock client needs one method.
// Implementations return "[... ERROR ...]" strings rather than throwing.

public interface LlmClient {

    String sendMessage(String prompt);

    default String sendMessage(LlmRequest request) {
        StringBuilder flat = new StringBuilder();
        if (request.systemInstruction() != null && !request.systemInstruction().isEmpty()) {
            flat.append(request.systemInstruction()).append("\n\n");
        }
        for (ChatMessage msg : request.messages()) {
            flat.append(msg.content()).append("\n\n");
        }
        return sendMessage(flat.toString().trim());
    }

    /** Multi-turn path: previousStateId continues an earlier reply; the returned id continues this one. */
    default StatefulResponse sendStateful(LlmRequest request, String previousStateId) {
        return new StatefulResponse(sendMessage(request), null);
    }

    /** Tool-use path: capable clients loop tool_use -> executor.executeAll -> tool_result until text. */
    default StatefulResponse sendStateful(LlmRequest request, String previousStateId,
                                          ToolExecutor executor) {
        return sendStateful(request, previousStateId);
    }
}
