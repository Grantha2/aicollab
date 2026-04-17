package collab;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.ExecutionException;

// Single-panel chat: one conversation with the panel. Pick a stakeholder,
// type a prompt, hit Send. Streams Phase 1, Phase 2, synthesis into
// the transcript.
public class MainGui extends JFrame {

    private final Config config;
    private final Profiles profiles;
    private final ContextController ctxController;
    private final ConversationContext conversation;
    private final SessionStore sessionStore;
    private final Maestro maestro;
    private int cycleCount = 0;

    private final JTextPane transcript = new JTextPane();
    private final JTextArea promptArea = new JTextArea(4, 60);
    private final JButton sendButton = new JButton("Send");
    private final JLabel statusLabel = new JLabel("Ready.");
    private final JComboBox<Profiles.Stakeholder> stakeholderCombo;

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(new FlatLightLaf());
            } catch (Exception ignored) {}

            if (!new java.io.File("config.properties").exists()) {
                JOptionPane.showMessageDialog(null,
                        "No config.properties found. Run with --cli first to set API keys.",
                        "Setup required", JOptionPane.WARNING_MESSAGE);
                System.exit(0);
            }
            Config cfg = new Config("config.properties", false);
            if (!cfg.hasAllKeys()) {
                JOptionPane.showMessageDialog(null,
                        "config.properties is missing API keys. Edit the file or run with --cli.",
                        "Setup required", JOptionPane.WARNING_MESSAGE);
                System.exit(0);
            }
            new MainGui(cfg).setVisible(true);
        });
    }

    public MainGui(Config config) {
        super("AI Collaboration Platform");
        this.config = config;
        this.profiles = Profiles.loadOrDefault();
        this.ctxController = new ContextController();
        this.conversation = new ConversationContext(config.getMaxHistoryChars());
        this.sessionStore = SessionStore.createNew();

        HttpClient httpClient = HttpClient.newHttpClient();
        int maxTokens = config.getMaxResponseTokens();
        List<LlmClient> clients = List.of(
            new AnthropicClient(httpClient, config.getClaudeUrl(), config.getClaudeKey(),
                    config.getClaudeModel(), maxTokens),
            new OpenAiClient(httpClient, config.getOpenAiUrl(), config.getOpenAiKey(),
                    config.getOpenAiModel(), maxTokens),
            new GeminiClient(httpClient, config.getGeminiKey(), config.getGeminiModel(), maxTokens)
        );
        List<Profiles.Agent> agents = profiles.agents().subList(0, Math.min(3, profiles.agents().size()));
        PromptBuilder promptBuilder = new PromptBuilder(conversation, ctxController, profiles.teamContext());
        this.maestro = new Maestro(clients, agents, promptBuilder, conversation,
                config.getDebateRounds(), sessionStore);

        Maestro.Listener listener = new Maestro.Listener();
        listener.onStatus = this::onStatus;
        listener.onPhase1 = this::onPhase1;
        listener.onPhase2 = this::onPhase2;
        listener.onSynthesis = this::onSynthesis;
        maestro.setListener(listener);

        stakeholderCombo = new JComboBox<>(profiles.stakeholders().toArray(new Profiles.Stakeholder[0]));
        stakeholderCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Profiles.Stakeholder s) {
                    setText(s.name() + " \u2014 " + s.role());
                }
                return c;
            }
        });

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setJMenuBar(buildMenuBar());
        add(buildTopBar(), BorderLayout.NORTH);
        add(buildTranscriptPanel(), BorderLayout.CENTER);
        add(buildInputPanel(), BorderLayout.SOUTH);
        setSize(1100, 780);
        setLocationRelativeTo(null);

        append("Session: " + sessionStore.getSessionFile() + "\n", Color.GRAY, false);
        append("Each cycle = " + maestro.getApiCallCount() + " API calls.\n\n", Color.GRAY, false);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu ctxMenu = new JMenu("Context");
        JMenuItem editOrg = new JMenuItem("Edit Organization Context...");
        editOrg.addActionListener(e -> showOrgContextDialog());
        JMenuItem clearConv = new JMenuItem("Clear Conversation");
        clearConv.addActionListener(e -> {
            conversation.clear();
            transcript.setText("");
            statusLabel.setText("Conversation cleared.");
        });
        ctxMenu.add(editOrg);
        ctxMenu.addSeparator();
        ctxMenu.add(clearConv);
        bar.add(ctxMenu);
        return bar;
    }

    private JPanel buildTopBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new EmptyBorder(4, 8, 0, 8));
        p.add(new JLabel("Stakeholder:"));
        p.add(stakeholderCombo);
        return p;
    }

    private JScrollPane buildTranscriptPanel() {
        transcript.setEditable(false);
        transcript.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        transcript.setMargin(new Insets(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(transcript);
        scroll.setBorder(BorderFactory.createTitledBorder("Conversation"));
        return scroll;
    }

    private JPanel buildInputPanel() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(new EmptyBorder(0, 8, 8, 8));

        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        promptArea.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && (e.isControlDown() || e.isMetaDown())) {
                    sendButton.doClick();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(promptArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Prompt (Ctrl+Enter to send)"));

        sendButton.addActionListener(e -> sendPrompt());

        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.add(statusLabel, BorderLayout.CENTER);
        south.add(sendButton, BorderLayout.EAST);

        p.add(scroll, BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private void sendPrompt() {
        String text = promptArea.getText().trim();
        if (text.isEmpty()) return;

        Profiles.Stakeholder active = (Profiles.Stakeholder) stakeholderCombo.getSelectedItem();
        if (active == null) return;

        promptArea.setText("");
        sendButton.setEnabled(false);
        cycleCount++;
        append("\n\u2500\u2500\u2500 Cycle " + cycleCount + " \u2500\u2500\u2500\n",
                new Color(100, 100, 100), true);
        append("[" + active.name() + "] ", new Color(0, 120, 0), true);
        append(text + "\n\n", Color.BLACK, false);

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                maestro.runDebate(text, active);
                return null;
            }
            @Override protected void done() {
                sendButton.setEnabled(true);
                try { get(); } catch (InterruptedException | ExecutionException ex) {
                    append("[ERROR] " + ex.getMessage() + "\n", Color.RED, false);
                }
            }
        }.execute();
    }

    private void onStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }

    private void onPhase1(int slot, String model, String perspective, String response) {
        SwingUtilities.invokeLater(() -> {
            append("[" + model + " \u2014 " + perspective + "]\n", new Color(30, 80, 180), true);
            append(response + "\n\n", Color.BLACK, false);
        });
    }

    private void onPhase2(int slot, int round, String model, String perspective, String reaction) {
        SwingUtilities.invokeLater(() -> {
            append("[" + model + " reacting, round " + round + "]\n", new Color(140, 80, 0), true);
            append(reaction + "\n\n", Color.BLACK, false);
        });
    }

    private void onSynthesis(String synthesis) {
        SwingUtilities.invokeLater(() -> {
            append("\n=== Synthesis ===\n", new Color(100, 30, 130), true);
            append(synthesis + "\n", Color.BLACK, false);
        });
    }

    private void append(String text, Color color, boolean bold) {
        var doc = transcript.getStyledDocument();
        var style = transcript.addStyle(null, null);
        javax.swing.text.StyleConstants.setForeground(style, color);
        javax.swing.text.StyleConstants.setBold(style, bold);
        try {
            doc.insertString(doc.getLength(), text, style);
            transcript.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    private void showOrgContextDialog() {
        JDialog dlg = new JDialog(this, "Organization Context", true);
        dlg.setLayout(new BorderLayout(8, 8));

        OrganizationContext org = ctxController.organization();
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        java.util.Map<String, JTextArea> inputs = new java.util.LinkedHashMap<>();
        int row = 0;
        for (var entry : OrganizationContext.defaultFields().entrySet()) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
            form.add(new JLabel(entry.getValue() + ":"), gbc);
            JTextArea ta = new JTextArea(org.get(entry.getKey()), 2, 40);
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            inputs.put(entry.getKey(), ta);
            gbc.gridx = 1; gbc.weightx = 1;
            form.add(new JScrollPane(ta), gbc);
            row++;
        }

        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            for (var ie : inputs.entrySet()) {
                org.set(ie.getKey(), ie.getValue().getText().trim());
            }
            org.save();
            dlg.dispose();
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dlg.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(save);

        dlg.add(new JScrollPane(form), BorderLayout.CENTER);
        dlg.add(buttons, BorderLayout.SOUTH);
        dlg.setSize(700, 500);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }
}
