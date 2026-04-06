package collab;

// ============================================================
// ContextController.java — Holds toggle/override state for
// every context layer sent to models.
//
// WHAT THIS CLASS DOES (one sentence):
// Sits between MainGui and PromptBuilder, letting users toggle
// individual context layers on/off and override their content.
// ============================================================

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ContextController {

    private boolean includeTeamContext = true;
    private boolean includeAgentIdentity = true;
    private boolean includeStakeholderProfile = true;
    private boolean includeHistory = true;
    private boolean includeTaskContext = true;

    // null = use default from PromptBuilder
    private String teamContextOverride = null;

    // Active task context (set when a task button is clicked)
    private TaskContext activeTaskContext = null;

    // Per-model agent identity toggles (model name → enabled)
    private final Map<String, Boolean> agentToggles = new HashMap<>();

    // Indices of history entries to exclude
    private final Set<Integer> excludedHistoryEntries = new HashSet<>();

    public boolean shouldIncludeTeamContext()       { return includeTeamContext; }
    public boolean shouldIncludeAgentIdentity()     { return includeAgentIdentity; }
    public boolean shouldIncludeStakeholderProfile() { return includeStakeholderProfile; }
    public boolean shouldIncludeHistory()           { return includeHistory; }
    public boolean shouldIncludeTaskContext()       { return includeTaskContext; }

    public void setIncludeTeamContext(boolean v)       { this.includeTeamContext = v; }
    public void setIncludeAgentIdentity(boolean v)     { this.includeAgentIdentity = v; }
    public void setIncludeStakeholderProfile(boolean v) { this.includeStakeholderProfile = v; }
    public void setIncludeHistory(boolean v)           { this.includeHistory = v; }
    public void setIncludeTaskContext(boolean v)       { this.includeTaskContext = v; }

    public TaskContext getActiveTaskContext()          { return activeTaskContext; }
    public void setActiveTaskContext(TaskContext ctx)  { this.activeTaskContext = ctx; }

    public String getTeamContextOverride()             { return teamContextOverride; }
    public void setTeamContextOverride(String override) { this.teamContextOverride = override; }

    // Returns the effective team context: override if set, null otherwise
    // (caller should fall back to default).
    public String getEffectiveTeamContext(String defaultContext) {
        if (!includeTeamContext) return "";
        return teamContextOverride != null ? teamContextOverride : defaultContext;
    }

    public boolean shouldIncludeAgent(String modelName) {
        if (!includeAgentIdentity) return false;
        return agentToggles.getOrDefault(modelName, true);
    }

    public void setAgentToggle(String modelName, boolean enabled) {
        agentToggles.put(modelName, enabled);
    }

    public void excludeHistoryEntry(int index) {
        excludedHistoryEntries.add(index);
    }

    public void includeHistoryEntry(int index) {
        excludedHistoryEntries.remove(index);
    }

    public boolean isHistoryEntryExcluded(int index) {
        return excludedHistoryEntries.contains(index);
    }

    public Set<Integer> getExcludedHistoryEntries() {
        return new HashSet<>(excludedHistoryEntries);
    }

    public void clearExclusions() {
        excludedHistoryEntries.clear();
    }
}
