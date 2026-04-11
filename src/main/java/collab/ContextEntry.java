package collab;

import java.time.Duration;
import java.time.Instant;

/**
 * Wraps a context value with metadata: when it was last updated,
 * where it came from, how confident we are, and its approval status.
 * Freshness is computed from lastUpdated vs a supplied TTL.
 */
public class ContextEntry<T> {

    private T value;
    private String lastUpdated;   // ISO-8601 instant string (Gson-friendly)
    private String source;        // "user_edit", "daily_update", "workflow_output", "import", "migration"
    private double confidence;    // 0.0–1.0
    private ContextStatus status; // APPROVED, PROVISIONAL, PENDING_REVIEW, ARCHIVED

    public ContextEntry() {
        this.value = null;
        this.lastUpdated = Instant.now().toString();
        this.source = "user_edit";
        this.confidence = 1.0;
        this.status = ContextStatus.APPROVED;
    }

    public ContextEntry(T value) {
        this.value = value;
        this.lastUpdated = Instant.now().toString();
        this.source = "user_edit";
        this.confidence = 1.0;
        this.status = ContextStatus.APPROVED;
    }

    public ContextEntry(T value, String source, double confidence, ContextStatus status) {
        this.value = value;
        this.lastUpdated = Instant.now().toString();
        this.source = source;
        this.confidence = confidence;
        this.status = status;
    }

    /** Wrap a plain value during migration from old format. */
    public static <T> ContextEntry<T> migrate(T value) {
        ContextEntry<T> entry = new ContextEntry<>(value);
        entry.source = "migration";
        entry.lastUpdated = Instant.EPOCH.toString(); // unknown — marks as NEEDS_CONFIRMATION
        return entry;
    }

    // --- Freshness ---

    public Freshness computeFreshness(Duration ttl) {
        Instant updated = getLastUpdatedInstant();
        Duration age = Duration.between(updated, Instant.now());
        if (age.isNegative()) return Freshness.FRESH;

        long ageMillis = age.toMillis();
        long ttlMillis = ttl.toMillis();

        if (ageMillis < ttlMillis / 2)         return Freshness.FRESH;
        if (ageMillis < (ttlMillis * 4) / 5)   return Freshness.AGING;
        if (ageMillis < ttlMillis)              return Freshness.STALE;
        return Freshness.NEEDS_CONFIRMATION;
    }

    public Instant getLastUpdatedInstant() {
        try {
            return Instant.parse(lastUpdated);
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    // --- Getters / Setters ---

    public T getValue() { return value; }
    public void setValue(T value) {
        this.value = value;
        this.lastUpdated = Instant.now().toString();
    }

    public String getLastUpdated()          { return lastUpdated; }
    public void setLastUpdated(String v)    { this.lastUpdated = v; }

    public String getSource()               { return source; }
    public void setSource(String v)         { this.source = v; }

    public double getConfidence()           { return confidence; }
    public void setConfidence(double v)     { this.confidence = v; }

    public ContextStatus getStatus()        { return status; }
    public void setStatus(ContextStatus v)  { this.status = v; }

    /** Returns true if the entry has a non-blank value. */
    public boolean hasValue() {
        if (value == null) return false;
        if (value instanceof String s) return !s.isBlank();
        return true;
    }
}
