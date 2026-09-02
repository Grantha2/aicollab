package cowork.llm;

// ToolSchema — pure data describing one callable tool: name, description and a
// JSON-Schema parameter map (String/Number/Boolean/List/Map values) that the
// client serialises with Gson. Behaviour lives in ToolExecutor handlers.

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ToolSchema(String name,
                         String description,
                         Map<String, Object> parameterSchema) {

    public ToolSchema {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ToolSchema requires a non-blank name");
        }
        if (description == null) description = "";
        if (parameterSchema == null) parameterSchema = Map.of();
    }

    /** A tool with no parameters: {"type":"object","properties":{}}. */
    public static ToolSchema noArgs(String name, String description) {
        return new ToolSchema(name, description,
                Map.of("type", "object", "properties", Map.of()));
    }

    /** A tool whose parameters are all required strings; richer schemas use the canonical ctor. */
    public static ToolSchema stringParams(String name, String description,
                                          List<String> requiredParams,
                                          Map<String, String> paramDescriptions) {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        for (String p : requiredParams) {
            LinkedHashMap<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", "string");
            if (paramDescriptions != null && paramDescriptions.containsKey(p)) {
                prop.put("description", paramDescriptions.get(p));
            }
            props.put(p, prop);
        }
        LinkedHashMap<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.copyOf(requiredParams));
        return new ToolSchema(name, description, schema);
    }
}
