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
// KEY DESIGN DECISION:
// This is a SHARED context block — all buttons read from it and
// the "Refresh Organization Context" flow updates it. It persists
// to org_context.json alongside buttons.json.
// ============================================================

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class OrganizationContext {

    private static final String FILE_NAME = "org_context.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- Organization Context Fields ---
    private String lastUpdated = "";
    private String whatChangedSinceLastUpdate = "";
    private String currentTermDateRange = "";
    private String topPriorities = "";
    private String activeInitiativesAndStatus = "";
    private String upcomingDeadlinesAndEvents = "";
    private String currentMetrics = "";
    private String officerRosterAndOwnership = "";
    private String keyPartnersStakeholders = "";
    private String currentBlockersRisks = "";
    private String pendingDecisions = "";
    private String preferredToneStyle = "";

    // --- Universal Intake Fields ---
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

    // Getters
    public String getLastUpdated()                  { return lastUpdated; }
    public String getWhatChangedSinceLastUpdate()   { return whatChangedSinceLastUpdate; }
    public String getCurrentTermDateRange()         { return currentTermDateRange; }
    public String getTopPriorities()                { return topPriorities; }
    public String getActiveInitiativesAndStatus()   { return activeInitiativesAndStatus; }
    public String getUpcomingDeadlinesAndEvents()   { return upcomingDeadlinesAndEvents; }
    public String getCurrentMetrics()               { return currentMetrics; }
    public String getOfficerRosterAndOwnership()    { return officerRosterAndOwnership; }
    public String getKeyPartnersStakeholders()      { return keyPartnersStakeholders; }
    public String getCurrentBlockersRisks()         { return currentBlockersRisks; }
    public String getPendingDecisions()             { return pendingDecisions; }
    public String getPreferredToneStyle()           { return preferredToneStyle; }
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

    // Setters
    public void setLastUpdated(String v)                  { this.lastUpdated = v; }
    public void setWhatChangedSinceLastUpdate(String v)   { this.whatChangedSinceLastUpdate = v; }
    public void setCurrentTermDateRange(String v)         { this.currentTermDateRange = v; }
    public void setTopPriorities(String v)                { this.topPriorities = v; }
    public void setActiveInitiativesAndStatus(String v)   { this.activeInitiativesAndStatus = v; }
    public void setUpcomingDeadlinesAndEvents(String v)   { this.upcomingDeadlinesAndEvents = v; }
    public void setCurrentMetrics(String v)               { this.currentMetrics = v; }
    public void setOfficerRosterAndOwnership(String v)    { this.officerRosterAndOwnership = v; }
    public void setKeyPartnersStakeholders(String v)      { this.keyPartnersStakeholders = v; }
    public void setCurrentBlockersRisks(String v)         { this.currentBlockersRisks = v; }
    public void setPendingDecisions(String v)             { this.pendingDecisions = v; }
    public void setPreferredToneStyle(String v)           { this.preferredToneStyle = v; }
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

    public void addMemberProfile(MemberProfile p) {
        if (memberProfiles == null) memberProfiles = new ArrayList<>();
        memberProfiles.add(p);
    }

    public void removeMemberProfile(int index) {
        if (memberProfiles != null && index >= 0 && index < memberProfiles.size()) {
            memberProfiles.remove(index);
        }
    }

    /**
     * Builds the organization context block appended to every task prompt.
     */
    public String buildContextBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ORGANIZATION CONTEXT ===\n");
        appendField(sb, "Last Updated", lastUpdated);
        appendField(sb, "What Changed Since Last Update", whatChangedSinceLastUpdate);
        appendField(sb, "Current Term / Date Range", currentTermDateRange);
        appendField(sb, "Top Priorities", topPriorities);
        appendField(sb, "Active Initiatives and Status", activeInitiativesAndStatus);
        appendField(sb, "Upcoming Deadlines and Events", upcomingDeadlinesAndEvents);
        appendField(sb, "Current Metrics", currentMetrics);
        appendField(sb, "Officer Roster and Ownership", officerRosterAndOwnership);
        appendField(sb, "Key Partners / Stakeholders", keyPartnersStakeholders);
        appendField(sb, "Current Blockers / Risks", currentBlockersRisks);
        appendField(sb, "Pending Decisions", pendingDecisions);
        appendField(sb, "Preferred Tone / Style", preferredToneStyle);

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

    // --- Persistence ---

    public static OrganizationContext load() {
        return load(Path.of(FILE_NAME));
    }

    public static OrganizationContext load(Path path) {
        if (!Files.exists(path)) {
            return new OrganizationContext();
        }
        try {
            String json = Files.readString(path);
            OrganizationContext ctx = GSON.fromJson(json, OrganizationContext.class);
            return ctx != null ? ctx : new OrganizationContext();
        } catch (IOException e) {
            System.err.println("[OrganizationContext] Failed to load: " + e.getMessage());
            return new OrganizationContext();
        }
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

    /**
     * A member/officer profile entry within the organization context.
     */
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
