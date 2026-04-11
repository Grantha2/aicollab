package collab;

// ============================================================
// AgenticRoutinesPanel.java — Agentic Tab workspace with task
// registry sidebar, context health checkboxes, and function
// output / approval queue.
//
// WHAT THIS CLASS DOES (one sentence):
// Renders registered agentic tasks and context health in a sidebar,
// executes tasks on demand, and displays results with approval cards.
//
// KEY DESIGN DECISIONS:
// - Task list at top of sidebar (from AgenticTaskRegistry)
// - Context health below with per-field checkboxes
// - "Refresh Selected" feeds checked fields into ContextRefreshTask
// - Tasks call back to this panel for output display and approvals
// - Session-start trigger checks for stale context on first view
// ============================================================

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class AgenticRoutinesPanel extends JPanel {

    private final OrganizationContext orgContext;
    private final ReconciliationService reconciliation;
    private final ContextChangeLog changeLog;
    private final Config config;
    private final AgenticTaskRegistry taskRegistry;
    private final OperationalFeedStore feedStore;
    private final WorkflowStore workflowStore;
    private final RecommendationEngine recommendationEngine;

    // Optional — managed-agents subsystem (null when disabled in config).
    private ManagedAgentClient managedAgentClient;

    // Active managed-agent session (null when no agentic-mode task is running).
    private String activeSessionId;
    private AgenticModeTask activeSessionTask;

    // UI components
    private final JPanel sidebarContent;    // left sidebar content (tasks + health + upcoming)
    private final JPanel mainPanel;         // right: function output + approvals
    private final JPanel outputArea;        // function output text
    private final JPanel approvalArea;      // approval diff cards
    private final JLabel statusLabel;
    private final JButton refreshSelectedBtn;

    // Per-field checkbox tracking
    private final Map<String, JCheckBox> fieldCheckboxes = new LinkedHashMap<>();

    // Session-start trigger state
    private boolean sessionStartCheckDone = false;

    public AgenticRoutinesPanel(OrganizationContext orgContext,
                                 ReconciliationService reconciliation,
                                 ContextChangeLog changeLog,
                                 Config config,
                                 AgenticTaskRegistry taskRegistry,
                                 OperationalFeedStore feedStore,
                                 WorkflowStore workflowStore,
                                 RecommendationEngine recommendationEngine) {
        this.orgContext = orgContext;
        this.reconciliation = reconciliation;
        this.changeLog = changeLog;
        this.config = config;
        this.feedStore = feedStore;
        this.taskRegistry = taskRegistry;
        this.workflowStore = workflowStore;
        this.recommendationEngine = recommendationEngine;

        setLayout(new BorderLayout());
        setBackground(new Color(245, 245, 250));

        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        header.setOpaque(false);
        JLabel titleLabel = new JLabel("Agentic Routines");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        header.add(titleLabel);
        add(header, BorderLayout.NORTH);

        // Status bar
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        statusLabel.setForeground(new Color(100, 100, 110));
        add(statusLabel, BorderLayout.SOUTH);

        // Left sidebar
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

        // Bottom button bar for sidebar
        JPanel sidebarButtons = new JPanel();
        sidebarButtons.setLayout(new BoxLayout(sidebarButtons, BoxLayout.Y_AXIS));
        sidebarButtons.setBackground(Color.WHITE);
        sidebarButtons.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 230)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        refreshSelectedBtn = new JButton("Refresh Selected");
        refreshSelectedBtn.setAlignmentX(LEFT_ALIGNMENT);
        refreshSelectedBtn.setMaximumSize(new Dimension(250, 30));
        refreshSelectedBtn.setEnabled(false);
        refreshSelectedBtn.addActionListener(e -> onRefreshSelected());
        sidebarButtons.add(refreshSelectedBtn);

        leftPanel.add(sidebarButtons, BorderLayout.SOUTH);
        leftPanel.setPreferredSize(new Dimension(270, 0));

        // Main area: output + approvals
        mainPanel = new JPanel(new BorderLayout(0, 12));
        mainPanel.setBackground(new Color(245, 245, 250));
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

        // Split pane
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, mainPanel);
        split.setDividerLocation(270);
        split.setResizeWeight(0.0);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        // Initial render
        rebuildSidebar();
        showEmptyState();
    }

    // ===================== Session-Start Trigger =====================

    public void onTabShown() {
        if (sessionStartCheckDone) return;
        sessionStartCheckDone = true;

        Map<String, Freshness> report = orgContext.getFreshnessReport();
        long staleCount = report.values().stream()
            .filter(f -> f == Freshness.STALE || f == Freshness.NEEDS_CONFIRMATION)
            .count();

        if (staleCount > 0) {
            statusLabel.setText(staleCount + " field(s) are stale. Select fields and click 'Refresh Selected' to update.");
            selectAllStale();
        }
    }

    // ===================== Sidebar: Tasks + Context Health =====================

    private void rebuildSidebar() {
        sidebarContent.removeAll();
        fieldCheckboxes.clear();

        // --- Task List Section ---
        addSidebarSection("TASKS");

        Map<String, List<AgenticTask>> byCategory = taskRegistry.getByCategory();
        for (var catEntry : byCategory.entrySet()) {
            String category = catEntry.getKey();
            List<AgenticTask> tasks = catEntry.getValue();

            // Category label
            JLabel catLabel = new JLabel(category);
            catLabel.setFont(catLabel.getFont().deriveFont(Font.BOLD, 11f));
            catLabel.setForeground(new Color(100, 100, 110));
            catLabel.setAlignmentX(LEFT_ALIGNMENT);
            catLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 0));
            sidebarContent.add(catLabel);

            for (AgenticTask task : tasks) {
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

                taskBtn.addActionListener(e -> {
                    AgenticTaskContext ctx = buildTaskContext();
                    task.execute(ctx);
                });
                sidebarContent.add(taskBtn);
            }
        }

        // "+ New Workflow" button
        sidebarContent.add(Box.createVerticalStrut(4));
        JButton newWorkflowBtn = new JButton("+ New Workflow");
        newWorkflowBtn.setFont(newWorkflowBtn.getFont().deriveFont(10f));
        newWorkflowBtn.setAlignmentX(LEFT_ALIGNMENT);
        newWorkflowBtn.setMaximumSize(new Dimension(250, 24));
        newWorkflowBtn.addActionListener(e -> onNewWorkflow());
        sidebarContent.add(newWorkflowBtn);

        // --- Separator ---
        sidebarContent.add(Box.createVerticalStrut(12));
        sidebarContent.add(new JSeparator());
        sidebarContent.add(Box.createVerticalStrut(8));

        // --- Recommendations Section ---
        addRecommendationsSection();

        // --- Separator ---
        sidebarContent.add(Box.createVerticalStrut(12));
        sidebarContent.add(new JSeparator());
        sidebarContent.add(Box.createVerticalStrut(8));

        // --- Context Health Section ---
        addSidebarSection("CONTEXT HEALTH");

        Map<String, Freshness> report = orgContext.getFreshnessReport();

        // Summary line
        long totalFields = report.size();
        long freshCount = report.values().stream().filter(f -> f == Freshness.FRESH).count();
        JLabel summaryLabel = new JLabel(freshCount + "/" + totalFields + " fields fresh");
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.ITALIC, 11f));
        summaryLabel.setForeground(new Color(130, 130, 140));
        summaryLabel.setAlignmentX(LEFT_ALIGNMENT);
        sidebarContent.add(summaryLabel);
        sidebarContent.add(Box.createVerticalStrut(8));

        // Group fields by freshness
        Map<Freshness, List<String>> grouped = new LinkedHashMap<>();
        grouped.put(Freshness.NEEDS_CONFIRMATION, new ArrayList<>());
        grouped.put(Freshness.STALE, new ArrayList<>());
        grouped.put(Freshness.AGING, new ArrayList<>());
        grouped.put(Freshness.FRESH, new ArrayList<>());

        for (var entry : report.entrySet()) {
            grouped.get(entry.getValue()).add(entry.getKey());
        }

        for (var freshnessEntry : grouped.entrySet()) {
            Freshness freshness = freshnessEntry.getKey();
            List<String> fields = freshnessEntry.getValue();
            if (fields.isEmpty()) continue;
            addFreshnessGroup(freshness, fields);
        }

        // Select all stale button
        JButton selectStaleBtn = new JButton("Select All Stale");
        selectStaleBtn.setAlignmentX(LEFT_ALIGNMENT);
        selectStaleBtn.setMaximumSize(new Dimension(250, 24));
        selectStaleBtn.setFont(selectStaleBtn.getFont().deriveFont(10f));
        selectStaleBtn.addActionListener(e -> selectAllStale());
        sidebarContent.add(Box.createVerticalStrut(4));
        sidebarContent.add(selectStaleBtn);

        // --- Separator ---
        sidebarContent.add(Box.createVerticalStrut(12));
        sidebarContent.add(new JSeparator());
        sidebarContent.add(Box.createVerticalStrut(8));

        // --- Upcoming Section ---
        addUpcomingSection();

        sidebarContent.add(Box.createVerticalGlue());
        sidebarContent.revalidate();
        sidebarContent.repaint();
        updateRefreshSelectedButton();
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
                default -> new Color(100, 100, 110);
            };

            JLabel urgencyDot = new JLabel("\u25CF");
            urgencyDot.setForeground(urgencyColor);
            urgencyDot.setFont(urgencyDot.getFont().deriveFont(8f));
            recPanel.add(urgencyDot);

            JLabel recLabel = new JLabel(rec.title());
            recLabel.setFont(recLabel.getFont().deriveFont(10f));
            recLabel.setForeground(new Color(33, 100, 200));
            recLabel.setToolTipText(rec.reason());
            recPanel.add(recLabel);

            recPanel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    AgenticTask task = taskRegistry.getById(rec.linkedTaskId());
                    if (task != null) {
                        AgenticTaskContext ctx = buildTaskContext();
                        task.execute(ctx);
                    }
                }
            });

            sidebarContent.add(recPanel);
        }
    }

    private void onNewWorkflow() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        WorkflowEditorDialog dialog = new WorkflowEditorDialog(owner);
        dialog.setVisible(true);
        if (!dialog.wasCancelled()) {
            WorkflowDefinition wd = dialog.getResult();
            workflowStore.add(wd);
            UserWorkflowTask task = new UserWorkflowTask(wd);
            taskRegistry.register(task);
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
        Color dotColor = freshnessColor(freshness);
        String label = switch (freshness) {
            case FRESH -> "FRESH";
            case AGING -> "AGING";
            case STALE -> "STALE";
            case NEEDS_CONFIRMATION -> "NEEDS UPDATE";
        };

        // Group header with dot
        JPanel groupHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        groupHeader.setOpaque(false);
        groupHeader.setAlignmentX(LEFT_ALIGNMENT);
        groupHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel dot = new JLabel("\u25CF");
        dot.setForeground(dotColor);
        dot.setFont(dot.getFont().deriveFont(10f));
        groupHeader.add(dot);

        JLabel groupLabel = new JLabel(label + " (" + fields.size() + ")");
        groupLabel.setFont(groupLabel.getFont().deriveFont(Font.BOLD, 10f));
        groupLabel.setForeground(new Color(80, 80, 90));
        groupHeader.add(groupLabel);

        sidebarContent.add(groupHeader);

        // Per-field checkboxes
        for (String fieldName : fields) {
            JCheckBox cb = new JCheckBox(OrganizationContext.getFieldLabel(fieldName));
            cb.setFont(cb.getFont().deriveFont(10f));
            cb.setOpaque(false);
            cb.setAlignmentX(LEFT_ALIGNMENT);
            cb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            cb.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 2));
            cb.setForeground(new Color(80, 80, 90));
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
            // Overdue items in red
            for (OperationalFeedItem item : overdue) {
                JLabel itemLabel = new JLabel(item.toDisplayString());
                itemLabel.setFont(itemLabel.getFont().deriveFont(10f));
                itemLabel.setForeground(new Color(244, 67, 54));
                itemLabel.setToolTipText("OVERDUE | " + (item.getNotes() != null ? item.getNotes() : ""));
                itemLabel.setAlignmentX(LEFT_ALIGNMENT);
                itemLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
                sidebarContent.add(itemLabel);
            }
            // Upcoming items
            int shown = 0;
            for (OperationalFeedItem item : upcoming) {
                if (item.isOverdue()) continue; // already shown above
                if (shown >= 5) break;
                JLabel itemLabel = new JLabel(item.toDisplayString());
                itemLabel.setFont(itemLabel.getFont().deriveFont(10f));
                itemLabel.setForeground(new Color(80, 80, 90));
                itemLabel.setToolTipText(item.getType() + " | " + (item.getNotes() != null ? item.getNotes() : ""));
                itemLabel.setAlignmentX(LEFT_ALIGNMENT);
                itemLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
                sidebarContent.add(itemLabel);
                shown++;
            }
        }

        sidebarContent.add(Box.createVerticalStrut(4));

        // Add item button
        JButton addItemBtn = new JButton("+ Add Item");
        addItemBtn.setFont(addItemBtn.getFont().deriveFont(10f));
        addItemBtn.setAlignmentX(LEFT_ALIGNMENT);
        addItemBtn.setMaximumSize(new Dimension(250, 24));
        addItemBtn.addActionListener(e -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            OperationalFeedDialog dialog = new OperationalFeedDialog(owner);
            dialog.setVisible(true);
            if (!dialog.wasCancelled()) {
                feedStore.addItem(dialog.getResult());
                rebuildSidebar();
            }
        });
        sidebarContent.add(addItemBtn);
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

    private List<String> getSelectedFields() {
        List<String> selected = new ArrayList<>();
        for (var entry : fieldCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) selected.add(entry.getKey());
        }
        return selected;
    }

    private void onRefreshSelected() {
        List<String> selected = getSelectedFields();
        if (selected.isEmpty()) return;

        AgenticTask refreshTask = taskRegistry.getById("context-refresh");
        if (refreshTask != null) {
            AgenticTaskContext ctx = buildTaskContext();
            refreshTask.execute(ctx, selected);
        }
    }

    private AgenticTaskContext buildTaskContext() {
        return new AgenticTaskContext(orgContext, reconciliation, changeLog, config, this, managedAgentClient);
    }

    /** Wires in the managed-agent client after construction (see MainGui.initApplication). */
    public void setManagedAgentClient(ManagedAgentClient client) {
        this.managedAgentClient = client;
    }

    // ===================== Public API for tasks to call back =====================

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    public void showLoading(String message) {
        outputArea.removeAll();
        approvalArea.removeAll();
        JPanel card = createCard("Processing...", message,
            new Color(227, 242, 253), new Color(144, 202, 249));
        outputArea.add(card);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    public void showFunctionOutput(String title, String content) {
        outputArea.removeAll();
        JPanel card = createCard(title, content, new Color(232, 245, 233), new Color(129, 199, 132));
        outputArea.add(card);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    // ===================== Managed Agent streaming =====================

    /**
     * Records the currently-running managed-agent session so the panel
     * can send follow-up events (e.g. a confirmation) on the same
     * conversation. Called by AgenticModeTask as soon as a session id
     * comes back from the API.
     */
    public void setActiveAgentSession(String sessionId,
                                       ManagedAgentClient client,
                                       AgenticModeTask task) {
        this.activeSessionId = sessionId;
        this.managedAgentClient = client;
        this.activeSessionTask = task;
    }

    /**
     * Renders one streamed AgentEvent in the output area. Always hops
     * to the EDT before touching Swing — callers may be SwingWorker
     * background threads.
     *
     * Special handling: if a text event contains the
     * "AWAITING_CONFIRMATION: optA | optB | optC" marker, we pop a
     * radio-selection dialog and send the user's choice as a follow-up
     * event on the same session.
     */
    public void appendAgentEvent(AgentEvent ev) {
        if (ev == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            final AgentEvent captured = ev;
            SwingUtilities.invokeLater(() -> appendAgentEvent(captured));
            return;
        }

        switch (ev.type()) {
            case "text" -> {
                outputArea.add(createCard("Agent", ev.content(),
                        new Color(232, 245, 233), new Color(129, 199, 132)));
                maybeHandleConfirmationMarker(ev.content());
            }
            case "tool_use" -> {
                outputArea.add(createCard("Tool call", ev.content(),
                        new Color(227, 242, 253), new Color(144, 202, 249)));
            }
            case "tool_result" -> {
                outputArea.add(createCard("Tool result", ev.content(),
                        new Color(245, 245, 245), new Color(189, 189, 189)));
            }
            case "file" -> {
                outputArea.add(createFileCard(ev.fileId(), ev.content()));
            }
            case "status" -> {
                outputArea.add(createCard("Status", ev.content(),
                        new Color(255, 249, 196), new Color(251, 192, 45)));
            }
            default -> {
                outputArea.add(createCard(ev.type(), String.valueOf(ev.content()),
                        new Color(245, 245, 245), new Color(189, 189, 189)));
            }
        }

        outputArea.add(Box.createVerticalStrut(6));
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    /**
     * Detects the AWAITING_CONFIRMATION marker on a text event and, if
     * present, pops a radio dialog for the user to pick one option.
     * The selection is then sent back into the same session as a
     * follow-up user message.
     */
    private void maybeHandleConfirmationMarker(String text) {
        if (text == null) return;
        int idx = text.indexOf("AWAITING_CONFIRMATION:");
        if (idx < 0) return;

        String tail = text.substring(idx + "AWAITING_CONFIRMATION:".length()).trim();
        // Stop at the first newline if the agent added more text after the options line.
        int nl = tail.indexOf('\n');
        if (nl >= 0) tail = tail.substring(0, nl);

        String[] rawOptions = tail.split("\\|");
        List<String> options = new ArrayList<>();
        for (String o : rawOptions) {
            String t = o.trim();
            if (!t.isEmpty()) options.add(t);
        }
        if (options.isEmpty()) return;

        if (activeSessionId == null || managedAgentClient == null) {
            outputArea.add(createCard("Confirmation needed",
                    "Agent asked for confirmation but the session is no longer active.",
                    new Color(255, 235, 238), new Color(229, 115, 115)));
            return;
        }

        final String sessionId = activeSessionId;
        final ManagedAgentClient client = managedAgentClient;
        final List<String> opts = options;

        // Dialog must run on the EDT; we're already on it here.
        String[] optArr = opts.toArray(new String[0]);
        String chosen = (String) JOptionPane.showInputDialog(
                this,
                "Select an option to confirm:",
                "Agent needs confirmation",
                JOptionPane.QUESTION_MESSAGE,
                null,
                optArr,
                optArr[0]);

        if (chosen == null) {
            outputArea.add(createCard("Cancelled",
                    "Confirmation skipped \u2014 session left open.",
                    new Color(255, 249, 196), new Color(251, 192, 45)));
            return;
        }

        final String choice = chosen;
        setStatus("Sending confirmation to agent\u2026");

        new SwingWorker<Void, AgentEvent>() {
            @Override
            protected Void doInBackground() {
                client.sendEvent(sessionId,
                        "Confirmed: " + choice,
                        List.of(),
                        this::publish);
                return null;
            }

            @Override
            protected void process(java.util.List<AgentEvent> chunks) {
                chunks.forEach(AgenticRoutinesPanel.this::appendAgentEvent);
            }

            @Override
            protected void done() {
                setStatus("Agent follow-up complete.");
            }
        }.execute();
    }

    /**
     * Builds a card with a "Download" button for a file event. Clicking
     * the button prompts for a save location and streams the file down
     * via the Managed Agents Files API.
     */
    private JPanel createFileCard(String fileId, String displayName) {
        JPanel card = new JPanel(new BorderLayout(8, 4));
        card.setBackground(new Color(237, 231, 246));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(149, 117, 205), 1),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));

        JLabel title = new JLabel("File ready: "
                + (displayName == null || displayName.isBlank() ? fileId : displayName));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        card.add(title, BorderLayout.NORTH);

        JButton downloadBtn = new JButton("Download");
        downloadBtn.addActionListener(e -> {
            if (managedAgentClient == null) {
                JOptionPane.showMessageDialog(this,
                        "Managed Agents client is not configured.",
                        "Download failed", JOptionPane.ERROR_MESSAGE);
                return;
            }
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File(
                    displayName == null || displayName.isBlank() ? fileId : displayName));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            java.nio.file.Path out = chooser.getSelectedFile().toPath();

            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    return managedAgentClient.downloadFile(fileId, out);
                }
                @Override
                protected void done() {
                    try {
                        boolean ok = get();
                        JOptionPane.showMessageDialog(AgenticRoutinesPanel.this,
                                ok ? "Saved to " + out : "Download failed \u2014 see log.",
                                ok ? "Download complete" : "Download failed",
                                ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(AgenticRoutinesPanel.this,
                                "Download failed: " + ex.getMessage(),
                                "Download failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.setOpaque(false);
        south.add(downloadBtn);
        card.add(south, BorderLayout.SOUTH);

        return card;
    }

    public void handleReconciliationResult(ReconciliationService.ReconciliationResult result) {
        if (result == null) {
            showFunctionOutput("Complete", "No updates needed \u2014 all context appears current.");
            statusLabel.setText("No updates needed.");
        } else {
            int autoCount = result.autoApplied().size();
            int pendingCount = result.needsApproval().size();

            StringBuilder summary = new StringBuilder();
            if (autoCount > 0) {
                summary.append(autoCount).append(" change(s) auto-applied (low-risk/additive).\n");
                for (ProposedChange c : result.autoApplied()) {
                    summary.append("  \u2713 ").append(OrganizationContext.getFieldLabel(c.fieldName())).append("\n");
                }
            }
            if (pendingCount > 0) {
                summary.append(pendingCount).append(" change(s) need your approval below.");
            }
            if (autoCount == 0 && pendingCount == 0) {
                summary.append("No changes proposed.");
            }

            showFunctionOutput("Results", summary.toString());
            showApprovalCards(reconciliation.getApprovalQueue());
            rebuildSidebar();
            statusLabel.setText("Complete. " + autoCount + " auto-applied, " + pendingCount + " pending.");
        }

        // Clear checkboxes
        fieldCheckboxes.values().forEach(cb -> cb.setSelected(false));
        updateRefreshSelectedButton();
    }

    public void showApprovalCards(List<ProposedChange> pendingChanges) {
        approvalArea.removeAll();
        if (pendingChanges.isEmpty()) return;

        JLabel title = new JLabel("Pending Approvals (" + pendingChanges.size() + ")");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        approvalArea.add(title);
        approvalArea.add(Box.createVerticalStrut(8));

        for (ProposedChange change : pendingChanges) {
            approvalArea.add(createApprovalCard(change));
            approvalArea.add(Box.createVerticalStrut(6));
        }

        mainPanel.revalidate();
        mainPanel.repaint();
    }

    // ===================== Approval Cards =====================

    private JPanel createApprovalCard(ProposedChange change) {
        JPanel card = new JPanel(new BorderLayout(8, 4));
        card.setBackground(Color.WHITE);
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 193, 7), 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        JLabel fieldLabel = new JLabel(OrganizationContext.getFieldLabel(change.fieldName()));
        fieldLabel.setFont(fieldLabel.getFont().deriveFont(Font.BOLD, 12f));
        card.add(fieldLabel, BorderLayout.NORTH);

        JPanel diffPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        diffPanel.setOpaque(false);

        String currentDisplay = (change.currentValue() == null || change.currentValue().isBlank())
            ? "(empty)" : truncate(change.currentValue(), 300);
        String proposedDisplay = truncate(change.proposedValue(), 300);

        JLabel currentLabel = new JLabel("<html><b>Current:</b> " + escapeHtml(currentDisplay) + "</html>");
        currentLabel.setFont(currentLabel.getFont().deriveFont(11f));
        currentLabel.setForeground(new Color(120, 120, 130));
        diffPanel.add(currentLabel);

        JLabel proposedLabel = new JLabel("<html><b>Proposed:</b> " + escapeHtml(proposedDisplay) + "</html>");
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
            rebuildSidebar();
            showApprovalCards(reconciliation.getApprovalQueue());
            if (reconciliation.getApprovalQueue().isEmpty()) statusLabel.setText("All changes processed.");
        });
        buttons.add(approveBtn);

        JButton rejectBtn = new JButton("Reject");
        rejectBtn.setForeground(new Color(244, 67, 54));
        rejectBtn.addActionListener(e -> {
            reconciliation.reject(change);
            showApprovalCards(reconciliation.getApprovalQueue());
            if (reconciliation.getApprovalQueue().isEmpty()) statusLabel.setText("All changes processed.");
        });
        buttons.add(rejectBtn);

        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    // ===================== Empty State =====================

    private void showEmptyState() {
        outputArea.removeAll();
        approvalArea.removeAll();

        JPanel card = createCard("Get Started",
            "Select a task from the sidebar, or check context fields and click 'Refresh Selected'.\n\n" +
            "Tasks run AI-powered routines that can read and update your organization context.",
            new Color(240, 240, 248), new Color(180, 180, 200));
        outputArea.add(card);

        mainPanel.revalidate();
        mainPanel.repaint();
    }

    // ===================== UI Helpers =====================

    private JPanel createCard(String title, String content, Color bg, Color border) {
        JPanel card = new JPanel(new BorderLayout(8, 4));
        card.setBackground(bg);
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(border, 1),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));

        if (title != null) {
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
            card.add(titleLabel, BorderLayout.NORTH);
        }

        JTextArea textArea = new JTextArea(content);
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
