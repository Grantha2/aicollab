package collab;

import java.util.List;

// Builds the 5-layer prompt:
//   1. team context
//   2. organization context
//   3. agent identity
//   4. stakeholder identity
//   5. conversation history
// plus the user question / phase-specific body.
public class PromptBuilder {

    private final ConversationContext history;
    private final ContextController context;
    private final String teamContext;

    public PromptBuilder(ConversationContext history, ContextController context, String teamContext) {
        this.history = history;
        this.context = context;
        this.teamContext = teamContext == null ? "" : teamContext;
    }

    public String buildSystemInstruction(Profiles.Agent agent, Profiles.Stakeholder stakeholder) {
        return teamContext
             + (context != null ? context.getOrgContextBlock() : "")
             + agent.toBriefing()
             + stakeholder.toBriefing()
             + history.getHistoryBlock();
    }

    public String buildPhase1UserMessage(String userPrompt) {
        return "=== STAKEHOLDER'S QUESTION ===\n" + userPrompt;
    }

    public String buildPhase2Message(Profiles.Agent agent, String userPrompt, List<Peer> peers) {
        StringBuilder sb = new StringBuilder();
        sb.append("THE ORIGINAL QUESTION:\n").append(userPrompt).append("\n\n");
        sb.append("You are ").append(agent.name()).append(". ");
        sb.append("Here are the other panelists' responses. React honestly: where do you agree, "
                + "where do you disagree, and what are they missing?\n\n");
        for (Peer p : peers) {
            sb.append("--- ").append(p.name()).append("'s Response ---\n");
            sb.append(p.response()).append("\n\n");
        }
        sb.append("Write your reaction. Be specific. Call out concrete agreements and disagreements.\n");
        return sb.toString();
    }

    public String buildPhase3SynthesisMessage(String userPrompt,
                                              List<Profiles.Agent> agents,
                                              List<String> phase1,
                                              List<String> reactions) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the orchestrator of a multi-AI advisory panel.\n\n");
        sb.append("The panel just debated this question. Produce a synthesis report with:\n");
        sb.append("  1. Points of agreement\n");
        sb.append("  2. Key disagreements (and who takes which side)\n");
        sb.append("  3. Insights the panel surfaced that the stakeholder might have missed\n");
        sb.append("  4. A concrete recommendation tailored to the active stakeholder\n\n");

        sb.append("=== ORIGINAL QUESTION ===\n").append(userPrompt).append("\n\n");

        sb.append("=== PHASE 1: INITIAL RESPONSES ===\n\n");
        for (int i = 0; i < agents.size(); i++) {
            sb.append("--- ").append(agents.get(i).name())
              .append(" (").append(agents.get(i).perspective()).append(") ---\n");
            sb.append(phase1.get(i)).append("\n\n");
        }

        sb.append("=== PHASE 2: CROSS-REACTIONS ===\n\n");
        for (int i = 0; i < agents.size(); i++) {
            sb.append("--- ").append(agents.get(i).name()).append("'s Reaction ---\n");
            sb.append(reactions.get(i)).append("\n\n");
        }

        return sb.toString();
    }

    public record Peer(String name, String response) {}
}
