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

    private JPanel buildHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JCheckBox toggle = new JCheckBox("Include conversation history in prompts",
                controller.shouldIncludeHistory());
        toggle.addActionListener(e -> controller.setIncludeHistory(toggle.isSelected()));

        String historyText = context.getHistoryBlock();
        int charCount = historyText.length();
        int estTokens = charCount / 4;
        JLabel stats = new JLabel("  Characters: " + charCount + "  |  Est. tokens: ~" + estTokens);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(toggle, BorderLayout.WEST);
        topPanel.add(stats, BorderLayout.EAST);
        panel.add(topPanel, BorderLayout.NORTH);

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
