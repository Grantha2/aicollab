package cowork.ui;

// Tabbed create/edit form for a SuiteButton: basic properties (label,
// category, icon, action) and the task template (prompt, follow-up
// questions, style). Editing preserves the button's id and sort order.

import cowork.buttons.CategoryColorMap;
import cowork.buttons.SuiteButton;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ButtonCreatorDialog extends JDialog {

    private static final String[] ACTION_TYPES = {
        "TASK_TEMPLATE", "CUSTOM_PROMPT", "OPEN_CONTEXT_MENU", "EDIT_CONFIG", "SPAWN_BUTTON"
    };

    private JTextField labelField;
    private JComboBox<String> categoryCombo;
    private JTextField iconField;
    private JComboBox<String> actionCombo;
    private JTextArea descriptionArea;
    private JTextField paramField;

    private JTextArea promptTemplateArea;
    private DefaultListModel<String> questionsModel;
    private JList<String> questionsList;
    private JTextArea styleArea;

    private SuiteButton result;
    private final SuiteButton editingExisting;

    public ButtonCreatorDialog(Frame owner, CategoryColorMap colorMap) {
        this(owner, colorMap, null);
    }

    public ButtonCreatorDialog(Frame owner, CategoryColorMap colorMap, SuiteButton existing) {
        super(owner, existing == null ? "Create New Task Button" : "Edit Task Button", true);
        this.editingExisting = existing;
        setLayout(new BorderLayout(8, 8));
        setSize(550, 620);
        setLocationRelativeTo(owner);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Basic", buildBasicTab(colorMap));
        tabs.addTab("Task Definition", buildTaskTab());
        add(tabs, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> onSave());
        buttonPanel.add(cancelBtn);
        buttonPanel.add(saveBtn);
        add(buttonPanel, BorderLayout.SOUTH);

        if (existing != null) {
            populateFromExisting(existing);
        }
    }

    private JPanel buildBasicTab(CategoryColorMap colorMap) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        labelField = new JTextField(20);
        addRow(panel, gbc, 0, "Label:", labelField);

        List<String> categories = colorMap.getAllCategories();
        categories.add("New...");
        categoryCombo = new JComboBox<>(categories.toArray(new String[0]));
        categoryCombo.addActionListener(e -> onCategoryChanged(colorMap));
        addRow(panel, gbc, 1, "Category:", categoryCombo);

        JPanel iconPanel = new JPanel(new BorderLayout(4, 0));
        iconField = new JTextField(15);
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> onBrowseIcon());
        iconPanel.add(iconField, BorderLayout.CENTER);
        iconPanel.add(browseBtn, BorderLayout.EAST);
        addRow(panel, gbc, 2, "Icon:", iconPanel);

        actionCombo = new JComboBox<>(ACTION_TYPES);
        addRow(panel, gbc, 3, "Action:", actionCombo);

        paramField = new JTextField(20);
        paramField.setToolTipText("For CUSTOM_PROMPT: the prompt text sent when the button is clicked.");
        addRow(panel, gbc, 4, "Parameter:", paramField);

        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0;
        panel.add(new JLabel("Description:"), gbc);
        descriptionArea = new JTextArea(3, 20);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        gbc.gridx = 1; gbc.weightx = 1; gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(descriptionArea), gbc);

        return panel;
    }

    private static void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private JPanel buildTaskTab() {
        JPanel taskPanel = new JPanel(new BorderLayout(8, 8));
        taskPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel templatePanel = new JPanel(new BorderLayout(4, 4));
        templatePanel.setBorder(BorderFactory.createTitledBorder("Prompt Template"));
        promptTemplateArea = new JTextArea(5, 30);
        promptTemplateArea.setLineWrap(true);
        promptTemplateArea.setWrapStyleWord(true);
        promptTemplateArea.setToolTipText(
                "The base instruction sent to the AI. Use {user_input} as placeholder for the user's text.");
        templatePanel.add(new JScrollPane(promptTemplateArea), BorderLayout.CENTER);
        JLabel templateHint = new JLabel(
                "<html><i>Use {user_input} for the user's text. E.g.: \"Write a thank you note for: {user_input}\"</i></html>");
        templateHint.setFont(templateHint.getFont().deriveFont(10f));
        templatePanel.add(templateHint, BorderLayout.SOUTH);

        JPanel questionsPanel = new JPanel(new BorderLayout(4, 4));
        questionsPanel.setBorder(BorderFactory.createTitledBorder("Follow-Up Questions"));
        questionsModel = new DefaultListModel<>();
        questionsList = new JList<>(questionsModel);
        questionsList.setVisibleRowCount(4);
        questionsPanel.add(new JScrollPane(questionsList), BorderLayout.CENTER);

        JPanel qButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton addQ = new JButton("Add");
        addQ.addActionListener(e -> onAddQuestion());
        JButton editQ = new JButton("Edit");
        editQ.addActionListener(e -> onEditQuestion());
        JButton removeQ = new JButton("Remove");
        removeQ.addActionListener(e -> onRemoveQuestion());
        JButton moveUp = new JButton("Up");
        moveUp.addActionListener(e -> onMoveQuestion(-1));
        JButton moveDown = new JButton("Down");
        moveDown.addActionListener(e -> onMoveQuestion(1));
        qButtons.add(addQ);
        qButtons.add(editQ);
        qButtons.add(removeQ);
        qButtons.add(moveUp);
        qButtons.add(moveDown);
        questionsPanel.add(qButtons, BorderLayout.SOUTH);
        JLabel qHint = new JLabel(
                "<html><i>Questions asked to the user before running the task (e.g., \"Who are you thanking?\")</i></html>");
        qHint.setFont(qHint.getFont().deriveFont(10f));
        questionsPanel.add(qHint, BorderLayout.NORTH);

        JPanel stylePanel = new JPanel(new BorderLayout(4, 4));
        stylePanel.setBorder(BorderFactory.createTitledBorder("Style & Tone Instructions"));
        styleArea = new JTextArea(3, 30);
        styleArea.setLineWrap(true);
        styleArea.setWrapStyleWord(true);
        styleArea.setToolTipText("Describe the desired style, tone, and formatting for AI output.");
        stylePanel.add(new JScrollPane(styleArea), BorderLayout.CENTER);

        JSplitPane topSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, templatePanel, questionsPanel);
        topSplit.setResizeWeight(0.4);
        topSplit.setBorder(null);
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, stylePanel);
        mainSplit.setResizeWeight(0.7);
        mainSplit.setBorder(null);
        taskPanel.add(mainSplit, BorderLayout.CENTER);
        return taskPanel;
    }

    private void onAddQuestion() {
        String q = JOptionPane.showInputDialog(this, "Enter follow-up question:", "Add Question",
                JOptionPane.PLAIN_MESSAGE);
        if (q != null && !q.trim().isEmpty()) {
            questionsModel.addElement(q.trim());
        }
    }

    private void onEditQuestion() {
        int idx = questionsList.getSelectedIndex();
        if (idx < 0) return;
        String edited = (String) JOptionPane.showInputDialog(this, "Edit question:", "Edit Question",
                JOptionPane.PLAIN_MESSAGE, null, null, questionsModel.get(idx));
        if (edited != null && !edited.trim().isEmpty()) {
            questionsModel.set(idx, edited.trim());
        }
    }

    private void onRemoveQuestion() {
        int idx = questionsList.getSelectedIndex();
        if (idx >= 0) {
            questionsModel.remove(idx);
        }
    }

    private void onMoveQuestion(int direction) {
        int idx = questionsList.getSelectedIndex();
        int newIdx = idx + direction;
        if (idx < 0 || newIdx < 0 || newIdx >= questionsModel.size()) return;
        String item = questionsModel.remove(idx);
        questionsModel.add(newIdx, item);
        questionsList.setSelectedIndex(newIdx);
    }

    private void onCategoryChanged(CategoryColorMap colorMap) {
        if (!"New...".equals(categoryCombo.getSelectedItem())) return;
        String newCat = JOptionPane.showInputDialog(this, "Enter new category name:", "New Category",
                JOptionPane.PLAIN_MESSAGE);
        if (newCat != null && !newCat.trim().isEmpty()) {
            newCat = newCat.trim();
            colorMap.colorForCategory(newCat);
            categoryCombo.insertItemAt(newCat, categoryCombo.getItemCount() - 1);
            categoryCombo.setSelectedItem(newCat);
        } else {
            categoryCombo.setSelectedIndex(0);
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

    private void populateFromExisting(SuiteButton existing) {
        labelField.setText(existing.getLabel());
        categoryCombo.setSelectedItem(existing.getCategory());
        iconField.setText(existing.getIconPath() != null ? existing.getIconPath() : "");
        actionCombo.setSelectedItem(existing.getActionType());
        descriptionArea.setText(existing.getDescription() != null ? existing.getDescription() : "");
        if (existing.getParam("value") != null) {
            paramField.setText(existing.getParam("value"));
        }
        if (existing.getPromptTemplate() != null) {
            promptTemplateArea.setText(existing.getPromptTemplate());
        }
        if (existing.getFollowUpQuestions() != null) {
            for (String q : existing.getFollowUpQuestions()) {
                questionsModel.addElement(q);
            }
        }
        if (existing.getStyleInstructions() != null) {
            styleArea.setText(existing.getStyleInstructions());
        }
    }

    private void onSave() {
        String label = labelField.getText().trim();
        if (label.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Label is required.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        result = new SuiteButton(label, (String) categoryCombo.getSelectedItem(), (String) actionCombo.getSelectedItem());
        if (editingExisting != null) {
            result.setId(editingExisting.getId());
            result.setSortOrder(editingExisting.getSortOrder());
        }
        result.setIconPath(iconField.getText().trim());
        result.setDescription(descriptionArea.getText().trim());

        String param = paramField.getText().trim();
        if (!param.isEmpty()) {
            result.putParam("value", param);
        }
        String template = promptTemplateArea.getText().trim();
        if (!template.isEmpty()) {
            result.setPromptTemplate(template);
        }
        List<String> questions = new ArrayList<>();
        for (int i = 0; i < questionsModel.size(); i++) {
            questions.add(questionsModel.get(i));
        }
        result.setFollowUpQuestions(questions);
        String style = styleArea.getText().trim();
        if (!style.isEmpty()) {
            result.setStyleInstructions(style);
        }
        dispose();
    }

    public SuiteButton getResult() {
        return result;
    }
}
