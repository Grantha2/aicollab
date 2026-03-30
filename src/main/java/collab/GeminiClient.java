package collab;

// ============================================================
// GeminiClient.java — Google Gemini API calls (implements LlmClient).
//
// WHAT THIS CLASS DOES (one sentence):
// Sends a prompt to Google's Gemini API and returns the text response.
//
// HOW IT FITS THE ARCHITECTURE:
// Same role as AnthropicClient and OpenAiClient — one of three
// LlmClient implementations. Orchestrator doesn't know or care
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
