package collab;

// ============================================================
// PromptBuilder.java — Builds all prompt types for the debate cycle.
//
// WHAT THIS CLASS DOES (one sentence):
// Assembles the multi-layered "onion" prompts for Phase 1, Phase 2,
// and Phase 3 by combining team context, agent identity, stakeholder
// profile, conversation history, and the user's question.
//
// HOW IT FITS THE ARCHITECTURE:
// Orchestrator calls PromptBuilder's methods to construct the prompts
// before sending them to each LlmClient. PromptBuilder doesn't make
// any API calls itself — it only builds the text.
//
// THE ONION MODEL (layered context):
// Every API call carries three layers of context, innermost to outermost:
//   1. TEAM CONTEXT    — shared by all agents (what kind of panel this is)
//   2. AGENT IDENTITY  — unique per model (AgentProfile.toBriefing())
//   3. STAKEHOLDER     — who's asking (StakeholderProfile.toBriefing())
//   4. HISTORY         — what was discussed before (ConversationContext)
//   5. THE QUESTION    — the user's actual prompt
//
// This isn't framework magic. It's just string concatenation — we
// prepend context to the prompt before sending it. The AI reads the
// context and adjusts its response. Simple mechanism, powerful effect.
// ============================================================

public class PromptBuilder {

    // The "middle layer" of the onion — shared by all agents.
    // Tells every model what kind of team they're part of.
    private static final String TEAM_CONTEXT =
            "=== COLLABORATION CONTEXT ===\n"
          + "You are one of three AI collaborators helping a team of four university students\n"
          + "build an AI collaboration platform as their final project (5-week deadline).\n"
          + "The panel consists of Claude (Anthropic), GPT (OpenAI), and Gemini (Google).\n"
          + "You are EQUAL PARTNERS \u2014 no one agent leads or outranks the others.\n"
          + "Your job is to think together, challenge each other constructively,\n"
          + "and help the students build the best possible solution.\n"
          + "You will first respond independently, then react to the other agents' positions,\n"
          + "and finally a synthesis report will be produced.\n"
          + "Stay true to your assigned perspective below, but remain collaborative.\n\n";

    // ConversationContext provides history from previous cycles.
    // Injected via constructor so this class doesn't depend on global state.
    private final ConversationContext context;

    // ============================================================
    // Constructor.
    //
    // PARAMETER:
    //   context — the conversation memory. PromptBuilder calls
    //             context.getHistoryBlock() to include prior cycles.
    // ============================================================
    public PromptBuilder(ConversationContext context) {
        this.context = context;
    }

    // ============================================================
    // buildPhase1Prompt() — Assembles the full "onion" prompt for
    // Phase 1 (independent responses).
    //
    // Each model gets: team context + its agent identity +
    // stakeholder profile + conversation history + the question.
    //
    // PARAMETERS:
    //   agent       — this model's identity (e.g., Claude's AgentProfile)
    //   stakeholder — who's in the hotseat
    //   userPrompt  — the user's original question
    //
    // RETURNS: the complete prompt string ready to send to the API
    // ============================================================
    public String buildPhase1Prompt(AgentProfile agent,
                                    StakeholderProfile stakeholder,
                                    String userPrompt) {
        return TEAM_CONTEXT
             + agent.toBriefing()
             + stakeholder.toBriefing()
             + context.getHistoryBlock()
             + "=== STAKEHOLDER'S QUESTION ===\n"
             + userPrompt;
    }

    // ============================================================
    // buildSystemInstruction() — Builds the shared system context
    // for the new multi-turn path.
    //
    // This is the single source of onion context and should be
    // passed as the system instruction for all phases.
    // ============================================================
    public String buildSystemInstruction(AgentProfile agent,
                                         StakeholderProfile stakeholder) {
        return TEAM_CONTEXT
             + agent.toBriefing()
             + stakeholder.toBriefing()
             + context.getHistoryBlock();
    }

    // ============================================================
    // buildPhase1UserMessage() — Builds only the user question
    // block for Phase 1 in the new multi-turn path.
    //
    // No onion wrapping is included here because shared context
    // comes from buildSystemInstruction(...).
    // ============================================================
    public String buildPhase1UserMessage(String userPrompt) {
        return "=== STAKEHOLDER'S QUESTION ===\n" + userPrompt;
    }

    // ============================================================
    // buildReactionPrompt() — Constructs the Phase 2 prompt where
    // one model reacts to the other two.
    //
    // This method wraps the cross-reaction with full context:
    //   - The model's agent identity (so it stays in character)
    //   - The stakeholder briefing (so it remembers who it's advising)
    //   - The original question
    //   - The other two models' responses from the previous round
    //   - Instructions for how to react
    //
    // PARAMETERS:
    //   agent         — this model's identity
    //   stakeholder   — who's in the hotseat
    //   userPrompt    — the original question
    //   peerAName     — first peer's display name
    //   peerAResponse — first peer's response (from Phase 1 or previous round)
    //   peerBName     — second peer's display name
    //   peerBResponse — second peer's response
    //
    // RETURNS: the complete reaction prompt
    // ============================================================
    public String buildReactionPrompt(AgentProfile agent,
                                      StakeholderProfile stakeholder,
                                      String userPrompt,
                                      String peerAName, String peerAResponse,
                                      String peerBName, String peerBResponse) {

        StringBuilder sb = new StringBuilder();

        // Layer 1: Agent identity (stay in character during reaction)
        sb.append(TEAM_CONTEXT);
        sb.append(agent.toBriefing());

        // Layer 2: Stakeholder context (remember who you're advising)
        sb.append(stakeholder.toBriefing());

        // Layer 3: The cross-reaction task
        sb.append("THE ORIGINAL QUESTION:\n");
        sb.append(userPrompt).append("\n\n");

        sb.append("You are ").append(agent.getName());
        sb.append(". You already provided your initial response.\n");
        sb.append("Now, the other two panel members have also responded. ");
        sb.append("Review their perspectives below, keeping YOUR assigned role in mind.\n\n");

        sb.append("--- ").append(peerAName).append("'s Response ---\n");
        sb.append(peerAResponse).append("\n\n");

        sb.append("--- ").append(peerBName).append("'s Response ---\n");
        sb.append(peerBResponse).append("\n\n");

        sb.append("React FROM YOUR ASSIGNED PERSPECTIVE. Specifically:\n");
        sb.append("1. Where do you AGREE with the other panel members? Why?\n");
        sb.append("2. Where do you DISAGREE? What did they get wrong from YOUR perspective?\n");
        sb.append("3. What important points did they MISS given the stakeholder's role and KPIs?\n");
        sb.append("4. Has seeing their responses changed or refined YOUR position?\n");
        sb.append("\nBe specific. Reference their actual arguments, not vague generalities.");

        return sb.toString();
    }

    // ============================================================
    // buildPhase2PeerMessage() — Constructs only the reaction body
    // for Phase 2 in the new multi-turn path.
    //
    // TEAM/agent/stakeholder context is intentionally omitted
    // because it now comes from buildSystemInstruction(...).
    // ============================================================
    public String buildPhase2PeerMessage(AgentProfile agent,
                                         String userPrompt,
                                         String peerAName, String peerAResponse,
                                         String peerBName, String peerBResponse) {
        StringBuilder sb = new StringBuilder();

        sb.append("THE ORIGINAL QUESTION:\n");
        sb.append(userPrompt).append("\n\n");

        sb.append("You are ").append(agent.getName());
        sb.append(". You already provided your initial response.\n");
        sb.append("Now, the other two panel members have also responded. ");
        sb.append("Review their perspectives below, keeping YOUR assigned role in mind.\n\n");

        sb.append("--- ").append(peerAName).append("'s Response ---\n");
        sb.append(peerAResponse).append("\n\n");

        sb.append("--- ").append(peerBName).append("'s Response ---\n");
        sb.append(peerBResponse).append("\n\n");

        sb.append("React FROM YOUR ASSIGNED PERSPECTIVE. Specifically:\n");
        sb.append("1. Where do you AGREE with the other panel members? Why?\n");
        sb.append("2. Where do you DISAGREE? What did they get wrong from YOUR perspective?\n");
        sb.append("3. What important points did they MISS given the stakeholder's role and KPIs?\n");
        sb.append("4. Has seeing their responses changed or refined YOUR position?\n");
        sb.append("\nBe specific. Reference their actual arguments, not vague generalities.");

        return sb.toString();
    }

    // ============================================================
    // buildSynthesisPrompt() — Constructs the Phase 3 prompt where
    // Claude synthesizes all perspectives into a structured report.
    //
    // This is the most important prompt in the system. It tells
    // Claude (as orchestrator) to analyze ALL six outputs and
    // produce a report with agreement, disagreement, insights,
    // and a stakeholder-specific recommendation.
    //
    // The quality of this prompt directly determines the quality
    // of the final output. Prompt engineering matters here.
    //
    // PARAMETERS:
    //   stakeholder     — who's in the hotseat (for tailoring)
    //   userPrompt      — the original question
    //   claudeInitial   — Claude's Phase 1 response
    //   gptInitial      — GPT's Phase 1 response
    //   geminiInitial   — Gemini's Phase 1 response
    //   claudeReaction  — Claude's Phase 2 reaction
    //   gptReaction     — GPT's Phase 2 reaction
    //   geminiReaction  — Gemini's Phase 2 reaction
    //
    // RETURNS: the complete synthesis prompt
    // ============================================================
    public String buildSynthesisPrompt(StakeholderProfile stakeholder,
                                       String userPrompt,
                                       String claudeInitial, String gptInitial, String geminiInitial,
                                       String claudeReaction, String gptReaction, String geminiReaction) {

        StringBuilder sb = new StringBuilder();

        sb.append("You are the orchestrator of a multi-AI advisory panel.\n\n");

        // Include stakeholder context so the synthesis is tailored
        sb.append(stakeholder.toBriefing());

        sb.append("=== PANEL COMPOSITION ===\n");
        sb.append("Claude: Chief Strategy & Risk Analyst\n");
        sb.append("GPT: Innovation & Opportunity Analyst\n");
        sb.append("Gemini: Technical Feasibility & Implementation Lead\n\n");

        sb.append("The panel debated the following question from the stakeholder above.\n\n");

        sb.append("=== ORIGINAL QUESTION ===\n");
        sb.append(userPrompt).append("\n\n");

        sb.append("=== PHASE 1: INITIAL RESPONSES ===\n\n");
        sb.append("--- Claude (Strategy & Risk) ---\n").append(claudeInitial).append("\n\n");
        sb.append("--- GPT (Innovation & Opportunity) ---\n").append(gptInitial).append("\n\n");
        sb.append("--- Gemini (Technical Feasibility) ---\n").append(geminiInitial).append("\n\n");

        sb.append("=== PHASE 2: CROSS-REACTIONS ===\n\n");
        sb.append("--- Claude's Reaction ---\n").append(claudeReaction).append("\n\n");
        sb.append("--- GPT's Reaction ---\n").append(gptReaction).append("\n\n");
        sb.append("--- Gemini's Reaction ---\n").append(geminiReaction).append("\n\n");

        sb.append("=== YOUR TASK ===\n");
        sb.append("Produce a SYNTHESIS REPORT with the following sections.\n");
        sb.append("Tailor the report to the active stakeholder's role, KPIs, ");
        sb.append("and decision authority.\n\n");

        sb.append("1. AREAS OF AGREEMENT\n");
        sb.append("   What conclusions do all three panel members converge on? ");
        sb.append("These are the highest-confidence findings.\n\n");

        sb.append("2. AREAS OF DISAGREEMENT\n");
        sb.append("   Where do panel members conflict? Identify which ROLE ");
        sb.append("(strategy vs innovation vs technical) drives each position.\n\n");

        sb.append("3. KEY INSIGHTS\n");
        sb.append("   What emerged from the cross-reaction that wasn't in ");
        sb.append("the initial responses? What changed when perspectives collided?\n\n");

        sb.append("4. RECOMMENDATION FOR THIS STAKEHOLDER\n");
        sb.append("   Given the stakeholder's specific KPIs, authority, and role, ");
        sb.append("what should THEY specifically do? What's actionable for THEM?\n\n");

        sb.append("Be thorough but concise. Reference specific arguments from each panel member.");

        return sb.toString();
    }

    // ============================================================
    // buildPhase3SynthesisMessage() — Constructs the synthesis body
    // for the new multi-turn path.
    //
    // Stakeholder profile prefix is intentionally omitted because
    // stakeholder context now comes from buildSystemInstruction(...).
    // ============================================================
    public String buildPhase3SynthesisMessage(StakeholderProfile stakeholder,
                                              String userPrompt,
                                              String claudeInitial, String gptInitial, String geminiInitial,
                                              String claudeReaction, String gptReaction, String geminiReaction) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are the orchestrator of a multi-AI advisory panel.\n\n");

        sb.append("=== PANEL COMPOSITION ===\n");
        sb.append("Claude: Chief Strategy & Risk Analyst\n");
        sb.append("GPT: Innovation & Opportunity Analyst\n");
        sb.append("Gemini: Technical Feasibility & Implementation Lead\n\n");

        sb.append("The panel debated the following question from the stakeholder above.\n\n");

        sb.append("=== ORIGINAL QUESTION ===\n");
        sb.append(userPrompt).append("\n\n");

        sb.append("=== PHASE 1: INITIAL RESPONSES ===\n\n");
        sb.append("--- Claude (Strategy & Risk) ---\n").append(claudeInitial).append("\n\n");
        sb.append("--- GPT (Innovation & Opportunity) ---\n").append(gptInitial).append("\n\n");
        sb.append("--- Gemini (Technical Feasibility) ---\n").append(geminiInitial).append("\n\n");

        sb.append("=== PHASE 2: CROSS-REACTIONS ===\n\n");
        sb.append("--- Claude's Reaction ---\n").append(claudeReaction).append("\n\n");
        sb.append("--- GPT's Reaction ---\n").append(gptReaction).append("\n\n");
        sb.append("--- Gemini's Reaction ---\n").append(geminiReaction).append("\n\n");

        sb.append("=== YOUR TASK ===\n");
        sb.append("Produce a SYNTHESIS REPORT with the following sections.\n");
        sb.append("Tailor the report to the active stakeholder's role, KPIs, ");
        sb.append("and decision authority.\n\n");

        sb.append("1. AREAS OF AGREEMENT\n");
        sb.append("   What conclusions do all three panel members converge on? ");
        sb.append("These are the highest-confidence findings.\n\n");

        sb.append("2. AREAS OF DISAGREEMENT\n");
        sb.append("   Where do panel members conflict? Identify which ROLE ");
        sb.append("(strategy vs innovation vs technical) drives each position.\n\n");

        sb.append("3. KEY INSIGHTS\n");
        sb.append("   What emerged from the cross-reaction that wasn't in ");
        sb.append("the initial responses? What changed when perspectives collided?\n\n");

        sb.append("4. RECOMMENDATION FOR THIS STAKEHOLDER\n");
        sb.append("   Given the stakeholder's specific KPIs, authority, and role, ");
        sb.append("what should THEY specifically do? What's actionable for THEM?\n\n");

        sb.append("Be thorough but concise. Reference specific arguments from each panel member.");

        return sb.toString();
    }
}
