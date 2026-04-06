package collab;

// ============================================================
// ButtonCreationAssistantDialog.java — AI-assisted button creation
// with a mini chat interface.
//
// WHAT THIS CLASS DOES (one sentence):
// Presents a split dialog where the left side is a mini chat with
// an AI assistant that helps define a task button, and the right
// side is a live preview of the SuiteButton being constructed.
//
// HOW IT WORKS:
// 1. User describes what kind of task button they want in the chat
// 2. AI responds with a suggested button definition (JSON-ish)
// 3. User can refine via follow-up messages
// 4. When satisfied, user clicks "Apply & Edit" to load the
//    suggestion into a ButtonCreatorDialog for final tweaks,
//    or "Accept" to create the button directly.
// ============================================================

import javax.swing.*;
import java.awt.*;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ButtonCreationAssistantDialog extends JDialog {

    private final Config config;
    private final CategoryColorMap colorMap;

    private JTextArea chatHistory;
    private JTextField chatInput;
    private JButton sendBtn;
    private JButton acceptBtn;
    private JButton editBtn;

    // Live preview fields
    private JLabel previewLabel;
    private JLabel previewCategory;
    private JLabel previewAction;
    private JLabel previewMode;
    private JTextArea previewTemplate;
    private JTextArea previewQuestions;
    private JTextArea previewStyle;

    private SuiteButton suggestedButton;
    private SuiteButton result;
    private final List<String> conversationHistory = new ArrayList<>();

    private static final String SYSTEM_PROMPT = """
            You are a button creation assistant for an AI Collaboration Platform.
            The user wants to create a new task button for their Executive Suite.

            Each button is a TASK TEMPLATE with these fields:
            - label: Short button label (e.g., "Thank You Note")
            - category: Grouping category (e.g., "Communication", "Creative", "Analysis", "System")
            - description: Brief description of what the button does
            - promptTemplate: The base instruction sent to the AI when this button is clicked
            - followUpQuestions: List of questions asked to the user before running (to gather specifics)
            - styleInstructions: Tone, style, and formatting guidance for the AI output
            - simpleMode: true = single API call, false = full multi-model debate

            Help the user define their button. Ask clarifying questions if needed.
            When you have enough info, output the button definition in this exact format:

            BUTTON_DEFINITION:
            label: [value]
            category: [value]
            description: [value]
            simpleMode: [true/false]
            promptTemplate: [value]
            followUpQuestions:
            - [question 1]
            - [question 2]
            styleInstructions: [value]
            END_DEFINITION

            Keep responses concise and helpful.
            """;

    public ButtonCreationAssistantDialog(Frame owner, Config config, CategoryColorMap colorMap) {
        super(owner, "Button Creation Assistant", true);
        this.config = config;
        this.colorMap = colorMap;
        setSize(800, 550);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        add(buildChatPanel(), BorderLayout.CENTER);
        add(buildPreviewPanel(), BorderLayout.EAST);
        add(buildBottomBar(), BorderLayout.SOUTH);

        // Initial greeting
        appendChat("Assistant", "Hi! I'll help you create a new task button. " +
                "Tell me what kind of task you'd like this button to perform. " +
                "For example: \"I want a button that helps draft meeting agendas\" " +
                "or \"Create a button for writing product descriptions\".");
    }

    private JPanel buildChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Chat with Assistant"));

        chatHistory = new JTextArea();
        chatHistory.setEditable(false);
        chatHistory.setLineWrap(true);
        chatHistory.setWrapStyleWord(true);
        chatHistory.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chatHistory.setMargin(new Insets(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(chatHistory);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        chatInput = new JTextField();
        chatInput.addActionListener(e -> onSendMessage());
        sendBtn = new JButton("Send");
        sendBtn.addActionListener(e -> onSendMessage());
        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        panel.add(inputPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildPreviewPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Button Preview"));
        panel.setPreferredSize(new Dimension(280, 0));

        previewLabel = new JLabel("(not yet defined)");
        previewLabel.setFont(previewLabel.getFont().deriveFont(Font.BOLD, 14f));
        previewLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewCategory = new JLabel("Category: —");
        previewCategory.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewAction = new JLabel("Description: —");
        previewAction.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewMode = new JLabel("Mode: —");
        previewMode.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewTemplate = new JTextArea(3, 20);
        previewTemplate.setEditable(false);
        previewTemplate.setLineWrap(true);
        previewTemplate.setWrapStyleWord(true);
        previewTemplate.setBorder(BorderFactory.createTitledBorder("Prompt Template"));

        previewQuestions = new JTextArea(3, 20);
        previewQuestions.setEditable(false);
        previewQuestions.setLineWrap(true);
        previewQuestions.setWrapStyleWord(true);
        previewQuestions.setBorder(BorderFactory.createTitledBorder("Follow-Up Questions"));

        previewStyle = new JTextArea(2, 20);
        previewStyle.setEditable(false);
        previewStyle.setLineWrap(true);
        previewStyle.setWrapStyleWord(true);
        previewStyle.setBorder(BorderFactory.createTitledBorder("Style"));

        panel.add(Box.createVerticalStrut(4));
        panel.add(previewLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(previewCategory);
        panel.add(previewCategory);
        panel.add(previewAction);
        panel.add(previewMode);
        panel.add(Box.createVerticalStrut(8));
        panel.add(previewTemplate);
        panel.add(Box.createVerticalStrut(4));
        panel.add(previewQuestions);
        panel.add(Box.createVerticalStrut(4));
        panel.add(previewStyle);

        return panel;
    }

    private JPanel buildBottomBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        editBtn = new JButton("Apply & Edit...");
        editBtn.setToolTipText("Load AI suggestion into the full editor for manual tweaks");
        editBtn.setEnabled(false);
        editBtn.addActionListener(e -> onApplyAndEdit());

        acceptBtn = new JButton("Accept");
        acceptBtn.setToolTipText("Create the button as suggested by the assistant");
        acceptBtn.setEnabled(false);
        acceptBtn.addActionListener(e -> onAccept());

        panel.add(cancelBtn);
        panel.add(editBtn);
        panel.add(acceptBtn);
        return panel;
    }

    private void onSendMessage() {
        String userMsg = chatInput.getText().trim();
        if (userMsg.isEmpty()) return;

        chatInput.setText("");
        chatInput.setEnabled(false);
        sendBtn.setEnabled(false);
        appendChat("You", userMsg);
        conversationHistory.add("User: " + userMsg);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return callAssistant(userMsg);
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    conversationHistory.add("Assistant: " + response);
                    appendChat("Assistant", response);
                    parseButtonDefinition(response);
                } catch (Exception e) {
                    appendChat("System", "Error: " + e.getMessage());
                }
                chatInput.setEnabled(true);
                sendBtn.setEnabled(true);
                chatInput.requestFocus();
            }
        }.execute();
    }

    private String callAssistant(String userMessage) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            int maxTokens = config.getMaxResponseTokens();
            LlmClient client = new AnthropicClient(httpClient, config.getClaudeUrl(),
                    config.getClaudeKey(), config.getClaudeModel(), maxTokens);

            StringBuilder prompt = new StringBuilder();
            prompt.append(SYSTEM_PROMPT).append("\n\n");
            prompt.append("Conversation so far:\n");
            for (String msg : conversationHistory) {
                prompt.append(msg).append("\n");
            }
            prompt.append("\nRespond to the user's latest message.");

            return client.sendMessage(prompt.toString());
        } catch (Exception e) {
            return "[ERROR] Failed to contact AI: " + e.getMessage();
        }
    }

    private void appendChat(String speaker, String message) {
        if (!chatHistory.getText().isEmpty()) {
            chatHistory.append("\n\n");
        }
        chatHistory.append(speaker + ":\n" + message);
        chatHistory.setCaretPosition(chatHistory.getDocument().getLength());
    }

    /**
     * Parses a BUTTON_DEFINITION block from the AI response and updates the preview.
     */
    private void parseButtonDefinition(String response) {
        int start = response.indexOf("BUTTON_DEFINITION:");
        int end = response.indexOf("END_DEFINITION");
        if (start < 0 || end < 0 || end <= start) return;

        String block = response.substring(start + "BUTTON_DEFINITION:".length(), end).trim();
        String[] lines = block.split("\n");

        suggestedButton = new SuiteButton();
        suggestedButton.setActionType("TASK_TEMPLATE");
        List<String> questions = new ArrayList<>();
        boolean inQuestions = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.startsWith("label:")) {
                suggestedButton.setLabel(line.substring("label:".length()).trim());
                inQuestions = false;
            } else if (line.startsWith("category:")) {
                suggestedButton.setCategory(line.substring("category:".length()).trim());
                inQuestions = false;
            } else if (line.startsWith("description:")) {
                suggestedButton.setDescription(line.substring("description:".length()).trim());
                inQuestions = false;
            } else if (line.startsWith("simpleMode:")) {
                suggestedButton.setSimpleMode(
                        "true".equalsIgnoreCase(line.substring("simpleMode:".length()).trim()));
                inQuestions = false;
            } else if (line.startsWith("promptTemplate:")) {
                suggestedButton.setPromptTemplate(line.substring("promptTemplate:".length()).trim());
                inQuestions = false;
            } else if (line.startsWith("followUpQuestions:")) {
                inQuestions = true;
            } else if (line.startsWith("styleInstructions:")) {
                suggestedButton.setStyleInstructions(line.substring("styleInstructions:".length()).trim());
                inQuestions = false;
            } else if (inQuestions && line.startsWith("- ")) {
                questions.add(line.substring(2).trim());
            }
        }
        suggestedButton.setFollowUpQuestions(questions);

        updatePreview();
        acceptBtn.setEnabled(true);
        editBtn.setEnabled(true);
    }

    private void updatePreview() {
        if (suggestedButton == null) return;
        previewLabel.setText(suggestedButton.getLabel() != null ? suggestedButton.getLabel() : "(unnamed)");
        previewCategory.setText("Category: " +
                (suggestedButton.getCategory() != null ? suggestedButton.getCategory() : "—"));
        previewAction.setText("Description: " +
                (suggestedButton.getDescription() != null ? suggestedButton.getDescription() : "—"));
        previewMode.setText("Mode: " + (suggestedButton.isSimpleMode() ? "Simple" : "Debate"));
        previewTemplate.setText(suggestedButton.getPromptTemplate() != null
                ? suggestedButton.getPromptTemplate() : "");
        previewQuestions.setText(suggestedButton.getFollowUpQuestions() != null
                ? String.join("\n", suggestedButton.getFollowUpQuestions()) : "");
        previewStyle.setText(suggestedButton.getStyleInstructions() != null
                ? suggestedButton.getStyleInstructions() : "");
    }

    private void onAccept() {
        if (suggestedButton != null) {
            result = suggestedButton;
            dispose();
        }
    }

    private void onApplyAndEdit() {
        if (suggestedButton == null) return;
        // Open the full editor pre-populated with the AI suggestion
        dispose();
        Frame owner = (Frame) getOwner();
        ButtonCreatorDialog editor = new ButtonCreatorDialog(owner, colorMap, suggestedButton);
        editor.setVisible(true);
        result = editor.getResult();
    }

    public SuiteButton getResult() {
        return result;
    }
}
