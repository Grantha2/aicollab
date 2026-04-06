package collab;

// ============================================================
// TaskContext.java — Task-specific context appended to API calls.
//
// WHAT THIS CLASS DOES (one sentence):
// Holds the task name, prompt template, follow-up question answers,
// style instructions, and simple/multi toggle so that PromptBuilder
// can assemble task-aware prompts for any model.
// ============================================================

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskContext {

    private String taskName;
    private String promptTemplate;
    private List<String> followUpQuestions;
    private Map<String, String> followUpAnswers;
    private String styleInstructions;
    private boolean simpleMode;

    public TaskContext() {
        this.followUpQuestions = new ArrayList<>();
        this.followUpAnswers = new LinkedHashMap<>();
    }

    // Getters
    public String getTaskName()                    { return taskName; }
    public String getPromptTemplate()              { return promptTemplate; }
    public List<String> getFollowUpQuestions()      { return followUpQuestions; }
    public Map<String, String> getFollowUpAnswers() { return followUpAnswers; }
    public String getStyleInstructions()           { return styleInstructions; }
    public boolean isSimpleMode()                  { return simpleMode; }

    // Setters
    public void setTaskName(String name)           { this.taskName = name; }
    public void setPromptTemplate(String template) { this.promptTemplate = template; }
    public void setFollowUpQuestions(List<String> questions) {
        this.followUpQuestions = questions != null ? questions : new ArrayList<>();
    }
    public void setFollowUpAnswers(Map<String, String> answers) {
        this.followUpAnswers = answers != null ? answers : new LinkedHashMap<>();
    }
    public void setStyleInstructions(String style) { this.styleInstructions = style; }
    public void setSimpleMode(boolean simple)      { this.simpleMode = simple; }

    /**
     * Records the user's answer to a follow-up question.
     */
    public void answerQuestion(String question, String answer) {
        followUpAnswers.put(question, answer);
    }

    /**
     * Builds the task context block that gets appended to the prompt.
     * This is the text that tells the AI what task the user wants done,
     * including style, specifications, and gathered follow-up answers.
     */
    public String buildTaskBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TASK CONTEXT ===\n");
        sb.append("Task: ").append(taskName != null ? taskName : "Custom Task").append("\n\n");

        if (promptTemplate != null && !promptTemplate.isBlank()) {
            sb.append("Instructions:\n").append(promptTemplate).append("\n\n");
        }

        if (styleInstructions != null && !styleInstructions.isBlank()) {
            sb.append("Style & Tone:\n").append(styleInstructions).append("\n\n");
        }

        if (!followUpAnswers.isEmpty()) {
            sb.append("Specifications:\n");
            for (Map.Entry<String, String> entry : followUpAnswers.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ")
                  .append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
