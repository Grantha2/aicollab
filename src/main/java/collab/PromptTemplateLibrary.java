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

// ============================================================
// PromptTemplateLibrary.java — Disk-backed store for PromptTemplate
// instances. Parallels ProfileLibrary's JSON-per-directory layout
// so users can keep multiple named template sets alongside their
// named profile sets.
//
// Default layout:
//   templates/<name>/prompt-template.json
// ============================================================

public class PromptTemplateLibrary {
    private final Path rootDir;
    private final Gson gson;

    public PromptTemplateLibrary() {
        this(Path.of("templates"));
    }

    public PromptTemplateLibrary(Path rootDir) {
        this.rootDir = rootDir;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void ensureDefaultExists() throws IOException {
        Path file = templateFile("default");
        if (Files.exists(file)) {
            return;
        }
        saveSet(PromptTemplate.fromDefaults(), "default");
    }

    public List<String> listAvailableSets() throws IOException {
        Files.createDirectories(rootDir);
        List<String> names = new ArrayList<>();

        try (var stream = Files.list(rootDir)) {
            stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(dir -> {
                        if (Files.exists(dir.resolve("prompt-template.json"))) {
                            names.add(dir.getFileName().toString());
                        }
                    });
        }

        names.remove("default");
        names.addFirst("default");
        return names;
    }

    public PromptTemplate loadSet(String name) throws IOException {
        Path file = templateFile(name);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            PromptTemplate loaded = gson.fromJson(reader, PromptTemplate.class);
            return loaded != null ? loaded : PromptTemplate.fromDefaults();
        }
    }

    public void saveSet(PromptTemplate set, String name) throws IOException {
        Path file = templateFile(name);
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(set, writer);
        }
    }

    private Path templateFile(String name) {
        return rootDir.resolve(name).resolve("prompt-template.json");
    }
}
