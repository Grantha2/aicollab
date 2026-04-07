package collab;

// ============================================================
// WorkflowDefinition.java — Data model for a user-defined workflow.
//
// WHAT THIS CLASS DOES (one sentence):
// Stores all configuration for a custom agentic workflow that
// the user defines and can execute on demand or on a schedule.
// ============================================================

import java.util.*;

public class WorkflowDefinition {

    public enum TriggerType { MANUAL, SESSION_START, SCHEDULED }
    public enum WriteBackPolicy { NONE, AUTO_SAFE, APPROVAL_ALL }
    public enum OutputFormat { REPORT, BRIEF, CHECKLIST, EMAIL_DRAFT }

    private String id;
    private String name;
    private String description;
    private String category;
    private TriggerType triggerType = TriggerType.MANUAL;
    private List<String> inputQuestions = new ArrayList<>();
    private List<String> requiredContextLayers = new ArrayList<>();
    private String promptTemplate = "";
    private WriteBackPolicy writeBackPolicy = WriteBackPolicy.NONE;
    private OutputFormat outputFormat = OutputFormat.REPORT;
    private String cadence = "";
    private boolean enabled = true;

    public WorkflowDefinition() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
    }

    public WorkflowDefinition(String id, String name, String description, String category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
    }

    // --- Getters ---
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public TriggerType getTriggerType() { return triggerType; }
    public List<String> getInputQuestions() { return inputQuestions; }
    public List<String> getRequiredContextLayers() { return requiredContextLayers; }
    public String getPromptTemplate() { return promptTemplate; }
    public WriteBackPolicy getWriteBackPolicy() { return writeBackPolicy; }
    public OutputFormat getOutputFormat() { return outputFormat; }
    public String getCadence() { return cadence; }
    public boolean isEnabled() { return enabled; }

    // --- Setters ---
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setCategory(String category) { this.category = category; }
    public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }
    public void setInputQuestions(List<String> inputQuestions) { this.inputQuestions = inputQuestions; }
    public void setRequiredContextLayers(List<String> layers) { this.requiredContextLayers = layers; }
    public void setPromptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; }
    public void setWriteBackPolicy(WriteBackPolicy policy) { this.writeBackPolicy = policy; }
    public void setOutputFormat(OutputFormat format) { this.outputFormat = format; }
    public void setCadence(String cadence) { this.cadence = cadence; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * Resolve the prompt template by replacing {fieldName} placeholders
     * with values from the org context.
     */
    public String resolvePrompt(OrganizationContext orgContext, Map<String, String> inputAnswers) {
        String resolved = promptTemplate;

        // Replace {field} placeholders with org context values
        for (String fieldName : orgContext.getFieldNames()) {
            ContextEntry<String> entry = orgContext.getEntry(fieldName);
            String value = (entry != null && entry.getValue() != null) ? entry.getValue() : "(not set)";
            resolved = resolved.replace("{" + fieldName + "}", value);
        }

        // Replace {input:N} placeholders with user answers
        for (int i = 0; i < inputQuestions.size(); i++) {
            String answer = inputAnswers.getOrDefault(String.valueOf(i), "");
            resolved = resolved.replace("{input:" + i + "}", answer);
        }

        return resolved;
    }
}
