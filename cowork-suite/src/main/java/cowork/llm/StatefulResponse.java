package cowork.llm;

// StatefulResponse — model text plus the opaque id that continues the chain
// (null when the call failed or the client has no state to continue).
public record StatefulResponse(String text, String stateId) {}
