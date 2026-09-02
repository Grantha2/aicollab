package cowork.ui;

// Create/edit form for a user-defined WorkflowDefinition: name, trigger,
// input questions, required org-context layers, prompt template,
// write-back policy and output format.

import cowork.context.OrganizationContext;
import cowork.data.WorkflowDefinition;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class WorkflowEditorDialog extends JDialog {

    private boolean cancelled = true;
    private WorkflowDefinition result;

    private final JTextField nameField = new JTextField(25);
    private final JTextField descriptionField = new JTextField(25);
    private final JTextField categoryField = new JTextField("Custom", 15);
    private final JComboBox<WorkflowDefinition.TriggerType> triggerCombo =
        new JComboBox<>(WorkflowDefinition.TriggerType.values());
    private final JTextField cadenceField = new JTextField(15);
    private final DefaultListModel<String> questionsModel = new DefaultListModel<>();
    private final JList<String> questionsList = new JList<>(questionsModel);
    private final JTextArea promptArea = new JTextArea(8, 30);
    private final Map<String, JCheckBox> contextCheckboxes = new LinkedHashMap<>();
    private final JComboBox<WorkflowDefinition.WriteBackPolicy> writeBackCombo =
        new JComboBox<>(WorkflowDefinition.WriteBackPolicy.values());
    private final JComboBox<WorkflowDefinition.OutputFormat> outputFormatCombo =
        new JComboBox<>(WorkflowDefinition.OutputFormat.values());

    public WorkflowEditorDialog(Frame owner) {
        this(owner, null);
    }

    public WorkflowEditorDialog(Frame owner, WorkflowDefinition existing) {
        super(owner, existing == null ? "New Workflow" : "Edit Workflow", true);
        setSize(600, 650);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 14, 6, 14));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        row = addField(form, gbc, row, "Name:", nameField);
        row = addField(form, gbc, row, "Description:", descriptionField);
        row = addField(form, gbc, row, "Category:", categoryField);
        row = addField(form, gbc, row, "Trigger:", triggerCombo);
        cadenceField.setToolTipText("e.g., 'Daily', 'Weekly on Fridays'");
        row = addField(form, gbc, row, "Cadence:", cadenceField);

        JPanel questionsPanel = new JPanel(new BorderLayout(4, 4));
        questionsPanel.setPreferredSize(new Dimension(0, 80));
        questionsList.setFont(questionsList.getFont().deriveFont(11f));
        questionsPanel.add(new JScrollPane(questionsList), BorderLayout.CENTER);
        JPanel qButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton addQ = new JButton("+");
        addQ.addActionListener(e -> {
            String q = JOptionPane.showInputDialog(this, "Enter question:");
            if (q != null && !q.isBlank()) questionsModel.addElement(q.trim());
        });
        JButton removeQ = new JButton("-");
        removeQ.addActionListener(e -> {
            int idx = questionsList.getSelectedIndex();
            if (idx >= 0) questionsModel.remove(idx);
        });
        qButtons.add(addQ);
        qButtons.add(removeQ);
        questionsPanel.add(qButtons, BorderLayout.SOUTH);
        row = addField(form, gbc, row, "Input Questions:", questionsPanel);

        JPanel contextPanel = new JPanel();
        contextPanel.setLayout(new BoxLayout(contextPanel, BoxLayout.Y_AXIS));
        for (String fieldName : OrganizationContext.getFieldNames()) {
            JCheckBox cb = new JCheckBox(OrganizationContext.getFieldLabel(fieldName));
            cb.setFont(cb.getFont().deriveFont(10f));
            cb.setSelected(true);
            contextCheckboxes.put(fieldName, cb);
            contextPanel.add(cb);
        }
        JScrollPane contextScroll = new JScrollPane(contextPanel);
        contextScroll.setPreferredSize(new Dimension(0, 100));
        row = addField(form, gbc, row, "Context Layers:", contextScroll);

        addLabel(form, gbc, row, "Prompt Template:");
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1;
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setToolTipText("Use {fieldName} for context, {input:0}, {input:1} for question answers");
        form.add(new JScrollPane(promptArea), gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weighty = 0;

        row = addField(form, gbc, row, "Write-back:", writeBackCombo);
        addField(form, gbc, row, "Output Format:", outputFormatCombo);

        add(new JScrollPane(form), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        JButton saveBtn = new JButton("Save Workflow");
        saveBtn.addActionListener(e -> onSave());
        buttons.add(cancelBtn);
        buttons.add(saveBtn);
        add(buttons, BorderLayout.SOUTH);

        if (existing != null) {
            populateFrom(existing);
        }
    }

    private void addLabel(JPanel form, GridBagConstraints gbc, int row, String text) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(text), gbc);
    }

    private int addField(JPanel form, GridBagConstraints gbc, int row, String label, JComponent field) {
        addLabel(form, gbc, row, label);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1;
        form.add(field, gbc);
        return row + 1;
    }

    private void populateFrom(WorkflowDefinition wd) {
        nameField.setText(wd.getName());
        descriptionField.setText(wd.getDescription());
        categoryField.setText(wd.getCategory());
        triggerCombo.setSelectedItem(wd.getTriggerType());
        cadenceField.setText(wd.getCadence());
        questionsModel.clear();
        for (String q : wd.getInputQuestions()) questionsModel.addElement(q);
        promptArea.setText(wd.getPromptTemplate());
        writeBackCombo.setSelectedItem(wd.getWriteBackPolicy());
        outputFormatCombo.setSelectedItem(wd.getOutputFormat());

        Set<String> required = new HashSet<>(wd.getRequiredContextLayers());
        for (var entry : contextCheckboxes.entrySet()) {
            entry.getValue().setSelected(required.contains(entry.getKey()));
        }
    }

    private void onSave() {
        if (nameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (promptArea.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Prompt template is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        result = new WorkflowDefinition();
        result.setName(nameField.getText().trim());
        result.setDescription(descriptionField.getText().trim());
        result.setCategory(categoryField.getText().trim().isEmpty() ? "Custom" : categoryField.getText().trim());
        result.setTriggerType((WorkflowDefinition.TriggerType) triggerCombo.getSelectedItem());
        result.setCadence(cadenceField.getText().trim());
        result.setPromptTemplate(promptArea.getText().trim());
        result.setWriteBackPolicy((WorkflowDefinition.WriteBackPolicy) writeBackCombo.getSelectedItem());
        result.setOutputFormat((WorkflowDefinition.OutputFormat) outputFormatCombo.getSelectedItem());

        List<String> questions = new ArrayList<>();
        for (int i = 0; i < questionsModel.size(); i++) questions.add(questionsModel.get(i));
        result.setInputQuestions(questions);

        List<String> layers = new ArrayList<>();
        for (var entry : contextCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) layers.add(entry.getKey());
        }
        result.setRequiredContextLayers(layers);

        cancelled = false;
        dispose();
    }

    public boolean wasCancelled() { return cancelled; }
    public WorkflowDefinition getResult() { return result; }
}
