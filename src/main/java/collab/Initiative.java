package collab;

/**
 * A structured initiative extracted from org context.
 */
public class Initiative {
    private String id;
    private String name;
    private String owner;
    private String status;       // "on-track", "at-risk", "blocked", "completed"
    private String deadline;
    private String blockers;
    private String nextActions;
    private String successMetric;

    public Initiative() {}

    public Initiative(String id, String name, String owner, String status) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.status = status;
    }

    public String getId()             { return id; }
    public String getName()           { return name; }
    public String getOwner()          { return owner; }
    public String getStatus()         { return status; }
    public String getDeadline()       { return deadline; }
    public String getBlockers()       { return blockers; }
    public String getNextActions()    { return nextActions; }
    public String getSuccessMetric()  { return successMetric; }

    public void setId(String v)             { this.id = v; }
    public void setName(String v)           { this.name = v; }
    public void setOwner(String v)          { this.owner = v; }
    public void setStatus(String v)         { this.status = v; }
    public void setDeadline(String v)       { this.deadline = v; }
    public void setBlockers(String v)       { this.blockers = v; }
    public void setNextActions(String v)    { this.nextActions = v; }
    public void setSuccessMetric(String v)  { this.successMetric = v; }

    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (owner != null && !owner.isBlank()) sb.append(" (").append(owner).append(")");
        sb.append(" — ").append(status != null ? status : "unknown");
        if (deadline != null && !deadline.isBlank()) sb.append(" | Due: ").append(deadline);
        if (blockers != null && !blockers.isBlank()) sb.append(" | Blockers: ").append(blockers);
        return sb.toString();
    }
}
