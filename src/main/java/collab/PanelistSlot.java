package collab;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

// ============================================================
// PanelistSlot.java — One seat in the debate panel.
//
// A slot combines three pieces that were previously scattered:
//   1) which provider runs the model (Anthropic/OpenAI/Google)
//   2) which model ID is sent in the request ("claude-opus-4-6" etc.)
//   3) which agent profile (identity / perspective / lens) is assigned
//
// Any mix is allowed — two Claude slots with different profiles
// is a legitimate configuration. The panel size is no longer
// fixed at three.
//
// Experimental branch additions:
//   4) per-slot attachments — files this slot alone should see
//      (on top of the debate-level profile-set attachments).
//   5) per-slot tool names — which ToolSchemas from the debate-wide
//      ToolExecutor are exposed to THIS slot. The Finance panelist
//      gets the budget PDF and a budget-query tool; the Engineering
//      panelist sees neither. Null / empty means "all tools" (the
//      back-compat default).
// ============================================================
public final class PanelistSlot {

    private Provider provider;
    private String model;
    // The agent profile is stored inline in the JSON so old profile-set
    // files keep rendering cleanly even if the AgentProfileLibrary ever
    // goes away. The editor sources selections from the library, but
    // what we persist is the resolved AgentProfile itself.
    private AgentProfile agent;

    // Experimental per-slot context. Both lists are intentionally
    // nullable in persisted JSON — older profile-set files won't
    // have these fields and should deserialise unchanged. The
    // getters below normalise null -> empty list so callers never
    // need a null-check.
    private List<FileAttachment> attachments;
    private List<String> allowedTools;

    // no-arg constructor for Gson
    PanelistSlot() {}

    public PanelistSlot(Provider provider, String model, AgentProfile agent) {
        this.provider = provider;
        this.model = model;
        this.agent = agent;
    }

    public Provider getProvider() { return provider; }
    public String getModel() { return model; }
    public AgentProfile getAgent() { return agent; }

    public void setProvider(Provider provider) { this.provider = provider; }
    public void setModel(String model) { this.model = model; }
    public void setAgent(AgentProfile agent) { this.agent = agent; }

    /**
     * Per-slot file attachments. Empty list when the slot has no
     * override; Maestro unions this with the debate-level attachment
     * list on each Phase 1 turn for this slot specifically.
     */
    public List<FileAttachment> getAttachments() {
        return attachments == null ? new ArrayList<>() : attachments;
    }

    public void setAttachments(List<FileAttachment> attachments) {
        this.attachments = attachments;
    }

    /**
     * Names of ToolSchemas (from the shared ToolExecutor) this slot
     * is allowed to invoke. Empty / null means "inherit the full set"
     * — which is the pre-experimental behaviour every existing debate
     * relies on, so legacy profile sets keep working.
     */
    public List<String> getAllowedTools() {
        return allowedTools == null ? List.of() : allowedTools;
    }

    public void setAllowedTools(List<String> allowedTools) {
        this.allowedTools = allowedTools;
    }

    // ============================================================
    // buildClient() — Instantiates the correct LlmClient for this
    // slot from the shared Config/HttpClient. Centralises the
    // provider -> client mapping so callers (Maestro wiring in
    // MainGui / Main) don't repeat switch statements.
    // ============================================================
    public LlmClient buildClient(Config config, HttpClient httpClient, int maxTokens) {
        Provider p = provider != null ? provider : Provider.ANTHROPIC;
        String modelId = (model == null || model.isBlank())
                ? defaultModelFor(p, config)
                : model;
        return switch (p) {
            case ANTHROPIC -> new AnthropicClient(httpClient,
                    config.getClaudeUrl(), config.getClaudeKey(), modelId, maxTokens);
            case OPENAI -> new OpenAiClient(httpClient,
                    config.getOpenAiUrl(), config.getOpenAiKey(), modelId, maxTokens);
            case GOOGLE -> new GeminiClient(httpClient,
                    config.getGeminiKey(), modelId, maxTokens);
        };
    }

    public static String defaultModelFor(Provider p, Config config) {
        return switch (p) {
            case ANTHROPIC -> config.getClaudeModel();
            case OPENAI -> config.getOpenAiModel();
            case GOOGLE -> config.getGeminiModel();
        };
    }

    /**
     * Short display label for audit logs and UI titles — "Claude" /
     * "GPT" / "Gemini" by convention, falling back to the provider name.
     */
    public String displayName() {
        if (agent != null && agent.getName() != null && !agent.getName().isBlank()) {
            return agent.getName();
        }
        return switch (provider) {
            case ANTHROPIC -> "Claude";
            case OPENAI -> "GPT";
            case GOOGLE -> "Gemini";
        };
    }

    public String providerKey() {
        return switch (provider) {
            case ANTHROPIC -> "anthropic";
            case OPENAI -> "openai";
            case GOOGLE -> "google";
        };
    }
}
