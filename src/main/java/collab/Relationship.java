package collab;

/**
 * A structured relationship/partnership extracted from org context.
 */
public class Relationship {
    private String id;
    private String name;
    private String organization;
    private String type;            // "partner", "sponsor", "advisor", "vendor", "community"
    private String status;          // "active", "developing", "dormant", "at-risk"
    private String lastInteraction;
    private String nextSteps;
    private String keyContacts;

    public Relationship() {}

    public Relationship(String id, String name, String organization, String type) {
        this.id = id;
        this.name = name;
        this.organization = organization;
        this.type = type;
    }

    public String getId()              { return id; }
    public String getName()            { return name; }
    public String getOrganization()    { return organization; }
    public String getType()            { return type; }
    public String getStatus()          { return status; }
    public String getLastInteraction() { return lastInteraction; }
    public String getNextSteps()       { return nextSteps; }
    public String getKeyContacts()     { return keyContacts; }

    public void setId(String v)              { this.id = v; }
    public void setName(String v)            { this.name = v; }
    public void setOrganization(String v)    { this.organization = v; }
    public void setType(String v)            { this.type = v; }
    public void setStatus(String v)          { this.status = v; }
    public void setLastInteraction(String v) { this.lastInteraction = v; }
    public void setNextSteps(String v)       { this.nextSteps = v; }
    public void setKeyContacts(String v)     { this.keyContacts = v; }

    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (organization != null && !organization.isBlank()) sb.append(" @ ").append(organization);
        sb.append(" (").append(type != null ? type : "unknown").append(")");
        if (status != null) sb.append(" — ").append(status);
        return sb.toString();
    }
}
