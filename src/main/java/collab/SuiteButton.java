package collab;

// ============================================================
// SuiteButton.java — Data model for one action button in the
// Executive Suite GUI.
//
// WHAT THIS CLASS DOES (one sentence):
// Stores the label, category, icon path, action type, and
// parameters for a single button that appears in the side panel.
//
// KEY DESIGN DECISION:
// Color is NOT stored per-button. Color comes from the category
// via CategoryColorMap. Change a button's category and its color
// changes automatically.
// ============================================================

import java.util.HashMap;
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

    public SuiteButton() {
        this.id = UUID.randomUUID().toString();
        this.actionParams = new HashMap<>();
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
        actionParams.put(key, value);
    }

    public String getParam(String key) {
        return actionParams.get(key);
    }
}
