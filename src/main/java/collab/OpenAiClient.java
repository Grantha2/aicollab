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

    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;
    private final String modelName;
    private final int maxTokens;

    public OpenAiClient(HttpClient httpClient, String apiUrl,
                        String apiKey, String modelName, int maxTokens) {
        this.httpClient = httpClient;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.maxTokens = maxTokens;
    }

    @Override
    public String send(String systemInstruction, List<ChatMessage> messages) {
        JsonArray msgs = new JsonArray();
        if (systemInstruction != null && !systemInstruction.isEmpty()) {
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", systemInstruction);
            msgs.add(sys);
        }
        for (ChatMessage msg : messages) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            m.addProperty("content", msg.content());
            msgs.add(m);
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", modelName);
        body.addProperty("max_completion_tokens", maxTokens);
        body.add("messages", msgs);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return "[GPT ERROR " + resp.statusCode() + "] " + resp.body();
            }

            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            return root.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        } catch (Exception e) {
            return "[GPT ERROR] " + e.getMessage();
        }
    }
}
