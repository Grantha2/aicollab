package collab;

// ============================================================
// OfficerRole.java — Enum of IDSSO officer positions with
// permission mappings.
//
// WHAT THIS ENUM DOES (one sentence):
// Defines every officer role in the organization and which
// FunctionalArea "buttons" each role is allowed to access.
//
// HOW IT FITS THE ARCHITECTURE:
// When a user logs in, they select an OfficerRole. That role's
// permissions determine which functional areas appear in their
// menu. UserSession delegates canAccess() checks to this enum.
//
// ADDING A NEW ROLE:
// 1. Add a new enum constant below with a display name and
//    a Set.of(...) listing the FunctionalArea values it can access.
// 2. That's it — the CLI menu and permission checks pick it up
//    automatically.
// ============================================================

import java.util.Set;

public enum OfficerRole {

    // --- Executive Board ---
    PRESIDENT(
        "President",
        Set.of(FunctionalArea.values())  // full access
    ),
    VICE_PRESIDENT(
        "Vice President",
        Set.of(FunctionalArea.values())  // full access
    ),
    TREASURER(
        "Treasurer",
        Set.of(FunctionalArea.FINANCES, FunctionalArea.WEBSITE)
    ),
    SECRETARY(
        "Secretary",
        Set.of(FunctionalArea.WEBSITE, FunctionalArea.PUBLIC_IMAGE)
    ),

    // --- Directors ---
    DIR_MEMBERSHIP_DEV(
        "Director of Membership Development",
        Set.of(FunctionalArea.ATTRACTION, FunctionalArea.ENGAGEMENT,
               FunctionalArea.NEW_MEMBER_ORIENTATION, FunctionalArea.DIVERSITY,
               FunctionalArea.PROFESSIONAL_DEV_PROGRAMMING)
    ),
    DIR_EXTERNAL_AFFAIRS(
        "Director of External Affairs",
        Set.of(FunctionalArea.PUBLIC_IMAGE, FunctionalArea.SOCIAL_MEDIA,
               FunctionalArea.ATTRACTION)
    ),
    DIR_MARKETING(
        "Director of Marketing",
        Set.of(FunctionalArea.PUBLIC_IMAGE, FunctionalArea.SOCIAL_MEDIA,
               FunctionalArea.WEBSITE)
    ),

    // --- Committee Chairs ---
    CHAIR_SOCIAL(
        "Social Committee Chair",
        Set.of(FunctionalArea.SOCIAL_COMMITTEE, FunctionalArea.ENGAGEMENT)
    ),
    CHAIR_PROFESSIONAL_DEV(
        "Professional Development Chair",
        Set.of(FunctionalArea.PROFESSIONAL_DEV_PROGRAMMING)
    ),
    CHAIR_DIVERSITY(
        "Diversity Chair",
        Set.of(FunctionalArea.DIVERSITY, FunctionalArea.ENGAGEMENT)
    ),
    CHAIR_ATTRACTION(
        "Attraction Chair",
        Set.of(FunctionalArea.ATTRACTION, FunctionalArea.SOCIAL_MEDIA)
    );

    private final String displayName;
    private final Set<FunctionalArea> permissions;

    OfficerRole(String displayName, Set<FunctionalArea> permissions) {
        this.displayName = displayName;
        this.permissions = permissions;
    }

    public String getDisplayName()          { return displayName; }
    public Set<FunctionalArea> getPermissions() { return permissions; }

    // ============================================================
    // canAccess() — Checks if this role is allowed to access a
    // given functional area.
    //
    // Used by UserSession and the CLI menu to filter which
    // "buttons" appear for the logged-in user.
    // ============================================================
    public boolean canAccess(FunctionalArea area) {
        return permissions.contains(area);
    }
}
