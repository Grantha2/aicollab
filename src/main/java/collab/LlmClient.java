package collab;

// ============================================================
// LlmClient.java — The interface every AI model must implement.
//
// WHAT THIS CLASS DOES (one sentence):
// Defines a contract — one method, sendMessage() — that every
// AI client (Claude, GPT, Gemini) must follow.
//
// HOW IT FITS THE ARCHITECTURE:
// Orchestrator.java calls sendMessage() on each client. It doesn't
// know or care whether it's talking to Claude, GPT, Gemini, or a
// mock. All it knows is: I give you a prompt, you give me a response.
//
// WHY THIS MATTERS (even though it's just 3 lines of code):
//
// 1. ADDING A NEW MODEL = ONE NEW FILE.
//    Want to add Llama, Mistral, or your own model? Create a new
//    class that implements LlmClient, write its sendMessage(), and
//    pass it to the Orchestrator. Zero changes to Orchestrator.java,
//    zero changes to Main.java, zero changes to PromptBuilder.java.
//
// 2. TESTING WITHOUT API CALLS = ONE MOCK CLASS.
//    Create a class called MockClient that implements LlmClient
//    and returns canned responses. Pass it to Orchestrator instead
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
//    that all plug into the same Orchestrator. That's the actual
//    reason interfaces exist in professional code.
//
// WHAT IS AN INTERFACE?
// An interface is a promise: "any class that implements me MUST
// provide these methods." It's like a contract. The Orchestrator
// says "I need something that can sendMessage()" and doesn't care
// HOW it sends the message — HTTP to Claude? HTTP to GPT? Read
// from a file? Doesn't matter. The interface guarantees the method
// exists, so the Orchestrator can call it safely.
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
}
