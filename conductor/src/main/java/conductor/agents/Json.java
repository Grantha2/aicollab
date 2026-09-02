package conductor.agents;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Gson conveniences shared by the provider clients: a one-line object
 * builder for wire shapes, and null-safe reads for responses that omit
 * fields freely (no usage on errors, no content on refusals).
 */
final class Json {

    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Json() {}

    /** {@code of("type","text","text",s)} -> {@code {"type":"text","text":s}}. Values: String, Number, Boolean, JsonElement, null. */
    static JsonObject of(Object... keyValues) {
        var o = new JsonObject();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = (String) keyValues[i];
            switch (keyValues[i + 1]) {
                case null            -> o.add(key, JsonNull.INSTANCE);
                case JsonElement e   -> o.add(key, e);
                case Number n        -> o.addProperty(key, n);
                case Boolean b       -> o.addProperty(key, b);
                case Object v        -> o.addProperty(key, v.toString());
            }
        }
        return o;
    }

    static JsonArray arrayOf(JsonElement... elements) {
        var a = new JsonArray();
        for (var e : elements) a.add(e);
        return a;
    }

    static String str(JsonObject o, String key) {
        var v = get(o, key);
        return v != null && v.isJsonPrimitive() ? v.getAsString() : "";
    }

    static int num(JsonObject o, String key) {
        var v = get(o, key);
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? v.getAsInt() : 0;
    }

    static JsonObject obj(JsonObject o, String key) {
        var v = get(o, key);
        return v != null && v.isJsonObject() ? v.getAsJsonObject() : new JsonObject();
    }

    static JsonArray arr(JsonObject o, String key) {
        var v = get(o, key);
        return v != null && v.isJsonArray() ? v.getAsJsonArray() : new JsonArray();
    }

    /** Parses a JSON object string; anything else (bad JSON, array, null) yields {}. */
    static JsonObject parseObject(String text) {
        try {
            var el = JsonParser.parseString(text == null ? "" : text);
            return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static JsonElement get(JsonObject o, String key) {
        var v = o == null ? null : o.get(key);
        return v == null || v.isJsonNull() ? null : v;
    }
}
