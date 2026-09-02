package cowork.context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cowork.config.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Shared organisational context every task reads before calling the model. Ten narrative fields
 * are ContextEntry<String> (value plus freshness/source/confidence/status); intake defaults and
 * member profiles are plain data. Persisted as org_context.json in the app data directory.
 */
public class OrganizationContext {

    private static final String FILE_NAME = "org_context.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Duration TTL_STRATEGIC   = Duration.ofDays(14);
    private static final Duration TTL_OPERATIONAL = Duration.ofDays(7);
    private static final Duration TTL_URGENT      = Duration.ofDays(3);
    private static final Duration TTL_STABLE      = Duration.ofDays(30);
    // Single source of truth for field order, prompt label and TTL.
    private record FieldSpec(String name, String label, Duration ttl) {}
    private static final List<FieldSpec> FIELDS = List.of(
        new FieldSpec("lastUpdated",                "Last Updated",                   TTL_OPERATIONAL),
        new FieldSpec("whatChangedSinceLastUpdate", "What Changed Since Last Update", TTL_URGENT),
        new FieldSpec("currentTermDateRange",       "Current Term / Date Range",      TTL_STABLE),
        new FieldSpec("topPriorities",              "Top Priorities",                 TTL_STRATEGIC),
        new FieldSpec("activeInitiativesAndStatus", "Active Initiatives and Status",  TTL_OPERATIONAL),
        new FieldSpec("upcomingDeadlinesAndEvents", "Upcoming Deadlines and Events",  TTL_URGENT),
        new FieldSpec("currentMetrics",             "Current Metrics",                TTL_OPERATIONAL),
        new FieldSpec("currentBlockersRisks",       "Current Blockers / Risks",       TTL_URGENT),
        new FieldSpec("pendingDecisions",           "Pending Decisions",              TTL_URGENT),
        new FieldSpec("preferredToneStyle",         "Preferred Tone / Style",         TTL_STABLE));
    private static final List<String> FIELD_NAMES = FIELDS.stream().map(FieldSpec::name).toList();
    private static final Map<String, Duration> FIELD_TTLS =
        FIELDS.stream().collect(Collectors.toMap(FieldSpec::name, FieldSpec::ttl));
    private static final Map<String, String> FIELD_LABELS =
        FIELDS.stream().collect(Collectors.toMap(FieldSpec::name, FieldSpec::label));

    private ContextEntry<String> lastUpdated                = new ContextEntry<>("");
    private ContextEntry<String> whatChangedSinceLastUpdate = new ContextEntry<>("");
    private ContextEntry<String> currentTermDateRange       = new ContextEntry<>("");
    private ContextEntry<String> topPriorities              = new ContextEntry<>("");
    private ContextEntry<String> activeInitiativesAndStatus = new ContextEntry<>("");
    private ContextEntry<String> upcomingDeadlinesAndEvents = new ContextEntry<>("");
    private ContextEntry<String> currentMetrics             = new ContextEntry<>("");
    private ContextEntry<String> currentBlockersRisks       = new ContextEntry<>("");
    private ContextEntry<String> pendingDecisions           = new ContextEntry<>("");
    private ContextEntry<String> preferredToneStyle         = new ContextEntry<>("");

    // Universal intake defaults: low churn, so plain strings rather than ContextEntry.
    private String defaultAudience = "";
    private String defaultGoal = "";
    private String defaultContext = "";
    private String defaultDesiredOutcome = "";
    private String defaultDeadline = "";
    private String defaultTone = "";
    private String defaultLength = "";
    private String defaultMustIncludeDetails = "";
    private String defaultAvoidSensitivityNotes = "";
    private String defaultOutputChannel = "";
    private List<MemberProfile> memberProfiles = new ArrayList<>();

    public String getLastUpdated()                  { return val(lastUpdated); }
    public String getWhatChangedSinceLastUpdate()   { return val(whatChangedSinceLastUpdate); }
    public String getCurrentTermDateRange()         { return val(currentTermDateRange); }
    public String getTopPriorities()                { return val(topPriorities); }
    public String getActiveInitiativesAndStatus()   { return val(activeInitiativesAndStatus); }
    public String getUpcomingDeadlinesAndEvents()   { return val(upcomingDeadlinesAndEvents); }
    public String getCurrentMetrics()               { return val(currentMetrics); }
    public String getCurrentBlockersRisks()         { return val(currentBlockersRisks); }
    public String getPendingDecisions()             { return val(pendingDecisions); }
    public String getPreferredToneStyle()           { return val(preferredToneStyle); }
    public String getDefaultAudience()              { return defaultAudience; }
    public String getDefaultGoal()                  { return defaultGoal; }
    public String getDefaultContext()               { return defaultContext; }
    public String getDefaultDesiredOutcome()        { return defaultDesiredOutcome; }
    public String getDefaultDeadline()              { return defaultDeadline; }
    public String getDefaultTone()                  { return defaultTone; }
    public String getDefaultLength()                { return defaultLength; }
    public String getDefaultMustIncludeDetails()    { return defaultMustIncludeDetails; }
    public String getDefaultAvoidSensitivityNotes() { return defaultAvoidSensitivityNotes; }
    public String getDefaultOutputChannel()         { return defaultOutputChannel; }
    public List<MemberProfile> getMemberProfiles()  { return memberProfiles; }

    // Plain-string setters record a direct user edit: source user_edit, confidence 1.0, APPROVED.
    public void setLastUpdated(String v)                  { userEdit(lastUpdated, v); }
    public void setWhatChangedSinceLastUpdate(String v)   { userEdit(whatChangedSinceLastUpdate, v); }
    public void setCurrentTermDateRange(String v)         { userEdit(currentTermDateRange, v); }
    public void setTopPriorities(String v)                { userEdit(topPriorities, v); }
    public void setActiveInitiativesAndStatus(String v)   { userEdit(activeInitiativesAndStatus, v); }
    public void setUpcomingDeadlinesAndEvents(String v)   { userEdit(upcomingDeadlinesAndEvents, v); }
    public void setCurrentMetrics(String v)               { userEdit(currentMetrics, v); }
    public void setCurrentBlockersRisks(String v)         { userEdit(currentBlockersRisks, v); }
    public void setPendingDecisions(String v)             { userEdit(pendingDecisions, v); }
    public void setPreferredToneStyle(String v)           { userEdit(preferredToneStyle, v); }
    public void setDefaultAudience(String v)              { this.defaultAudience = v; }
    public void setDefaultGoal(String v)                  { this.defaultGoal = v; }
    public void setDefaultContext(String v)               { this.defaultContext = v; }
    public void setDefaultDesiredOutcome(String v)        { this.defaultDesiredOutcome = v; }
    public void setDefaultDeadline(String v)              { this.defaultDeadline = v; }
    public void setDefaultTone(String v)                  { this.defaultTone = v; }
    public void setDefaultLength(String v)                { this.defaultLength = v; }
    public void setDefaultMustIncludeDetails(String v)    { this.defaultMustIncludeDetails = v; }
    public void setDefaultAvoidSensitivityNotes(String v) { this.defaultAvoidSensitivityNotes = v; }
    public void setDefaultOutputChannel(String v)         { this.defaultOutputChannel = v; }
    public void setMemberProfiles(List<MemberProfile> v)  { this.memberProfiles = v != null ? v : new ArrayList<>(); }

    public ContextEntry<String> getEntry(String fieldName) {
        return switch (fieldName) {
            case "lastUpdated"                -> lastUpdated;
            case "whatChangedSinceLastUpdate" -> whatChangedSinceLastUpdate;
            case "currentTermDateRange"       -> currentTermDateRange;
            case "topPriorities"              -> topPriorities;
            case "activeInitiativesAndStatus" -> activeInitiativesAndStatus;
            case "upcomingDeadlinesAndEvents" -> upcomingDeadlinesAndEvents;
            case "currentMetrics"             -> currentMetrics;
            case "currentBlockersRisks"       -> currentBlockersRisks;
            case "pendingDecisions"           -> pendingDecisions;
            case "preferredToneStyle"         -> preferredToneStyle;
            default -> null;
        };
    }

    public static List<String> getFieldNames()           { return FIELD_NAMES; }
    public static String getFieldLabel(String fieldName) { return FIELD_LABELS.getOrDefault(fieldName, fieldName); }
    public static Duration getFieldTtl(String fieldName) { return FIELD_TTLS.getOrDefault(fieldName, TTL_OPERATIONAL); }

    public Freshness getFieldFreshness(String fieldName) {
        ContextEntry<String> entry = getEntry(fieldName);
        return entry == null ? Freshness.NEEDS_CONFIRMATION : entry.computeFreshness(getFieldTtl(fieldName));
    }

    public Map<String, Freshness> getFreshnessReport() {
        Map<String, Freshness> report = new LinkedHashMap<>();
        for (String name : FIELD_NAMES) report.put(name, getFieldFreshness(name));
        return report;
    }

    public void updateField(String fieldName, String value, String source, double confidence, ContextStatus status) {
        ContextEntry<String> entry = getEntry(fieldName);
        if (entry == null) return;
        entry.setValue(value);
        entry.setSource(source);
        entry.setConfidence(confidence);
        entry.setStatus(status);
    }

    public void addMemberProfile(MemberProfile p) { memberProfiles.add(p); }
    public void removeMemberProfile(int i) { if (i >= 0 && i < memberProfiles.size()) memberProfiles.remove(i); }

    /** Renders the prompt block; blank fields are omitted so the model only sees real data. */
    public String buildContextBlock() {
        StringBuilder sb = new StringBuilder("=== ORGANIZATION CONTEXT ===\n");
        for (String name : FIELD_NAMES) appendField(sb, getFieldLabel(name), val(getEntry(name)));
        if (!memberProfiles.isEmpty()) {
            sb.append("\n--- Member/Officer Profiles ---\n");
            for (MemberProfile mp : memberProfiles) {
                sb.append("- ").append(mp.getName());
                if (mp.getRole() != null && !mp.getRole().isBlank()) sb.append(" (").append(mp.getRole()).append(")");
                if (mp.getDetails() != null && !mp.getDetails().isBlank()) sb.append(": ").append(mp.getDetails());
                sb.append("\n");
            }
        }
        sb.append("\n--- Universal Intake Defaults ---\n");
        appendField(sb, "Default Audience", defaultAudience);
        appendField(sb, "Default Goal", defaultGoal);
        appendField(sb, "Default Context", defaultContext);
        appendField(sb, "Default Desired Outcome", defaultDesiredOutcome);
        appendField(sb, "Default Deadline", defaultDeadline);
        appendField(sb, "Default Tone", defaultTone);
        appendField(sb, "Default Length", defaultLength);
        appendField(sb, "Default Must-Include Details", defaultMustIncludeDetails);
        appendField(sb, "Default Avoid / Sensitivity Notes", defaultAvoidSensitivityNotes);
        appendField(sb, "Default Output Channel", defaultOutputChannel);
        return sb.toString();
    }

    public static OrganizationContext load() { return load(AppPaths.data(FILE_NAME)); }
    public static OrganizationContext load(Path path) {
        if (!Files.exists(path)) return new OrganizationContext();
        try {
            OrganizationContext ctx = GSON.fromJson(Files.readString(path), OrganizationContext.class);
            return (ctx == null ? new OrganizationContext() : ctx).ensureNonNull();
        } catch (IOException e) {
            System.err.println("[OrganizationContext] Failed to load: " + e.getMessage());
            return new OrganizationContext();
        }
    }

    public void save() { save(AppPaths.data(FILE_NAME)); }
    public void save(Path path) {
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[OrganizationContext] Failed to save: " + e.getMessage());
        }
    }

    // Gson writes explicit JSON nulls straight into fields; restore the never-null invariant.
    private OrganizationContext ensureNonNull() {
        if (lastUpdated == null)                lastUpdated = new ContextEntry<>("");
        if (whatChangedSinceLastUpdate == null) whatChangedSinceLastUpdate = new ContextEntry<>("");
        if (currentTermDateRange == null)       currentTermDateRange = new ContextEntry<>("");
        if (topPriorities == null)              topPriorities = new ContextEntry<>("");
        if (activeInitiativesAndStatus == null) activeInitiativesAndStatus = new ContextEntry<>("");
        if (upcomingDeadlinesAndEvents == null) upcomingDeadlinesAndEvents = new ContextEntry<>("");
        if (currentMetrics == null)             currentMetrics = new ContextEntry<>("");
        if (currentBlockersRisks == null)       currentBlockersRisks = new ContextEntry<>("");
        if (pendingDecisions == null)           pendingDecisions = new ContextEntry<>("");
        if (preferredToneStyle == null)         preferredToneStyle = new ContextEntry<>("");
        if (memberProfiles == null)             memberProfiles = new ArrayList<>();
        return this;
    }

    private static String val(ContextEntry<String> entry) {
        return entry == null || entry.getValue() == null ? "" : entry.getValue();
    }

    private static void userEdit(ContextEntry<String> entry, String value) {
        entry.setValue(value != null ? value : "");
        entry.setSource("user_edit");
        entry.setConfidence(1.0);
        entry.setStatus(ContextStatus.APPROVED);
    }

    private static void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) sb.append(label).append(": ").append(value).append("\n");
    }

    public static class MemberProfile {
        private String name, role, details, skills, availability;

        public MemberProfile() {}
        public MemberProfile(String name, String role, String details) {
            this.name = name;
            this.role = role;
            this.details = details;
        }

        public String getName()         { return name; }
        public String getRole()         { return role; }
        public String getDetails()      { return details; }
        public String getSkills()       { return skills; }
        public String getAvailability() { return availability; }
        public void setName(String v)         { this.name = v; }
        public void setRole(String v)         { this.role = v; }
        public void setDetails(String v)      { this.details = v; }
        public void setSkills(String v)       { this.skills = v; }
        public void setAvailability(String v) { this.availability = v; }
    }
}
