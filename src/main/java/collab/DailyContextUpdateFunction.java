package collab;

// ============================================================
// DailyContextUpdateFunction.java — Built-in agentic function
// that refreshes stale organization context fields.
//
// WHAT THIS CLASS DOES (one sentence):
// Identifies stale context fields, sends them to Claude with
// user-provided updates, and returns proposed changes for
// reconciliation.
//
// KEY DESIGN DECISIONS:
// - Single-model (Claude) for speed — no debate needed
// - Asks AI to return JSON array of field updates
// - Feeds parsed proposals into ReconciliationService
// - Can target specific fields or all stale fields
// ============================================================

import com.google.gson.*;
import java.util.*;
import java.time.Instant;

public class DailyContextUpdateFunction {

    private final OrganizationContext orgContext;
    private final ReconciliationService reconciliation;

    public DailyContextUpdateFunction(OrganizationContext orgContext,
                                       ReconciliationService reconciliation) {
        this.orgContext = orgContext;
        this.reconciliation = reconciliation;
    }

    /**
     * Builds the prompt with a single user input string (legacy).
     */
    public String buildPrompt(List<String> targetFields, String userInput) {
        Map<String, String> perField = null;
        if (userInput != null && !userInput.isBlank()) {
            // Wrap single input as a general note
            perField = Map.of("_general", userInput);
        }
        return buildPrompt(targetFields, perField);
    }

    /**
     * Builds the prompt for the daily context update function.
     * Includes stale field current values and per-field user notes.
     *
     * @param targetFields   specific fields to update (null = all stale/aging fields)
     * @param perFieldInput  per-field user notes (fieldName -> what changed). May be null.
     *                       Key "_general" is treated as a general note for all fields.
     * @return the assembled prompt to send to Claude
     */
    public String buildPrompt(List<String> targetFields, Map<String, String> perFieldInput) {
        Map<String, Freshness> freshnessReport = orgContext.getFreshnessReport();

        // Determine which fields to include
        List<String> fields;
        if (targetFields != null && !targetFields.isEmpty()) {
            fields = targetFields;
        } else {
            // All non-FRESH fields
            fields = new ArrayList<>();
            for (var entry : freshnessReport.entrySet()) {
                if (entry.getValue() != Freshness.FRESH) {
                    fields.add(entry.getKey());
                }
            }
        }

        if (fields.isEmpty()) {
            return null; // nothing to update
        }

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
            String label = OrganizationContext.getFieldLabel(fieldName);
            String currentValue = getFieldValue(fieldName);
            Freshness freshness = freshnessReport.getOrDefault(fieldName, Freshness.NEEDS_CONFIRMATION);
            ContextEntry<String> entry = orgContext.getEntry(fieldName);
            String lastUpdated = entry != null ? entry.getLastUpdated() : "unknown";

            prompt.append("\nField: ").append(fieldName);
            prompt.append("\nLabel: ").append(label);
            prompt.append("\nFreshness: ").append(freshness);
            prompt.append("\nLast Updated: ").append(lastUpdated);
            prompt.append("\nCurrent Value: ").append(currentValue.isBlank() ? "(empty)" : currentValue);

            // Include per-field user note if provided
            if (perFieldInput != null && perFieldInput.containsKey(fieldName)) {
                prompt.append("\nUser Note: ").append(perFieldInput.get(fieldName));
            }

            prompt.append("\n---");
        }

        // Include general note if provided
        if (perFieldInput != null && perFieldInput.containsKey("_general")) {
            prompt.append("\n\n=== GENERAL USER NOTE ===\n");
            prompt.append(perFieldInput.get("_general"));
        }

        return prompt.toString();
    }

    /**
     * Parses the AI response into proposed changes.
     *
     * @param aiResponse the JSON array string from Claude
     * @return list of proposed changes for reconciliation
     */
    public List<ProposedChange> parseResponse(String aiResponse) {
        List<ProposedChange> proposals = new ArrayList<>();

        try {
            // Extract JSON array from response (may have surrounding text)
            String json = extractJsonArray(aiResponse);
            if (json == null) return proposals;

            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                String fieldName = obj.has("field") ? obj.get("field").getAsString() : null;
                String value = obj.has("value") ? obj.get("value").getAsString() : null;

                if (fieldName != null && value != null) {
                    String currentValue = getFieldValue(fieldName);
                    proposals.add(new ProposedChange(
                        fieldName,
                        currentValue,
                        value,
                        "daily_update",
                        0.7 // AI-generated proposals start at moderate confidence
                    ));
                }
            }
        } catch (Exception e) {
            System.err.println("[DailyContextUpdate] Failed to parse AI response: " + e.getMessage());
        }

        return proposals;
    }

    /**
     * Full execution with single user input string (legacy).
     */
    public ReconciliationService.ReconciliationResult execute(
            LlmClient client, List<String> targetFields, String userInput) {
        Map<String, String> perField = null;
        if (userInput != null && !userInput.isBlank()) {
            perField = Map.of("_general", userInput);
        }
        return execute(client, targetFields, perField);
    }

    /**
     * Full execution: build prompt, call API, parse response, reconcile.
     *
     * @param client        the LLM client to use (Claude)
     * @param targetFields  specific fields to update (null = all stale)
     * @param perFieldInput per-field user notes (fieldName -> what changed)
     * @return reconciliation result, or null if nothing to update
     */
    public ReconciliationService.ReconciliationResult execute(
            LlmClient client, List<String> targetFields, Map<String, String> perFieldInput) {

        String prompt = buildPrompt(targetFields, perFieldInput);
        if (prompt == null) return null;

        String response = client.sendMessage(prompt);
        if (response == null || response.startsWith("[ERROR]")) {
            System.err.println("[DailyContextUpdate] API error: " + response);
            return null;
        }

        List<ProposedChange> proposals = parseResponse(response);
        if (proposals.isEmpty()) return null;

        // Also auto-update the "lastUpdated" field
        proposals.add(new ProposedChange(
            "lastUpdated",
            orgContext.getLastUpdated(),
            Instant.now().toString().substring(0, 10), // just the date
            "daily_update",
            1.0
        ));

        return reconciliation.reconcile(proposals);
    }

    private String getFieldValue(String fieldName) {
        ContextEntry<String> entry = orgContext.getEntry(fieldName);
        if (entry == null || entry.getValue() == null) return "";
        return entry.getValue();
    }

    /** Extracts a JSON array from a response that may contain surrounding text. */
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
