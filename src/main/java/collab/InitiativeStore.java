package collab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class InitiativeStore {

    private static final String FILE_NAME = "initiatives.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path filePath;
    private List<Initiative> initiatives;

    public InitiativeStore() { this(Path.of(FILE_NAME)); }
    public InitiativeStore(Path filePath) { this.filePath = filePath; load(); }

    public List<Initiative> getAll() { return Collections.unmodifiableList(initiatives); }

    public Initiative getById(String id) {
        return initiatives.stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null);
    }

    public void addOrUpdate(Initiative init) {
        initiatives.removeIf(i -> i.getId().equals(init.getId()));
        initiatives.add(init);
        save();
    }

    public void remove(String id) {
        initiatives.removeIf(i -> i.getId().equals(id));
        save();
    }

    private void load() {
        if (!Files.exists(filePath)) { initiatives = new ArrayList<>(); return; }
        try {
            String json = Files.readString(filePath);
            initiatives = GSON.fromJson(json, new TypeToken<List<Initiative>>(){}.getType());
            if (initiatives == null) initiatives = new ArrayList<>();
        } catch (IOException e) {
            System.err.println("[InitiativeStore] Failed to load: " + e.getMessage());
            initiatives = new ArrayList<>();
        }
    }

    public void save() {
        try { Files.writeString(filePath, GSON.toJson(initiatives)); }
        catch (IOException e) { System.err.println("[InitiativeStore] Failed to save: " + e.getMessage()); }
    }
}
