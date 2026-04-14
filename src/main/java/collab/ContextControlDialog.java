package collab;

// ============================================================
// ContextControlDialog.java — 5-tab dialog for granular control
// over every context layer sent to the AI models.
//
// WHAT THIS CLASS DOES (one sentence):
// Shows a tabbed dialog where users can toggle, edit, and preview
// every layer of the Context Layering Architecture.
// ============================================================

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ContextControlDialog extends JDialog {

    private final ContextController controller;
    private final ConversationContext context;
    private final ProfileSet activeProfileSet;
    /**
     * Editable Phase 2 / Phase 3 instruction blocks. Mutated in-place by
     * the Prompt Templates tab; persistence is deferred to onTemplateSaved.
     */
    private final PromptTemplate activeTemplate;
    /**
     * Invoked when the dialog mutates the active ProfileSet (team context
     * edits). MainGui uses this to persist the ProfileSet via ProfileLibrary
     * and rebuild Maestro so the changes take effect immediately.
     */
    private final Runnable onProfileSetSaved;
    /**
     * Opens the full Profile Set editor. Supplied by MainGui so this dialog
     * doesn't need to know about ProfileLibrary or Maestro rebuilds.
     */
    private final Runnable onEditProfileRequested;
    /**
     * Invoked when the user saves edits to the Prompt Templates tab.
     * MainGui persists via PromptTemplateLibrary and rebuilds Maestro.
     */
    private final Runnable onTemplateSaved;
    /**
     * Append-only log of every request payload sent to an LLM provider.
     * Nullable so older callers that don't wire auditing still work. When
     * non-null, the "View Sent Payloads" button opens an ApiRequestViewerDialog
     * backed by this log.
     */
    private final ApiRequestLog apiRequestLog;

    public ContextControlDialog(Frame owner, ContextController controller,
                                ConversationContext context, ProfileSet activeProfileSet,
                                PromptTemplate activeTemplate,
                                Runnable onProfileSetSaved, Runnable onEditProfileRequested,
                                Runnable onTemplateSaved) {
        this(owner, controller, context, activeProfileSet, activeTemplate,
                onProfileSetSaved, onEditProfileRequested, onTemplateSaved, null);
    }

    public ContextControlDialog(Frame owner, ContextController controller,
                                ConversationContext context, ProfileSet activeProfileSet,
                                PromptTemplate activeTemplate,
                                Runnable onProfileSetSaved, Runnable onEditProfileRequested,
                                Runnable onTemplateSaved,
                                ApiRequestLog apiRequestLog) {
        super(owner, "Context Control", true);
        this.controller = controller;
        this.context = context;
        this.activeProfileSet = activeProfileSet;
        this.activeTemplate = activeTemplate;
        this.onProfileSetSaved = onProfileSetSaved;
        this.onEditProfileRequested = onEditProfileRequested;
        this.onTemplateSaved = onTemplateSaved;
        this.apiRequestLog = apiRequestLog;

        setSize(700, 500);
        setLocationRelativeTo(owner);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Profile", buildProfileTab());
        tabs.addTab("Stakeholders", buildStakeholdersTab());
        tabs.addTab("Organization", buildOrgContextTab());
        tabs.addTab("Task Context", buildTaskContextTab());
        tabs.addTab("History", buildHistoryTab());
        tabs.addTab("Prompt Templates", buildPromptTemplatesTab());
        tabs.addTab("Prompt Preview", buildPreviewTab());

        add(tabs, BorderLayout.CENTER);

        // Preset buttons at bottom
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(new JLabel("Presets:"));
        JButton fullBtn = new JButton("Full Context");
        fullBtn.addActionListener(e -> applyPreset(ContextPreset.fullContext()));
        JButton minBtn = new JButton("Minimal");
        minBtn.addActionListener(e -> applyPreset(ContextPreset.minimal()));
        JButton noHistBtn = new JButton("No History");
        noHistBtn.addActionListener(e -> applyPreset(ContextPreset.noHistory()));
        JButton viewPayloadsBtn = new JButton("View Sent Payloads");
        viewPayloadsBtn.setToolTipText("Open the audit viewer for every LLM request payload "
                + "sent this session (system instruction, messages, max tokens).");
        viewPayloadsBtn.addActionListener(e -> {
            ApiRequestLog log = apiRequestLog != null ? apiRequestLog : new ApiRequestLog();
            ApiRequestViewerDialog viewer = new ApiRequestViewerDialog(this, log);
            viewer.setVisible(true);
        });
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        bottomPanel.add(fullBtn);
        bottomPanel.add(minBtn);
        bottomPanel.add(noHistBtn);
        bottomPanel.add(Box.createHorizontalGlue());
        bottomPanel.add(viewPayloadsBtn);
        bottomPanel.add(closeBtn);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Single consolidated tab for the identity layer of context:
     * team context (persisted to ProfileSet), agent-identity toggle +
     * per-agent include checkboxes, stakeholder toggle, and a link to
     * the full Profile Set editor. Full CRUD lives in
     * ProfileSetEditorDialog, not here.
     */
    private JPanel buildProfileTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Header: profile set name + Edit button
        JPanel header = new JPanel(new BorderLayout(8, 0));
        String headerText = activeProfileSet != null
                ? "Profile set: " + activeProfileSet.getName()
                : "Profile set: (none)";
        JLabel headerLabel = new JLabel(headerText);
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD));
        header.add(headerLabel, BorderLayout.WEST);

        JButton editBtn = new JButton("Edit Profile...");
        editBtn.setToolTipText("Open the full Profile Set editor to add/remove agents and stakeholders.");
        editBtn.addActionListener(e -> {
            if (onEditProfileRequested != null) {
                dispose();
                onEditProfileRequested.run();
            }
        });
        header.add(editBtn, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        // Center: team context editor + toggle lists
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        // --- Team context section ---
        JPanel teamSection = new JPanel(new BorderLayout(4, 4));
        teamSection.setBorder(BorderFactory.createTitledBorder("Team Context"));

        JCheckBox teamToggle = new JCheckBox("Include team context in prompts",
                controller.shouldIncludeTeamContext());
        teamToggle.addActionListener(e -> controller.setIncludeTeamContext(teamToggle.isSelected()));
        teamSection.add(teamToggle, BorderLayout.NORTH);

        String teamText = (activeProfileSet != null && activeProfileSet.getTeamContext() != null)
                ? activeProfileSet.getTeamContext()
                : PromptBuilder.DEFAULT_TEAM_CONTEXT;
        JTextArea teamArea = new JTextArea(teamText, 6, 40);
        teamArea.setLineWrap(true);
        teamArea.setWrapStyleWord(true);
        teamSection.add(new JScrollPane(teamArea), BorderLayout.CENTER);

        JPanel teamButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton saveTeamBtn = new JButton("Save to Profile");
        saveTeamBtn.setToolTipText("Persists this team context to the active profile set on disk.");
        saveTeamBtn.addActionListener(e -> {
            if (activeProfileSet == null) return;
            activeProfileSet.setTeamContext(teamArea.getText());
            if (onProfileSetSaved != null) {
                onProfileSetSaved.run();
            }
        });
        JButton resetTeamBtn = new JButton("Reset to Built-in Default");
        resetTeamBtn.addActionListener(e -> teamArea.setText(PromptBuilder.DEFAULT_TEAM_CONTEXT));
        teamButtons.add(saveTeamBtn);
        teamButtons.add(resetTeamBtn);
        teamSection.add(teamButtons, BorderLayout.SOUTH);
        center.add(teamSection);

        // --- Agents section (toggles only, no editing) ---
        JPanel agentsSection = new JPanel();
        agentsSection.setLayout(new BoxLayout(agentsSection, BoxLayout.Y_AXIS));
        agentsSection.setBorder(BorderFactory.createTitledBorder("Agent Identities"));

        JCheckBox agentMaster = new JCheckBox("Include agent identities in prompts",
                controller.shouldIncludeAgentIdentity());
        agentMaster.addActionListener(e ->
                controller.setIncludeAgentIdentity(agentMaster.isSelected()));
        agentMaster.setAlignmentX(LEFT_ALIGNMENT);
        agentsSection.add(agentMaster);

        if (activeProfileSet != null && activeProfileSet.getAgents() != null) {
            for (AgentProfile agent : activeProfileSet.getAgents()) {
                JCheckBox agentBox = new JCheckBox(
                        agent.getName() + " \u00b7 " + agent.getPerspective(),
                        controller.shouldIncludeAgent(agent.getName()));
                agentBox.setAlignmentX(LEFT_ALIGNMENT);
                agentBox.addActionListener(e ->
                        controller.setAgentToggle(agent.getName(), agentBox.isSelected()));
                agentsSection.add(agentBox);
            }
        }
        center.add(agentsSection);

        // --- Stakeholders section (toggle only) ---
        JPanel stakeholdersSection = new JPanel();
        stakeholdersSection.setLayout(new BoxLayout(stakeholdersSection, BoxLayout.Y_AXIS));
        stakeholdersSection.setBorder(BorderFactory.createTitledBorder("Stakeholders"));

        JCheckBox stakeholderToggle = new JCheckBox("Include stakeholder context in prompts",
                controller.shouldIncludeStakeholderProfile());
        stakeholderToggle.addActionListener(e ->
                controller.setIncludeStakeholderProfile(stakeholderToggle.isSelected()));
        stakeholderToggle.setAlignmentX(LEFT_ALIGNMENT);
        stakeholdersSection.add(stakeholderToggle);

        if (activeProfileSet != null && activeProfileSet.getStakeholders() != null) {
            for (StakeholderProfile sp : activeProfileSet.getStakeholders()) {
                JLabel row = new JLabel("  \u2022 " + sp.getName() + " \u2014 " + sp.getRole());
                row.setAlignmentX(LEFT_ALIGNMENT);
                stakeholdersSection.add(row);
            }
        }
        JLabel stakeholdersHint = new JLabel(
                "<html><i>Add, edit, or remove stakeholders in the Stakeholders tab.</i></html>");
        stakeholdersHint.setAlignmentX(LEFT_ALIGNMENT);
        stakeholdersSection.add(stakeholdersHint);
        center.add(stakeholdersSection);

        panel.add(new JScrollPane(center), BorderLayout.CENTER);
        return panel;
    }

    // ============================================================
    // Stakeholders tab — full CRUD over the active ProfileSet's
    // stakeholder list. Each stakeholder is the "who's asking" context
    // injected by PromptBuilder.buildSystemInstruction / Phase 1. The
    // tab mirrors the Organization member-profiles pattern: a list on
    // the left with Add/Remove buttons and a detail form on the right
    // editing all seven StakeholderProfile fields. "Save" writes the
    // edits into the active ProfileSet and triggers onProfileSetSaved,
    // which persists via ProfileLibrary and rebuilds Maestro.
    // ============================================================
    private JPanel buildStakeholdersTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        if (activeProfileSet == null) {
            panel.add(new JLabel("No active profile set — stakeholders cannot be edited."),
                    BorderLayout.CENTER);
            return panel;
        }

        JCheckBox toggle = new JCheckBox("Include stakeholder context in prompts",
                controller.shouldIncludeStakeholderProfile());
        toggle.addActionListener(e ->
                controller.setIncludeStakeholderProfile(toggle.isSelected()));
        panel.add(toggle, BorderLayout.NORTH);

        // Working copy we mutate locally; committed to the ProfileSet on Save.
        java.util.List<StakeholderProfile> working = new java.util.ArrayList<>();
        if (activeProfileSet.getStakeholders() != null) {
            working.addAll(activeProfileSet.getStakeholders());
        }

        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (StakeholderProfile sp : working) {
            listModel.addElement(renderStakeholderLabel(sp));
        }
        JList<String> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(8);

        // Detail form (right side).
        JTextField nameField = new JTextField(24);
        JTextField roleField = new JTextField(24);
        JTextField focusField = new JTextField(24);
        JTextField worksWithField = new JTextField(24);
        JTextArea responsibilitiesArea = new JTextArea(2, 24);
        JTextArea evaluatedOnArea = new JTextArea(2, 24);
        JTextArea backgroundArea = new JTextArea(3, 24);
        for (JTextArea a : java.util.List.of(responsibilitiesArea, evaluatedOnArea, backgroundArea)) {
            a.setLineWrap(true);
            a.setWrapStyleWord(true);
        }

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Stakeholder details"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int r = 0;
        addFormRow(form, gbc, r++, "Name",             nameField);
        addFormRow(form, gbc, r++, "Title / Role",     roleField);
        addFormRow(form, gbc, r++, "Department",       focusField);
        addFormRow(form, gbc, r++, "Reports to",       worksWithField);
        addFormRow(form, gbc, r++, "Decision authority", new JScrollPane(responsibilitiesArea));
        addFormRow(form, gbc, r++, "KPIs",             new JScrollPane(evaluatedOnArea));
        addFormRow(form, gbc, r++, "Background",       new JScrollPane(backgroundArea));

        // Track which list row the form corresponds to. -1 means nothing selected.
        int[] editingIndex = {-1};
        Runnable clearForm = () -> {
            nameField.setText("");
            roleField.setText("");
            focusField.setText("");
            worksWithField.setText("");
            responsibilitiesArea.setText("");
            evaluatedOnArea.setText("");
            backgroundArea.setText("");
        };
        java.util.function.IntConsumer loadIndex = idx -> {
            if (idx < 0 || idx >= working.size()) {
                editingIndex[0] = -1;
                clearForm.run();
                return;
            }
            editingIndex[0] = idx;
            StakeholderProfile sp = working.get(idx);
            nameField.setText(nullToEmpty(sp.getName()));
            roleField.setText(nullToEmpty(sp.getRole()));
            focusField.setText(nullToEmpty(sp.getFocusArea()));
            worksWithField.setText(nullToEmpty(sp.getWorksWith()));
            responsibilitiesArea.setText(nullToEmpty(sp.getResponsibilities()));
            evaluatedOnArea.setText(nullToEmpty(sp.getEvaluatedOn()));
            backgroundArea.setText(nullToEmpty(sp.getBackground()));
        };
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadIndex.accept(list.getSelectedIndex());
            }
        });

        // List controls
        JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton addBtn = new JButton("Add");
        addBtn.addActionListener(e -> {
            StakeholderProfile fresh = new StakeholderProfile(
                    "New Stakeholder", "", "", "", "", "", "");
            working.add(fresh);
            listModel.addElement(renderStakeholderLabel(fresh));
            list.setSelectedIndex(working.size() - 1);
        });
        JButton removeBtn = new JButton("Remove");
        removeBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0) return;
            if (working.size() <= 1) {
                JOptionPane.showMessageDialog(panel,
                        "At least one stakeholder is required so hotseat selection still works.",
                        "Can't remove", JOptionPane.WARNING_MESSAGE);
                return;
            }
            working.remove(idx);
            listModel.remove(idx);
            editingIndex[0] = -1;
            clearForm.run();
        });
        listButtons.add(addBtn);
        listButtons.add(removeBtn);

        JPanel listPanel = new JPanel(new BorderLayout(4, 4));
        listPanel.setBorder(BorderFactory.createTitledBorder("Stakeholders"));
        listPanel.add(new JScrollPane(list), BorderLayout.CENTER);
        listPanel.add(listButtons, BorderLayout.SOUTH);

        // Form-side controls
        JPanel formButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton updateBtn = new JButton("Update Selected");
        updateBtn.setToolTipText("Copies the form values back into the selected stakeholder (in memory).");
        updateBtn.addActionListener(e -> {
            int idx = editingIndex[0];
            if (idx < 0 || idx >= working.size()) {
                JOptionPane.showMessageDialog(panel,
                        "Select a stakeholder from the list (or click Add) first.",
                        "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(panel,
                        "Stakeholder name is required.",
                        "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            StakeholderProfile updated = new StakeholderProfile(
                    name,
                    roleField.getText().trim(),
                    focusField.getText().trim(),
                    worksWithField.getText().trim(),
                    responsibilitiesArea.getText().trim(),
                    evaluatedOnArea.getText().trim(),
                    backgroundArea.getText().trim());
            working.set(idx, updated);
            listModel.set(idx, renderStakeholderLabel(updated));
        });
        formButtons.add(updateBtn);

        JPanel formWrap = new JPanel(new BorderLayout(4, 4));
        formWrap.add(new JScrollPane(form), BorderLayout.CENTER);
        formWrap.add(formButtons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, formWrap);
        split.setResizeWeight(0.35);
        panel.add(split, BorderLayout.CENTER);

        // Initialise form with the first entry if present.
        if (!working.isEmpty()) {
            list.setSelectedIndex(0);
        }

        // Commit: write working list back to the ProfileSet and fire the save callback.
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save Stakeholders");
        saveBtn.setToolTipText("Persists the stakeholder list to the active profile set and rebuilds the debate engine.");
        saveBtn.addActionListener(e -> {
            // Auto-commit any pending form edits to the currently-selected row.
            int idx = editingIndex[0];
            if (idx >= 0 && idx < working.size()) {
                String name = nameField.getText().trim();
                if (!name.isEmpty()) {
                    StakeholderProfile updated = new StakeholderProfile(
                            name,
                            roleField.getText().trim(),
                            focusField.getText().trim(),
                            worksWithField.getText().trim(),
                            responsibilitiesArea.getText().trim(),
                            evaluatedOnArea.getText().trim(),
                            backgroundArea.getText().trim());
                    working.set(idx, updated);
                    listModel.set(idx, renderStakeholderLabel(updated));
                }
            }
            if (working.isEmpty()) {
                JOptionPane.showMessageDialog(panel,
                        "At least one stakeholder is required.",
                        "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            activeProfileSet.setStakeholders(new java.util.ArrayList<>(working));
            if (onProfileSetSaved != null) {
                onProfileSetSaved.run();
            }
            JOptionPane.showMessageDialog(panel,
                    "Stakeholders saved.",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        });
        bottom.add(saveBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private static String renderStakeholderLabel(StakeholderProfile sp) {
        String n = sp.getName() == null || sp.getName().isBlank() ? "(unnamed)" : sp.getName();
        String r = sp.getRole() == null || sp.getRole().isBlank() ? "" : " \u2014 " + sp.getRole();
        return n + r;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static void addFormRow(JPanel form, GridBagConstraints gbc, int row,
                                   String label, Component field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(label + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(field, gbc);
    }

    private JPanel buildOrgContextTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        OrganizationContext orgCtx = controller.getOrganizationContext();

        JCheckBox toggle = new JCheckBox("Include organization context in prompts",
                controller.shouldIncludeOrgContext());
        toggle.addActionListener(e -> controller.setIncludeOrgContext(toggle.isSelected()));
        panel.add(toggle, BorderLayout.NORTH);

        // Scrollable form of all org context fields
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // NOTE: roster + stakeholders intentionally omitted here — they live
        // in memberProfiles (roster) and the active ProfileSet (stakeholders).
        int row = 0;
        String[][] fields = {
            {"Last Updated", orgCtx.getLastUpdated()},
            {"What Changed Since Last Update", orgCtx.getWhatChangedSinceLastUpdate()},
            {"Current Term / Date Range", orgCtx.getCurrentTermDateRange()},
            {"Top Priorities", orgCtx.getTopPriorities()},
            {"Active Initiatives and Status", orgCtx.getActiveInitiativesAndStatus()},
            {"Upcoming Deadlines and Events", orgCtx.getUpcomingDeadlinesAndEvents()},
            {"Current Metrics", orgCtx.getCurrentMetrics()},
            {"Current Blockers / Risks", orgCtx.getCurrentBlockersRisks()},
            {"Pending Decisions", orgCtx.getPendingDecisions()},
            {"Preferred Tone / Style", orgCtx.getPreferredToneStyle()},
        };

        java.util.List<JTextArea> fieldAreas = new java.util.ArrayList<>();
        for (String[] f : fields) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
            form.add(new JLabel(f[0] + ":"), gbc);
            JTextArea area = new JTextArea(f[1] != null ? f[1] : "", 2, 30);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            fieldAreas.add(area);
            gbc.gridx = 1; gbc.weightx = 1;
            form.add(new JScrollPane(area), gbc);
            row++;
        }

        // Member profiles section
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.weightx = 1;
        JPanel profilesPanel = new JPanel(new BorderLayout(4, 4));
        profilesPanel.setBorder(BorderFactory.createTitledBorder("Member/Officer Profiles"));
        DefaultListModel<String> profileModel = new DefaultListModel<>();
        if (orgCtx.getMemberProfiles() != null) {
            for (OrganizationContext.MemberProfile mp : orgCtx.getMemberProfiles()) {
                profileModel.addElement(mp.getName() + " - " + mp.getRole());
            }
        }
        JList<String> profileList = new JList<>(profileModel);
        profileList.setVisibleRowCount(4);
        profilesPanel.add(new JScrollPane(profileList), BorderLayout.CENTER);

        JPanel profileBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton addProfile = new JButton("Add");
        addProfile.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(panel, "Member name:");
            if (name == null || name.isBlank()) return;
            String role = JOptionPane.showInputDialog(panel, "Role/Title:");
            String details = JOptionPane.showInputDialog(panel, "Details (skills, responsibilities, notes):");
            OrganizationContext.MemberProfile mp = new OrganizationContext.MemberProfile(
                    name.trim(), role != null ? role.trim() : "", details != null ? details.trim() : "");
            orgCtx.addMemberProfile(mp);
            profileModel.addElement(mp.getName() + " - " + mp.getRole());
        });
        JButton removeProfile = new JButton("Remove");
        removeProfile.addActionListener(e -> {
            int idx = profileList.getSelectedIndex();
            if (idx >= 0) {
                orgCtx.removeMemberProfile(idx);
                profileModel.remove(idx);
            }
        });
        profileBtns.add(addProfile);
        profileBtns.add(removeProfile);
        profilesPanel.add(profileBtns, BorderLayout.SOUTH);
        form.add(profilesPanel, gbc);

        JScrollPane formScroll = new JScrollPane(form);
        panel.add(formScroll, BorderLayout.CENTER);

        // Save button
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save Organization Context");
        saveBtn.addActionListener(e -> {
            orgCtx.setLastUpdated(fieldAreas.get(0).getText().trim());
            orgCtx.setWhatChangedSinceLastUpdate(fieldAreas.get(1).getText().trim());
            orgCtx.setCurrentTermDateRange(fieldAreas.get(2).getText().trim());
            orgCtx.setTopPriorities(fieldAreas.get(3).getText().trim());
            orgCtx.setActiveInitiativesAndStatus(fieldAreas.get(4).getText().trim());
            orgCtx.setUpcomingDeadlinesAndEvents(fieldAreas.get(5).getText().trim());
            orgCtx.setCurrentMetrics(fieldAreas.get(6).getText().trim());
            orgCtx.setCurrentBlockersRisks(fieldAreas.get(7).getText().trim());
            orgCtx.setPendingDecisions(fieldAreas.get(8).getText().trim());
            orgCtx.setPreferredToneStyle(fieldAreas.get(9).getText().trim());
            controller.saveOrganizationContext();
            JOptionPane.showMessageDialog(panel, "Organization context saved.",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        });
        bottom.add(saveBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildTaskContextTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JCheckBox toggle = new JCheckBox("Include task context in prompts",
                controller.shouldIncludeTaskContext());
        toggle.addActionListener(e -> controller.setIncludeTaskContext(toggle.isSelected()));
        panel.add(toggle, BorderLayout.NORTH);

        TaskContext activeTask = controller.getActiveTaskContext();

        JPanel fieldsPanel = new JPanel(new GridLayout(0, 1, 4, 4));

        // Task name
        JPanel namePanel = new JPanel(new BorderLayout());
        namePanel.setBorder(BorderFactory.createTitledBorder("Active Task"));
        JLabel taskLabel = new JLabel(activeTask != null ? activeTask.getTaskName() : "(No task selected)");
        taskLabel.setFont(taskLabel.getFont().deriveFont(Font.BOLD, 13f));
        namePanel.add(taskLabel, BorderLayout.CENTER);
        fieldsPanel.add(namePanel);

        // Prompt template
        JPanel templatePanel = new JPanel(new BorderLayout());
        templatePanel.setBorder(BorderFactory.createTitledBorder("Prompt Template"));
        JTextArea templateArea = new JTextArea(
                activeTask != null && activeTask.getPromptTemplate() != null
                        ? activeTask.getPromptTemplate() : "(none)");
        templateArea.setEditable(false);
        templateArea.setLineWrap(true);
        templateArea.setWrapStyleWord(true);
        templateArea.setRows(3);
        templatePanel.add(new JScrollPane(templateArea), BorderLayout.CENTER);
        fieldsPanel.add(templatePanel);

        // Style instructions
        JPanel stylePanel = new JPanel(new BorderLayout());
        stylePanel.setBorder(BorderFactory.createTitledBorder("Style & Tone"));
        JTextArea styleArea = new JTextArea(
                activeTask != null && activeTask.getStyleInstructions() != null
                        ? activeTask.getStyleInstructions() : "(none)");
        styleArea.setEditable(false);
        styleArea.setLineWrap(true);
        styleArea.setWrapStyleWord(true);
        styleArea.setRows(2);
        stylePanel.add(new JScrollPane(styleArea), BorderLayout.CENTER);
        fieldsPanel.add(stylePanel);

        // Follow-up answers
        JPanel answersPanel = new JPanel(new BorderLayout());
        answersPanel.setBorder(BorderFactory.createTitledBorder("Follow-Up Answers"));
        StringBuilder answerText = new StringBuilder();
        if (activeTask != null && !activeTask.getFollowUpAnswers().isEmpty()) {
            for (var entry : activeTask.getFollowUpAnswers().entrySet()) {
                answerText.append("Q: ").append(entry.getKey()).append("\n");
                answerText.append("A: ").append(entry.getValue()).append("\n\n");
            }
        } else {
            answerText.append("(no answers collected yet)");
        }
        JTextArea answersArea = new JTextArea(answerText.toString());
        answersArea.setEditable(false);
        answersArea.setLineWrap(true);
        answersArea.setWrapStyleWord(true);
        answersArea.setRows(4);
        answersPanel.add(new JScrollPane(answersArea), BorderLayout.CENTER);
        fieldsPanel.add(answersPanel);

        // Mode indicator
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.setBorder(BorderFactory.createTitledBorder("Execution Mode"));
        String modeText = activeTask != null
                ? (activeTask.isSimpleMode() ? "Simple (single API call)" : "Multi-prompt (full debate cycle)")
                : "(no task selected)";
        modePanel.add(new JLabel(modeText));
        fieldsPanel.add(modePanel);

        panel.add(new JScrollPane(fieldsPanel), BorderLayout.CENTER);

        // Preview assembled task block
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton previewBtn = new JButton("Preview Task Block");
        previewBtn.addActionListener(e -> {
            if (activeTask != null) {
                JOptionPane.showMessageDialog(this,
                        activeTask.buildTaskBlock(),
                        "Task Context Block", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "No active task context.",
                        "Task Context Block", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        JButton clearBtn = new JButton("Clear Task");
        clearBtn.addActionListener(e -> {
            controller.setActiveTaskContext(null);
            taskLabel.setText("(No task selected)");
            templateArea.setText("(none)");
            styleArea.setText("(none)");
            answersArea.setText("(no answers collected yet)");
        });
        bottomPanel.add(previewBtn);
        bottomPanel.add(clearBtn);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JCheckBox toggle = new JCheckBox("Include conversation history in prompts",
                controller.shouldIncludeHistory());
        toggle.addActionListener(e -> controller.setIncludeHistory(toggle.isSelected()));

        String historyText = context.getHistoryBlock();
        int charCount = historyText.length();
        int estTokens = charCount / 4;
        JLabel stats = new JLabel("  Characters: " + charCount + "  |  Est. tokens: ~" + estTokens);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(toggle, BorderLayout.WEST);
        topPanel.add(stats, BorderLayout.EAST);
        panel.add(topPanel, BorderLayout.NORTH);

        JTextArea historyArea = new JTextArea(historyText.isEmpty()
                ? "(No conversation history yet)" : historyText);
        historyArea.setEditable(false);
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);
        panel.add(new JScrollPane(historyArea), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton clearBtn = new JButton("Clear History");
        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Clear all conversation history?", "Confirm Clear",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                context.clear();
                historyArea.setText("(No conversation history yet)");
                stats.setText("  Characters: 0  |  Est. tokens: ~0");
            }
        });
        buttons.add(clearBtn);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildPreviewTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel info = new JLabel("Shows what the assembled prompt looks like for each model.");
        panel.add(info, BorderLayout.NORTH);

        JTextArea previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        panel.add(new JScrollPane(previewArea), BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<String> modelCombo = new JComboBox<>(new String[]{"Claude", "GPT", "Gemini"});
        JButton refreshBtn = new JButton("Refresh Preview");
        refreshBtn.addActionListener(e -> {
            StringBuilder sb = new StringBuilder();
            String profileTeam = (activeProfileSet != null && activeProfileSet.getTeamContext() != null)
                    ? activeProfileSet.getTeamContext()
                    : PromptBuilder.DEFAULT_TEAM_CONTEXT;
            String teamCtx = controller.getEffectiveTeamContext(profileTeam);
            sb.append("=== TEAM CONTEXT ===\n");
            sb.append(teamCtx.isEmpty() ? "(disabled)\n" : teamCtx);
            sb.append("\n");

            String modelName = (String) modelCombo.getSelectedItem();
            sb.append("=== AGENT IDENTITY ===\n");
            if (activeProfileSet != null && activeProfileSet.getAgents() != null) {
                int idx = modelName.equals("Claude") ? 0 : modelName.equals("GPT") ? 1 : 2;
                if (idx < activeProfileSet.getAgents().size()) {
                    AgentProfile agent = activeProfileSet.getAgents().get(idx);
                    if (controller.shouldIncludeAgent(agent.getName())) {
                        sb.append(agent.toBriefing());
                    } else {
                        sb.append("(disabled for ").append(modelName).append(")\n");
                    }
                }
            }
            sb.append("\n");

            sb.append("=== STAKEHOLDER ===\n");
            if (controller.shouldIncludeStakeholderProfile()
                    && activeProfileSet != null
                    && activeProfileSet.getStakeholders() != null
                    && !activeProfileSet.getStakeholders().isEmpty()) {
                sb.append(activeProfileSet.getStakeholders().get(0).toBriefing());
            } else {
                sb.append("(disabled)\n");
            }
            sb.append("\n");

            sb.append("=== ORGANIZATION CONTEXT ===\n");
            if (controller.shouldIncludeOrgContext()) {
                sb.append(controller.getEffectiveOrgContext());
            } else {
                sb.append("(disabled)\n");
            }
            sb.append("\n");

            sb.append("=== TASK CONTEXT ===\n");
            if (controller.shouldIncludeTaskContext() && controller.getActiveTaskContext() != null) {
                sb.append(controller.getActiveTaskContext().buildTaskBlock());
            } else {
                sb.append("(disabled or no active task)\n");
            }
            sb.append("\n");

            sb.append("=== HISTORY ===\n");
            if (controller.shouldIncludeHistory()) {
                String hist = context.getHistoryBlock();
                sb.append(hist.isEmpty() ? "(no history)\n" : hist);
            } else {
                sb.append("(disabled)\n");
            }
            sb.append("\n");

            // Current in-memory template text, so users can confirm how
            // edits on the Prompt Templates tab will land in real prompts.
            sb.append("=== PHASE 2 REACTION INSTRUCTIONS (in-memory) ===\n");
            if (activeTemplate != null) {
                sb.append("-- Preamble --\n");
                sb.append(activeTemplate.getReactionPreamble()).append("\n");
                sb.append("-- Task --\n");
                sb.append(activeTemplate.getReactionTask()).append("\n");
            } else {
                sb.append("(no template loaded)\n");
            }
            sb.append("\n");

            sb.append("=== PHASE 3 SYNTHESIS INSTRUCTIONS (in-memory) ===\n");
            if (activeTemplate != null) {
                sb.append("-- Preamble --\n");
                sb.append(activeTemplate.getSynthesisPreamble()).append("\n");
                sb.append("-- Task --\n");
                sb.append(activeTemplate.getSynthesisTask()).append("\n");
            } else {
                sb.append("(no template loaded)\n");
            }

            previewArea.setText(sb.toString());
            previewArea.setCaretPosition(0);
        });

        JButton copyBtn = new JButton("Copy");
        copyBtn.addActionListener(e -> {
            previewArea.selectAll();
            previewArea.copy();
            previewArea.setCaretPosition(0);
        });

        controls.add(new JLabel("Model:"));
        controls.add(modelCombo);
        controls.add(refreshBtn);
        controls.add(copyBtn);
        panel.add(controls, BorderLayout.SOUTH);

        return panel;
    }

    private void applyPreset(ContextPreset preset) {
        preset.applyTo(controller);
        dispose();
        // Re-open to reflect changes
        new ContextControlDialog(
                (Frame) getOwner(), controller, context, activeProfileSet,
                activeTemplate,
                onProfileSetSaved, onEditProfileRequested, onTemplateSaved
        ).setVisible(true);
    }

    // ============================================================
    // Prompt Templates tab — lets users edit the "instructions-only"
    // portions of the Phase 2 reaction prompt and the Phase 3
    // synthesis prompt. The surrounding data plumbing (original
    // question, peer responses, phase markers) stays hardcoded.
    // ============================================================
    private JPanel buildPromptTemplatesTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        if (activeTemplate == null) {
            panel.add(new JLabel("Prompt templates are unavailable (no active template loaded)."),
                    BorderLayout.CENTER);
            return panel;
        }

        JLabel info = new JLabel(
                "<html>Edit the instructional text injected into Phase 2 (reaction) "
              + "and Phase 3 (synthesis) prompts. The original question, peer "
              + "responses, and phase markers are added by the system and cannot "
              + "be edited here.</html>");
        panel.add(info, BorderLayout.NORTH);

        // Four editable text areas, each with its own "Reset" button,
        // organised as a vertical stack of titled sections.
        JTextArea reactionPreambleArea = makeTemplateArea(activeTemplate.getReactionPreamble());
        JTextArea reactionTaskArea = makeTemplateArea(activeTemplate.getReactionTask());
        JTextArea synthesisPreambleArea = makeTemplateArea(activeTemplate.getSynthesisPreamble());
        JTextArea synthesisTaskArea = makeTemplateArea(activeTemplate.getSynthesisTask());

        JPanel sections = new JPanel();
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
        sections.add(buildTemplateSection(
                "Phase 2 — Reaction Preamble",
                "Appears just after \"You are <agent>. \" and before the peer responses.",
                reactionPreambleArea,
                PromptTemplate.DEFAULT_REACTION_PREAMBLE));
        sections.add(buildTemplateSection(
                "Phase 2 — Reaction Task",
                "Appears after the peer responses. Drives how each agent reacts.",
                reactionTaskArea,
                PromptTemplate.DEFAULT_REACTION_TASK));
        sections.add(buildTemplateSection(
                "Phase 3 — Synthesis Preamble",
                "Appears before the debate transcript. Panel composition and framing.",
                synthesisPreambleArea,
                PromptTemplate.DEFAULT_SYNTHESIS_PREAMBLE));
        sections.add(buildTemplateSection(
                "Phase 3 — Synthesis Task",
                "Appears after the debate transcript. Drives the final report structure.",
                synthesisTaskArea,
                PromptTemplate.DEFAULT_SYNTHESIS_TASK));

        panel.add(new JScrollPane(sections), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton resetAllBtn = new JButton("Reset All to Defaults");
        resetAllBtn.addActionListener(e -> {
            reactionPreambleArea.setText(PromptTemplate.DEFAULT_REACTION_PREAMBLE);
            reactionTaskArea.setText(PromptTemplate.DEFAULT_REACTION_TASK);
            synthesisPreambleArea.setText(PromptTemplate.DEFAULT_SYNTHESIS_PREAMBLE);
            synthesisTaskArea.setText(PromptTemplate.DEFAULT_SYNTHESIS_TASK);
        });
        JButton applyBtn = new JButton("Apply & Save");
        applyBtn.setToolTipText("Saves templates to disk and rebuilds the debate engine with new prompts.");
        applyBtn.addActionListener(e -> {
            activeTemplate.setReactionPreamble(reactionPreambleArea.getText());
            activeTemplate.setReactionTask(reactionTaskArea.getText());
            activeTemplate.setSynthesisPreamble(synthesisPreambleArea.getText());
            activeTemplate.setSynthesisTask(synthesisTaskArea.getText());
            if (onTemplateSaved != null) {
                onTemplateSaved.run();
            }
            JOptionPane.showMessageDialog(panel, "Prompt templates saved.",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        });
        bottom.add(resetAllBtn);
        bottom.add(applyBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private JTextArea makeTemplateArea(String initial) {
        JTextArea area = new JTextArea(initial, 6, 50);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return area;
    }

    private JPanel buildTemplateSection(String title, String hint,
                                        JTextArea area, String defaultValue) {
        JPanel section = new JPanel(new BorderLayout(4, 4));
        section.setBorder(BorderFactory.createTitledBorder(title));

        JLabel hintLabel = new JLabel("<html><i>" + hint + "</i></html>");
        section.add(hintLabel, BorderLayout.NORTH);
        section.add(new JScrollPane(area), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton resetBtn = new JButton("Reset to Default");
        resetBtn.addActionListener(e -> area.setText(defaultValue));
        buttons.add(resetBtn);
        section.add(buttons, BorderLayout.SOUTH);

        return section;
    }
}
