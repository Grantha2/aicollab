package collab;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Runs the 3-phase debate:
//   1. each agent responds independently
//   2. each agent reacts to the others
//   3. Claude (first client) synthesizes
//
// No server-side state IDs. Each call sends the full system instruction
// + the specific user/assistant turns relevant for that phase.
public class Maestro {

    private final List<LlmClient> clients;
    private final List<Profiles.Agent> agents;
    private final PromptBuilder promptBuilder;
    private final ConversationContext context;
    private final int debateRounds;
    private final SessionStore sessionStore;

    public Maestro(List<LlmClient> clients,
                   List<Profiles.Agent> agents,
                   PromptBuilder promptBuilder,
                   ConversationContext context,
                   int debateRounds,
                   SessionStore sessionStore) {
        if (clients.size() != agents.size() || agents.size() < 2) {
            throw new IllegalArgumentException("Need >=2 agents with matching clients");
        }
        this.clients = List.copyOf(clients);
        this.agents = List.copyOf(agents);
        this.promptBuilder = promptBuilder;
        this.context = context;
        this.debateRounds = debateRounds;
        this.sessionStore = sessionStore;
    }

    // Optional callbacks so the GUI can stream events. Nulls allowed.
    public static final class Listener {
        public Consumer<String> onStatus;
        public Phase1Cb onPhase1;
        public Phase2Cb onPhase2;
        public Consumer<String> onSynthesis;
    }
    @FunctionalInterface public interface Phase1Cb {
        void fire(int slotIndex, String model, String perspective, String response);
    }
    @FunctionalInterface public interface Phase2Cb {
        void fire(int slotIndex, int round, String model, String perspective, String reaction);
    }

    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void status(String s) {
        if (listener != null && listener.onStatus != null) listener.onStatus.accept(s);
    }

    public int getApiCallCount() {
        int n = agents.size();
        return 1 + n + (n * debateRounds);
    }

    public void runDebate(String userPrompt, Profiles.Stakeholder stakeholder) {
        int cycle = context.getCycleCount() + 1;
        int n = agents.size();

        String[] phase1 = new String[n];
        String[] latest = new String[n];

        // --- Phase 1: independent ---
        status("Phase 1: independent responses");
        for (int i = 0; i < n; i++) {
            Profiles.Agent agent = agents.get(i);
            status("Calling " + agent.name() + " (" + agent.perspective() + ")...");
            String sys = promptBuilder.buildSystemInstruction(agent, stakeholder);
            String msg = promptBuilder.buildPhase1UserMessage(userPrompt);
            String text = clients.get(i).send(sys, List.of(new ChatMessage("user", msg)));
            phase1[i] = text;
            latest[i] = text;
            sessionStore.appendTurn(cycle, "phase1", agent.name(), text);
            if (listener != null && listener.onPhase1 != null) {
                listener.onPhase1.fire(i, agent.name(), agent.perspective(), text);
            }
        }

        // --- Phase 2: cross-reaction rounds ---
        for (int round = 1; round <= debateRounds; round++) {
            status("Phase 2: reaction round " + round + " of " + debateRounds);
            String[] roundOut = new String[n];
            for (int i = 0; i < n; i++) {
                Profiles.Agent agent = agents.get(i);
                status("Round " + round + ": " + agent.name() + " reacting...");
                List<PromptBuilder.Peer> peers = new ArrayList<>();
                for (int j = 0; j < n; j++) {
                    if (j == i) continue;
                    peers.add(new PromptBuilder.Peer(agents.get(j).name(), latest[j]));
                }
                String sys = promptBuilder.buildSystemInstruction(agent, stakeholder);
                String body = promptBuilder.buildPhase2Message(agent, userPrompt, peers);
                String reaction = clients.get(i).send(sys, List.of(new ChatMessage("user", body)));
                roundOut[i] = reaction;
                sessionStore.appendTurn(cycle, "phase2-round-" + round, agent.name(), reaction);
                if (listener != null && listener.onPhase2 != null) {
                    listener.onPhase2.fire(i, round, agent.name(), agent.perspective(), reaction);
                }
            }
            latest = roundOut;
        }

        // --- Phase 3: synthesis (first client) ---
        status("Phase 3: synthesizing...");
        String sys = promptBuilder.buildSystemInstruction(agents.get(0), stakeholder);
        String body = promptBuilder.buildPhase3SynthesisMessage(
                userPrompt, agents, java.util.Arrays.asList(phase1), java.util.Arrays.asList(latest));
        String synthesis = clients.get(0).send(sys, List.of(new ChatMessage("user", body)));

        context.addSynthesis(synthesis);
        sessionStore.appendSynthesis(cycle, synthesis);
        status("Debate complete.");
        if (listener != null && listener.onSynthesis != null) listener.onSynthesis.accept(synthesis);
    }
}
