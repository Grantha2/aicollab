package collab;

// ============================================================
// StakeholderProfile.java — Holds one human team member's profile.
//
// WHAT THIS CLASS DOES (one sentence):
// Stores the name, role, focus area, and other details for one
// team member so the AI panel can tailor its advice to WHO is asking.
//
// HOW IT FITS THE ARCHITECTURE:
// Before each debate cycle, the user selects a stakeholder from
// the hotseat menu in Main.java. That StakeholderProfile gets
// passed to PromptBuilder, which formats it as the "outer layer"
// of the context layers — telling each AI model who they're advising.
//
// WHY THIS MATTERS:
// Grant asking "how should we structure the GUI?" gets different
// advice than Xavier asking the same question — because Grant is
// leading the project and needs architectural guidance, while
// Xavier might need more hands-on implementation guidance
// depending on his comfort level with the codebase.
// ============================================================

import java.util.List;

public class StakeholderProfile {

    // Each field maps to one line in the formatted briefing.
    // These match the original STAKEHOLDER_PROFILES array in the
    // old Main.java — same data, now in a proper class.
    private String name;
    private String role;
    private String focusArea;
    private String worksWith;
    private String responsibilities;
    private String evaluatedOn;
    private String background;

    StakeholderProfile() {}

    public StakeholderProfile(String name, String role, String focusArea,
                              String worksWith, String responsibilities,
                              String evaluatedOn, String background) {
        this.name = name;
        this.role = role;
        this.focusArea = focusArea;
        this.worksWith = worksWith;
        this.responsibilities = responsibilities;
        this.evaluatedOn = evaluatedOn;
        this.background = background;
    }

    public String getName()             { return name; }
    public String getRole()             { return role; }
    public String getFocusArea()        { return focusArea; }
    public String getResponsibilities() { return responsibilities; }
    public String getEvaluatedOn()      { return evaluatedOn; }
    public String getWorksWith()        { return worksWith; }
    public String getBackground()       { return background; }

    // ============================================================
    // toBriefing() — Formats this profile as a prompt-ready text block.
    //
    // This is the stakeholder context layer — it tells the model
    // WHO is asking the question and what they care about. The model
    // reads this and adjusts its advice to match the stakeholder's
    // role, authority, and KPIs.
    //
    // The format matches the original formatStakeholderBriefing()
    // output from the monolithic Main.java exactly.
    // ============================================================
    public String toBriefing() {
        return "=== ACTIVE STAKEHOLDER (who is asking this question) ===\n"
             + "Name: " + name + "\n"
             + "Title: " + role + "\n"
             + "Department: " + focusArea + "\n"
             + "Reports to: " + worksWith + "\n"
             + "Decision authority: " + responsibilities + "\n"
             + "KPIs: " + evaluatedOn + "\n"
             + "Background: " + background + "\n"
             + "=== END STAKEHOLDER PROFILE ===\n\n"
             + "Tailor your analysis to this stakeholder's role, authority, "
             + "and KPIs. Address what THEY specifically need to know.\n\n";
    }

    // ============================================================
    // getDefaults() — Returns a single generic "Operator" stakeholder
    // so the app can boot cold with no user setup. FirstLaunchSetupDialog
    // lets the user replace this with real team members, and the profile
    // system persists them. Always returns at least one entry so
    // stakeholder selection (GUI hotseat + CLI index) never crashes.
    // ============================================================
    public static List<StakeholderProfile> getDefaults() {
        return List.of(new StakeholderProfile(
            "Operator",
            "Primary User",
            "General",
            "(not set)",
            "Decisions within their own scope",
            "Overall outcomes",
            "Generic default profile — edit in the Profile Set editor to add "
                + "real team members and their specific roles, KPIs, and context."
        ));
    }
}
