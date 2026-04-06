package collab;

// ============================================================
// FunctionalArea.java — Enum of all "buttons" / functional areas
// that IDSSO officers can access.
//
// WHAT THIS ENUM DOES (one sentence):
// Defines every functional area in the organization, grouped by
// category, so the RBAC system knows what "buttons" exist.
//
// HOW IT FITS THE ARCHITECTURE:
// OfficerRole maps each role to a Set<FunctionalArea> — that set
// determines which menu items appear for the logged-in user.
// When a user selects an area and submits a prompt, the area is
// recorded in the Contribution so we know WHAT topic the context
// was about.
//
// CATEGORIES:
//   Internal Administration — Website, Finances, Public Image, Social Media
//   Membership             — Attraction, Engagement, New Member Orientation, Diversity
//   Workshops/Events       — Professional Development Programming, Social Committee
// ============================================================

public enum FunctionalArea {

    // --- Internal Administration ---
    WEBSITE("Website", "Internal Administration"),
    FINANCES("Finances", "Internal Administration"),
    PUBLIC_IMAGE("Public Image", "Internal Administration"),
    SOCIAL_MEDIA("Social Media", "Internal Administration"),

    // --- Membership ---
    ATTRACTION("Attraction", "Membership"),
    ENGAGEMENT("Engagement", "Membership"),
    NEW_MEMBER_ORIENTATION("New Member Orientation", "Membership"),
    DIVERSITY("Diversity", "Membership"),

    // --- Workshops / Service Projects / Events ---
    PROFESSIONAL_DEV_PROGRAMMING("Professional Development Programming", "Workshops/Events"),
    SOCIAL_COMMITTEE("Social Committee", "Workshops/Events");

    private final String displayName;
    private final String category;

    FunctionalArea(String displayName, String category) {
        this.displayName = displayName;
        this.category = category;
    }

    public String getDisplayName() { return displayName; }
    public String getCategory()    { return category; }
}
