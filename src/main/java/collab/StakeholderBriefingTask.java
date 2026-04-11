package collab;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class StakeholderBriefingTask implements AgenticTask {

    @Override public String getId()          { return "stakeholder-briefing"; }
    @Override public String getName()        { return "Stakeholder Briefing"; }
    @Override public String getDescription() { return "Generate a briefing tailored to a specific audience"; }
    @Override public String getCategory()    { return "Reports"; }
    @Override public boolean isAvailable()   { return true; }

    @Override
    public void execute(AgenticTaskContext ctx) {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(ctx.panel());

        // Input dialog
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField audienceField = new JTextField("Board of Directors", 25);
        JComboBox<String> scopeCombo = new JComboBox<>(new String[]{
            "Full Overview", "Initiative-Focused", "Financial Summary", "Operational Update"
        });
        JComboBox<String> toneCombo = new JComboBox<>(new String[]{
            "Executive / Formal", "Conversational", "Data-Driven", "Narrative"
        });

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        form.add(new JLabel("Audience:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(audienceField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        form.add(new JLabel("Scope:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(scopeCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        form.add(new JLabel("Tone:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(toneCombo, gbc);

        int result = JOptionPane.showConfirmDialog(owner, form,
            "Stakeholder Briefing", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String audience = audienceField.getText().trim();
        String scope = (String) scopeCombo.getSelectedItem();
        String tone = (String) toneCombo.getSelectedItem();

        ctx.panel().setStatus("Generating stakeholder briefing...");
        ctx.panel().showLoading("Preparing briefing for: " + audience);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                LlmClient client = new AnthropicClient(httpClient, ctx.config().getClaudeUrl(),
                        ctx.config().getClaudeKey(), ctx.config().getClaudeModel(),
                        ctx.config().getMaxResponseTokens());
                return client.sendMessage(buildPrompt(ctx, audience, scope, tone));
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    SwingUtilities.invokeLater(() -> {
                        ctx.panel().showFunctionOutput("Briefing: " + audience, response);
                        ctx.panel().setStatus("Stakeholder briefing complete.");
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        ctx.panel().showFunctionOutput("Error", "Failed: " + e.getMessage());
                        ctx.panel().setStatus("Error: " + e.getMessage());
                    });
                }
            }
        }.execute();
    }

    private String buildPrompt(AgenticTaskContext ctx, String audience, String scope, String tone) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an executive communications specialist preparing a stakeholder briefing.\n\n");
        prompt.append("Audience: ").append(audience).append("\n");
        prompt.append("Scope: ").append(scope).append("\n");
        prompt.append("Tone: ").append(tone).append("\n\n");

        prompt.append("""
            Produce a polished briefing document with:
            1. EXECUTIVE SUMMARY — 2-3 sentence overview
            2. KEY UPDATES — organized by scope area
            3. METRICS & PROGRESS — relevant KPIs and milestones
            4. CHALLENGES & MITIGATION — current risks and how they're being addressed
            5. OUTLOOK — forward-looking priorities and timeline
            6. ASK / CALL TO ACTION — what you need from this audience (if applicable)

            Match the tone and detail level to the specified audience and style.
            Use real data from the organization context provided.

            """);

        prompt.append("=== ORGANIZATION CONTEXT ===\n");
        prompt.append(ctx.orgContext().buildContextBlock());

        return prompt.toString();
    }
}
