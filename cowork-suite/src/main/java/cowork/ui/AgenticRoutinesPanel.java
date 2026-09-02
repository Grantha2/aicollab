package cowork.ui;

// The Agentic Routines view: a sidebar of registered tasks (by category),
// recommendations, per-field context-health checkboxes and the upcoming
// feed; a main area for task output and approval cards. Implements
// TaskOutput so tasks report back without knowing about Swing widgets.

import cowork.config.ClientFactory;
import cowork.config.Config;
import cowork.context.ContextChangeLog;
import cowork.context.ContextEntry;
import cowork.context.Freshness;
import cowork.context.OrganizationContext;
import cowork.context.ProposedChange;
import cowork.context.ReconciliationService;
import cowork.data.OperationalFeedItem;
import cowork.data.OperationalFeedStore;
import cowork.data.Recommendation;
import cowork.data.RecommendationEngine;
import cowork.data.WorkflowDefinition;
import cowork.data.WorkflowStore;
import cowork.tasks.AgenticTask;
import cowork.tasks.AgenticTaskContext;
import cowork.tasks.AgenticTaskRegistry;
import cowork.tasks.TaskOutput;
import cowork.tasks.UserWorkflowTask;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgenticRoutinesPanel extends JPanel implements TaskOutput {

    private static final Color PANEL_BG = new Color(245, 245, 250);
    private static final Color MUTED = new Color(100, 100, 110);
    private static final Color TEXT = new Color(80, 80, 90);

    private final OrganizationContext orgContext;
    private final ReconciliationService reconciliation;
    private final ContextChangeLog changeLog;
    private final Config config;
    private ClientFactory clients;
    private final AgenticTaskRegistry taskRegistry;
    private final OperationalFeedStore feedStore;
    private final WorkflowStore workflowStore;
    private final RecommendationEngine recommendationEngine;

    private final JPanel sidebarContent;
    private final JPanel mainPanel;
    private final JPanel outputArea;
    private final JPanel approvalArea;
    private final JLabel statusLabel;
    private final JButton refreshSelectedBtn;

    private final Map<String, JCheckBox> fieldCheckboxes = new LinkedHashMap<>();
    private boolean sessionStartCheckDone = false;

    public AgenticRoutinesPanel(OrganizationContext orgContext,
                                ReconciliationService reconciliation,
                                ContextChangeLog changeLog,
                                Config config,
                                ClientFactory clients,
                                AgenticTaskRegistry taskRegistry,
                                OperationalFeedStore feedStore,
                                WorkflowStore workflowStore,
                                RecommendationEngine recommendationEngine) {
        this.orgContext = orgContext;
        this.reconciliation = reconciliation;
        this.changeLog = changeLog;
        this.config = config;
        this.clients = clients;
        this.taskRegistry = taskRegistry;
        this.feedStore = feedStore;
        this.workflowStore = workflowStore;
        this.recommendationEngine = recommendationEngine;

        setLayout(new BorderLayout());
        setBackground(PANEL_BG);

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        header.setOpaque(false);
        JLabel titleLabel = new JLabel("Agentic Routines");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        header.add(titleLabel);
        add(header, BorderLayout.NORTH);

        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        statusLabel.setForeground(MUTED);
        add(statusLabel, BorderLayout.SOUTH);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(Color.WHITE);
        leftPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(220, 220, 230)));

        sidebarContent = new JPanel();
        sidebarContent.setLayout(new BoxLayout(sidebarContent, BoxLayout.Y_AXIS));
        sidebarContent.setBackground(Color.WHITE);
        sidebarContent.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane sidebarScroll = new JScrollPane(sidebarContent);
        sidebarScroll.setBorder(null);
        sidebarScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sidebarScroll.getVerticalScrollBar().setUnitIncrement(16);
        leftPanel.add(sidebarScroll, BorderLayout.CENTER);

        JPanel sidebarButtons = new JPanel();
        sidebarButtons.setLayout(new BoxLayout(sidebarButtons, BoxLayout.Y_AXIS));
        sidebarButtons.setBackground(Color.WHITE);
        sidebarButtons.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 230)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        refreshSelectedBtn = new JButton("Refresh Selected");
        refreshSelectedBtn.setAlignmentX(LEFT_ALIGNMENT);
        refreshSelectedBtn.setMaximumSize(new Dimension(250, 30));
        refreshSelectedBtn.setEnabled(false);
        refreshSelectedBtn.addActionListener(e -> onRefreshSelected());
        sidebarButtons.add(refreshSelectedBtn);
        leftPanel.add(sidebarButtons, BorderLayout.SOUTH);
        leftPanel.setPreferredSize(new Dimension(270, 0));

        mainPanel = new JPanel(new BorderLayout(0, 12));
        mainPanel.setBackground(PANEL_BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        outputArea = new JPanel();
        outputArea.setLayout(new BoxLayout(outputArea, BoxLayout.Y_AXIS));
        outputArea.setOpaque(false);
        approvalArea = new JPanel();
        approvalArea.setLayout(new BoxLayout(approvalArea, BoxLayout.Y_AXIS));
        approvalArea.setOpaque(false);

        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setOpaque(false);
        mainContent.add(outputArea);
        mainContent.add(Box.createVerticalStrut(8));
        mainContent.add(approvalArea);

        JScrollPane mainScroll = new JScrollPane(mainContent);
        mainScroll.setBorder(null);
        mainScroll.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(mainScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, mainPanel);
        split.setDividerLocation(270);
        split.setResizeWeight(0.0);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        rebuildSidebar();
        showEmptyState();
    }

    /** Swap in a new factory after the user edits configuration. */
    public void setClientFactory(ClientFactory clients) {
        this.clients = clients;
    }

    /** Session-start trigger: on first view, pre-select stale fields and nudge the user. */
    public void onTabShown() {
        if (sessionStartCheckDone) return;
        sessionStartCheckDone = true;
        long staleCount = orgContext.getFreshnessReport().values().stream()
            .filter(f -> f == Freshness.STALE || f == Freshness.NEEDS_CONFIRMATION)
            .count();
        if (staleCount > 0) {
            statusLabel.setText(staleCount + " field(s) are stale. Select fields and click 'Refresh Selected' to update.");
            selectAllStale();
        }
    }

    // ---------------------------------------------------------------- TaskOutput

    @Override
    public void setStatus(String status) {
        onEdt(() -> statusLabel.setText(status));
    }

    @Override
    public void showOutput(String title, String body) {
        onEdt(() -> {
            outputArea.removeAll();
            outputArea.add(createCard(title, body, new Color(232, 245, 233), new Color(129, 199, 132)));
            mainPanel.revalidate();
            mainPanel.repaint();
        });
    }

    @Override
    public void showProposals(List<ProposedChange> proposals) {
        onEdt(() -> {
            approvalArea.removeAll();
            List<ProposedChange> pending = proposals == null ? List.of() : proposals;
            if (!pending.isEmpty()) {
                JLabel title = new JLabel("Pending Approvals (" + pending.size() + ")");
                title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
                title.setAlignmentX(LEFT_ALIGNMENT);
                approvalArea.add(title);
                approvalArea.add(Box.createVerticalStrut(8));
                for (ProposedChange change : pending) {
                    approvalArea.add(createApprovalCard(change));
                    approvalArea.add(Box.createVerticalStrut(6));
                }
            }
            // Auto-applied changes may have altered freshness, so redraw context health.
            rebuildSidebar();
            mainPanel.revalidate();
            mainPanel.repaint();
        });
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    // ---------------------------------------------------------------- Sidebar

    private void rebuildSidebar() {
        sidebarContent.removeAll();
        fieldCheckboxes.clear();

        addSidebarSection("TASKS");
        for (var catEntry : taskRegistry.getByCategory().entrySet()) {
            JLabel catLabel = new JLabel(catEntry.getKey());
            catLabel.setFont(catLabel.getFont().deriveFont(Font.BOLD, 11f));
            catLabel.setForeground(MUTED);
            catLabel.setAlignmentX(LEFT_ALIGNMENT);
            catLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 0));
            sidebarContent.add(catLabel);

            for (AgenticTask task : catEntry.getValue()) {
                JButton taskBtn = new JButton(task.getName());
                taskBtn.setToolTipText(task.getDescription());
                taskBtn.setAlignmentX(LEFT_ALIGNMENT);
                taskBtn.setMaximumSize(new Dimension(250, 28));
                taskBtn.setFont(taskBtn.getFont().deriveFont(11f));
                taskBtn.setHorizontalAlignment(SwingConstants.LEFT);
                taskBtn.setEnabled(task.isAvailable());
                taskBtn.setBorder(BorderFactory.createEmptyBorder(2, 12, 2, 4));
                if (!task.isAvailable()) {
                    taskBtn.setForeground(new Color(180, 180, 190));
                }
                taskBtn.addActionListener(e -> task.execute(buildTaskContext()));
                sidebarContent.add(taskBtn);
            }
        }

        sidebarContent.add(Box.createVerticalStrut(4));
        sidebarContent.add(smallButton("+ New Workflow", e -> onNewWorkflow()));

        addSeparator();
        addRecommendationsSection();
        addSeparator();
        addContextHealthSection();
        addSeparator();
        addUpcomingSection();

        sidebarContent.add(Box.createVerticalGlue());
        sidebarContent.revalidate();
        sidebarContent.repaint();
        updateRefreshSelectedButton();
    }

    private void addSeparator() {
        sidebarContent.add(Box.createVerticalStrut(12));
        sidebarContent.add(new JSeparator());
        sidebarContent.add(Box.createVerticalStrut(8));
    }

    private JButton smallButton(String text, java.awt.event.ActionListener action) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(10f));
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(250, 24));
        btn.addActionListener(action);
        return btn;
    }

    private void addRecommendationsSection() {
        List<Recommendation> recs = recommendationEngine.getRecommendations();
        if (recs.isEmpty()) return;
        addSidebarSection("RECOMMENDED");

        for (Recommendation rec : recs) {
            JPanel recPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            recPanel.setOpaque(false);
            recPanel.setAlignmentX(LEFT_ALIGNMENT);
            recPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
            recPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            Color urgencyColor = switch (rec.urgency()) {
                case "HIGH" -> new Color(244, 67, 54);
                case "MEDIUM" -> new Color(255, 152, 0);
                default -> MUTED;
            };
            JLabel urgencyDot = new JLabel("●");
            urgencyDot.setForeground(urgencyColor);
            urgencyDot.setFont(urgencyDot.getFont().deriveFont(8f));
            recPanel.add(urgencyDot);

            JLabel recLabel = new JLabel(rec.title());
            recLabel.setFont(recLabel.getFont().deriveFont(10f));
            recLabel.setForeground(new Color(33, 100, 200));
            recLabel.setToolTipText(rec.reason());
            recPanel.add(recLabel);

            recPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    AgenticTask task = taskRegistry.getById(rec.linkedTaskId());
                    if (task != null) task.execute(buildTaskContext());
                }
            });
            sidebarContent.add(recPanel);
        }
    }

    private void addContextHealthSection() {
        addSidebarSection("CONTEXT HEALTH");
        Map<String, Freshness> report = orgContext.getFreshnessReport();

        long freshCount = report.values().stream().filter(f -> f == Freshness.FRESH).count();
        JLabel summaryLabel = new JLabel(freshCount + "/" + report.size() + " fields fresh");
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.ITALIC, 11f));
        summaryLabel.setForeground(new Color(130, 130, 140));
        summaryLabel.setAlignmentX(LEFT_ALIGNMENT);
        sidebarContent.add(summaryLabel);
        sidebarContent.add(Box.createVerticalStrut(8));

        // Worst first so the user's eye lands on what needs attention.
        Map<Freshness, List<String>> grouped = new LinkedHashMap<>();
        for (Freshness f : new Freshness[]{Freshness.NEEDS_CONFIRMATION, Freshness.STALE, Freshness.AGING, Freshness.FRESH}) {
            grouped.put(f, new ArrayList<>());
        }
        for (var entry : report.entrySet()) {
            grouped.get(entry.getValue()).add(entry.getKey());
        }
        for (var freshnessEntry : grouped.entrySet()) {
            if (!freshnessEntry.getValue().isEmpty()) {
                addFreshnessGroup(freshnessEntry.getKey(), freshnessEntry.getValue());
            }
        }

        sidebarContent.add(Box.createVerticalStrut(4));
        sidebarContent.add(smallButton("Select All Stale", e -> selectAllStale()));
    }

    private void onNewWorkflow() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        WorkflowEditorDialog dialog = new WorkflowEditorDialog(owner);
        dialog.setVisible(true);
        if (!dialog.wasCancelled()) {
            WorkflowDefinition wd = dialog.getResult();
            workflowStore.add(wd);
            taskRegistry.register(new UserWorkflowTask(wd));
            rebuildSidebar();
            statusLabel.setText("Workflow '" + wd.getName() + "' created.");
        }
    }

    private void addSidebarSection(String title) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setForeground(new Color(60, 60, 70));
        label.setAlignmentX(LEFT_ALIGNMENT);
        sidebarContent.add(label);
        sidebarContent.add(Box.createVerticalStrut(6));
    }

    private void addFreshnessGroup(Freshness freshness, List<String> fields) {
        String label = switch (freshness) {
            case FRESH -> "FRESH";
            case AGING -> "AGING";
            case STALE -> "STALE";
            case NEEDS_CONFIRMATION -> "NEEDS UPDATE";
        };

        JPanel groupHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        groupHeader.setOpaque(false);
        groupHeader.setAlignmentX(LEFT_ALIGNMENT);
        groupHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        JLabel dot = new JLabel("●");
        dot.setForeground(freshnessColor(freshness));
        dot.setFont(dot.getFont().deriveFont(10f));
        groupHeader.add(dot);
        JLabel groupLabel = new JLabel(label + " (" + fields.size() + ")");
        groupLabel.setFont(groupLabel.getFont().deriveFont(Font.BOLD, 10f));
        groupLabel.setForeground(TEXT);
        groupHeader.add(groupLabel);
        sidebarContent.add(groupHeader);

        for (String fieldName : fields) {
            JCheckBox cb = new JCheckBox(OrganizationContext.getFieldLabel(fieldName));
            cb.setFont(cb.getFont().deriveFont(10f));
            cb.setOpaque(false);
            cb.setAlignmentX(LEFT_ALIGNMENT);
            cb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            cb.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 2));
            cb.setForeground(TEXT);
            cb.addActionListener(e -> updateRefreshSelectedButton());

            ContextEntry<String> entry = orgContext.getEntry(fieldName);
            if (entry != null) {
                String preview = (entry.getValue() == null || entry.getValue().isBlank())
                    ? "(empty)" : truncate(entry.getValue(), 60);
                cb.setToolTipText("Last updated: " + entry.getLastUpdated() + " | " + preview);
            }
            fieldCheckboxes.put(fieldName, cb);
            sidebarContent.add(cb);
        }
        sidebarContent.add(Box.createVerticalStrut(4));
    }

    private void addUpcomingSection() {
        addSidebarSection("UPCOMING");
        List<OperationalFeedItem> overdue = feedStore.getOverdue();
        List<OperationalFeedItem> upcoming = feedStore.getUpcoming(7);

        if (overdue.isEmpty() && upcoming.isEmpty()) {
            JLabel emptyLabel = new JLabel("No upcoming items");
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 10f));
            emptyLabel.setForeground(new Color(150, 150, 160));
            emptyLabel.setAlignmentX(LEFT_ALIGNMENT);
            sidebarContent.add(emptyLabel);
        } else {
            for (OperationalFeedItem item : overdue) {
                sidebarContent.add(feedLabel(item, new Color(244, 67, 54), "OVERDUE"));
            }
            int shown = 0;
            for (OperationalFeedItem item : upcoming) {
                if (item.isOverdue()) continue;
                if (shown >= 5) break;
                sidebarContent.add(feedLabel(item, TEXT, item.getType()));
                shown++;
            }
        }

        sidebarContent.add(Box.createVerticalStrut(4));
        sidebarContent.add(smallButton("+ Add Item", e -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            OperationalFeedDialog dialog = new OperationalFeedDialog(owner);
            dialog.setVisible(true);
            if (!dialog.wasCancelled()) {
                feedStore.addItem(dialog.getResult());
                rebuildSidebar();
            }
        }));
    }

    private static JLabel feedLabel(OperationalFeedItem item, Color color, String tipPrefix) {
        JLabel itemLabel = new JLabel(item.toDisplayString());
        itemLabel.setFont(itemLabel.getFont().deriveFont(10f));
        itemLabel.setForeground(color);
        itemLabel.setToolTipText(tipPrefix + " | " + (item.getNotes() != null ? item.getNotes() : ""));
        itemLabel.setAlignmentX(LEFT_ALIGNMENT);
        itemLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        return itemLabel;
    }

    private void updateRefreshSelectedButton() {
        long count = fieldCheckboxes.values().stream().filter(JCheckBox::isSelected).count();
        refreshSelectedBtn.setEnabled(count > 0);
        refreshSelectedBtn.setText("Refresh Selected (" + count + ")");
    }

    private void selectAllStale() {
        Map<String, Freshness> report = orgContext.getFreshnessReport();
        for (var entry : fieldCheckboxes.entrySet()) {
            Freshness f = report.get(entry.getKey());
            entry.getValue().setSelected(f == Freshness.STALE || f == Freshness.NEEDS_CONFIRMATION);
        }
        updateRefreshSelectedButton();
    }

    private void onRefreshSelected() {
        List<String> selected = new ArrayList<>();
        for (var entry : fieldCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) selected.add(entry.getKey());
        }
        if (selected.isEmpty()) return;
        AgenticTask refreshTask = taskRegistry.getById("context-refresh");
        if (refreshTask != null) {
            refreshTask.execute(buildTaskContext(), selected);
        }
    }

    private AgenticTaskContext buildTaskContext() {
        return new AgenticTaskContext(orgContext, reconciliation, changeLog, config, clients, this);
    }

    // ---------------------------------------------------------------- Cards

    private JPanel createApprovalCard(ProposedChange change) {
        JPanel card = new JPanel(new BorderLayout(8, 4));
        card.setBackground(Color.WHITE);
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 193, 7), 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        JLabel fieldLabel = new JLabel(OrganizationContext.getFieldLabel(change.fieldName()));
        fieldLabel.setFont(fieldLabel.getFont().deriveFont(Font.BOLD, 12f));
        card.add(fieldLabel, BorderLayout.NORTH);

        JPanel diffPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        diffPanel.setOpaque(false);
        String currentDisplay = (change.currentValue() == null || change.currentValue().isBlank())
            ? "(empty)" : truncate(change.currentValue(), 300);
        JLabel currentLabel = new JLabel("<html><b>Current:</b> " + escapeHtml(currentDisplay) + "</html>");
        currentLabel.setFont(currentLabel.getFont().deriveFont(11f));
        currentLabel.setForeground(new Color(120, 120, 130));
        diffPanel.add(currentLabel);
        JLabel proposedLabel = new JLabel("<html><b>Proposed:</b> "
            + escapeHtml(truncate(change.proposedValue(), 300)) + "</html>");
        proposedLabel.setFont(proposedLabel.getFont().deriveFont(11f));
        proposedLabel.setForeground(new Color(33, 150, 243));
        diffPanel.add(proposedLabel);
        card.add(diffPanel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        JButton approveBtn = new JButton("Approve");
        approveBtn.setForeground(new Color(76, 175, 80));
        approveBtn.addActionListener(e -> {
            reconciliation.approve(change);
            afterDecision();
        });
        buttons.add(approveBtn);
        JButton rejectBtn = new JButton("Reject");
        rejectBtn.setForeground(new Color(244, 67, 54));
        rejectBtn.addActionListener(e -> {
            reconciliation.reject(change);
            afterDecision();
        });
        buttons.add(rejectBtn);
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    private void afterDecision() {
        List<ProposedChange> remaining = reconciliation.getApprovalQueue();
        showProposals(remaining);
        if (remaining.isEmpty()) statusLabel.setText("All changes processed.");
    }

    private void showEmptyState() {
        outputArea.removeAll();
        approvalArea.removeAll();
        outputArea.add(createCard("Get Started",
            "Select a task from the sidebar, or check context fields and click 'Refresh Selected'.\n\n"
            + "Tasks run AI-powered routines that can read and update your organization context.",
            new Color(240, 240, 248), new Color(180, 180, 200)));
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private JPanel createCard(String title, String content, Color bg, Color border) {
        JPanel card = new JPanel(new BorderLayout(8, 4));
        card.setBackground(bg);
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(border, 1),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        if (title != null) {
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
            card.add(titleLabel, BorderLayout.NORTH);
        }
        JTextArea textArea = new JTextArea(content == null ? "" : content);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setFont(textArea.getFont().deriveFont(12f));

        JScrollPane textScroll = new JScrollPane(textArea);
        textScroll.setBorder(null);
        textScroll.setOpaque(false);
        textScroll.getViewport().setOpaque(false);
        textScroll.setPreferredSize(new Dimension(0, 250));
        textScroll.getVerticalScrollBar().setUnitIncrement(16);
        card.add(textScroll, BorderLayout.CENTER);
        return card;
    }

    private static Color freshnessColor(Freshness f) {
        return switch (f) {
            case FRESH -> new Color(76, 175, 80);
            case AGING -> new Color(255, 193, 7);
            case STALE -> new Color(255, 152, 0);
            case NEEDS_CONFIRMATION -> new Color(244, 67, 54);
        };
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
