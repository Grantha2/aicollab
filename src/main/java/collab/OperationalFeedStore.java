package collab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class OperationalFeedStore {

    private static final String FILE_NAME = "operational_feeds.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path filePath;
    private List<OperationalFeedItem> items;

    public OperationalFeedStore() { this(Path.of(FILE_NAME)); }
    public OperationalFeedStore(Path filePath) { this.filePath = filePath; load(); }

    public List<OperationalFeedItem> getAll() { return Collections.unmodifiableList(items); }

    /** Returns upcoming items within the next N days, sorted by date. */
    public List<OperationalFeedItem> getUpcoming(int days) {
        LocalDate cutoff = LocalDate.now().plusDays(days);
        return items.stream()
            .filter(i -> i.getFeedStatus() == OperationalFeedItem.FeedStatus.UPCOMING)
            .filter(i -> !i.getLocalDate().isAfter(cutoff))
            .sorted(Comparator.comparing(OperationalFeedItem::getLocalDate))
            .collect(Collectors.toList());
    }

    /** Returns overdue items (date in past, still UPCOMING status). */
    public List<OperationalFeedItem> getOverdue() {
        return items.stream()
            .filter(OperationalFeedItem::isOverdue)
            .sorted(Comparator.comparing(OperationalFeedItem::getLocalDate))
            .collect(Collectors.toList());
    }

    /** Returns items of type MEETING within the next N days. */
    public List<OperationalFeedItem> getUpcomingMeetings(int days) {
        return getUpcoming(days).stream()
            .filter(i -> i.getFeedType() == OperationalFeedItem.FeedType.MEETING)
            .collect(Collectors.toList());
    }

    public void addItem(OperationalFeedItem item) {
        if (item.getId() == null || item.getId().isBlank()) {
            item.setId(java.util.UUID.randomUUID().toString());
        }
        items.add(item);
        save();
    }

    public void markComplete(String id) {
        items.stream()
            .filter(i -> i.getId().equals(id))
            .findFirst()
            .ifPresent(i -> i.setStatus(OperationalFeedItem.FeedStatus.COMPLETED.name()));
        save();
    }

    public void remove(String id) {
        items.removeIf(i -> i.getId().equals(id));
        save();
    }

    private void load() {
        if (!Files.exists(filePath)) { items = new ArrayList<>(); return; }
        try {
            String json = Files.readString(filePath);
            items = GSON.fromJson(json, new TypeToken<List<OperationalFeedItem>>(){}.getType());
            if (items == null) items = new ArrayList<>();
        } catch (IOException e) {
            System.err.println("[OperationalFeedStore] Failed to load: " + e.getMessage());
            items = new ArrayList<>();
        }
    }

    public void save() {
        try { Files.writeString(filePath, GSON.toJson(items)); }
        catch (IOException e) { System.err.println("[OperationalFeedStore] Failed to save: " + e.getMessage()); }
    }
}
