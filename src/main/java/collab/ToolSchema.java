package collab;

import java.util.List;
import java.util.Map;

// ============================================================
// ToolSchema.java — Provider-agnostic description of a callable tool.
//
// WHAT THIS RECORD DOES (one sentence):
// Describes a single function that an LLM is allowed to invoke
// during a turn, in a shape generic enough to translate into each
// provider's native tool-use protocol.
//
// HOW IT FITS THE ARCHITECTURE:
// Maestro does not own tools directly. Tools are registered with a
// ToolExecutor and their schemas are forwarded on LlmRequest.tools
// down to the provider clients. Each client (Anthropic/OpenAI/Gemini)
// serialises the same ToolSchema into its own wire format:
//
//   Anthropic   -> tools: [{"name":…,"description":…,"input_schema":…}]
//   OpenAI      -> tools: [{"type":"function","function":{…}}]
//   Gemini      -> tools: [{"functionDeclarations":[…]}]
//
// WHY A RECORD AND NOT AN INTERFACE:
// A ToolSchema is pure data — name, description, JSON schema for
// parameters. The BEHAVIOUR (what "call this tool" means) lives in
// ToolExecutor handlers, keyed by tool name. Separating schema from
// behaviour lets the same schema be forwarded to the provider while
// the handler stays local to the host process.
//
// WHY `parameterSchema` IS A Map RATHER THAN A String:
// We build it programmatically (toolbuilder helpers) and then each
// client serialises it via Gson. Keeping it in Java-land avoids
// quote-escaping bugs and lets the three clients each emit their own
// wire dialect from the same source Map. Value types allowed inside
// the map: String, Number, Boolean, List, Map — the standard JSON
// tree that Gson's JsonTreeBuilder understands.
// ============================================================
public record ToolSchema(String name,
                         String description,
                         Map<String, Object> parameterSchema) {

    // Canonical ctor normalises null fields so downstream code never
    // has to null-check. An anonymous tool with no parameters is legal
    // (e.g. "get_current_time") and serialises as {type:object, properties:{}}.
    public ToolSchema {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ToolSchema requires a non-blank name");
        }
        if (description == null) description = "";
        if (parameterSchema == null) parameterSchema = Map.of();
    }

    /**
     * Convenience factory for a no-parameter tool. Produces a schema
     * whose JSON form is {"type":"object","properties":{}}.
     */
    public static ToolSchema noArgs(String name, String description) {
        return new ToolSchema(name, description,
                Map.of("type", "object", "properties", Map.of()));
    }

    /**
     * Convenience factory for a tool with a flat set of required
     * string parameters. Every param is typed as "string"; anything
     * richer (enum, number, nested object) needs the full constructor.
     */
    public static ToolSchema stringParams(String name, String description,
                                          List<String> requiredParams,
                                          Map<String, String> paramDescriptions) {
        java.util.LinkedHashMap<String, Object> props = new java.util.LinkedHashMap<>();
        for (String p : requiredParams) {
            java.util.LinkedHashMap<String, Object> prop = new java.util.LinkedHashMap<>();
            prop.put("type", "string");
            if (paramDescriptions != null && paramDescriptions.containsKey(p)) {
                prop.put("description", paramDescriptions.get(p));
            }
            props.put(p, prop);
        }
        java.util.LinkedHashMap<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.copyOf(requiredParams));
        return new ToolSchema(name, description, schema);
    }
}
