package cowork.context;

// Freshness of a context entry relative to its field's TTL:
// FRESH below 50% of TTL, AGING below 80%, STALE below 100%, NEEDS_CONFIRMATION at or past TTL.
public enum Freshness {
    FRESH,
    AGING,
    STALE,
    NEEDS_CONFIRMATION
}
