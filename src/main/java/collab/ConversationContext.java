package collab;

import java.util.ArrayList;
import java.util.List;

// Stores synthesis reports from previous debate cycles so PromptBuilder
// can include them as "what the panel already decided."
public class ConversationContext {

    private final List<String> syntheses = new ArrayList<>();
    private final int maxHistoryChars;

    public ConversationContext(int maxHistoryChars) {
        this.maxHistoryChars = maxHistoryChars;
    }

    public void addSynthesis(String synthesis) {
        syntheses.add(synthesis);
        while (totalChars() > maxHistoryChars && syntheses.size() > 1) {
            syntheses.remove(0);
        }
    }

    public String getHistoryBlock() {
        if (syntheses.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("=== CONVERSATION HISTORY ===\n");
        sb.append("Previous cycle conclusions \u2014 build on these, don't repeat them.\n\n");
        for (int i = 0; i < syntheses.size(); i++) {
            sb.append("--- Cycle ").append(i + 1).append(" Synthesis ---\n");
            sb.append(syntheses.get(i)).append("\n\n");
        }
        sb.append("=== END HISTORY ===\n\n");
        return sb.toString();
    }

    public int getCycleCount() { return syntheses.size(); }

    public void clear() { syntheses.clear(); }

    private int totalChars() {
        int total = 0;
        for (String s : syntheses) total += s.length();
        return total;
    }
}
