package collab.workflows;

import collab.ToolCall;
import collab.ToolResult;
import collab.ToolSchema;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// ============================================================
// PdfFillTool.java — Fill the RSO facility request form PDF.
//
// WHAT THIS CLASS DOES (one sentence):
// Registers a ToolSchema named "fill_room_request_form" whose handler
// copies the source PDF (assets/RSO-Facility-Request-Form.pdf), fills
// its AcroForm fields from the arguments map, and writes the filled
// copy to the user's home directory under ~/aicollab-filled/.
//
// WHY THE TOOL WRITES TO DISK INSTEAD OF RETURNING BYTES:
// Tool results are text in all three provider tool protocols. Returning
// a base64 PDF would work but would bloat the conversation context and
// require the model to decide whether to echo it. A filesystem path is
// a small string that the email-draft tool (or the user's OS) can open
// directly. When we later add a binary-result channel, this tool can
// also include the bytes inline.
//
// FIELD-NAME STRATEGY:
// We accept arbitrary key=value pairs in the arguments map and iterate
// across the AcroForm trying to match each key to a field by exact or
// case-insensitive name. Unknown keys are reported in the result but
// do not fail the call — this keeps the tool tolerant of Claude's best
// effort at guessing the right field for each piece of event data.
//
// FALLBACK WHEN THE PDF IS NOT A FILLABLE ACROFORM:
// If the input PDF is a flat scan (no form fields), the tool returns
// an error result describing the shape and suggests the caller switch
// to the EmailDraftTool to communicate the event details textually.
// The debate continues.
// ============================================================
public final class PdfFillTool {

    private static final Path DEFAULT_SOURCE_PDF =
            Path.of("assets", "RSO-Facility-Request-Form.pdf");
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path sourcePdf;
    private final Path outputDir;

    public PdfFillTool() {
        this(DEFAULT_SOURCE_PDF,
                Path.of(System.getProperty("user.home"), "aicollab-filled"));
    }

    public PdfFillTool(Path sourcePdf, Path outputDir) {
        this.sourcePdf = sourcePdf;
        this.outputDir = outputDir;
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
                            + ". Commit the RSO form into assets/ first.");
        }
        try {
            Files.createDirectories(outputDir);
            Path outputPath = outputDir.resolve(
                    "RSO-Request-" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + ".pdf");

            try (PDDocument doc = Loader.loadPDF(sourcePdf.toFile())) {
                PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
                if (form == null) {
                    return ToolResult.error(call.id(),
                            "The source PDF has no AcroForm (it may be a flat scan). "
                                    + "Consider using the email_draft tool to share event "
                                    + "details textually instead.");
                }
                StringBuilder appliedLog = new StringBuilder();
                StringBuilder missedLog = new StringBuilder();
                for (Map.Entry<String, Object> e : call.arguments().entrySet()) {
                    if (e.getValue() == null) continue;
                    String value = e.getValue().toString();
                    PDField field = findField(form, e.getKey());
                    if (field == null) {
                        missedLog.append(" - ").append(e.getKey())
                                .append(" (no matching field)\n");
                        continue;
                    }
                    try {
                        field.setValue(value);
                        appliedLog.append(" - ").append(e.getKey())
                                .append(" = ").append(value).append("\n");
                    } catch (Exception setFail) {
                        missedLog.append(" - ").append(e.getKey())
                                .append(" (setValue failed: ")
                                .append(setFail.getMessage()).append(")\n");
                    }
                }
                // Flatten false so the user can still edit the fields
                // in Acrobat / Preview before sending. An unflattened
                // form is also smaller and easier to iterate on.
                doc.save(outputPath.toFile());
            }

            return ToolResult.ok(call.id(),
                    "Filled PDF written to: " + outputPath.toAbsolutePath()
                            + "\nOpen with: xdg-open \"" + outputPath + "\" "
                            + "(macOS: open; Windows: start)");
        } catch (Exception e) {
            return ToolResult.error(call.id(),
                    "fill_room_request_form failed: "
                            + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    /**
     * Resolve a field by exact name, then case-insensitive match.
     * Real RSO forms tend to use labels like "Event Name" or
     * "EventName" — Claude may guess either. Being tolerant here
     * makes the tool robust to prompt-driven variation.
     */
    private PDField findField(PDAcroForm form, String key) {
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
        // Loose match: "event_name" -> "Event Name"
        String normalised = key.replace("_", "").replace(" ", "").toLowerCase();
        for (PDField f : all) {
            String candidate = f.getPartialName() == null ? ""
                    : f.getPartialName().replace("_", "").replace(" ", "").toLowerCase();
            if (candidate.equals(normalised)) return f;
        }
        return null;
    }
}
