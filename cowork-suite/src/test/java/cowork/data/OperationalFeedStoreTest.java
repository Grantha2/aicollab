package cowork.data;

import cowork.data.OperationalFeedItem.FeedStatus;
import cowork.data.OperationalFeedItem.FeedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Date-window queries (getUpcoming / getOverdue / getUpcomingMeetings) and write-through persistence. */
class OperationalFeedStoreTest {

    @TempDir Path tempDir;

    private Path file;
    private OperationalFeedStore store;

    @BeforeEach
    void setUp() {
        System.setProperty("cowork.home", tempDir.toString());
        file = tempDir.resolve("operational_feeds.json");
        store = new OperationalFeedStore(file);
        LocalDate today = LocalDate.now();
        store.addItem(new OperationalFeedItem("late", "Grant report", FeedType.DEADLINE,
            today.minusDays(1).toString()));
        store.addItem(new OperationalFeedItem("soon", "Draft agenda", FeedType.TASK, today.plusDays(2).toString()));
        store.addItem(new OperationalFeedItem("today", "Board sync", FeedType.MEETING, today.toString()));
        store.addItem(new OperationalFeedItem("far", "Gala", FeedType.EVENT, today.plusDays(10).toString()));
        OperationalFeedItem done = new OperationalFeedItem("done", "Old task", FeedType.TASK,
            today.minusDays(3).toString());
        done.setStatus(FeedStatus.COMPLETED.name());
        store.addItem(done);
        store.addItem(new OperationalFeedItem("undated", "Someday", FeedType.TASK, null));
    }

    private static List<String> ids(List<OperationalFeedItem> items) {
        return items.stream().map(OperationalFeedItem::getId).toList();
    }

    private OperationalFeedItem find(String id) {
        return store.getAll().stream().filter(i -> id.equals(i.getId())).findFirst().orElseThrow();
    }

    @Test
    void upcomingCoversTodayThroughWindowSortedByDate() {
        assertEquals(List.of("today", "soon"), ids(store.getUpcoming(3)));
        assertEquals(List.of("today", "soon", "far"), ids(store.getUpcoming(10)));
        assertEquals(List.of("today"), ids(store.getUpcoming(0)));
    }

    @Test
    void overdueIsPastDatedAndStillUpcoming() {
        assertEquals(List.of("late"), ids(store.getOverdue()));
        assertFalse(find("done").isOverdue());
    }

    @Test
    void upcomingMeetingsFiltersByType() {
        assertEquals(List.of("today"), ids(store.getUpcomingMeetings(1)));
        assertTrue(store.getUpcomingMeetings(0).stream().allMatch(i -> i.getFeedType() == FeedType.MEETING));
    }

    @Test
    void markCompleteClearsOverdue() {
        store.markComplete("late");
        assertTrue(store.getOverdue().isEmpty());
        assertEquals(FeedStatus.COMPLETED, new OperationalFeedStore(file).getAll().stream()
            .filter(i -> i.getId().equals("late")).findFirst().orElseThrow().getFeedStatus());
    }

    @Test
    void mutationsWriteThroughToDisk() {
        store.remove("far");
        OperationalFeedStore reloaded = new OperationalFeedStore(file);
        assertEquals(5, reloaded.getAll().size());
        assertTrue(reloaded.getAll().stream().noneMatch(i -> i.getId().equals("far")));
    }

    @Test
    void addItemAssignsIdWhenBlank() {
        OperationalFeedItem item = new OperationalFeedItem();
        item.setTitle("No id yet");
        store.addItem(item);
        assertNotNull(item.getId());
        assertFalse(item.getId().isBlank());
    }

    @Test
    void defaultConstructorReadsFromAppDataDirectory() {
        assertTrue(Files.exists(tempDir.resolve("operational_feeds.json")));
        assertEquals(6, new OperationalFeedStore().getAll().size());
    }

    @Test
    void displayStringHandlesMissingDate() {
        OperationalFeedItem undated = find("undated");
        assertEquals("[TSK] Someday — no date", undated.toDisplayString());
        assertEquals(LocalDate.MAX, undated.getLocalDate());
    }
}
