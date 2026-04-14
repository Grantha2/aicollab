package collab;

// ============================================================
// Provider.java — Enumeration of LLM providers supported by the
// panel.
//
// WHY THIS EXISTS:
// The panel used to be hardcoded to Claude + GPT + Gemini (one
// slot per provider). Variable panelists allow any mix of
// providers — including duplicates (e.g. two Claudes). This
// enum is the single source of truth for which providers the
// app knows how to instantiate clients for, and gives the
// profile editor a typed list to populate the Provider combo.
// ============================================================
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
