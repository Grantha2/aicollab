package collab;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// ============================================================
// FileUploader.java — Uploads a local FileAttachment to a provider's
// Files API and caches the returned provider-specific ID.
//
// WHAT THIS CLASS DOES (one sentence):
// On first request for a (Provider, localPath, apiKey) triple it
// POSTs the file to that provider's upload endpoint; subsequent
// requests for the same triple return the cached ID/URI without
// another upload.
//
// WHY A SEPARATE CLASS (not a method on each client):
// Every client is going to need the same caching, the same "does
// this file still exist on disk?" guard, and the same "upload
// returned non-200 → log and fall back" policy. Centralising it
// in one place means the three clients stay focused on their core
// concern — building the generation request body — and the one
// tricky bit (multipart HTTP uploads) has one implementation to
// audit and test.
//
// CACHING STRATEGY:
// Keyed by provider + file path + a cheap hash of the API key so two
// instances of the app pointing at different keys don't collide.
// In-memory only. IDs are NOT persisted to disk because:
//   - Anthropic/Gemini IDs expire (30 days / 48 hours).
//   - OpenAI IDs are per-account; sharing a profile set across
//     accounts would replay a stranger's IDs.
//   - The cache is process-lifetime — app restart = re-upload. That's
//     fine: a debate runs to completion in minutes; re-uploading once
//     at the start of a new process is acceptable cost.
//
// PHASE A SCOPE:
// Only file upload + cache. No chunking, no RAG, no MCP. Binary
// bodies are sent in one shot — adequate for the PDFs / memos / text
// files we expect in executive debates. If someone needs to attach a
// 2 GB dataset, this class will reject it with a clear error and
// point them at the later RAG phase.
// ============================================================
public final class FileUploader {

    // Cache of successfully uploaded file references. Key is the
    // cacheKey() tuple below; value is a provider-specific reference
    // (Anthropic/OpenAI: file_id; Gemini: fileUri) plus the MIME type
    // re-echoed from the upload response so callers don't have to
    // probe the filesystem again on every turn.
    private static final ConcurrentHashMap<String, UploadedRef> CACHE
            = new ConcurrentHashMap<>();

    // Hard cap for a single attachment. 20 MB is comfortably above a
    // typical earnings PDF and below every provider's per-file limit
    // (Anthropic 32 MB, OpenAI 512 MB, Gemini 2 GB — we pick the
    // smallest common denominator minus headroom).
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    // Slightly generous per-upload timeout — PDFs can be large enough
    // that a 10s default starves on slow links, but we don't want an
    // outright-dead connection to stall a whole debate.
    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(60);

    private FileUploader() {
        // Static utility class.
    }

    /**
     * Reference returned by a successful upload. Provider-agnostic
     * shape: the clients decode `ref` themselves — Anthropic/OpenAI
     * treat it as a file_id, Gemini treats it as a fileUri.
     */
    public record UploadedRef(String ref, String mimeType) {}

    // ============================================================
    // ensureUploaded() — Returns an UploadedRef for (provider, file)
    // pair, uploading if necessary.
    //
    // Thread-safe: two concurrent callers with the same key may both
    // see a cache miss and both perform the upload. That's acceptable
    // (idempotent on each provider) and simpler than synchronising on
    // the key. Once either finishes, subsequent callers hit the cache.
    //
    // Returns null on any upload failure. Clients should treat null
    // as "attachment unavailable this turn" and proceed without it,
    // preserving the debate-continues-on-degraded-capability rule
    // the stateful clients already follow.
    // ============================================================
    public static UploadedRef ensureUploaded(Provider provider,
                                             FileAttachment attachment,
                                             String apiKey,
                                             HttpClient http) {
        if (attachment == null || attachment.getLocalPath() == null
                || attachment.getLocalPath().isBlank()) {
            return null;
        }
        Path path = Paths.get(attachment.getLocalPath());
        if (!Files.isReadable(path)) {
            System.err.println("[FileUploader] Skipping unreadable file: " + path);
            return null;
        }

        long size;
        try {
            size = Files.size(path);
        } catch (IOException ioe) {
            System.err.println("[FileUploader] Cannot stat file: " + path
                    + " (" + ioe.getMessage() + ")");
            return null;
        }
        if (size > MAX_BYTES) {
            System.err.println("[FileUploader] File exceeds "
                    + (MAX_BYTES / (1024 * 1024)) + " MB cap, skipping: " + path);
            return null;
        }

        String key = cacheKey(provider, path, apiKey);
        UploadedRef cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        UploadedRef ref = switch (provider) {
            case ANTHROPIC -> uploadAnthropic(path, attachment, apiKey, http);
            case OPENAI -> uploadOpenAi(path, attachment, apiKey, http);
            case GOOGLE -> uploadGemini(path, attachment, apiKey, http);
        };
        if (ref != null) {
            CACHE.put(key, ref);
        }
        return ref;
    }

    private static String cacheKey(Provider provider, Path path, String apiKey) {
        int keyHash = apiKey == null ? 0 : apiKey.hashCode();
        return provider.name() + "\u001f" + keyHash + "\u001f" + path.toAbsolutePath();
    }

    // ============================================================
    // uploadAnthropic() — POST multipart to /v1/files with the
    // files-api beta header. Returns the string file_id.
    //
    // Auth: "x-api-key" + "anthropic-version" + "anthropic-beta".
    // Request form: multipart field "file" with the raw bytes.
    // Response: { "id": "file_…", "type": "file", … }.
    // ============================================================
    private static UploadedRef uploadAnthropic(Path path, FileAttachment att,
                                               String apiKey, HttpClient http) {
        try {
            byte[] body = path.toFile().exists()
                    ? Files.readAllBytes(path)
                    : new byte[0];
            String boundary = newBoundary();
            byte[] multipart = buildSingleFieldMultipart(
                    boundary, "file",
                    att.getDisplayName() == null ? path.getFileName().toString() : att.getDisplayName(),
                    att.getMimeType() == null ? "application/octet-stream" : att.getMimeType(),
                    body);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/files"))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("anthropic-beta", "files-api-2025-04-14")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(UPLOAD_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                System.err.println("[FileUploader] Anthropic upload HTTP "
                        + resp.statusCode() + ": " + resp.body());
                return null;
            }
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (!root.has("id")) return null;
            return new UploadedRef(root.get("id").getAsString(),
                    att.getMimeType());

        } catch (Exception e) {
            System.err.println("[FileUploader] Anthropic upload failed: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // uploadOpenAi() — POST multipart to /v1/files with purpose=user_data
    // (the Responses API's canonical "attach-this-to-a-message" purpose).
    //
    // Auth: "Authorization: Bearer …".
    // Request form: multipart with "purpose" field + "file" field.
    // Response: { "id": "file-…", "object": "file", … }.
    // ============================================================
    private static UploadedRef uploadOpenAi(Path path, FileAttachment att,
                                            String apiKey, HttpClient http) {
        try {
            byte[] body = Files.readAllBytes(path);
            String boundary = newBoundary();
            byte[] multipart = buildPurposeAndFileMultipart(
                    boundary, "user_data",
                    att.getDisplayName() == null ? path.getFileName().toString() : att.getDisplayName(),
                    att.getMimeType() == null ? "application/octet-stream" : att.getMimeType(),
                    body);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/files"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(UPLOAD_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                System.err.println("[FileUploader] OpenAI upload HTTP "
                        + resp.statusCode() + ": " + resp.body());
                return null;
            }
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (!root.has("id")) return null;
            return new UploadedRef(root.get("id").getAsString(),
                    att.getMimeType());

        } catch (Exception e) {
            System.err.println("[FileUploader] OpenAI upload failed: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // uploadGemini() — POST multipart to /upload/v1beta/files with
    // uploadType=multipart. The JSON metadata part gives the file a
    // display name; the binary part carries the bytes. Response is
    // { "file": { "name":"files/…", "uri":"https://…", "mimeType":"…" } }.
    // The "uri" is what generateContent / Interactions consume as
    // fileData.fileUri.
    // ============================================================
    private static UploadedRef uploadGemini(Path path, FileAttachment att,
                                            String apiKey, HttpClient http) {
        try {
            byte[] body = Files.readAllBytes(path);
            String boundary = newBoundary();
            String displayName = att.getDisplayName() == null
                    ? path.getFileName().toString() : att.getDisplayName();
            String mime = att.getMimeType() == null
                    ? "application/octet-stream" : att.getMimeType();

            // Gemini's multipart protocol is "multipart/related" with a
            // JSON metadata part followed by the raw media part.
            String metadata = "{\"file\":{\"display_name\":\""
                    + escapeJsonString(displayName) + "\"}}";

            StringBuilder header = new StringBuilder();
            header.append("--").append(boundary).append("\r\n");
            header.append("Content-Type: application/json; charset=UTF-8\r\n\r\n");
            header.append(metadata).append("\r\n");
            header.append("--").append(boundary).append("\r\n");
            header.append("Content-Type: ").append(mime).append("\r\n\r\n");

            String footer = "\r\n--" + boundary + "--\r\n";

            byte[] headerBytes = header.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] footerBytes = footer.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] multipart = new byte[headerBytes.length + body.length + footerBytes.length];
            System.arraycopy(headerBytes, 0, multipart, 0, headerBytes.length);
            System.arraycopy(body, 0, multipart, headerBytes.length, body.length);
            System.arraycopy(footerBytes, 0, multipart,
                    headerBytes.length + body.length, footerBytes.length);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/upload/v1beta/files?uploadType=multipart"))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "multipart/related; boundary=" + boundary)
                    .timeout(UPLOAD_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                System.err.println("[FileUploader] Gemini upload HTTP "
                        + resp.statusCode() + ": " + resp.body());
                return null;
            }
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonObject file = root.has("file") && root.get("file").isJsonObject()
                    ? root.getAsJsonObject("file") : root;
            if (!file.has("uri")) return null;
            String uri = file.get("uri").getAsString();
            String serverMime = file.has("mimeType") ? file.get("mimeType").getAsString() : mime;
            return new UploadedRef(uri, serverMime);

        } catch (Exception e) {
            System.err.println("[FileUploader] Gemini upload failed: " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------
    // Multipart body builders. Kept local so no new dependencies
    // (Apache HttpClient, OkHttp) are introduced for one upload path.
    // ------------------------------------------------------------

    private static byte[] buildSingleFieldMultipart(String boundary,
                                                    String fieldName,
                                                    String fileName,
                                                    String mime,
                                                    byte[] body) {
        StringBuilder header = new StringBuilder();
        header.append("--").append(boundary).append("\r\n");
        header.append("Content-Disposition: form-data; name=\"")
                .append(fieldName).append("\"; filename=\"")
                .append(sanitizeFileName(fileName)).append("\"\r\n");
        header.append("Content-Type: ").append(mime).append("\r\n\r\n");
        String footer = "\r\n--" + boundary + "--\r\n";

        return concat(
                header.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                body,
                footer.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] buildPurposeAndFileMultipart(String boundary,
                                                       String purpose,
                                                       String fileName,
                                                       String mime,
                                                       byte[] body) {
        StringBuilder header = new StringBuilder();
        header.append("--").append(boundary).append("\r\n");
        header.append("Content-Disposition: form-data; name=\"purpose\"\r\n\r\n");
        header.append(purpose).append("\r\n");
        header.append("--").append(boundary).append("\r\n");
        header.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(sanitizeFileName(fileName)).append("\"\r\n");
        header.append("Content-Type: ").append(mime).append("\r\n\r\n");
        String footer = "\r\n--" + boundary + "--\r\n";

        return concat(
                header.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                body,
                footer.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] concat(byte[] a, byte[] b, byte[] c) {
        byte[] out = new byte[a.length + b.length + c.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        System.arraycopy(c, 0, out, a.length + b.length, c.length);
        return out;
    }

    private static String newBoundary() {
        // Random token is enough — the body can't contain this unless
        // by astronomical coincidence, and if it did the server would
        // reject with a clear 400 rather than silently truncate.
        Random r = new Random();
        return "collab-boundary-" + Long.toHexString(r.nextLong())
                + Long.toHexString(r.nextLong());
    }

    private static String sanitizeFileName(String name) {
        // Multipart filenames get wrapped in quotes; strip any quotes
        // and CR/LF from the name so a malicious filename can't break
        // out of the disposition header.
        return name.replace("\"", "").replace("\r", "").replace("\n", "");
    }

    private static String escapeJsonString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
