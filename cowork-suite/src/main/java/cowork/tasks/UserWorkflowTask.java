package cowork.tasks;

import cowork.context.ContextEntry;
import cowork.context.OrganizationContext;
import cowork.data.WorkflowDefinition;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AgenticTask wrapper for a user-defined WorkflowDefinition: asks the workflow's input
 * questions, resolves the prompt template against org context and answers, appends the
 * selected context layers, and makes one Claude call.
 */
public class UserWorkflowTask implements AgenticTask {

    private final WorkflowDefinition workflow;

    public UserWorkflowTask(WorkflowDefinition workflow) {
        this.workflow = workflow;
    }

    @Override public String getId()          { return "workflow-" + workflow.getId(); }
    @Override public String getName()        { return workflow.getName(); }
    @Override public String getDescription() { return workflow.getDescription(); }
    @Override public String getCategory()    { return workflow.getCategory(); }
    @Override public boolean isAvailable()   { return workflow.isEnabled(); }

    public WorkflowDefinition getWorkflowDefinition() { return workflow; }

    @Override
    public void execute(AgenticTaskContext ctx) {
        Map<String, String> inputAnswers = new LinkedHashMap<>();
        List<String> questions = workflow.getInputQuestions();

        if (!questions.isEmpty()) {
            WorkflowInputDialog dialog = new WorkflowInputDialog(TaskDialogs.owner(), workflow.getName(), questions);
            dialog.setVisible(true);
            if (dialog.wasCancelled()) return;
            inputAnswers = dialog.getAnswers();
        }

        ctx.output().setStatus("Running: " + workflow.getName() + "...");

        final Map<String, String> answers = inputAnswers;

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return ctx.clients().claude().sendMessage(buildPrompt(ctx, answers));
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    ctx.output().showOutput(workflow.getName(), response);
                    ctx.output().setStatus(workflow.getName() + " complete.");
                } catch (Exception e) {
                    ctx.output().showOutput("Error", "Failed: " + e.getMessage());
                    ctx.output().setStatus("Error: " + e.getMessage());
                }
            }
        }.execute();
    }

    private String buildPrompt(AgenticTaskContext ctx, Map<String, String> inputAnswers) {
        StringBuilder prompt = new StringBuilder();

        String formatInstruction = switch (workflow.getOutputFormat()) {
            case REPORT -> "Produce a detailed, structured report with sections and headers.";
            case BRIEF -> "Produce a concise briefing, no more than 1 page.";
            case CHECKLIST -> "Produce an actionable checklist with clear items.";
            case EMAIL_DRAFT -> "Produce a ready-to-send email draft with subject line and body.";
        };
        prompt.append(formatInstruction).append("\n\n");

        prompt.append(workflow.resolvePrompt(ctx.orgContext(), inputAnswers)).append("\n\n");

        List<String> layers = workflow.getRequiredContextLayers();
        if (!layers.isEmpty()) {
            prompt.append("=== ORGANIZATION CONTEXT ===\n");
            for (String fieldName : layers) {
                ContextEntry<String> entry = ctx.orgContext().getEntry(fieldName);
                if (entry != null && entry.getValue() != null && !entry.getValue().isBlank()) {
                    prompt.append(OrganizationContext.getFieldLabel(fieldName)).append(": ");
                    prompt.append(entry.getValue()).append("\n");
                }
            }
        }

        return prompt.toString();
    }

    static class WorkflowInputDialog extends JDialog {
        private boolean cancelled = true;
        private final List<JTextArea> answerFields = new ArrayList<>();

        WorkflowInputDialog(Frame owner, String workflowName, List<String> questions) {
            super(owner, workflowName + " — Input", true);
            setSize(500, 300 + (questions.size() * 60));
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(8, 8));

            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createEmptyBorder(10, 14, 6, 14));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1;

            for (int i = 0; i < questions.size(); i++) {
                gbc.gridx = 0; gbc.gridy = i * 2;
                JLabel label = new JLabel(questions.get(i));
                label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
                form.add(label, gbc);

                gbc.gridy = i * 2 + 1;
                JTextArea area = new JTextArea(2, 30);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                answerFields.add(area);
                form.add(new JScrollPane(area), gbc);
            }

            add(new JScrollPane(form), BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.addActionListener(e -> dispose());
            JButton goBtn = new JButton("Run");
            goBtn.addActionListener(e -> { cancelled = false; dispose(); });
            buttons.add(cancelBtn);
            buttons.add(goBtn);
            add(buttons, BorderLayout.SOUTH);
        }

        boolean wasCancelled() { return cancelled; }

        /** Answers keyed by question index as a string, matching the {input:N} template placeholders. */
        Map<String, String> getAnswers() {
            Map<String, String> answers = new LinkedHashMap<>();
            for (int i = 0; i < answerFields.size(); i++) {
                answers.put(String.valueOf(i), answerFields.get(i).getText().trim());
            }
            return answers;
        }
    }
}
