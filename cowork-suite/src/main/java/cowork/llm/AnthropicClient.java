package cowork.llm;

// AnthropicClient — the suite's only LlmClient: raw HTTPS to the Messages API
// via java.net.http + Gson. Anthropic is stateless on the wire, so sendStateful
// replays a client-side history keyed by an opaque state id, and the tool path
// round-trips tool_use / tool_result blocks until the model answers in text.
// Transient failures (I/O, 408/409/429/5xx) retry with backoff; keys are never
// echoed into error strings.

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class AnthropicClient implements LlmClient {

    static final String API_VERSION = "2023-06-01";
    static final int MAX_RETRIES = 3;
    static final int MAX_STATES = 32;
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final Pattern KEY_PATTERN = Pattern.compile("sk-[A-Za-z0-9_-]{8,}");
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    // Lazily-parsed numbers keep "3" as 3 (not 3.0) when handlers stringify arguments.
    private static final Gson GSON = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LAZILY_PARSED_NUMBER).create();

    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;
    private final String modelName;
    private final int maxTokens;

    // Access-ordered and bounded so abandoned chains cannot grow without limit.
    private final Map<String, ConversationState> conversations = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ConversationState> eldest) {
                    return size() > MAX_STATES;
                }
            });

    private static final class ConversationState {
        String systemInstruction;
        final List<ChatMessage> messages = new ArrayList<>();
    }

    private record Reply(JsonObject message, String error) {}

    public AnthropicClient(HttpClient httpClient, String apiUrl,
                           String apiKey, String modelName, int maxTokens) {
        this.httpClient = httpClient;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.modelName = modelName;
        this.maxTokens = maxTokens;
    }

    @Override
    public String sendMessage(String prompt) {
        return sendMessage(new LlmRequest(null, List.of(new ChatMessage("user", prompt)), maxTokens));
    }

    /** Single-shot; tools on the request are ignored here because there is no executor to run them. */
    @Override
    public String sendMessage(LlmRequest request) {
        Reply reply = call(buildBody(request.systemInstruction(), request.messages(),
                request.maxTokens(), List.of()));
        return reply.error() != null ? reply.error() : textOf(reply.message());
    }

    @Override
    public StatefulResponse sendStateful(LlmRequest request, String previousStateId) {
        return sendStateful(request, previousStateId, null);
    }

    @Override
    public StatefulResponse sendStateful(LlmRequest request, String previousStateId,
                                         ToolExecutor executor) {
        boolean useTools = executor != null && !request.tools().isEmpty();
        ConversationState state = resume(previousStateId);
        if (request.systemInstruction() != null && !request.systemInstruction().isBlank()) {
            state.systemInstruction = request.systemInstruction();
        }
        state.messages.addAll(request.messages());

        // tool_use / tool_result pairs live only for this call; the history keeps final text.
        JsonArray toolTurns = new JsonArray();
        String finalText = null;
        try {
            for (int i = 0; i < ToolExecutor.DEFAULT_MAX_ITERATIONS; i++) {
                JsonObject body = buildBody(state.systemInstruction, state.messages,
                        request.maxTokens(), useTools ? request.tools() : List.of());
                body.getAsJsonArray("messages").addAll(toolTurns);

                Reply reply = call(body);
                if (reply.error() != null) return new StatefulResponse(reply.error(), null);

                JsonArray content = reply.message().getAsJsonArray("content");
                List<ToolCall> calls = useTools && "tool_use".equals(stringOf(reply.message(), "stop_reason"))
                        ? toolCalls(content) : List.of();
                if (calls.isEmpty()) {
                    finalText = textOf(reply.message());
                    break;
                }
                toolTurns.add(turn("assistant", content));
                toolTurns.add(toolResultMessage(executor.executeAll(calls)));
            }
        } catch (RuntimeException e) {
            return new StatefulResponse("[Claude ERROR] " + describe(e), null);
        }
        if (finalText == null) {
            finalText = "[max tool calls exceeded after " + ToolExecutor.DEFAULT_MAX_ITERATIONS + " iterations]";
        }
        state.messages.add(new ChatMessage("assistant", finalText));
        return new StatefulResponse(finalText, store(state, previousStateId));
    }

    // ---- request body -------------------------------------------------------

    JsonObject buildBody(String system, List<ChatMessage> history, int maxTokensForCall,
                         List<ToolSchema> tools) {
        JsonObject body = new JsonObject();
        body.addProperty("model", modelName);
        body.addProperty("max_tokens", maxTokensForCall > 0 ? maxTokensForCall : maxTokens);
        if (system != null && !system.isBlank()) body.addProperty("system", system);
        if (tools != null && !tools.isEmpty()) body.add("tools", serializeTools(tools));
        JsonArray messages = new JsonArray();
        for (ChatMessage m : history) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role());
            o.addProperty("content", m.content());
            messages.add(o);
        }
        body.add("messages", messages);
        return body;
    }

    private static JsonArray serializeTools(List<ToolSchema> tools) {
        JsonArray out = new JsonArray();
        for (ToolSchema t : tools) {
            JsonObject o = new JsonObject();
            o.addProperty("name", t.name());
            o.addProperty("description", t.description());
            o.add("input_schema", GSON.toJsonTree(t.parameterSchema()));
            out.add(o);
        }
        return out;
    }

    /** One user message carrying every tool_result of a batch, as the API requires. */
    static JsonObject toolResultMessage(List<ToolResult> results) {
        JsonArray blocks = new JsonArray();
        for (ToolResult r : results) {
            JsonObject o = new JsonObject();
            o.addProperty("type", "tool_result");
            o.addProperty("tool_use_id", r.callId());
            o.addProperty("content", r.content());
            if (r.isError()) o.addProperty("is_error", true);
            blocks.add(o);
        }
        return turn("user", blocks);
    }

    private static JsonObject turn(String role, JsonArray content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.add("content", content);
        return o;
    }

    // ---- transport ----------------------------------------------------------

    private Reply call(JsonObject body) {
        try {
            HttpResponse<String> resp = post(body);
            if (resp.statusCode() / 100 != 2) {
                return new Reply(null, "[Claude ERROR " + resp.statusCode() + "] " + redact(resp.body()));
            }
            return new Reply(JsonParser.parseString(resp.body()).getAsJsonObject(), null);
        } catch (Exception e) {
            return new Reply(null, "[Claude ERROR] " + describe(e));
        }
    }

    /** Sends with retry/backoff; returns the last response, which may still be non-2xx. */
    private HttpResponse<String> post(JsonObject body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        for (int attempt = 0; ; attempt++) {
            HttpResponse<String> resp = null;
            try {
                resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (!retryable(resp.statusCode()) || attempt >= MAX_RETRIES) return resp;
            } catch (IOException e) {
                if (attempt >= MAX_RETRIES) throw e;
            }
            Thread.sleep(backoffMillis(resp, attempt));
        }
    }

    private static boolean retryable(int status) {
        return status == 408 || status == 409 || status == 429 || status >= 500;
    }

    private static long backoffMillis(HttpResponse<String> resp, int attempt) {
        if (resp != null) {
            String retryAfter = resp.headers().firstValue("retry-after").orElse(null);
            if (retryAfter != null) {
                try {
                    return Math.max(0L, Long.parseLong(retryAfter.trim())) * 1000L;
                } catch (NumberFormatException ignored) {
                    // fall through to exponential backoff
                }
            }
        }
        return 1000L << attempt;
    }

    // ---- response parsing ---------------------------------------------------

    /** Concatenates every text block; an empty refusal is surfaced rather than returned as "". */
    static String textOf(JsonObject message) {
        JsonArray content = message.getAsJsonArray("content");
        if (content == null) return "[Claude ERROR] response had no content blocks";
        StringBuilder sb = new StringBuilder();
        for (JsonElement el : content) {
            JsonObject block = el.getAsJsonObject();
            if ("text".equals(stringOf(block, "type"))) sb.append(stringOf(block, "text"));
        }
        if (sb.isEmpty() && "refusal".equals(stringOf(message, "stop_reason"))) {
            return "[Claude ERROR] request declined (stop_reason=refusal)";
        }
        return sb.toString();
    }

    private static List<ToolCall> toolCalls(JsonArray content) {
        List<ToolCall> calls = new ArrayList<>();
        for (JsonElement el : content) {
            JsonObject block = el.getAsJsonObject();
            if (!"tool_use".equals(stringOf(block, "type"))) continue;
            JsonElement input = block.get("input");
            Map<String, Object> args = input != null && input.isJsonObject()
                    ? GSON.fromJson(input, MAP_TYPE) : Map.of();
            calls.add(new ToolCall(stringOf(block, "id"), stringOf(block, "name"), args));
        }
        return calls;
    }

    private static String stringOf(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    // ---- state + hygiene ----------------------------------------------------

    private ConversationState resume(String previousStateId) {
        ConversationState s = previousStateId == null ? null : conversations.get(previousStateId);
        return s != null ? s : new ConversationState();
    }

    private String store(ConversationState state, String previousStateId) {
        String id = UUID.randomUUID().toString();
        synchronized (conversations) {
            if (previousStateId != null) conversations.remove(previousStateId);
            conversations.put(id, state);
        }
        return id;
    }

    /** Masks anything shaped like an API key so it never reaches logs or the UI. Null-safe. */
    static String redact(String s) {
        return s == null ? null : KEY_PATTERN.matcher(s).replaceAll("sk-***");
    }

    private static String describe(Exception e) {
        String msg = e.getMessage();
        return redact(msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg);
    }
}
