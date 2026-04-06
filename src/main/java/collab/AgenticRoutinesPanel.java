package collab;

// ============================================================
// AgenticRoutinesPanel.java — Placeholder panel for future
// agentic routine definition, creation, management, and output.
//
// WHAT THIS CLASS DOES (one sentence):
// Displays a placeholder view where users will eventually define
// and manage automated AI routines/workflows.
// ============================================================

import javax.swing.*;
import java.awt.*;

public class AgenticRoutinesPanel extends JPanel {

    public AgenticRoutinesPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(245, 245, 250));

        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        header.setOpaque(false);
        JLabel titleLabel = new JLabel("Agentic Routines");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        header.add(titleLabel);
        add(header, BorderLayout.NORTH);

        // Center placeholder
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 210), 1),
                BorderFactory.createEmptyBorder(32, 48, 32, 48)));

        JLabel icon = new JLabel("\u2699");
        icon.setFont(icon.getFont().deriveFont(48f));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(icon);
        card.add(Box.createVerticalStrut(16));

        JLabel heading = new JLabel("Routines & Automation");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(heading);
        card.add(Box.createVerticalStrut(8));

        JLabel desc1 = new JLabel("This view will allow you to:");
        desc1.setAlignmentX(Component.CENTER_ALIGNMENT);
        desc1.setForeground(Color.GRAY);
        card.add(desc1);
        card.add(Box.createVerticalStrut(12));

        String[] features = {
            "Define multi-step AI routines and workflows",
            "Schedule recurring automated tasks",
            "Chain task templates into pipelines",
            "Monitor routine execution and output",
            "Manage routine definitions and versions"
        };
        for (String feat : features) {
            JLabel item = new JLabel("\u2022  " + feat);
            item.setAlignmentX(Component.CENTER_ALIGNMENT);
            item.setForeground(new Color(100, 100, 110));
            card.add(item);
            card.add(Box.createVerticalStrut(4));
        }

        card.add(Box.createVerticalStrut(16));
        JLabel coming = new JLabel("Coming soon");
        coming.setFont(coming.getFont().deriveFont(Font.ITALIC, 12f));
        coming.setForeground(new Color(150, 150, 160));
        coming.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(coming);

        center.add(card);
        add(center, BorderLayout.CENTER);
    }
}
