package cowork.config;

// ClientFactory — the one place that turns Config into an LlmClient.
// Keeping `new AnthropicClient(...)` out of tasks and UI means swapping the
// provider or injecting a mock is a single-file change.

import java.net.http.HttpClient;
import java.util.Objects;

import cowork.llm.AnthropicClient;
import cowork.llm.LlmClient;

public final class ClientFactory {

    private final Config config;
    private final HttpClient http;

    public ClientFactory(Config config, HttpClient http) {
        this.config = Objects.requireNonNull(config, "config");
        this.http = http != null ? http : HttpClient.newHttpClient();
    }

    /** A fresh Claude client; hold one instance for the whole of a stateful chain. */
    public LlmClient claude() {
        return new AnthropicClient(http, config.getClaudeUrl(), config.getClaudeKey(),
                config.getClaudeModel(), config.getMaxResponseTokens());
    }

    public int maxTokens() { return config.getMaxResponseTokens(); }
}
