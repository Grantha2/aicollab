package collab;

import java.net.http.HttpClient;

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
// ============================================================
public final class PanelistSlot {

    private Provider provider;
    private String model;
    // The agent profile is stored inline in the JSON so old profile-set
    // files keep rendering cleanly even if the AgentProfileLibrary ever
    // goes away. The editor sources selections from the library, but
    // what we persist is the resolved AgentProfile itself.
    private AgentProfile agent;

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
