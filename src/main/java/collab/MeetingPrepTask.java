package collab;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class MeetingPrepTask implements AgenticTask {

    private final OperationalFeedStore feedStore;
    private final RelationshipStore relationshipStore;

    public MeetingPrepTask(OperationalFeedStore feedStore, RelationshipStore relationshipStore) {
        this.feedStore = feedStore;
        this.relationshipStore = relationshipStore;
    }

    @Override public String getId()          { return "meeting-prep"; }
    @Override public String getName()        { return "Meeting Prep"; }
    @Override public String getDescription() { return "Prepare briefing for an upcoming meeting"; }
    @Override public String getCategory()    { return "Meetings"; }
    @Override public boolean isAvailable()   { return true; }

    @Override
    public void execute(AgenticTaskContext ctx) {
        // Show input dialog: select meeting or enter manually
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(ctx.panel());
        MeetingInputDialog dialog = new MeetingInputDialog(owner, feedStore.getUpcomingMeetings(14));
        dialog.setVisible(true);
        if (dialog.wasCancelled()) return;

        String meetingTitle = dialog.getMeetingTitle();
        String attendees = dialog.getAttendees();
        String objectives = dialog.getObjectives();

        ctx.panel().setStatus("Preparing meeting brief...");
        ctx.panel().showLoading("Generating meeting prep for: " + meetingTitle);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                LlmClient client = new AnthropicClient(httpClient, ctx.config().getClaudeUrl(),
                        ctx.config().getClaudeKey(), ctx.config().getClaudeModel(),
                        ctx.config().getMaxResponseTokens());
                return client.sendMessage(buildPrompt(ctx, meetingTitle, attendees, objectives));
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    SwingUtilities.invokeLater(() -> {
                        ctx.panel().showFunctionOutput("Meeting Prep: " + meetingTitle, response);
                        ctx.panel().setStatus("Meeting prep complete.");
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        ctx.panel().showFunctionOutput("Error", "Failed: " + e.getMessage());
                        ctx.panel().setStatus("Error: " + e.getMessage());
                    });
                }
            }
        }.execute();
    }

    private String buildPrompt(AgenticTaskContext ctx, String title, String attendees, String objectives) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
            You are an executive assistant preparing a meeting brief.
            Produce a structured meeting prep document with:
            1. MEETING OVERVIEW — title, attendees, objectives
            2. SUGGESTED AGENDA — time-boxed items with owners
            3. TALKING POINTS — per attendee if known, key messages
            4. OPEN ITEMS — unresolved issues relevant to this meeting
            5. PREP CHECKLIST — what the leader should review/bring

            Be specific and actionable. Reference real context from the org data provided.

            """);

        prompt.append("=== MEETING DETAILS ===\n");
        prompt.append("Title: ").append(title).append("\n");
        prompt.append("Attendees: ").append(attendees).append("\n");
        prompt.append("Objectives: ").append(objectives).append("\n\n");

        prompt.append("=== ORGANIZATION CONTEXT ===\n");
        prompt.append(ctx.orgContext().buildContextBlock()).append("\n");

        // Include relationship context for attendees
        List<Relationship> relationships = relationshipStore.getAll();
        if (!relationships.isEmpty()) {
            prompt.append("=== KNOWN RELATIONSHIPS ===\n");
            for (Relationship rel : relationships) {
                prompt.append("- ").append(rel.toSummary()).append("\n");
            }
        }

        return prompt.toString();
    }

    // --- Inner dialog for meeting input ---
    static class MeetingInputDialog extends JDialog {
        private boolean cancelled = true;
        private final JTextField titleField;
        private final JTextField attendeesField = new JTextField(30);
        private final JTextArea objectivesArea = new JTextArea(3, 30);

        MeetingInputDialog(Frame owner, List<OperationalFeedItem> upcomingMeetings) {
            super(owner, "Meeting Prep", true);
            setLayout(new BorderLayout(8, 8));
            setSize(500, 350);
            setLocationRelativeTo(owner);

            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            // Initialize titleField early so combo listener can reference it
            titleField = new JTextField(30);

            // Meeting selector if there are upcoming meetings
            int row = 0;
            if (!upcomingMeetings.isEmpty()) {
                gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 2;
                JComboBox<String> meetingCombo = new JComboBox<>();
                meetingCombo.addItem("(Enter manually)");
                for (OperationalFeedItem m : upcomingMeetings) {
                    meetingCombo.addItem(m.getTitle() + " — " + m.getDate());
                }
                form.add(new JLabel("Select upcoming meeting or enter manually:"), gbc);
                gbc.gridy = ++row;
                form.add(meetingCombo, gbc);
                gbc.gridwidth = 1;

                meetingCombo.addActionListener(e -> {
                    int idx = meetingCombo.getSelectedIndex();
                    if (idx > 0) {
                        OperationalFeedItem m = upcomingMeetings.get(idx - 1);
                        titleField.setText(m.getTitle());
                        if (m.getAttendees() != null) attendeesField.setText(m.getAttendees());
                        if (m.getNotes() != null) objectivesArea.setText(m.getNotes());
                    }
                });
                row++;
            }

            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
            form.add(new JLabel("Meeting Title:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1;
            form.add(titleField, gbc);

            gbc.gridx = 0; gbc.gridy = ++row; gbc.weightx = 0;
            form.add(new JLabel("Attendees:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1;
            form.add(attendeesField, gbc);

            gbc.gridx = 0; gbc.gridy = ++row; gbc.weightx = 0;
            form.add(new JLabel("Objectives:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1;
            objectivesArea.setLineWrap(true);
            objectivesArea.setWrapStyleWord(true);
            form.add(new JScrollPane(objectivesArea), gbc);

            add(new JScrollPane(form), BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.addActionListener(e -> dispose());
            JButton goBtn = new JButton("Generate Prep");
            goBtn.addActionListener(e -> { cancelled = false; dispose(); });
            buttons.add(cancelBtn);
            buttons.add(goBtn);
            add(buttons, BorderLayout.SOUTH);
        }

        boolean wasCancelled() { return cancelled; }
        String getMeetingTitle() { return titleField.getText().trim(); }
        String getAttendees() { return attendeesField.getText().trim(); }
        String getObjectives() { return objectivesArea.getText().trim(); }
    }
}
