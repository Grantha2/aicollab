package collab;

// ============================================================
// ButtonPanel.java — Multi-column button board for Executive Suite.
//
// WHAT THIS CLASS DOES (one sentence):
// Displays SuiteButton objects grouped by category in a flowing
// multi-column grid, with colored headers, icon+label rendering,
// and a "+" button for creating new buttons.
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
    private final JPanel gridContainer;
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
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // Top bar: title + "New Button"
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));
        JLabel title = new JLabel("Executive Suite");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        topBar.add(title, BorderLayout.WEST);

        JButton addButton = new JButton("+ New Button");
        addButton.setToolTipText("Create a new task button");
        addButton.addActionListener(e -> {
            if (clickListener != null) clickListener.onCreateButton();
        });
        topBar.add(addButton, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // Scrollable grid area using WrapLayout for flowing columns
        gridContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JScrollPane scroll = new JScrollPane(gridContainer);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // Make FlowLayout wrap when the viewport resizes
        scroll.getViewport().addChangeListener(e -> {
            int viewWidth = scroll.getViewport().getWidth();
            if (viewWidth > 0) {
                gridContainer.setPreferredSize(null); // reset to let layout compute
                gridContainer.setPreferredSize(
                        new Dimension(viewWidth, gridContainer.getPreferredSize().height));
                gridContainer.revalidate();
            }
        });

        add(scroll, BorderLayout.CENTER);
        rebuildButtons();
    }

    public void setClickListener(ButtonClickListener listener) {
        this.clickListener = listener;
    }

    public void rebuildButtons() {
        gridContainer.removeAll();

        // Group by category
        Map<String, List<SuiteButton>> groups = new LinkedHashMap<>();
        for (SuiteButton btn : buttons) {
            groups.computeIfAbsent(btn.getCategory(), k -> new ArrayList<>()).add(btn);
        }

        // Sort within groups by sortOrder
        for (List<SuiteButton> group : groups.values()) {
            group.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
        }

        // Render each category as a fixed-width column card
        for (Map.Entry<String, List<SuiteButton>> entry : groups.entrySet()) {
            String category = entry.getKey();
            Color catColor = colorMap.colorForCategory(category);
            JPanel column = buildCategoryColumn(category, catColor, entry.getValue());
            gridContainer.add(column);
        }

        gridContainer.revalidate();
        gridContainer.repaint();
    }

    private JPanel buildCategoryColumn(String category, Color catColor, List<SuiteButton> btns) {
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setPreferredSize(new Dimension(220, 34 + btns.size() * 38));
        column.setBackground(new Color(250, 250, 250));
        column.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                BorderFactory.createEmptyBorder(0, 0, 4, 0)));

        // Category header
        JPanel header = new JPanel(new BorderLayout());
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        header.setBackground(catColor);
        header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JLabel headerLabel = new JLabel(category);
        headerLabel.setForeground(textColorFor(catColor));
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 12f));
        header.add(headerLabel, BorderLayout.CENTER);

        JLabel countLabel = new JLabel(String.valueOf(btns.size()));
        countLabel.setForeground(textColorFor(catColor));
        countLabel.setFont(countLabel.getFont().deriveFont(Font.PLAIN, 10f));
        header.add(countLabel, BorderLayout.EAST);

        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(header);

        // Button cards
        for (SuiteButton btn : btns) {
            JPanel card = createButtonCard(btn, catColor);
            column.add(card);
        }

        return column;
    }

    private JPanel createButtonCard(SuiteButton btn, Color catColor) {
        JPanel card = new JPanel(new BorderLayout(6, 0));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBackground(new Color(250, 250, 250));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, catColor),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Icon
        ImageIcon icon = iconLoader.loadIcon(btn.getIconPath());
        if (icon == null) {
            icon = IconLoader.createFallbackIcon(btn.getLabel(), catColor, 22);
        }
        JLabel iconLabel = new JLabel(icon);
        card.add(iconLabel, BorderLayout.WEST);

        // Label
        JLabel label = new JLabel(btn.getLabel());
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        card.add(label, BorderLayout.CENTER);

        // Mode indicator for task templates
        if (btn.isTaskTemplate()) {
            JLabel mode = new JLabel(btn.isSimpleMode() ? "S" : "W");
            mode.setFont(mode.getFont().deriveFont(Font.BOLD, 9f));
            mode.setForeground(new Color(160, 160, 160));
            mode.setToolTipText(btn.isSimpleMode() ? "Simple mode (single API call)" : "Workflow mode (full debate)");
            card.add(mode, BorderLayout.EAST);
        }

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
                card.setBackground(tint(catColor, 0.9f));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(new Color(250, 250, 250));
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

    /** Tint a color toward white by the given factor (0.0 = white, 1.0 = original). */
    private Color tint(Color c, float factor) {
        return new Color(
                clamp((int) (c.getRed() + (255 - c.getRed()) * (1 - factor))),
                clamp((int) (c.getGreen() + (255 - c.getGreen()) * (1 - factor))),
                clamp((int) (c.getBlue() + (255 - c.getBlue()) * (1 - factor))));
    }

    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
