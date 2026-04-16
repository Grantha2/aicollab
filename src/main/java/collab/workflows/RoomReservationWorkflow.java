package collab.workflows;

import collab.AgenticTask;
import collab.AgenticTaskContext;
import collab.AnthropicClient;
import collab.ChatMessage;
import collab.LlmClient;
import collab.LlmRequest;
import collab.StatefulResponse;
import collab.ToolExecutor;
import collab.ToolSchema;

import javax.swing.*;
import java.awt.*;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

// ============================================================
// RoomReservationWorkflow.java — End-to-end agentic workflow for
// reserving an on-campus room.
//
// WHAT THIS TASK DOES (one sentence):
// Collects the user's event details via a small intent dialog, runs
// a Claude panelist with the four room-workflow tools registered, and
// displays the resulting text + filled-PDF path + email draft in the
// agentic panel's function-output area.
//
// HOW IT FITS THE ARCHITECTURE:
// Implements AgenticTask so it registers alongside the built-in
// tasks on the Agentic Routines view. When the user clicks "Reserve a
// Room", execute() opens the intent dialog, assembles a ToolExecutor
// from:
//   - ComputerUseToolProxy  (three computer-use tools, sandbox-backed)
//   - RoomAvailabilityTool  (check_room_availability)
//   - PdfFillTool           (fill_room_request_form)
//   - EmailDraftTool        (draft_email)
// then kicks off one Claude turn with all of them available.
//
// TRUST GATES:
//   - The email draft tool never sends; the user is shown the draft
//     and must copy/paste or authorise send separately. This is
//     deliberate: an AI should not send email on the user's behalf
//     without a pressed button.
//   - The PDF fill tool writes to ~/aicollab-filled/ only. It never
//     overwrites the source assets/RSO-Facility-Request-Form.pdf.
//   - The computer-use sandbox is an opt-in external process
//     (docs/SANDBOX.md). If it isn't running, Claude gets tool-error
//     results and can still help the user textually.
// ============================================================
public final class RoomReservationWorkflow implements AgenticTask {

    @Override public String getId()          { return "reserve-room"; }
    @Override public String getName()        { return "Reserve a Room"; }
    @Override public String getDescription() {
        return "Check room availability, fill the RSO request form, "
                + "and draft the confirmation email — end to end.";
    }
    @Override public String getCategory()    { return "Operations"; }

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public void execute(AgenticTaskContext ctx) {
        Intent intent = promptForIntent(ctx);
        if (intent == null) {
            ctx.panel().setStatus("Room reservation cancelled.");
            return;
        }
        ctx.panel().setStatus("Preparing room-reservation tools...");

        // One ToolExecutor for this workflow. Registrations are local
        // to the workflow call so another task running at the same
        // time (unlikely in the Swing app, but defended against)
        // cannot see these tools.
        ToolExecutor executor = new ToolExecutor();
        HttpClient http = HttpClient.newHttpClient();

        ComputerUseToolProxy computerUse = new ComputerUseToolProxy(
                http, ctx.config().getProperty("computer.use.sandbox.url", ""));
        for (ToolSchema s : computerUse.schemas()) {
            executor.register(s, computerUse.handler());
        }

        RoomAvailabilityTool availability = new RoomAvailabilityTool(
                http, ctx.config().getProperty("room.availability.mode", "fixture"),
                null, null);
        executor.register(availability.schema(), availability.handler());

        PdfFillTool pdfFill = new PdfFillTool();
        executor.register(pdfFill.schema(), pdfFill.handler());

        EmailDraftTool emailDraft = new EmailDraftTool();
        executor.register(emailDraft.schema(), emailDraft.handler());

        // Build the panelist turn. The system instruction frames the
        // workflow so Claude reaches for the right tools; the user
        // message is a plain English statement of the user's intent.
        String systemInstruction = """
                You are the Room Reservation assistant for a student
                organization at UIC. You have tools to:

                  - check_room_availability: find free rooms that meet
                    the user's date/time/capacity constraints.
                  - fill_room_request_form: fill the RSO Facility
                    Request PDF with the user's event details.
                  - draft_email: produce a reviewable email draft
                    (never sent automatically).
                  - computer_20241022 / bash_20241022 /
                    text_editor_20241022: drive a sandboxed browser
                    via the computer-use sandbox when live availability
                    lookup is needed. Use these tools only when the
                    availability tool says live lookup is required.

                Work step by step. Ask the user short clarifying
                questions only when absolutely necessary. Stop once
                you have produced a filled PDF path and an email
                draft; the user will review both before sending.
                """;
        String userMessage = intent.toPromptBlock();

        LlmClient claude = new AnthropicClient(
                http,
                ctx.config().getClaudeUrl(),
                ctx.config().getClaudeKey(),
                ctx.config().getClaudeModel(),
                ctx.config().getMaxResponseTokens());

        LlmRequest request = new LlmRequest(
                systemInstruction,
                List.of(new ChatMessage("user", userMessage)),
                ctx.config().getMaxResponseTokens(),
                List.of(),   // no file attachments today; library lands in a later PR
                executor.schemas());

        ctx.panel().setStatus("Running room-reservation agent...");
        // Run off the EDT so the UI stays responsive while tools run.
        SwingWorker<StatefulResponse, Void> worker = new SwingWorker<>() {
            @Override
            protected StatefulResponse doInBackground() {
                return claude.sendStateful(request, null, executor);
            }

            @Override
            protected void done() {
                try {
                    StatefulResponse r = get();
                    StringBuilder output = new StringBuilder();
                    output.append("INTENT:\n").append(intent.toPromptBlock()).append("\n\n");
                    output.append("AGENT RESPONSE:\n").append(r.text()).append("\n");
                    ctx.panel().showFunctionOutput("Room Reservation", output.toString());
                    ctx.panel().setStatus("Room-reservation workflow complete.");
                } catch (Exception e) {
                    ctx.panel().showFunctionOutput("Room Reservation — failed",
                            "Workflow failed: " + e.getMessage());
                    ctx.panel().setStatus("Room-reservation workflow failed.");
                }
            }
        };
        worker.execute();
    }

    // ============================================================
    // promptForIntent() — Tiny Swing modal collecting the required
    // event details. Returns null if the user cancels.
    //
    // WHY A PLAIN DIALOG AND NOT A FANCY WIZARD:
    // The intent is a handful of fields; a one-screen dialog is the
    // least-friction capture. Any field the user leaves blank is
    // forwarded verbatim; Claude may ask a follow-up question or fill
    // a sensible default.
    // ============================================================
    private Intent promptForIntent(AgenticTaskContext ctx) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.fill = GridBagConstraints.HORIZONTAL;

        JTextField eventName = new JTextField(24);
        JTextField organization = new JTextField(24);
        JTextField date = new JTextField(12);
        JTextField start = new JTextField(6);
        JTextField end = new JTextField(6);
        JTextField attendance = new JTextField(6);
        JTextField layout = new JTextField(18);
        JTextField contactName = new JTextField(18);
        JTextField contactEmail = new JTextField(24);

        int row = 0;
        addRow(form, g, row++, "Event name", eventName);
        addRow(form, g, row++, "Organization", organization);
        addRow(form, g, row++, "Date (YYYY-MM-DD)", date);
        addRow(form, g, row++, "Start time (HH:MM)", start);
        addRow(form, g, row++, "End time (HH:MM)", end);
        addRow(form, g, row++, "Expected attendance", attendance);
        addRow(form, g, row++, "Preferred layout", layout);
        addRow(form, g, row++, "Contact name", contactName);
        addRow(form, g, row++, "Contact email", contactEmail);

        int result = JOptionPane.showConfirmDialog(ctx.panel(),
                form, "Reserve a Room — Intent",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;

        return new Intent(
                eventName.getText().trim(),
                organization.getText().trim(),
                date.getText().trim(),
                start.getText().trim(),
                end.getText().trim(),
                attendance.getText().trim(),
                layout.getText().trim(),
                contactName.getText().trim(),
                contactEmail.getText().trim());
    }

    private static void addRow(JPanel form, GridBagConstraints g, int row,
                               String label, JComponent field) {
        g.gridx = 0; g.gridy = row; g.weightx = 0.0;
        form.add(new JLabel(label + ":"), g);
        g.gridx = 1; g.gridy = row; g.weightx = 1.0;
        form.add(field, g);
    }

    /**
     * Captured intent from the dialog, shaped into a user-message
     * block the panelist can read.
     */
    private record Intent(String eventName, String organization, String date,
                          String startTime, String endTime, String attendance,
                          String layout, String contactName, String contactEmail) {
        String toPromptBlock() {
            StringBuilder sb = new StringBuilder();
            sb.append("I need to reserve a room on campus.\n");
            sb.append("- Event: ").append(eventName).append("\n");
            sb.append("- Organization: ").append(organization).append("\n");
            sb.append("- Date: ").append(date).append("\n");
            sb.append("- Time window: ").append(startTime).append(" to ").append(endTime).append("\n");
            sb.append("- Expected attendance: ").append(attendance).append("\n");
            sb.append("- Preferred layout: ").append(layout).append("\n");
            sb.append("- Contact: ").append(contactName);
            if (!contactEmail.isBlank()) sb.append(" <").append(contactEmail).append(">");
            sb.append("\n\nPlease check availability, fill the RSO request "
                    + "form with the matched room, and draft the confirmation "
                    + "email to the RSO office. I will review both before sending.");
            return sb.toString();
        }
    }
}
