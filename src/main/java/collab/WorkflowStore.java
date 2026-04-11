package collab;

// ============================================================
// WorkflowStore.java — JSON persistence for user-defined workflows.
//
// WHAT THIS CLASS DOES (one sentence):
// Loads, saves, and manages WorkflowDefinition objects from
// workflows.json, following the same pattern as InitiativeStore.
// ============================================================

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

public class WorkflowStore {

    private static final String FILE_NAME = "workflows.json";
    private final Path filePath;
    private final Gson gson;
    private final List<WorkflowDefinition> workflows = new ArrayList<>();

    public WorkflowStore() {
        this.filePath = Paths.get(FILE_NAME);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    private void load() {
        if (!Files.exists(filePath)) return;
        try {
            String json = Files.readString(filePath);
            Type listType = new TypeToken<List<WorkflowDefinition>>() {}.getType();
            List<WorkflowDefinition> loaded = gson.fromJson(json, listType);
            if (loaded != null) workflows.addAll(loaded);
        } catch (Exception e) {
            System.err.println("Failed to load workflows: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Files.writeString(filePath, gson.toJson(workflows));
        } catch (IOException e) {
            System.err.println("Failed to save workflows: " + e.getMessage());
        }
    }

    public List<WorkflowDefinition> getAll() {
        return Collections.unmodifiableList(workflows);
    }

    public WorkflowDefinition getById(String id) {
        return workflows.stream().filter(w -> w.getId().equals(id)).findFirst().orElse(null);
    }

    public void add(WorkflowDefinition workflow) {
        workflows.add(workflow);
        save();
    }

    public void update(WorkflowDefinition workflow) {
        workflows.removeIf(w -> w.getId().equals(workflow.getId()));
        workflows.add(workflow);
        save();
    }

    public void remove(String id) {
        workflows.removeIf(w -> w.getId().equals(id));
        save();
    }
}
