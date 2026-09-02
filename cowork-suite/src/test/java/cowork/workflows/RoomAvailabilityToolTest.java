package cowork.workflows;

import cowork.llm.ToolCall;
import cowork.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fixture mode reads assets/fixtures/room_availability.html under cowork.home and lists the rooms. */
class RoomAvailabilityToolTest {

    private static final String FIXTURE_HTML = """
        <!DOCTYPE html><html><head><title>fixture</title>
        <style>table { border: 1px solid #999; }</style>
        <script>console.log("should be stripped");</script></head>
        <body><h1>Campus Room Availability</h1>
        <table><tr><th>Room</th><th>Capacity</th><th>Layout</th></tr>
        <tr><td>SCE 302</td><td>40</td><td>Lecture</td></tr>
        <tr><td>BSB 135</td><td>120</td><td>Theater</td></tr>
        </table></body></html>
        """;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        System.setProperty("cowork.home", tempDir.toString());
        Path fixtures = tempDir.resolve("assets").resolve("fixtures");
        Files.createDirectories(fixtures);
        Files.writeString(fixtures.resolve("room_availability.html"), FIXTURE_HTML);
    }

    @Test
    void fixtureModeParsesRoomsFromAssetUnderCoworkHome() {
        RoomAvailabilityTool tool = new RoomAvailabilityTool(null, "fixture", null, null);
        ToolCall call = new ToolCall("c1", "check_room_availability", Map.of(
            "date", "2026-10-01", "start_time", "14:00", "end_time", "16:00", "minimum_capacity", "40"));

        ToolResult result = tool.handler().apply(call);

        assertFalse(result.isError(), result.content());
        assertTrue(result.content().contains("Room availability for 2026-10-01 between 14:00 and 16:00 (min capacity 40)"));
        assertTrue(result.content().contains("SCE 302 40 Lecture"));
        assertTrue(result.content().contains("BSB 135 120 Theater"));
        assertFalse(result.content().contains("should be stripped"));
        assertFalse(result.content().contains("<td>"));
    }

    @Test
    void missingFixtureIsAnErrorResultNotAnException() {
        RoomAvailabilityTool tool = new RoomAvailabilityTool(null, "fixture", tempDir.resolve("nope.html"), null);

        ToolResult result = tool.handler().apply(new ToolCall("c2", "check_room_availability", Map.of()));

        assertTrue(result.isError());
        assertTrue(result.content().contains("fixture not found"));
    }
}
