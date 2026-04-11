package collab;

/**
 * Freshness level of a context entry based on age vs configured TTL.
 */
public enum Freshness {
    FRESH,              // age < 50% of TTL
    AGING,              // age < 80% of TTL
    STALE,              // age < 100% of TTL
    NEEDS_CONFIRMATION  // age >= TTL
}
