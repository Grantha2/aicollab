package cowork.context;

// Pluggable backend for OrganizationContext. The app ships with the on-disk LocalContextSource;
// a remote implementation can be swapped in via ContextController.setContextSource() without touching callers.
public interface ContextSource {

    OrganizationContext get();

    void save(OrganizationContext context);
}
