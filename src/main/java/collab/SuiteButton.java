package collab;

// ============================================================
// SuiteButton.java — Data model for one task button in the
// Executive Suite GUI.
//
// WHAT THIS CLASS DOES (one sentence):
// Stores the task template definition for one button: its prompt
// template, follow-up questions, style instructions, and whether
// it runs as a simple single-API call or a full debate cycle.
//
// KEY DESIGN DECISIONS:
// - Color is NOT stored per-button. Color comes from the category
//   via CategoryColorMap.
// - Each button is a TASK TEMPLATE, not a feature shortcut.
//   It encodes the user's intent and auto-fills context.
// - simpleMode=true means single API call to one model;
//   simpleMode=false means full 3-phase Maestro debate cycle.
// ============================================================

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SuiteButton {

    private String id;
    private String label;
    private String category;
    private String iconPath;
    private String description;
    private String actionType;
    private Map<String, String> actionParams;
    private int sortOrder;

    // Task template fields
    private String promptTemplate;
    private List<String> followUpQuestions;
    private String styleInstructions;
    private boolean simpleMode;

    public SuiteButton() {
        this.id = UUID.randomUUID().toString();
        this.actionParams = new HashMap<>();
        this.followUpQuestions = new ArrayList<>();
        this.simpleMode = false;
    }

    public SuiteButton(String label, String category, String actionType) {
        this();
        this.label = label;
        this.category = category;
        this.actionType = actionType;
    }

    // Original getters/setters
    public String getId()          { return id; }
    public String getLabel()       { return label; }
    public String getCategory()    { return category; }
    public String getIconPath()    { return iconPath; }
    public String getDescription() { return description; }
    public String getActionType()  { return actionType; }
    public Map<String, String> getActionParams() { return actionParams; }
    public int getSortOrder()      { return sortOrder; }

    public void setId(String id)                   { this.id = id; }
    public void setLabel(String label)             { this.label = label; }
    public void setCategory(String category)       { this.category = category; }
    public void setIconPath(String iconPath)       { this.iconPath = iconPath; }
    public void setDescription(String description) { this.description = description; }
    public void setActionType(String actionType)   { this.actionType = actionType; }
    public void setActionParams(Map<String, String> params) { this.actionParams = params; }
    public void setSortOrder(int sortOrder)        { this.sortOrder = sortOrder; }

    public void putParam(String key, String value) {
        actionParams.put(key, value);
    }

    public String getParam(String key) {
        return actionParams.get(key);
    }

    // Task template getters/setters
    public String getPromptTemplate()              { return promptTemplate; }
    public List<String> getFollowUpQuestions()      { return followUpQuestions; }
    public String getStyleInstructions()           { return styleInstructions; }
    public boolean isSimpleMode()                  { return simpleMode; }

    public void setPromptTemplate(String template) { this.promptTemplate = template; }
    public void setFollowUpQuestions(List<String> questions) {
        this.followUpQuestions = questions != null ? questions : new ArrayList<>();
    }
    public void setStyleInstructions(String style) { this.styleInstructions = style; }
    public void setSimpleMode(boolean simple)      { this.simpleMode = simple; }

    /**
     * Builds a TaskContext from this button's template fields.
     * Returns null if this button has no task template data.
     */
    public TaskContext toTaskContext() {
        if (promptTemplate == null && (followUpQuestions == null || followUpQuestions.isEmpty())
                && styleInstructions == null) {
            return null;
        }
        TaskContext ctx = new TaskContext();
        ctx.setTaskName(label);
        ctx.setPromptTemplate(promptTemplate);
        ctx.setFollowUpQuestions(followUpQuestions != null ? new ArrayList<>(followUpQuestions) : new ArrayList<>());
        ctx.setStyleInstructions(styleInstructions);
        ctx.setSimpleMode(simpleMode);
        return ctx;
    }

    /**
     * Returns true if this button is a task template (has prompt template,
     * follow-up questions, or style instructions defined).
     */
    public boolean isTaskTemplate() {
        return "TASK_TEMPLATE".equals(actionType);
    }
}
