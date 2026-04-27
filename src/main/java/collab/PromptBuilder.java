package collab;

// ============================================================
// PromptBuilder.java — Builds all prompt types for the debate cycle.
//
// WHAT THIS CLASS DOES (one sentence):
// Assembles the multi-layered context prompts for Phase 1, Phase 2,
// and Phase 3 by combining team context, agent identity, stakeholder
// profile, conversation history, and the user's question.
//
// HOW IT FITS THE ARCHITECTURE:
// Maestro calls PromptBuilder's methods to construct the prompts
// before sending them to each LlmClient. PromptBuilder doesn't make
// any API calls itself — it only builds the text.
//
// CONTEXT LAYERING ARCHITECTURE (CLA):
// Every API call carries multiple layers of context, from foundation to surface:
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

    // The team context layer — shared by all agents.
    // Set by the user during first-launch setup. Empty until configured.
    static final String DEFAULT_TEAM_CONTEXT = "";

    // ConversationContext provides history from previous cycles.
    // Injected via constructor so this class doesn't depend on global state.
    private final ConversationContext context;
    private final String teamContext;
    private final ContextController controller;

    // User-editable Phase 2 / Phase 3 instruction blocks. Null means
    // "use baked-in defaults"; see effectiveTemplate() below.
    private PromptTemplate template;

    // ============================================================
    // Constructor.
    //
    // PARAMETER:
    //   context — the conversation memory. PromptBuilder calls
    //             context.getHistoryBlock() to include prior cycles.
    // ============================================================
    public PromptBuilder(ConversationContext context) {
        this(context, DEFAULT_TEAM_CONTEXT, null);
    }

    public PromptBuilder(ConversationContext context, String teamContext) {
        this(context, teamContext, null);
    }

    public PromptBuilder(ConversationContext context, String teamContext, ContextController controller) {
        this.context = context;
        this.teamContext = teamContext;
        this.controller = controller;
    }

    /**
     * Provide an editable template. Pass null to fall back to defaults.
     * Maestro (via MainGui.rebuildMaestro) calls this after construction
     * so legacy callers that don't know about templates still get the
     * original hardcoded behaviour.
     */
    public void setTemplate(PromptTemplate template) {
        this.template = template;
    }

    private PromptTemplate effectiveTemplate() {
        return template != null ? template : PromptTemplate.fromDefaults();
    }

    // Layer helpers that respect ContextController toggles
    private String effectiveTeamContext() {
        if (controller != null && !controller.shouldIncludeTeamContext()) return "";
        return teamContext;
    }
    private String effectiveAgentBriefing(AgentProfile agent) {
        if (controller != null && !controller.shouldIncludeAgentIdentity()) return "";
        return agent.toBriefing();
    }
    private String effectiveStakeholderBriefing(StakeholderProfile s) {
        if (controller != null && !controller.shouldIncludeStakeholderProfile()) return "";
        return s.toBriefing();
    }
    /**
     * Returns history block respecting the controller toggle.
     * The toggle only applies to Claude since GPT/Gemini maintain
     * history via their stateful APIs.
     */
    private String effectiveHistory() {
        if (controller != null && !controller.shouldIncludeHistory()) return "";
        return context.getHistoryBlock();
    }

    /** Returns history block unconditionally (for stateful API providers). */
    private String alwaysHistory() {
        return context.getHistoryBlock();
    }

    // ============================================================
    // buildPhase1Prompt() — Assembles the full layered context prompt
    // for Phase 1 (independent responses).
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
        return effectiveTeamContext()
             + effectiveAgentBriefing(agent)
             + effectiveStakeholderBriefing(stakeholder)
             + effectiveHistory()
             + "=== STAKEHOLDER'S QUESTION ===\n"
             + userPrompt;
    }

    // ============================================================
    // buildSystemInstruction() — Builds the shared system context
    // for the new multi-turn path.
    //
    // This is the single source of layered context and should be
    // passed as the system instruction for all phases.
    // ============================================================
    /**
     * Builds system instruction for Claude (history respects toggle).
     */
    public String buildSystemInstruction(AgentProfile agent,
                                         StakeholderProfile stakeholder) {
        return effectiveTeamContext()
             + effectiveAgentBriefing(agent)
             + effectiveStakeholderBriefing(stakeholder)
             + effectiveHistory();
    }

    /**
     * Builds system instruction for stateful providers (GPT/Gemini).
     * Always includes history since the toggle only applies to Claude.
     */
    public String buildStatefulSystemInstruction(AgentProfile agent,
                                                  StakeholderProfile stakeholder) {
        return effectiveTeamContext()
             + effectiveAgentBriefing(agent)
             + effectiveStakeholderBriefing(stakeholder)
             + alwaysHistory();
    }

    // ============================================================
    // buildPhase1UserMessage() — Builds only the user question
    // block for Phase 1 in the new multi-turn path.
    //
    // No context layering is included here because shared context
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
        sb.append(effectiveTeamContext());
        sb.append(effectiveAgentBriefing(agent));

        // Layer 2: Stakeholder context (remember who you're advising)
        sb.append(effectiveStakeholderBriefing(stakeholder));

        // Layer 3: The cross-reaction task (instructions editable via PromptTemplate)
        PromptTemplate tmpl = effectiveTemplate();

        sb.append("THE ORIGINAL QUESTION:\n");
        sb.append(userPrompt).append("\n\n");

        sb.append("You are ").append(agent.getName()).append(". ");
        sb.append(tmpl.getReactionPreamble());

        sb.append("--- ").append(peerAName).append("'s Response ---\n");
        sb.append(peerAResponse).append("\n\n");

        sb.append("--- ").append(peerBName).append("'s Response ---\n");
        sb.append(peerBResponse).append("\n\n");

        sb.append(tmpl.getReactionTask());

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
        PromptTemplate tmpl = effectiveTemplate();
        StringBuilder sb = new StringBuilder();

        sb.append("THE ORIGINAL QUESTION:\n");
        sb.append(userPrompt).append("\n\n");

        sb.append("You are ").append(agent.getName()).append(". ");
        sb.append(tmpl.getReactionPreamble());

        sb.append("--- ").append(peerAName).append("'s Response ---\n");
        sb.append(peerAResponse).append("\n\n");

        sb.append("--- ").append(peerBName).append("'s Response ---\n");
        sb.append(peerBResponse).append("\n\n");

        sb.append(tmpl.getReactionTask());

        return sb.toString();
    }

    // ============================================================
    // buildSynthesisPrompt() — Constructs the Phase 3 prompt where
    // Claude synthesizes all perspectives into a structured report.
    //
    // This is the most important prompt in the system. It tells
    // Claude (as maestro) to analyze ALL six outputs and
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

        PromptTemplate tmpl = effectiveTemplate();
        StringBuilder sb = new StringBuilder();

        sb.append("You are the orchestrator of a multi-AI advisory panel.\n\n");

        // Include stakeholder context so the synthesis is tailored
        sb.append(stakeholder.toBriefing());

        // Editable preamble: panel composition + "debated the following" line
        sb.append(tmpl.getSynthesisPreamble());

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

        // Editable task: "=== YOUR TASK ===" + four numbered sections
        sb.append(tmpl.getSynthesisTask());

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
        PromptTemplate tmpl = effectiveTemplate();
        StringBuilder sb = new StringBuilder();

        sb.append("You are the orchestrator of a multi-AI advisory panel.\n\n");

        // Editable preamble: panel composition + "debated the following" line
        sb.append(tmpl.getSynthesisPreamble());

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

        // Editable task: "=== YOUR TASK ===" + four numbered sections
        sb.append(tmpl.getSynthesisTask());

        return sb.toString();
    }
}
