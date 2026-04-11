package collab;

// ============================================================
// RoomBookingTask.java — Proof-of-concept agentic task.
//
// WHAT THIS TASK DOES (one sentence):
// Asks the user for event details, kicks off a Managed Agent that
// proposes available rooms, waits for the user to confirm one, and
// fills a shipped PDF template with the confirmed details via pypdf.
//
// TWO-TURN FLOW:
// The system prompt tells the agent to end its first turn with a
// recognizable AWAITING_CONFIRMATION: RoomA | RoomB | RoomC marker.
// AgenticRoutinesPanel.appendAgentEvent detects the marker, pops a
// radio dialog, and sends the user's choice as a follow-up event on
// the SAME session. The agent then fills the PDF and emits a file
// event the panel renders as a Download button.
//
// WHY THIS TASK IS THE POC:
// It exercises all three new subsystems end-to-end: the managed
// agent lifecycle, SSE streaming, Files API upload + download, and
// mid-session user confirmation. If this works, the rest of the
// agentic task library is straightforward.
// ============================================================

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RoomBookingTask extends AgenticModeTask {

    @Override
    public String getId() { return "room-booking"; }

    @Override
    public String getName() { return "Book Room"; }

    @Override
    public String getDescription() {
        return "Propose available rooms for an event, then fill a reservation PDF.";
    }

    @Override
    public String getCategory() { return "Events"; }

    @Override
    protected List<String> getTools() {
        // The agent needs bash to run pypdf and file_edit to tweak the filled form.
        return List.of("bash", "file_edit");
    }

    @Override
    protected List<String> getEnvironmentPackages() {
        return List.of("pypdf");
    }

    @Override
    protected List<Path> getInputFiles() {
        // Ship a blank template in the repo under templates/room_request.pdf.
        // If it's missing at runtime the base class silently skips it and
        // the agent can still propose rooms (just won't have a template to fill).
        return List.of(Path.of("templates/room_request.pdf"));
    }

    @Override
    protected String getSystemPrompt() {
        return """
               You are a room-booking assistant for an organization.
               Your job is to:
                 1. Review the event details supplied by the user.
                 2. Propose three candidate rooms that would fit the
                    event (you may reference typical campus or office
                    rooms if no explicit inventory is given).
                 3. END YOUR FIRST TURN with a single line in exactly
                    this format, so the app can pop a confirmation
                    dialog for the user:
                       AWAITING_CONFIRMATION: RoomA | RoomB | RoomC
                    Put each room name between the pipes. Do not add
                    anything after that line.
                 4. After the user replies "Confirmed: <room>", take
                    the attached PDF template (room_request.pdf) and
                    fill it with the event name, date, expected
                    attendance, special requirements, and the confirmed
                    room using pypdf. Save the filled form as
                    room_request_filled.pdf and make it downloadable
                    as a file output.
               Keep your responses tight and action-oriented.
               """;
    }

    @Override
    protected Map<String, String> collectInputs(AgenticTaskContext ctx) {
        JTextField nameField   = new JTextField();
        JTextField dateField   = new JTextField();
        JTextField countField  = new JTextField();
        JTextField reqField    = new JTextField();

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.add(new JLabel("Event name:"));        form.add(nameField);
        form.add(new JLabel("Date (YYYY-MM-DD):")); form.add(dateField);
        form.add(new JLabel("Expected attendance:")); form.add(countField);
        form.add(new JLabel("Special requirements:")); form.add(reqField);

        int result = JOptionPane.showConfirmDialog(
                ctx.panel(),
                form,
                "Book Room \u2014 Event Details",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;

        Map<String, String> inputs = new LinkedHashMap<>();
        inputs.put("eventName",   nameField.getText().trim());
        inputs.put("eventDate",   dateField.getText().trim());
        inputs.put("attendance",  countField.getText().trim());
        inputs.put("requirements", reqField.getText().trim());
        return inputs;
    }

    @Override
    protected String buildUserMessage(Map<String, String> inputs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Please propose rooms for the following event:\n\n");
        sb.append("  Event: ").append(inputs.getOrDefault("eventName", "")).append('\n');
        sb.append("  Date: ").append(inputs.getOrDefault("eventDate", "")).append('\n');
        sb.append("  Expected attendance: ").append(inputs.getOrDefault("attendance", "")).append('\n');
        sb.append("  Special requirements: ").append(inputs.getOrDefault("requirements", "")).append('\n');
        sb.append("\nA blank room_request.pdf template is attached. Propose three rooms, ")
          .append("then wait for my confirmation before filling the template.");
        return sb.toString();
    }
}
