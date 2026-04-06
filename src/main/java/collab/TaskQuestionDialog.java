package collab;

// ============================================================
// TaskQuestionDialog.java — Collects follow-up question answers
// from the user before executing a task template.
//
// WHAT THIS CLASS DOES (one sentence):
// Shows a modal dialog with one text field per follow-up question,
// returning the user's answers as a question→answer map.
// ============================================================

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskQuestionDialog extends JDialog {

    private final Map<String, JTextField> answerFields = new LinkedHashMap<>();
    private boolean cancelled = true;

    public TaskQuestionDialog(Frame owner, String taskName, List<String> questions) {
        super(owner, "Task: " + taskName, true);
        setLayout(new BorderLayout(8, 8));
        setSize(480, 120 + questions.size() * 50);
        setLocationRelativeTo(owner);

        // Header
        JLabel header = new JLabel("Please answer these questions to proceed:");
        header.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        add(header, BorderLayout.NORTH);

        // Questions form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        for (int i = 0; i < questions.size(); i++) {
            String question = questions.get(i);

            gbc.gridx = 0; gbc.gridy = i; gbc.weightx = 0;
            JLabel label = new JLabel(question);
            form.add(label, gbc);

            gbc.gridx = 1; gbc.weightx = 1;
            JTextField field = new JTextField(25);
            form.add(field, gbc);
            answerFields.put(question, field);
        }

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        JButton proceedBtn = new JButton("Proceed");
        proceedBtn.addActionListener(e -> {
            cancelled = false;
            dispose();
        });
        buttonPanel.add(cancelBtn);
        buttonPanel.add(proceedBtn);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    public boolean wasCancelled() {
        return cancelled;
    }

    public Map<String, String> getAnswers() {
        Map<String, String> answers = new LinkedHashMap<>();
        for (var entry : answerFields.entrySet()) {
            String answer = entry.getValue().getText().trim();
            if (!answer.isEmpty()) {
                answers.put(entry.getKey(), answer);
            }
        }
        return answers;
    }
}
