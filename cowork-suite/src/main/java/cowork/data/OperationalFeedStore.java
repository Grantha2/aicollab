package cowork.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import cowork.config.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JSON-backed list of OperationalFeedItems (operational_feeds.json). Loaded once at
 * construction; every mutation writes straight through so there is no dirty state to lose.
 */
public class OperationalFeedStore {

    private static final String FILE_NAME = "operational_feeds.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TypeToken<List<OperationalFeedItem>> LIST_TYPE = new TypeToken<>() {};

    private final Path filePath;
    private List<OperationalFeedItem> items;

    public OperationalFeedStore() { this(AppPaths.data(FILE_NAME)); }

    public OperationalFeedStore(Path filePath) {
        this.filePath = filePath;
        load();
    }

    public List<OperationalFeedItem> getAll() { return Collections.unmodifiableList(items); }

    /** UPCOMING items dated today through today+days, sorted by date. Past-due items are excluded. */
    public List<OperationalFeedItem> getUpcoming(int days) {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(days);
        return items.stream()
            .filter(i -> i.getFeedStatus() == OperationalFeedItem.FeedStatus.UPCOMING)
            .filter(i -> !i.getLocalDate().isBefore(today) && !i.getLocalDate().isAfter(cutoff))
            .sorted(Comparator.comparing(OperationalFeedItem::getLocalDate))
            .collect(Collectors.toList());
    }

    /** Items dated in the past that are still UPCOMING, sorted by date. */
    public List<OperationalFeedItem> getOverdue() {
        return items.stream()
            .filter(OperationalFeedItem::isOverdue)
            .sorted(Comparator.comparing(OperationalFeedItem::getLocalDate))
            .collect(Collectors.toList());
    }

    public List<OperationalFeedItem> getUpcomingMeetings(int days) {
        return getUpcoming(days).stream()
            .filter(i -> i.getFeedType() == OperationalFeedItem.FeedType.MEETING)
            .collect(Collectors.toList());
    }

    public void addItem(OperationalFeedItem item) {
        if (item.getId() == null || item.getId().isBlank()) item.setId(UUID.randomUUID().toString());
        items.add(item);
        save();
    }

    public void markComplete(String id) {
        items.stream().filter(i -> Objects.equals(i.getId(), id)).findFirst()
            .ifPresent(i -> i.setStatus(OperationalFeedItem.FeedStatus.COMPLETED.name()));
        save();
    }

    public void remove(String id) {
        items.removeIf(i -> Objects.equals(i.getId(), id));
        save();
    }

    private void load() {
        items = new ArrayList<>();
        if (!Files.exists(filePath)) return;
        try {
            List<OperationalFeedItem> loaded = GSON.fromJson(Files.readString(filePath), LIST_TYPE);
            if (loaded != null) items = loaded;
        } catch (IOException e) {
            System.err.println("[OperationalFeedStore] Failed to load: " + e.getMessage());
        }
    }

    public void save() {
        try {
            if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, GSON.toJson(items));
        } catch (IOException e) {
            System.err.println("[OperationalFeedStore] Failed to save: " + e.getMessage());
        }
    }
}
