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
 * JSON-backed list of Initiatives (initiatives.json), keyed by id. Loaded once at
 * construction; every mutation writes straight through.
 */
public class InitiativeStore {

    private static final String FILE_NAME = "initiatives.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TypeToken<List<Initiative>> LIST_TYPE = new TypeToken<>() {};

    private final Path filePath;
    private List<Initiative> initiatives;

    public InitiativeStore() { this(AppPaths.data(FILE_NAME)); }

    public InitiativeStore(Path filePath) {
        this.filePath = filePath;
        load();
    }

    public List<Initiative> getAll() { return Collections.unmodifiableList(initiatives); }

    public Initiative getById(String id) {
        return initiatives.stream().filter(i -> Objects.equals(i.getId(), id)).findFirst().orElse(null);
    }

    public void addOrUpdate(Initiative init) {
        initiatives.removeIf(i -> Objects.equals(i.getId(), init.getId()));
        initiatives.add(init);
        save();
    }

    public void remove(String id) {
        initiatives.removeIf(i -> Objects.equals(i.getId(), id));
        save();
    }

    private void load() {
        initiatives = new ArrayList<>();
        if (!Files.exists(filePath)) return;
        try {
            List<Initiative> loaded = GSON.fromJson(Files.readString(filePath), LIST_TYPE);
            if (loaded != null) initiatives = loaded;
        } catch (IOException e) {
            System.err.println("[InitiativeStore] Failed to load: " + e.getMessage());
        }
    }

    public void save() {
        try {
            if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, GSON.toJson(initiatives));
        } catch (IOException e) {
            System.err.println("[InitiativeStore] Failed to save: " + e.getMessage());
        }
    }
}
