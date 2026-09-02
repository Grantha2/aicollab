package cowork.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import cowork.config.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * JSON persistence for user-defined workflows (workflows.json), mirroring InitiativeStore.
 * A malformed file is logged and treated as empty rather than blocking startup.
 */
public class WorkflowStore {

    private static final String FILE_NAME = "workflows.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TypeToken<List<WorkflowDefinition>> LIST_TYPE = new TypeToken<>() {};

    private final Path filePath;
    private final List<WorkflowDefinition> workflows = new ArrayList<>();

    public WorkflowStore() { this(AppPaths.data(FILE_NAME)); }

    public WorkflowStore(Path filePath) {
        this.filePath = filePath;
        load();
    }

    private void load() {
        if (!Files.exists(filePath)) return;
        try {
            List<WorkflowDefinition> loaded = GSON.fromJson(Files.readString(filePath), LIST_TYPE);
            if (loaded != null) workflows.addAll(loaded);
        } catch (Exception e) {
            System.err.println("[WorkflowStore] Failed to load: " + e.getMessage());
        }
    }

    public void save() {
        try {
            if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, GSON.toJson(workflows));
        } catch (IOException e) {
            System.err.println("[WorkflowStore] Failed to save: " + e.getMessage());
        }
    }

    public List<WorkflowDefinition> getAll() { return Collections.unmodifiableList(workflows); }

    public WorkflowDefinition getById(String id) {
        return workflows.stream().filter(w -> Objects.equals(w.getId(), id)).findFirst().orElse(null);
    }

    public void add(WorkflowDefinition workflow) {
        workflows.add(workflow);
        save();
    }

    public void update(WorkflowDefinition workflow) {
        workflows.removeIf(w -> Objects.equals(w.getId(), workflow.getId()));
        workflows.add(workflow);
        save();
    }

    public void remove(String id) {
        workflows.removeIf(w -> Objects.equals(w.getId(), id));
        save();
    }
}
