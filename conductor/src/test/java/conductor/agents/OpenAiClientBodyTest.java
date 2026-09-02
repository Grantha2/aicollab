package conductor.agents;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiClientBodyTest {

    private final OpenAiClient client =
            new OpenAiClient(HttpClient.newHttpClient(), null, "sk-test-key-000000", "gpt-5.4-mini");

    private static JsonObject schema() {
        var s = new JsonObject();
        s.addProperty("type", "object");
        return s;
    }

    @Test
    void systemLeadsMessagesAndToolsAreFunctions() {
        var req = new AgentRequest("sys", List.of(ChatMessage.user("hi")),
                List.of(new ToolSpec("lookup", "d", schema())), schema(), 300);
        var body = client.buildBody(req);

        assertEquals("gpt-5.4-mini", body.get("model").getAsString());
        assertEquals(300, body.get("max_completion_tokens").getAsInt());
        assertFalse(body.has("max_tokens"));

        var messages = body.getAsJsonArray("messages");
        assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("sys", messages.get(0).getAsJsonObject().get("content").getAsString());
        assertEquals("user", messages.get(1).getAsJsonObject().get("role").getAsString());

        var tool = body.getAsJsonArray("tools").get(0).getAsJsonObject();
        assertEquals("function", tool.get("type").getAsString());
        var fn = tool.getAsJsonObject("function");
        assertEquals("lookup", fn.get("name").getAsString());
        assertTrue(fn.has("parameters"));
        assertTrue(fn.get("strict").getAsBoolean());

        var rf = body.getAsJsonObject("response_format");
        assertEquals("json_schema", rf.get("type").getAsString());
        var js = rf.getAsJsonObject("json_schema");
        assertEquals("result", js.get("name").getAsString());
        assertTrue(js.get("strict").getAsBoolean());
        assertTrue(js.has("schema"));
    }

    @Test
    void toolCallArgumentsAreStringsAndResultsAreSeparateMessages() {
        var args = new JsonObject();
        args.addProperty("q", "x");
        var history = List.of(
                ChatMessage.user("go"),
                ChatMessage.assistantToolCalls("", List.of(new ToolCall("call_1", "lookup", args))),
                ChatMessage.toolResults(List.of(ToolResult.ok("call_1", "one"), ToolResult.error("call_2", "bad"))));
        var messages = client.buildBody(AgentRequest.text(null, history, 10)).getAsJsonArray("messages");

        assertEquals(4, messages.size(), "user + assistant + one tool message PER result");
        var assistant = messages.get(1).getAsJsonObject();
        var call = assistant.getAsJsonArray("tool_calls").get(0).getAsJsonObject();
        assertEquals("call_1", call.get("id").getAsString());
        assertEquals("function", call.get("type").getAsString());
        var arguments = call.getAsJsonObject("function").get("arguments");
        assertTrue(arguments.isJsonPrimitive() && arguments.getAsJsonPrimitive().isString(), "arguments is a JSON string");
        assertEquals("x", JsonParser.parseString(arguments.getAsString()).getAsJsonObject().get("q").getAsString());

        var first = messages.get(2).getAsJsonObject();
        assertEquals("tool", first.get("role").getAsString());
        assertEquals("call_1", first.get("tool_call_id").getAsString());
        assertEquals("one", first.get("content").getAsString());
        assertEquals("ERROR: bad", messages.get(3).getAsJsonObject().get("content").getAsString());
    }
}
