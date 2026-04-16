package collab;

// ============================================================
// Maestro.java — Runs the 3-phase debate cycle.
//
// WHAT THIS CLASS DOES (one sentence):
// Coordinates an N-panelist panel through independent responses,
// cross-reactions (one or more rounds), and a final synthesis.
//
// HOW IT FITS THE ARCHITECTURE:
// Main.java / MainGui.java builds a list of PanelistSlots (provider +
// model + agent profile) and a parallel list of LlmClients, then passes
// both to Maestro. Maestro loops over the lists at each phase, keeps
// per-slot state (stateId, latest response), and never hardcodes a
// panelist count.
//
// THE KEY DESIGN INSIGHT:
// Maestro takes LlmClient interfaces — not AnthropicClient,
// OpenAiClient, or GeminiClient specifically. It calls sendStateful()
// and doesn't know or care which provider is behind each slot. Two
// Claudes + one GPT, three Geminis, eight mixed slots — all valid
// configurations without touching this file.
//
// THE 3-PHASE DEBATE:
//   Phase 1 — INDEPENDENT: Each panelist answers the question from
//             its assigned perspective, without seeing others.
//   Phase 2 — REACTION: Each panelist sees every OTHER panelist's
//             latest response and reacts. Multi-round: round 2+ reacts
//             to the previous round's reactions, not Phase 1.
//   Phase 3 — SYNTHESIS: The first slot's client aggregates all Phase 1
//             responses + all final reactions into a structured report.
// ============================================================

import java.util.ArrayList;
import java.util.List;

public class Maestro {

    // Parallel lists: slots.get(i) is served by clients.get(i).
    // Both lists are immutable after construction.
    private final List<PanelistSlot> slots;
    private final List<LlmClient> clients;

    // Builds prompts (team context + agent + stakeholder + question).
    private final PromptBuilder promptBuilder;

    // Stores synthesis reports so the panel has memory across cycles.
    private final ConversationContext context;
    private final int configuredMaxTokens;
    private final SessionStore sessionStore;
    // Optional API-request audit log. When non-null, every LlmRequest is
    // persisted as a JSONL line before the API call is made, so users can
    // audit exactly what peer text was embedded in each reaction.
    private final ApiRequestLog apiRequestLog;
    private DebateListener listener;

    // How many rounds of cross-reaction in Phase 2.
    private final int debateRounds;

    // Debate-level file attachments. Only injected on Phase 1 turns
    // (the first call per panelist) — chained Phase 2/3 turns rely on
    // provider-retained or client-replayed state to carry the files
    // forward. See each LlmClient for provider-specific realisation.
    private List<ContextAttachment> attachments = List.of();

    // ============================================================
    // Legacy 3-panel constructors (backwards compat for callers that
    // haven't been migrated to the slot-list API yet). They synthesize
    // three PanelistSlots using the historic provider assignment
    // (Anthropic/OpenAI/Google) and delegate to the primary
    // list-based constructor.
    // ============================================================
    public Maestro(LlmClient claudeClient, LlmClient gptClient, LlmClient geminiClient,
                        AgentProfile claudeAgent, AgentProfile gptAgent, AgentProfile geminiAgent,
                        PromptBuilder promptBuilder, ConversationContext context,
                        int debateRounds, int configuredMaxTokens, SessionStore sessionStore) {
        this(claudeClient, gptClient, geminiClient,
                claudeAgent, gptAgent, geminiAgent,
                promptBuilder, context, debateRounds, configuredMaxTokens, sessionStore, null);
    }

    public Maestro(LlmClient claudeClient, LlmClient gptClient, LlmClient geminiClient,
                        AgentProfile claudeAgent, AgentProfile gptAgent, AgentProfile geminiAgent,
                        PromptBuilder promptBuilder, ConversationContext context,
                        int debateRounds, int configuredMaxTokens, SessionStore sessionStore,
                        ApiRequestLog apiRequestLog) {
        this(
                List.of(
                        new PanelistSlot(Provider.ANTHROPIC, null, claudeAgent),
                        new PanelistSlot(Provider.OPENAI, null, gptAgent),
                        new PanelistSlot(Provider.GOOGLE, null, geminiAgent)),
                List.of(claudeClient, gptClient, geminiClient),
                promptBuilder, context, debateRounds, configuredMaxTokens, sessionStore, apiRequestLog);
    }

    // ============================================================
    // Primary constructor — list-based.
    //
    // The panel is defined by `slots` (metadata) and `clients` (which
    // LlmClient talks to each slot's provider+model). The two lists
    // MUST be the same size; slot i is served by client i.
    // ============================================================
    public Maestro(List<PanelistSlot> slots, List<LlmClient> clients,
                   PromptBuilder promptBuilder, ConversationContext context,
                   int debateRounds, int configuredMaxTokens, SessionStore sessionStore,
                   ApiRequestLog apiRequestLog) {
        if (slots == null || clients == null) {
            throw new IllegalArgumentException("slots and clients must be non-null");
        }
        if (slots.size() != clients.size()) {
            throw new IllegalArgumentException(
                    "slots and clients must be the same size (got "
                            + slots.size() + " and " + clients.size() + ")");
        }
        if (slots.size() < 2) {
            throw new IllegalArgumentException("A debate needs at least 2 panelists");
        }
        this.slots = List.copyOf(slots);
        this.clients = List.copyOf(clients);
        this.promptBuilder = promptBuilder;
        this.context = context;
        this.debateRounds = debateRounds;
        this.configuredMaxTokens = configuredMaxTokens;
        this.sessionStore = sessionStore;
        this.apiRequestLog = apiRequestLog;
    }

    // ============================================================
    // logRequest() — DRYs up the ApiRequestLog.append call sites
    // scattered across Phase 1/2/3. No-op when the log is absent
    // (e.g. older callers that skipped the new constructor overload).
    // ============================================================
    private void logRequest(int cycle, String phase, String model, String provider,
                            LlmRequest request, String stateId) {
        if (apiRequestLog == null) return;
        apiRequestLog.append(ApiRequestLog.RequestRecord.from(
                cycle, phase, model, provider, request, stateId));
    }

    public void setDebateListener(DebateListener listener) {
        this.listener = listener;
    }

    /**
     * Replaces the set of debate-level attachments. The list is copied
     * and normalised to non-null; callers can pass null for "clear all".
     * Applied on the NEXT runDebate() — an in-flight debate keeps the
     * attachments it started with.
     */
    public void setAttachments(List<? extends ContextAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            this.attachments = List.of();
        } else {
            this.attachments = List.copyOf(attachments);
        }
    }

    public List<PanelistSlot> getSlots() {
        return slots;
    }

    private void notifyStatus(String message) {
        if (listener != null) {
            listener.onStatusUpdate(message);
        }
    }

    private void notifyPhase1(int slotIndex, String model, String perspective, String response) {
        if (listener != null) {
            listener.onPhase1Response(slotIndex, model, perspective, response);
        }
    }

    private void notifyPhase2(int slotIndex, int round, String model, String perspective, String reaction) {
        if (listener != null) {
            listener.onPhase2Reaction(slotIndex, round, model, perspective, reaction);
        }
    }

    private void notifySynthesis(String synthesis) {
        if (listener != null) {
            listener.onSynthesis(synthesis);
        }
    }

    private String perspectiveOf(PanelistSlot slot) {
        AgentProfile agent = slot.getAgent();
        return agent == null || agent.getPerspective() == null ? "" : agent.getPerspective();
    }

    // ============================================================
    // runDebate() — Executes the full 3-phase debate cycle.
    //
    // PARAMETERS:
    //   userPrompt        — the user's question
    //   activeStakeholder — who's in the hotseat (shapes all prompts)
    // ============================================================
    public void runDebate(String userPrompt, StakeholderProfile activeStakeholder) {
        int cycle = context.getCycleCount() + 1;
        int n = slots.size();

        // Per-slot state. stateIds track stateful-chaining; phase1 keeps
        // the Phase 1 responses so Phase 3 synthesis can reference them
        // even after several rounds of cross-reactions have rolled `latest`.
        String[] stateIds = new String[n];
        String[] phase1 = new String[n];
        String[] latest = new String[n];

        // ==========================
        // PHASE 1: INDEPENDENT RESPONSES
        // ==========================
        System.out.println();
        System.out.println("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
        System.out.println("\u2551   PHASE 1: Independent Responses     \u2551");
        System.out.println("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");

        for (int i = 0; i < n; i++) {
            PanelistSlot slot = slots.get(i);
            AgentProfile agent = slot.getAgent();
            String displayName = slot.displayName();
            String perspective = perspectiveOf(slot);

            System.out.println("\n[Calling " + displayName + " (" + perspective + ")...]");
            notifyStatus("Calling " + displayName + " (" + perspective + ")...");

            LlmRequest req = new LlmRequest(
                    promptBuilder.buildSystemInstruction(agent, activeStakeholder),
                    List.of(new ChatMessage("user", promptBuilder.buildPhase1UserMessage(userPrompt))),
                    configuredMaxTokens,
                    attachments);
            logRequest(cycle, "phase1", displayName, slot.providerKey(), req, null);
            StatefulResponse r = clients.get(i).sendStateful(req, null);
            String text = r.text();
            stateIds[i] = r.stateId();
            phase1[i] = text;
            latest[i] = text;

            persistTurn(cycle, "phase1", displayName, perspective, text);
            System.out.println(displayName + " responded. \u2713");
            notifyPhase1(i, displayName, perspective, text);
            notifyStatus(displayName + " responded.");
        }

        // Print Phase 1 results
        for (int i = 0; i < n; i++) {
            System.out.println("\n--- " + slots.get(i).displayName() + "'s Response ---");
            System.out.println(phase1[i]);
        }

        // ==========================
        // PHASE 2: CROSS-REACTION
        // ==========================
        for (int round = 1; round <= debateRounds; round++) {

            System.out.println();
            if (debateRounds == 1) {
                System.out.println("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
                System.out.println("\u2551   PHASE 2: Cross-Reaction            \u2551");
                System.out.println("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");
            } else {
                System.out.println("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
                System.out.println("\u2551   PHASE 2: Reaction Round " + round + " of " + debateRounds + "      \u2551");
                System.out.println("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");
            }

            // Compute this round's reactions into a fresh array so every
            // panelist reacts to the same snapshot of peers (the previous
            // round's outputs). Without a snapshot, panelists processed
            // later in the loop would see some of this round's reactions.
            String[] roundOut = new String[n];
            for (int i = 0; i < n; i++) {
                PanelistSlot slot = slots.get(i);
                AgentProfile agent = slot.getAgent();
                String displayName = slot.displayName();
                String perspective = perspectiveOf(slot);

                List<PromptBuilder.Peer> peers = new ArrayList<>();
                for (int j = 0; j < n; j++) {
                    if (j == i) continue;
                    peers.add(new PromptBuilder.Peer(slots.get(j).displayName(), latest[j]));
                }

                System.out.println("\n[" + displayName + " reacting...]");
                notifyStatus("Round " + round + ": " + displayName + " reacting...");

                String body = promptBuilder.buildPhase2PeerMessage(agent, userPrompt, peers);
                LlmRequest req = new LlmRequest(
                        null, // system instruction already in conversation state
                        List.of(new ChatMessage("user", body)),
                        configuredMaxTokens);
                logRequest(cycle, "phase2-round-" + round, displayName, slot.providerKey(), req, stateIds[i]);
                StatefulResponse r = clients.get(i).sendStateful(req, stateIds[i]);
                String reaction = r.text();
                stateIds[i] = r.stateId();
                roundOut[i] = reaction;

                persistTurn(cycle, "phase2-round-" + round, displayName, perspective, reaction);
                System.out.println(displayName + " reacted. \u2713");
                notifyPhase2(i, round, displayName, perspective, reaction);
                notifyStatus("Round " + round + ": " + displayName + " reacted.");
            }

            // Print this round's reactions, then promote to `latest` for
            // either the next round or Phase 3 synthesis.
            for (int i = 0; i < n; i++) {
                System.out.println("\n--- " + slots.get(i).displayName() + "'s Reaction ---");
                System.out.println(roundOut[i]);
            }
            latest = roundOut;
        }

        // ==========================
        // PHASE 3: SYNTHESIS
        // ==========================
        // Slot 0's client continues its stateful conversation and
        // produces the synthesis — it's already seen the full Phase 1
        // + Phase 2 history on the server, which keeps the Claude/Opus
        // synthesis quality that the platform was originally tuned for.

        System.out.println();
        System.out.println("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
        System.out.println("\u2551   PHASE 3: Synthesis Report           \u2551");
        System.out.println("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");

        List<AgentProfile> agentList = new ArrayList<>();
        List<String> displayList = new ArrayList<>();
        List<String> phase1List = new ArrayList<>();
        List<String> latestList = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            agentList.add(slots.get(i).getAgent());
            displayList.add(slots.get(i).displayName());
            phase1List.add(phase1[i]);
            latestList.add(latest[i]);
        }

        String synthesisBody = promptBuilder.buildPhase3SynthesisMessage(
                activeStakeholder, userPrompt,
                agentList, displayList, phase1List, latestList);

        PanelistSlot synthSlot = slots.get(0);
        System.out.println("\n[" + synthSlot.displayName() + " synthesizing all perspectives...]");
        notifyStatus("Generating synthesis...");
        LlmRequest synthReq = new LlmRequest(
                null, // system instruction already in conversation state
                List.of(new ChatMessage("user", synthesisBody)),
                configuredMaxTokens);
        logRequest(cycle, "phase3", synthSlot.displayName(), synthSlot.providerKey(), synthReq, stateIds[0]);
        StatefulResponse synthR = clients.get(0).sendStateful(synthReq, stateIds[0]);
        String synthesis = synthR.text();

        System.out.println("\n" + synthesis);
        notifySynthesis(synthesis);
        notifyStatus("Debate complete.");

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
    // Phase 1: N calls (one per panelist)
    // Phase 2: N calls per round x debateRounds
    // Phase 3: 1 call (slot 0 synthesizes)
    // Total:   1 + N + (N x debateRounds)
    // ============================================================
    public int getApiCallCount() {
        int n = slots.size();
        return 1 + n + (n * debateRounds);
    }
}
