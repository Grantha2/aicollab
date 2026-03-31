package collab;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public class ProfileSelectorDialog extends JDialog {

    private final ProfileLibrary profileLibrary;
    private final JList<String> namesList;
    private final JTextArea descriptionArea;
    private ProfileSet selectedProfileSet;

    public ProfileSelectorDialog(Frame owner, ProfileLibrary profileLibrary) throws IOException {
        super(owner, "Select Profile Set", true);
        this.profileLibrary = profileLibrary;

        setLayout(new BorderLayout(8, 8));

        List<String> names = profileLibrary.listAvailableSets();
        namesList = new JList<>(names.toArray(String[]::new));
        namesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (!names.isEmpty()) {
            namesList.setSelectedIndex(0);
        }

        descriptionArea = new JTextArea(8, 30);
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        namesList.addListSelectionListener(e -> loadDescription());
        loadDescription();

        add(new JScrollPane(namesList), BorderLayout.WEST);
        add(new JScrollPane(descriptionArea), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton loadBtn = new JButton("Load");
        JButton cancelBtn = new JButton("Cancel");

        loadBtn.addActionListener(e -> onLoad());
        cancelBtn.addActionListener(e -> dispose());

        panel.add(loadBtn);
        panel.add(cancelBtn);
        return panel;
    }

    private void loadDescription() {
        String selectedName = namesList.getSelectedValue();
        if (selectedName == null) {
            descriptionArea.setText("");
            return;
        }
        try {
            ProfileSet set = profileLibrary.loadSet(selectedName);
            descriptionArea.setText(set.getDescription());
        } catch (IOException e) {
            descriptionArea.setText("Unable to load description: " + e.getMessage());
        }
    }

    private void onLoad() {
        String selectedName = namesList.getSelectedValue();
        if (selectedName == null) {
            return;
        }
        try {
            selectedProfileSet = profileLibrary.loadSet(selectedName);
            dispose();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load profile set: " + e.getMessage(),
                    "Load Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public ProfileSet getSelectedProfileSet() {
        return selectedProfileSet;
    }
}
