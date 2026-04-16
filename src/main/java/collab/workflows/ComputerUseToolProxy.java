package collab.workflows;

import collab.ToolCall;
import collab.ToolResult;
import collab.ToolSchema;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// ============================================================
// ComputerUseToolProxy.java — Java-side handler for Claude's native
// computer-use tools, forwarded to an external sandbox.
//
// WHAT THIS CLASS DOES (one sentence):
// Exposes the four Anthropic computer-use tool types (computer,
// bash, text_editor, and the convenience str_replace_based_edit)
// as ToolSchemas backed by HTTP calls to a local sandbox container
// that owns a real desktop (Xvfb + Chromium or equivalent).
//
// WHY A PROXY INSTEAD OF IMPLEMENTING COMPUTER USE IN JAVA:
// Computer use requires screenshotting a live display and sending
// keystrokes/clicks to a GUI. That is infrastructure (Xvfb, GUI
// automation libraries, a packaged browser) that does not belong
// inside a Swing desktop app that the student team must be able to
// explain line-by-line. The canonical pattern, documented by
// Anthropic in the computer-use reference image, is to run that
// infrastructure in a separate container and forward tool calls to
// it over HTTP. The Java side stays small and pedagogical:
// "serialise the ToolCall to JSON, POST it, parse the result."
//
// HOW TO RUN THE SANDBOX:
// See docs/SANDBOX.md. The short version is:
//     docker compose up -d computer-use-sandbox
// which exposes a /v1/computer-use endpoint on localhost:9000. Set
// `computer.use.sandbox.url` in config.properties to override.
//
// FALLBACK:
// If the sandbox isn't reachable, every tool call returns a
// ToolResult.error so Claude sees the failure and can recover (e.g.
// by explaining to the user that it cannot check availability right
// now). The debate continues — a missing sandbox does not crash the
// workflow.
// ============================================================
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

    /**
     * Returns the three tool schemas Claude expects for computer use
     * (names and shapes match Anthropic's computer-use beta). These
     * are registered with the debate's ToolExecutor so Claude can
     * emit computer_20241022 / bash_20241022 / text_editor_20241022
     * tool_use blocks and receive proxied results.
     */
    public List<ToolSchema> schemas() {
        return List.of(
                ToolSchema.stringParams(
                        "computer_20241022",
                        "Computer-use tool: screenshot the sandbox display "
                                + "or send click / type / key actions.",
                        List.of("action"),
                        Map.of(
                                "action", "One of: screenshot, click, type, key, mouse_move, scroll"
                        )),
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

    /**
     * Handler factory. Returns a Function<ToolCall, ToolResult> that
     * POSTs to the sandbox and wraps the response as a ToolResult.
     * Pass this to ToolExecutor.register(schema, handler).
     */
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
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                return ToolResult.error(call.id(),
                        "computer-use sandbox returned HTTP " + resp.statusCode()
                                + ": " + resp.body());
            }
            // The sandbox returns { "output": "<text>", "image": "<base64 png|null>" }.
            // For this first cut we surface only the textual `output` — the
            // image channel is retained by Claude's native image-input path,
            // which is out of scope for this release. When we upgrade the
            // tool loop to accept images, this is where they are spliced in.
            JsonObject parsed = JsonParser.parseString(resp.body()).getAsJsonObject();
            String out = parsed.has("output") ? parsed.get("output").getAsString() : resp.body();
            return ToolResult.ok(call.id(), out);
        } catch (Exception e) {
            return ToolResult.error(call.id(),
                    "computer-use sandbox unreachable (" + e.getClass().getSimpleName()
                            + "): " + e.getMessage()
                            + " — start the container with `docker compose up computer-use-sandbox`.");
        }
    }
}
