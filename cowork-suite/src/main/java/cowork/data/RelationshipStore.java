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
 * JSON-backed list of Relationships (relationships.json), keyed by id. Loaded once at
 * construction; every mutation writes straight through.
 */
public class RelationshipStore {

    private static final String FILE_NAME = "relationships.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TypeToken<List<Relationship>> LIST_TYPE = new TypeToken<>() {};

    private final Path filePath;
    private List<Relationship> relationships;

    public RelationshipStore() { this(AppPaths.data(FILE_NAME)); }

    public RelationshipStore(Path filePath) {
        this.filePath = filePath;
        load();
    }

    public List<Relationship> getAll() { return Collections.unmodifiableList(relationships); }

    public Relationship getById(String id) {
        return relationships.stream().filter(r -> Objects.equals(r.getId(), id)).findFirst().orElse(null);
    }

    public void addOrUpdate(Relationship rel) {
        relationships.removeIf(r -> Objects.equals(r.getId(), rel.getId()));
        relationships.add(rel);
        save();
    }

    public void remove(String id) {
        relationships.removeIf(r -> Objects.equals(r.getId(), id));
        save();
    }

    private void load() {
        relationships = new ArrayList<>();
        if (!Files.exists(filePath)) return;
        try {
            List<Relationship> loaded = GSON.fromJson(Files.readString(filePath), LIST_TYPE);
            if (loaded != null) relationships = loaded;
        } catch (IOException e) {
            System.err.println("[RelationshipStore] Failed to load: " + e.getMessage());
        }
    }

    public void save() {
        try {
            if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, GSON.toJson(relationships));
        } catch (IOException e) {
            System.err.println("[RelationshipStore] Failed to save: " + e.getMessage());
        }
    }
}
