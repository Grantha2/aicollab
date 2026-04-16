package collab;

import java.nio.file.Files;
import java.nio.file.Path;

// ============================================================
// FileAttachment.java — A local file attached to a debate.
//
// WHAT THIS CLASS DOES (one sentence):
// Holds the local filesystem source-of-truth for one file the user
// wants every panelist to read — path, MIME type, and display name —
// so FileUploader can lazily upload it to each provider's Files API
// on first use and cache the returned provider-specific ID.
//
// WHY A CLASS, NOT A RECORD:
// ProfileSet is Gson-serialised; records work, but we already use the
// "no-arg constructor + getters/setters" pattern everywhere else in
// this package (PanelistSlot, ProfileSet, AgentProfile) so Gson never
// has to reach for reflection-on-records. Keeping the pattern uniform
// avoids a subtle deserialisation bug later.
//
// WHAT IS NOT STORED HERE:
// Provider-specific file IDs are intentionally NOT fields on this
// class. They belong to FileUploader's in-memory cache, keyed by
// (Provider, absolute path). Reasons:
//   1. IDs expire (Anthropic: 30 days, OpenAI: persistent but
//      revocable, Gemini: 48 hours). Persisting them in a JSON
//      profile set would mean stale IDs get replayed after a restart.
//   2. Keeping the persisted record small means a profile set stays
//      portable — a user can ship their ProfileSet JSON to a teammate
//      without leaking file IDs that were issued to their API keys.
// ============================================================
public final class FileAttachment implements ContextAttachment {

    // Absolute path to the file on disk. Source of truth — on every
    // turn the uploader re-checks the file exists at this path before
    // deciding whether to reuse a cached provider ID or re-upload.
    private String localPath;

    // e.g. "application/pdf", "text/plain". Detected once on attach
    // via Files.probeContentType and stored here so the upload body
    // doesn't have to re-probe on every turn.
    private String mimeType;

    // Short human-readable label shown in the UI and sent to providers
    // that accept a display name (e.g. Gemini's "displayName" field).
    // Defaults to the file's basename if the caller doesn't override.
    private String displayName;

    // Gson no-arg ctor.
    FileAttachment() {}

    public FileAttachment(String localPath, String mimeType, String displayName) {
        this.localPath = localPath;
        this.mimeType = mimeType;
        this.displayName = displayName;
    }

    public String getLocalPath() { return localPath; }
    public String getMimeType() { return mimeType; }
    public String getDisplayName() { return displayName; }

    public void setLocalPath(String localPath) { this.localPath = localPath; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    // ============================================================
    // fromPath() — Convenience factory that fills sensible defaults.
    //
    // Probes MIME type from the filesystem; falls back to
    // "application/octet-stream" when the OS can't identify the type
    // (rare for common formats like PDF / TXT / MD, but possible on
    // bare container setups). Uses the basename as the display name
    // so the UI shows "Q3-earnings.pdf" rather than a 200-char path.
    // ============================================================
    public static FileAttachment fromPath(Path path) {
        String mime = null;
        try {
            mime = Files.probeContentType(path);
        } catch (Exception ignored) {
            // Probing can throw on unreadable files; caller will hit
            // the same error again at upload time with a clearer
            // message, so swallow here and use the generic default.
        }
        if (mime == null || mime.isBlank()) {
            mime = "application/octet-stream";
        }
        String baseName = path.getFileName() == null
                ? path.toString()
                : path.getFileName().toString();
        return new FileAttachment(path.toAbsolutePath().toString(), mime, baseName);
    }
}
