package cowork.llm;

// ToolCall — one tool invocation the model asked for. `id` is echoed back on
// the matching tool_result so the API can pair results to calls when several
// tools run in one turn; `arguments` is pre-parsed so handlers never touch JSON.

import java.util.Map;

public record ToolCall(String id,
                       String name,
                       Map<String, Object> arguments) {

    public ToolCall {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ToolCall requires a tool name");
        }
        if (id == null) id = "";
        if (arguments == null) arguments = Map.of();
    }
}
