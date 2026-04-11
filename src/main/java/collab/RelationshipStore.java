package collab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class RelationshipStore {

    private static final String FILE_NAME = "relationships.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path filePath;
    private List<Relationship> relationships;

    public RelationshipStore() { this(Path.of(FILE_NAME)); }
    public RelationshipStore(Path filePath) { this.filePath = filePath; load(); }

    public List<Relationship> getAll() { return Collections.unmodifiableList(relationships); }

    public Relationship getById(String id) {
        return relationships.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
    }

    public void addOrUpdate(Relationship rel) {
        relationships.removeIf(r -> r.getId().equals(rel.getId()));
        relationships.add(rel);
        save();
    }

    public void remove(String id) {
        relationships.removeIf(r -> r.getId().equals(id));
        save();
    }

    private void load() {
        if (!Files.exists(filePath)) { relationships = new ArrayList<>(); return; }
        try {
            String json = Files.readString(filePath);
            relationships = GSON.fromJson(json, new TypeToken<List<Relationship>>(){}.getType());
            if (relationships == null) relationships = new ArrayList<>();
        } catch (IOException e) {
            System.err.println("[RelationshipStore] Failed to load: " + e.getMessage());
            relationships = new ArrayList<>();
        }
    }

    public void save() {
        try { Files.writeString(filePath, GSON.toJson(relationships)); }
        catch (IOException e) { System.err.println("[RelationshipStore] Failed to save: " + e.getMessage()); }
    }
}
