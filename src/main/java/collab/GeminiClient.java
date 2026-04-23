package collab;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class GeminiClient implements LlmClient {
    private final HttpClient http;
    private final String key, model;
    private final int maxTokens;

    public GeminiClient(HttpClient http, String key, String model, int maxTokens) {
        this.http = http; this.key = key; this.model = model; this.maxTokens = maxTokens;
    }

    @Override
    public String send(String system, List<ChatMessage> messages) {
        JsonObject body = new JsonObject();
        if (system != null && !system.isEmpty()) body.add("system_instruction", textParts(system));
        JsonArray contents = new JsonArray();
        for (ChatMessage m : messages) {
            JsonObject entry = textParts(m.content());
            entry.addProperty("role", "assistant".equals(m.role()) ? "model" : m.role());
            contents.add(entry);
        }
        body.add("contents", contents);
        JsonObject gen = new JsonObject(); gen.addProperty("maxOutputTokens", maxTokens);
        body.add("generationConfig", gen);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + key;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return "[Gemini ERROR " + resp.statusCode() + "] " + resp.body();

            JsonArray parts = JsonParser.parseString(resp.body()).getAsJsonObject()
                    .getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content").getAsJsonArray("parts");
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                JsonObject p = parts.get(i).getAsJsonObject();
                if (p.has("text")) out.append(p.get("text").getAsString());
            }
            return out.toString();
        } catch (Exception e) {
            return "[Gemini ERROR] " + e.getMessage();
        }
    }

    // Gemini's "content" objects wrap text inside a parts array.
    // This helper builds the {parts:[{text:"..."}]} shape used both
    // for system_instruction and for each content entry.
    private static JsonObject textParts(String text) {
        JsonObject o = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject p = new JsonObject(); p.addProperty("text", text); parts.add(p);
        o.add("parts", parts);
        return o;
    }
}
