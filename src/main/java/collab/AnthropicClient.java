package collab;

// ============================================================
// AnthropicClient.java — Claude API calls (implements LlmClient).
//
// WHAT THIS CLASS DOES (one sentence):
// Sends a prompt to Anthropic's Claude API and returns the text response.
//
// HOW IT FITS THE ARCHITECTURE:
// This is one of three LlmClient implementations. Maestro calls
// sendMessage() without knowing which provider is behind it. This class
// handles the Anthropic-specific details: URL, headers, JSON format,
// and response parsing.
//
// UNIQUE ANTHROPIC DETAILS:
//   - Auth header: "x-api-key" (not "Authorization: Bearer ...")
//   - Requires an extra header: "anthropic-version: 2023-06-01"
//   - Request JSON uses "messages" array with "role" + "content"
//   - Response path: content[0].text
//
// LEARN BY PATTERN:
// This class follows the exact same structure as OpenAiClient and
// GeminiClient: constructor stores config, sendMessage() builds JSON,
// sends HTTP, extracts the response. Reading one teaches you all three.
// The differences are small and highlighted in comments.
// ============================================================

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;

public class AnthropicClient implements LlmClient {

    // These are set once in the constructor and never change.
    // 'final' means Java won't let you accidentally reassign them.
    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;
    private final String modelName;
    private final int maxTokens;

    // Client-side conversation state for stateful calls.
    // Anthropic's API is stateless, so we accumulate messages here
    // and replay the full conversation on each call.
    private final Map<String, ConversationState> conversations = new ConcurrentHashMap<>();

    private static class ConversationState {
        String systemInstruction;
        final List<ChatMessage> messages = new ArrayList<>();
        // Attachments stashed on the first turn so client-side replay
        // can re-inject them as document blocks on every subsequent
        // turn's outgoing request. Anthropic is stateless on the wire,
        // so "the server remembers the document" is our responsibility.
        final List<ContextAttachment> pinnedAttachments = new ArrayList<>();
    }

    // ============================================================
    // Constructor — stores everything needed to make API calls.
    //
    // PARAMETERS:
    //   httpClient — shared HTTP client (created once in Main, reused
    //                by all three providers to save resources)
    //   apiUrl     — Anthropic's API endpoint
    //   apiKey     — your Anthropic API key (loaded from Config)
    //   modelName  — which Claude model to use (e.g., "claude-sonnet-4-20250514")
    //   maxTokens  — maximum tokens in Claude's response
    // ============================================================
    public AnthropicClient(HttpClient httpClient, String apiUrl,
                           String apiKey, String modelName, int maxTokens) {
        this.httpClient = httpClient;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.maxTokens = maxTokens;
    }

    // ============================================================
    // sendMessage() — Implements the LlmClient interface.
    //
    // Three steps (same pattern as every API client):
    //   1. Build the JSON request body
    //   2. Build and send the HTTP request with provider-specific headers
    //   3. Extract the text from the JSON response
    //
    // If anything goes wrong (network error, bad key, rate limit),
    // returns an error string instead of crashing. This lets the
    // debate continue even if one model fails.
    // ============================================================
    @Override
    public String sendMessage(String prompt) {

        String escapedPrompt = escapeForJson(prompt);

        // Anthropic's JSON format: "messages" array with "role" and "content".
        // This is very similar to OpenAI's format — not a coincidence;
        // OpenAI popularized it and others adopted the pattern.
        String requestBody = """
                {
                    "model": "%s",
                    "max_tokens": %d,
                    "messages": [
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ]
                }
                """.formatted(modelName, maxTokens, escapedPrompt);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    // ANTHROPIC-SPECIFIC: Uses "x-api-key" header (not Bearer token).
                    // This is Anthropic's custom auth scheme.
                    .header("x-api-key", apiKey)
                    // ANTHROPIC-SPECIFIC: Required version header. Without this,
                    // the API rejects the request.
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "[Claude ERROR " + response.statusCode() + "] " + response.body();
            }

            // Claude's response JSON structure:
            // { "content": [{ "type": "text", "text": "the actual answer" }] }
            // We want the value of the "text" field.
            return extractField(response.body(), "text");

        } catch (Exception e) {
            return "[Claude ERROR] " + e.getMessage();
        }
    }

    // ============================================================
    // sendMessage(LlmRequest) — Native multi-turn request path.
    //
    // WHY THIS EXISTS:
    // The migration goal is to send structured role-tagged turns
    // instead of one giant flat prompt string. Anthropic supports
    // top-level "system" plus a "messages" array, so we map the
    // provider-agnostic LlmRequest to that shape here.
    // ============================================================
    @Override
    public String sendMessage(LlmRequest request) {

        JsonObject body = new JsonObject();
        body.addProperty("model", modelName);
        body.addProperty("max_tokens", request.maxTokens());

        if (request.systemInstruction() != null
                && !request.systemInstruction().isEmpty()) {
            body.addProperty("system", request.systemInstruction());
        }

        // Resolve attachments to file_ids up-front. Failed uploads
        // return null and are simply omitted — debate continues
        // without the attachment rather than erroring out.
        List<String> attachmentFileIds = resolveAttachmentIds(request.attachments());

        JsonArray messages = new JsonArray();
        int lastUserIndex = lastUserMessageIndex(request.messages());
        for (int i = 0; i < request.messages().size(); i++) {
            ChatMessage msg = request.messages().get(i);
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            // Only the LAST user message carries the document blocks
            // on the non-stateful path — no replay, single shot.
            if (i == lastUserIndex && !attachmentFileIds.isEmpty()) {
                m.add("content", buildContentWithDocuments(msg.content(), attachmentFileIds));
            } else {
                m.addProperty("content", msg.content());
            }
            messages.add(m);
        }
        body.add("messages", messages);

        String requestBody = body.toString();

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    // files-api-2025-04-14 enables the document/file
                    // content block. Harmless to include when no
                    // attachments are present — the API accepts but
                    // doesn't require it in that case.
                    .header("anthropic-beta", "files-api-2025-04-14")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "[Claude ERROR " + response.statusCode() + "] " + response.body();
            }

            return extractField(response.body(), "text");

        } catch (Exception e) {
            return "[Claude ERROR] " + e.getMessage();
        }
    }


    // ============================================================
    // sendStateful() — Client-side stateful conversation for Claude.
    //
    // WHY THIS EXISTS:
    // GPT and Gemini have server-side state (Responses API / Interactions API).
    // Anthropic's API is stateless — every request must include the full
    // message history. This method simulates statefulness by accumulating
    // messages client-side and replaying them on each call, returning a
    // state ID that maps to the stored conversation.
    // ============================================================
    @Override
    public StatefulResponse sendStateful(LlmRequest request, String previousStateId) {
        ConversationState state;

        if (previousStateId != null && conversations.containsKey(previousStateId)) {
            state = conversations.get(previousStateId);
        } else {
            state = new ConversationState();
        }

        // Set or update system instruction if provided
        if (request.systemInstruction() != null && !request.systemInstruction().isEmpty()) {
            state.systemInstruction = request.systemInstruction();
        }

        // Pin any newly-supplied attachments to the conversation
        // state so future turns' replayed history still sees the
        // document blocks. Only the first call typically supplies
        // attachments; later calls pass an empty list.
        if (request.attachments() != null && !request.attachments().isEmpty()
                && state.pinnedAttachments.isEmpty()) {
            state.pinnedAttachments.addAll(request.attachments());
        }

        // Add new messages to history
        for (ChatMessage msg : request.messages()) {
            state.messages.add(msg);
        }

        // Resolve pinned attachments to Anthropic file_ids (hits the
        // FileUploader cache on every turn after the first, so this
        // costs one HTTP round-trip per unique file per app lifetime).
        List<String> attachmentFileIds = resolveAttachmentIds(state.pinnedAttachments);

        // Build the full request with accumulated conversation
        JsonObject body = new JsonObject();
        body.addProperty("model", modelName);
        body.addProperty("max_tokens", request.maxTokens());

        if (state.systemInstruction != null && !state.systemInstruction.isEmpty()) {
            body.addProperty("system", state.systemInstruction);
        }

        JsonArray messages = new JsonArray();
        // Attach document blocks to the FIRST user message of the
        // replayed history. That way the model reads them before its
        // first response is conceptually "constructed", and later
        // turns don't carry duplicated document blocks.
        int firstUserIndex = firstUserMessageIndex(state.messages);
        for (int i = 0; i < state.messages.size(); i++) {
            ChatMessage msg = state.messages.get(i);
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            if (i == firstUserIndex && !attachmentFileIds.isEmpty()) {
                m.add("content", buildContentWithDocuments(msg.content(), attachmentFileIds));
            } else {
                m.addProperty("content", msg.content());
            }
            messages.add(m);
        }
        body.add("messages", messages);

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("anthropic-beta", "files-api-2025-04-14")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return new StatefulResponse(
                        "[Claude ERROR " + response.statusCode() + "] " + response.body(), null);
            }

            String text = extractField(response.body(), "text");

            // Add assistant response to conversation history
            state.messages.add(new ChatMessage("assistant", text));

            // Store state and return new ID
            String newStateId = UUID.randomUUID().toString();
            conversations.put(newStateId, state);

            // Clean up old state ID to prevent memory leak
            if (previousStateId != null) {
                conversations.remove(previousStateId);
            }

            return new StatefulResponse(text, newStateId);

        } catch (Exception e) {
            return new StatefulResponse("[Claude ERROR] " + e.getMessage(), null);
        }
    }

    // ============================================================
    // resolveAttachmentIds() — Uploads every FileAttachment (cache-hits
    // after the first call) and collects the returned file_ids. Any
    // attachment type we don't yet understand (future RagCorpus /
    // McpServer types) is silently skipped — the sealed interface's
    // other permits don't exist yet, but this switch is ready for
    // them. Null/empty inputs return an empty list.
    // ============================================================
    private List<String> resolveAttachmentIds(List<ContextAttachment> atts) {
        if (atts == null || atts.isEmpty()) return List.of();
        List<String> ids = new ArrayList<>();
        for (ContextAttachment a : atts) {
            if (a instanceof FileAttachment fa) {
                FileUploader.UploadedRef ref = FileUploader.ensureUploaded(
                        Provider.ANTHROPIC, fa, apiKey, httpClient);
                if (ref != null && ref.ref() != null) {
                    ids.add(ref.ref());
                }
            }
        }
        return ids;
    }

    private static int lastUserMessageIndex(List<ChatMessage> msgs) {
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if ("user".equals(msgs.get(i).role())) return i;
        }
        return -1;
    }

    private static int firstUserMessageIndex(List<ChatMessage> msgs) {
        for (int i = 0; i < msgs.size(); i++) {
            if ("user".equals(msgs.get(i).role())) return i;
        }
        return -1;
    }

    // ============================================================
    // buildContentWithDocuments() — Upgrades a single user-message
    // content from a plain string to an array of content blocks:
    //   [{type:"document", source:{type:"file", file_id:"…"}}, ...,
    //    {type:"text", text:"…"}]
    // Claude's content array accepts any mix of block types; text
    // goes last by convention so the user's prompt reads as the
    // final instruction after the documents.
    // ============================================================
    private static JsonArray buildContentWithDocuments(String text, List<String> fileIds) {
        JsonArray blocks = new JsonArray();
        for (String id : fileIds) {
            JsonObject doc = new JsonObject();
            doc.addProperty("type", "document");
            JsonObject src = new JsonObject();
            src.addProperty("type", "file");
            src.addProperty("file_id", id);
            doc.add("source", src);
            blocks.add(doc);
        }
        JsonObject t = new JsonObject();
        t.addProperty("type", "text");
        t.addProperty("text", text == null ? "" : text);
        blocks.add(t);
        return blocks;
    }

    // ============================================================
    // escapeForJson() — Makes user input safe to embed in JSON.
    //
    // WHY THIS IS NECESSARY:
    // JSON uses double quotes to wrap strings. If the user types:
    //   He said "hello"
    // And we naively insert that into JSON, it becomes:
    //   { "content": "He said "hello"" }
    //                          ^ JSON breaks here
    //
    // ORDER MATTERS: We escape backslashes FIRST because the other
    // replacements ADD backslashes. If we did backslashes last,
    // we'd double-escape the ones we just added.
    //
    // NOTE: This method is duplicated in all three client classes.
    // That's intentional — it keeps each client self-contained and
    // readable without jumping between files. We can extract a
    // shared JsonUtil.java later when the team is comfortable.
    // ============================================================
    private static String escapeForJson(String input) {
        return input
                .replace("\\", "\\\\")   // backslash -> \\  (must be first!)
                .replace("\"", "\\\"")   // double quote -> \"
                .replace("\n", "\\n")    // newline -> \n
                .replace("\r", "\\r")    // carriage return -> \r
                .replace("\t", "\\t");   // tab -> \t
    }


    // ============================================================
    // extractField() — Pulls a specific value from JSON by field name.
    //
    // HOW IT WORKS:
    //   1. Search for the pattern  "fieldName": "
    //   2. Everything after that opening quote until the next
    //      unescaped quote is our value.
    //
    // WHY lastIndexOf:
    // Some APIs include the field name multiple times in the response.
    // The LAST occurrence is typically the actual answer. So we
    // search from the end of the string backward.
    //
    // LIMITATION: Won't work for nested objects or arrays. Fine for
    // now — all three APIs put the answer in a simple string field.
    // When we add Gson later, this goes away.
    //
    // NOTE: Duplicated in all three clients. See escapeForJson note.
    // ============================================================
    private static String extractField(String json, String fieldName) {

        String marker = "\"" + fieldName + "\": \"";
        String markerAlt = "\"" + fieldName + "\":\"";

        int startIndex = json.lastIndexOf(marker);
        int markerLength = marker.length();

        if (startIndex == -1) {
            startIndex = json.lastIndexOf(markerAlt);
            markerLength = markerAlt.length();
        }

        if (startIndex == -1) {
            return "[PARSE ERROR] Could not find field '" + fieldName
                    + "' in response.\nRaw: " + json;
        }

        startIndex += markerLength;

        // Find the closing quote, handling escaped quotes.
        // A quote preceded by an odd number of backslashes is escaped
        // (part of the value). An even number means it's the real end.
        int endIndex = startIndex;
        while (endIndex < json.length()) {
            char current = json.charAt(endIndex);

            if (current == '"') {
                int backslashCount = 0;
                int checkIndex = endIndex - 1;
                while (checkIndex >= startIndex && json.charAt(checkIndex) == '\\') {
                    backslashCount++;
                    checkIndex--;
                }
                if (backslashCount % 2 == 0) {
                    break;
                }
            }
            endIndex++;
        }

        // Extract and unescape the JSON string
        String extracted = json.substring(startIndex, endIndex);
        extracted = extracted.replace("\\n", "\n");
        extracted = extracted.replace("\\t", "\t");
        extracted = extracted.replace("\\\"", "\"");
        extracted = extracted.replace("\\\\", "\\");

        return extracted;
    }

    // ============================================================
    // sendStateful() WITH TOOL EXECUTOR — the tool-use loop.
    //
    // WHAT THIS METHOD DOES (one sentence):
    // Runs a Claude conversation turn with tools available, looping
    // until Claude produces a final text response or the iteration
    // cap (ToolExecutor.DEFAULT_MAX_ITERATIONS) is exceeded.
    //
    // WHY A SEPARATE METHOD (instead of extending sendStateful):
    // The tool loop adds ~150 lines of parsing and re-entry logic
    // that are dead weight for non-tool debates. Keeping the original
    // sendStateful intact means the common case (no tools) runs the
    // exact same code path it ran before tool-calling landed.
    //
    // CLAUDE'S TOOL-USE PROTOCOL (what we handle here):
    //   Request: body.tools = [{name, description, input_schema}]
    //   Response: content can mix text blocks and tool_use blocks.
    //     - { "type": "text", "text": "…" }
    //     - { "type": "tool_use", "id": "tu_…", "name": "…", "input": {…} }
    //   If stop_reason == "tool_use", we must append a user message
    //   with one tool_result block per tool_use id (same id echoed
    //   back), then request the next turn. Claude continues from
    //   where it left off.
    //
    // FALLBACK: If executor is null or request.tools() is empty, we
    // delegate to the non-tool sendStateful so the plain debate path
    // is untouched.
    // ============================================================
    @Override
    public StatefulResponse sendStateful(LlmRequest request,
                                         String previousStateId,
                                         ToolExecutor executor) {
        if (executor == null || request.tools() == null || request.tools().isEmpty()) {
            return sendStateful(request, previousStateId);
        }

        // Resolve or create conversation state — identical bookkeeping
        // to the non-tool sendStateful, duplicated intentionally so the
        // two paths can diverge without risk of breaking each other.
        ConversationState state;
        if (previousStateId != null && conversations.containsKey(previousStateId)) {
            state = conversations.get(previousStateId);
        } else {
            state = new ConversationState();
        }
        if (request.systemInstruction() != null && !request.systemInstruction().isEmpty()) {
            state.systemInstruction = request.systemInstruction();
        }
        if (request.attachments() != null && !request.attachments().isEmpty()
                && state.pinnedAttachments.isEmpty()) {
            state.pinnedAttachments.addAll(request.attachments());
        }
        for (ChatMessage msg : request.messages()) {
            state.messages.add(msg);
        }

        // `assistantTurns` collects assistant content blocks (text +
        // tool_use) across loop iterations so each successive request
        // replays them correctly. Because ChatMessage.content() is a
        // plain string, we keep the richer structure as JsonArrays
        // here and splice them directly into the request body instead
        // of round-tripping through ChatMessage on tool-use turns.
        List<JsonArray> assistantTurns = new ArrayList<>();
        List<JsonArray> toolResultTurns = new ArrayList<>();

        List<String> attachmentFileIds = resolveAttachmentIds(state.pinnedAttachments);

        String finalText = "";
        int iteration = 0;
        try {
            while (iteration < ToolExecutor.DEFAULT_MAX_ITERATIONS) {
                iteration++;

                // Build the request with accumulated state + tools.
                JsonObject body = new JsonObject();
                body.addProperty("model", modelName);
                body.addProperty("max_tokens", request.maxTokens());
                if (state.systemInstruction != null && !state.systemInstruction.isEmpty()) {
                    body.addProperty("system", state.systemInstruction);
                }
                body.add("tools", serializeTools(request.tools()));

                JsonArray messages = new JsonArray();
                int firstUserIndex = firstUserMessageIndex(state.messages);
                // Replay the same text messages as the non-tool path.
                // Any assistant tool_use + user tool_result pairs from
                // earlier iterations are spliced in after the initial
                // user message.
                int assistantIdx = 0;
                int toolResultIdx = 0;
                for (int i = 0; i < state.messages.size(); i++) {
                    ChatMessage msg = state.messages.get(i);
                    JsonObject m = new JsonObject();
                    m.addProperty("role", msg.role());
                    if (i == firstUserIndex && !attachmentFileIds.isEmpty()) {
                        m.add("content", buildContentWithDocuments(msg.content(), attachmentFileIds));
                    } else {
                        m.addProperty("content", msg.content());
                    }
                    messages.add(m);
                }
                // Splice in past assistant/user tool pairs.
                while (assistantIdx < assistantTurns.size()
                        && toolResultIdx < toolResultTurns.size()) {
                    JsonObject a = new JsonObject();
                    a.addProperty("role", "assistant");
                    a.add("content", assistantTurns.get(assistantIdx++));
                    messages.add(a);
                    JsonObject u = new JsonObject();
                    u.addProperty("role", "user");
                    u.add("content", toolResultTurns.get(toolResultIdx++));
                    messages.add(u);
                }
                // If the last assistant turn had tool_use without a
                // matching tool_result yet (shouldn't happen post-loop,
                // but guard anyway), it is spliced without a follower.
                while (assistantIdx < assistantTurns.size()) {
                    JsonObject a = new JsonObject();
                    a.addProperty("role", "assistant");
                    a.add("content", assistantTurns.get(assistantIdx++));
                    messages.add(a);
                }
                body.add("messages", messages);

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .header("anthropic-beta", "files-api-2025-04-14")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
                HttpResponse<String> response = httpClient.send(httpRequest,
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return new StatefulResponse(
                            "[Claude ERROR " + response.statusCode() + "] " + response.body(), null);
                }

                JsonObject parsed = JsonParser.parseString(response.body()).getAsJsonObject();
                String stopReason = parsed.has("stop_reason")
                        ? parsed.get("stop_reason").getAsString() : "";
                JsonArray content = parsed.getAsJsonArray("content");

                // Stash the assistant content as-is so next turn can replay it.
                assistantTurns.add(content);

                // Extract text output (may span multiple text blocks).
                StringBuilder textBuf = new StringBuilder();
                List<ToolCall> calls = new ArrayList<>();
                for (JsonElement el : content) {
                    if (!el.isJsonObject()) continue;
                    JsonObject block = el.getAsJsonObject();
                    String type = block.has("type") ? block.get("type").getAsString() : "";
                    if ("text".equals(type) && block.has("text")) {
                        textBuf.append(block.get("text").getAsString());
                    } else if ("tool_use".equals(type)) {
                        String id = block.has("id") ? block.get("id").getAsString() : "";
                        String name = block.has("name") ? block.get("name").getAsString() : "";
                        Map<String, Object> args = block.has("input") && block.get("input").isJsonObject()
                                ? jsonObjToMap(block.getAsJsonObject("input"))
                                : Map.of();
                        calls.add(new ToolCall(id, name, args));
                    }
                }

                if (!"tool_use".equals(stopReason) || calls.isEmpty()) {
                    finalText = textBuf.toString();
                    // End of loop — persist final assistant text as a
                    // ChatMessage so subsequent sendStateful calls that
                    // do NOT use tools can still replay history.
                    state.messages.add(new ChatMessage("assistant", finalText));
                    // Drop the tool-use turns we already spliced; they
                    // are only needed while the tool loop is active.
                    break;
                }

                // Run the tools and build a matching tool_result content
                // array for the next turn's user message.
                List<ToolResult> results = executor.executeAll(calls);
                JsonArray resultArray = new JsonArray();
                for (ToolResult r : results) {
                    JsonObject rb = new JsonObject();
                    rb.addProperty("type", "tool_result");
                    rb.addProperty("tool_use_id", r.callId());
                    rb.addProperty("content", r.content());
                    if (r.isError()) rb.addProperty("is_error", true);
                    resultArray.add(rb);
                }
                toolResultTurns.add(resultArray);
            }

            if (iteration >= ToolExecutor.DEFAULT_MAX_ITERATIONS && finalText.isEmpty()) {
                finalText = "[max tool calls exceeded after "
                        + ToolExecutor.DEFAULT_MAX_ITERATIONS + " iterations]";
                state.messages.add(new ChatMessage("assistant", finalText));
            }

            String newStateId = UUID.randomUUID().toString();
            conversations.put(newStateId, state);
            if (previousStateId != null) conversations.remove(previousStateId);
            return new StatefulResponse(finalText, newStateId);

        } catch (Exception e) {
            return new StatefulResponse("[Claude ERROR] " + e.getMessage(), null);
        }
    }

    // ============================================================
    // serializeTools() — ToolSchema list -> Anthropic tools array.
    //
    // Anthropic expects:
    //   [ { "name": "...", "description": "...", "input_schema": {...} } ]
    // The ToolSchema's parameterSchema Map is handed straight to Gson
    // via toJsonTree so nested objects / arrays serialise faithfully.
    // ============================================================
    private static JsonArray serializeTools(List<ToolSchema> tools) {
        Gson gson = new Gson();
        JsonArray out = new JsonArray();
        for (ToolSchema t : tools) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", t.name());
            obj.addProperty("description", t.description());
            obj.add("input_schema", gson.toJsonTree(t.parameterSchema()));
            out.add(obj);
        }
        return out;
    }

    // Recursive JsonObject -> Map conversion so ToolCall.arguments()
    // gives handlers plain-Java data without a Gson dependency on the
    // handler side. Mirrors McpHost.jsonObjectToMap — kept local here
    // so this file can be explained without cross-file jumps.
    private static Map<String, Object> jsonObjToMap(JsonObject obj) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            out.put(e.getKey(), jsonElemToJava(e.getValue()));
        }
        return out;
    }

    private static Object jsonElemToJava(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) {
            var p = el.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsNumber();
            return p.getAsString();
        }
        if (el.isJsonArray()) {
            List<Object> arr = new ArrayList<>();
            for (JsonElement x : el.getAsJsonArray()) arr.add(jsonElemToJava(x));
            return arr;
        }
        if (el.isJsonObject()) return jsonObjToMap(el.getAsJsonObject());
        return null;
    }
}
