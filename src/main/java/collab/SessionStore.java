package collab;

// ============================================================
// SessionStore.java — Persists contributions to a JSON file
// using Gson.
//
// WHAT THIS CLASS DOES (one sentence):
// Saves and loads Contribution records to/from a local JSON file
// so context attribution survives across sessions.
//
// HOW IT FITS THE ARCHITECTURE:
// After each debate cycle, Main.java tells SessionStore to save
// the new Contribution. On startup, SessionStore loads prior
// contributions so ConversationContext can include them in the
// history block.
//
// WHY A SINGLE JSON FILE?
// One file = one class, one read/write path, easy debugging.
// The data volume for a university project will never exceed
// what a single JSON file handles comfortably. If we ever need
// a database, the Contribution records map directly to rows.
//
// FILE: session_data.json (gitignored — local to each machine)
// ============================================================

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class SessionStore {

    private static final String DEFAULT_FILE = "session_data.json";
    private final Gson gson;
    private final String filePath;

    public SessionStore() {
        this(DEFAULT_FILE);
    }

    public SessionStore(String filePath) {
        this.filePath = filePath;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    // ============================================================
    // loadContributions() — Reads all saved contributions from
    // the JSON file. Returns an empty list if the file doesn't
    // exist yet (first run).
    // ============================================================
    public List<Contribution> loadContributions() {
        File file = new File(filePath);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<Contribution>>() {}.getType();
            List<Contribution> loaded = gson.fromJson(reader, listType);
            return loaded != null ? new ArrayList<>(loaded) : new ArrayList<>();
        } catch (IOException e) {
            System.out.println("[SessionStore] Warning: could not load " + filePath
                    + " — starting fresh. (" + e.getMessage() + ")");
            return new ArrayList<>();
        }
    }

    // ============================================================
    // saveContributions() — Writes the full list of contributions
    // to the JSON file, overwriting any previous content.
    //
    // Called after each new contribution is added.
    // ============================================================
    public void saveContributions(List<Contribution> contributions) {
        try (Writer writer = new FileWriter(filePath)) {
            gson.toJson(contributions, writer);
        } catch (IOException e) {
            System.out.println("[SessionStore] Warning: could not save to " + filePath
                    + " (" + e.getMessage() + ")");
        }
    }

    // ============================================================
    // appendContribution() — Adds a single contribution to the
    // existing list and saves everything to disk.
    //
    // This is the most common operation: after each debate cycle,
    // Main.java calls this to persist the new contribution.
    //
    // PARAMETERS:
    //   contribution — the new contribution to add
    //   existing     — the in-memory list (modified in place)
    // ============================================================
    public void appendContribution(Contribution contribution,
                                   List<Contribution> existing) {
        existing.add(contribution);
        saveContributions(existing);
    }
}
