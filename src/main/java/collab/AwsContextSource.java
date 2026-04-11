package collab;

// ============================================================
// AwsContextSource.java — Cloud-backed ContextSource implementation.
//
// WHAT THIS CLASS DOES (one sentence):
// Reads and writes OrganizationContext via an AWS Lambda + API Gateway
// endpoint backed by MongoDB, so every member of an organization sees
// the same shared context.
//
// HOW IT FITS:
// LocalContextSource keeps each user's context in a local JSON file.
// AwsContextSource is the drop-in replacement for a team deployment:
// it hits a shared endpoint (one document per orgId) and caches the
// last-fetched copy in memory so the UI stays responsive if the
// network hiccups.
//
// WIRE-UP:
// Activated by MainGui when aws.context.enabled=true in
// config.properties. Swapped in via ContextController.setContextSource.
// The rest of the app is unchanged.
//
// PROTOCOL:
//   GET  {baseUrl}/context?orgId={orgId}  -> 200 + OrganizationContext JSON
//   POST {baseUrl}/context                -> body is OrganizationContext JSON
// Both require the header x-api-key: {sharedKey}. Auth is v1-grade —
// see the plan for hardening notes.
// ============================================================

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class AwsContextSource implements ContextSource {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String orgId;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Last successfully fetched context. If the network is down we fall
    // through to this so the UI keeps working instead of blowing up.
    private OrganizationContext cached;

    public AwsContextSource(HttpClient httpClient,
                            String baseUrl,
                            String apiKey,
                            String orgId) {
        this.httpClient = httpClient;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.orgId = orgId;
    }

    // ============================================================
    // get() — Fetch the shared context from the cloud.
    //
    // On network / non-200: log and return the cached copy (or an
    // empty OrganizationContext if we never fetched successfully).
    // ============================================================
    @Override
    public OrganizationContext get() {
        try {
            String encodedOrg = URLEncoder.encode(orgId, StandardCharsets.UTF_8);
            URI uri = URI.create(baseUrl + "/context?orgId=" + encodedOrg);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("x-api-key", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("[AwsContextSource] GET returned "
                        + response.statusCode() + ": " + response.body());
                return fallback();
            }

            // Reuse the canonical deserializer so migration logic stays in one place.
            OrganizationContext ctx = OrganizationContext.deserialize(response.body());
            cached = ctx;
            return ctx;

        } catch (Exception e) {
            System.err.println("[AwsContextSource] GET failed: " + e.getMessage());
            return fallback();
        }
    }

    // ============================================================
    // save() — POST the full OrganizationContext to the cloud store.
    //
    // On any failure: log; we still update the cache so the UI is
    // consistent until the next successful round-trip.
    // ============================================================
    @Override
    public void save(OrganizationContext context) {
        if (context == null) return;
        this.cached = context;

        try {
            String body = GSON.toJson(context);
            String encodedOrg = URLEncoder.encode(orgId, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/context?orgId=" + encodedOrg))
                    .header("x-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("[AwsContextSource] POST returned "
                        + response.statusCode() + ": " + response.body());
            }

        } catch (Exception e) {
            System.err.println("[AwsContextSource] POST failed: " + e.getMessage());
        }
    }

    private OrganizationContext fallback() {
        if (cached != null) return cached;
        cached = new OrganizationContext();
        return cached;
    }

    private static String trimTrailingSlash(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
