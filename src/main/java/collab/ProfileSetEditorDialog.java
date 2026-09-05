package collab;

// ============================================================
// ProfileSetEditorDialog.java — Editor for a full ProfileSet.
//
// WHAT THIS DIALOG DOES:
// Lets the user name a profile set, set team context, and compose the
// debate panel by adding one row per panelist slot. Every slot picks a
// Provider (Anthropic / OpenAI / Google), a Model, and an AgentProfile
// (from the shared AgentProfileLibrary or newly created inline).
//
// WHY PER-SLOT (vs per-agent):
// The debate panel is no longer hardcoded to three (Claude / GPT /
// Gemini). Any mix of providers is allowed, including duplicates
// (e.g. two Claudes with different profiles). Each slot combines a
// provider+model binding with an agent identity. Saving writes a
// List<PanelistSlot> onto the ProfileSet; the legacy `agents` list is
// mirrored automatically by ProfileSet.setSlots() so CLI paths still work.
//
// VALIDATION:
// Save is blocked until EVERY slot has a non-empty Provider, Model, and
// AgentProfile. New rows added via "+ Add Agent" start empty and must
// be filled before the set can be persisted.
// ============================================================

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ProfileSetEditorDialog extends JDialog {

    // Panel size bounds. MIN=2 guarantees at least one peer for Phase 2
    // cross-reactions. MAX=8 keeps the grid layout (2x4) readable on
    // typical laptop displays.
    private static final int MIN_AGENTS = 2;
    private static final int MAX_AGENTS = 8;

    // Sentinel item in the Agent Profile combo that opens the inline
    // AgentProfileEditorDialog when selected. Not an AgentProfile so
    // String equality works as the selection check.
    private static final String CREATE_NEW_PROFILE_ITEM = "\u2795 Create new agent profile\u2026";

    private final JTextField nameField = new JTextField(24);
    private final JTextField descriptionField = new JTextField(24);
    private final JTextArea teamContextArea = new JTextArea(8, 40);

    private final List<SlotRow> slotRows = new ArrayList<>();
    private JPanel agentsPanel;
    private JButton addAgentBtn;

    private final AgentProfileLibrary profileLib;
    private final Config config;

    private ProfileSet profileSet;

    public ProfileSetEditorDialog(Frame owner, ProfileSet seed) {
        this(owner, seed, null, null);
    }

    public ProfileSetEditorDialog(Frame owner, ProfileSet seed,
                                  AgentProfileLibrary profileLib, Config config) {
        super(owner, "Create Profile Set", true);

        // Fallback so callers that haven't been migrated still work.
        if (profileLib == null) {
            profileLib = new AgentProfileLibrary();
            try {
                profileLib.ensureSeeded();
            } catch (Exception ignored) {
                // If seeding fails we simply show an empty list; the
                // user can still create profiles inline.
            }
        }
        this.profileLib = profileLib;
        this.config = config;

        setLayout(new BorderLayout(8, 8));

        add(buildMainPanel(seed), BorderLayout.CENTER);
        add(buildButtons(seed), BorderLayout.SOUTH);

        pack();

        // Ensure the dialog fits on-screen (clamped to 80% of screen) and never
        // collapses so small that the SOUTH button panel is clipped.
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxW = (int) (screen.width * 0.8);
        int maxH = (int) (screen.height * 0.8);
        int w = Math.min(getWidth(), maxW);
        int h = Math.min(getHeight(), maxH);
        setSize(Math.max(w, 720), Math.max(h, 520));
        setMinimumSize(new Dimension(720, 520));

        setLocationRelativeTo(owner);
    }

    private JComponent buildMainPanel(ProfileSet seed) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        // GridBagLayout gives labels their natural width and lets inputs
        // consume the remaining horizontal space. The old GridLayout(3,2)
        // forced equal columns, leaving a large whitespace gap left of the
        // inputs.
        JPanel meta = new JPanel(new GridBagLayout());
        meta.setBorder(BorderFactory.createTitledBorder("Profile Set Metadata"));

        GridBagConstraints gLabel = new GridBagConstraints();
        gLabel.gridx = 0;
        gLabel.anchor = GridBagConstraints.LINE_START;
        gLabel.fill = GridBagConstraints.NONE;
        gLabel.weightx = 0.0;
        gLabel.insets = new Insets(4, 6, 4, 8);

        GridBagConstraints gInput = new GridBagConstraints();
        gInput.gridx = 1;
        gInput.fill = GridBagConstraints.HORIZONTAL;
        gInput.weightx = 1.0;
        gInput.insets = new Insets(4, 0, 4, 6);

        gLabel.gridy = 0;
        meta.add(new JLabel("Set name"), gLabel);
        gInput.gridy = 0;
        meta.add(nameField, gInput);

        gLabel.gridy = 1;
        meta.add(new JLabel("Description"), gLabel);
        gInput.gridy = 1;
        meta.add(descriptionField, gInput);

        gLabel.gridy = 2;
        gLabel.anchor = GridBagConstraints.FIRST_LINE_START;
        meta.add(new JLabel("Team context"), gLabel);

        teamContextArea.setLineWrap(true);
        teamContextArea.setWrapStyleWord(true);
        JScrollPane teamContextScroll = new JScrollPane(teamContextArea);
        teamContextScroll.setPreferredSize(new Dimension(400, 120));
        gInput.gridy = 2;
        gInput.fill = GridBagConstraints.BOTH;
        gInput.weighty = 0.0;
        meta.add(teamContextScroll, gInput);

        if (seed != null) {
            nameField.setText(seed.getName() == null ? "" : seed.getName());
            descriptionField.setText(seed.getDescription() == null ? "" : seed.getDescription());
            teamContextArea.setText(seed.getTeamContext() == null ? "" : seed.getTeamContext());
        }

        // -------- Agents (slot rows) --------
        agentsPanel = new JPanel();
        agentsPanel.setLayout(new BoxLayout(agentsPanel, BoxLayout.Y_AXIS));
        agentsPanel.setBorder(BorderFactory.createTitledBorder("Panelist Slots"));

        // Build the add-panelist button BEFORE the seed loop — addSlotRow
        // calls relayoutAgentsPanel which references addAgentBtn. The old
        // init-after-loop ordering caused a NullPointerException on open.
        addAgentBtn = new JButton("+ Add Panelist Slot");
        addAgentBtn.setToolTipText("Add one more chat window / panelist to the debate panel.");
        addAgentBtn.setAlignmentX(LEFT_ALIGNMENT);
        addAgentBtn.addActionListener(e -> {
            if (slotRows.size() >= MAX_AGENTS) {
                JOptionPane.showMessageDialog(this,
                        "The panel is capped at " + MAX_AGENTS + " slots.",
                        "Slot limit", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            // Append with empty selections so onSave() validation forces
            // the user to fill provider/model/profile before saving.
            addSlotRow(new PanelistSlot(null, null, null));
            relayoutAgentsPanel();
        });

        // Seed slot rows from the existing set. getSlots() synthesises
        // defaults from legacy `agents` lists so old profile sets still
        // open cleanly.
        List<PanelistSlot> seedSlots = seed != null ? seed.getSlots() : null;
        if (seedSlots == null || seedSlots.isEmpty()) {
            // Brand new set — seed with the historic three-panel default.
            seedSlots = List.of(
                    new PanelistSlot(Provider.ANTHROPIC, null, null),
                    new PanelistSlot(Provider.OPENAI, null, null),
                    new PanelistSlot(Provider.GOOGLE, null, null));
        }

        // Legacy profile sets may reference AgentProfiles that are not yet
        // in the library. Write them in on open so the combos show them
        // as selectable and subsequent edits don't have to recreate them.
        for (PanelistSlot s : seedSlots) {
            AgentProfile ap = s.getAgent();
            if (ap != null && ap.getName() != null && !ap.getName().isBlank()
                    && !profileLib.exists(ap.getName())) {
                try {
                    profileLib.save(ap);
                } catch (Exception ignored) {
                    // non-fatal — the combo will still list library entries
                }
            }
        }

        for (PanelistSlot slot : seedSlots) {
            addSlotRow(slot);
        }

        // Discoverability: a plain-English sentence sitting above the slot
        // list so first-time users understand slots == on-screen chat windows.
        JLabel slotsHint = new JLabel(
                "Each slot is one chat window on the main view. "
                        + "Add, remove, or reconfigure below.");
        slotsHint.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));

        panel.add(meta, BorderLayout.NORTH);
        JPanel slotsWrapper = new JPanel(new BorderLayout());
        slotsWrapper.add(slotsHint, BorderLayout.NORTH);
        slotsWrapper.add(new JScrollPane(agentsPanel), BorderLayout.CENTER);
        panel.add(slotsWrapper, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Builds one row with provider / model / profile combos + a remove
     * button, appends it to agentsPanel (above the add button) and
     * registers it in slotRows.
     */
    private void addSlotRow(PanelistSlot seed) {
        SlotRow row = new SlotRow(seed);
        slotRows.add(row);
        relayoutAgentsPanel();
    }

    /**
     * Tears down the agents panel and rebuilds it in current order —
     * simplest way to keep the +Add button pinned to the bottom and to
     * refresh per-row "Slot N" titles / Remove-button visibility when
     * rows are added or removed.
     */
    private void relayoutAgentsPanel() {
        agentsPanel.removeAll();
        for (int i = 0; i < slotRows.size(); i++) {
            SlotRow row = slotRows.get(i);
            row.updateTitle(i + 1);
            row.setRemoveEnabled(slotRows.size() > MIN_AGENTS);
            agentsPanel.add(row.panel);
        }
        agentsPanel.add(addAgentBtn);
        agentsPanel.revalidate();
        agentsPanel.repaint();
    }

    private JComponent buildButtons(ProfileSet seed) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> onSave(seed));
        cancelButton.addActionListener(e -> dispose());

        panel.add(saveButton);
        panel.add(cancelButton);
        return panel;
    }

    private void onSave(ProfileSet seed) {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Profile set name is required.",
                    "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (slotRows.size() < MIN_AGENTS) {
            JOptionPane.showMessageDialog(this,
                    "The debate panel needs at least " + MIN_AGENTS + " slots.",
                    "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (slotRows.size() > MAX_AGENTS) {
            JOptionPane.showMessageDialog(this,
                    "The debate panel is capped at " + MAX_AGENTS + " slots.",
                    "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validate every slot has Provider + Model + Agent Profile. Any
        // "new" row the user just added without filling in must block save.
        List<Integer> incomplete = new ArrayList<>();
        for (int i = 0; i < slotRows.size(); i++) {
            if (!slotRows.get(i).isComplete()) {
                incomplete.add(i + 1);
            }
        }
        if (!incomplete.isEmpty()) {
            StringBuilder msg = new StringBuilder("The following slot")
                    .append(incomplete.size() == 1 ? " is" : "s are")
                    .append(" missing a provider, model, or agent profile:\n\n");
            for (int idx : incomplete) msg.append("  \u2022 Slot ").append(idx).append("\n");
            msg.append("\nAssign a profile (or create a new one via \"")
                    .append(CREATE_NEW_PROFILE_ITEM).append("\") before saving.");
            JOptionPane.showMessageDialog(this,
                    msg.toString(),
                    "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<PanelistSlot> slots = new ArrayList<>();
        List<AgentProfile> agents = new ArrayList<>();
        for (SlotRow row : slotRows) {
            PanelistSlot ps = row.toSlot();
            slots.add(ps);
            if (ps.getAgent() != null) agents.add(ps.getAgent());
        }

        List<StakeholderProfile> stakeholders = seed != null && seed.getStakeholders() != null
                ? seed.getStakeholders()
                : StakeholderProfile.getDefaults();

        profileSet = new ProfileSet(
                name,
                descriptionField.getText().trim(),
                teamContextArea.getText(),
                agents,
                stakeholders,
                slots);
        dispose();
    }

    public ProfileSet getProfileSet() {
        return profileSet;
    }

    // ============================================================
    // SlotRow — Inner view model for one panelist row.
    //
    // Holds the provider/model/profile combos plus the row panel so
    // ProfileSetEditorDialog can keep a stable list of rows independent
    // of the BoxLayout ordering and the Add/Remove workflow.
    // ============================================================
    private final class SlotRow {
        final JComboBox<Provider> providerCombo = new JComboBox<>(Provider.values());
        // Editable so users can type a custom model ID that isn't in the
        // Config-provided default list.
        final JComboBox<String> modelCombo = new JComboBox<>();
        // Holds either an AgentProfile or the CREATE_NEW_PROFILE_ITEM
        // sentinel string. Object-typed for that reason.
        final JComboBox<Object> profileCombo = new JComboBox<>();
        final JButton removeBtn = new JButton("\u2715");
        final JPanel panel;

        SlotRow(PanelistSlot seed) {
            modelCombo.setEditable(true);

            profileCombo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                        int index, boolean isSelected, boolean cellHasFocus) {
                    String display;
                    if (value instanceof AgentProfile ap) {
                        display = ap.getName() == null ? "(unnamed)" : ap.getName();
                    } else if (value == null) {
                        display = "\u2014 select profile \u2014";
                    } else {
                        display = value.toString();
                    }
                    return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus);
                }
            });

            // Wire up: provider change repopulates the model combo with
            // that provider's default(s). Keeps model/provider in sync.
            providerCombo.addActionListener(e -> reloadModelCombo());

            // Selecting the sentinel opens the inline create dialog.
            profileCombo.addActionListener(e -> {
                Object sel = profileCombo.getSelectedItem();
                if (CREATE_NEW_PROFILE_ITEM.equals(sel)) {
                    // Defer to avoid the combo popup being open when the
                    // dialog tries to show — Swing gets cranky otherwise.
                    SwingUtilities.invokeLater(this::openCreateProfileDialog);
                }
            });

            reloadProfileCombo();

            // Seed initial values from the incoming slot.
            if (seed != null && seed.getProvider() != null) {
                providerCombo.setSelectedItem(seed.getProvider());
            }
            reloadModelCombo();
            if (seed != null && seed.getModel() != null && !seed.getModel().isBlank()) {
                modelCombo.setSelectedItem(seed.getModel());
            }
            if (seed != null && seed.getAgent() != null) {
                selectAgentByName(seed.getAgent().getName());
            } else {
                profileCombo.setSelectedItem(null);
            }

            removeBtn.setMargin(new Insets(0, 6, 0, 6));
            removeBtn.setToolTipText("Remove this slot");
            removeBtn.addActionListener(e -> {
                if (slotRows.size() <= MIN_AGENTS) {
                    JOptionPane.showMessageDialog(ProfileSetEditorDialog.this,
                            "A debate needs at least " + MIN_AGENTS + " slots.",
                            "Cannot remove", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                slotRows.remove(this);
                relayoutAgentsPanel();
            });

            panel = new JPanel(new GridBagLayout());
            panel.setBorder(BorderFactory.createTitledBorder("Slot"));
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(4, 4, 4, 4);
            g.anchor = GridBagConstraints.LINE_START;

            g.gridx = 0; g.gridy = 0; g.weightx = 0;
            panel.add(new JLabel("Provider"), g);
            g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
            panel.add(providerCombo, g);

            g.gridx = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Model"), g);
            g.gridx = 3; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
            panel.add(modelCombo, g);

            g.gridx = 4; g.weightx = 0; g.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Profile"), g);
            g.gridx = 5; g.weightx = 1.2; g.fill = GridBagConstraints.HORIZONTAL;
            panel.add(profileCombo, g);

            g.gridx = 6; g.weightx = 0; g.fill = GridBagConstraints.NONE;
            panel.add(removeBtn, g);
        }

        void updateTitle(int slotNumber) {
            panel.setBorder(BorderFactory.createTitledBorder("Slot " + slotNumber));
        }

        void setRemoveEnabled(boolean enabled) {
            removeBtn.setEnabled(enabled);
        }

        void reloadModelCombo() {
            Object current = modelCombo.getSelectedItem();
            modelCombo.removeAllItems();
            Provider p = (Provider) providerCombo.getSelectedItem();
            String defaultModel = config != null
                    ? PanelistSlot.defaultModelFor(p, config)
                    : null;
            if (defaultModel != null && !defaultModel.isBlank()) {
                modelCombo.addItem(defaultModel);
            }
            // Preserve user-typed custom model when the provider list changes,
            // so switching providers and back doesn't wipe a hand-entered ID.
            if (current instanceof String s && !s.isBlank()) {
                if (defaultModel == null || !s.equals(defaultModel)) {
                    modelCombo.addItem(s);
                }
                modelCombo.setSelectedItem(s);
            }
        }

        void reloadProfileCombo() {
            Object current = profileCombo.getSelectedItem();
            String currentName = (current instanceof AgentProfile ap) ? ap.getName() : null;

            profileCombo.removeAllItems();
            profileCombo.addItem(null); // placeholder; validation flags nulls
            for (AgentProfile ap : profileLib.listAll()) {
                profileCombo.addItem(ap);
            }
            profileCombo.addItem(CREATE_NEW_PROFILE_ITEM);

            if (currentName != null) {
                selectAgentByName(currentName);
            } else {
                profileCombo.setSelectedItem(null);
            }
        }

        private void selectAgentByName(String name) {
            if (name == null) return;
            for (int i = 0; i < profileCombo.getItemCount(); i++) {
                Object item = profileCombo.getItemAt(i);
                if (item instanceof AgentProfile ap && name.equals(ap.getName())) {
                    profileCombo.setSelectedItem(ap);
                    return;
                }
            }
        }

        private void openCreateProfileDialog() {
            Window owner = SwingUtilities.getWindowAncestor(panel);
            AgentProfileEditorDialog dlg = new AgentProfileEditorDialog(owner, profileLib, null);
            dlg.setVisible(true);
            AgentProfile created = dlg.getProfile();
            // Reload every row's profile combo so the new profile shows up
            // for any slot the user picks next — not just this one.
            for (SlotRow r : slotRows) {
                r.reloadProfileCombo();
            }
            if (created != null) {
                selectAgentByName(created.getName());
            } else {
                // User cancelled. Revert to a blank selection so the
                // sentinel isn't left selected (which would re-open the
                // dialog on the next action).
                profileCombo.setSelectedItem(null);
            }
        }

        boolean isComplete() {
            Provider p = (Provider) providerCombo.getSelectedItem();
            if (p == null) return false;
            Object m = modelCombo.getSelectedItem();
            if (!(m instanceof String s) || s.isBlank()) return false;
            Object a = profileCombo.getSelectedItem();
            return a instanceof AgentProfile;
        }

        PanelistSlot toSlot() {
            Provider p = (Provider) providerCombo.getSelectedItem();
            String model = modelCombo.getSelectedItem() == null
                    ? null : modelCombo.getSelectedItem().toString().trim();
            AgentProfile agent = (profileCombo.getSelectedItem() instanceof AgentProfile ap) ? ap : null;
            return new PanelistSlot(p, model, agent);
        }
    }
}
