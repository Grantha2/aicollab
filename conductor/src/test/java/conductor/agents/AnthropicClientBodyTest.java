package conductor.agents;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicClientBodyTest {

    private final AnthropicClient client =
            new AnthropicClient(HttpClient.newHttpClient(), null, "sk-test-key-000000", "claude-opus-5");

    private static JsonObject schema() {
        var s = new JsonObject();
        s.addProperty("type", "object");
        s.addProperty("additionalProperties", false);
        return s;
    }

    @Test
    void systemIsCachedArrayAndToolsAreStrict() {
        var tools = List.of(new ToolSpec("a", "first", schema()), new ToolSpec("b", "second", schema()));
        var body = client.buildBody(new AgentRequest("be terse", List.of(ChatMessage.user("hi")), tools, schema(), 500));

        assertEquals("claude-opus-5", body.get("model").getAsString());
        assertEquals(500, body.get("max_tokens").getAsInt());

        var system = body.getAsJsonArray("system");
        assertEquals(1, system.size());
        var block = system.get(0).getAsJsonObject();
        assertEquals("text", block.get("type").getAsString());
        assertEquals("be terse", block.get("text").getAsString());
        assertEquals("ephemeral", block.getAsJsonObject("cache_control").get("type").getAsString());

        var toolArr = body.getAsJsonArray("tools");
        assertEquals(2, toolArr.size());
        assertTrue(toolArr.get(0).getAsJsonObject().get("strict").getAsBoolean());
        assertTrue(toolArr.get(0).getAsJsonObject().has("input_schema"));
        assertFalse(toolArr.get(0).getAsJsonObject().has("cache_control"));
        assertTrue(toolArr.get(1).getAsJsonObject().has("cache_control"));

        var format = body.getAsJsonObject("output_config").getAsJsonObject("format");
        assertEquals("json_schema", format.get("type").getAsString());
        assertTrue(format.has("schema"));
        assertFalse(body.has("output_format"));
        assertFalse(body.has("tool_choice"));

        for (String banned : List.of("temperature", "top_p", "top_k", "thinking")) assertFalse(body.has(banned), banned);
    }

    @Test
    void blankSystemAndNoToolsOmitsOptionalFields() {
        var body = client.buildBody(AgentRequest.text("  ", List.of(ChatMessage.user("hi")), 10));
        assertFalse(body.has("system"));
        assertFalse(body.has("tools"));
        assertFalse(body.has("output_config"));
        assertEquals("hi", body.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
    }

    @Test
    void toolTurnsMapToContentBlocks() {
        var args = new JsonObject();
        args.addProperty("q", "x");
        var history = List.of(
                ChatMessage.user("go"),
                ChatMessage.assistantToolCalls("thinking", List.of(new ToolCall("tu_1", "a", args), new ToolCall("tu_2", "b", args))),
                ChatMessage.toolResults(List.of(ToolResult.ok("tu_1", "one"), ToolResult.error("tu_2", "bad"))));
        var messages = client.buildBody(AgentRequest.text(null, history, 10)).getAsJsonArray("messages");

        var assistant = messages.get(1).getAsJsonObject();
        assertEquals("assistant", assistant.get("role").getAsString());
        var content = assistant.getAsJsonArray("content");
        assertEquals(3, content.size());
        assertEquals("text", content.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("tool_use", content.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("tu_1", content.get(1).getAsJsonObject().get("id").getAsString());
        assertEquals("x", content.get(1).getAsJsonObject().getAsJsonObject("input").get("q").getAsString());

        var results = messages.get(2).getAsJsonObject();
        assertEquals("user", results.get("role").getAsString());
        var blocks = results.getAsJsonArray("content");
        assertEquals(2, blocks.size(), "all results in ONE user message");
        assertEquals("tool_result", blocks.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("tu_1", blocks.get(0).getAsJsonObject().get("tool_use_id").getAsString());
        assertFalse(blocks.get(0).getAsJsonObject().has("is_error"));
        assertTrue(blocks.get(1).getAsJsonObject().get("is_error").getAsBoolean());
    }
}
