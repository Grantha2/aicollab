package collab;

import java.util.List;

public class ProfileSet {
    private String name;
    private String description;
    private String teamContext;
    private List<AgentProfile> agents;
    private List<StakeholderProfile> stakeholders;

    ProfileSet() {}

    public ProfileSet(String name, String description, String teamContext,
                      List<AgentProfile> agents, List<StakeholderProfile> stakeholders) {
        this.name = name;
        this.description = description;
        this.teamContext = teamContext;
        this.agents = agents;
        this.stakeholders = stakeholders;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getTeamContext() {
        return teamContext;
    }

    public void setTeamContext(String teamContext) {
        this.teamContext = teamContext;
    }

    public List<AgentProfile> getAgents() {
        return agents;
    }

    public List<StakeholderProfile> getStakeholders() {
        return stakeholders;
    }

    public void setStakeholders(List<StakeholderProfile> stakeholders) {
        this.stakeholders = stakeholders;
    }

    public static ProfileSet fromDefaults() {
        return new ProfileSet(
                "default",
                "Built-in default panel profiles",
                PromptBuilder.DEFAULT_TEAM_CONTEXT,
                AgentProfile.getDefaults(),
                StakeholderProfile.getDefaults());
    }
}
