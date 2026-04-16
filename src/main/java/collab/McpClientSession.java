package collab;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// ============================================================
// McpClientSession.java — Minimal MCP client over JSON-RPC 2.0.
//
// WHAT THIS CLASS DOES (one sentence):
// Opens and owns a single JSON-RPC session to one MCP server (stdio
// subprocess or HTTP endpoint), exposes list-tools and call-tool
// methods, and cleanly shuts the server down on close.
//
// WHY A HAND-ROLLED CLIENT INSTEAD OF mcp-sdk-java:
// The Model Context Protocol wire is JSON-RPC 2.0 — a single text
// frame per request/response. The protocol's "core" for our purposes
// is two methods: tools/list and tools/call. Pulling in the full
// mcp-sdk-java would add a transitive dependency footprint the
// student team would have to explain; a ~180-line hand-rolled client
// stays entirely in-tree and matches the existing "small pile of JDK
// HTTP calls" pattern of AnthropicClient / OpenAiClient / GeminiClient.
//
// TRANSPORTS:
//   stdio — Spawn the configured command as a subprocess. Write one
//   JSON frame per line to its stdin, read one JSON frame per line
//   from its stdout. Servers following the MCP reference use
//   newline-delimited JSON; we honour that.
//   http  — POST the JSON frame to the configured URL. Read the body
//   as the response frame. Synchronous request/response, no SSE
//   streaming for this experimental cut (adequate for retrieval and
//   PDF tools; richer transports can come later).
//
// LIFECYCLE:
//   - Constructor opens the transport (spawns process / no-op for http).
//   - initialize() performs the MCP handshake (protocolVersion,
//     clientInfo, capabilities).
//   - listTools() returns the server's advertised tools as JSON.
//   - callTool(name, args) invokes a tool and returns its text content.
//   - close() sends shutdown + waits for the subprocess to exit with
//     a short timeout.
// ============================================================
public final class McpClientSession implements AutoCloseable {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final McpServerAttachment attachment;
    private final HttpClient http; // used only for http transport
    private final AtomicLong nextId = new AtomicLong(1);
    private final Gson gson = new Gson();

    // stdio transport state
    private Process process;
    private BufferedWriter stdinWriter;
    private BufferedReader stdoutReader;

    public McpClientSession(McpServerAttachment attachment, HttpClient http) throws IOException {
        this.attachment = attachment;
        this.http = http;
        if (McpServerAttachment.TRANSPORT_STDIO.equals(attachment.transport())) {
            openStdio();
        }
        // http transport has no "open" step; every call is a fresh POST.
    }

    private void openStdio() throws IOException {
        List<String> tokens = tokenize(attachment.command());
        ProcessBuilder pb = new ProcessBuilder(tokens);
        pb.redirectErrorStream(false);
        this.process = pb.start();
        this.stdinWriter = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.stdoutReader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    // Crude shell-like tokenizer: splits on whitespace, honours
    // double-quoted segments. Adequate for commands configured via
    // the attachments dialog; we are not parsing arbitrary shell.
    private static List<String> tokenize(String command) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '"') { inQuote = !inQuote; continue; }
            if (!inQuote && Character.isWhitespace(c)) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /**
     * Sends the MCP initialize request. Must be called once before
     * listTools / callTool. Throws on any wire failure so callers
     * can degrade gracefully (disable the server and keep the
     * debate running with the remaining tools).
     */
    public void initialize() throws IOException {
        JsonObject params = new JsonObject();
        params.addProperty("protocolVersion", PROTOCOL_VERSION);
        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", "aicollab");
        clientInfo.addProperty("version", "1.0");
        params.add("clientInfo", clientInfo);
        params.add("capabilities", new JsonObject());
        sendRequest("initialize", params);
        // Fire-and-forget notification that we've finished initializing.
        sendNotification("notifications/initialized", new JsonObject());
    }

    /**
     * Returns the server's advertised tools. The JsonArray holds the
     * raw tool descriptors from the MCP server
     * ({name, description, inputSchema}). Caller is responsible for
     * translating each into a ToolSchema.
     */
    public JsonArray listTools() throws IOException {
        JsonElement resp = sendRequest("tools/list", new JsonObject());
        if (resp != null && resp.isJsonObject() && resp.getAsJsonObject().has("tools")) {
            return resp.getAsJsonObject().getAsJsonArray("tools");
        }
        return new JsonArray();
    }

    /**
     * Invokes a tool. Returns the concatenated text content blocks
     * of the response (MCP returns tool output as an array of
     * content blocks; for our purposes we stitch textual blocks
     * into one string).
     */
    public String callTool(String toolName, Map<String, Object> arguments) throws IOException {
        JsonObject params = new JsonObject();
        params.addProperty("name", toolName);
        params.add("arguments", gson.toJsonTree(arguments == null ? Map.of() : arguments));
        JsonElement resp = sendRequest("tools/call", params);
        if (resp == null || !resp.isJsonObject()) return "";
        JsonObject root = resp.getAsJsonObject();
        if (!root.has("content") || !root.get("content").isJsonArray()) {
            return root.has("isError") && root.get("isError").getAsBoolean()
                    ? "[tool error]" : "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonElement el : root.getAsJsonArray("content")) {
            if (!el.isJsonObject()) continue;
            JsonObject block = el.getAsJsonObject();
            if (block.has("type") && "text".equals(block.get("type").getAsString())
                    && block.has("text")) {
                text.append(block.get("text").getAsString());
            }
        }
        return text.toString();
    }

    private JsonElement sendRequest(String method, JsonObject params) throws IOException {
        long id = nextId.getAndIncrement();
        JsonObject req = new JsonObject();
        req.addProperty("jsonrpc", "2.0");
        req.addProperty("id", id);
        req.addProperty("method", method);
        req.add("params", params);
        String frame = gson.toJson(req);

        String respBody = McpServerAttachment.TRANSPORT_HTTP.equals(attachment.transport())
                ? sendHttp(frame)
                : sendStdio(frame);
        if (respBody == null || respBody.isBlank()) return null;
        JsonObject resp = JsonParser.parseString(respBody).getAsJsonObject();
        if (resp.has("error")) {
            throw new IOException("MCP error: " + resp.get("error"));
        }
        return resp.get("result");
    }

    private void sendNotification(String method, JsonObject params) throws IOException {
        JsonObject req = new JsonObject();
        req.addProperty("jsonrpc", "2.0");
        req.addProperty("method", method);
        req.add("params", params);
        String frame = gson.toJson(req);
        if (McpServerAttachment.TRANSPORT_HTTP.equals(attachment.transport())) {
            sendHttp(frame); // HTTP transport: server may reply empty — ignored.
        } else {
            writeStdioFrame(frame);
            // Notifications have no response; do NOT read stdout here.
        }
    }

    private String sendHttp(String frame) throws IOException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(attachment.url()))
                .header("Content-Type", "application/json")
                .timeout(HTTP_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(frame, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("MCP http send interrupted", ie);
        }
    }

    private String sendStdio(String frame) throws IOException {
        writeStdioFrame(frame);
        return stdoutReader.readLine();
    }

    private void writeStdioFrame(String frame) throws IOException {
        stdinWriter.write(frame);
        stdinWriter.newLine();
        stdinWriter.flush();
    }

    @Override
    public void close() {
        try {
            if (stdinWriter != null) stdinWriter.close();
        } catch (IOException ignored) {}
        try {
            if (stdoutReader != null) stdoutReader.close();
        } catch (IOException ignored) {}
        if (process != null) {
            try {
                if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroy();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                process.destroy();
            }
        }
    }

    public String name() {
        return attachment.name();
    }
}
