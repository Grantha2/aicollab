package collab;

import java.util.List;

// Every AI model implements one method: send a system + message list,
// get back text. Blocking HTTP. No streaming, no tools, no state IDs.
public interface LlmClient {
    String send(String systemInstruction, List<ChatMessage> messages);
}
