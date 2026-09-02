package cowork.ui;

// Two-step first-launch wizard: (1) Anthropic API key and optional model,
// (2) seed three organisation-context fields. Finishing writes the config
// file (created if absent) and org_context.json; cancelling writes nothing.

import cowork.config.Config;
import cowork.context.OrganizationContext;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

public class FirstLaunchSetupDialog extends JDialog {

    private static final int TOTAL_STEPS = 2;

    private boolean completed = false;
    private int currentStep = 0;

    private final JPasswordField apiKeyField = new JPasswordField(36);
    private final JTextField modelField = new JTextField(36);
    private final JTextArea prioritiesArea = new JTextArea(4, 36);
    private final JTextField termField = new JTextField(36);
    private final JTextField toneField = new JTextField(36);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel stepsPanel = new JPanel(cardLayout);
    private final JButton backBtn = new JButton("Back");
    private final JButton nextBtn = new JButton("Next");

    public FirstLaunchSetupDialog(Frame owner) {
        super(owner, "Cowork Suite — First Launch Setup", true);
        setSize(620, 420);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JLabel header = new JLabel("Welcome to Cowork Suite");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 4, 16));
        add(header, BorderLayout.NORTH);

        stepsPanel.add(buildKeyStep(), "step0");
        stepsPanel.add(buildContextStep(), "step1");
        add(stepsPanel, BorderLayout.CENTER);

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        backBtn.addActionListener(e -> navigate(-1));
        nextBtn.addActionListener(e -> navigate(1));
        backBtn.setEnabled(false);
        navPanel.add(cancelBtn);
        navPanel.add(backBtn);
        navPanel.add(nextBtn);
        add(navPanel, BorderLayout.SOUTH);
    }

    private JPanel buildKeyStep() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        panel.add(new JLabel("<html><b>Step 1/2: Anthropic API key</b><br>"
                + "Every button and routine makes one Claude call, so the suite needs a key. "
                + "Get one at console.anthropic.com/settings/keys. It is stored locally and never shown again.</html>"),
                BorderLayout.NORTH);

        JPanel form = newForm();
        GridBagConstraints gbc = newConstraints();
        addRow(form, gbc, 0, "API key:", apiKeyField);
        modelField.setToolTipText("Leave blank to use the suite's default Claude model.");
        addRow(form, gbc, 1, "Model (optional):", modelField);
        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildContextStep() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        panel.add(new JLabel("<html><b>Step 2/2: Organisation context</b><br>"
                + "These three fields ground every AI output. All are optional and editable later "
                + "under Context &gt; Context Control.</html>"), BorderLayout.NORTH);

        JPanel form = newForm();
        GridBagConstraints gbc = newConstraints();
        prioritiesArea.setLineWrap(true);
        prioritiesArea.setWrapStyleWord(true);
        addRow(form, gbc, 0, "Top Priorities:", new JScrollPane(prioritiesArea));
        termField.setToolTipText("e.g. Fall 2026 (Aug 24 - Dec 18)");
        addRow(form, gbc, 1, "Current Term / Date Range:", termField);
        toneField.setToolTipText("e.g. Executive, concise, warm but direct");
        addRow(form, gbc, 2, "Preferred Tone:", toneField);
        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel newForm() {
        return new JPanel(new GridBagLayout());
    }

    private static GridBagConstraints newConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return gbc;
    }

    private static void addRow(JPanel form, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(field, gbc);
    }

    private void navigate(int delta) {
        if (delta > 0 && currentStep == 0 && apiKeyField.getPassword().length == 0) {
            JOptionPane.showMessageDialog(this, "Please enter your Anthropic API key to continue.",
                    "Missing API Key", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (delta > 0 && currentStep == TOTAL_STEPS - 1) {
            finish();
            return;
        }
        currentStep = Math.max(0, Math.min(TOTAL_STEPS - 1, currentStep + delta));
        cardLayout.show(stepsPanel, "step" + currentStep);
        backBtn.setEnabled(currentStep > 0);
        nextBtn.setText(currentStep == TOTAL_STEPS - 1 ? "Finish" : "Next");
    }

    private void finish() {
        try {
            saveConfig();
            saveOrgContext();
            completed = true;
            dispose();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save configuration: " + e.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveConfig() throws IOException {
        Path path = Config.defaultPath();
        if (!Files.exists(path)) {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                new Properties().store(w, "Cowork Suite configuration — contains secrets, never commit.");
            }
        }
        Config config = Config.load(path);
        char[] key = apiKeyField.getPassword();
        config.setProperty("claude.key", new String(key).trim());
        Arrays.fill(key, '\0');
        String model = modelField.getText().trim();
        if (!model.isEmpty()) {
            config.setProperty("claude.model", model);
        }
        config.save();
    }

    private void saveOrgContext() {
        OrganizationContext org = OrganizationContext.load();
        String priorities = prioritiesArea.getText().trim();
        String term = termField.getText().trim();
        String tone = toneField.getText().trim();
        if (!priorities.isEmpty()) org.setTopPriorities(priorities);
        if (!term.isEmpty()) org.setCurrentTermDateRange(term);
        if (!tone.isEmpty()) org.setPreferredToneStyle(tone);
        org.save();
    }

    public boolean wasCompleted() { return completed; }

    public boolean wasCancelled() { return !completed; }
}
