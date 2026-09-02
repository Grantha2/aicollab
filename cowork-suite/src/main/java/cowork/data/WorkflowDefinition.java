package cowork.data;

import cowork.context.ContextEntry;
import cowork.context.OrganizationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A user-defined agentic workflow: trigger, intake questions, prompt template with
 * {field} and {input:N} placeholders, write-back policy and output format. resolvePrompt()
 * fills the placeholders from the org context and the user's answers.
 */
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

    public String getId()                          { return id; }
    public String getName()                        { return name; }
    public String getDescription()                 { return description; }
    public String getCategory()                    { return category; }
    public TriggerType getTriggerType()            { return triggerType; }
    public List<String> getInputQuestions()        { return inputQuestions; }
    public List<String> getRequiredContextLayers() { return requiredContextLayers; }
    public String getPromptTemplate()              { return promptTemplate; }
    public WriteBackPolicy getWriteBackPolicy()    { return writeBackPolicy; }
    public OutputFormat getOutputFormat()          { return outputFormat; }
    public String getCadence()                     { return cadence; }
    public boolean isEnabled()                     { return enabled; }

    public void setId(String id)                            { this.id = id; }
    public void setName(String name)                        { this.name = name; }
    public void setDescription(String description)          { this.description = description; }
    public void setCategory(String category)                { this.category = category; }
    public void setTriggerType(TriggerType triggerType)     { this.triggerType = triggerType; }
    public void setPromptTemplate(String promptTemplate)    { this.promptTemplate = promptTemplate; }
    public void setWriteBackPolicy(WriteBackPolicy policy)  { this.writeBackPolicy = policy; }
    public void setOutputFormat(OutputFormat format)        { this.outputFormat = format; }
    public void setCadence(String cadence)                  { this.cadence = cadence; }
    public void setEnabled(boolean enabled)                 { this.enabled = enabled; }

    public void setInputQuestions(List<String> inputQuestions) {
        this.inputQuestions = inputQuestions != null ? inputQuestions : new ArrayList<>();
    }

    public void setRequiredContextLayers(List<String> layers) {
        this.requiredContextLayers = layers != null ? layers : new ArrayList<>();
    }

    /**
     * Replaces {fieldName} with the org-context value and {input:N} with the answer to the
     * N-th input question (answers keyed by "0", "1", ...). Unanswered inputs resolve to "".
     */
    public String resolvePrompt(OrganizationContext orgContext, Map<String, String> inputAnswers) {
        String resolved = promptTemplate;
        for (String fieldName : OrganizationContext.getFieldNames()) {
            ContextEntry<String> entry = orgContext.getEntry(fieldName);
            String value = (entry != null && entry.getValue() != null) ? entry.getValue() : "(not set)";
            resolved = resolved.replace("{" + fieldName + "}", value);
        }
        for (int i = 0; i < inputQuestions.size(); i++) {
            resolved = resolved.replace("{input:" + i + "}", inputAnswers.getOrDefault(String.valueOf(i), ""));
        }
        return resolved;
    }
}
