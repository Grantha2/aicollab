package cowork.ui;

// Scrollable stack of result cards for the Executive Suite view. Each card
// shows a title, timestamp, the wrapped response text and a Copy button.
// Newest results go on top so the latest answer is never below the fold.

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ResultPanel extends JPanel {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color CARD_BG = new Color(255, 250, 240);
    private static final Color CARD_BORDER = new Color(230, 200, 150);

    private final JPanel cards = new JPanel();

    public ResultPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 8));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));
        JLabel title = new JLabel("Results");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        topBar.add(title, BorderLayout.WEST);
        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> clear());
        topBar.add(clearBtn, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        // Wrapper pins the card stack to the top instead of centring it vertically.
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(cards, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(wrapper);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    public void addResult(String title, String body) {
        cards.add(createCard(title, body == null ? "" : body), 0);
        cards.add(Box.createVerticalStrut(8), 1);
        cards.revalidate();
        cards.repaint();
    }

    public void clear() {
        cards.removeAll();
        cards.revalidate();
        cards.repaint();
    }

    private JPanel createCard(String title, String body) {
        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBackground(CARD_BG);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER, 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        JLabel titleLabel = new JLabel(title + "  ·  " + LocalTime.now().format(TIME));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        header.add(titleLabel, BorderLayout.CENTER);
        JButton copyBtn = new JButton("Copy");
        copyBtn.setToolTipText("Copy this result to the clipboard");
        copyBtn.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(body), null));
        header.add(copyBtn, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JTextArea text = new JTextArea(body);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setBorder(null);
        card.add(text, BorderLayout.CENTER);
        return card;
    }
}
