package collab;

// ============================================================
// AgentProfileEditorDialog.java — Inline modal for creating or
// editing a single AgentProfile.
//
// Opened from ProfileSetEditorDialog when a user picks
// "+ Create new agent profile" in a slot's profile combo.
// On save, writes the profile to AgentProfileLibrary and
// exposes it via getProfile() so the caller can select it
// for the slot.
// ============================================================

import javax.swing.*;
import java.awt.*;

public class AgentProfileEditorDialog extends JDialog {

    private final JTextField nameField = new JTextField(20);
    private final JTextField perspectiveField = new JTextField(20);
    private final JTextArea lensArea = new JTextArea(6, 40);

    private final AgentProfileLibrary library;
    private AgentProfile result;

    public AgentProfileEditorDialog(Window owner, AgentProfileLibrary library, AgentProfile seed) {
        super(owner, seed == null ? "Create Agent Profile" : "Edit Agent Profile",
                ModalityType.APPLICATION_MODAL);
        this.library = library;

        if (seed != null) {
            nameField.setText(seed.getName() == null ? "" : seed.getName());
            perspectiveField.setText(seed.getPerspective() == null ? "" : seed.getPerspective());
            lensArea.setText(seed.getLens() == null ? "" : seed.getLens());
        }

        lensArea.setLineWrap(true);
        lensArea.setWrapStyleWord(true);

        setLayout(new BorderLayout(8, 8));
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(520, 360));
        setLocationRelativeTo(owner);
    }

    private JComponent buildForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gL = new GridBagConstraints();
        gL.gridx = 0;
        gL.anchor = GridBagConstraints.LINE_START;
        gL.insets = new Insets(4, 4, 4, 8);

        GridBagConstraints gI = new GridBagConstraints();
        gI.gridx = 1;
        gI.fill = GridBagConstraints.HORIZONTAL;
        gI.weightx = 1.0;
        gI.insets = new Insets(4, 0, 4, 4);

        gL.gridy = 0; panel.add(new JLabel("Name"), gL);
        gI.gridy = 0; panel.add(nameField, gI);

        gL.gridy = 1; panel.add(new JLabel("Perspective"), gL);
        gI.gridy = 1; panel.add(perspectiveField, gI);

        gL.gridy = 2; gL.anchor = GridBagConstraints.FIRST_LINE_START;
        panel.add(new JLabel("Lens"), gL);
        gI.gridy = 2; gI.fill = GridBagConstraints.BOTH; gI.weighty = 1.0;
        panel.add(new JScrollPane(lensArea), gI);

        return panel;
    }

    private JComponent buildButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton save = new JButton("Save");
        JButton cancel = new JButton("Cancel");

        save.addActionListener(e -> onSave());
        cancel.addActionListener(e -> dispose());

        panel.add(save);
        panel.add(cancel);
        return panel;
    }

    private void onSave() {
        String name = nameField.getText().trim();
        String perspective = perspectiveField.getText().trim();
        String lens = lensArea.getText().trim();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Agent name is required.",
                    "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (perspective.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Perspective is required.",
                    "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (lens.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Lens (detailed description) is required.",
                    "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        AgentProfile ap = new AgentProfile(name, perspective, lens);
        try {
            if (library != null) library.save(ap);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Could not save profile to library: " + e.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        result = ap;
        dispose();
    }

    /** The saved profile, or null if the user cancelled. */
    public AgentProfile getProfile() {
        return result;
    }
}
