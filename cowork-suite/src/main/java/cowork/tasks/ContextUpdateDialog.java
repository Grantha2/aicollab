package cowork.tasks;

import cowork.context.ContextEntry;
import cowork.context.Freshness;
import cowork.context.OrganizationContext;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-field input dialog for the context refresh: one card per selected field showing the
 * current value, its freshness badge and a "what changed?" box. Lives in cowork.tasks (its
 * only caller is ContextRefreshTask) so tasks do not depend on cowork.ui.
 */
public class ContextUpdateDialog extends JDialog {

    private final Map<String, JTextArea> inputAreas = new LinkedHashMap<>();
    private boolean cancelled = true;

    public ContextUpdateDialog(Frame owner, List<String> fieldNames, OrganizationContext orgContext) {
        super(owner, "Context Update", true);
        setLayout(new BorderLayout(8, 8));

        int fieldCount = fieldNames.size();
        setSize(600, Math.min(200 + fieldCount * 140, 800));
        setLocationRelativeTo(owner);

        JLabel header = new JLabel("Provide updates for each field (leave blank to skip):");
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        add(header, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));

        for (String fieldName : fieldNames) {
            ContextEntry<String> entry = orgContext.getEntry(fieldName);
            String label = OrganizationContext.getFieldLabel(fieldName);
            String currentValue = (entry != null && entry.getValue() != null) ? entry.getValue() : "";
            Freshness freshness = orgContext.getFieldFreshness(fieldName);

            form.add(buildFieldCard(fieldName, label, currentValue, freshness));
            form.add(Box.createVerticalStrut(8));
        }

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

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
        // Stops BoxLayout stretching the card to fill leftover height.
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

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

        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        centerPanel.setOpaque(false);

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
                TitledBorder.LEFT,
                TitledBorder.TOP,
                currentArea.getFont().deriveFont(Font.ITALIC, 10f),
                new Color(140, 140, 150)
            ),
            BorderFactory.createEmptyBorder(2, 4, 2, 4)
        ));
        centerPanel.add(currentArea);

        JTextArea inputArea = new JTextArea();
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setRows(2);
        inputArea.setFont(inputArea.getFont().deriveFont(12f));
        inputArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(33, 150, 243)),
                "What changed?",
                TitledBorder.LEFT,
                TitledBorder.TOP,
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

    /** Field name to note, only for fields where the user typed something. */
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
