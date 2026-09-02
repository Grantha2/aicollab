package cowork.context;

import java.time.Duration;
import java.time.Instant;

/**
 * A context value plus its metadata: when it was last updated, where it came from, how
 * confident the producer was, and its approval status. Freshness is derived on demand from
 * lastUpdated against a caller-supplied TTL. lastUpdated is kept as an ISO-8601 string so
 * Gson round-trips it without a type adapter.
 */
public class ContextEntry<T> {

    private T value;
    private String lastUpdated;
    private String source;        // "user_edit", "daily_update", "workflow_output", "import"
    private double confidence;    // 0.0-1.0
    private ContextStatus status;

    public ContextEntry() {
        this(null);
    }

    public ContextEntry(T value) {
        this(value, "user_edit", 1.0, ContextStatus.APPROVED);
    }

    public ContextEntry(T value, String source, double confidence, ContextStatus status) {
        this.value = value;
        this.lastUpdated = Instant.now().toString();
        this.source = source;
        this.confidence = confidence;
        this.status = status;
    }

    public Freshness computeFreshness(Duration ttl) {
        Duration age = Duration.between(getLastUpdatedInstant(), Instant.now());
        if (age.isNegative()) return Freshness.FRESH;
        long ageMillis = age.toMillis();
        long ttlMillis = ttl.toMillis();
        if (ageMillis < ttlMillis / 2)        return Freshness.FRESH;
        if (ageMillis < (ttlMillis * 4) / 5)  return Freshness.AGING;
        if (ageMillis < ttlMillis)            return Freshness.STALE;
        return Freshness.NEEDS_CONFIRMATION;
    }

    /** Unparseable timestamps read as EPOCH so the entry surfaces as NEEDS_CONFIRMATION. */
    public Instant getLastUpdatedInstant() {
        try {
            return Instant.parse(lastUpdated);
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    public T getValue() { return value; }

    /** Setting a value stamps lastUpdated with the current time. */
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

    /** True when the value is non-null and, for strings, non-blank. */
    public boolean hasValue() {
        if (value == null) return false;
        if (value instanceof String s) return !s.isBlank();
        return true;
    }
}
