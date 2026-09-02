package cowork.ui;

// Two-tab control panel for what gets sent to Claude: the Organization tab
// edits every OrganizationContext field (driven by getFieldNames(), so labels
// and values can never be misaligned) with per-field freshness; the Task
// Context tab shows the active task template. Also opens the payload viewer.

import cowork.context.ContextController;
import cowork.context.ContextEntry;
import cowork.context.ContextStatus;
import cowork.context.Freshness;
import cowork.context.OrganizationContext;
import cowork.context.TaskContext;
import cowork.llm.ApiRequestLog;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class ContextControlDialog extends JDialog {

    private final ContextController controller;
    private final ApiRequestLog apiRequestLog;
    private final Map<String, JTextArea> fieldAreas = new LinkedHashMap<>();
    private final Map<String, JLabel> freshnessLabels = new LinkedHashMap<>();

    public ContextControlDialog(Frame owner, ContextController controller, ApiRequestLog log) {
        super(owner, "Context Control", true);
        this.controller = controller;
        this.apiRequestLog = log;
        setSize(720, 560);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Organization", buildOrgContextTab());
        tabs.addTab("Task Context", buildTaskContextTab());
        add(tabs, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JButton viewPayloadsBtn = new JButton("View Sent Payloads");
        viewPayloadsBtn.setToolTipText("Audit every request payload sent to Claude this session.");
        viewPayloadsBtn.addActionListener(e -> new ApiRequestViewerDialog(this, apiRequestLog).setVisible(true));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(viewPayloadsBtn);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(closeBtn);
        bottom.add(left, BorderLayout.WEST);
        bottom.add(right, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
    }

    // ---------------------------------------------------------------- Organization

    private JPanel buildOrgContextTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        OrganizationContext orgCtx = controller.getOrganizationContext();

        JCheckBox toggle = new JCheckBox("Include organization context in prompts", controller.shouldIncludeOrgContext());
        toggle.addActionListener(e -> controller.setIncludeOrgContext(toggle.isSelected()));
        panel.add(toggle, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        for (String fieldName : OrganizationContext.getFieldNames()) {
            ContextEntry<String> entry = orgCtx.getEntry(fieldName);
            String value = entry == null || entry.getValue() == null ? "" : entry.getValue();

            JPanel labelBox = new JPanel();
            labelBox.setLayout(new BoxLayout(labelBox, BoxLayout.Y_AXIS));
            labelBox.add(new JLabel(OrganizationContext.getFieldLabel(fieldName) + ":"));
            JLabel freshness = new JLabel();
            freshness.setFont(freshness.getFont().deriveFont(10f));
            freshnessLabels.put(fieldName, freshness);
            labelBox.add(freshness);
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
            form.add(labelBox, gbc);

            JTextArea area = new JTextArea(value, 2, 30);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            fieldAreas.put(fieldName, area);
            gbc.gridx = 1; gbc.weightx = 1;
            form.add(new JScrollPane(area), gbc);
            row++;
        }
        refreshFreshnessLabels();

        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.weightx = 1;
        form.add(buildMemberProfilesPanel(orgCtx), gbc);

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(formScroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save Organization Context");
        saveBtn.addActionListener(e -> saveOrgContext(panel));
        bottom.add(saveBtn);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    /** Roster editing; changes hit the in-memory context immediately and persist on Save. */
    private JPanel buildMemberProfilesPanel(OrganizationContext orgCtx) {
        JPanel profilesPanel = new JPanel(new BorderLayout(4, 4));
        profilesPanel.setBorder(BorderFactory.createTitledBorder("Member/Officer Profiles"));
        DefaultListModel<String> model = new DefaultListModel<>();
        for (OrganizationContext.MemberProfile mp : orgCtx.getMemberProfiles()) {
            model.addElement(mp.getName() + " - " + mp.getRole());
        }
        JList<String> list = new JList<>(model);
        list.setVisibleRowCount(4);
        profilesPanel.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton add = new JButton("Add");
        add.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Member name:");
            if (name == null || name.isBlank()) return;
            String role = JOptionPane.showInputDialog(this, "Role/Title:");
            String details = JOptionPane.showInputDialog(this, "Details (skills, responsibilities, notes):");
            OrganizationContext.MemberProfile mp = new OrganizationContext.MemberProfile(
                    name.trim(), role != null ? role.trim() : "", details != null ? details.trim() : "");
            orgCtx.addMemberProfile(mp);
            model.addElement(mp.getName() + " - " + mp.getRole());
        });
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0) {
                orgCtx.removeMemberProfile(idx);
                model.remove(idx);
            }
        });
        buttons.add(add);
        buttons.add(remove);
        profilesPanel.add(buttons, BorderLayout.SOUTH);
        return profilesPanel;
    }

    private void saveOrgContext(Component parent) {
        OrganizationContext orgCtx = controller.getOrganizationContext();
        for (var e : fieldAreas.entrySet()) {
            String text = e.getValue().getText().trim();
            ContextEntry<String> entry = orgCtx.getEntry(e.getKey());
            String current = entry == null || entry.getValue() == null ? "" : entry.getValue();
            // Only write fields the user changed so untouched ones keep their freshness timestamp.
            if (!text.equals(current)) {
                orgCtx.updateField(e.getKey(), text, "user_edit", 1.0, ContextStatus.APPROVED);
            }
        }
        controller.saveOrganizationContext();
        refreshFreshnessLabels();
        JOptionPane.showMessageDialog(parent, "Organization context saved.", "Saved", JOptionPane.INFORMATION_MESSAGE);
    }

    private void refreshFreshnessLabels() {
        OrganizationContext orgCtx = controller.getOrganizationContext();
        for (var e : freshnessLabels.entrySet()) {
            Freshness f = orgCtx.getFieldFreshness(e.getKey());
            ContextEntry<String> entry = orgCtx.getEntry(e.getKey());
            String updated = entry == null || entry.getLastUpdated() == null ? "" : entry.getLastUpdated();
            if (updated.length() > 10) updated = updated.substring(0, 10);
            e.getValue().setText("● " + freshnessText(f) + (updated.isEmpty() ? "" : "  (" + updated + ")"));
            e.getValue().setForeground(freshnessColor(f));
        }
    }

    private static String freshnessText(Freshness f) {
        return switch (f) {
            case FRESH -> "Fresh";
            case AGING -> "Aging";
            case STALE -> "Stale";
            case NEEDS_CONFIRMATION -> "Needs update";
        };
    }

    private static Color freshnessColor(Freshness f) {
        return switch (f) {
            case FRESH -> new Color(76, 175, 80);
            case AGING -> new Color(255, 193, 7);
            case STALE -> new Color(255, 152, 0);
            case NEEDS_CONFIRMATION -> new Color(244, 67, 54);
        };
    }

    // ---------------------------------------------------------------- Task Context

    private JPanel buildTaskContextTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JCheckBox toggle = new JCheckBox("Include task context in prompts", controller.shouldIncludeTaskContext());
        toggle.addActionListener(e -> controller.setIncludeTaskContext(toggle.isSelected()));
        panel.add(toggle, BorderLayout.NORTH);

        TaskContext activeTask = controller.getActiveTaskContext();
        JPanel fieldsPanel = new JPanel(new GridLayout(0, 1, 4, 4));

        JPanel namePanel = new JPanel(new BorderLayout());
        namePanel.setBorder(BorderFactory.createTitledBorder("Active Task"));
        JLabel taskLabel = new JLabel(activeTask != null ? activeTask.getTaskName() : "(No task selected)");
        taskLabel.setFont(taskLabel.getFont().deriveFont(Font.BOLD, 13f));
        namePanel.add(taskLabel, BorderLayout.CENTER);
        fieldsPanel.add(namePanel);

        JTextArea templateArea = readOnlyArea(activeTask != null && activeTask.getPromptTemplate() != null
                ? activeTask.getPromptTemplate() : "(none)", 3);
        fieldsPanel.add(titled("Prompt Template", templateArea));

        JTextArea styleArea = readOnlyArea(activeTask != null && activeTask.getStyleInstructions() != null
                ? activeTask.getStyleInstructions() : "(none)", 2);
        fieldsPanel.add(titled("Style & Tone", styleArea));

        JTextArea answersArea = readOnlyArea(formatAnswers(activeTask), 4);
        fieldsPanel.add(titled("Follow-Up Answers", answersArea));

        panel.add(new JScrollPane(fieldsPanel), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton previewBtn = new JButton("Preview Task Block");
        previewBtn.addActionListener(e -> {
            TaskContext current = controller.getActiveTaskContext();
            JOptionPane.showMessageDialog(this,
                    current != null ? current.buildTaskBlock() : "No active task context.",
                    "Task Context Block", JOptionPane.INFORMATION_MESSAGE);
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

    private static String formatAnswers(TaskContext task) {
        if (task == null || task.getFollowUpAnswers() == null || task.getFollowUpAnswers().isEmpty()) {
            return "(no answers collected yet)";
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : task.getFollowUpAnswers().entrySet()) {
            sb.append("Q: ").append(entry.getKey()).append("\nA: ").append(entry.getValue()).append("\n\n");
        }
        return sb.toString();
    }

    private static JTextArea readOnlyArea(String text, int rows) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setRows(rows);
        return area;
    }

    private static JPanel titled(String title, JComponent content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(new JScrollPane(content), BorderLayout.CENTER);
        return p;
    }
}
