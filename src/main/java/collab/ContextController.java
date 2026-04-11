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
    private boolean includeOrgContext = true;

    // Active task context (set when a task button is clicked)
    private TaskContext activeTaskContext = null;

    // Backing store for organization context. Defaults to the on-disk
    // LocalContextSource; a follow-up PR will add AwsContextSource so this
    // same controller can talk to an AWS API without any call-site changes.
    private ContextSource contextSource = new LocalContextSource();

    // Per-model agent identity toggles (model name → enabled)
    private final Map<String, Boolean> agentToggles = new HashMap<>();

    // Indices of history entries to exclude
    private final Set<Integer> excludedHistoryEntries = new HashSet<>();

    public boolean shouldIncludeTeamContext()       { return includeTeamContext; }
    public boolean shouldIncludeAgentIdentity()     { return includeAgentIdentity; }
    public boolean shouldIncludeStakeholderProfile() { return includeStakeholderProfile; }
    public boolean shouldIncludeHistory()           { return includeHistory; }
    public boolean shouldIncludeTaskContext()       { return includeTaskContext; }
    public boolean shouldIncludeOrgContext()        { return includeOrgContext; }

    public void setIncludeTeamContext(boolean v)       { this.includeTeamContext = v; }
    public void setIncludeAgentIdentity(boolean v)     { this.includeAgentIdentity = v; }
    public void setIncludeStakeholderProfile(boolean v) { this.includeStakeholderProfile = v; }
    public void setIncludeHistory(boolean v)           { this.includeHistory = v; }
    public void setIncludeTaskContext(boolean v)       { this.includeTaskContext = v; }
    public void setIncludeOrgContext(boolean v)        { this.includeOrgContext = v; }

    public OrganizationContext getOrganizationContext() {
        return contextSource.get();
    }

    /**
     * Persist in-place edits to the OrganizationContext returned by
     * {@link #getOrganizationContext()} through the underlying
     * {@link ContextSource}.
     */
    public void saveOrganizationContext() {
        contextSource.save(contextSource.get());
    }

    /** Swap the backing store — used by tests and the future AwsContextSource. */
    public void setContextSource(ContextSource source) {
        this.contextSource = source != null ? source : new LocalContextSource();
    }

    /**
     * Returns the org context block if enabled, empty string otherwise.
     */
    public String getEffectiveOrgContext() {
        if (!includeOrgContext) return "";
        OrganizationContext ctx = contextSource.get();
        if (ctx == null) return "";
        return ctx.buildContextBlock();
    }

    public TaskContext getActiveTaskContext()          { return activeTaskContext; }
    public void setActiveTaskContext(TaskContext ctx)  { this.activeTaskContext = ctx; }

    /**
     * Returns the team context to include in prompts, or empty string when
     * the team-context layer is toggled off. Team context text itself lives
     * on {@link ProfileSet} — this controller only decides whether to
     * include it.
     */
    public String getEffectiveTeamContext(String profileTeamContext) {
        if (!includeTeamContext) return "";
        return profileTeamContext == null ? "" : profileTeamContext;
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
