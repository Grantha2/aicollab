package collab;

// ============================================================
// ButtonCreatorDialog.java — Form to create or edit a SuiteButton.
//
// WHAT THIS CLASS DOES (one sentence):
// Shows a dialog with fields for label, category, icon, action
// type, and description, then returns a configured SuiteButton.
// ============================================================

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ButtonCreatorDialog extends JDialog {

    private static final String[] ACTION_TYPES = {
        "RUN_DEBATE", "SWITCH_PROFILE", "CREATE_PROFILE",
        "EXPORT_SESSION", "OPEN_CONTEXT_MENU", "EDIT_CONFIG",
        "CUSTOM_PROMPT", "SPAWN_BUTTON", "MACRO"
    };

    private JTextField labelField;
    private JComboBox<String> categoryCombo;
    private JTextField iconField;
    private JComboBox<String> actionCombo;
    private JTextArea descriptionArea;
    private JTextField paramField;
    private SuiteButton result;

    public ButtonCreatorDialog(Frame owner, CategoryColorMap colorMap) {
        this(owner, colorMap, null);
    }

    public ButtonCreatorDialog(Frame owner, CategoryColorMap colorMap, SuiteButton existing) {
        super(owner, existing == null ? "Create New Button" : "Edit Button", true);
        setLayout(new BorderLayout(8, 8));
        setSize(450, 420);
        setLocationRelativeTo(owner);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Label
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        form.add(new JLabel("Label:"), gbc);
        labelField = new JTextField(20);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(labelField, gbc);

        // Category
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        form.add(new JLabel("Category:"), gbc);
        List<String> categories = colorMap.getAllCategories();
        categories.add("New...");
        categoryCombo = new JComboBox<>(categories.toArray(new String[0]));
        categoryCombo.addActionListener(e -> onCategoryChanged(colorMap));
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(categoryCombo, gbc);

        // Icon path
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        form.add(new JLabel("Icon:"), gbc);
        JPanel iconPanel = new JPanel(new BorderLayout(4, 0));
        iconField = new JTextField(15);
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> onBrowseIcon());
        iconPanel.add(iconField, BorderLayout.CENTER);
        iconPanel.add(browseBtn, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(iconPanel, gbc);

        // Action type
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        form.add(new JLabel("Action:"), gbc);
        actionCombo = new JComboBox<>(ACTION_TYPES);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(actionCombo, gbc);

        // Action parameter
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        form.add(new JLabel("Parameter:"), gbc);
        paramField = new JTextField(20);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(paramField, gbc);

        // Description
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0;
        form.add(new JLabel("Description:"), gbc);
        descriptionArea = new JTextArea(3, 20);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        gbc.gridx = 1; gbc.weightx = 1; gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        form.add(new JScrollPane(descriptionArea), gbc);

        add(form, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> onSave());
        buttonPanel.add(cancelBtn);
        buttonPanel.add(saveBtn);
        add(buttonPanel, BorderLayout.SOUTH);

        // Pre-fill if editing
        if (existing != null) {
            labelField.setText(existing.getLabel());
            categoryCombo.setSelectedItem(existing.getCategory());
            iconField.setText(existing.getIconPath() != null ? existing.getIconPath() : "");
            actionCombo.setSelectedItem(existing.getActionType());
            descriptionArea.setText(existing.getDescription() != null ? existing.getDescription() : "");
            if (existing.getParam("value") != null) {
                paramField.setText(existing.getParam("value"));
            }
        }
    }

    private void onCategoryChanged(CategoryColorMap colorMap) {
        if ("New...".equals(categoryCombo.getSelectedItem())) {
            String newCat = JOptionPane.showInputDialog(this,
                    "Enter new category name:", "New Category",
                    JOptionPane.PLAIN_MESSAGE);
            if (newCat != null && !newCat.trim().isEmpty()) {
                newCat = newCat.trim();
                colorMap.colorForCategory(newCat); // auto-assigns color
                categoryCombo.insertItemAt(newCat, categoryCombo.getItemCount() - 1);
                categoryCombo.setSelectedItem(newCat);
            } else {
                categoryCombo.setSelectedIndex(0);
            }
        }
    }

    private void onBrowseIcon() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Image files", "png", "jpg", "gif", "svg"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            iconField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onSave() {
        String label = labelField.getText().trim();
        if (label.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Label is required.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        result = new SuiteButton(label,
                (String) categoryCombo.getSelectedItem(),
                (String) actionCombo.getSelectedItem());
        result.setIconPath(iconField.getText().trim());
        result.setDescription(descriptionArea.getText().trim());
        String param = paramField.getText().trim();
        if (!param.isEmpty()) {
            result.putParam("value", param);
        }
        dispose();
    }

    public SuiteButton getResult() {
        return result;
    }
}
