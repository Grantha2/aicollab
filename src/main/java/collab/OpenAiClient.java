package collab;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class OpenAiClient implements LlmClient {
    private final HttpClient http;
    private final String url, key, model;
    private final int maxTokens;

    public OpenAiClient(HttpClient http, String url, String key, String model, int maxTokens) {
        this.http = http; this.url = url; this.key = key; this.model = model; this.maxTokens = maxTokens;
    }

    @Override
    public String send(String system, List<ChatMessage> messages) {
        JsonArray arr = new JsonArray();
        if (system != null && !system.isEmpty()) {
            JsonObject s = new JsonObject();
            s.addProperty("role", "system");
            s.addProperty("content", system);
            arr.add(s);
        }
        for (ChatMessage m : messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role());
            o.addProperty("content", m.content());
            arr.add(o);
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_completion_tokens", maxTokens);
        body.add("messages", arr);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return "[GPT ERROR " + resp.statusCode() + "] " + resp.body();

            return JsonParser.parseString(resp.body()).getAsJsonObject()
                    .getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        } catch (Exception e) {
            return "[GPT ERROR] " + e.getMessage();
        }
    }
}
