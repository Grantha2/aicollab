package collab;

// ============================================================
// GeminiClient.java — Google Gemini API calls (implements LlmClient).
//
// WHAT THIS CLASS DOES (one sentence):
// Sends a prompt to Google's Gemini API and returns the text response.
//
// HOW IT FITS THE ARCHITECTURE:
// Same role as AnthropicClient and OpenAiClient — one of three
// LlmClient implementations. Maestro doesn't know or care
// that this one talks to Google.
//
// HOW GEMINI DIFFERS FROM CLAUDE AND GPT:
//   - Auth: API key goes IN THE URL as a query parameter, not in a header!
//     This is an older authentication pattern. Google is the odd one out.
//   - Request JSON uses "contents" and "parts" (not "messages").
//     Every API has its own dialect — this is normal.
//   - Token limit: "maxOutputTokens" inside a "generationConfig" block
//     (not a top-level field like the other two)
//   - Response path: candidates[0].content.parts[0].text
//   - The URL includes the model name (not just a body parameter)
//
// LEARN BY PATTERN:
// Same three-step structure as the other clients. Compare the JSON
// bodies side by side to see how each provider invented its own format
// for what's essentially the same operation: "take this text, respond."
// ============================================================

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GeminiClient implements LlmClient {

    private final HttpClient httpClient;
    private final String apiKey;
    private final String modelName;
    private final int maxTokens;

    // NOTE: No apiUrl field — Gemini's URL is built dynamically because
    // the model name and API key are embedded in the URL itself.

    // ============================================================
    // Constructor — same pattern, but no URL parameter.
    // The URL is constructed in sendMessage() because it includes
    // the model name and API key as URL components.
    // ============================================================
    public GeminiClient(HttpClient httpClient, String apiKey,
                        String modelName, int maxTokens) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.maxTokens = maxTokens;
    }

    // ============================================================
    // sendMessage() — Same three-step pattern, Gemini-specific details.
    // ============================================================
    @Override
    public String sendMessage(String prompt) {

        String escapedPrompt = escapeForJson(prompt);

        // GEMINI-SPECIFIC: The URL includes the model name AND API key.
        // Claude and GPT put the key in headers; Gemini puts it in the URL.
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + modelName + ":generateContent?key=" + apiKey;

        // GEMINI-SPECIFIC: Uses "contents" with "parts" containing "text".
        // Claude and GPT use "messages" with "role" and "content".
        // Every API has its own dialect — this is normal.
        //
        // GEMINI-SPECIFIC: Token limit goes inside a "generationConfig" block
        // as "maxOutputTokens" — not a top-level field like the other two.
        String requestBody = """
                {
                    "contents": [
                        {
                            "parts": [
                                {
                                    "text": "%s"
                                }
                            ]
                        }
                    ],
                    "generationConfig": {
                        "maxOutputTokens": %d
                    }
                }
                """.formatted(escapedPrompt, maxTokens);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    // GEMINI-SPECIFIC: No auth header needed — the key is
                    // already in the URL above. This is why we build the URL
                    // dynamically instead of storing a fixed apiUrl.
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "[Gemini ERROR " + response.statusCode() + "] " + response.body();
            }

            // Gemini's response JSON structure:
            // { "candidates": [{ "content": { "parts": [{ "text": "answer" }] } }] }
            // We want the "text" field inside parts.
            return extractField(response.body(), "text");

        } catch (Exception e) {
            return "[Gemini ERROR] " + e.getMessage();
        }
    }

    // ============================================================
    // sendMessage(LlmRequest) — Native multi-turn request path.
    //
    // WHY THIS EXISTS:
    // Gemini uses a different JSON dialect ("contents"/"parts"),
    // so this method maps app-level ChatMessage roles and content
    // into Google's provider-specific format without changing the
    // legacy flat-string path.
    // ============================================================
    @Override
    public String sendMessage(LlmRequest request) {

        JsonObject body = new JsonObject();

        if (request.systemInstruction() != null
                && !request.systemInstruction().isEmpty()) {
            JsonObject sysInstr = new JsonObject();
            JsonArray sysParts = new JsonArray();
            JsonObject sysPart = new JsonObject();
            sysPart.addProperty("text", request.systemInstruction());
            sysParts.add(sysPart);
            sysInstr.add("parts", sysParts);
            body.add("system_instruction", sysInstr);
        }

        JsonArray contents = new JsonArray();
        for (ChatMessage msg : request.messages()) {
            JsonObject entry = new JsonObject();
            String geminiRole = msg.role().equals("assistant") ? "model" : msg.role();
            entry.addProperty("role", geminiRole);

            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", msg.content());
            parts.add(part);
            entry.add("parts", parts);

            contents.add(entry);
        }
        body.add("contents", contents);

        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("maxOutputTokens", request.maxTokens());
        body.add("generationConfig", genConfig);

        String requestBody = body.toString();

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + modelName + ":generateContent?key=" + apiKey;

        try {
            HttpRequest requestObj = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(requestObj,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "[Gemini ERROR " + response.statusCode() + "] " + response.body();
            }

            return extractField(response.body(), "text");

        } catch (Exception e) {
            return "[Gemini ERROR] " + e.getMessage();
        }
    }


    // ============================================================
    // sendStateful() — Gemini Interactions API (stateful conversation).
    //
    // Uses POST https://generativelanguage.googleapis.com/v1beta/interactions
    // with x-goog-api-key header (not key-in-URL like generateContent).
    // On first call, sends input text. On chained calls, also sends
    // previous_interaction_id. Falls back to stateless on any failure.
    // ============================================================
    @Override
    public StatefulResponse sendStateful(LlmRequest request, String previousStateId) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", modelName);

            if (request.systemInstruction() != null
                    && !request.systemInstruction().isEmpty()) {
                body.addProperty("system_instruction", request.systemInstruction());
            }

            // Always send the latest user message as input
            body.addProperty("input", request.messages().getLast().content());

            if (previousStateId != null) {
                body.addProperty("previous_interaction_id", previousStateId);
            }

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/interactions"))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(httpReq,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("HTTP " + response.statusCode()
                        + ": " + response.body());
            }

            // Parse Interactions API JSON:
            // { "id": "interaction_xyz", "outputs": [{ "type": "message",
            //   "content": [{ "type": "text", "text": "..." }] }] }
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            String id = root.get("id").getAsString();
            JsonArray outputs = root.getAsJsonArray("outputs");
            JsonObject lastOutput = outputs.get(outputs.size() - 1).getAsJsonObject();
            JsonArray content = lastOutput.getAsJsonArray("content");
            String text = content.get(content.size() - 1).getAsJsonObject()
                    .get("text").getAsString();

            return new StatefulResponse(text, id);

        } catch (Exception e) {
            // Fallback to stateless path
            String fallback = sendMessage(request);
            return new StatefulResponse(fallback, null);
        }
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
