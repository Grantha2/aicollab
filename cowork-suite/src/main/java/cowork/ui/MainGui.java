package cowork.ui;

// The suite's main window: menu bar, a CardLayout with the Executive Suite
// (button board + results) and Agentic Routines views, and a status bar.
// launch() installs FlatLaf, runs first-launch setup if there is no API key,
// wires every store/service once, and only then shows the frame.

import com.formdev.flatlaf.FlatLightLaf;
import cowork.buttons.ButtonStore;
import cowork.buttons.CategoryColorMap;
import cowork.buttons.IconLoader;
import cowork.buttons.SuiteButton;
import cowork.config.ClientFactory;
import cowork.config.Config;
import cowork.context.ContextChangeLog;
import cowork.context.ContextController;
import cowork.context.OrganizationContext;
import cowork.context.ReconciliationService;
import cowork.context.TaskContext;
import cowork.data.InitiativeStore;
import cowork.data.OperationalFeedStore;
import cowork.data.RecommendationEngine;
import cowork.data.RelationshipStore;
import cowork.data.WorkflowDefinition;
import cowork.data.WorkflowStore;
import cowork.llm.ApiRequestLog;
import cowork.llm.ChatMessage;
import cowork.llm.LlmRequest;
import cowork.tasks.AgenticTaskRegistry;
import cowork.tasks.ContextRefreshTask;
import cowork.tasks.DailyContextUpdateFunction;
import cowork.tasks.InitiativeReviewTask;
import cowork.tasks.MeetingPrepTask;
import cowork.tasks.OutboundMessagesTask;
import cowork.tasks.StakeholderBriefingTask;
import cowork.tasks.StartYourDayTask;
import cowork.tasks.UserWorkflowTask;
import cowork.tasks.WeeklyReportTask;
import cowork.workflows.RoomReservationWorkflow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainGui extends JFrame implements ButtonPanel.ButtonClickListener {

    private static final String VIEW_SUITE = "Executive Suite";
    private static final String VIEW_AGENTIC = "Agentic Routines";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private Config config;
    private ClientFactory clients;
    private ApiRequestLog apiRequestLog;

    private final CategoryColorMap colorMap = new CategoryColorMap();
    private final ButtonStore buttonStore = new ButtonStore();
    private final IconLoader iconLoader = new IconLoader();
    private final ContextController contextController = new ContextController();
    private final List<SuiteButton> suiteButtons;

    private ButtonPanel buttonPanel;
    private ResultPanel resultPanel;
    private JTextField extraInputField;
    private JLabel statusLabel;
    private CardLayout viewCardLayout;
    private JPanel viewContainer;
    private AgenticRoutinesPanel agenticPanel;

    private MainGui() {
        super("Cowork Suite — " + VIEW_SUITE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        suiteButtons = buttonStore.loadButtons();

        setJMenuBar(buildMenuBar());
        add(buildViewContainer(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        setSize(1500, 900);
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------- Startup

    public static void launch() {
        // FlatLaf paints crisp text on Windows fractional scales where the system LAF fuzzes out.
        FlatLightLaf.setup();
        bumpDefaultFont(2);
        SwingUtilities.invokeLater(() -> {
            MainGui frame = new MainGui();
            if (frame.initApplication()) {
                frame.setVisible(true);
            } else {
                frame.dispose();
            }
        });
    }

    private static void bumpDefaultFont(int deltaPt) {
        Font base = UIManager.getFont("defaultFont");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        }
        UIManager.put("defaultFont", base.deriveFont((float) (base.getSize() + deltaPt)));
    }

    /** Returns false when the frame must not be shown (setup cancelled or startup failed). */
    private boolean initApplication() {
        config = loadConfigOrRunSetup();
        if (config == null) return false;
        try {
            clients = new ClientFactory(config, httpClient);
            apiRequestLog = new ApiRequestLog();

            OrganizationContext orgCtx = contextController.getOrganizationContext();
            ContextChangeLog changeLog = new ContextChangeLog();
            ReconciliationService reconciliation = new ReconciliationService(orgCtx, changeLog);
            InitiativeStore initiativeStore = new InitiativeStore();
            RelationshipStore relationshipStore = new RelationshipStore();
            OperationalFeedStore feedStore = new OperationalFeedStore();
            WorkflowStore workflowStore = new WorkflowStore();

            AgenticTaskRegistry registry = new AgenticTaskRegistry();
            registry.register(new StartYourDayTask(feedStore));
            registry.register(new OutboundMessagesTask(feedStore, relationshipStore));
            registry.register(new ContextRefreshTask(new DailyContextUpdateFunction(orgCtx, reconciliation)));
            registry.register(new MeetingPrepTask(feedStore, relationshipStore));
            registry.register(new InitiativeReviewTask(initiativeStore));
            registry.register(new WeeklyReportTask());
            registry.register(new StakeholderBriefingTask());
            registry.register(new RoomReservationWorkflow());
            for (WorkflowDefinition wd : workflowStore.getAll()) {
                if (wd.isEnabled()) registry.register(new UserWorkflowTask(wd));
            }
            RecommendationEngine recommendationEngine = new RecommendationEngine(orgCtx, feedStore, changeLog);

            agenticPanel = new AgenticRoutinesPanel(orgCtx, reconciliation, changeLog, config, clients,
                    registry, feedStore, workflowStore, recommendationEngine);
            viewContainer.add(agenticPanel, VIEW_AGENTIC);
            statusLabel.setText("Ready.");
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to initialize application: " + e.getMessage(),
                    "Startup Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /** Loads config, running the first-launch wizard when the file or the Claude key is missing. */
    private Config loadConfigOrRunSetup() {
        Config loaded = null;
        try {
            loaded = Config.load(Config.defaultPath());
        } catch (IOException e) {
            // Missing or unreadable config: the wizard below creates it.
        }
        if (loaded != null && loaded.hasClaudeKey()) return loaded;

        FirstLaunchSetupDialog setup = new FirstLaunchSetupDialog(this);
        setup.setVisible(true);
        if (!setup.wasCompleted()) {
            JOptionPane.showMessageDialog(this,
                    "Setup was cancelled. Cowork Suite needs an Anthropic API key to run.",
                    "Setup Cancelled", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        try {
            return Config.load(Config.defaultPath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not read configuration: " + e.getMessage(),
                    "Startup Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    // ---------------------------------------------------------------- Layout

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu settingsMenu = new JMenu("Settings");
        JMenuItem editConfig = new JMenuItem("Edit Configuration...");
        editConfig.addActionListener(e -> onEditConfig());
        settingsMenu.add(editConfig);
        menuBar.add(settingsMenu);

        JMenu contextMenu = new JMenu("Context");
        JMenuItem contextControl = new JMenuItem("Context Control...");
        contextControl.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        contextControl.addActionListener(e -> onOpenContextControl());
        contextMenu.add(contextControl);
        menuBar.add(contextMenu);

        JMenu viewMenu = new JMenu("View");
        ButtonGroup viewGroup = new ButtonGroup();
        JRadioButtonMenuItem suiteView = new JRadioButtonMenuItem(VIEW_SUITE, true);
        suiteView.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
        suiteView.addActionListener(e -> switchView(VIEW_SUITE));
        JRadioButtonMenuItem agenticView = new JRadioButtonMenuItem(VIEW_AGENTIC);
        agenticView.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_2, InputEvent.CTRL_DOWN_MASK));
        agenticView.addActionListener(e -> switchView(VIEW_AGENTIC));
        viewGroup.add(suiteView);
        viewGroup.add(agenticView);
        viewMenu.add(suiteView);
        viewMenu.add(agenticView);
        menuBar.add(viewMenu);
        return menuBar;
    }

    private JComponent buildViewContainer() {
        viewCardLayout = new CardLayout();
        viewContainer = new JPanel(viewCardLayout);

        JPanel suiteView = new JPanel(new BorderLayout(8, 8));
        JPanel inputBar = new JPanel(new BorderLayout(6, 0));
        inputBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 0, 12));
        inputBar.add(new JLabel("Extra input (optional):"), BorderLayout.WEST);
        extraInputField = new JTextField();
        extraInputField.setToolTipText("Free text appended to the next task prompt, e.g. notes or a draft to work from.");
        inputBar.add(extraInputField, BorderLayout.CENTER);
        suiteView.add(inputBar, BorderLayout.NORTH);

        buttonPanel = new ButtonPanel(colorMap, iconLoader, suiteButtons);
        buttonPanel.setClickListener(this);
        suiteView.add(buttonPanel, BorderLayout.CENTER);

        resultPanel = new ResultPanel();
        resultPanel.setPreferredSize(new Dimension(420, 0));
        suiteView.add(resultPanel, BorderLayout.EAST);

        viewContainer.add(suiteView, VIEW_SUITE);
        return viewContainer;
    }

    private JComponent buildStatusBar() {
        statusLabel = new JLabel("Starting...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        return statusLabel;
    }

    private void switchView(String viewName) {
        viewCardLayout.show(viewContainer, viewName);
        setTitle("Cowork Suite — " + viewName);
        statusLabel.setText("Switched to " + viewName + " view.");
        if (VIEW_AGENTIC.equals(viewName) && agenticPanel != null) {
            agenticPanel.onTabShown();
        }
    }

    // ---------------------------------------------------------------- Button actions

    @Override
    public void onButtonClicked(SuiteButton button) {
        switch (button.getActionType()) {
            case "TASK_TEMPLATE" -> onExecuteTaskTemplate(button);
            case "CUSTOM_PROMPT" -> onExecuteCustomPrompt(button);
            case "OPEN_CONTEXT_MENU" -> onOpenContextControl();
            case "EDIT_CONFIG" -> onEditConfig();
            case "SPAWN_BUTTON" -> onCreateButton();
            default -> statusLabel.setText("Unknown action: " + button.getActionType());
        }
    }

    private void onExecuteTaskTemplate(SuiteButton button) {
        TaskContext taskCtx = button.toTaskContext();
        if (taskCtx == null) {
            statusLabel.setText("Task button has no template defined.");
            return;
        }
        if (taskCtx.getFollowUpQuestions() != null && !taskCtx.getFollowUpQuestions().isEmpty()) {
            TaskQuestionDialog questionDialog = new TaskQuestionDialog(this, button.getLabel(), taskCtx.getFollowUpQuestions());
            questionDialog.setVisible(true);
            if (questionDialog.wasCancelled()) {
                statusLabel.setText("Task cancelled.");
                return;
            }
            for (var entry : questionDialog.getAnswers().entrySet()) {
                taskCtx.answerQuestion(entry.getKey(), entry.getValue());
            }
        }
        contextController.setActiveTaskContext(taskCtx);
        runClaude(button, buildTaskEnrichedPrompt(taskCtx, extraInputField.getText().trim()));
    }

    private void onExecuteCustomPrompt(SuiteButton button) {
        String param = button.getParam("value");
        if (param == null || param.isBlank()) {
            statusLabel.setText("Custom prompt button has no prompt. Edit it and fill in the Parameter field.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (contextController.shouldIncludeOrgContext()) {
            sb.append(contextController.getEffectiveOrgContext()).append("\n");
        }
        sb.append(param);
        String extra = extraInputField.getText().trim();
        if (!extra.isEmpty()) {
            sb.append("\n\nUser Input:\n").append(extra);
        }
        runClaude(button, sb.toString());
    }

    /** Org context block, then task block, then the user's text (substituted into {user_input} if present). */
    private String buildTaskEnrichedPrompt(TaskContext taskCtx, String userText) {
        StringBuilder sb = new StringBuilder();
        if (contextController.shouldIncludeOrgContext()) {
            sb.append(contextController.getEffectiveOrgContext()).append("\n");
        }
        if (contextController.shouldIncludeTaskContext()) {
            sb.append(taskCtx.buildTaskBlock());
        }
        String template = taskCtx.getPromptTemplate();
        if (template != null && template.contains("{user_input}")) {
            sb.append("User Input:\n");
            sb.append(template.replace("{user_input}", userText.isEmpty() ? "(not provided)" : userText));
        } else if (!userText.isEmpty()) {
            sb.append("User Input:\n").append(userText);
        }
        return sb.toString();
    }

    private void runClaude(SuiteButton button, String prompt) {
        apiRequestLog.append(ApiRequestLog.RequestRecord.from(button.getId(), "task",
                config.getClaudeModel(), "anthropic",
                new LlmRequest(null, List.of(new ChatMessage("user", prompt)), clients.maxTokens()), null));
        statusLabel.setText("Running \"" + button.getLabel() + "\"...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return clients.claude().sendMessage(prompt);
            }

            @Override
            protected void done() {
                try {
                    resultPanel.addResult(button.getLabel(), get());
                    statusLabel.setText("\"" + button.getLabel() + "\" complete.");
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    resultPanel.addResult(button.getLabel() + " — failed", String.valueOf(cause.getMessage()));
                    statusLabel.setText("Task error: " + cause.getMessage());
                }
            }
        }.execute();
    }

    // ---------------------------------------------------------------- Button CRUD

    @Override
    public void onCreateButton() {
        String[] options = {"AI Assistant", "Manual Editor"};
        int choice = JOptionPane.showOptionDialog(this, "How would you like to create your new task button?",
                "Create New Button", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        SuiteButton newBtn = null;
        if (choice == 0) {
            ButtonCreationAssistantDialog assistant = new ButtonCreationAssistantDialog(this, clients, colorMap);
            assistant.setVisible(true);
            newBtn = assistant.getResult();
        } else if (choice == 1) {
            ButtonCreatorDialog dialog = new ButtonCreatorDialog(this, colorMap);
            dialog.setVisible(true);
            newBtn = dialog.getResult();
        }
        if (newBtn != null) {
            suiteButtons.add(newBtn);
            persistButtons("Created button: " + newBtn.getLabel());
        }
    }

    @Override
    public void onEditButton(SuiteButton button) {
        ButtonCreatorDialog dialog = new ButtonCreatorDialog(this, colorMap, button);
        dialog.setVisible(true);
        SuiteButton edited = dialog.getResult();
        if (edited != null) {
            int idx = suiteButtons.indexOf(button);
            if (idx >= 0) {
                suiteButtons.set(idx, edited);
            } else {
                suiteButtons.add(edited);
            }
            persistButtons("Updated button: " + edited.getLabel());
        }
    }

    @Override
    public void onDeleteButton(SuiteButton button) {
        int confirm = JOptionPane.showConfirmDialog(this, "Delete button \"" + button.getLabel() + "\"?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            suiteButtons.remove(button);
            persistButtons("Deleted button: " + button.getLabel());
        }
    }

    private void persistButtons(String status) {
        buttonStore.saveButtons(suiteButtons);
        buttonPanel.rebuildButtons();
        statusLabel.setText(status);
    }

    // ---------------------------------------------------------------- Menu actions

    private void onEditConfig() {
        new ConfigEditorDialog(this, config).setVisible(true);
        // Config is mutated in place; rebuild the factory so new key/model take effect everywhere.
        clients = new ClientFactory(config, httpClient);
        if (agenticPanel != null) agenticPanel.setClientFactory(clients);
        statusLabel.setText("Configuration updated.");
    }

    private void onOpenContextControl() {
        new ContextControlDialog(this, contextController, apiRequestLog).setVisible(true);
    }
}
