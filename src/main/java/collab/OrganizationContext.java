package collab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

// Organization context: a map of labeled strings, loaded from and saved
// to org_context.json. Renders as a "=== ORGANIZATION CONTEXT ===" block
// for prompt injection.
public class OrganizationContext {

    private static final String FILE_NAME = "org_context.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Map<String, String> fields = new LinkedHashMap<>();

    public OrganizationContext() {
        for (String key : DEFAULT_FIELDS.keySet()) {
            fields.put(key, "");
        }
    }

    public Map<String, String> fields() { return fields; }

    public String get(String key) {
        return fields.getOrDefault(key, "");
    }

    public void set(String key, String value) {
        fields.put(key, value == null ? "" : value);
    }

    public String buildContextBlock() {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            String value = e.getValue();
            if (value == null || value.isBlank()) continue;
            if (!any) {
                sb.append("=== ORGANIZATION CONTEXT ===\n");
                any = true;
            }
            String label = DEFAULT_FIELDS.getOrDefault(e.getKey(), e.getKey());
            sb.append(label).append(": ").append(value).append("\n");
        }
        if (any) sb.append("\n");
        return sb.toString();
    }

    public static OrganizationContext load() {
        Path path = Path.of(FILE_NAME);
        if (!Files.exists(path)) return new OrganizationContext();
        try {
            OrganizationContext ctx = GSON.fromJson(Files.readString(path), OrganizationContext.class);
            if (ctx == null) return new OrganizationContext();
            if (ctx.fields == null) ctx.fields = new LinkedHashMap<>();
            // Seed any missing default fields for UI continuity.
            for (String key : DEFAULT_FIELDS.keySet()) {
                ctx.fields.putIfAbsent(key, "");
            }
            return ctx;
        } catch (IOException e) {
            System.err.println("[OrganizationContext] Failed to load: " + e.getMessage());
            return new OrganizationContext();
        }
    }

    public void save() {
        try {
            Files.writeString(Path.of(FILE_NAME), GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[OrganizationContext] Failed to save: " + e.getMessage());
        }
    }

    private static final Map<String, String> DEFAULT_FIELDS = new LinkedHashMap<>();
    static {
        DEFAULT_FIELDS.put("currentTermDateRange",       "Current Term / Date Range");
        DEFAULT_FIELDS.put("topPriorities",              "Top Priorities");
        DEFAULT_FIELDS.put("activeInitiativesAndStatus", "Active Initiatives and Status");
        DEFAULT_FIELDS.put("upcomingDeadlinesAndEvents", "Upcoming Deadlines and Events");
        DEFAULT_FIELDS.put("currentMetrics",             "Current Metrics");
        DEFAULT_FIELDS.put("currentBlockersRisks",       "Current Blockers / Risks");
        DEFAULT_FIELDS.put("pendingDecisions",           "Pending Decisions");
        DEFAULT_FIELDS.put("preferredToneStyle",         "Preferred Tone / Style");
    }

    public static Map<String, String> defaultFields() {
        return DEFAULT_FIELDS;
    }
}
