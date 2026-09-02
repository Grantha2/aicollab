package cowork.context;

/**
 * Decides which context layers a prompt receives (task block, organisation block) and
 * fronts the ContextSource so callers never touch OrganizationContext storage directly.
 * Defaults to the on-disk LocalContextSource; setContextSource() swaps in another backend
 * without any call-site changes.
 */
public class ContextController {

    private boolean includeTaskContext = true;
    private boolean includeOrgContext = true;
    private TaskContext activeTaskContext;
    private ContextSource contextSource = new LocalContextSource();

    public boolean shouldIncludeTaskContext()      { return includeTaskContext; }
    public boolean shouldIncludeOrgContext()       { return includeOrgContext; }
    public void setIncludeTaskContext(boolean v)   { this.includeTaskContext = v; }
    public void setIncludeOrgContext(boolean v)    { this.includeOrgContext = v; }

    public OrganizationContext getOrganizationContext() {
        return contextSource.get();
    }

    /** Persists in-place edits made to the object returned by getOrganizationContext(). */
    public void saveOrganizationContext() {
        contextSource.save(contextSource.get());
    }

    public void setContextSource(ContextSource source) {
        this.contextSource = source != null ? source : new LocalContextSource();
    }

    /** The org context prompt block, or "" when that layer is toggled off. */
    public String getEffectiveOrgContext() {
        if (!includeOrgContext) return "";
        OrganizationContext ctx = contextSource.get();
        return ctx == null ? "" : ctx.buildContextBlock();
    }

    public TaskContext getActiveTaskContext()          { return activeTaskContext; }
    public void setActiveTaskContext(TaskContext ctx)  { this.activeTaskContext = ctx; }
}
