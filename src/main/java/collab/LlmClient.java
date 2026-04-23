package collab;

import java.util.List;

// One method: send a system prompt + message history, get back text.
// Blocking HTTP. No streaming, no tools, no stateful IDs.
public interface LlmClient {
    String send(String systemInstruction, List<ChatMessage> messages);
}
