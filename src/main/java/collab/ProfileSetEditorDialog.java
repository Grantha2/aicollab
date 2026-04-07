package collab;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ProfileSetEditorDialog extends JDialog {

    private final JTextField nameField = new JTextField(24);
    private final JTextField descriptionField = new JTextField(24);
    private final JTextArea teamContextArea = new JTextArea(8, 40);

    private final List<JTextField> agentNameFields = new ArrayList<>();
    private final List<JTextField> agentPerspectiveFields = new ArrayList<>();
    private final List<JTextArea> agentLensAreas = new ArrayList<>();

    private ProfileSet profileSet;

    public ProfileSetEditorDialog(Frame owner, ProfileSet seed) {
        super(owner, "Create Profile Set", true);
        setLayout(new BorderLayout(8, 8));

        add(buildMainPanel(seed), BorderLayout.CENTER);
        add(buildButtons(seed), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private JComponent buildMainPanel(ProfileSet seed) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel meta = new JPanel(new GridLayout(3, 2, 6, 6));
        meta.setBorder(BorderFactory.createTitledBorder("Profile Set Metadata"));
        meta.add(new JLabel("Set name"));
        meta.add(nameField);
        meta.add(new JLabel("Description"));
        meta.add(descriptionField);
        meta.add(new JLabel("Team context"));

        teamContextArea.setLineWrap(true);
        teamContextArea.setWrapStyleWord(true);
        meta.add(new JScrollPane(teamContextArea));

        if (seed != null) {
            nameField.setText(seed.getName() == null ? "" : seed.getName());
            descriptionField.setText(seed.getDescription() == null ? "" : seed.getDescription());
            teamContextArea.setText(seed.getTeamContext() == null ? "" : seed.getTeamContext());
        }

        JPanel agentsPanel = new JPanel();
        agentsPanel.setLayout(new BoxLayout(agentsPanel, BoxLayout.Y_AXIS));
        agentsPanel.setBorder(BorderFactory.createTitledBorder("Agent Profiles"));

        List<AgentProfile> seedAgents = (seed != null && seed.getAgents() != null)
                ? seed.getAgents()
                : AgentProfile.getDefaults();

        int agentCount = Math.max(seedAgents.size(), 1); // at least 1 empty slot
        for (int i = 0; i < agentCount; i++) {
            AgentProfile ap = i < seedAgents.size() ? seedAgents.get(i) : new AgentProfile("", "", "");
            JTextField name = new JTextField(ap.getName(), 18);
            JTextField perspective = new JTextField(ap.getPerspective(), 18);
            JTextArea lens = new JTextArea(ap.getLens(), 5, 40);
            lens.setLineWrap(true);
            lens.setWrapStyleWord(true);

            agentNameFields.add(name);
            agentPerspectiveFields.add(perspective);
            agentLensAreas.add(lens);

            JPanel row = new JPanel(new BorderLayout(6, 6));
            row.setBorder(BorderFactory.createTitledBorder("Agent " + (i + 1)));

            JPanel top = new JPanel(new GridLayout(2, 2, 4, 4));
            top.add(new JLabel("Name"));
            top.add(name);
            top.add(new JLabel("Perspective"));
            top.add(perspective);

            row.add(top, BorderLayout.NORTH);
            row.add(new JScrollPane(lens), BorderLayout.CENTER);
            agentsPanel.add(row);
        }

        // Add Agent button
        JButton addAgentBtn = new JButton("+ Add Agent");
        addAgentBtn.setAlignmentX(LEFT_ALIGNMENT);
        addAgentBtn.addActionListener(e -> {
            JTextField name = new JTextField("", 18);
            JTextField perspective = new JTextField("", 18);
            JTextArea lens = new JTextArea("", 5, 40);
            lens.setLineWrap(true);
            lens.setWrapStyleWord(true);

            agentNameFields.add(name);
            agentPerspectiveFields.add(perspective);
            agentLensAreas.add(lens);

            int idx = agentNameFields.size();
            JPanel row = new JPanel(new BorderLayout(6, 6));
            row.setBorder(BorderFactory.createTitledBorder("Agent " + idx));

            JPanel top = new JPanel(new GridLayout(2, 2, 4, 4));
            top.add(new JLabel("Name"));
            top.add(name);
            top.add(new JLabel("Perspective"));
            top.add(perspective);

            row.add(top, BorderLayout.NORTH);
            row.add(new JScrollPane(lens), BorderLayout.CENTER);

            // Insert before the button
            agentsPanel.add(row, agentsPanel.getComponentCount() - 1);
            agentsPanel.revalidate();
            agentsPanel.repaint();
        });
        agentsPanel.add(addAgentBtn);

        panel.add(meta, BorderLayout.NORTH);
        panel.add(new JScrollPane(agentsPanel), BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildButtons(ProfileSet seed) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> onSave(seed));
        cancelButton.addActionListener(e -> dispose());

        panel.add(saveButton);
        panel.add(cancelButton);
        return panel;
    }

    private void onSave(ProfileSet seed) {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Profile set name is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<AgentProfile> agents = new ArrayList<>();
        for (int i = 0; i < agentNameFields.size(); i++) {
            String agentName = agentNameFields.get(i).getText().trim();
            if (agentName.isEmpty()) continue; // skip empty agent slots
            agents.add(new AgentProfile(
                    agentName,
                    agentPerspectiveFields.get(i).getText().trim(),
                    agentLensAreas.get(i).getText().trim()));
        }

        List<StakeholderProfile> stakeholders = seed != null && seed.getStakeholders() != null
                ? seed.getStakeholders()
                : StakeholderProfile.getDefaults();

        profileSet = new ProfileSet(
                name,
                descriptionField.getText().trim(),
                teamContextArea.getText(),
                agents,
                stakeholders);
        dispose();
    }

    public ProfileSet getProfileSet() {
        return profileSet;
    }
}
