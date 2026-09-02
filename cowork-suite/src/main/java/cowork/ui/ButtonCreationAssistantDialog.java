package cowork.ui;

// AI-assisted button creation: a mini chat with Claude on the left and a
// live preview of the SuiteButton being defined on the right. The model
// emits a BUTTON_DEFINITION block that is parsed into the preview; the user
// can Accept it directly or open it in ButtonCreatorDialog for tweaks.

import cowork.buttons.CategoryColorMap;
import cowork.buttons.SuiteButton;
import cowork.config.ClientFactory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ButtonCreationAssistantDialog extends JDialog {

    private final ClientFactory clients;
    private final CategoryColorMap colorMap;

    private JTextArea chatHistory;
    private JTextField chatInput;
    private JButton sendBtn;
    private JButton acceptBtn;
    private JButton editBtn;

    private JLabel previewLabel;
    private JLabel previewCategory;
    private JLabel previewDescription;
    private JTextArea previewTemplate;
    private JTextArea previewQuestions;
    private JTextArea previewStyle;

    private SuiteButton suggestedButton;
    private SuiteButton result;
    private final List<String> conversationHistory = new ArrayList<>();

    private static final String SYSTEM_PROMPT = """
            You are a button creation assistant for Cowork Suite, a button-driven AI desk for organisational leaders.
            The user wants to create a new task button for their Executive Suite.

            Each button is a TASK TEMPLATE with these fields:
            - label: Short button label (e.g., "Thank You Note")
            - category: Grouping category (e.g., "Leadership", "Meetings", "Communications", "Events", "Finance")
            - description: Brief description of what the button does
            - promptTemplate: The base instruction sent to the AI when this button is clicked
            - followUpQuestions: List of questions asked to the user before running (to gather specifics)
            - styleInstructions: Tone, style, and formatting guidance for the AI output

            Help the user define their button. Ask clarifying questions if needed.
            When you have enough info, output the button definition in this exact format:

            BUTTON_DEFINITION:
            label: [value]
            category: [value]
            description: [value]
            promptTemplate: [value]
            followUpQuestions:
            - [question 1]
            - [question 2]
            styleInstructions: [value]
            END_DEFINITION

            Keep responses concise and helpful.
            """;

    public ButtonCreationAssistantDialog(Frame owner, ClientFactory clients, CategoryColorMap colorMap) {
        super(owner, "Button Creation Assistant", true);
        this.clients = clients;
        this.colorMap = colorMap;
        setSize(800, 550);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        add(buildChatPanel(), BorderLayout.CENTER);
        add(buildPreviewPanel(), BorderLayout.EAST);
        add(buildBottomBar(), BorderLayout.SOUTH);

        appendChat("Assistant", "Hi! I'll help you create a new task button. "
                + "Tell me what kind of task you'd like this button to perform. "
                + "For example: \"I want a button that helps draft meeting agendas\" "
                + "or \"Create a button for writing sponsor thank-you notes\".");
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
        panel.add(new JScrollPane(chatHistory), BorderLayout.CENTER);

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
        previewDescription = new JLabel("Description: —");
        previewDescription.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewTemplate = previewArea(3, "Prompt Template");
        previewQuestions = previewArea(3, "Follow-Up Questions");
        previewStyle = previewArea(2, "Style");

        panel.add(Box.createVerticalStrut(4));
        panel.add(previewLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(previewCategory);
        panel.add(previewDescription);
        panel.add(Box.createVerticalStrut(8));
        panel.add(previewTemplate);
        panel.add(Box.createVerticalStrut(4));
        panel.add(previewQuestions);
        panel.add(Box.createVerticalStrut(4));
        panel.add(previewStyle);
        return panel;
    }

    private static JTextArea previewArea(int rows, String title) {
        JTextArea area = new JTextArea(rows, 20);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createTitledBorder(title));
        return area;
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
                return callAssistant();
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

    private String callAssistant() {
        try {
            StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT).append("\n\nConversation so far:\n");
            for (String msg : conversationHistory) {
                prompt.append(msg).append("\n");
            }
            prompt.append("\nRespond to the user's latest message.");
            return clients.claude().sendMessage(prompt.toString());
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

    private void parseButtonDefinition(String response) {
        int start = response.indexOf("BUTTON_DEFINITION:");
        int end = response.indexOf("END_DEFINITION");
        if (start < 0 || end < 0 || end <= start) return;

        String block = response.substring(start + "BUTTON_DEFINITION:".length(), end).trim();
        suggestedButton = new SuiteButton();
        suggestedButton.setActionType("TASK_TEMPLATE");
        List<String> questions = new ArrayList<>();
        boolean inQuestions = false;

        for (String rawLine : block.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("followUpQuestions:")) {
                inQuestions = true;
                continue;
            }
            if (inQuestions && line.startsWith("- ")) {
                questions.add(line.substring(2).trim());
                continue;
            }
            inQuestions = false;
            if (line.startsWith("label:")) {
                suggestedButton.setLabel(valueOf(line, "label:"));
            } else if (line.startsWith("category:")) {
                suggestedButton.setCategory(valueOf(line, "category:"));
            } else if (line.startsWith("description:")) {
                suggestedButton.setDescription(valueOf(line, "description:"));
            } else if (line.startsWith("promptTemplate:")) {
                suggestedButton.setPromptTemplate(valueOf(line, "promptTemplate:"));
            } else if (line.startsWith("styleInstructions:")) {
                suggestedButton.setStyleInstructions(valueOf(line, "styleInstructions:"));
            }
        }
        suggestedButton.setFollowUpQuestions(questions);

        updatePreview();
        acceptBtn.setEnabled(true);
        editBtn.setEnabled(true);
    }

    private static String valueOf(String line, String key) {
        return line.substring(key.length()).trim();
    }

    private void updatePreview() {
        if (suggestedButton == null) return;
        previewLabel.setText(suggestedButton.getLabel() != null ? suggestedButton.getLabel() : "(unnamed)");
        previewCategory.setText("Category: " + orDash(suggestedButton.getCategory()));
        previewDescription.setText("Description: " + orDash(suggestedButton.getDescription()));
        previewTemplate.setText(suggestedButton.getPromptTemplate() != null ? suggestedButton.getPromptTemplate() : "");
        previewQuestions.setText(String.join("\n", suggestedButton.getFollowUpQuestions()));
        previewStyle.setText(suggestedButton.getStyleInstructions() != null ? suggestedButton.getStyleInstructions() : "");
    }

    private static String orDash(String s) {
        return s != null ? s : "—";
    }

    private void onAccept() {
        if (suggestedButton != null) {
            result = suggestedButton;
            dispose();
        }
    }

    private void onApplyAndEdit() {
        if (suggestedButton == null) return;
        dispose();
        ButtonCreatorDialog editor = new ButtonCreatorDialog((Frame) getOwner(), colorMap, suggestedButton);
        editor.setVisible(true);
        result = editor.getResult();
    }

    public SuiteButton getResult() {
        return result;
    }
}
