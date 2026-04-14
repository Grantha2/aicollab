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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class GeminiClient implements LlmClient {

    private final HttpClient httpClient;
    private final String apiKey;
    private final String modelName;
    private final int maxTokens;

    // Retry configuration for transient errors (connection reset,
    // HTTP 408/425/429/499/500/502/503/504). Keeps total wall time
    // bounded: 1s + 2s + 4s + 8s = 15s of backoff across 4 retries.
    private static final int MAX_RETRIES = 4;
    private static final long INITIAL_BACKOFF_MS = 1000L;

    // Per-request timeout so a stalled connection fails fast and
    // enters the retry loop instead of hanging the whole debate.
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                // GEMINI-SPECIFIC: No auth header needed — the key is
                // already in the URL above. This is why we build the URL
                // dynamically instead of storing a fixed apiUrl.
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = sendWithRetry(request);
        if (response == null) {
            return "[Gemini ERROR] Request failed after " + MAX_RETRIES
                    + " retries (connection reset / transient error).";
        }

        if (response.statusCode() != 200) {
            return "[Gemini ERROR " + response.statusCode() + "] " + response.body();
        }

        // Gemini's response JSON structure:
        // { "candidates": [{ "content": { "parts": [{ "text": "answer" }] } }] }
        // We want the "text" field inside parts.
        return extractField(response.body(), "text");
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

        HttpRequest requestObj = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = sendWithRetry(requestObj);
        if (response == null) {
            return "[Gemini ERROR] Request failed after " + MAX_RETRIES
                    + " retries (connection reset / transient error).";
        }

        if (response.statusCode() != 200) {
            return "[Gemini ERROR " + response.statusCode() + "] " + response.body();
        }

        return extractField(response.body(), "text");
    }


    // ============================================================
    // sendStateful() — Stateless delegate.
    //
    // Google's Gemini API does NOT expose a stateful conversation
    // endpoint (no "interactions" API exists at v1beta/interactions —
    // that URL returns errors and burns a round-trip). Stateful
    // conversation with Gemini is emulated client-side by resending
    // the full message history through generateContent, which the
    // Maestro already accumulates in the LlmRequest it passes in.
    //
    // We therefore delegate to the stateless sendMessage(LlmRequest)
    // path (which now has retry + timeout) and return a null state ID
    // so the Maestro keeps passing full history on each turn.
    // ============================================================
    @Override
    public StatefulResponse sendStateful(LlmRequest request, String previousStateId) {
        return new StatefulResponse(sendMessage(request), null);
    }

    // ============================================================
    // sendWithRetry() — Execute an HttpRequest with exponential
    // backoff for transient failures.
    //
    // Retries on:
    //   - IOException / connection reset (no HTTP response at all)
    //   - HTTP 408 Request Timeout
    //   - HTTP 425 Too Early
    //   - HTTP 429 Too Many Requests
    //   - HTTP 499 Client Closed Request (nginx; typically upstream reset)
    //   - HTTP 500/502/503/504 server-side transient errors
    //
    // Does NOT retry on 4xx responses that indicate a client error
    // (bad key, bad request, etc.) — those won't fix themselves.
    //
    // Returns null if every retry is exhausted.
    // ============================================================
    private HttpResponse<String> sendWithRetry(HttpRequest request) {
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (isTransientStatus(response.statusCode())
                        && attempt < MAX_RETRIES) {
                    sleepQuietly(backoffMs);
                    backoffMs *= 2;
                    continue;
                }
                return response;

            } catch (IOException ioe) {
                // Connection reset, socket timeout, etc. — retry.
                if (attempt >= MAX_RETRIES) {
                    return null;
                }
                sleepQuietly(backoffMs);
                backoffMs *= 2;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static boolean isTransientStatus(int status) {
        return status == 408
                || status == 425
                || status == 429
                || status == 499
                || (status >= 500 && status <= 599);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
