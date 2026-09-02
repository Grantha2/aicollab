package cowork.workflows;

import cowork.llm.ToolCall;
import cowork.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PdfFillTool degrades to an error result when the source form is absent; no exception, no side effects. */
class PdfFillToolTest {

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        System.setProperty("cowork.home", tempDir.toString());
    }

    @Test
    void missingSourceUnderCoworkHomeIsAnErrorResult() {
        ToolResult result = new PdfFillTool().handler().apply(
            new ToolCall("c1", "fill_room_request_form", Map.of("event_name", "Hack Night")));

        assertTrue(result.isError());
        assertTrue(result.content().contains("Source PDF not found"));
        assertTrue(result.content().contains("RSO-Facility-Request-Form.pdf"));
    }

    @Test
    void missingExplicitSourceDoesNotCreateOutputDir() {
        Path outputDir = tempDir.resolve("filled");
        PdfFillTool tool = new PdfFillTool(tempDir.resolve("missing.pdf"), outputDir);

        ToolResult result = tool.handler().apply(new ToolCall("c2", "fill_room_request_form", Map.of()));

        assertTrue(result.isError());
        assertFalse(Files.exists(outputDir));
        assertEquals("fill_room_request_form", tool.schema().name());
    }

    @Test
    void expandHomeResolvesTilde() {
        Path home = Path.of(System.getProperty("user.home"));

        assertEquals(home.resolve("cowork-filled"), PdfFillTool.expandHome("~/cowork-filled"));
        assertEquals(home.resolve("cowork-filled"), PdfFillTool.expandHome(""));
        assertEquals(Path.of("/var/tmp/out"), PdfFillTool.expandHome("/var/tmp/out"));
    }
}
