package cowork.context;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** computeFreshness() thresholds against a fixed 10-day TTL, plus the timestamp edge cases. */
class ContextEntryTest {

    private static final Duration TTL = Duration.ofDays(10);

    private static ContextEntry<String> agedBy(Duration age) {
        ContextEntry<String> entry = new ContextEntry<>("value");
        entry.setLastUpdated(Instant.now().minus(age).toString());
        return entry;
    }

    @Test
    void freshBelowHalfOfTtl() {
        assertEquals(Freshness.FRESH, new ContextEntry<>("just set").computeFreshness(TTL));
        assertEquals(Freshness.FRESH, agedBy(Duration.ofDays(4)).computeFreshness(TTL));
    }

    @Test
    void agingFromHalfToFourFifthsOfTtl() {
        assertEquals(Freshness.AGING, agedBy(Duration.ofDays(5)).computeFreshness(TTL));
        assertEquals(Freshness.AGING, agedBy(Duration.ofDays(7)).computeFreshness(TTL));
    }

    @Test
    void staleFromFourFifthsUpToTtl() {
        assertEquals(Freshness.STALE, agedBy(Duration.ofDays(8)).computeFreshness(TTL));
        assertEquals(Freshness.STALE, agedBy(Duration.ofDays(9)).computeFreshness(TTL));
    }

    @Test
    void needsConfirmationAtOrBeyondTtl() {
        assertEquals(Freshness.NEEDS_CONFIRMATION, agedBy(Duration.ofDays(10)).computeFreshness(TTL));
        assertEquals(Freshness.NEEDS_CONFIRMATION, agedBy(Duration.ofDays(40)).computeFreshness(TTL));
    }

    @Test
    void futureTimestampIsTreatedAsFresh() {
        assertEquals(Freshness.FRESH, agedBy(Duration.ofDays(-2)).computeFreshness(TTL));
    }

    @Test
    void unparseableTimestampNeedsConfirmation() {
        ContextEntry<String> entry = new ContextEntry<>("value");
        entry.setLastUpdated("not-a-timestamp");
        assertEquals(Instant.EPOCH, entry.getLastUpdatedInstant());
        assertEquals(Freshness.NEEDS_CONFIRMATION, entry.computeFreshness(TTL));
    }

    @Test
    void setValueRestampsLastUpdated() {
        ContextEntry<String> entry = agedBy(Duration.ofDays(9));
        entry.setValue("refreshed");
        assertEquals(Freshness.FRESH, entry.computeFreshness(TTL));
    }

    @Test
    void hasValueRejectsNullAndBlank() {
        assertFalse(new ContextEntry<String>().hasValue());
        assertFalse(new ContextEntry<>("   ").hasValue());
        assertTrue(new ContextEntry<>("x").hasValue());
        assertTrue(new ContextEntry<>(42).hasValue());
    }
}
