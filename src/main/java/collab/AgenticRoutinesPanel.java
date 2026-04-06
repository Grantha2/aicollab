package collab;

// ============================================================
// AgenticRoutinesPanel.java — Agentic Tab workspace with
// context health sidebar and function output / approval queue.
//
// WHAT THIS CLASS DOES (one sentence):
// Displays context freshness as an actionable sidebar with
// per-field checkboxes, runs agentic functions, and shows
// approval diff cards for context write-back.
//
// KEY DESIGN DECISIONS:
// - Context health IS the entry point to refresh flows (not passive)
// - Per-field checkboxes let users select exactly which fields to refresh
// - Stale fields grouped at top to drive action
// - Approval cards show current vs proposed with approve/reject
// - Session-start trigger checks for stale context on first view
// - Runs Daily Update function via SwingWorker to avoid blocking
// ============================================================

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class AgenticRoutinesPanel extends JPanel {

    private final OrganizationContext orgContext;
    private final ReconciliationService reconciliation;
    private final DailyContextUpdateFunction dailyUpdateFn;
    private final Config config;

    // UI components
    private final JPanel healthPanel;       // left sidebar: freshness indicators
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
                                 DailyContextUpdateFunction dailyUpdateFn,
                                 Config config) {
        this.orgContext = orgContext;
        this.reconciliation = reconciliation;
        this.dailyUpdateFn = dailyUpdateFn;
        this.config = config;

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

        // Left sidebar: context health with checkboxes
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(Color.WHITE);
        leftPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(220, 220, 230)));

        healthPanel = new JPanel();
        healthPanel.setLayout(new BoxLayout(healthPanel, BoxLayout.Y_AXIS));
        healthPanel.setBackground(Color.WHITE);
        healthPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane healthScroll = new JScrollPane(healthPanel);
        healthScroll.setBorder(null);
        healthScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftPanel.add(healthScroll, BorderLayout.CENTER);

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
        refreshSelectedBtn.setMaximumSize(new Dimension(240, 30));
        refreshSelectedBtn.setEnabled(false);
        refreshSelectedBtn.addActionListener(e -> onRefreshSelected());
        sidebarButtons.add(refreshSelectedBtn);
        sidebarButtons.add(Box.createVerticalStrut(4));

        JButton selectAllStaleBtn = new JButton("Select All Stale");
        selectAllStaleBtn.setAlignmentX(LEFT_ALIGNMENT);
        selectAllStaleBtn.setMaximumSize(new Dimension(240, 26));
        selectAllStaleBtn.setFont(selectAllStaleBtn.getFont().deriveFont(11f));
        selectAllStaleBtn.addActionListener(e -> selectAllStale());
        sidebarButtons.add(selectAllStaleBtn);
        sidebarButtons.add(Box.createVerticalStrut(4));

        JButton dailyUpdateBtn = new JButton("Daily Update (All)");
        dailyUpdateBtn.setAlignmentX(LEFT_ALIGNMENT);
        dailyUpdateBtn.setMaximumSize(new Dimension(240, 26));
        dailyUpdateBtn.setFont(dailyUpdateBtn.getFont().deriveFont(11f));
        dailyUpdateBtn.addActionListener(e -> onDailyUpdate(null));
        sidebarButtons.add(dailyUpdateBtn);

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
        refreshHealthPanel();
        showEmptyState();
    }

    // ===================== Session-Start Trigger =====================

    /**
     * Called when the agentic tab becomes visible. Checks for stale context
     * and prompts the user if a refresh is recommended.
     */
    public void onTabShown() {
        if (sessionStartCheckDone) return;
        sessionStartCheckDone = true;

        // Count stale/needs-confirmation fields
        Map<String, Freshness> report = orgContext.getFreshnessReport();
        long staleCount = report.values().stream()
            .filter(f -> f == Freshness.STALE || f == Freshness.NEEDS_CONFIRMATION)
            .count();

        if (staleCount > 0) {
            statusLabel.setText(staleCount + " field(s) are stale. Select fields and click 'Refresh Selected' to update.");
            // Auto-select stale fields
            selectAllStale();
        }
    }

    // ===================== Context Health Sidebar =====================

    public void refreshHealthPanel() {
        healthPanel.removeAll();
        fieldCheckboxes.clear();

        Map<String, Freshness> report = orgContext.getFreshnessReport();

        // Group fields by freshness
        Map<Freshness, List<String>> grouped = new LinkedHashMap<>();
        grouped.put(Freshness.NEEDS_CONFIRMATION, new ArrayList<>());
        grouped.put(Freshness.STALE, new ArrayList<>());
        grouped.put(Freshness.AGING, new ArrayList<>());
        grouped.put(Freshness.FRESH, new ArrayList<>());

        for (var entry : report.entrySet()) {
            grouped.get(entry.getValue()).add(entry.getKey());
        }

        // Section header
        JLabel sectionTitle = new JLabel("Context Health");
        sectionTitle.setFont(sectionTitle.getFont().deriveFont(Font.BOLD, 14f));
        sectionTitle.setAlignmentX(LEFT_ALIGNMENT);
        healthPanel.add(sectionTitle);
        healthPanel.add(Box.createVerticalStrut(8));

        // Summary line
        long totalFields = report.size();
        long freshCount = report.values().stream().filter(f -> f == Freshness.FRESH).count();
        JLabel summaryLabel = new JLabel(freshCount + "/" + totalFields + " fields fresh");
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.ITALIC, 11f));
        summaryLabel.setForeground(new Color(130, 130, 140));
        summaryLabel.setAlignmentX(LEFT_ALIGNMENT);
        healthPanel.add(summaryLabel);
        healthPanel.add(Box.createVerticalStrut(12));

        // Render each freshness group
        for (var freshnessEntry : grouped.entrySet()) {
            Freshness freshness = freshnessEntry.getKey();
            List<String> fields = freshnessEntry.getValue();
            if (fields.isEmpty()) continue;

            addFreshnessGroup(freshness, fields);
        }

        healthPanel.add(Box.createVerticalGlue());
        healthPanel.revalidate();
        healthPanel.repaint();
        updateRefreshSelectedButton();
    }

    private void addFreshnessGroup(Freshness freshness, List<String> fields) {
        Color dotColor = switch (freshness) {
            case FRESH -> new Color(76, 175, 80);               // green
            case AGING -> new Color(255, 193, 7);               // amber
            case STALE -> new Color(255, 152, 0);               // orange
            case NEEDS_CONFIRMATION -> new Color(244, 67, 54);  // red
        };
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
        groupHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel dot = new JLabel("\u25CF"); // filled circle
        dot.setForeground(dotColor);
        dot.setFont(dot.getFont().deriveFont(12f));
        groupHeader.add(dot);

        JLabel groupLabel = new JLabel(label + " (" + fields.size() + ")");
        groupLabel.setFont(groupLabel.getFont().deriveFont(Font.BOLD, 11f));
        groupLabel.setForeground(new Color(80, 80, 90));
        groupHeader.add(groupLabel);

        healthPanel.add(groupHeader);

        // Field list with checkboxes
        for (String fieldName : fields) {
            JCheckBox cb = new JCheckBox(OrganizationContext.getFieldLabel(fieldName));
            cb.setFont(cb.getFont().deriveFont(11f));
            cb.setOpaque(false);
            cb.setAlignmentX(LEFT_ALIGNMENT);
            cb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
            cb.setBorder(BorderFactory.createEmptyBorder(1, 16, 1, 4));
            cb.setForeground(new Color(80, 80, 90));
            cb.addActionListener(e -> updateRefreshSelectedButton());

            // Show last-updated tooltip
            ContextEntry<String> entry = orgContext.getEntry(fieldName);
            if (entry != null) {
                String lastUpdated = entry.getLastUpdated();
                String value = entry.getValue();
                String preview = (value == null || value.isBlank()) ? "(empty)" : truncate(value, 60);
                cb.setToolTipText("Last updated: " + lastUpdated + " | " + preview);
            }

            fieldCheckboxes.put(fieldName, cb);
            healthPanel.add(cb);
        }

        healthPanel.add(Box.createVerticalStrut(6));
    }

    private void updateRefreshSelectedButton() {
        long selectedCount = fieldCheckboxes.values().stream().filter(JCheckBox::isSelected).count();
        refreshSelectedBtn.setEnabled(selectedCount > 0);
        refreshSelectedBtn.setText("Refresh Selected (" + selectedCount + ")");
    }

    private void selectAllStale() {
        Map<String, Freshness> report = orgContext.getFreshnessReport();
        for (var entry : fieldCheckboxes.entrySet()) {
            Freshness f = report.get(entry.getKey());
            boolean isStale = (f == Freshness.STALE || f == Freshness.NEEDS_CONFIRMATION);
            entry.getValue().setSelected(isStale);
        }
        updateRefreshSelectedButton();
    }

    private List<String> getSelectedFields() {
        List<String> selected = new ArrayList<>();
        for (var entry : fieldCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    // ===================== Main Area: Output + Approvals =====================

    private void showEmptyState() {
        outputArea.removeAll();
        approvalArea.removeAll();

        JPanel emptyCard = createCard(
            "Get Started",
            "Select context fields using the checkboxes, then click 'Refresh Selected' " +
            "to update them with AI assistance.\n\n" +
            "Or click 'Daily Update (All)' to review and refresh all stale context at once.\n\n" +
            "The update function will:\n" +
            "  1. Ask what has changed recently\n" +
            "  2. Propose structured updates for each field\n" +
            "  3. Let you approve or reject each change",
            new Color(240, 240, 248),
            new Color(180, 180, 200)
        );
        outputArea.add(emptyCard);

        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private void showFunctionOutput(String title, String content) {
        outputArea.removeAll();

        JPanel card = createCard(title, content, new Color(232, 245, 233), new Color(129, 199, 132));
        outputArea.add(card);

        mainPanel.revalidate();
        mainPanel.repaint();
    }

    public void showApprovalCards(List<ProposedChange> pendingChanges) {
        approvalArea.removeAll();

        if (pendingChanges.isEmpty()) {
            return;
        }

        JLabel approvalTitle = new JLabel("Pending Approvals (" + pendingChanges.size() + ")");
        approvalTitle.setFont(approvalTitle.getFont().deriveFont(Font.BOLD, 13f));
        approvalTitle.setAlignmentX(LEFT_ALIGNMENT);
        approvalArea.add(approvalTitle);
        approvalArea.add(Box.createVerticalStrut(8));

        for (ProposedChange change : pendingChanges) {
            JPanel card = createApprovalCard(change);
            approvalArea.add(card);
            approvalArea.add(Box.createVerticalStrut(6));
        }

        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private JPanel createApprovalCard(ProposedChange change) {
        JPanel card = new JPanel(new BorderLayout(8, 4));
        card.setBackground(Color.WHITE);
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 193, 7), 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        // Field name header
        JLabel fieldLabel = new JLabel(OrganizationContext.getFieldLabel(change.fieldName()));
        fieldLabel.setFont(fieldLabel.getFont().deriveFont(Font.BOLD, 12f));
        card.add(fieldLabel, BorderLayout.NORTH);

        // Diff content
        JPanel diffPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        diffPanel.setOpaque(false);

        String currentDisplay = change.currentValue() == null || change.currentValue().isBlank()
            ? "(empty)" : truncate(change.currentValue(), 120);
        String proposedDisplay = truncate(change.proposedValue(), 120);

        JLabel currentLabel = new JLabel("<html><b>Current:</b> " + escapeHtml(currentDisplay) + "</html>");
        currentLabel.setFont(currentLabel.getFont().deriveFont(11f));
        currentLabel.setForeground(new Color(120, 120, 130));
        diffPanel.add(currentLabel);

        JLabel proposedLabel = new JLabel("<html><b>Proposed:</b> " + escapeHtml(proposedDisplay) + "</html>");
        proposedLabel.setFont(proposedLabel.getFont().deriveFont(11f));
        proposedLabel.setForeground(new Color(33, 150, 243));
        diffPanel.add(proposedLabel);

        card.add(diffPanel, BorderLayout.CENTER);

        // Approve / Reject buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);

        JButton approveBtn = new JButton("Approve");
        approveBtn.setForeground(new Color(76, 175, 80));
        approveBtn.addActionListener(e -> {
            reconciliation.approve(change);
            refreshHealthPanel();
            showApprovalCards(reconciliation.getApprovalQueue());
            if (reconciliation.getApprovalQueue().isEmpty()) {
                statusLabel.setText("All changes processed.");
            }
        });
        buttons.add(approveBtn);

        JButton rejectBtn = new JButton("Reject");
        rejectBtn.setForeground(new Color(244, 67, 54));
        rejectBtn.addActionListener(e -> {
            reconciliation.reject(change);
            showApprovalCards(reconciliation.getApprovalQueue());
            if (reconciliation.getApprovalQueue().isEmpty()) {
                statusLabel.setText("All changes processed.");
            }
        });
        buttons.add(rejectBtn);

        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    // ===================== Update Flows =====================

    /** Refreshes only the checkbox-selected fields. */
    private void onRefreshSelected() {
        List<String> selected = getSelectedFields();
        if (selected.isEmpty()) return;
        onDailyUpdate(selected);
    }

    /**
     * Runs the Daily Context Update function.
     * @param targetFields specific fields to update, or null for all stale
     */
    public void onDailyUpdate(List<String> targetFields) {
        // Build a description of what will be updated
        String fieldDesc;
        if (targetFields != null) {
            List<String> labels = targetFields.stream()
                .map(OrganizationContext::getFieldLabel)
                .toList();
            fieldDesc = "Fields to update: " + String.join(", ", labels);
        } else {
            fieldDesc = "All stale and aging fields will be reviewed.";
        }

        // Ask user what changed
        String userInput = (String) JOptionPane.showInputDialog(
            this,
            fieldDesc + "\n\nWhat has changed since your last update?\n" +
            "(Or press OK to just review current context.)",
            "Daily Context Update",
            JOptionPane.QUESTION_MESSAGE,
            null, null, ""
        );

        if (userInput == null) return; // cancelled

        statusLabel.setText("Running Daily Context Update...");
        outputArea.removeAll();
        approvalArea.removeAll();

        // Show loading state
        JPanel loadingCard = createCard("Processing...",
            "Analyzing context and generating update proposals...",
            new Color(227, 242, 253), new Color(144, 202, 249));
        outputArea.add(loadingCard);
        mainPanel.revalidate();
        mainPanel.repaint();

        // Run in background
        new SwingWorker<ReconciliationService.ReconciliationResult, Void>() {
            @Override
            protected ReconciliationService.ReconciliationResult doInBackground() {
                java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                int maxTokens = config.getMaxResponseTokens();
                LlmClient client = new AnthropicClient(httpClient, config.getClaudeUrl(),
                        config.getClaudeKey(), config.getClaudeModel(), maxTokens);
                return dailyUpdateFn.execute(client, targetFields, userInput);
            }

            @Override
            protected void done() {
                try {
                    ReconciliationService.ReconciliationResult result = get();
                    SwingUtilities.invokeLater(() -> {
                        if (result == null) {
                            showFunctionOutput("Daily Update Complete",
                                "No updates needed \u2014 all context appears current.");
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

                            showFunctionOutput("Daily Update Results", summary.toString());
                            showApprovalCards(reconciliation.getApprovalQueue());
                            refreshHealthPanel();

                            statusLabel.setText("Daily update complete. " +
                                autoCount + " auto-applied, " + pendingCount + " pending approval.");
                        }

                        // Clear checkboxes after run
                        fieldCheckboxes.values().forEach(cb -> cb.setSelected(false));
                        updateRefreshSelectedButton();
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        showFunctionOutput("Error",
                            "Daily update failed: " + e.getMessage());
                        statusLabel.setText("Error: " + e.getMessage());
                    });
                }
            }
        }.execute();
    }

    // ===================== UI Helpers =====================

    private JPanel createCard(String title, String content, Color bg, Color border) {
        JPanel card = new JPanel(new BorderLayout(8, 4));
        card.setBackground(bg);
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
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
        card.add(textArea, BorderLayout.CENTER);

        return card;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
