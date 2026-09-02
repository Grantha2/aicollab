package cowork.context;

/**
 * ContextSource backed by org_context.json in the app data directory. Loads once at
 * construction and caches; save() refreshes the cache and writes through to disk.
 */
public class LocalContextSource implements ContextSource {

    private OrganizationContext cached;

    public LocalContextSource() {
        this.cached = OrganizationContext.load();
    }

    @Override
    public OrganizationContext get() {
        if (cached == null) cached = OrganizationContext.load();
        return cached;
    }

    @Override
    public void save(OrganizationContext context) {
        if (context == null) return;
        this.cached = context;
        context.save();
    }
}
