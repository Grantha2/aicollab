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
//   - Auth: API key goes IN THE URL as a query parameter, not in a header
//     (for the legacy generateContent path). The newer Interactions API
//     used by sendStateful() uses an "x-goog-api-key" header instead —
//     so this file uses BOTH auth styles, one per endpoint.
//   - Request JSON uses "contents" and "parts" (not "messages") for
//     generateContent; the Interactions API uses "input" with "role"/
//     "content" objects instead. Two different dialects in one file.
//   - Token limit: "maxOutputTokens" inside a "generationConfig" block
//     for generateContent, "max_output_tokens" inside "generation_config"
//     for Interactions — snake_case vs camelCase, yes, really.
//   - Response path (generateContent): candidates[0].content.parts[0].text
//   - Response path (Interactions):    outputs[last-with-type=text].text
//   - The generateContent URL includes the model name; the Interactions
//     URL does not (model goes in the body).
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
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

    // Cache of system instructions keyed by Interactions-API stateId.
    //
    // WHY: The Interactions API treats `system_instruction`,
    // `generation_config`, and `tools` as INTERACTION-SCOPED — they are
    // NOT carried forward by `previous_interaction_id`. Our Maestro sets
    // systemInstruction=null on Phase 2+ chained turns (relying on the
    // provider to retain it, which OpenAI/Anthropic do). Gemini does not.
    // We therefore cache the first-turn system instruction here and
    // re-submit it on every chained call. The stateId used as the key is
    // the one returned from the PREVIOUS turn; we move the entry forward
    // to the new stateId on each successful chain and remove the old key
    // to bound memory — mirrors AnthropicClient's conversation map.
    private final ConcurrentHashMap<String, String> systemInstructionByStateId
            = new ConcurrentHashMap<>();

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
    // sendStateful() — Server-side state via the Gemini Interactions
    // API (BETA: v1beta/interactions).
    //
    // WHY THIS EXISTS:
    // The Maestro passes `systemInstruction=null` and only the new
    // user turn on Phase 2+ requests, relying on the provider to
    // carry conversation history. OpenAI does that via
    // `previous_response_id`; Anthropic fakes it with a client-side
    // message accumulator. Gemini's generateContent is stateless —
    // so without this method, every Gemini Phase 2+ turn would see
    // only the fresh user message, losing Phase 1 context and the
    // agent-identity system instruction entirely.
    //
    // The Interactions API gives us server-side chaining via
    // `previous_interaction_id`. Per Google's docs, that ID carries
    // ONLY the conversation history; `system_instruction` and
    // `generation_config` are interaction-scoped and must be
    // re-submitted every turn. We therefore cache the first-turn
    // system instruction in `systemInstructionByStateId` and
    // re-attach it on every chained call.
    //
    // WIRE SHAPE:
    //   POST https://generativelanguage.googleapis.com/v1beta/interactions
    //   Header: x-goog-api-key: <KEY>
    //   Body (first turn):
    //     { "model":"…", "system_instruction":"…",
    //       "input":[{"role":"user","content":"…"}, …],
    //       "generation_config":{"max_output_tokens":N},
    //       "store":true }
    //   Body (chained turn):
    //     { "model":"…", "system_instruction":"…",
    //       "previous_interaction_id":"…",
    //       "input":"…new user turn…",
    //       "generation_config":{"max_output_tokens":N},
    //       "store":true }
    //   Response: { "id":"…", "outputs":[{"type":"text","text":"…"}, …] }
    //
    // BETA CAVEAT:
    // Google explicitly recommends generateContent for production
    // workloads — Interactions schemas may change. On any non-200
    // response or exception we fall back to the stateless
    // sendMessage(LlmRequest) path and return a null stateId, which
    // Maestro already tolerates (it just means "no chaining available
    // for the next turn"), so behaviour silently reverts to today's
    // baseline rather than breaking the debate.
    // ============================================================
    @Override
    public StatefulResponse sendStateful(LlmRequest request, String previousStateId) {
        try {
            // Resolve the system instruction for this turn:
            //  - First turn: take from the request (may be null/empty).
            //  - Chained turn: use the one we cached when the previous
            //    interaction was created. If the cache miss (first-ever
            //    chained turn after a restart, etc.), the request's own
            //    systemInstruction is the next best thing.
            String systemInstr;
            if (previousStateId == null) {
                systemInstr = request.systemInstruction();
            } else {
                String cached = systemInstructionByStateId.get(previousStateId);
                systemInstr = cached != null ? cached : request.systemInstruction();
            }

            JsonObject body = new JsonObject();
            body.addProperty("model", modelName);
            body.addProperty("store", true);

            if (systemInstr != null && !systemInstr.isEmpty()) {
                // REST examples in the Interactions docs pass
                // system_instruction as a plain string; the API accepts
                // that form directly.
                body.addProperty("system_instruction", systemInstr);
            }

            if (previousStateId == null) {
                // First turn — send the conversation as an input array
                // of role+content objects. Map "assistant" → "model"
                // the same way the stateless sendMessage(LlmRequest)
                // path already does.
                JsonArray input = new JsonArray();
                for (ChatMessage msg : request.messages()) {
                    JsonObject entry = new JsonObject();
                    String role = "assistant".equals(msg.role()) ? "model" : msg.role();
                    entry.addProperty("role", role);
                    entry.addProperty("content", msg.content());
                    input.add(entry);
                }
                body.add("input", input);
            } else {
                // Chained turn — server has prior context. Send only
                // the latest user message as a plain string plus the
                // previous interaction id.
                body.addProperty("previous_interaction_id", previousStateId);
                body.addProperty("input", request.messages().getLast().content());
            }

            JsonObject genConfig = new JsonObject();
            genConfig.addProperty("max_output_tokens", request.maxTokens());
            body.add("generation_config", genConfig);

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/interactions"))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = sendWithRetry(httpReq);
            if (response == null || response.statusCode() != 200) {
                // Beta endpoint flaked — fall back so the debate keeps
                // going. Null stateId means Maestro will resend full
                // state next turn (which it won't actually do today
                // for Gemini, but that matches the pre-migration
                // behaviour exactly).
                return new StatefulResponse(sendMessage(request), null);
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            String newStateId = root.has("id") && !root.get("id").isJsonNull()
                    ? root.get("id").getAsString()
                    : null;

            // Extract text: outputs[] may include thought blocks or
            // tool-call blocks — pick the LAST block with type=="text",
            // matching the pattern shown in Google's own examples
            // (`outputs[-1].text`) when only text is expected.
            String text = null;
            if (root.has("outputs") && root.get("outputs").isJsonArray()) {
                JsonArray outputs = root.getAsJsonArray("outputs");
                for (int i = outputs.size() - 1; i >= 0; i--) {
                    JsonObject out = outputs.get(i).getAsJsonObject();
                    String type = out.has("type") ? out.get("type").getAsString() : null;
                    if ("text".equals(type) && out.has("text")) {
                        text = out.get("text").getAsString();
                        break;
                    }
                }
            }
            if (text == null) {
                // Couldn't find a text block — treat as a soft failure
                // and fall back rather than returning empty.
                return new StatefulResponse(sendMessage(request), null);
            }

            // Move the cached system instruction forward so the next
            // chained turn can re-submit it, and drop the old key to
            // bound memory (same lifecycle AnthropicClient uses).
            if (newStateId != null && systemInstr != null && !systemInstr.isEmpty()) {
                systemInstructionByStateId.put(newStateId, systemInstr);
            }
            if (previousStateId != null) {
                systemInstructionByStateId.remove(previousStateId);
            }

            return new StatefulResponse(text, newStateId);

        } catch (Exception e) {
            // Any parse error, malformed JSON, or unexpected shape —
            // degrade gracefully to the stateless path.
            return new StatefulResponse(sendMessage(request), null);
        }
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
