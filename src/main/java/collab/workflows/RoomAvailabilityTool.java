package collab.workflows;

import collab.ToolCall;
import collab.ToolResult;
import collab.ToolSchema;

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

// ============================================================
// RoomAvailabilityTool.java — Check which on-campus rooms are free
// at a requested date/time/capacity.
//
// WHAT THIS CLASS DOES (one sentence):
// Registers a ToolSchema named "check_room_availability" whose handler
// either hits a local fixture HTML page (fast, deterministic, offline)
// or drives a browser via the computer-use sandbox to query UIC's EMS
// at emsenterprise.uic.edu/vems/BrowseForSpace.aspx.
//
// WHY TWO MODES:
// The live EMS page is ASP.NET WebForms with viewstate + postback +
// heavy JS. Plain HTTP scraping would require reverse-engineering
// the viewstate. The tool supports two modes:
//   - "fixture": reads assets/fixtures/room_availability.html and
//     parses the static table. Used for offline development and
//     when the demo needs to be deterministic.
//   - "live":    forwards the availability check to Claude's
//     computer-use tool loop so the browser in the sandbox fills
//     the form on the real EMS page and reads the results. Only
//     works when the sandbox is running.
//
// THE LIVE MODE IS A MARKER:
// This file only implements the fixture branch directly. The "live"
// branch returns instructions telling Claude to use the computer-use
// tools (which are registered in parallel by ComputerUseToolProxy).
// That means Claude, when asked to check availability in live mode,
// will issue computer_20241022 / bash_20241022 calls that the proxy
// forwards to the sandbox. The availability "tool" itself exists
// mostly to give Claude a high-level name to reach for.
// ============================================================
public final class RoomAvailabilityTool {

    public static final String MODE_FIXTURE = "fixture";
    public static final String MODE_LIVE = "live";
    private static final Duration LIVE_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http;
    private final String mode;
    private final Path fixturePath;
    private final String liveUrl;

    public RoomAvailabilityTool(HttpClient http, String mode, Path fixturePath, String liveUrl) {
        this.http = http;
        this.mode = mode == null ? MODE_FIXTURE : mode;
        this.fixturePath = fixturePath == null
                ? Path.of("assets", "fixtures", "room_availability.html")
                : fixturePath;
        this.liveUrl = liveUrl == null
                ? "https://emsenterprise.uic.edu/vems/BrowseForSpace.aspx"
                : liveUrl;
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
                    "check_room_availability failed: "
                            + e.getClass().getSimpleName() + " " + e.getMessage());
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
        // Extract visible text between tags with a naive regex. This
        // is only ever run against a fixture we own; it's not intended
        // to parse arbitrary third-party HTML safely.
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
        // Live mode is cooperative: we can't drive an ASP.NET WebForms
        // page from a plain HTTP call. Instead we hand Claude an
        // instruction string that nudges it to use the computer_20241022
        // tool to operate the sandbox browser. This works because the
        // sandbox and Claude share a conversation and computer-use
        // tools are registered alongside this one. When the model
        // completes its browser automation, it calls this tool again
        // with mode=fixture to parse the result — or emits the answer
        // directly in text.
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
