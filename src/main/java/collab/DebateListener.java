package collab;

public interface DebateListener {
    void onPhase1Response(String model, String perspective, String response);
    void onPhase2Reaction(int round, String model, String perspective, String reaction);
    void onSynthesis(String synthesis);
    void onStatusUpdate(String message);
}
