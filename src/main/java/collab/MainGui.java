package collab;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Single chat window. Settings menu: Agents... | Context... | Clear.
// Agents are the three panelists; Context is one free-form blob
// prepended to every prompt.
public class MainGui extends JFrame {

    private final Maestro maestro;
    private final JTextArea transcript = new JTextArea();
    private final JTextArea promptArea = new JTextArea(4, 60);
    private final JButton sendButton = new JButton("Send");

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(new FlatLightLaf()); } catch (Exception ignored) {}
            Config cfg;
            try { cfg = new Config("config.properties"); }
            catch (IOException e) { warn(e.getMessage()); return; }
            if (!cfg.hasAllKeys()) { warn("config.properties missing API keys."); return; }
            new MainGui(cfg).setVisible(true);
        });
    }

    private static void warn(String msg) {
        JOptionPane.showMessageDialog(null, msg, "Setup", JOptionPane.WARNING_MESSAGE);
    }

    public MainGui(Config config) {
        super("AI Collaboration Platform");
        this.maestro = Main.buildMaestro(config);
        maestro.setListener((kind, model, perspective, text) -> SwingUtilities.invokeLater(() -> {
            if ("status".equals(kind)) setTitle("AI Collaboration Platform — " + text);
            else {
                transcript.append("\n[" + model + (perspective.isEmpty() ? "" : " — " + perspective) + "]\n");
                transcript.append(text + "\n");
                transcript.setCaretPosition(transcript.getDocument().getLength());
            }
        }));

        transcript.setEditable(false);
        transcript.setLineWrap(true); transcript.setWrapStyleWord(true);
        transcript.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        transcript.setMargin(new Insets(8, 10, 8, 10));

        promptArea.setLineWrap(true); promptArea.setWrapStyleWord(true);
        promptArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        promptArea.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && (e.isControlDown() || e.isMetaDown())) sendButton.doClick();
            }
        });
        sendButton.addActionListener(e -> sendPrompt());

        JScrollPane promptScroll = new JScrollPane(promptArea);
        promptScroll.setBorder(BorderFactory.createTitledBorder("Prompt (Ctrl+Enter to send)"));
        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(new EmptyBorder(0, 8, 8, 8));
        south.add(promptScroll, BorderLayout.CENTER);
        south.add(sendButton, BorderLayout.EAST);

        JMenu menu = new JMenu("Settings");
        menu.add(item("Agents...",  e -> showAgentsDialog()));
        menu.add(item("Context...", e -> showContextDialog()));
        menu.addSeparator();
        menu.add(item("Clear Conversation", e -> transcript.setText("")));
        JMenuBar bar = new JMenuBar(); bar.add(menu);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setJMenuBar(bar);
        add(new JScrollPane(transcript), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setSize(1000, 720);
        setLocationRelativeTo(null);
        transcript.append(maestro.apiCallCount() + " API calls per cycle.\n");
    }

    private static JMenuItem item(String label, java.awt.event.ActionListener a) {
        JMenuItem it = new JMenuItem(label); it.addActionListener(a); return it;
    }

    private void sendPrompt() {
        String text = promptArea.getText().trim();
        if (text.isEmpty()) return;
        promptArea.setText("");
        sendButton.setEnabled(false);
        transcript.append("\n[You]\n" + text + "\n");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() { maestro.runDebate(text); return null; }
            @Override protected void done() { sendButton.setEnabled(true); }
        }.execute();
    }

    // ----- Context dialog: one text area ------------------------------

    private void showContextDialog() {
        JTextArea area = new JTextArea(Config.loadContext(), 20, 60);
        area.setLineWrap(true); area.setWrapStyleWord(true);
        int r = JOptionPane.showConfirmDialog(this, new JScrollPane(area),
                "Context (prepended to every prompt)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r == JOptionPane.OK_OPTION) Config.saveContext(area.getText());
    }

    // ----- Agents dialog: one labelled box per agent ------------------

    private void showAgentsDialog() {
        List<Agent> agents = Agent.loadAll();
        int n = agents.size();
        JTextField[] nf = new JTextField[n], pf = new JTextField[n];
        JTextArea[] lf = new JTextArea[n];
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        for (int i = 0; i < n; i++) {
            Agent a = agents.get(i);
            nf[i] = new JTextField(a.name());
            pf[i] = new JTextField(a.perspective());
            lf[i] = new JTextArea(a.lens(), 3, 40);
            lf[i].setLineWrap(true); lf[i].setWrapStyleWord(true);
            JPanel box = new JPanel(new BorderLayout(4, 4));
            box.setBorder(BorderFactory.createTitledBorder("Agent " + (i + 1)));
            JPanel top = new JPanel(new GridLayout(2, 2, 4, 4));
            top.add(new JLabel("Name:"));        top.add(nf[i]);
            top.add(new JLabel("Perspective:")); top.add(pf[i]);
            box.add(top, BorderLayout.NORTH);
            box.add(new JScrollPane(lf[i]), BorderLayout.CENTER);
            form.add(box);
        }
        int r = JOptionPane.showConfirmDialog(this, new JScrollPane(form), "Agents",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        List<Agent> updated = new ArrayList<>();
        for (int i = 0; i < n; i++) updated.add(new Agent(
                nf[i].getText().trim(), pf[i].getText().trim(), lf[i].getText().trim()));
        Agent.saveAll(updated);
    }
}
