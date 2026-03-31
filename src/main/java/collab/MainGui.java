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
    private JPanel claudeStream;
    private JPanel gptStream;
    private JPanel geminiStream;
    private JScrollPane claudeScroll;
    private JScrollPane gptScroll;
    private JScrollPane geminiScroll;
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
        setSize(1300, 850);
        initApplication();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
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

        claudeStream = createStreamPanel();
        gptStream = createStreamPanel();
        geminiStream = createStreamPanel();
        claudeScroll = new JScrollPane(claudeStream);
        gptScroll = new JScrollPane(gptStream);
        geminiScroll = new JScrollPane(geminiStream);

        JSplitPane leftMidSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrap("Claude", claudeScroll),
                wrap("GPT", gptScroll));
        leftMidSplit.setResizeWeight(0.5);

        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftMidSplit,
                wrap("Gemini", geminiScroll));
        topSplit.setResizeWeight(0.67);

        promptArea = new JTextArea();
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);

        synthesisArea = new JTextArea();
        synthesisArea.setEditable(false);
        synthesisArea.setLineWrap(true);
        synthesisArea.setWrapStyleWord(true);

        JSplitPane bottomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                wrap("Prompt", new JScrollPane(promptArea)),
                wrap("Synthesis", new JScrollPane(synthesisArea)));
        bottomSplit.setResizeWeight(0.5);

        JSplitPane mainVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, bottomSplit);
        mainVerticalSplit.setResizeWeight(0.62);

        panel.add(mainVerticalSplit, BorderLayout.CENTER);
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
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(statusLabel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel wrap(String title, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(component, BorderLayout.CENTER);
        return panel;
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
            rebuildOrchestrator();
            statusLabel.setText("Created and loaded profile set: " + newSet.getName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Unable to save profile set: " + e.getMessage(),
                    "Profile Save Error",
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

        clearStream(claudeStream);
        clearStream(gptStream);
        clearStream(geminiStream);
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
        SwingUtilities.invokeLater(() -> appendCard(model,
                "Phase 1 — " + perspective,
                response,
                colorForPhase(model, 0),
                false));
    }

    @Override
    public void onPhase2Reaction(int round, String model, String perspective, String reaction) {
        SwingUtilities.invokeLater(() -> appendCard(model,
                "Phase 2 Round " + round + " — " + perspective,
                reaction,
                colorForPhase(model, round),
                false));
    }

    @Override
    public void onSynthesis(String synthesis) {
        SwingUtilities.invokeLater(() -> synthesisArea.setText(synthesis));
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
