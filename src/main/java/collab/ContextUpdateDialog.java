package collab;

// ============================================================
// ContextUpdateDialog.java — Per-field input dialog for context
// refresh. Shows one input box per selected field with current
// value as reference.
//
// WHAT THIS CLASS DOES (one sentence):
// Presents a dynamic dialog with one labeled text area per
// selected context field, collecting per-field update notes.
// ============================================================

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ContextUpdateDialog extends JDialog {

    private final Map<String, JTextArea> inputAreas = new LinkedHashMap<>();
    private boolean cancelled = true;

    /**
     * @param owner        parent frame
     * @param fieldNames   the context field names to show input boxes for
     * @param orgContext   used to look up current values and labels
     */
    public ContextUpdateDialog(Frame owner, List<String> fieldNames, OrganizationContext orgContext) {
        super(owner, "Context Update", true);
        setLayout(new BorderLayout(8, 8));

        int fieldCount = fieldNames.size();
        setSize(600, Math.min(200 + fieldCount * 140, 800));
        setLocationRelativeTo(owner);

        // Header
        JLabel header = new JLabel("Provide updates for each field (leave blank to skip):");
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        add(header, BorderLayout.NORTH);

        // Scrollable form with one card per field
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));

        for (String fieldName : fieldNames) {
            ContextEntry<String> entry = orgContext.getEntry(fieldName);
            String label = OrganizationContext.getFieldLabel(fieldName);
            String currentValue = (entry != null && entry.getValue() != null) ? entry.getValue() : "";
            Freshness freshness = orgContext.getFieldFreshness(fieldName);

            JPanel card = buildFieldCard(fieldName, label, currentValue, freshness);
            form.add(card);
            form.add(Box.createVerticalStrut(8));
        }

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        JButton submitBtn = new JButton("Submit Updates");
        submitBtn.addActionListener(e -> {
            cancelled = false;
            dispose();
        });
        buttonPanel.add(cancelBtn);
        buttonPanel.add(submitBtn);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel buildFieldCard(String fieldName, String label, String currentValue, Freshness freshness) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(Color.WHITE);
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 230), 1),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        // Prevent card from stretching infinitely in BoxLayout
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        // Header: field label + freshness badge
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        headerPanel.setOpaque(false);

        JLabel nameLabel = new JLabel(label);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        headerPanel.add(nameLabel);

        JLabel badge = new JLabel(freshness.name());
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 9f));
        badge.setForeground(freshnessColor(freshness));
        badge.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(freshnessColor(freshness), 1),
            BorderFactory.createEmptyBorder(1, 4, 1, 4)
        ));
        headerPanel.add(badge);

        card.add(headerPanel, BorderLayout.NORTH);

        // Center: current value (read-only) + input area
        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        centerPanel.setOpaque(false);

        // Current value display
        String displayValue = currentValue.isBlank() ? "(empty)" : currentValue;
        if (displayValue.length() > 200) displayValue = displayValue.substring(0, 200) + "...";
        JTextArea currentArea = new JTextArea(displayValue);
        currentArea.setEditable(false);
        currentArea.setLineWrap(true);
        currentArea.setWrapStyleWord(true);
        currentArea.setRows(2);
        currentArea.setFont(currentArea.getFont().deriveFont(11f));
        currentArea.setBackground(new Color(248, 248, 252));
        currentArea.setForeground(new Color(100, 100, 110));
        currentArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(230, 230, 240)),
                "Current Value",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                currentArea.getFont().deriveFont(Font.ITALIC, 10f),
                new Color(140, 140, 150)
            ),
            BorderFactory.createEmptyBorder(2, 4, 2, 4)
        ));
        centerPanel.add(currentArea);

        // Input area for update
        JTextArea inputArea = new JTextArea();
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setRows(2);
        inputArea.setFont(inputArea.getFont().deriveFont(12f));
        inputArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(33, 150, 243)),
                "What changed?",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                inputArea.getFont().deriveFont(Font.ITALIC, 10f),
                new Color(33, 150, 243)
            ),
            BorderFactory.createEmptyBorder(2, 4, 2, 4)
        ));
        inputAreas.put(fieldName, inputArea);
        centerPanel.add(inputArea);

        card.add(centerPanel, BorderLayout.CENTER);
        return card;
    }

    public boolean wasCancelled() {
        return cancelled;
    }

    /**
     * Returns per-field user input. Only includes fields where the user typed something.
     * Key: field name, Value: user's update note for that field.
     */
    public Map<String, String> getPerFieldInput() {
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : inputAreas.entrySet()) {
            String text = entry.getValue().getText().trim();
            if (!text.isEmpty()) {
                result.put(entry.getKey(), text);
            }
        }
        return result;
    }

    private static Color freshnessColor(Freshness freshness) {
        return switch (freshness) {
            case FRESH -> new Color(76, 175, 80);
            case AGING -> new Color(255, 193, 7);
            case STALE -> new Color(255, 152, 0);
            case NEEDS_CONFIRMATION -> new Color(244, 67, 54);
        };
    }
}
