package collab;

import javax.swing.*;
import java.awt.*;
import java.net.http.HttpClient;
import java.util.List;

public class MainGui extends JFrame implements DebateListener {

    private Config config;
    private ProfileLibrary profileLibrary;
    private ProfileSet activeProfileSet;
    private Orchestrator orchestrator;

    private JComboBox<String> stakeholderCombo;
    private JComboBox<String> profileCombo;
    private JTextArea claudeArea;
    private JTextArea gptArea;
    private JTextArea geminiArea;
    private JTextArea promptArea;
    private JTextArea synthesisArea;
    private JLabel statusLabel;

    public MainGui() {
        super("AI Collaboration Platform");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setJMenuBar(buildMenuBar());
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildMainPanel(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        setSize(1200, 800);
        initApplication();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu settingsMenu = new JMenu("Settings");

        JMenuItem editConfig = new JMenuItem("Edit Configuration...");
        editConfig.addActionListener(e -> onEditConfig());

        JMenuItem selectProfile = new JMenuItem("Select Profile Set...");
        selectProfile.addActionListener(e -> onSelectProfileSet());

        settingsMenu.add(editConfig);
        settingsMenu.add(selectProfile);
        menuBar.add(settingsMenu);
        return menuBar;
    }

    private JComponent buildToolbar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        stakeholderCombo = new JComboBox<>();
        profileCombo = new JComboBox<>();
        profileCombo.addActionListener(e -> onProfileComboChanged());
        JButton runButton = new JButton("Run Debate");
        runButton.addActionListener(e -> onRunDebate());

        panel.add(new JLabel("Stakeholder:"));
        panel.add(stakeholderCombo);
        panel.add(new JLabel("Profile:"));
        panel.add(profileCombo);
        panel.add(runButton);
        return panel;
    }

    private JComponent buildMainPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        claudeArea = createReadOnlyArea();
        gptArea = createReadOnlyArea();
        geminiArea = createReadOnlyArea();

        JPanel top = new JPanel(new GridLayout(1, 3, 8, 8));
        top.add(wrap("Claude", claudeArea));
        top.add(wrap("GPT", gptArea));
        top.add(wrap("Gemini", geminiArea));

        promptArea = new JTextArea();
        synthesisArea = createReadOnlyArea();
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                wrap("Prompt", new JScrollPane(promptArea)),
                wrap("Synthesis", new JScrollPane(synthesisArea)));
        splitPane.setResizeWeight(0.45);

        panel.add(top, BorderLayout.CENTER);
        panel.add(splitPane, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildStatusBar() {
        statusLabel = new JLabel("Ready");
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(statusLabel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel wrap(String title, JTextArea area) {
        return wrap(title, new JScrollPane(area));
    }

    private JPanel wrap(String title, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private JTextArea createReadOnlyArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private void initApplication() {
        try {
            config = new Config("config.properties");
            profileLibrary = new ProfileLibrary();
            profileLibrary.ensureDefaultExists();
            List<String> names = profileLibrary.listAvailableSets();
            activeProfileSet = profileLibrary.loadSet(names.getFirst());
            refreshProfileCombo();
            rebuildStakeholderCombo();
            rebuildOrchestrator();
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
            rebuildOrchestrator();
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
            stakeholderCombo.addItem(p.getName() + " — " + p.getRole());
        }
        if (stakeholderCombo.getItemCount() > 0) {
            stakeholderCombo.setSelectedIndex(0);
        }
    }

    private void rebuildOrchestrator() {
        HttpClient httpClient = HttpClient.newHttpClient();
        int maxTokens = config.getMaxResponseTokens();

        LlmClient claudeClient = new AnthropicClient(httpClient, config.getClaudeUrl(),
                config.getClaudeKey(), config.getClaudeModel(), maxTokens);
        LlmClient gptClient = new OpenAiClient(httpClient, config.getOpenAiUrl(),
                config.getOpenAiKey(), config.getOpenAiModel(), maxTokens);
        LlmClient geminiClient = new GeminiClient(httpClient,
                config.getGeminiKey(), config.getGeminiModel(), maxTokens);

        ConversationContext context = new ConversationContext(config.getMaxHistoryChars());
        PromptBuilder promptBuilder = new PromptBuilder(context, activeProfileSet.getTeamContext());
        SessionStore sessionStore = SessionStore.createNewDefaultSession();

        List<AgentProfile> agents = activeProfileSet.getAgents();
        orchestrator = new Orchestrator(
                claudeClient, gptClient, geminiClient,
                agents.get(0), agents.get(1), agents.get(2),
                promptBuilder, context,
                config.getDebateRounds(), maxTokens, sessionStore);
        orchestrator.setDebateListener(this);
    }

    private void onEditConfig() {
        ConfigEditorDialog dialog = new ConfigEditorDialog(this, config);
        dialog.setVisible(true);
        rebuildOrchestrator();
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
                rebuildOrchestrator();
                statusLabel.setText("Loaded profile set: " + selected.getName());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Unable to select profile set: " + e.getMessage(),
                    "Profile Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onRunDebate() {
        if (orchestrator == null || activeProfileSet == null) {
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

        claudeArea.setText("");
        gptArea.setText("");
        geminiArea.setText("");
        synthesisArea.setText("");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                orchestrator.runDebate(prompt, activeStakeholder);
                return null;
            }
        }.execute();
    }

    @Override
    public void onPhase1Response(String model, String perspective, String response) {
        SwingUtilities.invokeLater(() -> appendToModelArea(model,
                "[Phase 1 — " + perspective + "]\n" + response + "\n\n"));
    }

    @Override
    public void onPhase2Reaction(int round, String model, String perspective, String reaction) {
        SwingUtilities.invokeLater(() -> appendToModelArea(model,
                "[Phase 2 Round " + round + " — " + perspective + "]\n" + reaction + "\n\n"));
    }

    @Override
    public void onSynthesis(String synthesis) {
        SwingUtilities.invokeLater(() -> synthesisArea.setText(synthesis));
    }

    @Override
    public void onStatusUpdate(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    private void appendToModelArea(String model, String text) {
        JTextArea target = switch (model) {
            case "Claude" -> claudeArea;
            case "GPT" -> gptArea;
            case "Gemini" -> geminiArea;
            default -> null;
        };
        if (target != null) {
            target.append(text);
        }
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
