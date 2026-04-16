package collab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// ============================================================
// ConversationHistorySidebar.java — Dialog for browsing and
// resuming past debate sessions.
//
// WHAT THIS CLASS DOES (one sentence):
// Lists every SessionStore JSONL file under sessions/ in reverse
// chronological order, shows a preview of each, and offers an
// action to load the file's turns + syntheses into the active
// ConversationContext (and switch the SessionStore to that file).
//
// WHY A DIALOG INSTEAD OF A PERSISTENT SIDEBAR:
// MainGui's layout is complex and the experimental branch is not
// the place to restructure it. A modal dialog gives the user the
// "sidebar of past conversations" functionality without touching
// the debate view's layout code. A later, non-experimental PR can
// promote this into a docked JSplitPane if the feature proves its
// weight.
//
// WHAT "RESUME" MEANS:
// Same mechanism as Main.java's --cli resume path (lines 337-349
// at time of writing): replay the file's ConversationTurns into
// the active ConversationContext so prompts from the next cycle
// include the prior history as usual, and attach the SessionStore
// itself to the active Maestro so new turns append to the same
// file.
// ============================================================
public final class ConversationHistorySidebar extends JDialog {

    private static final DateTimeFormatter HUMAN_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final JList<SessionRow> list;
    private final JTextArea preview;
    private final ResumeCallback onResume;

    // Kept as a separate row type so the JList renderer stays simple.
    private record SessionRow(Path file, String label) {
        @Override public String toString() { return label; }
    }

    /**
     * Callback fired when the user clicks "Resume". Receives the
     * selected file's parsed turns and syntheses plus the Path for
     * attaching a new SessionStore.
     */
    @FunctionalInterface
    public interface ResumeCallback {
        void onResume(Path file, List<ConversationTurn> turns, List<String> syntheses);
    }

    public ConversationHistorySidebar(Frame owner, ResumeCallback onResume) {
        super(owner, "Past Conversations", true);
        this.onResume = onResume;

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        list = new JList<>(loadModel());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(12);
        list.addListSelectionListener(e -> updatePreview());

        preview = new JTextArea();
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(list), new JScrollPane(preview));
        split.setResizeWeight(0.35);
        split.setDividerLocation(260);
        add(split, BorderLayout.CENTER);

        JButton resumeBtn = new JButton("Resume selected");
        resumeBtn.addActionListener(e -> resumeSelected());
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> setVisible(false));
        JButton refreshBtn = new JButton("Refresh list");
        refreshBtn.addActionListener(e -> list.setModel(loadModel()));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(refreshBtn);
        actions.add(resumeBtn);
        actions.add(closeBtn);
        add(actions, BorderLayout.SOUTH);

        setSize(820, 520);
        setLocationRelativeTo(owner);
    }

    // ============================================================
    // loadModel() — Scan sessions/ for JSONL files, newest first.
    //
    // SessionStore.listSessionFiles already sorts reverse-chrono for
    // us. We convert each Path into a label like "2026-04-16 14:02 —
    // session-2026-04-16....jsonl" so the user can pick by date.
    // ============================================================
    private DefaultListModel<SessionRow> loadModel() {
        DefaultListModel<SessionRow> model = new DefaultListModel<>();
        List<Path> files = new ArrayList<>(
                SessionStore.listSessionFiles(SessionStore.defaultSessionsDir()));
        files.sort(Comparator.reverseOrder());
        for (Path f : files) {
            model.addElement(new SessionRow(f, describe(f)));
        }
        return model;
    }

    private String describe(Path file) {
        try {
            Instant modified = Files.getLastModifiedTime(file).toInstant();
            return HUMAN_FORMAT.format(modified) + "  —  " + file.getFileName();
        } catch (Exception e) {
            return file.getFileName().toString();
        }
    }

    // ============================================================
    // updatePreview() — Shows the most recent synthesis (if any) and
    // the turn count for the selected session.
    //
    // Full turn content would balloon the preview; the last synthesis
    // is the shortest meaningful signal of "what this conversation
    // was about."
    // ============================================================
    private void updatePreview() {
        SessionRow row = list.getSelectedValue();
        if (row == null) { preview.setText(""); return; }
        try {
            SessionStore store = new SessionStore(row.file());
            List<ConversationTurn> turns = store.loadTurns(row.file());
            List<String> syntheses = store.loadSyntheses(row.file());
            StringBuilder sb = new StringBuilder();
            sb.append("File: ").append(row.file().getFileName()).append("\n");
            sb.append("Turns: ").append(turns.size()).append("\n");
            sb.append("Syntheses: ").append(syntheses.size()).append("\n\n");
            if (!syntheses.isEmpty()) {
                sb.append("=== LATEST SYNTHESIS ===\n");
                sb.append(syntheses.get(syntheses.size() - 1));
            } else if (!turns.isEmpty()) {
                ConversationTurn last = turns.get(turns.size() - 1);
                sb.append("=== LAST TURN (").append(last.phase()).append(" · ")
                        .append(last.model()).append(") ===\n");
                sb.append(last.content());
            } else {
                sb.append("(no content yet)");
            }
            preview.setText(sb.toString());
            preview.setCaretPosition(0);
        } catch (Exception e) {
            preview.setText("Could not read session: " + e.getMessage());
        }
    }

    private void resumeSelected() {
        SessionRow row = list.getSelectedValue();
        if (row == null) return;
        try {
            SessionStore store = new SessionStore(row.file());
            List<ConversationTurn> turns = store.loadTurns(row.file());
            List<String> syntheses = store.loadSyntheses(row.file());
            onResume.onResume(row.file(), turns, syntheses);
            setVisible(false);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Could not resume session: " + e.getMessage(),
                    "Resume failed", JOptionPane.ERROR_MESSAGE);
        }
    }
}
