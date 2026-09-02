package cowork.workflows;

import cowork.config.AppPaths;
import cowork.llm.ChatMessage;
import cowork.llm.LlmClient;
import cowork.llm.LlmRequest;
import cowork.llm.StatefulResponse;
import cowork.llm.ToolExecutor;
import cowork.llm.ToolSchema;
import cowork.tasks.AgenticTask;
import cowork.tasks.AgenticTaskContext;
import cowork.tasks.TaskDialogs;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * "Reserve a Room" routine: collects event details in a small dialog, then runs ONE Claude
 * turn with the room tools registered (availability lookup, RSO PDF fill, email draft and
 * sandboxed computer use). Nothing is sent or submitted on the user's behalf: the result is
 * a filled-PDF path and an email draft for the user to review.
 */
public final class RoomReservationWorkflow implements AgenticTask {

    private static final String SOURCE_PDF = "RSO-Facility-Request-Form.pdf";
    private static final String DEFAULT_OUTPUT_DIR = "~/cowork-filled";

    private static final String SYSTEM_INSTRUCTION = """
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

    @Override public String getId()          { return "reserve-room"; }
    @Override public String getName()        { return "Reserve a Room"; }
    @Override public String getDescription() {
        return "Check room availability, fill the RSO request form, "
                + "and draft the confirmation email — end to end.";
    }
    @Override public String getCategory()    { return "Operations"; }
    @Override public boolean isAvailable()   { return true; }

    @Override
    public void execute(AgenticTaskContext ctx) {
        Intent intent = promptForIntent();
        if (intent == null) {
            ctx.output().setStatus("Room reservation cancelled.");
            return;
        }
        ctx.output().setStatus("Preparing room-reservation tools...");

        ToolExecutor executor = registerTools(ctx);
        LlmClient claude = ctx.clients().claude();
        LlmRequest request = new LlmRequest(
                SYSTEM_INSTRUCTION,
                List.of(new ChatMessage("user", intent.toPromptBlock())),
                ctx.clients().maxTokens(),
                executor.schemas());

        ctx.output().setStatus("Running room-reservation agent...");
        new SwingWorker<StatefulResponse, Void>() {
            @Override
            protected StatefulResponse doInBackground() {
                return claude.sendStateful(request, null, executor);
            }

            @Override
            protected void done() {
                try {
                    StatefulResponse r = get();
                    String output = "INTENT:\n" + intent.toPromptBlock() + "\n\n"
                            + "AGENT RESPONSE:\n" + r.text() + "\n";
                    ctx.output().showOutput("Room Reservation", output);
                    ctx.output().setStatus("Room-reservation workflow complete.");
                } catch (Exception e) {
                    ctx.output().showOutput("Room Reservation — failed", "Workflow failed: " + e.getMessage());
                    ctx.output().setStatus("Room-reservation workflow failed.");
                }
            }
        }.execute();
    }

    /**
     * Registrations are local to this run so no other task can see these tools. The tool
     * transport talks to the local sandbox and the EMS page, not to Anthropic, so it is
     * separate from ClientFactory; a short connect timeout makes an absent sandbox fail fast.
     */
    private static ToolExecutor registerTools(AgenticTaskContext ctx) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        ToolExecutor executor = new ToolExecutor();

        ComputerUseToolProxy computerUse = new ComputerUseToolProxy(
                http, ctx.config().getProperty("computer.use.sandbox.url", ""));
        for (ToolSchema s : computerUse.schemas()) {
            executor.register(s, computerUse.handler());
        }

        RoomAvailabilityTool availability = new RoomAvailabilityTool(
                http, ctx.config().getProperty("room.availability.mode", RoomAvailabilityTool.MODE_FIXTURE),
                null, null);
        executor.register(availability.schema(), availability.handler());

        Path outputDir = PdfFillTool.expandHome(ctx.config().getProperty("pdf.output.dir", DEFAULT_OUTPUT_DIR));
        PdfFillTool pdfFill = new PdfFillTool(AppPaths.asset(SOURCE_PDF), outputDir);
        executor.register(pdfFill.schema(), pdfFill.handler());

        EmailDraftTool emailDraft = new EmailDraftTool();
        executor.register(emailDraft.schema(), emailDraft.handler());
        return executor;
    }

    /** One-screen intent capture; blank fields are forwarded verbatim for Claude to query or default. */
    private static Intent promptForIntent() {
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
        addRow(form, g, row, "Contact email", contactEmail);

        int result = JOptionPane.showConfirmDialog(TaskDialogs.owner(),
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
