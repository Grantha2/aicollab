package collab;

// ============================================================
// ButtonPanel.java — Left-side panel rendering grouped buttons.
//
// WHAT THIS CLASS DOES (one sentence):
// Displays SuiteButton objects grouped by category, with colored
// headers, icon+label rendering, and a "+" button at the bottom.
// ============================================================

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ButtonPanel extends JPanel {

    private final CategoryColorMap colorMap;
    private final IconLoader iconLoader;
    private final List<SuiteButton> buttons;
    private final JPanel buttonContainer;
    private ButtonClickListener clickListener;

    public interface ButtonClickListener {
        void onButtonClicked(SuiteButton button);
        void onCreateButton();
        void onEditButton(SuiteButton button);
        void onDeleteButton(SuiteButton button);
    }

    public ButtonPanel(CategoryColorMap colorMap, IconLoader iconLoader, List<SuiteButton> buttons) {
        this.colorMap = colorMap;
        this.iconLoader = iconLoader;
        this.buttons = buttons;

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(200, 0));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Title
        JLabel title = new JLabel("Executive Suite");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));
        add(title, BorderLayout.NORTH);

        // Scrollable button area
        buttonContainer = new JPanel();
        buttonContainer.setLayout(new BoxLayout(buttonContainer, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(buttonContainer);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        // "+" button at bottom
        JButton addButton = new JButton("+ New Button");
        addButton.setToolTipText("Create a new action button");
        addButton.addActionListener(e -> {
            if (clickListener != null) clickListener.onCreateButton();
        });
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        bottomPanel.add(addButton, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        rebuildButtons();
    }

    public void setClickListener(ButtonClickListener listener) {
        this.clickListener = listener;
    }

    public void rebuildButtons() {
        buttonContainer.removeAll();

        // Group by category
        Map<String, List<SuiteButton>> groups = new LinkedHashMap<>();
        for (SuiteButton btn : buttons) {
            groups.computeIfAbsent(btn.getCategory(), k -> new ArrayList<>()).add(btn);
        }

        // Sort within groups by sortOrder
        for (List<SuiteButton> group : groups.values()) {
            group.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
        }

        // Render each group
        for (Map.Entry<String, List<SuiteButton>> entry : groups.entrySet()) {
            String category = entry.getKey();
            Color catColor = colorMap.colorForCategory(category);

            // Category header
            JPanel header = new JPanel(new BorderLayout());
            header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            header.setBackground(catColor);
            header.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            JLabel headerLabel = new JLabel(category);
            headerLabel.setForeground(textColorFor(catColor));
            headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 11f));
            header.add(headerLabel, BorderLayout.CENTER);
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            buttonContainer.add(header);

            // Buttons in this group
            for (SuiteButton btn : entry.getValue()) {
                JPanel card = createButtonCard(btn, catColor);
                buttonContainer.add(card);
            }

            buttonContainer.add(Box.createVerticalStrut(6));
        }

        buttonContainer.revalidate();
        buttonContainer.repaint();
    }

    private JPanel createButtonCard(SuiteButton btn, Color catColor) {
        JPanel card = new JPanel(new BorderLayout(6, 0));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBackground(new Color(245, 245, 245));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, catColor),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Icon
        ImageIcon icon = iconLoader.loadIcon(btn.getIconPath());
        if (icon == null) {
            icon = IconLoader.createFallbackIcon(btn.getLabel(), catColor, 24);
        }
        JLabel iconLabel = new JLabel(icon);
        card.add(iconLabel, BorderLayout.WEST);

        // Label
        JLabel label = new JLabel(btn.getLabel());
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        card.add(label, BorderLayout.CENTER);

        // Tooltip
        if (btn.getDescription() != null && !btn.getDescription().isEmpty()) {
            card.setToolTipText(btn.getDescription());
        }

        // Click handler
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e, btn);
                } else if (clickListener != null) {
                    clickListener.onButtonClicked(btn);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBackground(new Color(230, 230, 230));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(new Color(245, 245, 245));
            }
        });

        return card;
    }

    private void showContextMenu(MouseEvent e, SuiteButton btn) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem edit = new JMenuItem("Edit");
        edit.addActionListener(ev -> {
            if (clickListener != null) clickListener.onEditButton(btn);
        });
        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(ev -> {
            if (clickListener != null) clickListener.onDeleteButton(btn);
        });
        menu.add(edit);
        menu.add(delete);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private Color textColorFor(Color bg) {
        int luminance = (int) (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue());
        return luminance < 140 ? Color.WHITE : Color.BLACK;
    }
}
