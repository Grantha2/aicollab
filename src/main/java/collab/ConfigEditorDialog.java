package collab;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.util.Properties;

public class ConfigEditorDialog extends JDialog {

    private final Config config;
    private final JPasswordField claudeKeyField = new JPasswordField(28);
    private final JPasswordField openAiKeyField = new JPasswordField(28);
    private final JPasswordField geminiKeyField = new JPasswordField(28);

    private final JTextField claudeModelField = new JTextField(28);
    private final JTextField openAiModelField = new JTextField(28);
    private final JTextField geminiModelField = new JTextField(28);

    private final JSpinner maxTokensSpinner = new JSpinner(new SpinnerNumberModel(8192, 256, 65536, 256));
    private final JSpinner debateRoundsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
    private final JSpinner maxHistorySpinner = new JSpinner(new SpinnerNumberModel(24000, 1000, 200000, 1000));

    public ConfigEditorDialog(Frame owner, Config config) {
        super(owner, "Edit Configuration", true);
        this.config = config;
        setLayout(new BorderLayout(8, 8));
        add(buildFormPanel(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
        loadValues();
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(buildApiPanel());
        panel.add(buildModelPanel());
        panel.add(buildTuningPanel());
        return panel;
    }

    private JPanel buildApiPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 6));
        panel.setBorder(new TitledBorder("API Keys"));
        panel.add(new JLabel("claude.key"));
        panel.add(claudeKeyField);
        panel.add(new JLabel("openai.key"));
        panel.add(openAiKeyField);
        panel.add(new JLabel("gemini.key"));
        panel.add(geminiKeyField);
        return panel;
    }

    private JPanel buildModelPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 6));
        panel.setBorder(new TitledBorder("Model Names"));
        panel.add(new JLabel("claude.model"));
        panel.add(claudeModelField);
        panel.add(new JLabel("openai.model"));
        panel.add(openAiModelField);
        panel.add(new JLabel("gemini.model"));
        panel.add(geminiModelField);
        return panel;
    }

    private JPanel buildTuningPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 6));
        panel.setBorder(new TitledBorder("Tuning"));
        panel.add(new JLabel("max.response.tokens"));
        panel.add(maxTokensSpinner);
        panel.add(new JLabel("debate.rounds"));
        panel.add(debateRoundsSpinner);
        panel.add(new JLabel("max.history.chars"));
        panel.add(maxHistorySpinner);
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
        Properties props = config.getProperties();
        claudeKeyField.setText(props.getProperty("claude.key", ""));
        openAiKeyField.setText(props.getProperty("openai.key", ""));
        geminiKeyField.setText(props.getProperty("gemini.key", ""));

        claudeModelField.setText(props.getProperty("claude.model", "claude-opus-4-6"));
        openAiModelField.setText(props.getProperty("openai.model", "gpt-5.4-mini"));
        geminiModelField.setText(props.getProperty("gemini.model", "gemini-3.1-pro-preview"));

        maxTokensSpinner.setValue(Integer.parseInt(props.getProperty("max.response.tokens", "8192")));
        debateRoundsSpinner.setValue(Integer.parseInt(props.getProperty("debate.rounds", "1")));
        maxHistorySpinner.setValue(Integer.parseInt(props.getProperty("max.history.chars", "24000")));
    }

    private void onSave() {
        config.setProperty("claude.key", new String(claudeKeyField.getPassword()));
        config.setProperty("openai.key", new String(openAiKeyField.getPassword()));
        config.setProperty("gemini.key", new String(geminiKeyField.getPassword()));

        config.setProperty("claude.model", claudeModelField.getText().trim());
        config.setProperty("openai.model", openAiModelField.getText().trim());
        config.setProperty("gemini.model", geminiModelField.getText().trim());

        config.setProperty("max.response.tokens", String.valueOf(maxTokensSpinner.getValue()));
        config.setProperty("debate.rounds", String.valueOf(debateRoundsSpinner.getValue()));
        config.setProperty("max.history.chars", String.valueOf(maxHistorySpinner.getValue()));

        try {
            config.save();
            dispose();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to save config: " + ex.getMessage(),
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
