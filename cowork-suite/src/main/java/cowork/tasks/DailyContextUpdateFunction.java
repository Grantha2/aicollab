package cowork.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cowork.context.ContextEntry;
import cowork.context.Freshness;
import cowork.context.OrganizationContext;
import cowork.context.ProposedChange;
import cowork.context.ReconciliationService;
import cowork.llm.LlmClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Refreshes stale organisation-context fields with one Claude call: builds a per-field
 * prompt, asks for a JSON array of updates, parses it with Gson into ProposedChanges and
 * hands them to the ReconciliationService. Malformed model output never throws; the raw
 * text is kept so the caller can show it instead of a misleading "nothing to update".
 */
public class DailyContextUpdateFunction {

    private static final String SOURCE = "daily_update";
    private static final double AI_CONFIDENCE = 0.7;

    private final OrganizationContext orgContext;
    private final ReconciliationService reconciliation;
    private volatile String lastRawResponse;

    public DailyContextUpdateFunction(OrganizationContext orgContext,
                                      ReconciliationService reconciliation) {
        this.orgContext = orgContext;
        this.reconciliation = reconciliation;
    }

    /**
     * @param targetFields  fields to update (null/empty = every non-fresh field)
     * @param perFieldInput user notes keyed by field name; key "_general" applies to all. May be null.
     * @return the prompt, or null when there is nothing to update
     */
    public String buildPrompt(List<String> targetFields, Map<String, String> perFieldInput) {
        Map<String, Freshness> freshnessReport = orgContext.getFreshnessReport();

        List<String> fields;
        if (targetFields != null && !targetFields.isEmpty()) {
            fields = targetFields;
        } else {
            fields = new ArrayList<>();
            for (var entry : freshnessReport.entrySet()) {
                if (entry.getValue() != Freshness.FRESH) {
                    fields.add(entry.getKey());
                }
            }
        }
        if (fields.isEmpty()) return null;

        StringBuilder prompt = new StringBuilder();
        prompt.append("""
            You are an organizational context management assistant. Your job is to help \
            update structured organizational context fields based on what the user tells you.

            Below are the current values of context fields that need refreshing. The user \
            may provide specific update notes per field. Based on their input, produce \
            updated values for any fields that should change.

            IMPORTANT: Return your response as a JSON array of update objects. Each object has:
            - "field": the exact field name (from the list below)
            - "value": the complete updated value for that field (not just the delta)
            - "reason": brief explanation of what changed

            Only include fields that actually need updating based on the user's input. \
            If a field's current value is still accurate, do not include it.

            Return ONLY the JSON array, no other text. Example:
            [{"field": "topPriorities", "value": "1. New priority...", "reason": "User mentioned new focus area"}]

            === CURRENT FIELD VALUES ===
            """);

        for (String fieldName : fields) {
            String currentValue = getFieldValue(fieldName);
            Freshness freshness = freshnessReport.getOrDefault(fieldName, Freshness.NEEDS_CONFIRMATION);
            ContextEntry<String> entry = orgContext.getEntry(fieldName);
            String lastUpdated = entry != null ? entry.getLastUpdated() : "unknown";

            prompt.append("\nField: ").append(fieldName);
            prompt.append("\nLabel: ").append(OrganizationContext.getFieldLabel(fieldName));
            prompt.append("\nFreshness: ").append(freshness);
            prompt.append("\nLast Updated: ").append(lastUpdated);
            prompt.append("\nCurrent Value: ").append(currentValue.isBlank() ? "(empty)" : currentValue);
            if (perFieldInput != null && perFieldInput.containsKey(fieldName)) {
                prompt.append("\nUser Note: ").append(perFieldInput.get(fieldName));
            }
            prompt.append("\n---");
        }

        if (perFieldInput != null && perFieldInput.containsKey("_general")) {
            prompt.append("\n\n=== GENERAL USER NOTE ===\n");
            prompt.append(perFieldInput.get("_general"));
        }

        return prompt.toString();
    }

    /** Parses the model's JSON array (fenced or with surrounding prose) into proposals; garbage yields an empty list. */
    public List<ProposedChange> parseResponse(String aiResponse) {
        List<ProposedChange> proposals = new ArrayList<>();
        String json = extractJsonArray(stripFences(aiResponse));
        if (json == null) return proposals;

        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject obj = element.getAsJsonObject();
                if (!isString(obj, "field") || !isString(obj, "value")) continue;
                String fieldName = obj.get("field").getAsString();
                proposals.add(new ProposedChange(
                    fieldName, getFieldValue(fieldName), obj.get("value").getAsString(), SOURCE, AI_CONFIDENCE));
            }
        } catch (RuntimeException e) {
            System.err.println("[DailyContextUpdate] Unparseable model response: " + e.getMessage());
        }
        return proposals;
    }

    /**
     * Build prompt, call Claude once, parse, reconcile.
     *
     * @return the reconciliation result, or null when there was nothing to update or the model
     *         proposed nothing parseable (see {@link #lastRawResponse()})
     * @throws IllegalStateException when the client reports an API error
     */
    public ReconciliationService.ReconciliationResult execute(
            LlmClient client, List<String> targetFields, Map<String, String> perFieldInput) {
        lastRawResponse = null;

        String prompt = buildPrompt(targetFields, perFieldInput);
        if (prompt == null) return null;

        String response = client.sendMessage(prompt);
        if (response == null || response.startsWith("[ERROR]")) {
            throw new IllegalStateException("Claude call failed: " + (response == null ? "no response" : response));
        }
        lastRawResponse = stripFences(response);

        List<ProposedChange> proposals = parseResponse(response);
        if (proposals.isEmpty()) return null;

        proposals.add(new ProposedChange(
            "lastUpdated",
            orgContext.getLastUpdated(),
            Instant.now().toString().substring(0, 10),
            SOURCE,
            1.0));

        return reconciliation.reconcile(proposals);
    }

    /** Fence-stripped model text from the most recent execute(); null before any call or after an API error. */
    public String lastRawResponse() {
        return lastRawResponse;
    }

    private String getFieldValue(String fieldName) {
        ContextEntry<String> entry = orgContext.getEntry(fieldName);
        if (entry == null || entry.getValue() == null) return "";
        return entry.getValue();
    }

    private static boolean isString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString();
    }

    /** Removes a surrounding ```json ... ``` fence if the model added one. */
    static String stripFences(String text) {
        if (text == null) return null;
        String t = text.strip();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            t = firstNewline >= 0 ? t.substring(firstNewline + 1) : "";
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.strip();
    }

    /** Extracts the outermost JSON array from text that may contain surrounding prose. */
    static String extractJsonArray(String text) {
        if (text == null) return null;
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }
}
