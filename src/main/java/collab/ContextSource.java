package collab;

// ============================================================
// ContextSource.java — Pluggable backend for organization context.
//
// WHAT THIS INTERFACE DOES (one sentence):
// Abstracts how OrganizationContext is fetched and persisted so the
// app can later swap the local JSON store for a live AWS-backed
// source without touching ContextController or any dialog.
//
// WHY IT EXISTS:
// This PR keeps everything local (see LocalContextSource), but a
// follow-up PR will add AwsContextSource that POSTs to API Gateway +
// Lambda + DynamoDB. The interface freezes the shape today so that
// change is purely additive.
// ============================================================

public interface ContextSource {

    /** Loads the latest full OrganizationContext. */
    OrganizationContext get();

    /** Persists the given OrganizationContext. */
    void save(OrganizationContext context);
}
