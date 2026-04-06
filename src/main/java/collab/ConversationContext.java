package collab;

// ============================================================
// ConversationContext.java — Stores conversation history so the
// AI panel can reference previous debate cycles.
//
// WHAT THIS CLASS DOES (one sentence):
// Keeps a running log of synthesis reports from past cycles and
// provides that history as a text block for prompt injection.
//
// HOW IT FITS THE ARCHITECTURE:
// After each debate cycle, Maestro calls addSynthesis() to
// store the synthesis report. On the NEXT cycle, PromptBuilder
// calls getHistoryBlock() to include that history in the prompt
// so the AI models know what was discussed before.
//
// WHY CONVERSATION MEMORY MATTERS:
// Without memory, every cycle starts from scratch. The panel
// can't say "as we discussed last round..." or build on previous
// conclusions. Memory turns isolated Q&A into a real conversation.
//
// OVERFLOW PROTECTION:
// Each synthesis report can be thousands of characters. After
// several cycles, the combined history could exceed the model's
// token limit and cause API errors. We cap history at a maximum
// character count (default 24,000 chars ~= 6,000 tokens) and
// trim the OLDEST cycles first when it overflows.
//
// WHY CHARACTERS INSTEAD OF TOKENS?
// Counting tokens accurately requires the model's tokenizer,
// which is different per provider. Characters are a rough proxy:
// ~4 chars per token for English text. Not precise, but good
// enough for overflow protection without adding a dependency.
// ============================================================

import java.util.ArrayList;
import java.util.List;

public class ConversationContext {

    // Each entry is one cycle's synthesis report.
    // The oldest entry is at index 0; newest is at the end.
    private final List<String> syntheses = new ArrayList<>();
    private final List<ConversationTurn> turns = new ArrayList<>();

    // Maximum total characters across all stored syntheses.
    // When exceeded, we remove the oldest entries first.
    private final int maxHistoryChars;

    // ============================================================
    // Constructor.
    //
    // PARAMETER:
    //   maxHistoryChars — the character budget for stored history.
    //                     Default in Config is 24,000 (~6,000 tokens).
    // ============================================================
    public ConversationContext(int maxHistoryChars) {
        this.maxHistoryChars = maxHistoryChars;
    }

    // ============================================================
    // addSynthesis() — Stores a new synthesis report and trims
    // overflow if the total history exceeds the character budget.
    //
    // Called by Maestro after Phase 3 completes.
    //
    // PARAMETER:
    //   synthesis — the full synthesis report text from Claude
    // ============================================================
    public void addSynthesis(String synthesis) {
        syntheses.add(synthesis);
        trimHistory();
    }

    public void addTurn(ConversationTurn turn) {
        turns.add(turn);
    }

    // ============================================================
    // getHistoryBlock() — Returns all stored history as a single
    // formatted text block ready for prompt injection.
    //
    // If there's no history yet (first cycle), returns an empty
    // string so prompts work identically to the stateless version.
    //
    // FORMAT:
    //   === CONVERSATION HISTORY ===
    //   --- Cycle 1 Synthesis ---
    //   [synthesis text]
    //   --- Cycle 2 Synthesis ---
    //   [synthesis text]
    //   === END HISTORY ===
    // ============================================================
    public String getHistoryBlock() {
        if (syntheses.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== CONVERSATION HISTORY ===\n");
        sb.append("The panel has discussed this topic before. Here are the key ");
        sb.append("conclusions from previous cycles. Build on these \u2014 don't repeat them.\n\n");

        for (int i = 0; i < syntheses.size(); i++) {
            sb.append("--- Cycle ").append(i + 1).append(" Synthesis ---\n");
            sb.append(syntheses.get(i)).append("\n\n");
        }

        sb.append("=== END HISTORY ===\n\n");
        return sb.toString();
    }

    // ============================================================
    // getCycleCount() — Returns how many synthesis reports are stored.
    //
    // Used by Main.java and Maestro to show status messages
    // like "Panel has context from 3 previous cycles."
    // ============================================================
    public int getCycleCount() {
        return syntheses.size();
    }

    public int getTurnCount() {
        return turns.size();
    }

    // ============================================================
    // clear() — Resets all stored history.
    //
    // Useful if the user wants to start a fresh conversation
    // without restarting the program.
    // ============================================================
    public void clear() {
        syntheses.clear();
        turns.clear();
    }

    // ============================================================
    // trimHistory() — Removes oldest cycles until total history
    // fits within the character budget.
    //
    // WHY TRIM FROM THE OLDEST:
    // Recent discussions are more relevant than old ones. If we
    // have to cut something, the first cycle's synthesis is the
    // least useful context for the current cycle.
    //
    // This runs automatically after every addSynthesis() call.
    // ============================================================
    private void trimHistory() {
        while (totalChars() > maxHistoryChars && syntheses.size() > 1) {
            // Remove the oldest synthesis (index 0).
            // We keep at least 1 entry so there's always SOME context.
            String removed = syntheses.remove(0);
            System.out.println("[Memory] Trimmed oldest cycle (" + removed.length()
                    + " chars) to stay within " + maxHistoryChars + " char limit.");
        }
    }

    // ============================================================
    // totalChars() — Counts total characters across all stored
    // syntheses. Used by trimHistory() to check the budget.
    // ============================================================
    private int totalChars() {
        int total = 0;
        for (String s : syntheses) {
            total += s.length();
        }
        return total;
    }
}
