package collab;

// ============================================================
// DebateListener — Callback interface the Maestro uses to notify
// the UI (MainGui) of debate progress.
//
// WHY slotIndex:
// The panel is no longer fixed at three (Claude/GPT/Gemini). Any
// mix of providers is allowed, including duplicates (two Claudes).
// The GUI needs a stable index to route each response to the
// correct dynamically-built stream panel — display name alone is
// ambiguous once duplicates exist.
// ============================================================
public interface DebateListener {
    void onPhase1Response(int slotIndex, String model, String perspective, String response);
    void onPhase2Reaction(int slotIndex, int round, String model, String perspective, String reaction);
    void onSynthesis(String synthesis);
    void onStatusUpdate(String message);
}
