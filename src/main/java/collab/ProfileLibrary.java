package collab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ProfileLibrary {
    private final Path rootDir;
    private final Gson gson;

    public ProfileLibrary() {
        this(Path.of("profiles"));
    }

    public ProfileLibrary(Path rootDir) {
        this.rootDir = rootDir;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void ensureDefaultExists() throws IOException {
        Path file = profileFile("default");
        if (Files.exists(file)) {
            return;
        }
        saveSet(ProfileSet.fromDefaults(), "default");
    }

    public List<String> listAvailableSets() throws IOException {
        Files.createDirectories(rootDir);
        List<String> names = new ArrayList<>();

        try (var stream = Files.list(rootDir)) {
            stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(dir -> {
                        if (Files.exists(dir.resolve("profile-set.json"))) {
                            names.add(dir.getFileName().toString());
                        }
                    });
        }

        names.remove("default");
        names.addFirst("default");
        return names;
    }

    public ProfileSet loadSet(String name) throws IOException {
        Path file = profileFile(name);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, ProfileSet.class);
        }
    }

    public void saveSet(ProfileSet set, String name) throws IOException {
        Path file = profileFile(name);
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(set, writer);
        }
    }

    private Path profileFile(String name) {
        return rootDir.resolve(name).resolve("profile-set.json");
    }
}
