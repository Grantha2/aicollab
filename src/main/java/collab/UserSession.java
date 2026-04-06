package collab;

// ============================================================
// UserSession.java — Represents the logged-in user's identity
// and role for the current session.
//
// WHAT THIS CLASS DOES (one sentence):
// Holds the current user's name and OfficerRole, filters which
// functional areas they can access, and bridges to the existing
// StakeholderProfile system.
//
// HOW IT FITS THE ARCHITECTURE:
// Main.java creates a UserSession when the user logs in (types
// their name and selects a role). The session is used to:
//   1. Filter the functional area menu (canAccess / getAccessibleAreas)
//   2. Generate a StakeholderProfile for Orchestrator (toStakeholderProfile)
//   3. Tag contributions with identity (getUserName / getRole)
//
// WHY THE BRIDGE TO STAKEHOLDER PROFILE?
// The Orchestrator and PromptBuilder already work with
// StakeholderProfile. Instead of rewriting them, UserSession
// converts itself into a StakeholderProfile so the downstream
// code works unchanged. When the GUI comes in Week 4, this
// same UserSession becomes the natural session object.
// ============================================================

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class UserSession {

    private final String userName;
    private final OfficerRole role;
    private final long loginTimeMillis;

    public UserSession(String userName, OfficerRole role) {
        this.userName = userName;
        this.role = role;
        this.loginTimeMillis = System.currentTimeMillis();
    }

    public String getUserName()       { return userName; }
    public OfficerRole getRole()      { return role; }
    public long getLoginTimeMillis()  { return loginTimeMillis; }

    // ============================================================
    // canAccess() — Checks if the current user's role allows
    // access to a given functional area.
    // ============================================================
    public boolean canAccess(FunctionalArea area) {
        return role.canAccess(area);
    }

    // ============================================================
    // getAccessibleAreas() — Returns the list of functional areas
    // this user can access, sorted by category for clean display.
    //
    // Used by Main.java to build the role-filtered menu.
    // ============================================================
    public List<FunctionalArea> getAccessibleAreas() {
        List<FunctionalArea> areas = new ArrayList<>();
        for (FunctionalArea area : FunctionalArea.values()) {
            if (role.canAccess(area)) {
                areas.add(area);
            }
        }
        areas.sort(Comparator.comparing(FunctionalArea::getCategory)
                              .thenComparing(FunctionalArea::getDisplayName));
        return areas;
    }

    // ============================================================
    // toStakeholderProfile() — Converts this session into a
    // StakeholderProfile for the existing Orchestrator/PromptBuilder.
    //
    // This is the bridge between the new RBAC system and the
    // existing prompt engineering pipeline. The Orchestrator
    // doesn't need to know about roles or permissions — it just
    // receives a StakeholderProfile like before.
    // ============================================================
    public StakeholderProfile toStakeholderProfile() {
        // Build a focus area string from the accessible areas
        List<FunctionalArea> areas = getAccessibleAreas();
        String focusAreas = areas.stream()
                .map(FunctionalArea::getDisplayName)
                .collect(Collectors.joining(", "));

        return new StakeholderProfile(
            userName,
            role.getDisplayName(),
            focusAreas,
            "IDSSO Executive Board",
            role.getDisplayName() + " — oversees " + focusAreas,
            "Effective leadership in assigned functional areas",
            "IDSSO officer logged in as " + role.getDisplayName()
        );
    }
}
