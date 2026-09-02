package cowork.tasks;

import cowork.data.OperationalFeedItem;
import cowork.data.OperationalFeedStore;
import cowork.data.Relationship;
import cowork.data.RelationshipStore;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

/**
 * "Meeting Prep" routine: the user picks an upcoming meeting from the feed (or types one),
 * then one Claude call produces agenda, talking points and a prep checklist grounded in
 * org context and known relationships.
 */
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
        MeetingInputDialog dialog = new MeetingInputDialog(TaskDialogs.owner(), feedStore.getUpcomingMeetings(14));
        dialog.setVisible(true);
        if (dialog.wasCancelled()) return;

        String meetingTitle = dialog.getMeetingTitle();
        String attendees = dialog.getAttendees();
        String objectives = dialog.getObjectives();

        ctx.output().setStatus("Preparing meeting brief: generating meeting prep for " + meetingTitle + "...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return ctx.clients().claude().sendMessage(buildPrompt(ctx, meetingTitle, attendees, objectives));
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    ctx.output().showOutput("Meeting Prep: " + meetingTitle, response);
                    ctx.output().setStatus("Meeting prep complete.");
                } catch (Exception e) {
                    ctx.output().showOutput("Error", "Failed: " + e.getMessage());
                    ctx.output().setStatus("Error: " + e.getMessage());
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

        List<Relationship> relationships = relationshipStore.getAll();
        if (!relationships.isEmpty()) {
            prompt.append("=== KNOWN RELATIONSHIPS ===\n");
            for (Relationship rel : relationships) {
                prompt.append("- ").append(rel.toSummary()).append("\n");
            }
        }

        return prompt.toString();
    }

    static class MeetingInputDialog extends JDialog {
        private boolean cancelled = true;
        private final JTextField titleField = new JTextField(30);
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
