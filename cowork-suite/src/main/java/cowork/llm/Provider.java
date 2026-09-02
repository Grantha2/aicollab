package cowork.llm;

// Provider — the LLM vendors the suite knows how to name. Only ANTHROPIC has a
// client today; the other values stay so persisted records keep parsing.
public enum Provider {
    ANTHROPIC("Anthropic (Claude)"),
    OPENAI("OpenAI (GPT)"),
    GOOGLE("Google (Gemini)");

    private final String displayName;

    Provider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
