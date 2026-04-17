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

    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;
    private final String modelName;
    private final int maxTokens;

    public AnthropicClient(HttpClient httpClient, String apiUrl,
                           String apiKey, String modelName, int maxTokens) {
        this.httpClient = httpClient;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.maxTokens = maxTokens;
    }

    @Override
    public String send(String systemInstruction, List<ChatMessage> messages) {
        JsonObject body = new JsonObject();
        body.addProperty("model", modelName);
        body.addProperty("max_tokens", maxTokens);

        if (systemInstruction != null && !systemInstruction.isEmpty()) {
            body.addProperty("system", systemInstruction);
        }

        JsonArray msgArr = new JsonArray();
        for (ChatMessage msg : messages) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            m.addProperty("content", msg.content());
            msgArr.add(m);
        }
        body.add("messages", msgArr);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return "[Claude ERROR " + resp.statusCode() + "] " + resp.body();
            }

            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray content = root.getAsJsonArray("content");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < content.size(); i++) {
                JsonObject block = content.get(i).getAsJsonObject();
                if ("text".equals(block.get("type").getAsString()) && block.has("text")) {
                    sb.append(block.get("text").getAsString());
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "[Claude ERROR] " + e.getMessage();
        }
    }
}
