package collab;

import java.util.Map;

// ============================================================
// ToolCall.java — One tool invocation produced by an LLM.
//
// WHAT THIS RECORD DOES (one sentence):
// Captures a model's request to run a named tool with a JSON
// argument payload, plus the provider-specific id we need to
// echo back when we return the result.
//
// WHY `id` MATTERS:
// Anthropic and OpenAI BOTH require us to echo an opaque id on the
// matching tool_result / tool_response content block so they can
// pair result-to-call when a model issues multiple tool calls in
// one turn. Gemini's functionCall lacks an id field, so for Gemini
// we synthesise a deterministic id ("call_<index>") when parsing
// the response.
//
// WHY `arguments` IS A Map, NOT A String:
// Providers return the arguments as a JSON object embedded in their
// response. We parse it once into a Map at the client boundary so
// tool handlers don't have to re-parse. Gson-friendly: values are
// already Strings / Numbers / Booleans / Lists / Maps.
// ============================================================
public record ToolCall(String id,
                       String name,
                       Map<String, Object> arguments) {

    public ToolCall {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ToolCall requires a tool name");
        }
        if (id == null) id = "";
        if (arguments == null) arguments = Map.of();
    }
}
