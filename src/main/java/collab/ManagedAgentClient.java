package collab;

// ============================================================
// ManagedAgentClient.java — Claude Managed Agents + Files API client.
//
// WHAT THIS CLASS DOES (one sentence):
// Wraps the Claude Managed Agents REST API (agents / environments /
// sessions / streaming events) and the Files API (upload / download)
// behind small Java methods the agentic task layer can call.
//
// WHY IT IS NOT AN LlmClient:
// LlmClient is synchronous request/response — fine for the debate and
// simple paths. Managed Agents are session-based, long-running, and
// stream events (tool use, tool results, text chunks, files) as the
// agent works. The paradigm is different enough that we keep this as
// a sibling class so the existing paths are untouched.
//
// BETA HEADERS:
// Both Managed Agents and the Files API are beta surfaces. We pin the
// beta version with the "anthropic-beta" header and isolate all calls
// here so any future API shift is contained to this one file.
//
// HTTP STRATEGY:
// Same hand-rolled java.net.http.HttpClient pattern as AnthropicClient.
// No Anthropic Java SDK dependency — keeps the dependency tree at
// Gson-only and avoids blocking on SDK coverage of the beta.
// ============================================================

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ManagedAgentClient {

    // Beta version pins — update here when the APIs graduate.
    private static final String MANAGED_AGENTS_BETA = "managed-agents-2026-04-01";
    private static final String FILES_API_BETA      = "files-api-2025-04-14";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String modelName;

    public ManagedAgentClient(HttpClient httpClient,
                              String baseUrl,
                              String apiKey,
                              String modelName) {
        this.httpClient = httpClient;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    /** True when the client has a non-empty API key and base URL configured. */
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }

    // ============================================================
    // createAgent() — POST /v1/agents
    //
    // Registers a reusable agent definition (system prompt + tool set)
    // and returns the server-issued agent id.
    // ============================================================
    public String createAgent(String systemPrompt, List<String> tools) {
        JsonObject body = new JsonObject();
        body.addProperty("model", modelName);
        body.addProperty("instructions", systemPrompt == null ? "" : systemPrompt);

        JsonArray toolsArr = new JsonArray();
        if (tools != null) {
            for (String t : tools) {
                JsonObject tool = new JsonObject();
                tool.addProperty("type", t);
                toolsArr.add(tool);
            }
        }
        body.add("tools", toolsArr);

        JsonObject resp = postJson("/v1/agents", body.toString(), /*withAgentsBeta=*/true);
        if (resp == null) return null;
        return optString(resp, "id");
    }

    // ============================================================
    // createEnvironment() — POST /v1/environments
    //
    // Spins up a sandboxed environment the agent will run tools in.
    // packages = pip-installable dependencies (e.g. ["pypdf"]).
    // ============================================================
    public String createEnvironment(List<String> packages) {
        JsonObject body = new JsonObject();

        JsonArray pkgs = new JsonArray();
        if (packages != null) packages.forEach(pkgs::add);
        body.add("packages", pkgs);

        JsonObject resp = postJson("/v1/environments", body.toString(), /*withAgentsBeta=*/true);
        if (resp == null) return null;
        return optString(resp, "id");
    }

    // ============================================================
    // createSession() — POST /v1/sessions
    //
    // Binds an agent to an environment and returns a session id we can
    // stream events against. One session per task invocation.
    // ============================================================
    public String createSession(String agentId, String envId) {
        JsonObject body = new JsonObject();
        body.addProperty("agent_id", agentId);
        body.addProperty("environment_id", envId);

        JsonObject resp = postJson("/v1/sessions", body.toString(), /*withAgentsBeta=*/true);
        if (resp == null) return null;
        return optString(resp, "id");
    }

    // ============================================================
    // sendEvent() — POST /v1/sessions/{id}/events (SSE streamed)
    //
    // Sends a user turn into the session and streams back every event
    // the agent produces — text chunks, tool calls, tool results, and
    // file outputs — by calling the supplied consumer as each one
    // arrives.
    //
    // WHY A CONSUMER INSTEAD OF A Stream:
    // Stream-of-events across a live HTTP connection interacts badly
    // with try-with-resources and lazy evaluation. A simple push-style
    // callback keeps the reader loop obvious and finishes cleanly.
    //
    // PARSING:
    // The server speaks Server-Sent Events. Each event is a block of
    // "field: value" lines terminated by a blank line. We care about
    // the "data:" lines, which carry a JSON object per delta. We read
    // line-by-line from the response input stream, accumulate lines in
    // a buffer until we hit a blank line, then decode and dispatch.
    // ============================================================
    public void sendEvent(String sessionId,
                          String userMessage,
                          List<String> inputFileIds,
                          Consumer<AgentEvent> consumer) {

        JsonObject body = new JsonObject();
        body.addProperty("type", "user_message");

        JsonArray contentArr = new JsonArray();

        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", userMessage == null ? "" : userMessage);
        contentArr.add(textPart);

        if (inputFileIds != null) {
            for (String fid : inputFileIds) {
                if (fid == null || fid.isBlank()) continue;
                JsonObject filePart = new JsonObject();
                filePart.addProperty("type", "file");
                filePart.addProperty("file_id", fid);
                contentArr.add(filePart);
            }
        }
        body.add("content", contentArr);
        body.addProperty("stream", true);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/sessions/" + sessionId + "/events"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("anthropic-beta", MANAGED_AGENTS_BETA)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String err = "[Agent ERROR " + response.statusCode() + "] "
                        + new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                consumer.accept(AgentEvent.status(err));
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

                StringBuilder dataBuf = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        // blank line -> dispatch whatever we've buffered
                        if (dataBuf.length() > 0) {
                            dispatchSseData(dataBuf.toString(), consumer);
                            dataBuf.setLength(0);
                        }
                        continue;
                    }
                    if (line.startsWith(":")) {
                        // SSE comment / keep-alive — ignore
                        continue;
                    }
                    if (line.startsWith("data:")) {
                        // SSE spec: strip one leading space after the colon if present.
                        String chunk = line.substring(5);
                        if (chunk.startsWith(" ")) chunk = chunk.substring(1);
                        if (dataBuf.length() > 0) dataBuf.append('\n');
                        dataBuf.append(chunk);
                    }
                    // Other SSE fields ("event:", "id:", "retry:") aren't used.
                }
                // Flush any trailing event the server forgot to terminate with \n\n.
                if (dataBuf.length() > 0) {
                    dispatchSseData(dataBuf.toString(), consumer);
                }
            }

        } catch (Exception e) {
            consumer.accept(AgentEvent.status("[Agent ERROR] " + e.getMessage()));
        }
    }

    // ============================================================
    // dispatchSseData() — Decode one SSE "data:" payload and turn it
    // into one or more AgentEvents that the consumer understands.
    //
    // The Managed Agents SSE stream wraps every delta in a JSON object
    // with a "type" field. We map the shapes we care about:
    //   { "type": "text_delta", "text": "..." }
    //   { "type": "tool_use",  "name": "bash", "input": {...} }
    //   { "type": "tool_result", "output": "..." }
    //   { "type": "file", "file_id": "...", "name": "..." }
    //   { "type": "session_status", "status": "completed" | "error" }
    //
    // Anything we don't recognize becomes a "status" event with the
    // raw JSON so nothing is silently dropped.
    // ============================================================
    private static void dispatchSseData(String data, Consumer<AgentEvent> consumer) {
        String trimmed = data.trim();
        if (trimmed.isEmpty() || trimmed.equals("[DONE]")) return;

        try {
            JsonElement el = JsonParser.parseString(trimmed);
            if (!el.isJsonObject()) {
                consumer.accept(AgentEvent.status(trimmed));
                return;
            }
            JsonObject obj = el.getAsJsonObject();
            String type = optString(obj, "type");
            if (type == null) type = "";

            switch (type) {
                case "text_delta":
                case "text":
                case "message_delta": {
                    String text = firstNonNull(
                            optString(obj, "text"),
                            optString(obj, "delta"),
                            optString(obj, "content"));
                    if (text != null) consumer.accept(AgentEvent.text(text));
                    break;
                }
                case "tool_use": {
                    String name = optString(obj, "name");
                    JsonElement input = obj.get("input");
                    String body = (name != null ? name : "tool")
                            + (input != null ? " " + input.toString() : "");
                    consumer.accept(AgentEvent.toolUse(body));
                    break;
                }
                case "tool_result": {
                    String output = firstNonNull(
                            optString(obj, "output"),
                            optString(obj, "content"));
                    consumer.accept(AgentEvent.toolResult(output == null ? "" : output));
                    break;
                }
                case "file":
                case "file_output": {
                    String fileId = firstNonNull(optString(obj, "file_id"), optString(obj, "id"));
                    String name   = firstNonNull(optString(obj, "name"), optString(obj, "filename"));
                    if (fileId != null) {
                        consumer.accept(AgentEvent.file(fileId, name == null ? fileId : name));
                    }
                    break;
                }
                case "session_status":
                case "status":
                case "message_stop":
                case "message_start": {
                    String status = firstNonNull(optString(obj, "status"), type);
                    consumer.accept(AgentEvent.status(status));
                    break;
                }
                default:
                    consumer.accept(AgentEvent.status(trimmed));
            }
        } catch (Exception e) {
            consumer.accept(AgentEvent.status("[parse] " + data));
        }
    }

    // ============================================================
    // uploadFile() — POST /v1/files (multipart/form-data)
    //
    // Uploads a local file and returns the Files API file_id the agent
    // can reference in a user message. Hand-rolled multipart because
    // java.net.http.HttpClient doesn't ship a multipart builder and
    // pulling in a library for this one call isn't worth it.
    //
    // Multipart body layout:
    //   --BOUNDARY\r\n
    //   Content-Disposition: form-data; name="purpose"\r\n\r\n
    //   vision\r\n
    //   --BOUNDARY\r\n
    //   Content-Disposition: form-data; name="file"; filename="..."\r\n
    //   Content-Type: ...\r\n\r\n
    //   <raw bytes>\r\n
    //   --BOUNDARY--\r\n
    // ============================================================
    public String uploadFile(Path filePath, String mimeType) {
        try {
            byte[] fileBytes = Files.readAllBytes(filePath);
            String filename  = filePath.getFileName().toString();
            String boundary  = "----aicollabBoundary" + UUID.randomUUID().toString().replace("-", "");

            String partHeader1 =
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"purpose\"\r\n\r\n" +
                    "vision\r\n";

            String partHeader2 =
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n" +
                    "Content-Type: " + (mimeType == null ? "application/octet-stream" : mimeType) + "\r\n\r\n";

            String tail = "\r\n--" + boundary + "--\r\n";

            List<byte[]> chunks = new ArrayList<>();
            chunks.add(partHeader1.getBytes(StandardCharsets.UTF_8));
            chunks.add(partHeader2.getBytes(StandardCharsets.UTF_8));
            chunks.add(fileBytes);
            chunks.add(tail.getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/files"))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("anthropic-beta", FILES_API_BETA)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(chunks))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("[ManagedAgentClient] uploadFile " + response.statusCode()
                        + ": " + response.body());
                return null;
            }

            JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
            return optString(obj, "id");

        } catch (Exception e) {
            System.err.println("[ManagedAgentClient] uploadFile failed: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // downloadFile() — GET /v1/files/{id}/content
    //
    // Streams an agent-produced file to disk. Used by the panel when
    // the user clicks a "file" event's download button.
    // ============================================================
    public boolean downloadFile(String fileId, Path outputPath) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/files/" + fileId + "/content"))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("anthropic-beta", FILES_API_BETA)
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                System.err.println("[ManagedAgentClient] downloadFile " + response.statusCode());
                return false;
            }

            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            try (InputStream in = response.body()) {
                Files.copy(in, outputPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;

        } catch (Exception e) {
            System.err.println("[ManagedAgentClient] downloadFile failed: " + e.getMessage());
            return false;
        }
    }

    // ============================================================
    // Low-level JSON POST helper used by the three create* methods.
    // Returns the parsed response object, or null on any failure.
    // ============================================================
    private JsonObject postJson(String pathSuffix, String body, boolean withAgentsBeta) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + pathSuffix))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01");

            if (withAgentsBeta) {
                builder.header("anthropic-beta", MANAGED_AGENTS_BETA);
            }

            HttpRequest request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("[ManagedAgentClient] " + pathSuffix + " returned "
                        + response.statusCode() + ": " + response.body());
                return null;
            }

            JsonElement el = JsonParser.parseString(response.body());
            return el.isJsonObject() ? el.getAsJsonObject() : null;

        } catch (IOException | InterruptedException e) {
            System.err.println("[ManagedAgentClient] " + pathSuffix + " failed: " + e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }

    // ---------- tiny helpers ----------

    private static String optString(JsonObject obj, String field) {
        if (obj == null || !obj.has(field)) return null;
        JsonElement el = obj.get(field);
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) return el.getAsString();
        return el.toString();
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) if (v != null) return v;
        return null;
    }

    private static String trimTrailingSlash(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
