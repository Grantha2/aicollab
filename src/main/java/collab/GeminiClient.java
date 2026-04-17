package collab;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class GeminiClient implements LlmClient {

    private final HttpClient httpClient;
    private final String apiKey;
    private final String modelName;
    private final int maxTokens;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    public GeminiClient(HttpClient httpClient, String apiKey,
                        String modelName, int maxTokens) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.maxTokens = maxTokens;
    }

    @Override
    public String send(String systemInstruction, List<ChatMessage> messages) {
        JsonObject body = new JsonObject();

        if (systemInstruction != null && !systemInstruction.isEmpty()) {
            JsonObject sysInstr = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", systemInstruction);
            parts.add(part);
            sysInstr.add("parts", parts);
            body.add("system_instruction", sysInstr);
        }

        JsonArray contents = new JsonArray();
        for (ChatMessage msg : messages) {
            JsonObject entry = new JsonObject();
            entry.addProperty("role", "assistant".equals(msg.role()) ? "model" : msg.role());
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", msg.content());
            parts.add(part);
            entry.add("parts", parts);
            contents.add(entry);
        }
        body.add("contents", contents);

        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("maxOutputTokens", maxTokens);
        body.add("generationConfig", genConfig);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + modelName + ":generateContent?key=" + apiKey;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> resp = sendWithRetry(req);
        if (resp == null) {
            return "[Gemini ERROR] Request failed after retries.";
        }
        if (resp.statusCode() != 200) {
            return "[Gemini ERROR " + resp.statusCode() + "] " + resp.body();
        }

        try {
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) return "";
            JsonObject candidate = candidates.get(0).getAsJsonObject();
            JsonArray parts = candidate.getAsJsonObject("content").getAsJsonArray("parts");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                JsonObject part = parts.get(i).getAsJsonObject();
                if (part.has("text")) sb.append(part.get("text").getAsString());
            }
            return sb.toString();
        } catch (Exception e) {
            return "[Gemini PARSE ERROR] " + e.getMessage() + "\nRaw: " + resp.body();
        }
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) {
        long backoffMs = INITIAL_BACKOFF_MS;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (isTransientStatus(resp.statusCode()) && attempt < MAX_RETRIES) {
                    sleepQuietly(backoffMs);
                    backoffMs *= 2;
                    continue;
                }
                return resp;
            } catch (IOException ioe) {
                if (attempt >= MAX_RETRIES) return null;
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
        return status == 408 || status == 425 || status == 429 || status == 499
                || (status >= 500 && status <= 599);
    }

    private static void sleepQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
