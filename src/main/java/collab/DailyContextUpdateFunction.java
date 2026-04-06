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
     * Builds the prompt for the daily context update function.
     * Includes stale field current values and asks AI to propose updates.
     *
     * @param targetFields specific fields to update (null = all stale/aging fields)
     * @param userInput    free-text from user about what changed
     * @return the assembled prompt to send to Claude
     */
    public String buildPrompt(List<String> targetFields, String userInput) {
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
            will describe what has changed. Based on their input, produce updated values \
            for any fields that should change.

            IMPORTANT: Return your response as a JSON array of update objects. Each object has:
            - "field": the exact field name (from the list below)
            - "value": the updated value for that field
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
            prompt.append("\n---");
        }

        prompt.append("\n\n=== USER UPDATE ===\n");
        prompt.append(userInput != null ? userInput : "(No specific update provided. Review fields and suggest any needed changes based on the current values and dates.)");

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
     * Full execution: build prompt, call API, parse response, reconcile.
     * Returns the reconciliation result.
     *
     * @param client       the LLM client to use (Claude)
     * @param targetFields specific fields to update (null = all stale)
     * @param userInput    what the user says changed
     * @return reconciliation result, or null if nothing to update
     */
    public ReconciliationService.ReconciliationResult execute(
            LlmClient client, List<String> targetFields, String userInput) {

        String prompt = buildPrompt(targetFields, userInput);
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
