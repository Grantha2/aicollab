package collab;

// ============================================================
// OrganizationContext.java — Shared organizational context that
// every task button refreshes before execution.
//
// WHAT THIS CLASS DOES (one sentence):
// Holds live organizational data (priorities, roster, metrics,
// deadlines, risks) so every AI output is grounded in current
// organizational reality.
//
// KEY DESIGN DECISIONS:
// - This is a SHARED context block — all buttons read from it.
// - Each field is wrapped in ContextEntry<String> for metadata
//   (freshness, source, confidence, approval status).
// - Backward-compatible: load() detects old format (plain strings)
//   and auto-migrates to ContextEntry wrappers.
// - External API (getters/setters) still uses plain Strings so
//   existing callers (ContextControlDialog, PromptBuilder) work
//   unchanged.
// ============================================================

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class OrganizationContext {

    private static final String FILE_NAME = "org_context.json";

    // --- Default TTLs per field category ---
    private static final Duration TTL_STRATEGIC   = Duration.ofDays(14);
    private static final Duration TTL_OPERATIONAL  = Duration.ofDays(7);
    private static final Duration TTL_URGENT       = Duration.ofDays(3);
    private static final Duration TTL_STABLE       = Duration.ofDays(30);

    private static final Map<String, Duration> FIELD_TTLS = Map.ofEntries(
        Map.entry("lastUpdated",                TTL_OPERATIONAL),
        Map.entry("whatChangedSinceLastUpdate",  TTL_URGENT),
        Map.entry("currentTermDateRange",        TTL_STABLE),
        Map.entry("topPriorities",               TTL_STRATEGIC),
        Map.entry("activeInitiativesAndStatus",  TTL_OPERATIONAL),
        Map.entry("upcomingDeadlinesAndEvents",  TTL_URGENT),
        Map.entry("currentMetrics",              TTL_OPERATIONAL),
        Map.entry("currentBlockersRisks",        TTL_URGENT),
        Map.entry("pendingDecisions",            TTL_URGENT),
        Map.entry("preferredToneStyle",          TTL_STABLE)
    );

    // Human-readable labels for each field
    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
        Map.entry("lastUpdated",                "Last Updated"),
        Map.entry("whatChangedSinceLastUpdate",  "What Changed Since Last Update"),
        Map.entry("currentTermDateRange",        "Current Term / Date Range"),
        Map.entry("topPriorities",               "Top Priorities"),
        Map.entry("activeInitiativesAndStatus",  "Active Initiatives and Status"),
        Map.entry("upcomingDeadlinesAndEvents",  "Upcoming Deadlines and Events"),
        Map.entry("currentMetrics",              "Current Metrics"),
        Map.entry("currentBlockersRisks",        "Current Blockers / Risks"),
        Map.entry("pendingDecisions",            "Pending Decisions"),
        Map.entry("preferredToneStyle",          "Preferred Tone / Style")
    );

    // --- Organization Context Fields (metadata-wrapped) ---
    //
    // NOTE: roster and stakeholder data are intentionally NOT stored here.
    // Agent panel and stakeholders are owned by ProfileSet (structured), and
    // the human org roster is owned by the memberProfiles list below. Two
    // earlier freeform fields ("officerRosterAndOwnership" and
    // "keyPartnersStakeholders") were dropped to eliminate duplication —
    // see the migration in deserialize() / migrateFromOldFormat().
    private ContextEntry<String> lastUpdated                = new ContextEntry<>("");
    private ContextEntry<String> whatChangedSinceLastUpdate  = new ContextEntry<>("");
    private ContextEntry<String> currentTermDateRange        = new ContextEntry<>("");
    private ContextEntry<String> topPriorities               = new ContextEntry<>("");
    private ContextEntry<String> activeInitiativesAndStatus  = new ContextEntry<>("");
    private ContextEntry<String> upcomingDeadlinesAndEvents  = new ContextEntry<>("");
    private ContextEntry<String> currentMetrics              = new ContextEntry<>("");
    private ContextEntry<String> currentBlockersRisks        = new ContextEntry<>("");
    private ContextEntry<String> pendingDecisions            = new ContextEntry<>("");
    private ContextEntry<String> preferredToneStyle          = new ContextEntry<>("");

    // --- Universal Intake Fields (kept as plain strings — low churn, not context-managed) ---
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

    // --- Member/Officer Profiles ---
    private List<MemberProfile> memberProfiles = new ArrayList<>();

    // ===================== Convenience getters (return plain String) =====================

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

    // ===================== Convenience setters (accept plain String, mark as user_edit) =====================

    public void setLastUpdated(String v)                  { setField(lastUpdated, v, "user_edit"); }
    public void setWhatChangedSinceLastUpdate(String v)   { setField(whatChangedSinceLastUpdate, v, "user_edit"); }
    public void setCurrentTermDateRange(String v)         { setField(currentTermDateRange, v, "user_edit"); }
    public void setTopPriorities(String v)                { setField(topPriorities, v, "user_edit"); }
    public void setActiveInitiativesAndStatus(String v)   { setField(activeInitiativesAndStatus, v, "user_edit"); }
    public void setUpcomingDeadlinesAndEvents(String v)   { setField(upcomingDeadlinesAndEvents, v, "user_edit"); }
    public void setCurrentMetrics(String v)               { setField(currentMetrics, v, "user_edit"); }
    public void setCurrentBlockersRisks(String v)         { setField(currentBlockersRisks, v, "user_edit"); }
    public void setPendingDecisions(String v)             { setField(pendingDecisions, v, "user_edit"); }
    public void setPreferredToneStyle(String v)           { setField(preferredToneStyle, v, "user_edit"); }
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

    // ===================== ContextEntry accessors (for agentic/reconciliation layer) =====================

    /** Returns the raw ContextEntry for a field by name. */
    public ContextEntry<String> getEntry(String fieldName) {
        return switch (fieldName) {
            case "lastUpdated"               -> lastUpdated;
            case "whatChangedSinceLastUpdate" -> whatChangedSinceLastUpdate;
            case "currentTermDateRange"       -> currentTermDateRange;
            case "topPriorities"             -> topPriorities;
            case "activeInitiativesAndStatus" -> activeInitiativesAndStatus;
            case "upcomingDeadlinesAndEvents" -> upcomingDeadlinesAndEvents;
            case "currentMetrics"            -> currentMetrics;
            case "currentBlockersRisks"      -> currentBlockersRisks;
            case "pendingDecisions"          -> pendingDecisions;
            case "preferredToneStyle"        -> preferredToneStyle;
            default -> null;
        };
    }

    /** Returns all context field names (the 10 metadata-wrapped fields). */
    public static List<String> getFieldNames() {
        return List.of(
            "lastUpdated", "whatChangedSinceLastUpdate", "currentTermDateRange",
            "topPriorities", "activeInitiativesAndStatus", "upcomingDeadlinesAndEvents",
            "currentMetrics", "currentBlockersRisks", "pendingDecisions", "preferredToneStyle"
        );
    }

    /** Returns the human-readable label for a field name. */
    public static String getFieldLabel(String fieldName) {
        return FIELD_LABELS.getOrDefault(fieldName, fieldName);
    }

    /** Returns the configured TTL for a field. */
    public static Duration getFieldTtl(String fieldName) {
        return FIELD_TTLS.getOrDefault(fieldName, TTL_OPERATIONAL);
    }

    /** Computes freshness for a specific field. */
    public Freshness getFieldFreshness(String fieldName) {
        ContextEntry<String> entry = getEntry(fieldName);
        if (entry == null) return Freshness.NEEDS_CONFIRMATION;
        return entry.computeFreshness(getFieldTtl(fieldName));
    }

    /** Returns a freshness report: fieldName → Freshness for all fields. */
    public Map<String, Freshness> getFreshnessReport() {
        Map<String, Freshness> report = new LinkedHashMap<>();
        for (String name : getFieldNames()) {
            report.put(name, getFieldFreshness(name));
        }
        return report;
    }

    /** Updates a field programmatically with source/confidence metadata. */
    public void updateField(String fieldName, String value, String source, double confidence, ContextStatus status) {
        ContextEntry<String> entry = getEntry(fieldName);
        if (entry != null) {
            entry.setValue(value);
            entry.setSource(source);
            entry.setConfidence(confidence);
            entry.setStatus(status);
        }
    }

    // ===================== Member profiles =====================

    public void addMemberProfile(MemberProfile p) {
        if (memberProfiles == null) memberProfiles = new ArrayList<>();
        memberProfiles.add(p);
    }

    public void removeMemberProfile(int index) {
        if (memberProfiles != null && index >= 0 && index < memberProfiles.size()) {
            memberProfiles.remove(index);
        }
    }

    // ===================== Prompt block builder =====================

    public String buildContextBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ORGANIZATION CONTEXT ===\n");
        appendField(sb, "Last Updated", getLastUpdated());
        appendField(sb, "What Changed Since Last Update", getWhatChangedSinceLastUpdate());
        appendField(sb, "Current Term / Date Range", getCurrentTermDateRange());
        appendField(sb, "Top Priorities", getTopPriorities());
        appendField(sb, "Active Initiatives and Status", getActiveInitiativesAndStatus());
        appendField(sb, "Upcoming Deadlines and Events", getUpcomingDeadlinesAndEvents());
        appendField(sb, "Current Metrics", getCurrentMetrics());
        appendField(sb, "Current Blockers / Risks", getCurrentBlockersRisks());
        appendField(sb, "Pending Decisions", getPendingDecisions());
        appendField(sb, "Preferred Tone / Style", getPreferredToneStyle());

        if (memberProfiles != null && !memberProfiles.isEmpty()) {
            sb.append("\n--- Member/Officer Profiles ---\n");
            for (MemberProfile mp : memberProfiles) {
                sb.append("- ").append(mp.getName());
                if (mp.getRole() != null && !mp.getRole().isBlank()) {
                    sb.append(" (").append(mp.getRole()).append(")");
                }
                if (mp.getDetails() != null && !mp.getDetails().isBlank()) {
                    sb.append(": ").append(mp.getDetails());
                }
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

    private void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }

    // ===================== Persistence with backward-compatible migration =====================

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static OrganizationContext load() {
        return load(Path.of(FILE_NAME));
    }

    public static OrganizationContext load(Path path) {
        if (!Files.exists(path)) {
            return new OrganizationContext();
        }
        try {
            String json = Files.readString(path);
            return deserialize(json);
        } catch (IOException e) {
            System.err.println("[OrganizationContext] Failed to load: " + e.getMessage());
            return new OrganizationContext();
        }
    }

    /**
     * Deserializes from JSON, detecting old format (plain strings) vs new format (ContextEntry objects).
     * Old format: { "topPriorities": "some text", ... }
     * New format: { "topPriorities": { "value": "some text", "lastUpdated": "...", ... }, ... }
     */
    static OrganizationContext deserialize(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        // Detect format by checking if a known field is a string or an object
        boolean isOldFormat = false;
        if (root.has("topPriorities")) {
            JsonElement el = root.get("topPriorities");
            isOldFormat = el.isJsonPrimitive();
        } else if (root.has("lastUpdated")) {
            JsonElement el = root.get("lastUpdated");
            isOldFormat = el.isJsonPrimitive();
        }

        if (isOldFormat) {
            return migrateFromOldFormat(root);
        } else {
            // Also warn when loading new-format JSON that still has the
            // dropped fields lingering (e.g., from before this cleanup).
            warnIfDropped(root, "officerRosterAndOwnership");
            warnIfDropped(root, "keyPartnersStakeholders");
            OrganizationContext ctx = GSON.fromJson(root, OrganizationContext.class);
            if (ctx == null) ctx = new OrganizationContext();
            ctx.ensureNonNull();
            return ctx;
        }
    }

    /**
     * Logs a one-line warning if the loaded JSON still has one of the two
     * dropped fields. Used during the migration off of
     * OrganizationContext.officerRosterAndOwnership and .keyPartnersStakeholders —
     * roster data now lives in {@link #memberProfiles} and stakeholders in
     * {@link ProfileSet#getStakeholders()}.
     */
    private static void warnIfDropped(JsonObject root, String fieldName) {
        if (!root.has(fieldName)) return;
        JsonElement el = root.get(fieldName);
        String value = null;
        if (el.isJsonPrimitive()) {
            value = el.getAsString();
        } else if (el.isJsonObject() && el.getAsJsonObject().has("value")) {
            JsonElement inner = el.getAsJsonObject().get("value");
            if (inner != null && inner.isJsonPrimitive()) {
                value = inner.getAsString();
            }
        }
        if (value != null && !value.isBlank()) {
            System.out.println("[OrganizationContext migration] Dropping '" + fieldName
                    + "' field (has value, will be lost). Move this data into "
                    + "ProfileSet (stakeholders) or memberProfiles (roster) instead.");
        }
    }

    private static OrganizationContext migrateFromOldFormat(JsonObject root) {
        OrganizationContext ctx = new OrganizationContext();

        ctx.lastUpdated                = migrateField(root, "lastUpdated");
        ctx.whatChangedSinceLastUpdate  = migrateField(root, "whatChangedSinceLastUpdate");
        ctx.currentTermDateRange        = migrateField(root, "currentTermDateRange");
        ctx.topPriorities               = migrateField(root, "topPriorities");
        ctx.activeInitiativesAndStatus  = migrateField(root, "activeInitiativesAndStatus");
        ctx.upcomingDeadlinesAndEvents  = migrateField(root, "upcomingDeadlinesAndEvents");
        ctx.currentMetrics              = migrateField(root, "currentMetrics");
        ctx.currentBlockersRisks        = migrateField(root, "currentBlockersRisks");
        ctx.pendingDecisions            = migrateField(root, "pendingDecisions");
        ctx.preferredToneStyle          = migrateField(root, "preferredToneStyle");

        // Dropped fields: officerRosterAndOwnership + keyPartnersStakeholders.
        // These overlapped with ProfileSet.stakeholders and
        // OrganizationContext.memberProfiles respectively. Warn if the old
        // JSON still has non-empty values so the user notices the data move.
        warnIfDropped(root, "officerRosterAndOwnership");
        warnIfDropped(root, "keyPartnersStakeholders");

        // Migrate plain string fields
        ctx.defaultAudience              = getString(root, "defaultAudience");
        ctx.defaultGoal                  = getString(root, "defaultGoal");
        ctx.defaultContext               = getString(root, "defaultContext");
        ctx.defaultDesiredOutcome        = getString(root, "defaultDesiredOutcome");
        ctx.defaultDeadline              = getString(root, "defaultDeadline");
        ctx.defaultTone                  = getString(root, "defaultTone");
        ctx.defaultLength                = getString(root, "defaultLength");
        ctx.defaultMustIncludeDetails    = getString(root, "defaultMustIncludeDetails");
        ctx.defaultAvoidSensitivityNotes = getString(root, "defaultAvoidSensitivityNotes");
        ctx.defaultOutputChannel         = getString(root, "defaultOutputChannel");

        // Migrate member profiles
        if (root.has("memberProfiles") && root.get("memberProfiles").isJsonArray()) {
            ctx.memberProfiles = GSON.fromJson(root.get("memberProfiles"),
                    new TypeToken<List<MemberProfile>>(){}.getType());
        }

        System.out.println("[OrganizationContext] Migrated from old format to ContextEntry format.");
        return ctx;
    }

    private static ContextEntry<String> migrateField(JsonObject root, String fieldName) {
        String value = getString(root, fieldName);
        return ContextEntry.migrate(value);
    }

    private static String getString(JsonObject root, String key) {
        if (root.has(key) && root.get(key).isJsonPrimitive()) {
            return root.get(key).getAsString();
        }
        return "";
    }

    private void ensureNonNull() {
        if (lastUpdated == null) lastUpdated = new ContextEntry<>("");
        if (whatChangedSinceLastUpdate == null) whatChangedSinceLastUpdate = new ContextEntry<>("");
        if (currentTermDateRange == null) currentTermDateRange = new ContextEntry<>("");
        if (topPriorities == null) topPriorities = new ContextEntry<>("");
        if (activeInitiativesAndStatus == null) activeInitiativesAndStatus = new ContextEntry<>("");
        if (upcomingDeadlinesAndEvents == null) upcomingDeadlinesAndEvents = new ContextEntry<>("");
        if (currentMetrics == null) currentMetrics = new ContextEntry<>("");
        if (currentBlockersRisks == null) currentBlockersRisks = new ContextEntry<>("");
        if (pendingDecisions == null) pendingDecisions = new ContextEntry<>("");
        if (preferredToneStyle == null) preferredToneStyle = new ContextEntry<>("");
        if (memberProfiles == null) memberProfiles = new ArrayList<>();
    }

    public void save() {
        save(Path.of(FILE_NAME));
    }

    public void save(Path path) {
        try {
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[OrganizationContext] Failed to save: " + e.getMessage());
        }
    }

    // ===================== Helpers =====================

    private static String val(ContextEntry<String> entry) {
        if (entry == null || entry.getValue() == null) return "";
        return entry.getValue();
    }

    private static void setField(ContextEntry<String> entry, String value, String source) {
        if (entry != null) {
            entry.setValue(value != null ? value : "");
            entry.setSource(source);
            entry.setConfidence(1.0);
            entry.setStatus(ContextStatus.APPROVED);
        }
    }

    // ===================== MemberProfile inner class =====================

    public static class MemberProfile {
        private String name;
        private String role;
        private String details;
        private String skills;
        private String availability;

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
