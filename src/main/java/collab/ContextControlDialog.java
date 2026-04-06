package collab;

// ============================================================
// ContextControlDialog.java — 5-tab dialog for granular control
// over every context layer sent to the AI models.
//
// WHAT THIS CLASS DOES (one sentence):
// Shows a tabbed dialog where users can toggle, edit, and preview
// every layer of the Context Layering Architecture.
// ============================================================

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ContextControlDialog extends JDialog {

    private final ContextController controller;
    private final ConversationContext context;
    private final ProfileSet activeProfileSet;

    public ContextControlDialog(Frame owner, ContextController controller,
                                ConversationContext context, ProfileSet activeProfileSet) {
        super(owner, "Context Control", true);
        this.controller = controller;
        this.context = context;
        this.activeProfileSet = activeProfileSet;

        setSize(700, 500);
        setLocationRelativeTo(owner);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Team Context", buildTeamContextTab());
        tabs.addTab("Agent Identities", buildAgentIdentitiesTab());
        tabs.addTab("Stakeholder", buildStakeholderTab());
        tabs.addTab("Organization", buildOrgContextTab());
        tabs.addTab("Task Context", buildTaskContextTab());
        tabs.addTab("History", buildHistoryTab());
        tabs.addTab("Prompt Preview", buildPreviewTab());

        add(tabs, BorderLayout.CENTER);

        // Preset buttons at bottom
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(new JLabel("Presets:"));
        JButton fullBtn = new JButton("Full Context");
        fullBtn.addActionListener(e -> applyPreset(ContextPreset.fullContext()));
        JButton minBtn = new JButton("Minimal");
        minBtn.addActionListener(e -> applyPreset(ContextPreset.minimal()));
        JButton noHistBtn = new JButton("No History");
        noHistBtn.addActionListener(e -> applyPreset(ContextPreset.noHistory()));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        bottomPanel.add(fullBtn);
        bottomPanel.add(minBtn);
        bottomPanel.add(noHistBtn);
        bottomPanel.add(Box.createHorizontalGlue());
        bottomPanel.add(closeBtn);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel buildTeamContextTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JCheckBox toggle = new JCheckBox("Include team context in prompts",
                controller.shouldIncludeTeamContext());
        toggle.addActionListener(e -> controller.setIncludeTeamContext(toggle.isSelected()));
        panel.add(toggle, BorderLayout.NORTH);

        String currentText = controller.getTeamContextOverride() != null
                ? controller.getTeamContextOverride()
                : PromptBuilder.DEFAULT_TEAM_CONTEXT;
        JTextArea textArea = new JTextArea(currentText);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton applyBtn = new JButton("Apply Override");
        applyBtn.addActionListener(e -> {
            String text = textArea.getText().trim();
            controller.setTeamContextOverride(text.isEmpty() ? null : text);
        });
        JButton resetBtn = new JButton("Reset to Default");
        resetBtn.addActionListener(e -> {
            textArea.setText(PromptBuilder.DEFAULT_TEAM_CONTEXT);
            controller.setTeamContextOverride(null);
        });
        buttons.add(applyBtn);
        buttons.add(resetBtn);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildAgentIdentitiesTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JCheckBox masterToggle = new JCheckBox("Include agent identities in prompts",
                controller.shouldIncludeAgentIdentity());
        masterToggle.addActionListener(e ->
                controller.setIncludeAgentIdentity(masterToggle.isSelected()));
        panel.add(masterToggle, BorderLayout.NORTH);

        JPanel agentsPanel = new JPanel(new GridLayout(1, 3, 8, 0));
        if (activeProfileSet != null && activeProfileSet.getAgents() != null) {
            for (AgentProfile agent : activeProfileSet.getAgents()) {
                agentsPanel.add(buildAgentCard(agent));
            }
        }
        panel.add(new JScrollPane(agentsPanel), BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildAgentCard(AgentProfile agent) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBorder(BorderFactory.createTitledBorder(agent.getName()));

        JCheckBox toggle = new JCheckBox("Include",
                controller.shouldIncludeAgent(agent.getName()));
        toggle.addActionListener(e ->
                controller.setAgentToggle(agent.getName(), toggle.isSelected()));
        card.add(toggle, BorderLayout.NORTH);

        JTextArea briefing = new JTextArea(agent.toBriefing());
        briefing.setEditable(false);
        briefing.setLineWrap(true);
        briefing.setWrapStyleWord(true);
        briefing.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        card.add(new JScrollPane(briefing), BorderLayout.CENTER);

        return card;
    }

    private JPanel buildStakeholderTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JCheckBox toggle = new JCheckBox("Include stakeholder context in prompts",
                controller.shouldIncludeStakeholderProfile());
        toggle.addActionListener(e ->
                controller.setIncludeStakeholderProfile(toggle.isSelected()));
        panel.add(toggle, BorderLayout.NORTH);

        JPanel fieldsPanel = new JPanel(new GridLayout(0, 1, 4, 4));
        if (activeProfileSet != null && activeProfileSet.getStakeholders() != null) {
            for (StakeholderProfile sp : activeProfileSet.getStakeholders()) {
                JPanel row = new JPanel(new BorderLayout());
                row.setBorder(BorderFactory.createTitledBorder(
                        sp.getName() + " — " + sp.getRole()));
                JTextArea briefing = new JTextArea(sp.toBriefing());
                briefing.setEditable(false);
                briefing.setLineWrap(true);
                briefing.setWrapStyleWord(true);
                briefing.setRows(4);
                row.add(new JScrollPane(briefing), BorderLayout.CENTER);
                fieldsPanel.add(row);
            }
        }
        panel.add(new JScrollPane(fieldsPanel), BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildOrgContextTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        OrganizationContext orgCtx = controller.getOrganizationContext();

        JCheckBox toggle = new JCheckBox("Include organization context in prompts",
                controller.shouldIncludeOrgContext());
        toggle.addActionListener(e -> controller.setIncludeOrgContext(toggle.isSelected()));
        panel.add(toggle, BorderLayout.NORTH);

        // Scrollable form of all org context fields
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        String[][] fields = {
            {"Last Updated", orgCtx.getLastUpdated()},
            {"What Changed Since Last Update", orgCtx.getWhatChangedSinceLastUpdate()},
            {"Current Term / Date Range", orgCtx.getCurrentTermDateRange()},
            {"Top Priorities", orgCtx.getTopPriorities()},
            {"Active Initiatives and Status", orgCtx.getActiveInitiativesAndStatus()},
            {"Upcoming Deadlines and Events", orgCtx.getUpcomingDeadlinesAndEvents()},
            {"Current Metrics", orgCtx.getCurrentMetrics()},
            {"Officer Roster and Ownership", orgCtx.getOfficerRosterAndOwnership()},
            {"Key Partners / Stakeholders", orgCtx.getKeyPartnersStakeholders()},
            {"Current Blockers / Risks", orgCtx.getCurrentBlockersRisks()},
            {"Pending Decisions", orgCtx.getPendingDecisions()},
            {"Preferred Tone / Style", orgCtx.getPreferredToneStyle()},
        };

        java.util.List<JTextArea> fieldAreas = new java.util.ArrayList<>();
        for (String[] f : fields) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
            form.add(new JLabel(f[0] + ":"), gbc);
            JTextArea area = new JTextArea(f[1] != null ? f[1] : "", 2, 30);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            fieldAreas.add(area);
            gbc.gridx = 1; gbc.weightx = 1;
            form.add(new JScrollPane(area), gbc);
            row++;
        }

        // Member profiles section
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.weightx = 1;
        JPanel profilesPanel = new JPanel(new BorderLayout(4, 4));
        profilesPanel.setBorder(BorderFactory.createTitledBorder("Member/Officer Profiles"));
        DefaultListModel<String> profileModel = new DefaultListModel<>();
        if (orgCtx.getMemberProfiles() != null) {
            for (OrganizationContext.MemberProfile mp : orgCtx.getMemberProfiles()) {
                profileModel.addElement(mp.getName() + " - " + mp.getRole());
            }
        }
        JList<String> profileList = new JList<>(profileModel);
        profileList.setVisibleRowCount(4);
        profilesPanel.add(new JScrollPane(profileList), BorderLayout.CENTER);

        JPanel profileBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton addProfile = new JButton("Add");
        addProfile.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(panel, "Member name:");
            if (name == null || name.isBlank()) return;
            String role = JOptionPane.showInputDialog(panel, "Role/Title:");
            String details = JOptionPane.showInputDialog(panel, "Details (skills, responsibilities, notes):");
            OrganizationContext.MemberProfile mp = new OrganizationContext.MemberProfile(
                    name.trim(), role != null ? role.trim() : "", details != null ? details.trim() : "");
            orgCtx.addMemberProfile(mp);
            profileModel.addElement(mp.getName() + " - " + mp.getRole());
        });
        JButton removeProfile = new JButton("Remove");
        removeProfile.addActionListener(e -> {
            int idx = profileList.getSelectedIndex();
            if (idx >= 0) {
                orgCtx.removeMemberProfile(idx);
                profileModel.remove(idx);
            }
        });
        profileBtns.add(addProfile);
        profileBtns.add(removeProfile);
        profilesPanel.add(profileBtns, BorderLayout.SOUTH);
        form.add(profilesPanel, gbc);

        JScrollPane formScroll = new JScrollPane(form);
        panel.add(formScroll, BorderLayout.CENTER);

        // Save button
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save Organization Context");
        saveBtn.addActionListener(e -> {
            orgCtx.setLastUpdated(fieldAreas.get(0).getText().trim());
            orgCtx.setWhatChangedSinceLastUpdate(fieldAreas.get(1).getText().trim());
            orgCtx.setCurrentTermDateRange(fieldAreas.get(2).getText().trim());
            orgCtx.setTopPriorities(fieldAreas.get(3).getText().trim());
            orgCtx.setActiveInitiativesAndStatus(fieldAreas.get(4).getText().trim());
            orgCtx.setUpcomingDeadlinesAndEvents(fieldAreas.get(5).getText().trim());
            orgCtx.setCurrentMetrics(fieldAreas.get(6).getText().trim());
            orgCtx.setOfficerRosterAndOwnership(fieldAreas.get(7).getText().trim());
            orgCtx.setKeyPartnersStakeholders(fieldAreas.get(8).getText().trim());
            orgCtx.setCurrentBlockersRisks(fieldAreas.get(9).getText().trim());
            orgCtx.setPendingDecisions(fieldAreas.get(10).getText().trim());
            orgCtx.setPreferredToneStyle(fieldAreas.get(11).getText().trim());
            orgCtx.save();
            JOptionPane.showMessageDialog(panel, "Organization context saved.",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        });
        bottom.add(saveBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildTaskContextTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JCheckBox toggle = new JCheckBox("Include task context in prompts",
                controller.shouldIncludeTaskContext());
        toggle.addActionListener(e -> controller.setIncludeTaskContext(toggle.isSelected()));
        panel.add(toggle, BorderLayout.NORTH);

        TaskContext activeTask = controller.getActiveTaskContext();

        JPanel fieldsPanel = new JPanel(new GridLayout(0, 1, 4, 4));

        // Task name
        JPanel namePanel = new JPanel(new BorderLayout());
        namePanel.setBorder(BorderFactory.createTitledBorder("Active Task"));
        JLabel taskLabel = new JLabel(activeTask != null ? activeTask.getTaskName() : "(No task selected)");
        taskLabel.setFont(taskLabel.getFont().deriveFont(Font.BOLD, 13f));
        namePanel.add(taskLabel, BorderLayout.CENTER);
        fieldsPanel.add(namePanel);

        // Prompt template
        JPanel templatePanel = new JPanel(new BorderLayout());
        templatePanel.setBorder(BorderFactory.createTitledBorder("Prompt Template"));
        JTextArea templateArea = new JTextArea(
                activeTask != null && activeTask.getPromptTemplate() != null
                        ? activeTask.getPromptTemplate() : "(none)");
        templateArea.setEditable(false);
        templateArea.setLineWrap(true);
        templateArea.setWrapStyleWord(true);
        templateArea.setRows(3);
        templatePanel.add(new JScrollPane(templateArea), BorderLayout.CENTER);
        fieldsPanel.add(templatePanel);

        // Style instructions
        JPanel stylePanel = new JPanel(new BorderLayout());
        stylePanel.setBorder(BorderFactory.createTitledBorder("Style & Tone"));
        JTextArea styleArea = new JTextArea(
                activeTask != null && activeTask.getStyleInstructions() != null
                        ? activeTask.getStyleInstructions() : "(none)");
        styleArea.setEditable(false);
        styleArea.setLineWrap(true);
        styleArea.setWrapStyleWord(true);
        styleArea.setRows(2);
        stylePanel.add(new JScrollPane(styleArea), BorderLayout.CENTER);
        fieldsPanel.add(stylePanel);

        // Follow-up answers
        JPanel answersPanel = new JPanel(new BorderLayout());
        answersPanel.setBorder(BorderFactory.createTitledBorder("Follow-Up Answers"));
        StringBuilder answerText = new StringBuilder();
        if (activeTask != null && !activeTask.getFollowUpAnswers().isEmpty()) {
            for (var entry : activeTask.getFollowUpAnswers().entrySet()) {
                answerText.append("Q: ").append(entry.getKey()).append("\n");
                answerText.append("A: ").append(entry.getValue()).append("\n\n");
            }
        } else {
            answerText.append("(no answers collected yet)");
        }
        JTextArea answersArea = new JTextArea(answerText.toString());
        answersArea.setEditable(false);
        answersArea.setLineWrap(true);
        answersArea.setWrapStyleWord(true);
        answersArea.setRows(4);
        answersPanel.add(new JScrollPane(answersArea), BorderLayout.CENTER);
        fieldsPanel.add(answersPanel);

        // Mode indicator
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.setBorder(BorderFactory.createTitledBorder("Execution Mode"));
        String modeText = activeTask != null
                ? (activeTask.isSimpleMode() ? "Simple (single API call)" : "Multi-prompt (full debate cycle)")
                : "(no task selected)";
        modePanel.add(new JLabel(modeText));
        fieldsPanel.add(modePanel);

        panel.add(new JScrollPane(fieldsPanel), BorderLayout.CENTER);

        // Preview assembled task block
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton previewBtn = new JButton("Preview Task Block");
        previewBtn.addActionListener(e -> {
            if (activeTask != null) {
                JOptionPane.showMessageDialog(this,
                        activeTask.buildTaskBlock(),
                        "Task Context Block", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "No active task context.",
                        "Task Context Block", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        JButton clearBtn = new JButton("Clear Task");
        clearBtn.addActionListener(e -> {
            controller.setActiveTaskContext(null);
            taskLabel.setText("(No task selected)");
            templateArea.setText("(none)");
            styleArea.setText("(none)");
            answersArea.setText("(no answers collected yet)");
        });
        bottomPanel.add(previewBtn);
        bottomPanel.add(clearBtn);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JCheckBox toggle = new JCheckBox("Include conversation history in Claude prompts",
                controller.shouldIncludeHistory());
        toggle.addActionListener(e -> controller.setIncludeHistory(toggle.isSelected()));

        String historyText = context.getHistoryBlock();
        int charCount = historyText.length();
        int estTokens = charCount / 4;
        JLabel stats = new JLabel("  Characters: " + charCount + "  |  Est. tokens: ~" + estTokens);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(toggle, BorderLayout.WEST);
        topPanel.add(stats, BorderLayout.EAST);
        JLabel note = new JLabel("GPT and Gemini maintain conversation history via their stateful APIs.");
        note.setFont(note.getFont().deriveFont(Font.ITALIC, 11f));
        note.setForeground(new Color(120, 120, 120));
        JPanel topWrapper = new JPanel();
        topWrapper.setLayout(new BoxLayout(topWrapper, BoxLayout.Y_AXIS));
        topWrapper.add(topPanel);
        topWrapper.add(note);
        panel.add(topWrapper, BorderLayout.NORTH);

        JTextArea historyArea = new JTextArea(historyText.isEmpty()
                ? "(No conversation history yet)" : historyText);
        historyArea.setEditable(false);
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);
        panel.add(new JScrollPane(historyArea), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton clearBtn = new JButton("Clear History");
        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Clear all conversation history?", "Confirm Clear",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                context.clear();
                historyArea.setText("(No conversation history yet)");
                stats.setText("  Characters: 0  |  Est. tokens: ~0");
            }
        });
        buttons.add(clearBtn);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildPreviewTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel info = new JLabel("Shows what the assembled prompt looks like for each model.");
        panel.add(info, BorderLayout.NORTH);

        JTextArea previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        panel.add(new JScrollPane(previewArea), BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<String> modelCombo = new JComboBox<>(new String[]{"Claude", "GPT", "Gemini"});
        JButton refreshBtn = new JButton("Refresh Preview");
        refreshBtn.addActionListener(e -> {
            StringBuilder sb = new StringBuilder();
            String teamCtx = controller.getEffectiveTeamContext(PromptBuilder.DEFAULT_TEAM_CONTEXT);
            sb.append("=== TEAM CONTEXT ===\n");
            sb.append(teamCtx.isEmpty() ? "(disabled)\n" : teamCtx);
            sb.append("\n");

            String modelName = (String) modelCombo.getSelectedItem();
            sb.append("=== AGENT IDENTITY ===\n");
            if (activeProfileSet != null && activeProfileSet.getAgents() != null) {
                int idx = modelName.equals("Claude") ? 0 : modelName.equals("GPT") ? 1 : 2;
                if (idx < activeProfileSet.getAgents().size()) {
                    AgentProfile agent = activeProfileSet.getAgents().get(idx);
                    if (controller.shouldIncludeAgent(agent.getName())) {
                        sb.append(agent.toBriefing());
                    } else {
                        sb.append("(disabled for ").append(modelName).append(")\n");
                    }
                }
            }
            sb.append("\n");

            sb.append("=== STAKEHOLDER ===\n");
            if (controller.shouldIncludeStakeholderProfile()
                    && activeProfileSet != null
                    && activeProfileSet.getStakeholders() != null
                    && !activeProfileSet.getStakeholders().isEmpty()) {
                sb.append(activeProfileSet.getStakeholders().get(0).toBriefing());
            } else {
                sb.append("(disabled)\n");
            }
            sb.append("\n");

            sb.append("=== ORGANIZATION CONTEXT ===\n");
            if (controller.shouldIncludeOrgContext()) {
                sb.append(controller.getEffectiveOrgContext());
            } else {
                sb.append("(disabled)\n");
            }
            sb.append("\n");

            sb.append("=== TASK CONTEXT ===\n");
            if (controller.shouldIncludeTaskContext() && controller.getActiveTaskContext() != null) {
                sb.append(controller.getActiveTaskContext().buildTaskBlock());
            } else {
                sb.append("(disabled or no active task)\n");
            }
            sb.append("\n");

            sb.append("=== HISTORY ===\n");
            if (controller.shouldIncludeHistory()) {
                String hist = context.getHistoryBlock();
                sb.append(hist.isEmpty() ? "(no history)\n" : hist);
            } else {
                sb.append("(disabled)\n");
            }

            previewArea.setText(sb.toString());
            previewArea.setCaretPosition(0);
        });

        JButton copyBtn = new JButton("Copy");
        copyBtn.addActionListener(e -> {
            previewArea.selectAll();
            previewArea.copy();
            previewArea.setCaretPosition(0);
        });

        controls.add(new JLabel("Model:"));
        controls.add(modelCombo);
        controls.add(refreshBtn);
        controls.add(copyBtn);
        panel.add(controls, BorderLayout.SOUTH);

        return panel;
    }

    private void applyPreset(ContextPreset preset) {
        preset.applyTo(controller);
        dispose();
        // Re-open to reflect changes
        new ContextControlDialog(
                (Frame) getOwner(), controller, context, activeProfileSet
        ).setVisible(true);
    }
}
