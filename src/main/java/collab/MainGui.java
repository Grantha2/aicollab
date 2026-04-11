package collab;

import javax.swing.*;
import java.awt.*;
import java.net.http.HttpClient;
import java.util.List;

public class MainGui extends JFrame implements DebateListener, ButtonPanel.ButtonClickListener {

    // View names for CardLayout
    private static final String VIEW_EXECUTIVE_SUITE = "Executive Suite";
    private static final String VIEW_DEBATE = "Debate & Conversation";
    private static final String VIEW_AGENTIC = "Agentic Routines";

    private Config config;
    private ProfileLibrary profileLibrary;
    private ProfileSet activeProfileSet;
    private Maestro maestro;
    private ConversationContext conversationContext;

    // Executive Suite components
    private CategoryColorMap colorMap;
    private ButtonStore buttonStore;
    private IconLoader iconLoader;
    private ButtonPanel buttonPanel;
    private ContextController contextController;
    private List<SuiteButton> suiteButtons;

    // View switching
    private CardLayout viewCardLayout;
    private JPanel viewContainer;
    private String activeView = VIEW_EXECUTIVE_SUITE;
    private ButtonGroup viewGroup;

    // Shared debate components (re-parented across views)
    private JPanel debateContentPanel;
    private JPanel debateToolbar;

    private JComboBox<String> stakeholderCombo;
    private JComboBox<String> profileCombo;
    private JPanel claudeStream;
    private JPanel gptStream;
    private JPanel geminiStream;
    private JScrollPane claudeScroll;
    private JScrollPane gptScroll;
    private JScrollPane geminiScroll;
    private JTextArea promptArea;
    private JTextArea synthesisArea;
    private JLabel statusLabel;
    private JLabel activePanelLabel;
    private int cycleCount = 0;

    // View panel for debate
    private JPanel debateViewPanel;

    // Agentic infrastructure
    private ContextChangeLog changeLog;
    private ReconciliationService reconciliationService;
    private DailyContextUpdateFunction dailyUpdateFn;
    private AgenticRoutinesPanel agenticPanel;

    public MainGui() {
        super("AI Collaboration Platform \u2014 Executive Suite");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // Initialize Executive Suite components
        colorMap = new CategoryColorMap();
        buttonStore = new ButtonStore();
        iconLoader = new IconLoader();
        contextController = new ContextController();
        suiteButtons = buttonStore.loadButtons();

        setJMenuBar(buildMenuBar());
        add(buildViewContainer(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        setSize(1500, 900);
        initApplication();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Settings menu
        JMenu settingsMenu = new JMenu("Settings");
        JMenuItem editConfig = new JMenuItem("Edit Configuration...");
        editConfig.addActionListener(e -> onEditConfig());
        JMenuItem selectProfile = new JMenuItem("Select Profile Set...");
        selectProfile.addActionListener(e -> onSelectProfileSet());
        JMenuItem createProfile = new JMenuItem("Create Profile Set...");
        createProfile.addActionListener(e -> onCreateProfileSet());
        settingsMenu.add(editConfig);
        settingsMenu.add(selectProfile);
        settingsMenu.add(createProfile);
        menuBar.add(settingsMenu);

        // Context menu
        JMenu contextMenu = new JMenu("Context");
        JMenuItem contextControl = new JMenuItem("Context Control...");
        contextControl.setAccelerator(KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_C,
                java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        contextControl.addActionListener(e -> onOpenContextControl());
        JMenuItem clearHistory = new JMenuItem("Clear Conversation");
        clearHistory.addActionListener(e -> onClearConversation());
        contextMenu.add(contextControl);
        contextMenu.addSeparator();
        contextMenu.add(clearHistory);
        menuBar.add(contextMenu);

        // View menu
        JMenu viewMenu = new JMenu("View");
        viewGroup = new ButtonGroup();

        JRadioButtonMenuItem execView = new JRadioButtonMenuItem(VIEW_EXECUTIVE_SUITE, true);
        execView.setAccelerator(KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_1,
                java.awt.event.InputEvent.CTRL_DOWN_MASK));
        execView.addActionListener(e -> switchView(VIEW_EXECUTIVE_SUITE));
        viewGroup.add(execView);
        viewMenu.add(execView);

        JRadioButtonMenuItem debateView = new JRadioButtonMenuItem(VIEW_DEBATE);
        debateView.setAccelerator(KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_2,
                java.awt.event.InputEvent.CTRL_DOWN_MASK));
        debateView.addActionListener(e -> switchView(VIEW_DEBATE));
        viewGroup.add(debateView);
        viewMenu.add(debateView);

        JRadioButtonMenuItem agenticView = new JRadioButtonMenuItem(VIEW_AGENTIC);
        agenticView.setAccelerator(KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_3,
                java.awt.event.InputEvent.CTRL_DOWN_MASK));
        agenticView.addActionListener(e -> switchView(VIEW_AGENTIC));
        viewGroup.add(agenticView);
        viewMenu.add(agenticView);

        menuBar.add(viewMenu);

        return menuBar;
    }

    private void switchView(String viewName) {
        activeView = viewName;

        // Debate components only live in the Debate & Conversation view.
        // Executive Suite is buttons-only for easier prompting.
        if (VIEW_DEBATE.equals(viewName)) {
            debateViewPanel.add(debateToolbar, BorderLayout.NORTH);
            debateViewPanel.add(debateContentPanel, BorderLayout.CENTER);
        }

        viewCardLayout.show(viewContainer, viewName);
        setTitle("AI Collaboration Platform \u2014 " + viewName);
        statusLabel.setText("Switched to " + viewName + " view.");

        // Trigger session-start check when agentic tab becomes visible
        if (VIEW_AGENTIC.equals(viewName) && agenticPanel != null) {
            agenticPanel.onTabShown();
        }

        viewContainer.revalidate();
        viewContainer.repaint();
    }

    private JComponent buildButtonPanel() {
        buttonPanel = new ButtonPanel(colorMap, iconLoader, suiteButtons);
        buttonPanel.setClickListener(this);
        return buttonPanel;
    }

    /**
     * Builds the debate toolbar with stakeholder selector, profile selector,
     * and run debate button. This toolbar is shared between Executive Suite
     * and Debate views.
     */
    private JPanel buildDebateToolbar() {
        debateToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        stakeholderCombo = new JComboBox<>();
        profileCombo = new JComboBox<>();
        profileCombo.addActionListener(e -> onProfileComboChanged());
        JButton runButton = new JButton("Run Debate");
        runButton.addActionListener(e -> onRunDebate());

        debateToolbar.add(new JLabel("Stakeholder:"));
        debateToolbar.add(stakeholderCombo);
        debateToolbar.add(Box.createHorizontalStrut(8));
        debateToolbar.add(new JLabel("Profile:"));
        debateToolbar.add(profileCombo);
        debateToolbar.add(Box.createHorizontalStrut(8));
        debateToolbar.add(runButton);
        return debateToolbar;
    }

    /**
     * Builds the view container that holds all three views with CardLayout.
     */
    private JComponent buildViewContainer() {
        viewCardLayout = new CardLayout();
        viewContainer = new JPanel(viewCardLayout);

        // Build shared debate components first
        buildDebateComponents();
        buildDebateToolbar();
        debateContentPanel = buildDebateContentPanel();

        // Executive Suite view: buttons only (for easier prompting)
        JPanel execView = new JPanel(new BorderLayout(8, 8));
        execView.add(buildButtonPanel(), BorderLayout.CENTER);
        viewContainer.add(execView, VIEW_EXECUTIVE_SUITE);

        // Debate & Conversation view: toolbar + streams + synthesis + prompt
        debateViewPanel = new JPanel(new BorderLayout(8, 8));
        debateViewPanel.add(debateToolbar, BorderLayout.NORTH);
        debateViewPanel.add(debateContentPanel, BorderLayout.CENTER);
        viewContainer.add(debateViewPanel, VIEW_DEBATE);

        // Agentic Routines view: placeholder until initApplication() creates real panel
        JPanel agenticPlaceholder = new JPanel(new BorderLayout());
        agenticPlaceholder.setBackground(new Color(245, 245, 250));
        JLabel loadingLabel = new JLabel("Loading agentic routines...", SwingConstants.CENTER);
        loadingLabel.setForeground(Color.GRAY);
        agenticPlaceholder.add(loadingLabel, BorderLayout.CENTER);
        viewContainer.add(agenticPlaceholder, VIEW_AGENTIC);

        return viewContainer;
    }

    /**
     * Creates the shared stream panels, synthesis area, and prompt area.
     */
    private void buildDebateComponents() {
        claudeStream = createStreamPanel();
        gptStream = createStreamPanel();
        geminiStream = createStreamPanel();
        claudeScroll = new JScrollPane(claudeStream);
        gptScroll = new JScrollPane(gptStream);
        geminiScroll = new JScrollPane(geminiStream);

        synthesisArea = new JTextArea();
        synthesisArea.setEditable(false);
        synthesisArea.setLineWrap(true);
        synthesisArea.setWrapStyleWord(true);

        promptArea = new JTextArea();
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
    }

    /**
     * Builds the debate content panel containing model streams, synthesis,
     * and prompt. This panel is re-parented between Executive Suite and
     * Debate views when switching.
     */
    private JPanel buildDebateContentPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JSplitPane leftMidSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrap("Claude", claudeScroll),
                wrap("GPT", gptScroll));
        leftMidSplit.setResizeWeight(0.5);

        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftMidSplit,
                wrap("Gemini", geminiScroll));
        topSplit.setResizeWeight(0.67);

        JSplitPane mainVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                topSplit,
                wrap("Synthesis", new JScrollPane(synthesisArea)));
        mainVerticalSplit.setResizeWeight(0.62);

        JScrollPane promptScroll = new JScrollPane(promptArea);
        promptScroll.setPreferredSize(new Dimension(0, 180));
        JPanel promptDock = wrap("Prompt", promptScroll);
        promptDock.setPreferredSize(new Dimension(0, 200));

        panel.add(mainVerticalSplit, BorderLayout.CENTER);
        panel.add(promptDock, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createStreamPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        return panel;
    }

    private JComponent buildStatusBar() {
        statusLabel = new JLabel("Ready");
        activePanelLabel = new JLabel(" ");
        activePanelLabel.setFont(activePanelLabel.getFont().deriveFont(Font.PLAIN, 11f));
        activePanelLabel.setForeground(new Color(90, 90, 90));
        activePanelLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(statusLabel, BorderLayout.CENTER);
        panel.add(activePanelLabel, BorderLayout.EAST);
        return panel;
    }

    /**
     * Renders the three active panel agents in the status bar so it is
     * always obvious which three personas Maestro will use.
     */
    private void updateActivePanelLabel() {
        if (activePanelLabel == null) return;
        if (activeProfileSet == null || activeProfileSet.getAgents() == null
                || activeProfileSet.getAgents().size() < 3) {
            activePanelLabel.setText("Panel: (not configured)");
            return;
        }
        List<AgentProfile> agents = activeProfileSet.getAgents();
        activePanelLabel.setText("Panel: "
                + agents.get(0).getName() + " \u00b7 " + agents.get(0).getPerspective() + "  \u2502  "
                + agents.get(1).getName() + " \u00b7 " + agents.get(1).getPerspective() + "  \u2502  "
                + agents.get(2).getName() + " \u00b7 " + agents.get(2).getPerspective());
    }

    private JPanel wrap(String title, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private boolean needsSetup(ProfileSet profile) {
        return (profile.getTeamContext() == null || profile.getTeamContext().isBlank())
            && (profile.getAgents() == null || profile.getAgents().isEmpty())
            && (profile.getStakeholders() == null || profile.getStakeholders().isEmpty());
    }

    private void initApplication() {
        try {
            config = new Config("config.properties");
            profileLibrary = new ProfileLibrary();
            profileLibrary.ensureDefaultExists();
            List<String> names = profileLibrary.listAvailableSets();
            activeProfileSet = profileLibrary.loadSet(names.getFirst());

            // First-launch check: if profile has no agents or stakeholders, run setup wizard
            if (needsSetup(activeProfileSet)) {
                FirstLaunchSetupDialog setupDialog = new FirstLaunchSetupDialog(this);
                setupDialog.setVisible(true);
                if (!setupDialog.wasCancelled()) {
                    activeProfileSet = setupDialog.buildProfileSet();
                    profileLibrary.saveSet(activeProfileSet, "default");
                }
            }

            refreshProfileCombo();
            rebuildStakeholderCombo();
            rebuildMaestro();

            // Initialize agentic infrastructure
            OrganizationContext orgCtx = contextController.getOrganizationContext();
            changeLog = new ContextChangeLog();
            reconciliationService = new ReconciliationService(orgCtx, changeLog);
            dailyUpdateFn = new DailyContextUpdateFunction(orgCtx, reconciliationService);

            // Initialize structured data stores
            InitiativeStore initiativeStore = new InitiativeStore();
            RelationshipStore relationshipStore = new RelationshipStore();
            OperationalFeedStore feedStore = new OperationalFeedStore();
            WorkflowStore workflowStore = new WorkflowStore();

            // Build task registry with built-in tasks
            AgenticTaskRegistry taskRegistry = new AgenticTaskRegistry();
            taskRegistry.register(new StartYourDayTask(feedStore));
            taskRegistry.register(new OutboundMessagesTask(feedStore, relationshipStore));
            taskRegistry.register(new ContextRefreshTask(dailyUpdateFn));
            taskRegistry.register(new MeetingPrepTask(feedStore, relationshipStore));
            taskRegistry.register(new InitiativeReviewTask(initiativeStore));
            taskRegistry.register(new WeeklyReportTask());
            taskRegistry.register(new StakeholderBriefingTask());

            // Register user-defined workflows
            for (WorkflowDefinition wd : workflowStore.getAll()) {
                if (wd.isEnabled()) taskRegistry.register(new UserWorkflowTask(wd));
            }

            // Recommendation engine
            RecommendationEngine recommendationEngine = new RecommendationEngine(orgCtx, feedStore, changeLog);

            agenticPanel = new AgenticRoutinesPanel(orgCtx, reconciliationService, changeLog,
                    config, taskRegistry, feedStore, workflowStore, recommendationEngine);

            // Replace placeholder with real agentic panel
            viewContainer.remove(viewContainer.getComponentCount() - 1);
            viewContainer.add(agenticPanel, VIEW_AGENTIC);
            viewContainer.revalidate();

            statusLabel.setText("Loaded profile set: " + activeProfileSet.getName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to initialize application: " + e.getMessage(),
                    "Startup Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshProfileCombo() throws Exception {
        String selected = activeProfileSet == null ? null : activeProfileSet.getName();
        profileCombo.removeAllItems();
        for (String name : profileLibrary.listAvailableSets()) {
            profileCombo.addItem(name);
        }
        if (selected != null) {
            profileCombo.setSelectedItem(selected);
        }
    }

    private void onProfileComboChanged() {
        Object selected = profileCombo.getSelectedItem();
        if (selected == null || profileLibrary == null) {
            return;
        }
        String name = selected.toString();
        if (activeProfileSet != null && name.equals(activeProfileSet.getName())) {
            return;
        }
        try {
            activeProfileSet = profileLibrary.loadSet(name);
            rebuildStakeholderCombo();
            rebuildMaestro();
            statusLabel.setText("Loaded profile set: " + name);
        } catch (Exception e) {
            statusLabel.setText("Failed loading profile: " + e.getMessage());
        }
    }

    private void rebuildStakeholderCombo() {
        stakeholderCombo.removeAllItems();
        if (activeProfileSet == null || activeProfileSet.getStakeholders() == null) {
            return;
        }
        for (StakeholderProfile p : activeProfileSet.getStakeholders()) {
            stakeholderCombo.addItem(p.getName() + " \u2014 " + p.getRole());
        }
        if (stakeholderCombo.getItemCount() > 0) {
            stakeholderCombo.setSelectedIndex(0);
        }
    }

    private void rebuildMaestro() {
        // Maestro requires exactly 3 agent slots (Claude / GPT / Gemini).
        // ProfileSetEditorDialog enforces this on save, but guard defensively
        // so a manually-edited profile-set.json can't crash the app silently.
        List<AgentProfile> agents = activeProfileSet.getAgents();
        if (agents == null || agents.size() < 3) {
            statusLabel.setText("Active profile set has fewer than 3 agents — edit the profile to continue.");
            maestro = null;
            updateActivePanelLabel();
            return;
        }

        HttpClient httpClient = HttpClient.newHttpClient();
        int maxTokens = config.getMaxResponseTokens();

        LlmClient claudeClient = new AnthropicClient(httpClient, config.getClaudeUrl(),
                config.getClaudeKey(), config.getClaudeModel(), maxTokens);
        LlmClient gptClient = new OpenAiClient(httpClient, config.getOpenAiUrl(),
                config.getOpenAiKey(), config.getOpenAiModel(), maxTokens);
        LlmClient geminiClient = new GeminiClient(httpClient,
                config.getGeminiKey(), config.getGeminiModel(), maxTokens);

        conversationContext = new ConversationContext(config.getMaxHistoryChars());
        String effectiveTeamCtx = contextController.getEffectiveTeamContext(activeProfileSet.getTeamContext());
        PromptBuilder promptBuilder = new PromptBuilder(conversationContext, effectiveTeamCtx, contextController);
        SessionStore sessionStore = SessionStore.createNewDefaultSession();

        maestro = new Maestro(
                claudeClient, gptClient, geminiClient,
                agents.get(0), agents.get(1), agents.get(2),
                promptBuilder, conversationContext,
                config.getDebateRounds(), maxTokens, sessionStore);
        maestro.setDebateListener(this);
        updateActivePanelLabel();
    }

    // ============================================================
    // Button Panel Callback — ButtonPanel.ButtonClickListener
    // ============================================================

    @Override
    public void onButtonClicked(SuiteButton button) {
        switch (button.getActionType()) {
            case "TASK_TEMPLATE" -> onExecuteTaskTemplate(button);
            case "RUN_DEBATE" -> onRunDebate();
            case "SWITCH_PROFILE" -> onSelectProfileSet();
            case "CREATE_PROFILE" -> onCreateProfileSet();
            case "OPEN_CONTEXT_MENU" -> onOpenContextControl();
            case "EDIT_CONFIG" -> onEditConfig();
            case "EXPORT_SESSION" -> statusLabel.setText("Export not yet implemented.");
            case "SPAWN_BUTTON" -> onCreateButton();
            case "CUSTOM_PROMPT" -> {
                String param = button.getParam("value");
                if (param != null && !param.isEmpty()) {
                    promptArea.setText(param);
                    onRunDebate();
                }
            }
            default -> statusLabel.setText("Unknown action: " + button.getActionType());
        }
    }

    /**
     * Executes a task template button: collects follow-up answers from the user,
     * builds a task-enriched prompt, and routes to simple or debate mode.
     */
    private void onExecuteTaskTemplate(SuiteButton button) {
        TaskContext taskCtx = button.toTaskContext();
        if (taskCtx == null) {
            statusLabel.setText("Task button has no template defined.");
            return;
        }

        // Collect follow-up answers via dialog
        if (taskCtx.getFollowUpQuestions() != null && !taskCtx.getFollowUpQuestions().isEmpty()) {
            TaskQuestionDialog questionDialog = new TaskQuestionDialog(
                    this, button.getLabel(), taskCtx.getFollowUpQuestions());
            questionDialog.setVisible(true);
            if (questionDialog.wasCancelled()) {
                statusLabel.setText("Task cancelled.");
                return;
            }
            for (var entry : questionDialog.getAnswers().entrySet()) {
                taskCtx.answerQuestion(entry.getKey(), entry.getValue());
            }
        }

        // Store active task context in controller
        contextController.setActiveTaskContext(taskCtx);

        // Build the enriched prompt
        String userText = promptArea.getText().trim();
        String enrichedPrompt = buildTaskEnrichedPrompt(taskCtx, userText);

        if (button.isSimpleMode()) {
            onRunSimpleTask(enrichedPrompt);
        } else {
            promptArea.setText(enrichedPrompt);
            onRunDebate();
        }
    }

    /**
     * Builds a prompt enriched with task context: template, style, and answers.
     */
    private String buildTaskEnrichedPrompt(TaskContext taskCtx, String userText) {
        StringBuilder sb = new StringBuilder();

        // Prepend organization context if enabled
        if (contextController.shouldIncludeOrgContext()) {
            sb.append(contextController.getEffectiveOrgContext());
            sb.append("\n");
        }

        if (contextController.shouldIncludeTaskContext()) {
            sb.append(taskCtx.buildTaskBlock());
        }

        // Substitute {user_input} in the template or append user text
        if (taskCtx.getPromptTemplate() != null && taskCtx.getPromptTemplate().contains("{user_input}")) {
            sb.append("User Input:\n");
            sb.append(taskCtx.getPromptTemplate().replace("{user_input}",
                    userText.isEmpty() ? "(not provided)" : userText));
        } else if (!userText.isEmpty()) {
            sb.append("User Input:\n").append(userText);
        }

        return sb.toString();
    }

    /**
     * Runs a simple single-API-call task using Claude (first available model).
     * Displays the result in the Claude stream panel.
     */
    private void onRunSimpleTask(String prompt) {
        if (maestro == null || activeProfileSet == null) {
            statusLabel.setText("No model configured. Check settings.");
            return;
        }
        statusLabel.setText("Running simple task...");

        cycleCount++;
        if (cycleCount > 1) {
            appendCycleDivider(claudeStream, cycleCount);
        }

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                int maxTokens = config.getMaxResponseTokens();
                LlmClient client = new AnthropicClient(httpClient, config.getClaudeUrl(),
                        config.getClaudeKey(), config.getClaudeModel(), maxTokens);
                return client.sendMessage(prompt);
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    SwingUtilities.invokeLater(() -> {
                        appendCard("Claude", "Task Result",
                                response, tint(Color.ORANGE, 0.9), false);
                        statusLabel.setText("Simple task complete.");
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                            statusLabel.setText("Task error: " + e.getMessage()));
                }
            }
        }.execute();
    }

    @Override
    public void onCreateButton() {
        String[] options = {"AI Assistant", "Manual Editor"};
        int choice = JOptionPane.showOptionDialog(this,
                "How would you like to create your new task button?",
                "Create New Button",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        SuiteButton newBtn = null;

        if (choice == 0) {
            ButtonCreationAssistantDialog assistant =
                    new ButtonCreationAssistantDialog(this, config, colorMap);
            assistant.setVisible(true);
            newBtn = assistant.getResult();
        } else if (choice == 1) {
            ButtonCreatorDialog dialog = new ButtonCreatorDialog(this, colorMap);
            dialog.setVisible(true);
            newBtn = dialog.getResult();
        }

        if (newBtn != null) {
            suiteButtons.add(newBtn);
            buttonStore.saveButtons(suiteButtons);
            buttonPanel.rebuildButtons();
            statusLabel.setText("Created button: " + newBtn.getLabel());
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
            }
            buttonStore.saveButtons(suiteButtons);
            buttonPanel.rebuildButtons();
            statusLabel.setText("Updated button: " + edited.getLabel());
        }
    }

    @Override
    public void onDeleteButton(SuiteButton button) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete button \"" + button.getLabel() + "\"?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            suiteButtons.remove(button);
            buttonStore.saveButtons(suiteButtons);
            buttonPanel.rebuildButtons();
            statusLabel.setText("Deleted button: " + button.getLabel());
        }
    }

    // ============================================================
    // Menu Actions
    // ============================================================

    private void onEditConfig() {
        ConfigEditorDialog dialog = new ConfigEditorDialog(this, config);
        dialog.setVisible(true);
        rebuildMaestro();
    }

    private void onSelectProfileSet() {
        try {
            ProfileSelectorDialog dialog = new ProfileSelectorDialog(this, profileLibrary);
            dialog.setVisible(true);
            ProfileSet selected = dialog.getSelectedProfileSet();
            if (selected != null) {
                activeProfileSet = selected;
                refreshProfileCombo();
                rebuildStakeholderCombo();
                rebuildMaestro();
                statusLabel.setText("Loaded profile set: " + selected.getName());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Unable to select profile set: " + e.getMessage(),
                    "Profile Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onCreateProfileSet() {
        ProfileSetEditorDialog dialog = new ProfileSetEditorDialog(this, activeProfileSet);
        dialog.setVisible(true);
        ProfileSet newSet = dialog.getProfileSet();
        if (newSet == null) {
            return;
        }
        try {
            profileLibrary.saveSet(newSet, newSet.getName());
            activeProfileSet = profileLibrary.loadSet(newSet.getName());
            refreshProfileCombo();
            rebuildStakeholderCombo();
            rebuildMaestro();
            statusLabel.setText("Created and loaded profile set: " + newSet.getName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Unable to save profile set: " + e.getMessage(),
                    "Profile Save Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onOpenContextControl() {
        Runnable onProfileSaved = () -> {
            try {
                profileLibrary.saveSet(activeProfileSet, activeProfileSet.getName());
                rebuildMaestro();
                statusLabel.setText("Saved profile set: " + activeProfileSet.getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Unable to save profile set: " + e.getMessage(),
                        "Profile Save Error", JOptionPane.ERROR_MESSAGE);
            }
        };
        Runnable onEditProfile = this::onCreateProfileSet;
        ContextControlDialog dialog = new ContextControlDialog(
                this, contextController, conversationContext, activeProfileSet,
                onProfileSaved, onEditProfile);
        dialog.setVisible(true);
    }

    private void onClearConversation() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Clear all conversation history and streams?",
                "Confirm Clear", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            clearStream(claudeStream);
            clearStream(gptStream);
            clearStream(geminiStream);
            synthesisArea.setText("");
            if (conversationContext != null) {
                conversationContext.clear();
            }
            cycleCount = 0;
            statusLabel.setText("Conversation cleared.");
        }
    }

    // ============================================================
    // Debate Execution
    // ============================================================

    private void onRunDebate() {
        if (maestro == null || activeProfileSet == null) {
            return;
        }
        int idx = stakeholderCombo.getSelectedIndex();
        if (idx < 0) {
            return;
        }
        StakeholderProfile activeStakeholder = activeProfileSet.getStakeholders().get(idx);
        String prompt = promptArea.getText().trim();
        if (prompt.isEmpty()) {
            statusLabel.setText("Enter a prompt first.");
            return;
        }

        // Phase 5b: Don't clear streams \u2014 add cycle divider instead
        cycleCount++;
        if (cycleCount > 1) {
            appendCycleDivider(claudeStream, cycleCount);
            appendCycleDivider(gptStream, cycleCount);
            appendCycleDivider(geminiStream, cycleCount);
            synthesisArea.append("\n\n" + "\u2550".repeat(40) + " Cycle " + cycleCount + " " + "\u2550".repeat(40) + "\n\n");
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                maestro.runDebate(prompt, activeStakeholder);
                return null;
            }
        }.execute();
    }

    private void appendCycleDivider(JPanel stream, int cycle) {
        JPanel divider = new JPanel(new BorderLayout());
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        divider.setBackground(new Color(60, 60, 60));
        divider.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JLabel label = new JLabel("\u2550\u2550\u2550 Cycle " + cycle + " \u2550\u2550\u2550");
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        divider.add(label, BorderLayout.CENTER);
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        stream.add(divider);
        stream.add(Box.createVerticalStrut(4));
        stream.revalidate();
        stream.repaint();
    }

    // ============================================================
    // DebateListener Implementation
    // ============================================================

    @Override
    public void onPhase1Response(String model, String perspective, String response) {
        SwingUtilities.invokeLater(() -> appendCard(model,
                "Phase 1 \u2014 " + perspective,
                response,
                colorForPhase(model, 0),
                false));
    }

    @Override
    public void onPhase2Reaction(int round, String model, String perspective, String reaction) {
        SwingUtilities.invokeLater(() -> appendCard(model,
                "Phase 2 Round " + round + " \u2014 " + perspective,
                reaction,
                colorForPhase(model, round),
                false));
    }

    @Override
    public void onSynthesis(String synthesis) {
        SwingUtilities.invokeLater(() -> {
            if (synthesisArea.getText().isEmpty()) {
                synthesisArea.setText(synthesis);
            } else {
                synthesisArea.append("\n\n" + synthesis);
            }
        });
    }

    @Override
    public void onStatusUpdate(String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
            maybeAppendApiCallBadge(message);
        });
    }

    private void maybeAppendApiCallBadge(String message) {
        if (message == null) {
            return;
        }
        if (message.contains("Claude")) {
            appendCard("Claude", "API Call", message, tint(Color.ORANGE, 0.9), true);
        } else if (message.contains("GPT")) {
            appendCard("GPT", "API Call", message, tint(Color.DARK_GRAY, 0.92), true);
        } else if (message.contains("Gemini")) {
            appendCard("Gemini", "API Call", message, tint(new Color(66, 133, 244), 0.9), true);
        }
    }

    private void clearStream(JPanel panel) {
        panel.removeAll();
        panel.revalidate();
        panel.repaint();
    }

    private void appendCard(String model, String title, String body, Color bg, boolean compact) {
        JPanel stream = streamForModel(model);
        JScrollPane scroll = scrollForModel(model);
        if (stream == null || scroll == null) {
            return;
        }

        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBackground(bg);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(darker(bg), 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        JLabel header = new JLabel(title);
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        header.setForeground(textColor(bg));

        JTextArea content = new JTextArea(body);
        content.setEditable(false);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setOpaque(false);
        content.setForeground(textColor(bg));
        content.setFont(compact ? content.getFont().deriveFont(11f) : content.getFont());

        card.add(header, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        stream.add(card);
        stream.add(Box.createVerticalStrut(8));
        stream.revalidate();
        stream.repaint();

        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private JPanel streamForModel(String model) {
        return switch (model) {
            case "Claude" -> claudeStream;
            case "GPT" -> gptStream;
            case "Gemini" -> geminiStream;
            default -> null;
        };
    }

    private JScrollPane scrollForModel(String model) {
        return switch (model) {
            case "Claude" -> claudeScroll;
            case "GPT" -> gptScroll;
            case "Gemini" -> geminiScroll;
            default -> null;
        };
    }

    // ============================================================
    // Color Utilities
    // ============================================================

    private Color colorForPhase(String model, int round) {
        return switch (model) {
            case "Claude" -> gradient(new Color(255, 175, 80), round, 5);
            case "GPT" -> gradient(new Color(120, 120, 120), round, 5);
            case "Gemini" -> {
                Color[] rgb = {new Color(66, 133, 244), new Color(234, 67, 53), new Color(52, 168, 83)};
                Color base = rgb[round % rgb.length];
                yield gradient(base, round, 6);
            }
            default -> Color.WHITE;
        };
    }

    private Color gradient(Color base, int round, int maxRound) {
        float factor = Math.min(round, maxRound) / (float) maxRound;
        int r = (int) (base.getRed() + (255 - base.getRed()) * factor * 0.55f);
        int g = (int) (base.getGreen() + (255 - base.getGreen()) * factor * 0.55f);
        int b = (int) (base.getBlue() + (255 - base.getBlue()) * factor * 0.55f);
        return new Color(clamp(r), clamp(g), clamp(b));
    }

    private Color tint(Color base, double amountToWhite) {
        int r = (int) (base.getRed() + (255 - base.getRed()) * amountToWhite);
        int g = (int) (base.getGreen() + (255 - base.getGreen()) * amountToWhite);
        int b = (int) (base.getBlue() + (255 - base.getBlue()) * amountToWhite);
        return new Color(clamp(r), clamp(g), clamp(b));
    }

    private Color darker(Color c) {
        return new Color(clamp((int) (c.getRed() * 0.7)), clamp((int) (c.getGreen() * 0.7)), clamp((int) (c.getBlue() * 0.7)));
    }

    private Color textColor(Color bg) {
        int luminance = (int) (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue());
        return luminance < 140 ? Color.WHITE : Color.BLACK;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static void launch() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> {
            MainGui frame = new MainGui();
            frame.setVisible(true);
        });
    }
}
