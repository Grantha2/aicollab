package collab;

import java.util.ArrayList;
import java.util.List;

// Runs the 3-phase debate:
//   1. each agent answers independently
//   2. each agent reacts to the others (debateRounds times)
//   3. the first client synthesizes everything
// Keeps in-memory list of past syntheses so later cycles see them.
public class Maestro {

    @FunctionalInterface
    public interface Listener {
        // kind is "status" for status updates, otherwise "phase1" / "phase2" / "synthesis".
        void on(String kind, String model, String perspective, String text);
    }

    private final List<LlmClient> clients;
    private final List<Agent> agents;
    private final int debateRounds;
    private final String contextBlob;
    private final List<String> syntheses = new ArrayList<>();
    private Listener listener;

    public Maestro(List<LlmClient> clients, List<Agent> agents, String contextBlob, int debateRounds) {
        if (clients.size() != agents.size() || agents.size() < 2)
            throw new IllegalArgumentException("Need matching clients for >=2 agents");
        this.clients = List.copyOf(clients);
        this.agents = List.copyOf(agents);
        this.contextBlob = contextBlob == null ? "" : contextBlob;
        this.debateRounds = debateRounds;
    }

    public void setListener(Listener l) { this.listener = l; }
    public int apiCallCount() { return 1 + agents.size() + agents.size() * debateRounds; }

    public void runDebate(String userPrompt) {
        int n = agents.size();
        String[] phase1 = new String[n];
        String[] latest = new String[n];

        for (int i = 0; i < n; i++) {
            Agent a = agents.get(i);
            emitStatus("Phase 1: " + a.name() + " responding...");
            String text = clients.get(i).send(systemInstruction(a),
                    List.of(new ChatMessage("user", "=== QUESTION ===\n" + userPrompt)));
            phase1[i] = latest[i] = text;
            emitMessage(a, text, "phase1");
        }
        for (int round = 1; round <= debateRounds; round++) {
            String[] roundOut = new String[n];
            for (int i = 0; i < n; i++) {
                Agent a = agents.get(i);
                emitStatus("Phase 2 round " + round + ": " + a.name() + " reacting...");
                String reaction = clients.get(i).send(systemInstruction(a),
                        List.of(new ChatMessage("user", phase2Body(a, userPrompt, latest, i))));
                roundOut[i] = reaction;
                emitMessage(a, reaction, "phase2");
            }
            latest = roundOut;
        }
        emitStatus("Phase 3: synthesizing...");
        String synthesis = clients.get(0).send(systemInstruction(agents.get(0)),
                List.of(new ChatMessage("user", phase3Body(userPrompt, phase1, latest))));
        syntheses.add(synthesis);
        if (listener != null) listener.on("synthesis", "Synthesis", "", synthesis);
        emitStatus("Done.");
    }

    private String systemInstruction(Agent agent) {
        StringBuilder sb = new StringBuilder();
        if (!contextBlob.isBlank()) sb.append("=== CONTEXT ===\n").append(contextBlob.trim()).append("\n\n");
        sb.append(agent.briefing());
        if (!syntheses.isEmpty()) {
            sb.append("=== PRIOR CYCLE CONCLUSIONS ===\n");
            for (int i = 0; i < syntheses.size(); i++)
                sb.append("--- Cycle ").append(i + 1).append(" ---\n").append(syntheses.get(i)).append("\n\n");
        }
        return sb.toString();
    }

    private String phase2Body(Agent self, String userPrompt, String[] latest, int selfIdx) {
        StringBuilder sb = new StringBuilder();
        sb.append("THE QUESTION:\n").append(userPrompt).append("\n\n");
        sb.append("You are ").append(self.name()).append(". Other panelists responded:\n\n");
        for (int j = 0; j < agents.size(); j++) {
            if (j == selfIdx) continue;
            sb.append("--- ").append(agents.get(j).name()).append(" ---\n").append(latest[j]).append("\n\n");
        }
        sb.append("React: where do you agree, disagree, or think they're missing something?");
        return sb.toString();
    }

    private String phase3Body(String userPrompt, String[] phase1, String[] latest) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are orchestrating a multi-AI advisory panel. Produce a synthesis with: "
                + "points of agreement, key disagreements, missed insights, and a concrete recommendation.\n\n");
        sb.append("=== QUESTION ===\n").append(userPrompt).append("\n\n");
        sb.append("=== INITIAL RESPONSES ===\n");
        for (int i = 0; i < agents.size(); i++)
            sb.append("--- ").append(agents.get(i).name()).append(" (").append(agents.get(i).perspective())
              .append(") ---\n").append(phase1[i]).append("\n\n");
        sb.append("=== REACTIONS ===\n");
        for (int i = 0; i < agents.size(); i++)
            sb.append("--- ").append(agents.get(i).name()).append(" ---\n").append(latest[i]).append("\n\n");
        return sb.toString();
    }

    private void emitStatus(String s) { if (listener != null) listener.on("status", "", "", s); }
    private void emitMessage(Agent a, String t, String phase) {
        if (listener != null) listener.on(phase, a.name(), a.perspective(), t);
    }
}
