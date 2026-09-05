package collab;

// ============================================================
// AgentProfileLibrary.java — Persists individual AgentProfile
// entries by name so the Profile Set editor can offer a
// "pick a profile" combo when a user adds a new slot.
//
// WHY THIS EXISTS:
// Agent profiles used to live only inside a ProfileSet. Variable
// panelists means users can add a 4th/5th/6th slot at any time
// and must assign a profile to each new slot. This library is
// the catalogue they pick from (or add to, via the inline
// AgentProfileEditorDialog).
//
// File layout:
//   agent_profiles/
//     <name>.json        one profile per file, pretty-printed
// ============================================================

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

public class AgentProfileLibrary {

    private final Path rootDir;
    private final Gson gson;

    public AgentProfileLibrary() {
        this(Path.of("agent_profiles"));
    }

    public AgentProfileLibrary(Path rootDir) {
        this.rootDir = rootDir;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /** Seeds the library with built-in defaults on first launch. No-op if any file exists. */
    public void ensureSeeded() throws IOException {
        Files.createDirectories(rootDir);
        if (!listAll().isEmpty()) return;
        for (AgentProfile ap : AgentProfile.getDefaults()) {
            save(ap);
        }
    }

    /** Returns every saved profile, sorted by name. */
    public List<AgentProfile> listAll() {
        List<AgentProfile> result = new ArrayList<>();
        if (!Files.exists(rootDir)) return result;
        try (var stream = Files.list(rootDir)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path f : files) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    AgentProfile ap = gson.fromJson(r, AgentProfile.class);
                    if (ap != null && ap.getName() != null && !ap.getName().isBlank()) {
                        result.add(ap);
                    }
                } catch (Exception e) {
                    // skip malformed file
                }
            }
        } catch (IOException e) {
            System.err.println("[AgentProfileLibrary] Failed to list: " + e.getMessage());
        }
        return result;
    }

    public AgentProfile load(String name) throws IOException {
        Path file = fileFor(name);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, AgentProfile.class);
        }
    }

    public void save(AgentProfile profile) throws IOException {
        if (profile == null || profile.getName() == null || profile.getName().isBlank()) {
            throw new IllegalArgumentException("AgentProfile requires a non-blank name");
        }
        Path file = fileFor(profile.getName());
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(profile, writer);
        }
    }

    public void delete(String name) throws IOException {
        Files.deleteIfExists(fileFor(name));
    }

    public boolean exists(String name) {
        return Files.exists(fileFor(name));
    }

    private Path fileFor(String name) {
        // Basic filesystem-safety: strip slashes and collapse whitespace.
        String safe = name.replaceAll("[\\\\/]", "_").trim();
        return rootDir.resolve(safe + ".json");
    }
}
