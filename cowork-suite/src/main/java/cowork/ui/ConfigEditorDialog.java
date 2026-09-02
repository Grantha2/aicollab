package cowork.ui;

// Edits the handful of config.properties keys the suite uses. The API key
// goes through a JPasswordField and is never echoed anywhere.

import cowork.config.Config;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;

public class ConfigEditorDialog extends JDialog {

    private final Config config;
    private final JPasswordField claudeKeyField = new JPasswordField(28);
    private final JTextField claudeModelField = new JTextField(28);
    private final JSpinner maxTokensSpinner = new JSpinner(new SpinnerNumberModel(8192, 256, 65536, 256));
    private final JTextField sandboxUrlField = new JTextField(28);
    private final JComboBox<String> availabilityModeCombo = new JComboBox<>(new String[]{"fixture", "live"});
    private final JTextField pdfOutputDirField = new JTextField(28);

    public ConfigEditorDialog(Frame owner, Config config) {
        super(owner, "Edit Configuration", true);
        this.config = config;
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(section("Claude",
                "claude.key", claudeKeyField,
                "claude.model", claudeModelField,
                "max.response.tokens", maxTokensSpinner));
        form.add(section("Room Reservation Workflow",
                "computer.use.sandbox.url", sandboxUrlField,
                "room.availability.mode", availabilityModeCombo,
                "pdf.output.dir", pdfOutputDirField));
        add(form, BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        sandboxUrlField.setToolTipText("Base URL of the optional computer-use sandbox; leave blank to disable.");
        availabilityModeCombo.setToolTipText("fixture = bundled sample page; live = real availability lookup.");
        pdfOutputDirField.setToolTipText("Where filled room-request PDFs are written; blank = ~/aicollab-filled.");

        loadValues();
        pack();
        setLocationRelativeTo(owner);
    }

    /** Alternating label/field pairs laid out as a two-column titled grid. */
    private static JPanel section(String title, Object... labelsAndFields) {
        JPanel panel = new JPanel(new GridLayout(labelsAndFields.length / 2, 2, 6, 6));
        panel.setBorder(new TitledBorder(title));
        for (int i = 0; i < labelsAndFields.length; i += 2) {
            panel.add(new JLabel((String) labelsAndFields[i]));
            panel.add((Component) labelsAndFields[i + 1]);
        }
        return panel;
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save");
        JButton cancelBtn = new JButton("Cancel");
        saveBtn.addActionListener(e -> onSave());
        cancelBtn.addActionListener(e -> dispose());
        buttons.add(saveBtn);
        buttons.add(cancelBtn);
        return buttons;
    }

    private void loadValues() {
        claudeKeyField.setText(config.getProperty("claude.key", ""));
        claudeModelField.setText(config.getClaudeModel());
        maxTokensSpinner.setValue(config.getMaxResponseTokens());
        sandboxUrlField.setText(config.getProperty("computer.use.sandbox.url", ""));
        availabilityModeCombo.setSelectedItem(config.getProperty("room.availability.mode", "fixture"));
        pdfOutputDirField.setText(config.getProperty("pdf.output.dir", ""));
    }

    private void onSave() {
        char[] key = claudeKeyField.getPassword();
        config.setProperty("claude.key", new String(key).trim());
        Arrays.fill(key, '\0');
        config.setProperty("claude.model", claudeModelField.getText().trim());
        config.setProperty("max.response.tokens", String.valueOf(maxTokensSpinner.getValue()));
        config.setProperty("computer.use.sandbox.url", sandboxUrlField.getText().trim());
        config.setProperty("room.availability.mode", String.valueOf(availabilityModeCombo.getSelectedItem()));
        config.setProperty("pdf.output.dir", pdfOutputDirField.getText().trim());
        try {
            config.save();
            dispose();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save config: " + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
