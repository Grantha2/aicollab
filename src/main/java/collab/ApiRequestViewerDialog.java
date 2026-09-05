package collab;

// ============================================================
// ApiRequestViewerDialog.java — Browses the ApiRequestLog.
//
// WHY THIS EXISTS:
// Users need to see EXACTLY what was sent to each model per API
// call, especially during Phase 2 "reactions" where peer panelist
// responses are embedded inside the user prompt. Previously the
// exact peer text shipped to a panelist was not visible anywhere.
//
// Layout:
//   [Filters]
//   +------------------+-------------------------------+
//   | Cycle | Phase... |  System  | Messages  |  Meta  |
//   | list entries     |  tabs showing payload content |
//   +------------------+-------------------------------+
// ============================================================

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ApiRequestViewerDialog extends JDialog {

    private final ApiRequestLog log;
    private final DefaultListModel<ApiRequestLog.RequestRecord> listModel = new DefaultListModel<>();
    private final JList<ApiRequestLog.RequestRecord> entryList = new JList<>(listModel);

    private final JComboBox<String> cycleFilter = new JComboBox<>();
    private final JComboBox<String> phaseFilter = new JComboBox<>();
    private final JComboBox<String> modelFilter = new JComboBox<>();

    private final JTextArea systemArea = new JTextArea();
    private final JTextArea messagesArea = new JTextArea();
    private final JTextArea metaArea = new JTextArea();

    private List<ApiRequestLog.RequestRecord> allRecords = List.of();

    public ApiRequestViewerDialog(Window owner, ApiRequestLog log) {
        super(owner, "API Request Viewer \u2014 Sent Payloads", ModalityType.APPLICATION_MODAL);
        this.log = log;

        setLayout(new BorderLayout(8, 8));

        // Top region stacks the state-management help banner on top of
        // the filter bar so newcomers have context for why Gemini/Claude
        // messages look different from OpenAI's.
        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.add(buildStateHelpBanner(), BorderLayout.NORTH);
        north.add(buildFilterBar(), BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        add(buildSplit(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(1000, 640);
        setMinimumSize(new Dimension(800, 480));
        setLocationRelativeTo(owner);

        reload();
    }

    // ============================================================
    // buildStateHelpBanner() — Short explainer for why the payload
    // shape differs across providers.
    //
    // The three LlmClient implementations handle "stateful" calls
    // very differently, and the audit viewer is the first place a
    // user sees that on the wire. Without this banner, an Anthropic
    // Phase-2 request (full history) vs. an OpenAI Phase-2 request
    // (previous_response_id + one new turn) looks like a bug.
    // ============================================================
    private JComponent buildStateHelpBanner() {
        // HTML so the long sentence wraps gracefully at narrow widths.
        JLabel help = new JLabel(
                "<html><body style='margin:0;'>"
                + "<b>About request state:</b> "
                + "<b>Anthropic (Claude)</b> uses client-side history \u2014 full prior messages are resent each turn. "
                + "<b>OpenAI (GPT)</b> uses server-side state via the Responses API \u2014 chained turns send only the new user message plus <code>previous_response_id</code>. "
                + "<b>Google (Gemini)</b> uses server-side state via the Interactions API (beta) \u2014 chained turns send only the new user message plus <code>previous_interaction_id</code>; <code>system_instruction</code> is re-submitted each turn (interaction-scoped). Falls back to stateless <code>generateContent</code> on Interactions API errors."
                + "</body></html>");
        help.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        return help;
    }

    private JComponent buildFilterBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));
        panel.add(new JLabel("Cycle:"));
        panel.add(cycleFilter);
        panel.add(new JLabel("Phase:"));
        panel.add(phaseFilter);
        panel.add(new JLabel("Model:"));
        panel.add(modelFilter);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> reload());
        panel.add(refresh);

        // Re-apply filters when user changes any combo.
        cycleFilter.addActionListener(e -> applyFilters());
        phaseFilter.addActionListener(e -> applyFilters());
        modelFilter.addActionListener(e -> applyFilters());

        return panel;
    }

    private JComponent buildSplit() {
        // Left: list of entries
        entryList.setCellRenderer(new EntryRenderer());
        entryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelected();
        });
        JScrollPane listScroll = new JScrollPane(entryList);
        listScroll.setPreferredSize(new Dimension(280, 480));

        // Right: tabs for system / messages / meta
        systemArea.setEditable(false);
        systemArea.setLineWrap(true);
        systemArea.setWrapStyleWord(true);

        messagesArea.setEditable(false);
        messagesArea.setLineWrap(true);
        messagesArea.setWrapStyleWord(true);

        metaArea.setEditable(false);

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
        Set<String> cycles = new LinkedHashSet<>();
        Set<String> phases = new LinkedHashSet<>();
        Set<String> models = new LinkedHashSet<>();
        cycles.add("All");
        phases.add("All");
        models.add("All");
        for (ApiRequestLog.RequestRecord r : allRecords) {
            cycles.add(Integer.toString(r.cycle()));
            if (r.phase() != null) phases.add(r.phase());
            if (r.model() != null) models.add(r.model());
        }
        setComboItems(cycleFilter, cycles);
        setComboItems(phaseFilter, phases);
        setComboItems(modelFilter, models);
    }

    private static void setComboItems(JComboBox<String> combo, Set<String> items) {
        String prev = (String) combo.getSelectedItem();
        combo.removeAllItems();
        for (String item : items) combo.addItem(item);
        if (prev != null && items.contains(prev)) {
            combo.setSelectedItem(prev);
        } else {
            combo.setSelectedIndex(0);
        }
    }

    private void applyFilters() {
        String cy = (String) cycleFilter.getSelectedItem();
        String ph = (String) phaseFilter.getSelectedItem();
        String md = (String) modelFilter.getSelectedItem();

        listModel.clear();
        for (ApiRequestLog.RequestRecord r : allRecords) {
            if (cy != null && !"All".equals(cy) && !Integer.toString(r.cycle()).equals(cy)) continue;
            if (ph != null && !"All".equals(ph) && !java.util.Objects.equals(r.phase(), ph)) continue;
            if (md != null && !"All".equals(md) && !java.util.Objects.equals(r.model(), md)) continue;
            listModel.addElement(r);
        }

        if (!listModel.isEmpty()) {
            entryList.setSelectedIndex(0);
        } else {
            clearRightPane();
        }
    }

    private void showSelected() {
        ApiRequestLog.RequestRecord r = entryList.getSelectedValue();
        if (r == null) {
            clearRightPane();
            return;
        }
        systemArea.setText(r.systemInstruction() == null ? "(none \u2014 stateful follow-up)" : r.systemInstruction());
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

        StringBuilder meta = new StringBuilder();
        meta.append("Timestamp:  ").append(r.timestamp()).append('\n');
        meta.append("Cycle:      ").append(r.cycle()).append('\n');
        meta.append("Phase:      ").append(r.phase()).append('\n');
        meta.append("Model:      ").append(r.model()).append('\n');
        meta.append("Provider:   ").append(r.provider()).append('\n');
        meta.append("Max tokens: ").append(r.maxTokens()).append('\n');
        meta.append("State id:   ").append(r.stateId() == null ? "(none)" : r.stateId()).append('\n');
        metaArea.setText(meta.toString());
        metaArea.setCaretPosition(0);
    }

    private void clearRightPane() {
        systemArea.setText("");
        messagesArea.setText("");
        metaArea.setText("");
    }

    /** Renderer that labels each list row as "[cycle N | phase | model]". */
    private static class EntryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ApiRequestLog.RequestRecord r) {
                setText("cycle " + r.cycle() + "  |  " + r.phase() + "  |  " + r.model());
            }
            return c;
        }
    }
}
