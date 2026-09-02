package cowork.workflows;

import cowork.config.AppPaths;
import cowork.llm.ToolCall;
import cowork.llm.ToolResult;
import cowork.llm.ToolSchema;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * "check_room_availability" tool with two modes. "fixture" reads assets/fixtures/
 * room_availability.html (offline, deterministic demo). "live" fetches UIC's EMS page,
 * which is ASP.NET WebForms and cannot be scraped statelessly, so it returns a preview plus
 * an instruction telling Claude to drive the form through the computer-use tools.
 */
public final class RoomAvailabilityTool {

    public static final String MODE_FIXTURE = "fixture";
    public static final String MODE_LIVE = "live";
    private static final String FIXTURE_ASSET = "fixtures/room_availability.html";
    private static final String DEFAULT_LIVE_URL = "https://emsenterprise.uic.edu/vems/BrowseForSpace.aspx";
    private static final Duration LIVE_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http;
    private final String mode;
    private final Path fixturePath;
    private final String liveUrl;

    public RoomAvailabilityTool(HttpClient http, String mode, Path fixturePath, String liveUrl) {
        this.http = http;
        this.mode = mode == null ? MODE_FIXTURE : mode;
        this.fixturePath = fixturePath == null ? AppPaths.asset(FIXTURE_ASSET) : fixturePath;
        this.liveUrl = liveUrl == null ? DEFAULT_LIVE_URL : liveUrl;
    }

    public ToolSchema schema() {
        return ToolSchema.stringParams(
                "check_room_availability",
                "Check which rooms are available at UIC at a given date, "
                        + "time window, and minimum capacity. Returns a list "
                        + "of candidate rooms with capacity and layout info.",
                List.of("date", "start_time", "end_time", "minimum_capacity"),
                Map.of(
                        "date", "Requested date (YYYY-MM-DD).",
                        "start_time", "Start time (HH:MM, 24h).",
                        "end_time", "End time (HH:MM, 24h).",
                        "minimum_capacity", "Minimum room capacity (integer)."
                ));
    }

    public Function<ToolCall, ToolResult> handler() {
        return this::check;
    }

    private ToolResult check(ToolCall call) {
        try {
            if (MODE_LIVE.equalsIgnoreCase(mode)) {
                return checkLive(call);
            }
            return checkFixture(call);
        } catch (Exception e) {
            return ToolResult.error(call.id(),
                    "check_room_availability failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private ToolResult checkFixture(ToolCall call) throws Exception {
        if (!Files.exists(fixturePath)) {
            return ToolResult.error(call.id(),
                    "Room-availability fixture not found at " + fixturePath.toAbsolutePath()
                            + ". The fixture is expected to be a simple HTML "
                            + "document listing rooms with capacity and layout.");
        }
        String html = Files.readString(fixturePath, StandardCharsets.UTF_8);
        // Tag stripping by regex is fine here: this only ever runs against a fixture we own.
        String text = html.replaceAll("(?s)<script.*?</script>", "")
                          .replaceAll("(?s)<style.*?</style>", "")
                          .replaceAll("<[^>]+>", " ")
                          .replaceAll("\\s+", " ")
                          .trim();
        int minCap = parseIntOrZero(call.arguments().get("minimum_capacity"));
        String summary = "Room availability for "
                + call.arguments().getOrDefault("date", "(unspecified date)")
                + " between " + call.arguments().getOrDefault("start_time", "?")
                + " and " + call.arguments().getOrDefault("end_time", "?")
                + " (min capacity " + minCap + "):\n"
                + text;
        return ToolResult.ok(call.id(), summary);
    }

    private ToolResult checkLive(ToolCall call) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(liveUrl))
                    .timeout(LIVE_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            String preview = resp.body() == null ? ""
                    : resp.body().substring(0, Math.min(400, resp.body().length()));
            return ToolResult.ok(call.id(),
                    "Live EMS page fetched (HTTP " + resp.statusCode() + "). "
                            + "Because BrowseForSpace.aspx uses ASP.NET postbacks, "
                            + "you must drive the form via computer_20241022 tool "
                            + "calls (screenshot the sandbox browser, type date "
                            + "fields, click Search, read results). First 400 chars "
                            + "of the initial response:\n\n" + preview);
        } catch (Exception e) {
            return ToolResult.error(call.id(),
                    "Could not reach live EMS: " + e.getMessage()
                            + " — falling back to fixture is recommended.");
        }
    }

    private static int parseIntOrZero(Object o) {
        if (o == null) return 0;
        try { return Integer.parseInt(o.toString().trim()); }
        catch (NumberFormatException nfe) { return 0; }
    }
}
