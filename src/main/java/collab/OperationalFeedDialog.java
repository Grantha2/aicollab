package collab;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;

public class OperationalFeedDialog extends JDialog {

    private boolean cancelled = true;
    private final JTextField titleField = new JTextField(25);
    private final JComboBox<String> typeCombo = new JComboBox<>(new String[]{"EVENT", "MEETING", "DEADLINE", "TASK"});
    private final JTextField dateField = new JTextField(LocalDate.now().plusDays(1).toString(), 12);
    private final JTextField timeField = new JTextField(8);
    private final JTextField ownerField = new JTextField(20);
    private final JTextField attendeesField = new JTextField(25);
    private final JTextArea notesArea = new JTextArea(3, 25);

    public OperationalFeedDialog(Frame owner) {
        super(owner, "Add Operational Feed Item", true);
        setLayout(new BorderLayout(8, 8));
        setSize(480, 380);
        setLocationRelativeTo(owner);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addRow(form, gbc, row++, "Title:", titleField);
        addRow(form, gbc, row++, "Type:", typeCombo);
        addRow(form, gbc, row++, "Date (YYYY-MM-DD):", dateField);
        addRow(form, gbc, row++, "Time (HH:MM):", timeField);
        addRow(form, gbc, row++, "Owner:", ownerField);
        addRow(form, gbc, row++, "Attendees:", attendeesField);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Notes:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        form.add(new JScrollPane(notesArea), gbc);

        add(new JScrollPane(form), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        JButton addBtn = new JButton("Add");
        addBtn.addActionListener(e -> { cancelled = false; dispose(); });
        buttons.add(cancelBtn);
        buttons.add(addBtn);
        add(buttons, BorderLayout.SOUTH);
    }

    private void addRow(JPanel form, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(field, gbc);
    }

    public boolean wasCancelled() { return cancelled; }

    public OperationalFeedItem getResult() {
        OperationalFeedItem item = new OperationalFeedItem();
        item.setId(java.util.UUID.randomUUID().toString());
        item.setTitle(titleField.getText().trim());
        item.setType((String) typeCombo.getSelectedItem());
        item.setDate(dateField.getText().trim());
        item.setTime(timeField.getText().trim());
        item.setOwner(ownerField.getText().trim());
        item.setAttendees(attendeesField.getText().trim());
        item.setNotes(notesArea.getText().trim());
        item.setStatus(OperationalFeedItem.FeedStatus.UPCOMING.name());
        return item;
    }
}
