package collab;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * A structured operational feed item: event, meeting, deadline, or task.
 */
public class OperationalFeedItem {

    public enum FeedType { EVENT, MEETING, DEADLINE, TASK }
    public enum FeedStatus { UPCOMING, COMPLETED, OVERDUE }

    private String id;
    private String title;
    private String type;     // stored as string for Gson compatibility
    private String date;     // ISO local date: "2026-04-10"
    private String time;     // optional: "14:00"
    private String owner;
    private String attendees;
    private String notes;
    private String status;   // stored as string for Gson compatibility

    public OperationalFeedItem() {
        this.status = FeedStatus.UPCOMING.name();
    }

    public OperationalFeedItem(String id, String title, FeedType type, String date) {
        this.id = id;
        this.title = title;
        this.type = type.name();
        this.date = date;
        this.status = FeedStatus.UPCOMING.name();
    }

    public String getId()        { return id; }
    public String getTitle()     { return title; }
    public String getType()      { return type; }
    public String getDate()      { return date; }
    public String getTime()      { return time; }
    public String getOwner()     { return owner; }
    public String getAttendees() { return attendees; }
    public String getNotes()     { return notes; }
    public String getStatus()    { return status; }

    public void setId(String v)        { this.id = v; }
    public void setTitle(String v)     { this.title = v; }
    public void setType(String v)      { this.type = v; }
    public void setDate(String v)      { this.date = v; }
    public void setTime(String v)      { this.time = v; }
    public void setOwner(String v)     { this.owner = v; }
    public void setAttendees(String v) { this.attendees = v; }
    public void setNotes(String v)     { this.notes = v; }
    public void setStatus(String v)    { this.status = v; }

    public FeedType getFeedType() {
        try { return FeedType.valueOf(type); }
        catch (Exception e) { return FeedType.EVENT; }
    }

    public FeedStatus getFeedStatus() {
        try { return FeedStatus.valueOf(status); }
        catch (Exception e) { return FeedStatus.UPCOMING; }
    }

    public LocalDate getLocalDate() {
        try { return LocalDate.parse(date); }
        catch (DateTimeParseException | NullPointerException e) { return LocalDate.MAX; }
    }

    public boolean isOverdue() {
        return getFeedStatus() == FeedStatus.UPCOMING && getLocalDate().isBefore(LocalDate.now());
    }

    public String toDisplayString() {
        String icon = switch (getFeedType()) {
            case MEETING -> "[MTG]";
            case DEADLINE -> "[DUE]";
            case TASK -> "[TSK]";
            case EVENT -> "[EVT]";
        };
        return icon + " " + title + " — " + (date != null ? date : "no date");
    }
}
