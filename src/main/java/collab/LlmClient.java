package collab;

// ============================================================
// LlmClient.java — The interface every AI model must implement.
//
// WHAT THIS CLASS DOES (one sentence):
// Defines a contract — one method, sendMessage() — that every
// AI client (Claude, GPT, Gemini) must follow.
//
// HOW IT FITS THE ARCHITECTURE:
// Maestro.java calls sendMessage() on each client. It doesn't
// know or care whether it's talking to Claude, GPT, Gemini, or a
// mock. All it knows is: I give you a prompt, you give me a response.
//
// WHY THIS MATTERS (even though it's just 3 lines of code):
//
// 1. ADDING A NEW MODEL = ONE NEW FILE.
//    Want to add Llama, Mistral, or your own model? Create a new
//    class that implements LlmClient, write its sendMessage(), and
//    pass it to the Maestro. Zero changes to Maestro.java,
//    zero changes to Main.java, zero changes to PromptBuilder.java.
//
// 2. TESTING WITHOUT API CALLS = ONE MOCK CLASS.
//    Create a class called MockClient that implements LlmClient
//    and returns canned responses. Pass it to Maestro instead
//    of real clients. No money spent, no network needed, instant
//    tests. Example:
//
//      public class MockClient implements LlmClient {
//          public String sendMessage(String prompt) {
//              return "Mock response for testing";
//          }
//      }
//
// 3. YOUR PROFESSOR SEES POLYMORPHISM USED FOR A REAL PURPOSE.
//    This isn't a textbook exercise where you create Animal and
//    Dog and Cat. This is a real system where the interface lets
//    four different team members write four different implementations
//    that all plug into the same Maestro. That's the actual
//    reason interfaces exist in professional code.
//
// WHAT IS AN INTERFACE?
// An interface is a promise: "any class that implements me MUST
// provide these methods." It's like a contract. The Maestro
// says "I need something that can sendMessage()" and doesn't care
// HOW it sends the message — HTTP to Claude? HTTP to GPT? Read
// from a file? Doesn't matter. The interface guarantees the method
// exists, so the Maestro can call it safely.
// ============================================================

public interface LlmClient {

    // ============================================================
    // sendMessage() — Send a prompt to an AI model and get a response.
    //
    // PARAMETER:
    //   prompt — the full text to send (already includes all context
    //            layers: team context, agent identity, stakeholder
    //            profile, and the user's actual question)
    //
    // RETURNS:
    //   The model's response as a plain text string.
    //   If something goes wrong (network error, bad API key, etc.),
    //   the implementation returns an error message string starting
    //   with "[ERROR]" rather than throwing an exception. This keeps
    //   the debate going even if one model fails.
    // ============================================================
    String sendMessage(String prompt);

    // ============================================================
    // sendMessage(LlmRequest) — New structured request path.
    //
    // WHY THIS IS A DEFAULT METHOD:
    // We are migrating in phases. Existing clients already implement
    // sendMessage(String). By providing a default implementation here,
    // we can introduce LlmRequest without breaking any existing code.
    //
    // MIGRATION BEHAVIOR:
    // For now, the default path flattens systemInstruction + messages
    // into one string and delegates to the legacy method. As each
    // provider is upgraded, it can override this method to send true
    // multi-turn payloads natively.
    // ============================================================
    default String sendMessage(LlmRequest request) {
        StringBuilder flat = new StringBuilder();

        if (request.systemInstruction() != null
                && !request.systemInstruction().isEmpty()) {
            flat.append(request.systemInstruction()).append("\n\n");
        }

        for (ChatMessage msg : request.messages()) {
            flat.append(msg.content()).append("\n\n");
        }

        return sendMessage(flat.toString().trim());
    }

    // ============================================================
    // sendStateful() — Stateful conversation path.
    //
    // Providers that support server-side conversation state (OpenAI
    // Responses API, Gemini Interactions API) override this to pass
    // a previous state ID and receive a new one. The default falls
    // back to the stateless sendMessage path with a null stateId.
    // ============================================================
    default StatefulResponse sendStateful(LlmRequest request, String previousStateId) {
        String text = sendMessage(request);
        return new StatefulResponse(text, null);
    }

    // ============================================================
    // sendStateful() with a ToolExecutor — the tool-use path.
    //
    // When the request carries tool schemas AND the caller provides
    // a non-null ToolExecutor, the client is expected to:
    //   1. Serialise the tools into its provider-specific tools array.
    //   2. Send the turn.
    //   3. If the provider returns a tool-use / function-call block,
    //      invoke executor.executeAll(calls), append the results to
    //      the same stateful conversation, and continue requesting
    //      text from the model.
    //   4. Loop until the provider returns plain text or the call
    //      count exceeds ToolExecutor.DEFAULT_MAX_ITERATIONS.
    //
    // The default implementation falls through to the no-tool path,
    // so any client that has not yet implemented tool-calling keeps
    // compiling and behaves identically to today. Clients that DO
    // implement the loop override this method. This is the same
    // pattern as the sendMessage(LlmRequest) -> sendMessage(String)
    // fall-through above: additive, opt-in, never-breaking.
    // ============================================================
    default StatefulResponse sendStateful(LlmRequest request,
                                          String previousStateId,
                                          ToolExecutor executor) {
        return sendStateful(request, previousStateId);
    }
}
