package cowork.llm;

// AnthropicClientBodyTest — request-body shape without any network: no
// attachment/document blocks, system only when non-blank, max_tokens honoured,
// tools array and tool_result message shapes, no sampling/thinking params.

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicClientBodyTest {

    private final AnthropicClient client = new AnthropicClient(
            HttpClient.newHttpClient(), "http://localhost/none", "sk-test-key-000000", "claude-opus-5", 1234);

    @Test
    void bodyHasModelSystemMaxTokensAndPlainMessagesOnly() {
        LlmRequest req = new LlmRequest("Be terse.", List.of(
                new ChatMessage("user", "hi"),
                new ChatMessage("assistant", "hello"),
                new ChatMessage("user", "again")), 777);
        JsonObject body = client.buildBody(req.systemInstruction(), req.messages(), req.maxTokens(), req.tools());

        assertEquals("claude-opus-5", body.get("model").getAsString());
        assertEquals(777, body.get("max_tokens").getAsInt());
        assertEquals("Be terse.", body.get("system").getAsString());
        JsonArray msgs = body.getAsJsonArray("messages");
        assertEquals(3, msgs.size());
        assertEquals("user", msgs.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("hi", msgs.get(0).getAsJsonObject().get("content").getAsString());
        assertEquals("assistant", msgs.get(1).getAsJsonObject().get("role").getAsString());
        assertFalse(body.has("tools"));
        assertEquals(Set.of("model", "max_tokens", "system", "messages"), body.keySet());

        String json = body.toString();
        assertFalse(json.contains("attachments"));
        assertFalse(json.contains("\"document\""));
        assertFalse(json.contains("file_id"));
        for (String forbidden : List.of("temperature", "top_p", "top_k", "thinking")) {
            assertFalse(body.has(forbidden), forbidden);
        }
    }

    @Test
    void blankSystemIsOmittedAndNonPositiveMaxTokensFallsBackToClientDefault() {
        JsonObject body = client.buildBody("   ", List.of(new ChatMessage("user", "x")), 0, List.of());
        assertFalse(body.has("system"));
        assertEquals(1234, body.get("max_tokens").getAsInt());
        assertFalse(client.buildBody(null, List.of(), 5, null).has("system"));
    }

    @Test
    void toolsSerialiseAsNameDescriptionInputSchema() {
        ToolSchema a = ToolSchema.noArgs("get_time", "Current time");
        ToolSchema b = ToolSchema.stringParams("lookup_room", "Find a room",
                List.of("building", "date"), Map.of("date", "ISO date"));
        JsonObject body = client.buildBody(null, List.of(new ChatMessage("user", "x")), 100, List.of(a, b));

        JsonArray tools = body.getAsJsonArray("tools");
        assertEquals(2, tools.size());
        JsonObject t0 = tools.get(0).getAsJsonObject();
        assertEquals(Set.of("name", "description", "input_schema"), t0.keySet());
        assertEquals("get_time", t0.get("name").getAsString());
        assertEquals("Current time", t0.get("description").getAsString());
        JsonObject s0 = t0.getAsJsonObject("input_schema");
        assertEquals("object", s0.get("type").getAsString());
        assertEquals(0, s0.getAsJsonObject("properties").size());

        JsonObject s1 = tools.get(1).getAsJsonObject().getAsJsonObject("input_schema");
        JsonObject props = s1.getAsJsonObject("properties");
        assertEquals("string", props.getAsJsonObject("building").get("type").getAsString());
        assertEquals("ISO date", props.getAsJsonObject("date").get("description").getAsString());
        assertEquals(2, s1.getAsJsonArray("required").size());
        assertEquals("building", s1.getAsJsonArray("required").get(0).getAsString());
    }

    @Test
    void toolResultMessageIsOneUserTurnCarryingEveryBlock() {
        JsonObject msg = AnthropicClient.toolResultMessage(List.of(
                ToolResult.ok("tu_1", "42"), ToolResult.error("tu_2", "boom")));

        assertEquals("user", msg.get("role").getAsString());
        JsonArray blocks = msg.getAsJsonArray("content");
        assertEquals(2, blocks.size());
        JsonObject ok = blocks.get(0).getAsJsonObject();
        assertEquals("tool_result", ok.get("type").getAsString());
        assertEquals("tu_1", ok.get("tool_use_id").getAsString());
        assertEquals("42", ok.get("content").getAsString());
        assertFalse(ok.has("is_error"));
        JsonObject err = blocks.get(1).getAsJsonObject();
        assertEquals("tool_result", err.get("type").getAsString());
        assertEquals("tu_2", err.get("tool_use_id").getAsString());
        assertEquals("boom", err.get("content").getAsString());
        assertTrue(err.get("is_error").getAsBoolean());
    }

    @Test
    void textOfConcatenatesOnlyTextBlocks() {
        JsonObject msg = JsonParser.parseString("""
                {"stop_reason":"end_turn","content":[
                  {"type":"text","text":"a"},
                  {"type":"tool_use","id":"x","name":"n","input":{}},
                  {"type":"text","text":"b"}]}
                """).getAsJsonObject();
        assertEquals("ab", AnthropicClient.textOf(msg));

        JsonObject refusal = JsonParser.parseString("{\"stop_reason\":\"refusal\",\"content\":[]}").getAsJsonObject();
        assertTrue(AnthropicClient.textOf(refusal).startsWith("[Claude ERROR]"));
    }
}
