package collab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Agent panel + stakeholder roster + team context, all in one place.
// Loads from profiles.json if present; otherwise uses built-in defaults.
public final class Profiles {

    private static final String FILE_NAME = "profiles.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Agent(String name, String perspective, String lens) {
        public String toBriefing() {
            return "=== YOUR AGENT IDENTITY ===\n"
                 + "Agent name: " + name + "\n"
                 + "Perspective: " + perspective + "\n"
                 + lens + "\n\n";
        }
    }

    public record Stakeholder(
            String name, String role, String focusArea,
            String worksWith, String responsibilities,
            String evaluatedOn, String background) {
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
    }

    private final List<Agent> agents;
    private final List<Stakeholder> stakeholders;
    private final String teamContext;

    public Profiles(List<Agent> agents, List<Stakeholder> stakeholders, String teamContext) {
        this.agents = List.copyOf(agents);
        this.stakeholders = List.copyOf(stakeholders);
        this.teamContext = teamContext == null ? "" : teamContext;
    }

    public List<Agent> agents() { return agents; }
    public List<Stakeholder> stakeholders() { return stakeholders; }
    public String teamContext() { return teamContext; }

    public static Profiles loadOrDefault() {
        Path path = Path.of(FILE_NAME);
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                Profiles loaded = GSON.fromJson(json, Profiles.class);
                if (loaded != null && loaded.agents != null && !loaded.agents.isEmpty()) {
                    return loaded;
                }
            } catch (IOException e) {
                System.err.println("[Profiles] Failed to load: " + e.getMessage());
            }
        }
        return defaults();
    }

    public void save() {
        try {
            Files.writeString(Path.of(FILE_NAME), GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[Profiles] Failed to save: " + e.getMessage());
        }
    }

    public static Profiles defaults() {
        List<Agent> agents = List.of(
            new Agent("Claude", "Architecture & Quality",
                "Priorities: structural soundness, long-term maintainability, "
                    + "edge cases, and failure modes. Push back when a proposal "
                    + "ignores reversibility or hides complexity. Prefer clear "
                    + "interfaces and explicit trade-offs over clever shortcuts."),
            new Agent("GPT", "Ideas & Possibilities",
                "Priorities: surfacing options the team hasn't considered, "
                    + "reframing the problem, and connecting the current question "
                    + "to adjacent opportunities. Push back when the group "
                    + "converges too early. Offer at least one alternative framing "
                    + "before endorsing the default."),
            new Agent("Gemini", "Execution & Delivery",
                "Priorities: what it takes to actually ship. Concrete next steps, "
                    + "owners, dependencies, and realistic sequencing. Push back "
                    + "when a plan skips the unglamorous work. Flag anything that "
                    + "would block execution in the next 1-2 weeks.")
        );
        List<Stakeholder> stakeholders = List.of(new Stakeholder(
            "Operator", "Primary User", "General", "(not set)",
            "Decisions within their own scope", "Overall outcomes",
            "Generic default profile \u2014 edit profiles.json to customize."
        ));
        return new Profiles(agents, stakeholders, "");
    }
}
