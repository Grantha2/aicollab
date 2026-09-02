package cowork.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Task-specific context for one button press: task name, prompt template, follow-up
 * questions with the user's answers, style instructions, and the simple/multi toggle.
 * buildTaskBlock() renders it as the "=== TASK CONTEXT ===" section of the prompt.
 */
public class TaskContext {

    private String taskName;
    private String promptTemplate;
    private List<String> followUpQuestions = new ArrayList<>();
    private Map<String, String> followUpAnswers = new LinkedHashMap<>();
    private String styleInstructions;
    private boolean simpleMode;

    public String getTaskName()                     { return taskName; }
    public String getPromptTemplate()               { return promptTemplate; }
    public List<String> getFollowUpQuestions()      { return followUpQuestions; }
    public Map<String, String> getFollowUpAnswers() { return followUpAnswers; }
    public String getStyleInstructions()            { return styleInstructions; }
    public boolean isSimpleMode()                   { return simpleMode; }

    public void setTaskName(String name)            { this.taskName = name; }
    public void setPromptTemplate(String template)  { this.promptTemplate = template; }
    public void setStyleInstructions(String style)  { this.styleInstructions = style; }
    public void setSimpleMode(boolean simple)       { this.simpleMode = simple; }

    public void setFollowUpQuestions(List<String> questions) {
        this.followUpQuestions = questions != null ? questions : new ArrayList<>();
    }

    public void setFollowUpAnswers(Map<String, String> answers) {
        this.followUpAnswers = answers != null ? answers : new LinkedHashMap<>();
    }

    public void answerQuestion(String question, String answer) {
        followUpAnswers.put(question, answer);
    }

    public String buildTaskBlock() {
        StringBuilder sb = new StringBuilder("=== TASK CONTEXT ===\n");
        sb.append("Task: ").append(taskName != null ? taskName : "Custom Task").append("\n\n");
        if (promptTemplate != null && !promptTemplate.isBlank()) {
            sb.append("Instructions:\n").append(promptTemplate).append("\n\n");
        }
        if (styleInstructions != null && !styleInstructions.isBlank()) {
            sb.append("Style & Tone:\n").append(styleInstructions).append("\n\n");
        }
        if (!followUpAnswers.isEmpty()) {
            sb.append("Specifications:\n");
            followUpAnswers.forEach((q, a) -> sb.append("- ").append(q).append(": ").append(a).append("\n"));
            sb.append("\n");
        }
        return sb.toString();
    }
}
