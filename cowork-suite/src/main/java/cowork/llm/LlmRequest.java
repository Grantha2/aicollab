package cowork.llm;

// LlmRequest — provider-neutral request envelope: system instruction, ordered
// turns, token budget and (optionally) the tool schemas the model may call.

import java.util.List;

public record LlmRequest(String systemInstruction,
                         List<ChatMessage> messages,
                         int maxTokens,
                         List<ToolSchema> tools) {

    public LlmRequest(String systemInstruction, List<ChatMessage> messages, int maxTokens) {
        this(systemInstruction, messages, maxTokens, List.of());
    }

    // Normalise nulls so every consumer can iterate unconditionally.
    public LlmRequest {
        if (messages == null) messages = List.of();
        if (tools == null) tools = List.of();
    }
}
