package collab;

// ============================================================
// OpenAiClient.java — GPT API calls (implements LlmClient).
//
// WHAT THIS CLASS DOES (one sentence):
// Sends a prompt to OpenAI's GPT API and returns the text response.
//
// HOW IT FITS THE ARCHITECTURE:
// Same role as AnthropicClient — one of three LlmClient implementations.
// Maestro calls sendMessage() without knowing this is GPT.
//
// HOW GPT DIFFERS FROM CLAUDE:
//   - Auth: "Authorization: Bearer ..." header (industry standard OAuth pattern)
//   - Token limit field: "max_completion_tokens" (not "max_tokens" — OpenAI
//     renamed this for newer models like gpt-4o)
//   - Response path: choices[0].message.content
//   - The response JSON contains "content" MULTIPLE TIMES (once in the
//     request echo, once in the actual answer). We use lastIndexOf to
//     grab the last one, which is the answer.
//   - Request JSON is nearly identical to Claude — both use "messages"
//     with "role" and "content". This isn't a coincidence; OpenAI
//     popularized this format and others adopted it.
//
// LEARN BY PATTERN:
// Compare this file to AnthropicClient.java line by line. Same structure,
// small differences. The comments highlight exactly what's different.
// ============================================================

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class OpenAiClient implements LlmClient {

    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;
    private final String modelName;
    private final int maxTokens;

    // ============================================================
    // Constructor — same pattern as AnthropicClient.
    // Stores config, reuses the shared HttpClient.
    // ============================================================
    public OpenAiClient(HttpClient httpClient, String apiUrl,
                        String apiKey, String modelName, int maxTokens) {
        this.httpClient = httpClient;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.maxTokens = maxTokens;
    }

    // ============================================================
    // sendMessage() — Same three-step pattern as AnthropicClient:
    //   1. Build JSON  2. Send HTTP  3. Extract response
    // Differences are highlighted with "OPENAI-SPECIFIC" comments.
    // ============================================================
    @Override
    public String sendMessage(String prompt) {

        String escapedPrompt = escapeForJson(prompt);

        // Almost identical JSON to Claude. "messages" array with "role"
        // and "content" — same pattern, different provider.
        // OPENAI-SPECIFIC: Uses "max_completion_tokens" instead of "max_tokens".
        // OpenAI renamed this field for newer models like gpt-4o.
        String requestBody = """
                {
                    "model": "%s",
                    "max_completion_tokens": %d,
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
                    // OPENAI-SPECIFIC: Uses "Authorization: Bearer ..." header.
                    // This is the industry-standard OAuth pattern, different from
                    // Anthropic's custom "x-api-key" header.
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "[GPT ERROR " + response.statusCode() + "] " + response.body();
            }

            // GPT's response JSON structure:
            // { "choices": [{ "message": { "content": "the actual answer" } }] }
            //
            // OPENAI-SPECIFIC: The word "content" appears MULTIPLE times in the
            // response — once in the request echo section and once in the actual
            // answer. extractField uses lastIndexOf() to grab the LAST occurrence,
            // which is the answer we want.
            return extractField(response.body(), "content");

        } catch (Exception e) {
            return "[GPT ERROR] " + e.getMessage();
        }
    }

    // ============================================================
    // sendMessage(LlmRequest) — Native multi-turn request path.
    //
    // WHY THIS EXISTS:
    // OpenAI expects conversation structure as an ordered messages
    // array. During migration we preserve the old flat path while
    // adding this provider-native path for role-aware multi-turn use.
    // ============================================================
    @Override
    public String sendMessage(LlmRequest request) {

        JsonArray messages = new JsonArray();

        if (request.systemInstruction() != null
                && !request.systemInstruction().isEmpty()) {
            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", request.systemInstruction());
            messages.add(sysMsg);
        }

        for (ChatMessage msg : request.messages()) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            m.addProperty("content", msg.content());
            messages.add(m);
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", modelName);
        body.addProperty("max_completion_tokens", request.maxTokens());
        body.add("messages", messages);

        String requestBody = body.toString();

        try {
            HttpRequest requestObj = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(requestObj,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "[GPT ERROR " + response.statusCode() + "] " + response.body();
            }

            return extractField(response.body(), "content");

        } catch (Exception e) {
            return "[GPT ERROR] " + e.getMessage();
        }
    }


    // ============================================================
    // sendStateful() — OpenAI Responses API (stateful conversation).
    //
    // Uses POST https://api.openai.com/v1/responses with store=true.
    // On first call (previousStateId == null), sends full messages as input.
    // On chained calls, sends previous_response_id + only the new user message.
    // Falls back to stateless sendMessage on any failure.
    // ============================================================
    @Override
    public StatefulResponse sendStateful(LlmRequest request, String previousStateId) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", modelName);
            body.addProperty("store", true);

            if (request.systemInstruction() != null
                    && !request.systemInstruction().isEmpty()) {
                body.addProperty("instructions", request.systemInstruction());
            }

            // Resolve attachments -> file_ids once. On the first turn
            // we splice input_file blocks into the new user message's
            // content array; on chained turns the server retains the
            // file context from the first turn, so we pass text only
            // to keep the request small.
            List<String> attachmentFileIds = previousStateId == null
                    ? resolveAttachmentIds(request.attachments())
                    : List.of();

            if (previousStateId == null) {
                // First call — send full conversation as input array.
                // The LAST user message carries any input_file blocks.
                JsonArray input = new JsonArray();
                int lastUserIdx = lastUserMessageIndex(request.messages());
                for (int i = 0; i < request.messages().size(); i++) {
                    ChatMessage msg = request.messages().get(i);
                    JsonObject m = new JsonObject();
                    m.addProperty("role", msg.role());
                    if (i == lastUserIdx && !attachmentFileIds.isEmpty()) {
                        m.add("content", buildResponsesContentWithFiles(
                                msg.content(), attachmentFileIds));
                    } else {
                        m.addProperty("content", msg.content());
                    }
                    input.add(m);
                }
                body.add("input", input);
            } else {
                // Chained call — server has prior context, just send new input
                body.addProperty("previous_response_id", previousStateId);
                body.addProperty("input", request.messages().getLast().content());
            }

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/responses"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(httpReq,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("HTTP " + response.statusCode()
                        + ": " + response.body());
            }

            // Parse Responses API JSON:
            // { "id": "resp_abc", "output": [{ "type": "message",
            //   "content": [{ "type": "output_text", "text": "..." }] }] }
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            String id = root.get("id").getAsString();
            String text = root.getAsJsonArray("output")
                    .get(0).getAsJsonObject()
                    .getAsJsonArray("content")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();

            return new StatefulResponse(text, id);

        } catch (Exception e) {
            // Fallback to stateless path
            String fallback = sendMessage(request);
            return new StatefulResponse(fallback, null);
        }
    }

    // ============================================================
    // resolveAttachmentIds() — Uploads every FileAttachment (cache-hit
    // after the first call) and collects the OpenAI file_ids. Any
    // future non-File attachment type (RAG / MCP) is ignored here.
    //
    // NOTE ON /v1/chat/completions: The sendMessage(LlmRequest) path
    // above targets chat completions, which does NOT accept
    // input_file blocks — it only supports image_url for non-text.
    // We therefore don't thread attachments into that path; the
    // stateful Responses API path below is where files live.
    // ============================================================
    private List<String> resolveAttachmentIds(List<ContextAttachment> atts) {
        if (atts == null || atts.isEmpty()) return List.of();
        List<String> ids = new ArrayList<>();
        for (ContextAttachment a : atts) {
            if (a instanceof FileAttachment fa) {
                FileUploader.UploadedRef ref = FileUploader.ensureUploaded(
                        Provider.OPENAI, fa, apiKey, httpClient);
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

    // ============================================================
    // buildResponsesContentWithFiles() — Upgrades a single message's
    // content from a plain string to the Responses API's content-array
    // shape:
    //   [{type:"input_text", text:"…"},
    //    {type:"input_file", file_id:"file-…"}, …]
    // file blocks go AFTER the text so the user's prompt reads first;
    // OpenAI's examples use either ordering but this keeps parity with
    // Anthropic's "text-last" convention isn't important — the model
    // reads both regardless.
    // ============================================================
    private static JsonArray buildResponsesContentWithFiles(String text, List<String> fileIds) {
        JsonArray blocks = new JsonArray();
        JsonObject t = new JsonObject();
        t.addProperty("type", "input_text");
        t.addProperty("text", text == null ? "" : text);
        blocks.add(t);
        for (String id : fileIds) {
            JsonObject f = new JsonObject();
            f.addProperty("type", "input_file");
            f.addProperty("file_id", id);
            blocks.add(f);
        }
        return blocks;
    }

    // --- Utility methods (duplicated for self-containment — see AnthropicClient for full comments) ---

    private static String escapeForJson(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

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

        String extracted = json.substring(startIndex, endIndex);
        extracted = extracted.replace("\\n", "\n");
        extracted = extracted.replace("\\t", "\t");
        extracted = extracted.replace("\\\"", "\"");
        extracted = extracted.replace("\\\\", "\\");

        return extracted;
    }
}
