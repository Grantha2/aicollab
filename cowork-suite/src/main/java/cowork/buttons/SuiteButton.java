package cowork.buttons;

// Data model for one button on the Executive Suite board. A button is a
// task template: prompt template + follow-up questions + style guidance.
// Colour is not stored here — it comes from the category via CategoryColorMap.

import cowork.context.TaskContext;

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

    private String promptTemplate;
    private List<String> followUpQuestions;
    private String styleInstructions;

    public SuiteButton() {
        this.id = UUID.randomUUID().toString();
        this.actionParams = new HashMap<>();
        this.followUpQuestions = new ArrayList<>();
    }

    public SuiteButton(String label, String category, String actionType) {
        this();
        this.label = label;
        this.category = category;
        this.actionType = actionType;
    }

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
        if (actionParams == null) actionParams = new HashMap<>();
        actionParams.put(key, value);
    }

    public String getParam(String key) {
        return actionParams == null ? null : actionParams.get(key);
    }

    public String getPromptTemplate()              { return promptTemplate; }
    public List<String> getFollowUpQuestions()      { return followUpQuestions; }
    public String getStyleInstructions()           { return styleInstructions; }

    public void setPromptTemplate(String template) { this.promptTemplate = template; }
    public void setFollowUpQuestions(List<String> questions) {
        this.followUpQuestions = questions != null ? questions : new ArrayList<>();
    }
    public void setStyleInstructions(String style) { this.styleInstructions = style; }

    /** Builds a TaskContext from the template fields; null if this button has no template data. */
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
        return ctx;
    }

    public boolean isTaskTemplate() {
        return "TASK_TEMPLATE".equals(actionType);
    }
}
