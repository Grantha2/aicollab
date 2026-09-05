package collab;

import java.util.ArrayList;
import java.util.List;

public class ProfileSet {
    private String name;
    private String description;
    private String teamContext;
    private List<AgentProfile> agents;
    private List<StakeholderProfile> stakeholders;
    // New: one PanelistSlot per panel seat. Persisted alongside `agents`
    // for backwards compatibility — legacy profile-set JSON files with
    // only `agents` still load; getSlots() synthesises slots on demand.
    private List<PanelistSlot> slots;

    ProfileSet() {}

    public ProfileSet(String name, String description, String teamContext,
                      List<AgentProfile> agents, List<StakeholderProfile> stakeholders) {
        this.name = name;
        this.description = description;
        this.teamContext = teamContext;
        this.agents = agents;
        this.stakeholders = stakeholders;
    }

    public ProfileSet(String name, String description, String teamContext,
                      List<AgentProfile> agents, List<StakeholderProfile> stakeholders,
                      List<PanelistSlot> slots) {
        this(name, description, teamContext, agents, stakeholders);
        this.slots = slots;
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

    /**
     * Returns the per-slot panel configuration. Legacy profile-set files
     * that were saved before variable panelists existed have no `slots`
     * field — for those we synthesise a three-slot default pairing each
     * legacy agent with the historically-assigned provider
     * (Anthropic/OpenAI/Google in that order). This means old saved
     * profile sets keep working without a migration prompt.
     */
    public List<PanelistSlot> getSlots() {
        if (slots != null && !slots.isEmpty()) return slots;

        List<AgentProfile> legacy = agents;
        if (legacy == null || legacy.isEmpty()) {
            return new ArrayList<>();
        }

        Provider[] defaults = { Provider.ANTHROPIC, Provider.OPENAI, Provider.GOOGLE };
        List<PanelistSlot> synthesised = new ArrayList<>();
        for (int i = 0; i < legacy.size(); i++) {
            Provider p = i < defaults.length ? defaults[i] : Provider.ANTHROPIC;
            synthesised.add(new PanelistSlot(p, null /* let Config pick model */, legacy.get(i)));
        }
        return synthesised;
    }

    public void setSlots(List<PanelistSlot> slots) {
        this.slots = slots;
        // Keep the legacy `agents` list in sync so any reader that still
        // accesses getAgents() (e.g. the CLI fallback path) sees the same
        // set of AgentProfiles, in the same order, as the slots.
        if (slots != null) {
            List<AgentProfile> mirrored = new ArrayList<>();
            for (PanelistSlot s : slots) {
                if (s != null && s.getAgent() != null) mirrored.add(s.getAgent());
            }
            this.agents = mirrored;
        }
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
