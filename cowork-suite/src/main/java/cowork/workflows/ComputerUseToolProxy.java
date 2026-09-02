package cowork.workflows;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cowork.llm.ToolCall;
import cowork.llm.ToolResult;
import cowork.llm.ToolSchema;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Exposes Claude's computer-use tools (computer, bash, text_editor) as ToolSchemas whose
 * handler forwards each call as JSON to an external sandbox container that owns a real
 * desktop. Screenshot/GUI automation stays out of the Swing app; if the sandbox is not
 * reachable every call returns ToolResult.error so Claude can recover textually.
 */
public final class ComputerUseToolProxy {

    public static final String DEFAULT_SANDBOX_URL = "http://localhost:9000/v1/computer-use";
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient http;
    private final String sandboxUrl;
    private final Gson gson = new Gson();

    public ComputerUseToolProxy(HttpClient http, String sandboxUrl) {
        this.http = http;
        this.sandboxUrl = sandboxUrl == null || sandboxUrl.isBlank()
                ? DEFAULT_SANDBOX_URL : sandboxUrl;
    }

    /** Names and shapes match Anthropic's computer-use beta tool types. */
    public List<ToolSchema> schemas() {
        return List.of(
                ToolSchema.stringParams(
                        "computer_20241022",
                        "Computer-use tool: screenshot the sandbox display "
                                + "or send click / type / key actions.",
                        List.of("action"),
                        Map.of("action", "One of: screenshot, click, type, key, mouse_move, scroll")),
                ToolSchema.stringParams(
                        "bash_20241022",
                        "Execute a bash command inside the sandbox container.",
                        List.of("command"),
                        Map.of("command", "Shell command to execute")),
                ToolSchema.stringParams(
                        "text_editor_20241022",
                        "View / create / edit text files inside the sandbox container.",
                        List.of("command", "path"),
                        Map.of(
                                "command", "view | create | str_replace | insert | undo_edit",
                                "path", "Absolute path inside the sandbox")));
    }

    public Function<ToolCall, ToolResult> handler() {
        return this::dispatch;
    }

    private ToolResult dispatch(ToolCall call) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("tool", call.name());
            body.add("arguments", gson.toJsonTree(call.arguments()));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(sandboxUrl))
                    .header("Content-Type", "application/json")
                    .timeout(CALL_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                return ToolResult.error(call.id(),
                        "computer-use sandbox returned HTTP " + resp.statusCode() + ": " + resp.body());
            }
            // Sandbox replies { "output": "<text>", "image": "<base64 png|null>" }; only the
            // text channel is surfaced until the tool loop accepts image results.
            JsonObject parsed = JsonParser.parseString(resp.body()).getAsJsonObject();
            String out = parsed.has("output") && !parsed.get("output").isJsonNull()
                    ? parsed.get("output").getAsString() : resp.body();
            return ToolResult.ok(call.id(), out);
        } catch (Exception e) {
            return ToolResult.error(call.id(),
                    "computer-use sandbox unreachable (" + e.getClass().getSimpleName()
                            + "): " + e.getMessage()
                            + " — start the container with `docker compose up computer-use-sandbox`.");
        }
    }
}
