package collab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// One panelist on the debate panel. Loaded from agents.json; if that
// file is missing we fall back to three built-in defaults so the app
// always boots cold.
public record Agent(String name, String perspective, String lens) {

    public String briefing() {
        return "=== YOUR AGENT IDENTITY ===\n"
             + "Agent name: " + name + "\n"
             + "Perspective: " + perspective + "\n"
             + lens + "\n\n";
    }

    private static final Path FILE = Path.of("agents.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static List<Agent> loadAll() {
        if (!Files.exists(FILE)) return defaults();
        try {
            Agent[] arr = GSON.fromJson(Files.readString(FILE), Agent[].class);
            return (arr != null && arr.length > 0) ? List.of(arr) : defaults();
        } catch (IOException e) {
            System.err.println("[Agent] load failed: " + e.getMessage());
            return defaults();
        }
    }

    public static void saveAll(List<Agent> agents) {
        try { Files.writeString(FILE, GSON.toJson(agents)); }
        catch (IOException e) { System.err.println("[Agent] save failed: " + e.getMessage()); }
    }

    public static List<Agent> defaults() {
        return List.of(
            new Agent("Claude", "Architecture & Quality",
                "Priorities: structural soundness, maintainability, edge cases, "
                + "and failure modes. Push back when a proposal hides complexity."),
            new Agent("GPT", "Ideas & Possibilities",
                "Priorities: surfacing options the team hasn't considered, "
                + "reframing the problem. Offer at least one alternative framing."),
            new Agent("Gemini", "Execution & Delivery",
                "Priorities: concrete next steps, owners, dependencies, "
                + "realistic sequencing. Flag anything that would block execution.")
        );
    }
}
