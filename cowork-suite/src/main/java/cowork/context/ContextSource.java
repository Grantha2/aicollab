package cowork.context;

/**
 * Pluggable backend for OrganizationContext. The app ships with the on-disk
 * LocalContextSource; a remote implementation can be swapped in through
 * ContextController.setContextSource() without touching any caller.
 */
public interface ContextSource {

    /** Loads the latest full OrganizationContext. */
    OrganizationContext get();

    /** Persists the given OrganizationContext. */
    void save(OrganizationContext context);
}
