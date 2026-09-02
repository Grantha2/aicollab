package conductor.agents;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeminiClientBodyTest {

    private static final String KEY = "AIzaSyTESTKEY0000000000";
    private final GeminiClient client = new GeminiClient(HttpClient.newHttpClient(), KEY, "gemini-3.1-pro-preview");

    private static JsonObject schema() {
        var s = new JsonObject();
        s.addProperty("type", "object");
        return s;
    }

    @Test
    void keyTravelsInHeaderNotUrl() {
        assertFalse(client.endpoint().toString().contains(KEY));
        assertNull(client.endpoint().getQuery());
        assertTrue(client.endpoint().toString().endsWith("/models/gemini-3.1-pro-preview:generateContent"));
        assertEquals(KEY, client.headers().get("x-goog-api-key"));
    }

    @Test
    void rolesSystemToolsAndJsonModeMapToGeminiShapes() {
        var req = new AgentRequest("sys", List.of(ChatMessage.user("q"), ChatMessage.assistant("a"), ChatMessage.user("q2")),
                List.of(new ToolSpec("lookup", "d", schema())), schema(), 250);
        var body = client.buildBody(req);

        assertEquals("sys", body.getAsJsonObject("system_instruction").getAsJsonArray("parts")
                .get(0).getAsJsonObject().get("text").getAsString());

        var contents = body.getAsJsonArray("contents");
        assertEquals("user", contents.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("model", contents.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("a", contents.get(1).getAsJsonObject().getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString());

        var decl = body.getAsJsonArray("tools").get(0).getAsJsonObject()
                .getAsJsonArray("functionDeclarations").get(0).getAsJsonObject();
        assertEquals("lookup", decl.get("name").getAsString());
        assertTrue(decl.has("parameters"));

        var gen = body.getAsJsonObject("generationConfig");
        assertEquals(250, gen.get("maxOutputTokens").getAsInt());
        assertEquals("application/json", gen.get("responseMimeType").getAsString());
        assertTrue(gen.has("responseSchema"));
    }

    @Test
    void toolResultsAreKeyedByFunctionNameFromPrecedingCall() {
        var args = new JsonObject();
        args.addProperty("q", "x");
        var history = List.of(
                ChatMessage.user("go"),
                ChatMessage.assistantToolCalls("", List.of(new ToolCall("lookup-abcd1234", "lookup", args))),
                ChatMessage.toolResults(List.of(ToolResult.ok("lookup-abcd1234", "one"))));
        var contents = client.buildBody(AgentRequest.text(null, history, 10)).getAsJsonArray("contents");

        var model = contents.get(1).getAsJsonObject();
        assertEquals("model", model.get("role").getAsString());
        var fc = model.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonObject("functionCall");
        assertEquals("lookup", fc.get("name").getAsString());
        assertEquals("x", fc.getAsJsonObject("args").get("q").getAsString());

        var user = contents.get(2).getAsJsonObject();
        assertEquals("user", user.get("role").getAsString());
        var fr = user.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonObject("functionResponse");
        assertEquals("lookup", fr.get("name").getAsString(), "result keyed by function name, not call id");
        assertEquals("one", fr.getAsJsonObject("response").get("content").getAsString());
    }
}
