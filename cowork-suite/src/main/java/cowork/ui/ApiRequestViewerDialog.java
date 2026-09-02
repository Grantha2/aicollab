package cowork.ui;

// Browser for the ApiRequestLog so users can see exactly what was sent to
// Claude for each task: system instruction, messages and request metadata,
// filterable by task, step and model.

import cowork.llm.ApiRequestLog;
import cowork.llm.ChatMessage;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ApiRequestViewerDialog extends JDialog {

    private final ApiRequestLog log;
    private final DefaultListModel<ApiRequestLog.RequestRecord> listModel = new DefaultListModel<>();
    private final JList<ApiRequestLog.RequestRecord> entryList = new JList<>(listModel);

    private final JComboBox<String> taskFilter = new JComboBox<>();
    private final JComboBox<String> stepFilter = new JComboBox<>();
    private final JComboBox<String> modelFilter = new JComboBox<>();

    private final JTextArea systemArea = new JTextArea();
    private final JTextArea messagesArea = new JTextArea();
    private final JTextArea metaArea = new JTextArea();

    private List<ApiRequestLog.RequestRecord> allRecords = List.of();

    public ApiRequestViewerDialog(Window owner, ApiRequestLog log) {
        super(owner, "API Request Viewer — Sent Payloads", ModalityType.APPLICATION_MODAL);
        this.log = log;

        setLayout(new BorderLayout(8, 8));
        add(buildFilterBar(), BorderLayout.NORTH);
        add(buildSplit(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(1000, 640);
        setMinimumSize(new Dimension(800, 480));
        setLocationRelativeTo(owner);
        reload();
    }

    private JComponent buildFilterBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));
        panel.add(new JLabel("Task:"));
        panel.add(taskFilter);
        panel.add(new JLabel("Step:"));
        panel.add(stepFilter);
        panel.add(new JLabel("Model:"));
        panel.add(modelFilter);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> reload());
        panel.add(refresh);

        taskFilter.addActionListener(e -> applyFilters());
        stepFilter.addActionListener(e -> applyFilters());
        modelFilter.addActionListener(e -> applyFilters());
        return panel;
    }

    private JComponent buildSplit() {
        entryList.setCellRenderer(new EntryRenderer());
        entryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelected();
        });
        JScrollPane listScroll = new JScrollPane(entryList);
        listScroll.setPreferredSize(new Dimension(280, 480));

        for (JTextArea area : List.of(systemArea, messagesArea, metaArea)) {
            area.setEditable(false);
        }
        systemArea.setLineWrap(true);
        systemArea.setWrapStyleWord(true);
        messagesArea.setLineWrap(true);
        messagesArea.setWrapStyleWord(true);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("System", new JScrollPane(systemArea));
        tabs.addTab("Messages", new JScrollPane(messagesArea));
        tabs.addTab("Meta", new JScrollPane(metaArea));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, tabs);
        split.setDividerLocation(280);
        split.setResizeWeight(0.25);
        return split;
    }

    private JComponent buildButtons() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        bar.add(closeBtn);
        return bar;
    }

    private void reload() {
        allRecords = log.readAll();
        rebuildFilters();
        applyFilters();
    }

    private void rebuildFilters() {
        Set<String> tasks = new LinkedHashSet<>(List.of("All"));
        Set<String> steps = new LinkedHashSet<>(List.of("All"));
        Set<String> models = new LinkedHashSet<>(List.of("All"));
        for (ApiRequestLog.RequestRecord r : allRecords) {
            if (r.taskId() != null) tasks.add(r.taskId());
            if (r.step() != null) steps.add(r.step());
            if (r.model() != null) models.add(r.model());
        }
        setComboItems(taskFilter, tasks);
        setComboItems(stepFilter, steps);
        setComboItems(modelFilter, models);
    }

    private static void setComboItems(JComboBox<String> combo, Set<String> items) {
        String prev = (String) combo.getSelectedItem();
        combo.removeAllItems();
        for (String item : items) combo.addItem(item);
        combo.setSelectedItem(prev != null && items.contains(prev) ? prev : "All");
    }

    private void applyFilters() {
        String task = (String) taskFilter.getSelectedItem();
        String step = (String) stepFilter.getSelectedItem();
        String model = (String) modelFilter.getSelectedItem();

        listModel.clear();
        for (ApiRequestLog.RequestRecord r : allRecords) {
            if (!matches(task, r.taskId()) || !matches(step, r.step()) || !matches(model, r.model())) continue;
            listModel.addElement(r);
        }
        if (!listModel.isEmpty()) {
            entryList.setSelectedIndex(0);
        } else {
            clearRightPane();
        }
    }

    private static boolean matches(String filter, String value) {
        return filter == null || "All".equals(filter) || Objects.equals(filter, value);
    }

    private void showSelected() {
        ApiRequestLog.RequestRecord r = entryList.getSelectedValue();
        if (r == null) {
            clearRightPane();
            return;
        }
        systemArea.setText(r.systemInstruction() == null ? "(none)" : r.systemInstruction());
        systemArea.setCaretPosition(0);

        StringBuilder sb = new StringBuilder();
        List<ChatMessage> msgs = r.messages() == null ? new ArrayList<>() : r.messages();
        for (int i = 0; i < msgs.size(); i++) {
            ChatMessage m = msgs.get(i);
            sb.append("--- Message ").append(i + 1).append(" [").append(m.role()).append("] ---\n");
            sb.append(m.content()).append("\n\n");
        }
        if (msgs.isEmpty()) sb.append("(no messages)");
        messagesArea.setText(sb.toString());
        messagesArea.setCaretPosition(0);

        String meta = "Timestamp:    " + r.timestamp() + '\n'
                + "Task:         " + r.taskId() + '\n'
                + "Step:         " + r.step() + '\n'
                + "Model:        " + r.model() + '\n'
                + "Provider:     " + r.provider() + '\n'
                + "Max tokens:   " + r.maxTokens() + '\n'
                + "State id:     " + (r.stateId() == null ? "(none)" : r.stateId()) + '\n'
                + "Tools:        " + (r.toolsSummary() == null || r.toolsSummary().isBlank() ? "(none)" : r.toolsSummary()) + '\n';
        metaArea.setText(meta);
        metaArea.setCaretPosition(0);
    }

    private void clearRightPane() {
        systemArea.setText("");
        messagesArea.setText("");
        metaArea.setText("");
    }

    private static class EntryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ApiRequestLog.RequestRecord r) {
                setText(r.taskId() + "  |  " + r.step() + "  |  " + r.model());
            }
            return c;
        }
    }
}
