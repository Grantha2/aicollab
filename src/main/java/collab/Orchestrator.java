package collab;

// ============================================================
// Orchestrator.java — Runs the 3-phase debate cycle.
//
// WHAT THIS CLASS DOES (one sentence):
// Coordinates Claude, GPT, and Gemini through independent responses,
// cross-reactions (one or more rounds), and a final synthesis report.
//
// HOW IT FITS THE ARCHITECTURE:
// Main.java handles user input and the CLI loop. When the user
// confirms a debate, Main calls orchestrator.runDebate(). The
// Orchestrator takes over from there: it builds prompts using
// PromptBuilder, sends them via LlmClient implementations, prints
// results, and stores the synthesis in ConversationContext.
//
// THE KEY DESIGN INSIGHT:
// Orchestrator takes LlmClient interfaces — not AnthropicClient,
// OpenAiClient, or GeminiClient specifically. It calls
// sendMessage() and doesn't know or care which provider is behind
// it. This means:
//   - You could swap in a MockClient for testing (no API calls,
//     no money spent, instant results)
//   - You could add a new model (Llama, Mistral, your own) by
//     creating one new file that implements LlmClient. Zero
//     changes to Orchestrator.
//   - You could run two Claudes and one GPT, or three Geminis,
//     without touching this file.
// This is the real power of the interface pattern.
//
// THE 3-PHASE DEBATE:
//   Phase 1 — INDEPENDENT: Each model answers the question from
//             its assigned perspective, without seeing others.
//   Phase 2 — REACTION: Each model sees what the other two said
//             and reacts. This can run multiple rounds — each round
//             reacts to the PREVIOUS round's outputs, not always
//             Phase 1. More rounds = deeper debate.
//   Phase 3 — SYNTHESIS: Claude merges all outputs into a
//             structured report with agreement, disagreement,
//             insights, and a stakeholder-specific recommendation.
// ============================================================

public class Orchestrator {

    // The three AI clients. These are LlmClient interfaces —
    // Orchestrator doesn't know which provider is behind each one.
    private final LlmClient claudeClient;
    private final LlmClient gptClient;
    private final LlmClient geminiClient;

    // The three agent identities — what role each model plays.
    private final AgentProfile claudeAgent;
    private final AgentProfile gptAgent;
    private final AgentProfile geminiAgent;

    // Builds all the prompts (team context + agent + stakeholder + question).
    private final PromptBuilder promptBuilder;

    // Stores synthesis reports so the panel has memory across cycles.
    private final ConversationContext context;
    private final SessionStore sessionStore;

    // How many rounds of cross-reaction in Phase 2.
    // 1 round = standard (each model reacts once to the other two).
    // 2+ rounds = deeper debate (reactions build on previous reactions).
    private final int debateRounds;

    // ============================================================
    // Constructor — wires together all the pieces.
    //
    // Main.java creates all these objects and passes them in.
    // Orchestrator doesn't create anything itself — it just
    // coordinates. This makes it easy to test and reconfigure.
    // ============================================================
    public Orchestrator(LlmClient claudeClient, LlmClient gptClient, LlmClient geminiClient,
                        AgentProfile claudeAgent, AgentProfile gptAgent, AgentProfile geminiAgent,
                        PromptBuilder promptBuilder, ConversationContext context,
                        int debateRounds, SessionStore sessionStore) {
        this.claudeClient = claudeClient;
        this.gptClient = gptClient;
        this.geminiClient = geminiClient;
        this.claudeAgent = claudeAgent;
        this.gptAgent = gptAgent;
        this.geminiAgent = geminiAgent;
        this.promptBuilder = promptBuilder;
        this.context = context;
        this.debateRounds = debateRounds;
        this.sessionStore = sessionStore;
    }

    // ============================================================
    // runDebate() — Executes the full 3-phase debate cycle.
    //
    // This is the brain of the platform. Each phase is just an
    // API call with a different prompt. The "debate" isn't special
    // technology — it's just passing text between models strategically.
    //
    // PARAMETERS:
    //   userPrompt        — the user's question
    //   activeStakeholder — who's in the hotseat (shapes all prompts)
    // ============================================================
    public void runDebate(String userPrompt, StakeholderProfile activeStakeholder) {
        int cycle = context.getCycleCount() + 1;

        // ==========================
        // PHASE 1: INDEPENDENT RESPONSES
        // ==========================
        // Each model sees the user's question PLUS its agent identity
        // PLUS the active stakeholder's profile PLUS conversation history.
        // They respond from their assigned perspective, tailored to who's asking.

        System.out.println();
        System.out.println("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
        System.out.println("\u2551   PHASE 1: Independent Responses     \u2551");
        System.out.println("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");

        System.out.println("\n[Calling Claude (Strategy & Risk)...]");
        String claudeResponse = claudeClient.sendMessage(
                promptBuilder.buildPhase1Prompt(claudeAgent, activeStakeholder, userPrompt));
        persistTurn(cycle, "phase1", "Claude", claudeAgent.getPerspective(), claudeResponse);
        System.out.println("Claude responded. \u2713");

        System.out.println("\n[Calling GPT (Innovation & Opportunity)...]");
        String gptResponse = gptClient.sendMessage(
                promptBuilder.buildPhase1Prompt(gptAgent, activeStakeholder, userPrompt));
        persistTurn(cycle, "phase1", "GPT", gptAgent.getPerspective(), gptResponse);
        System.out.println("GPT responded. \u2713");

        System.out.println("\n[Calling Gemini (Technical Feasibility)...]");
        String geminiResponse = geminiClient.sendMessage(
                promptBuilder.buildPhase1Prompt(geminiAgent, activeStakeholder, userPrompt));
        persistTurn(cycle, "phase1", "Gemini", geminiAgent.getPerspective(), geminiResponse);
        System.out.println("Gemini responded. \u2713");

        // Print Phase 1 results so the user can see what each model said
        System.out.println("\n--- Claude's Response ---");
        System.out.println(claudeResponse);
        System.out.println("\n--- GPT's Response ---");
        System.out.println(gptResponse);
        System.out.println("\n--- Gemini's Response ---");
        System.out.println(geminiResponse);


        // ==========================
        // PHASE 2: CROSS-REACTION
        // ==========================
        // Now each model sees what the OTHER TWO said and reacts.
        //
        // THIS IS THE KEY INSIGHT: We're not using any special
        // "debate API." We're just sending a bigger prompt that
        // says "here's what others think — now react."
        //
        // MULTI-ROUND SUPPORT:
        // If debateRounds > 1, the reactions feed into the next round.
        // Round 2 reacts to Round 1's reactions (not Phase 1 responses).
        // This creates deeper, more refined debate.

        // Track the latest responses — starts with Phase 1 outputs,
        // then gets replaced by each round's reactions.
        String latestClaude = claudeResponse;
        String latestGpt = gptResponse;
        String latestGemini = geminiResponse;

        for (int round = 1; round <= debateRounds; round++) {

            System.out.println();
            if (debateRounds == 1) {
                System.out.println("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
                System.out.println("\u2551   PHASE 2: Cross-Reaction            \u2551");
                System.out.println("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");
            } else {
                // Multi-round: show which round we're on
                System.out.println("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
                System.out.println("\u2551   PHASE 2: Reaction Round " + round + " of " + debateRounds + "      \u2551");
                System.out.println("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");
            }

            // Each model reacts to the other two's LATEST responses.
            // In round 1, "latest" = Phase 1 outputs.
            // In round 2+, "latest" = previous round's reactions.

            System.out.println("\n[Claude reacting to GPT and Gemini...]");
            String claudeReaction = claudeClient.sendMessage(
                    promptBuilder.buildReactionPrompt(claudeAgent, activeStakeholder,
                            userPrompt, "GPT", latestGpt, "Gemini", latestGemini));
            persistTurn(cycle, "phase2-round-" + round, "Claude", claudeAgent.getPerspective(), claudeReaction);
            System.out.println("Claude reacted. \u2713");

            System.out.println("\n[GPT reacting to Claude and Gemini...]");
            String gptReaction = gptClient.sendMessage(
                    promptBuilder.buildReactionPrompt(gptAgent, activeStakeholder,
                            userPrompt, "Claude", latestClaude, "Gemini", latestGemini));
            persistTurn(cycle, "phase2-round-" + round, "GPT", gptAgent.getPerspective(), gptReaction);
            System.out.println("GPT reacted. \u2713");

            System.out.println("\n[Gemini reacting to Claude and GPT...]");
            String geminiReaction = geminiClient.sendMessage(
                    promptBuilder.buildReactionPrompt(geminiAgent, activeStakeholder,
                            userPrompt, "Claude", latestClaude, "GPT", latestGpt));
            persistTurn(cycle, "phase2-round-" + round, "Gemini", geminiAgent.getPerspective(), geminiReaction);
            System.out.println("Gemini reacted. \u2713");

            // Print this round's reactions
            System.out.println("\n--- Claude's Reaction ---");
            System.out.println(claudeReaction);
            System.out.println("\n--- GPT's Reaction ---");
            System.out.println(gptReaction);
            System.out.println("\n--- Gemini's Reaction ---");
            System.out.println(geminiReaction);

            // Update "latest" for the next round (or for synthesis)
            latestClaude = claudeReaction;
            latestGpt = gptReaction;
            latestGemini = geminiReaction;
        }


        // ==========================
        // PHASE 3: SYNTHESIS
        // ==========================
        // Claude acts as the orchestrator. It receives ALL SIX outputs
        // (3 initial responses + 3 final reactions) and produces a structured
        // report identifying agreement, disagreement, and a recommendation.
        //
        // NOTE: For multi-round debates, we send the LAST round's reactions
        // (not all intermediate rounds). The synthesis captures the most
        // refined positions.

        System.out.println();
        System.out.println("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
        System.out.println("\u2551   PHASE 3: Synthesis Report           \u2551");
        System.out.println("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");

        String synthesisPrompt = promptBuilder.buildSynthesisPrompt(
                activeStakeholder, userPrompt,
                claudeResponse, gptResponse, geminiResponse,
                latestClaude, latestGpt, latestGemini
        );

        System.out.println("\n[Claude synthesizing all perspectives...]");
        String synthesis = claudeClient.sendMessage(synthesisPrompt);

        System.out.println("\n" + synthesis);

        // Store the synthesis in conversation memory so the next cycle
        // can reference what was discussed in this one.
        context.addSynthesis(synthesis);
        sessionStore.appendSynthesis(cycle, synthesis);
    }

    private void persistTurn(int cycle, String phase, String model, String role, String content) {
        ConversationTurn turn = new ConversationTurn(
                cycle, phase, model, role, content, System.currentTimeMillis());
        context.addTurn(turn);
        sessionStore.appendTurn(turn);
    }

    // ============================================================
    // getApiCallCount() — Returns how many API calls one debate makes.
    //
    // Phase 1: 3 calls (one per model)
    // Phase 2: 3 calls per round x debateRounds
    // Phase 3: 1 call (Claude synthesizes)
    // Total:   4 + (3 x debateRounds)
    //
    // Used by Main.java to show the user how many calls they're
    // about to trigger before they confirm.
    // ============================================================
    public int getApiCallCount() {
        return 4 + (3 * debateRounds);
    }
}
