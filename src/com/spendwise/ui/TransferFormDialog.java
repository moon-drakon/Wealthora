package com.spendwise.ui;

import com.spendwise.model.Account;
import com.spendwise.model.Transfer;
import com.spendwise.service.TransferService;
import com.spendwise.ui.component.PrimaryButton;
import com.spendwise.ui.component.SecondaryButton;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

final class TransferFormDialog extends JDialog {

    private final TransferService transferService;
    private final Transfer transferToEdit;
    private final JTextField dateField = new JTextField(14);
    private final JTextField amountField = new JTextField(14);
    private final JComboBox<Account> sourceComboBox = new JComboBox<>();
    private final JComboBox<Account> destinationComboBox = new JComboBox<>();
    private final JTextArea noteArea = new JTextArea(4, 26);
    private final JTextField tagsField = new JTextField(26);
    private boolean saved;

    TransferFormDialog(
            Window owner,
            TransferService transferService,
            Transfer transferToEdit,
            List<Account> selectableAccounts,
            Account preferredAccount) {
        super(owner, transferToEdit == null ? "Add Transfer" : "Edit Transfer",
                ModalityType.APPLICATION_MODAL);
        this.transferService = Objects.requireNonNull(transferService);
        this.transferToEdit = transferToEdit;
        populateAccounts(selectableAccounts);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildInterface();
        populateFields(Objects.requireNonNull(preferredAccount));
        pack();
        setMinimumSize(new Dimension(480, 410));
        setLocationRelativeTo(owner);
    }

    boolean showDialog() {
        setVisible(true);
        return saved;
    }

    private void buildInterface() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(18, 18, 10, 18));
        addField(form, 0, "Date (yyyy-MM-dd)", dateField);
        addField(form, 1, "Amount", amountField);
        addField(form, 2, "From account", sourceComboBox);
        addField(form, 3, "To account", destinationComboBox);
        addField(form, 4, "Tags (comma-separated)", tagsField);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        addField(form, 5, "Note", new JScrollPane(noteArea));

        SecondaryButton cancel = new SecondaryButton("Cancel");
        cancel.addActionListener(event -> dispose());
        PrimaryButton save = new PrimaryButton("Save Transfer");
        save.addActionListener(event -> saveTransfer());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 18, 18, 18));
        actions.add(cancel);
        actions.add(save);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
    }

    private void populateFields(Account preferredAccount) {
        if (transferToEdit == null) {
            dateField.setText(LocalDate.now().toString());
            sourceComboBox.setSelectedItem(preferredAccount);
            if (destinationComboBox.getItemCount() > 1) {
                destinationComboBox.setSelectedIndex(
                        sourceComboBox.getSelectedIndex() == 0 ? 1 : 0);
            }
            return;
        }
        dateField.setText(transferToEdit.getDate().toString());
        amountField.setText(transferToEdit.getAmount().toPlainString());
        sourceComboBox.setSelectedItem(transferToEdit.getSourceAccount());
        destinationComboBox.setSelectedItem(
                transferToEdit.getDestinationAccount());
        tagsField.setText(String.join(", ", transferToEdit.getTags()));
        noteArea.setText(transferToEdit.getNote());
    }

    private void populateAccounts(List<Account> accounts) {
        Objects.requireNonNull(accounts);
        for (Account account : accounts) {
            sourceComboBox.addItem(account);
            destinationComboBox.addItem(account);
        }
        if (transferToEdit != null) {
            addHistoricalIfMissing(transferToEdit.getSourceAccount());
            addHistoricalIfMissing(transferToEdit.getDestinationAccount());
        }
        if (sourceComboBox.getItemCount() < 2 && transferToEdit == null) {
            throw new IllegalArgumentException(
                    "At least two active accounts are required for a transfer.");
        }
    }

    private void addHistoricalIfMissing(Account account) {
        for (int index = 0; index < sourceComboBox.getItemCount(); index++) {
            if (account.equals(sourceComboBox.getItemAt(index))) {
                return;
            }
        }
        sourceComboBox.addItem(account);
        destinationComboBox.addItem(account);
    }

    private void saveTransfer() {
        try {
            LocalDate date = LocalDate.parse(dateField.getText().strip());
            BigDecimal amount = new BigDecimal(amountField.getText().strip());
            Account source = (Account) sourceComboBox.getSelectedItem();
            Account destination =
                    (Account) destinationComboBox.getSelectedItem();
            List<String> tags = ExpenseFormDialog.parseTags(tagsField.getText());
            if (transferToEdit == null) {
                transferService.createTransfer(
                        date, amount, source, destination, tags,
                        noteArea.getText());
            } else {
                transferService.updateTransfer(
                        transferToEdit.getId(), date, amount, source,
                        destination, tags, noteArea.getText());
            }
            saved = true;
            dispose();
        } catch (DateTimeParseException exception) {
            showError("Enter the date in yyyy-MM-dd format.");
        } catch (NumberFormatException exception) {
            showError("Enter a valid amount using decimal numbers.");
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message,
                "Unable to Save Transfer", JOptionPane.ERROR_MESSAGE);
    }

    private static void addField(
            JPanel panel, int row, String text, java.awt.Component component) {
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.gridy = row;
        label.anchor = GridBagConstraints.NORTHWEST;
        label.insets = new Insets(6, 0, 6, 12);
        JLabel fieldLabel = new JLabel(text + ":");
        fieldLabel.setLabelFor(component);
        panel.add(fieldLabel, label);

        GridBagConstraints field = new GridBagConstraints();
        field.gridx = 1;
        field.gridy = row;
        field.weightx = 1;
        field.fill = GridBagConstraints.HORIZONTAL;
        field.anchor = GridBagConstraints.NORTHWEST;
        field.insets = new Insets(6, 0, 6, 0);
        panel.add(component, field);
    }
}
