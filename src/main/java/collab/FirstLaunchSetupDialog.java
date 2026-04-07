package collab;

// ============================================================
// FirstLaunchSetupDialog.java — First-launch setup wizard.
//
// WHAT THIS CLASS DOES (one sentence):
// Multi-step modal dialog shown on first launch to collect
// team context, agent identities, and stakeholder profiles
// from the user, replacing all hardcoded defaults.
// ============================================================

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class FirstLaunchSetupDialog extends JDialog {

    private boolean cancelled = true;

    // Step 1: Team context
    private final JTextArea teamContextArea = new JTextArea(8, 40);

    // Step 2: Agent identities
    private final DefaultListModel<AgentEntry> agentListModel = new DefaultListModel<>();
    private final JList<AgentEntry> agentList = new JList<>(agentListModel);

    // Step 3: Stakeholder profiles
    private final DefaultListModel<StakeholderEntry> stakeholderListModel = new DefaultListModel<>();
    private final JList<StakeholderEntry> stakeholderList = new JList<>(stakeholderListModel);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel stepsPanel = new JPanel(cardLayout);
    private final JButton backBtn = new JButton("Back");
    private final JButton nextBtn = new JButton("Next");
    private int currentStep = 0;
    private static final int TOTAL_STEPS = 3;

    public FirstLaunchSetupDialog(Frame owner) {
        super(owner, "Executive Suite — First Launch Setup", true);
        setSize(650, 520);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // Header
        JLabel header = new JLabel("Welcome to Executive Suite");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 4, 16));
        add(header, BorderLayout.NORTH);

        // Step panels
        stepsPanel.add(buildStep1(), "step1");
        stepsPanel.add(buildStep2(), "step2");
        stepsPanel.add(buildStep3(), "step3");
        add(stepsPanel, BorderLayout.CENTER);

        // Navigation buttons
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        backBtn.addActionListener(e -> navigateBack());
        nextBtn.addActionListener(e -> navigateNext());
        backBtn.setEnabled(false);
        navPanel.add(cancelBtn);
        navPanel.add(backBtn);
        navPanel.add(nextBtn);
        add(navPanel, BorderLayout.SOUTH);
    }

    // ======================== Step 1: Team Context ========================

    private JPanel buildStep1() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        JLabel instructions = new JLabel("<html><b>Step 1/3: Team & Collaboration Context</b><br>" +
            "Describe your team, mission, and how the AI panel should collaborate.<br>" +
            "This is shared with all AI agents as background context.</html>");
        instructions.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        panel.add(instructions, BorderLayout.NORTH);

        teamContextArea.setLineWrap(true);
        teamContextArea.setWrapStyleWord(true);
        teamContextArea.setText(
            "=== COLLABORATION CONTEXT ===\n" +
            "Describe your team, project, and goals here.\n" +
            "Example: You are AI collaborators helping a leadership team manage operations...\n"
        );
        panel.add(new JScrollPane(teamContextArea), BorderLayout.CENTER);

        return panel;
    }

    // ======================== Step 2: Agent Identities ========================

    private JPanel buildStep2() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        JLabel instructions = new JLabel("<html><b>Step 2/3: AI Agent Identities</b><br>" +
            "Add the AI agents that will participate in debates.<br>" +
            "Each agent needs a name, perspective, and detailed lens.</html>");
        instructions.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        panel.add(instructions, BorderLayout.NORTH);

        agentList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof AgentEntry ae) {
                    setText(ae.name + " — " + ae.perspective);
                }
                return this;
            }
        });
        panel.add(new JScrollPane(agentList), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton addBtn = new JButton("+ Add Agent");
        addBtn.addActionListener(e -> addAgent());
        JButton editBtn = new JButton("Edit");
        editBtn.addActionListener(e -> editAgent());
        JButton removeBtn = new JButton("Remove");
        removeBtn.addActionListener(e -> {
            int idx = agentList.getSelectedIndex();
            if (idx >= 0) agentListModel.remove(idx);
        });
        buttons.add(addBtn);
        buttons.add(editBtn);
        buttons.add(removeBtn);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    private void addAgent() {
        AgentEntry entry = showAgentEditor(null);
        if (entry != null) agentListModel.addElement(entry);
    }

    private void editAgent() {
        int idx = agentList.getSelectedIndex();
        if (idx < 0) return;
        AgentEntry existing = agentListModel.get(idx);
        AgentEntry updated = showAgentEditor(existing);
        if (updated != null) agentListModel.set(idx, updated);
    }

    private AgentEntry showAgentEditor(AgentEntry existing) {
        JTextField nameField = new JTextField(existing != null ? existing.name : "", 20);
        JTextField perspectiveField = new JTextField(existing != null ? existing.perspective : "", 20);
        JTextArea lensArea = new JTextArea(existing != null ? existing.lens : "", 5, 30);
        lensArea.setLineWrap(true);
        lensArea.setWrapStyleWord(true);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        form.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        form.add(new JLabel("Perspective:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(perspectiveField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        form.add(new JLabel("Lens:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1;
        form.add(new JScrollPane(lensArea), gbc);

        int result = JOptionPane.showConfirmDialog(this, form, "Agent Identity",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;
        if (nameField.getText().trim().isEmpty()) return null;

        return new AgentEntry(
            nameField.getText().trim(),
            perspectiveField.getText().trim(),
            lensArea.getText().trim()
        );
    }

    // ======================== Step 3: Stakeholders ========================

    private JPanel buildStep3() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        JLabel instructions = new JLabel("<html><b>Step 3/3: Stakeholder Profiles</b><br>" +
            "Add the people who will use this tool. Each stakeholder gets tailored AI advice.<br>" +
            "You can add more later from the profile editor.</html>");
        instructions.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        panel.add(instructions, BorderLayout.NORTH);

        stakeholderList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof StakeholderEntry se) {
                    setText(se.name + " — " + se.role);
                }
                return this;
            }
        });
        panel.add(new JScrollPane(stakeholderList), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton addBtn = new JButton("+ Add Stakeholder");
        addBtn.addActionListener(e -> addStakeholder());
        JButton editBtn = new JButton("Edit");
        editBtn.addActionListener(e -> editStakeholder());
        JButton removeBtn = new JButton("Remove");
        removeBtn.addActionListener(e -> {
            int idx = stakeholderList.getSelectedIndex();
            if (idx >= 0) stakeholderListModel.remove(idx);
        });
        buttons.add(addBtn);
        buttons.add(editBtn);
        buttons.add(removeBtn);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    private void addStakeholder() {
        StakeholderEntry entry = showStakeholderEditor(null);
        if (entry != null) stakeholderListModel.addElement(entry);
    }

    private void editStakeholder() {
        int idx = stakeholderList.getSelectedIndex();
        if (idx < 0) return;
        StakeholderEntry existing = stakeholderListModel.get(idx);
        StakeholderEntry updated = showStakeholderEditor(existing);
        if (updated != null) stakeholderListModel.set(idx, updated);
    }

    private StakeholderEntry showStakeholderEditor(StakeholderEntry existing) {
        JTextField nameField = new JTextField(existing != null ? existing.name : "", 20);
        JTextField roleField = new JTextField(existing != null ? existing.role : "", 20);
        JTextField focusField = new JTextField(existing != null ? existing.focusArea : "", 20);
        JTextField worksWithField = new JTextField(existing != null ? existing.worksWith : "", 20);
        JTextArea responsibilitiesArea = new JTextArea(existing != null ? existing.responsibilities : "", 3, 30);
        responsibilitiesArea.setLineWrap(true);
        responsibilitiesArea.setWrapStyleWord(true);
        JTextField kpisField = new JTextField(existing != null ? existing.evaluatedOn : "", 20);
        JTextArea backgroundArea = new JTextArea(existing != null ? existing.background : "", 3, 30);
        backgroundArea.setLineWrap(true);
        backgroundArea.setWrapStyleWord(true);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addFormRow(form, gbc, row++, "Name:", nameField);
        addFormRow(form, gbc, row++, "Role/Title:", roleField);
        addFormRow(form, gbc, row++, "Focus Area:", focusField);
        addFormRow(form, gbc, row++, "Works With:", worksWithField);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Responsibilities:"), gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 1;
        form.add(new JScrollPane(responsibilitiesArea), gbc);

        addFormRow(form, gbc, row++, "KPIs:", kpisField);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Background:"), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1;
        form.add(new JScrollPane(backgroundArea), gbc);

        int result = JOptionPane.showConfirmDialog(this, form, "Stakeholder Profile",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;
        if (nameField.getText().trim().isEmpty()) return null;

        return new StakeholderEntry(
            nameField.getText().trim(),
            roleField.getText().trim(),
            focusField.getText().trim(),
            worksWithField.getText().trim(),
            responsibilitiesArea.getText().trim(),
            kpisField.getText().trim(),
            backgroundArea.getText().trim()
        );
    }

    private void addFormRow(JPanel form, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(field, gbc);
    }

    // ======================== Navigation ========================

    private void navigateNext() {
        if (currentStep == TOTAL_STEPS - 1) {
            // Finish
            if (teamContextArea.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "Please provide team context before finishing.",
                    "Missing Context", JOptionPane.WARNING_MESSAGE);
                cardLayout.show(stepsPanel, "step1");
                currentStep = 0;
                updateNavButtons();
                return;
            }
            cancelled = false;
            dispose();
        } else {
            currentStep++;
            cardLayout.show(stepsPanel, "step" + (currentStep + 1));
            updateNavButtons();
        }
    }

    private void navigateBack() {
        if (currentStep > 0) {
            currentStep--;
            cardLayout.show(stepsPanel, "step" + (currentStep + 1));
            updateNavButtons();
        }
    }

    private void updateNavButtons() {
        backBtn.setEnabled(currentStep > 0);
        nextBtn.setText(currentStep == TOTAL_STEPS - 1 ? "Finish" : "Next");
    }

    // ======================== Results ========================

    public boolean wasCancelled() { return cancelled; }

    public ProfileSet buildProfileSet() {
        String teamContext = teamContextArea.getText().trim();

        List<AgentProfile> agents = new ArrayList<>();
        for (int i = 0; i < agentListModel.size(); i++) {
            AgentEntry ae = agentListModel.get(i);
            agents.add(new AgentProfile(ae.name, ae.perspective, ae.lens));
        }

        List<StakeholderProfile> stakeholders = new ArrayList<>();
        for (int i = 0; i < stakeholderListModel.size(); i++) {
            StakeholderEntry se = stakeholderListModel.get(i);
            stakeholders.add(new StakeholderProfile(
                se.name, se.role, se.focusArea, se.worksWith,
                se.responsibilities, se.evaluatedOn, se.background
            ));
        }

        return new ProfileSet("default", "User-configured profile", teamContext, agents, stakeholders);
    }

    // ======================== Data holders ========================

    record AgentEntry(String name, String perspective, String lens) {}
    record StakeholderEntry(String name, String role, String focusArea,
                            String worksWith, String responsibilities,
                            String evaluatedOn, String background) {}
}
