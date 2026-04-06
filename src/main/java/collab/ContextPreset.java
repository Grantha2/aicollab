package collab;

// ============================================================
// ContextPreset.java — A named context configuration that can
// be saved and loaded.
//
// WHAT THIS CLASS DOES (one sentence):
// Stores a named set of context toggle states (team context on/off,
// agent identity on/off, etc.) so users can quickly switch between
// configurations like "Full Context" and "Minimal".
// ============================================================

public class ContextPreset {

    private String name;
    private boolean teamContext;
    private boolean agentIdentity;
    private boolean stakeholder;
    private boolean history;
    private boolean taskContext;
    private boolean orgContext;
    private String teamContextOverride;

    public ContextPreset() {}

    public ContextPreset(String name, boolean teamContext, boolean agentIdentity,
                         boolean stakeholder, boolean history, boolean taskContext,
                         boolean orgContext) {
        this.name = name;
        this.teamContext = teamContext;
        this.agentIdentity = agentIdentity;
        this.stakeholder = stakeholder;
        this.history = history;
        this.taskContext = taskContext;
        this.orgContext = orgContext;
    }

    public String getName()             { return name; }
    public boolean isTeamContext()       { return teamContext; }
    public boolean isAgentIdentity()    { return agentIdentity; }
    public boolean isStakeholder()      { return stakeholder; }
    public boolean isHistory()          { return history; }
    public boolean isTaskContext()      { return taskContext; }
    public boolean isOrgContext()       { return orgContext; }
    public String getTeamContextOverride() { return teamContextOverride; }

    public void setName(String name)                      { this.name = name; }
    public void setTeamContext(boolean v)                  { this.teamContext = v; }
    public void setAgentIdentity(boolean v)               { this.agentIdentity = v; }
    public void setStakeholder(boolean v)                  { this.stakeholder = v; }
    public void setHistory(boolean v)                      { this.history = v; }
    public void setTaskContext(boolean v)                  { this.taskContext = v; }
    public void setOrgContext(boolean v)                   { this.orgContext = v; }
    public void setTeamContextOverride(String override)    { this.teamContextOverride = override; }

    // Apply this preset's settings to a ContextController.
    public void applyTo(ContextController controller) {
        controller.setIncludeTeamContext(teamContext);
        controller.setIncludeAgentIdentity(agentIdentity);
        controller.setIncludeStakeholderProfile(stakeholder);
        controller.setIncludeHistory(history);
        controller.setIncludeTaskContext(taskContext);
        controller.setIncludeOrgContext(orgContext);
        controller.setTeamContextOverride(teamContextOverride);
    }

    // Built-in presets.
    public static ContextPreset fullContext() {
        return new ContextPreset("Full Context", true, true, true, true, true, true);
    }

    public static ContextPreset minimal() {
        return new ContextPreset("Minimal", false, false, false, false, false, false);
    }

    public static ContextPreset noHistory() {
        return new ContextPreset("No History", true, true, true, false, true, true);
    }
}
