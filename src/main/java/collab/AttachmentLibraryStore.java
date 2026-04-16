package collab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// ============================================================
// AttachmentLibraryStore.java — Persistent library of reusable
// FileAttachments.
//
// WHAT THIS CLASS DOES (one sentence):
// Reads and writes a single library/attachments.json file holding
// every FileAttachment a user has pinned for reuse across debates
// (the RSO request PDF, a standing budget template, a program
// calendar, etc.).
//
// HOW IT FITS THE ARCHITECTURE:
// Mirrors ProfileLibrary: one Gson-backed file, load() returns a
// mutable list the editor renders, save(list) overwrites on OK.
// FileUploader consumes the list items the same way it consumes
// profile-set attachments — the source of truth is the local path,
// provider file IDs are resolved lazily on first use and never
// persisted here.
//
// WHY A SEPARATE STORE FROM ProfileSet.attachments:
// ProfileSet.attachments is debate-scoped: those files travel with a
// specific named set of panelists. The library is debate-independent:
// it's the user's personal closet of "files I reach for often".
// Attachments can be promoted from library -> profile set -> panelist
// slot without duplicating the underlying path on disk.
//
// JSON FORMAT:
// {
//   "items": [
//     {"localPath":"/home/.../file.pdf","mimeType":"application/pdf",
//      "displayName":"Q3 Earnings"}
//   ]
// }
// Pretty-printed for easy hand-editing (and diffing) across the team.
// ============================================================
public final class AttachmentLibraryStore {

    private static final Path DEFAULT_PATH =
            Path.of("library", "attachments.json");

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public AttachmentLibraryStore() {
        this(DEFAULT_PATH);
    }

    public AttachmentLibraryStore(Path file) {
        this.file = file;
        ensureParent();
    }

    private void ensureParent() {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create library directory for "
                    + file, e);
        }
    }

    /**
     * Returns the library contents, or an empty mutable list when
     * the file does not yet exist (fresh install, first-launch).
     */
    public List<FileAttachment> load() {
        if (!Files.exists(file)) return new ArrayList<>();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Wrapper w = gson.fromJson(r, Wrapper.class);
            if (w == null || w.items == null) return new ArrayList<>();
            return new ArrayList<>(w.items);
        } catch (IOException e) {
            throw new RuntimeException("Failed reading attachment library " + file, e);
        }
    }

    /**
     * Overwrites the library with the given list. Pretty-printed JSON
     * keeps team-level diffs human-readable if anyone chooses to
     * commit the library file to source control.
     */
    public void save(List<FileAttachment> items) {
        Wrapper w = new Wrapper();
        w.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(w, Wrapper.class, out);
        } catch (IOException e) {
            throw new RuntimeException("Failed writing attachment library " + file, e);
        }
    }

    /**
     * Convenience helper used by the library editor dialog: append a
     * single attachment and return the updated list.
     */
    public List<FileAttachment> add(FileAttachment attachment) {
        List<FileAttachment> all = load();
        all.add(attachment);
        save(all);
        return all;
    }

    /**
     * Remove by absolute path. Silently no-ops when the path is not in
     * the library — removing something that was never there is not an
     * error condition worth surfacing.
     */
    public List<FileAttachment> removeByPath(String localPath) {
        List<FileAttachment> all = load();
        all.removeIf(a -> a.getLocalPath() != null
                && a.getLocalPath().equals(localPath));
        save(all);
        return all;
    }

    // Gson-serialisable wrapper so we don't rely on List<FileAttachment>
    // generic-type-token gymnastics (a bare TypeToken is awkward to
    // document for students). A named wrapper with a single "items"
    // field also leaves room to add library-level metadata later
    // (schema version, last-updated timestamp) without breaking the
    // file format.
    private static final class Wrapper {
        List<FileAttachment> items;
    }

    // Kept for future use: bulk-import callers can deserialise a raw
    // JSON array via this type token and feed it into save(). Not
    // used yet, but cheaper to keep than to re-derive when needed.
    @SuppressWarnings("unused")
    private static final TypeToken<List<FileAttachment>> LIST_TYPE =
            new TypeToken<>() {};
}
