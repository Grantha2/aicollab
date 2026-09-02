package cowork.tasks;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cowork.config.ClientFactory;
import cowork.config.Config;
import cowork.llm.ChatMessage;
import cowork.llm.LlmClient;
import cowork.llm.LlmRequest;
import cowork.llm.StatefulResponse;
import cowork.llm.ToolExecutor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scripted LlmClient for tests: returns the queued replies in order (the last one repeats)
 * and records every prompt. asFactory() wraps it in a real ClientFactory whose HttpClient is
 * an in-process stub that turns the Anthropic wire body back into a prompt and answers with
 * this fake's reply, so tasks are exercised through ctx.clients().claude() unchanged.
 */
public final class FakeLlmClient implements LlmClient {

    private static final Gson GSON = new Gson();

    private final List<String> replies;
    private final List<String> prompts = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger next = new AtomicInteger();

    public FakeLlmClient(String... replies) {
        this.replies = replies.length == 0 ? List.of("(fake reply)") : List.of(replies);
    }

    @Override
    public String sendMessage(String prompt) {
        prompts.add(prompt);
        int i = Math.min(next.getAndIncrement(), replies.size() - 1);
        return replies.get(i);
    }

    @Override
    public String sendMessage(LlmRequest request) {
        StringBuilder flat = new StringBuilder();
        if (request.systemInstruction() != null && !request.systemInstruction().isEmpty()) {
            flat.append(request.systemInstruction()).append("\n\n");
        }
        for (ChatMessage msg : request.messages()) {
            flat.append(msg.content()).append("\n\n");
        }
        return sendMessage(flat.toString().trim());
    }

    @Override
    public StatefulResponse sendStateful(LlmRequest request, String previousStateId) {
        return new StatefulResponse(sendMessage(request), null);
    }

    @Override
    public StatefulResponse sendStateful(LlmRequest request, String previousStateId, ToolExecutor executor) {
        return sendStateful(request, previousStateId);
    }

    public List<String> prompts() {
        return List.copyOf(prompts);
    }

    /** Real ClientFactory over a throwaway config file under cowork.home and the wire stub below. */
    public ClientFactory asFactory() throws IOException {
        Path file = Config.defaultPath();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "claude.key=test-placeholder\nclaude.model=test-model\nmax.response.tokens=1024\n");
        return new ClientFactory(new Config(file), new WireStub());
    }

    /** Answers every Messages-API POST with the fake's next reply; the prompt is the flattened body. */
    private final class WireStub extends HttpClient {

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            String reply = sendMessage(promptOf(requestBody(request)));
            JsonObject block = new JsonObject();
            block.addProperty("type", "text");
            block.addProperty("text", reply);
            JsonArray content = new JsonArray();
            content.add(block);
            JsonObject message = new JsonObject();
            message.add("content", content);
            message.addProperty("stop_reason", "end_turn");
            // AnthropicClient always asks for a String body, so T is String here.
            return (HttpResponse<T>) new StubResponse(request, GSON.toJson(message));
        }

        private static String promptOf(String json) {
            JsonObject body = JsonParser.parseString(json).getAsJsonObject();
            StringBuilder flat = new StringBuilder();
            JsonElement system = body.get("system");
            if (system != null && system.isJsonPrimitive()) flat.append(system.getAsString()).append("\n\n");
            for (JsonElement m : body.getAsJsonArray("messages")) {
                JsonElement c = m.getAsJsonObject().get("content");
                flat.append(c.isJsonPrimitive() ? c.getAsString() : c.toString()).append("\n\n");
            }
            return flat.toString().trim();
        }

        private static String requestBody(HttpRequest request) {
            HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElse(null);
            if (publisher == null) return "{}";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            CompletableFuture<Void> done = new CompletableFuture<>();
            publisher.subscribe(new Flow.Subscriber<>() {
                @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
                @Override public void onNext(ByteBuffer b) {
                    byte[] bytes = new byte[b.remaining()];
                    b.get(bytes);
                    out.writeBytes(bytes);
                }
                @Override public void onError(Throwable t) { done.completeExceptionally(t); }
                @Override public void onComplete() { done.complete(null); }
            });
            done.join();
            return out.toString(StandardCharsets.UTF_8);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            return CompletableFuture.completedFuture(send(request, handler));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, handler);
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { throw new UnsupportedOperationException(); }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
    }

    private record StubResponse(HttpRequest request, String body) implements HttpResponse<String> {
        @Override public int statusCode() { return 200; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
