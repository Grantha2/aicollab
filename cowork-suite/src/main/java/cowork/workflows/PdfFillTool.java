package cowork.workflows;

import cowork.config.AppPaths;
import cowork.llm.ToolCall;
import cowork.llm.ToolResult;
import cowork.llm.ToolSchema;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * "fill_room_request_form" tool: fills the AcroForm fields of the RSO Facility Request PDF
 * from the call arguments and writes a timestamped copy into the configured output
 * directory, returning its path (tool results are text, so a path beats base64 bytes). The
 * source asset is never modified; unknown keys are reported, not fatal.
 */
public final class PdfFillTool {

    private static final String SOURCE_ASSET = "RSO-Facility-Request-Form.pdf";
    private static final String DEFAULT_OUTPUT_DIR = "~/cowork-filled";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path sourcePdf;
    private final Path outputDir;

    public PdfFillTool() {
        this(AppPaths.asset(SOURCE_ASSET), expandHome(DEFAULT_OUTPUT_DIR));
    }

    public PdfFillTool(Path sourcePdf, Path outputDir) {
        this.sourcePdf = sourcePdf;
        this.outputDir = outputDir;
    }

    /** Resolves a configured directory, expanding a leading "~" to the user's home. */
    public static Path expandHome(String dir) {
        if (dir == null || dir.isBlank()) return expandHome(DEFAULT_OUTPUT_DIR);
        String trimmed = dir.trim();
        if (trimmed.equals("~")) return Path.of(System.getProperty("user.home"));
        if (trimmed.startsWith("~/") || trimmed.startsWith("~\\")) {
            return Path.of(System.getProperty("user.home"), trimmed.substring(2));
        }
        return Path.of(trimmed);
    }

    public ToolSchema schema() {
        return new ToolSchema(
                "fill_room_request_form",
                "Fill the RSO Facility Request PDF with the given event "
                        + "details. Arguments are field_name=value pairs. "
                        + "Returns the path of the filled PDF.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "event_name", Map.of("type", "string",
                                        "description", "Event or meeting name."),
                                "organization", Map.of("type", "string",
                                        "description", "Sponsoring organization."),
                                "requested_date", Map.of("type", "string",
                                        "description", "Event date (YYYY-MM-DD)."),
                                "start_time", Map.of("type", "string",
                                        "description", "Start time (HH:MM, 24h)."),
                                "end_time", Map.of("type", "string",
                                        "description", "End time (HH:MM, 24h)."),
                                "expected_attendance", Map.of("type", "string",
                                        "description", "Expected number of attendees."),
                                "room", Map.of("type", "string",
                                        "description", "Room name or identifier."),
                                "contact_name", Map.of("type", "string",
                                        "description", "Primary contact name."),
                                "contact_email", Map.of("type", "string",
                                        "description", "Primary contact email.")
                        )
                ));
    }

    public Function<ToolCall, ToolResult> handler() {
        return this::fill;
    }

    private ToolResult fill(ToolCall call) {
        if (!Files.exists(sourcePdf)) {
            return ToolResult.error(call.id(),
                    "Source PDF not found at " + sourcePdf.toAbsolutePath()
                            + ". Place the RSO form in the assets/ folder first.");
        }
        try {
            Files.createDirectories(outputDir);
            Path outputPath = outputDir.resolve(
                    "RSO-Request-" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + ".pdf");

            StringBuilder appliedLog = new StringBuilder();
            StringBuilder missedLog = new StringBuilder();
            try (PDDocument doc = Loader.loadPDF(sourcePdf.toFile())) {
                PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
                if (form == null) {
                    return ToolResult.error(call.id(),
                            "The source PDF has no AcroForm (it may be a flat scan). "
                                    + "Consider using the draft_email tool to share event "
                                    + "details textually instead.");
                }
                for (Map.Entry<String, Object> e : call.arguments().entrySet()) {
                    if (e.getValue() == null) continue;
                    String value = e.getValue().toString();
                    PDField field = findField(form, e.getKey());
                    if (field == null) {
                        missedLog.append(" - ").append(e.getKey()).append(" (no matching field)\n");
                        continue;
                    }
                    try {
                        field.setValue(value);
                        appliedLog.append(" - ").append(e.getKey()).append(" = ").append(value).append("\n");
                    } catch (Exception setFail) {
                        missedLog.append(" - ").append(e.getKey())
                                .append(" (setValue failed: ").append(setFail.getMessage()).append(")\n");
                    }
                }
                // Not flattened, so the user can still edit fields before sending.
                doc.save(outputPath.toFile());
            }

            StringBuilder result = new StringBuilder();
            result.append("Filled PDF written to: ").append(outputPath.toAbsolutePath()).append("\n");
            if (!appliedLog.isEmpty()) result.append("Applied:\n").append(appliedLog);
            if (!missedLog.isEmpty()) result.append("Not applied:\n").append(missedLog);
            result.append("Open with: xdg-open \"").append(outputPath).append("\" (macOS: open; Windows: start)");
            return ToolResult.ok(call.id(), result.toString());
        } catch (Exception e) {
            return ToolResult.error(call.id(),
                    "fill_room_request_form failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    /** Exact name, then case-insensitive, then "event_name" ~ "Event Name": Claude may guess any of them. */
    private static PDField findField(PDAcroForm form, String key) {
        PDField exact = form.getField(key);
        if (exact != null) return exact;
        List<PDField> all = form.getFields();
        for (PDField f : all) {
            if (f.getPartialName().equalsIgnoreCase(key)
                    || (f.getFullyQualifiedName() != null
                        && f.getFullyQualifiedName().equalsIgnoreCase(key))) {
                return f;
            }
        }
        String normalised = key.replace("_", "").replace(" ", "").toLowerCase();
        for (PDField f : all) {
            String candidate = f.getPartialName() == null ? ""
                    : f.getPartialName().replace("_", "").replace(" ", "").toLowerCase();
            if (candidate.equals(normalised)) return f;
        }
        return null;
    }
}
