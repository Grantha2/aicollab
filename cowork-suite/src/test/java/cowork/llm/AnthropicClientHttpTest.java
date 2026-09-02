package cowork.llm;

// AnthropicClientHttpTest — drives the real client against a scripted local
// HTTP server (no network): headers, retry/backoff with retry-after, no retry
// on plain 4xx, key redaction in errors, and the full tool loop with state
// chaining.

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicClientHttpTest {

    private static final String KEY = "sk-ant-testkey0123456789";
    private static final Gson GSON = new Gson();

    private record Scripted(int status, String body, Map<String, String> headers) {}

    private HttpServer server;
    private final Deque<Scripted> script = new ConcurrentLinkedDeque<>();
    private final List<JsonObject> requestBodies = Collections.synchronizedList(new ArrayList<>());
    private final List<Headers> requestHeaders = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestBodies.add(JsonParser.parseString(body).getAsJsonObject());
            requestHeaders.add(ex.getRequestHeaders());
            Scripted s = script.pollFirst();
            if (s == null) s = new Scripted(500, "{\"error\":\"script exhausted\"}", Map.of());
            s.headers().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
            byte[] out = s.body().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(s.status(), out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AnthropicClient client() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages";
        return new AnthropicClient(HttpClient.newHttpClient(), url, KEY, "claude-opus-5", 512);
    }

    private static String textReply(String text) {
        JsonObject block = new JsonObject();
        block.addProperty("type", "text");
        block.addProperty("text", text);
        JsonArray content = new JsonArray();
        content.add(block);
        JsonObject msg = new JsonObject();
        msg.add("content", content);
        msg.addProperty("stop_reason", "end_turn");
        return GSON.toJson(msg);
    }

    private static String toolUseReply(String id, String name, Map<String, Object> input) {
        JsonObject text = new JsonObject();
        text.addProperty("type", "text");
        text.addProperty("text", "Let me compute that.");
        JsonObject use = new JsonObject();
        use.addProperty("type", "tool_use");
        use.addProperty("id", id);
        use.addProperty("name", name);
        use.add("input", GSON.toJsonTree(input));
        JsonArray content = new JsonArray();
        content.add(text);
        content.add(use);
        JsonObject msg = new JsonObject();
        msg.add("content", content);
        msg.addProperty("stop_reason", "tool_use");
        return GSON.toJson(msg);
    }

    private static Scripted ok(String body) {
        return new Scripted(200, body, Map.of());
    }

    @Test
    void simplePromptSendsExpectedHeadersAndBody() {
        script.add(ok(textReply("hi there")));
        assertEquals("hi there", client().sendMessage("hello"));

        assertEquals(1, requestBodies.size());
        JsonObject body = requestBodies.get(0);
        assertEquals("claude-opus-5", body.get("model").getAsString());
        assertEquals(512, body.get("max_tokens").getAsInt());
        assertFalse(body.has("system"));
        assertEquals("hello", body.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());

        Headers h = requestHeaders.get(0);
        assertEquals(KEY, h.getFirst("x-api-key"));
        assertEquals("2023-06-01", h.getFirst("anthropic-version"));
        assertNull(h.getFirst("anthropic-beta"));
    }

    @Test
    void clientErrorIsNotRetriedAndKeyIsRedacted() {
        script.add(new Scripted(401, "{\"error\":{\"message\":\"invalid x-api-key " + KEY + "\"}}", Map.of()));
        String out = client().sendMessage("hello");

        assertTrue(out.startsWith("[Claude ERROR 401] "), out);
        assertFalse(out.contains(KEY), out);
        assertTrue(out.contains("sk-***"));
        assertEquals(1, requestBodies.size(), "4xx other than 408/409/429 must not retry");
    }

    @Test
    void retriesOverloadedThenSucceedsHonouringRetryAfter() {
        script.add(new Scripted(529, "{\"error\":\"overloaded\"}", Map.of("retry-after", "0")));
        script.add(new Scripted(429, "{\"error\":\"rate\"}", Map.of("retry-after", "0")));
        script.add(ok(textReply("recovered")));

        assertEquals("recovered", client().sendMessage("hello"));
        assertEquals(3, requestBodies.size());
    }

    @Test
    void givesUpAfterMaxRetries() {
        for (int i = 0; i < AnthropicClient.MAX_RETRIES + 2; i++) {
            script.add(new Scripted(503, "{\"error\":\"down\"}", Map.of("retry-after", "0")));
        }
        String out = client().sendMessage("hello");
        assertTrue(out.startsWith("[Claude ERROR 503]"), out);
        assertEquals(AnthropicClient.MAX_RETRIES + 1, requestBodies.size());
    }

    @Test
    void toolLoopRoundTripsResultsAndChainsState() {
        ToolExecutor executor = new ToolExecutor();
        List<String> seenArgs = new ArrayList<>();
        executor.register(ToolSchema.stringParams("add", "Add two numbers", List.of("a", "b"), null), call -> {
            seenArgs.add(String.valueOf(call.arguments().get("a")) + "+" + call.arguments().get("b"));
            return ToolResult.ok(call.id(), "5");
        });
        script.add(ok(toolUseReply("tu_1", "add", Map.of("a", 2, "b", 3))));
        script.add(ok(textReply("2 + 3 = 5")));

        AnthropicClient client = client();
        LlmRequest req = new LlmRequest("You add.", List.of(new ChatMessage("user", "what is 2+3")), 300,
                executor.schemas());
        StatefulResponse first = client.sendStateful(req, null, executor);

        assertEquals("2 + 3 = 5", first.text());
        assertNotNull(first.stateId());
        assertEquals(List.of("2+3"), seenArgs, "numbers must not be widened to doubles");
        assertEquals(2, requestBodies.size());

        JsonObject b0 = requestBodies.get(0);
        assertEquals("You add.", b0.get("system").getAsString());
        assertEquals("add", b0.getAsJsonArray("tools").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(1, b0.getAsJsonArray("messages").size());

        JsonArray m1 = requestBodies.get(1).getAsJsonArray("messages");
        assertEquals(3, m1.size());
        assertEquals("what is 2+3", m1.get(0).getAsJsonObject().get("content").getAsString());
        JsonObject assistant = m1.get(1).getAsJsonObject();
        assertEquals("assistant", assistant.get("role").getAsString());
        assertEquals("tool_use", assistant.getAsJsonArray("content").get(1).getAsJsonObject().get("type").getAsString());
        JsonObject results = m1.get(2).getAsJsonObject();
        assertEquals("user", results.get("role").getAsString());
        JsonObject r0 = results.getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals("tool_result", r0.get("type").getAsString());
        assertEquals("tu_1", r0.get("tool_use_id").getAsString());
        assertEquals("5", r0.get("content").getAsString());
        assertFalse(r0.has("is_error"));

        // Chained turn: history keeps final text only, not the tool_use/tool_result turns.
        script.add(ok(textReply("you're welcome")));
        StatefulResponse second = client.sendStateful(
                new LlmRequest(null, List.of(new ChatMessage("user", "thanks")), 300), first.stateId());
        assertEquals("you're welcome", second.text());
        assertNotEquals(first.stateId(), second.stateId());
        JsonArray m2 = requestBodies.get(2).getAsJsonArray("messages");
        assertEquals(3, m2.size());
        assertEquals("2 + 3 = 5", m2.get(1).getAsJsonObject().get("content").getAsString());
        assertEquals("thanks", m2.get(2).getAsJsonObject().get("content").getAsString());
        assertEquals("You add.", requestBodies.get(2).get("system").getAsString());
        assertFalse(requestBodies.get(2).has("tools"));
    }

    @Test
    void toolLoopStopsAtIterationCap() {
        ToolExecutor executor = new ToolExecutor();
        executor.register(ToolSchema.noArgs("ping", "Ping"), call -> ToolResult.ok(call.id(), "pong"));
        for (int i = 0; i < ToolExecutor.DEFAULT_MAX_ITERATIONS + 1; i++) {
            script.add(ok(toolUseReply("tu_" + i, "ping", Map.of())));
        }
        StatefulResponse r = client().sendStateful(
                new LlmRequest(null, List.of(new ChatMessage("user", "go")), 100, executor.schemas()),
                null, executor);
        assertEquals("[max tool calls exceeded after " + ToolExecutor.DEFAULT_MAX_ITERATIONS + " iterations]", r.text());
        assertNotNull(r.stateId());
        assertEquals(ToolExecutor.DEFAULT_MAX_ITERATIONS, requestBodies.size());
    }

    @Test
    void unknownStateIdStartsAFreshConversation() {
        script.add(ok(textReply("fresh")));
        StatefulResponse r = client().sendStateful(
                new LlmRequest("sys", List.of(new ChatMessage("user", "hi")), 100), "no-such-state");
        assertEquals("fresh", r.text());
        assertEquals(1, requestBodies.get(0).getAsJsonArray("messages").size());
    }
}
