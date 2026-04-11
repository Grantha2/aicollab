package collab;

// ============================================================
// LocalContextSource.java — On-disk ContextSource implementation.
//
// WHAT THIS CLASS DOES (one sentence):
// Reads and writes OrganizationContext using the existing local
// org_context.json file via OrganizationContext.load() / save().
//
// HOW IT FITS:
// This is the current behavior, wrapped behind a ContextSource so
// a future AwsContextSource can slot in without callers changing.
// ============================================================

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
