package collab;

// ============================================================
// PromptTemplate.java — Editable "instructions-only" text blocks
// injected into the Phase 2 reaction and Phase 3 synthesis prompts.
//
// WHAT THIS CLASS DOES (one sentence):
// Holds four user-editable instruction strings that PromptBuilder
// splices into the fixed data-bearing prompt structure.
//
// EDITABLE FIELDS:
//   reactionPreamble  — appears BEFORE peer responses in Phase 2.
//                       Injected in buildReactionPrompt() and
//                       buildPhase2PeerMessage() right after the
//                       hardcoded "You are <agent>. " prefix.
//   reactionTask      — appears AFTER peer responses in Phase 2.
//                       Contains the numbered react-from-your-perspective
//                       task list.
//   synthesisPreamble — appears AFTER "You are the orchestrator..."
//                       and (in buildSynthesisPrompt only) after the
//                       stakeholder briefing. Contains the PANEL
//                       COMPOSITION block and the setup sentence.
//   synthesisTask     — appears AFTER all phase data in Phase 3.
//                       Contains the "=== YOUR TASK ===" block and
//                       the four numbered synthesis sections.
//
// Persistence: see PromptTemplateLibrary, which serializes instances
// to templates/<name>/prompt-template.json via Gson (parallel to the
// existing ProfileLibrary pattern for ProfileSet).
// ============================================================

public class PromptTemplate {

    // Default values are the verbatim strings that were hardcoded in
    // PromptBuilder before this class existed. Keep them here so
    // fromDefaults() produces byte-for-byte identical prompts.
    public static final String DEFAULT_REACTION_PREAMBLE =
            "You already provided your initial response.\n"
          + "Now, the other two panel members have also responded. "
          + "Review their perspectives below, keeping YOUR assigned role in mind.\n\n";

    public static final String DEFAULT_REACTION_TASK =
            "React FROM YOUR ASSIGNED PERSPECTIVE. Specifically:\n"
          + "1. Where do you AGREE with the other panel members? Why?\n"
          + "2. Where do you DISAGREE? What did they get wrong from YOUR perspective?\n"
          + "3. What important points did they MISS given the stakeholder's role and KPIs?\n"
          + "4. Has seeing their responses changed or refined YOUR position?\n"
          + "\nBe specific. Reference their actual arguments, not vague generalities.";

    public static final String DEFAULT_SYNTHESIS_PREAMBLE =
            "=== PANEL COMPOSITION ===\n"
          + "Claude: Chief Strategy & Risk Analyst\n"
          + "GPT: Innovation & Opportunity Analyst\n"
          + "Gemini: Technical Feasibility & Implementation Lead\n\n"
          + "The panel debated the following question from the stakeholder above.\n\n";

    public static final String DEFAULT_SYNTHESIS_TASK =
            "=== YOUR TASK ===\n"
          + "Produce a SYNTHESIS REPORT with the following sections.\n"
          + "Tailor the report to the active stakeholder's role, KPIs, "
          + "and decision authority.\n\n"
          + "1. AREAS OF AGREEMENT\n"
          + "   What conclusions do all three panel members converge on? "
          + "These are the highest-confidence findings.\n\n"
          + "2. AREAS OF DISAGREEMENT\n"
          + "   Where do panel members conflict? Identify which ROLE "
          + "(strategy vs innovation vs technical) drives each position.\n\n"
          + "3. KEY INSIGHTS\n"
          + "   What emerged from the cross-reaction that wasn't in "
          + "the initial responses? What changed when perspectives collided?\n\n"
          + "4. RECOMMENDATION FOR THIS STAKEHOLDER\n"
          + "   Given the stakeholder's specific KPIs, authority, and role, "
          + "what should THEY specifically do? What's actionable for THEM?\n\n"
          + "Be thorough but concise. Reference specific arguments from each panel member.";

    private String reactionPreamble;
    private String reactionTask;
    private String synthesisPreamble;
    private String synthesisTask;

    // No-arg constructor for Gson deserialization.
    public PromptTemplate() {
    }

    public PromptTemplate(String reactionPreamble, String reactionTask,
                          String synthesisPreamble, String synthesisTask) {
        this.reactionPreamble = reactionPreamble;
        this.reactionTask = reactionTask;
        this.synthesisPreamble = synthesisPreamble;
        this.synthesisTask = synthesisTask;
    }

    /**
     * Builds a template with the out-of-box defaults that match the
     * original hardcoded PromptBuilder output byte-for-byte.
     */
    public static PromptTemplate fromDefaults() {
        return new PromptTemplate(
                DEFAULT_REACTION_PREAMBLE,
                DEFAULT_REACTION_TASK,
                DEFAULT_SYNTHESIS_PREAMBLE,
                DEFAULT_SYNTHESIS_TASK);
    }

    public String getReactionPreamble() {
        return reactionPreamble != null ? reactionPreamble : DEFAULT_REACTION_PREAMBLE;
    }

    public void setReactionPreamble(String reactionPreamble) {
        this.reactionPreamble = reactionPreamble;
    }

    public String getReactionTask() {
        return reactionTask != null ? reactionTask : DEFAULT_REACTION_TASK;
    }

    public void setReactionTask(String reactionTask) {
        this.reactionTask = reactionTask;
    }

    public String getSynthesisPreamble() {
        return synthesisPreamble != null ? synthesisPreamble : DEFAULT_SYNTHESIS_PREAMBLE;
    }

    public void setSynthesisPreamble(String synthesisPreamble) {
        this.synthesisPreamble = synthesisPreamble;
    }

    public String getSynthesisTask() {
        return synthesisTask != null ? synthesisTask : DEFAULT_SYNTHESIS_TASK;
    }

    public void setSynthesisTask(String synthesisTask) {
        this.synthesisTask = synthesisTask;
    }
}
