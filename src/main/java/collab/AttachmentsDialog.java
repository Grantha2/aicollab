package collab;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// ============================================================
// AttachmentsDialog.java — Minimal UI for managing debate-level
// file attachments.
//
// WHAT THIS DIALOG DOES (one sentence):
// Lets the user add/remove local files that every panelist will
// read on Phase 1 of the next debate.
//
// WHY THIS IS SMALL:
// Phase A scope is intentionally "drop a PDF, see it referenced in
// every panelist's payload." No preview, no per-panelist pinning,
// no size stats. Later phases (RAG, MCP, per-slot attachments) will
// grow a richer dialog — Phase D in the plan.
//
// WHAT THIS DIALOG WRITES:
// Nothing directly. The caller reads getAttachments() on close and
// decides whether to persist it on the active ProfileSet. The
// dialog is deliberately a pure view over its own state so it's
// reusable from multiple entry points (MainGui toolbar today,
// ProfileSetEditorDialog later).
// ============================================================
public class AttachmentsDialog extends JDialog {

    private final DefaultListModel<FileAttachment> listModel = new DefaultListModel<>();
    private final JList<FileAttachment> list = new JList<>(listModel);
    private boolean confirmed = false;

    public AttachmentsDialog(Frame owner, List<FileAttachment> seed) {
        super(owner, "Attach Files", true);
        setLayout(new BorderLayout(8, 8));

        if (seed != null) {
            for (FileAttachment f : seed) {
                if (f != null) listModel.addElement(f);
            }
        }

        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value,
                                                          int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                String display = "(empty)";
                if (value instanceof FileAttachment fa) {
                    String name = fa.getDisplayName() == null ? fa.getLocalPath() : fa.getDisplayName();
                    String mime = fa.getMimeType() == null ? "?" : fa.getMimeType();
                    display = name + "    [" + mime + "]";
                }
                return super.getListCellRendererComponent(l, display, index, isSelected, cellHasFocus);
            }
        });

        // List of currently-attached files. Scrollpane so big lists
        // don't push the buttons off-screen on small laptops.
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(480, 220));

        // Instructional sentence above the list — plain-English,
        // matches the tone of the slot-editor hint.
        JLabel hint = new JLabel(
                "<html>Files below will be uploaded to each provider's "
                        + "Files API on the next debate and referenced in every "
                        + "panelist's Phase 1 prompt.</html>");
        hint.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        JPanel listWrapper = new JPanel(new BorderLayout(4, 4));
        listWrapper.add(hint, BorderLayout.NORTH);
        listWrapper.add(scroll, BorderLayout.CENTER);

        // Add / remove controls on the right so they visually
        // "attach" to the list they affect.
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        JButton addBtn = new JButton("Add Files\u2026");
        JButton removeBtn = new JButton("Remove Selected");
        addBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        removeBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(addBtn);
        side.add(Box.createVerticalStrut(6));
        side.add(removeBtn);
        side.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        addBtn.addActionListener(e -> onAdd());
        removeBtn.addActionListener(e -> onRemove());

        // OK / Cancel at the bottom — standard right-aligned.
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("Save");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> { confirmed = true; dispose(); });
        cancel.addActionListener(e -> { confirmed = false; dispose(); });
        bottom.add(ok);
        bottom.add(cancel);

        add(listWrapper, BorderLayout.CENTER);
        add(side, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private void onAdd() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Select files to attach");
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        for (File f : chooser.getSelectedFiles()) {
            if (f == null || !f.isFile()) continue;
            Path p = f.toPath();
            // Skip obvious duplicates (same absolute path already in list).
            boolean duplicate = false;
            String abs = p.toAbsolutePath().toString();
            for (int i = 0; i < listModel.size(); i++) {
                if (abs.equals(listModel.get(i).getLocalPath())) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;
            listModel.addElement(FileAttachment.fromPath(p));
        }
    }

    private void onRemove() {
        int[] selected = list.getSelectedIndices();
        // Remove back-to-front so earlier indices stay valid.
        for (int i = selected.length - 1; i >= 0; i--) {
            listModel.remove(selected[i]);
        }
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public List<FileAttachment> getAttachments() {
        List<FileAttachment> out = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            out.add(listModel.get(i));
        }
        return out;
    }
}
