package collab;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class AnthropicClient implements LlmClient {
    private final HttpClient http;
    private final String url, key, model;
    private final int maxTokens;

    public AnthropicClient(HttpClient http, String url, String key, String model, int maxTokens) {
        this.http = http; this.url = url; this.key = key; this.model = model; this.maxTokens = maxTokens;
    }

    @Override
    public String send(String system, List<ChatMessage> messages) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        if (system != null && !system.isEmpty()) body.addProperty("system", system);

        JsonArray arr = new JsonArray();
        for (ChatMessage m : messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role());
            o.addProperty("content", m.content());
            arr.add(o);
        }
        body.add("messages", arr);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", key)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return "[Claude ERROR " + resp.statusCode() + "] " + resp.body();

            JsonArray content = JsonParser.parseString(resp.body()).getAsJsonObject().getAsJsonArray("content");
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < content.size(); i++) {
                JsonObject block = content.get(i).getAsJsonObject();
                if ("text".equals(block.get("type").getAsString())) out.append(block.get("text").getAsString());
            }
            return out.toString();
        } catch (Exception e) {
            return "[Claude ERROR] " + e.getMessage();
        }
    }
}
