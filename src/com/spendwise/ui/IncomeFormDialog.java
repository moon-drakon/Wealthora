package com.spendwise.ui;

import com.spendwise.model.Account;
import com.spendwise.model.Income;
import com.spendwise.service.IncomeService;
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

final class IncomeFormDialog extends JDialog {

    private final IncomeService incomeService;
    private final Income incomeToEdit;
    private final Account preferredAccount;
    private final JTextField dateField = new JTextField(14);
    private final JTextField amountField = new JTextField(14);
    private final JTextField sourceField = new JTextField(26);
    private final JComboBox<Account> accountComboBox = new JComboBox<>();
    private final JTextArea noteArea = new JTextArea(4, 26);
    private boolean saved;

    IncomeFormDialog(
            Window owner,
            IncomeService incomeService,
            Income incomeToEdit,
            List<Account> selectableAccounts,
            Account preferredAccount) {
        super(owner, incomeToEdit == null ? "Add Income" : "Edit Income",
                ModalityType.APPLICATION_MODAL);
        this.incomeService = Objects.requireNonNull(incomeService);
        this.incomeToEdit = incomeToEdit;
        this.preferredAccount = Objects.requireNonNull(preferredAccount);
        populateAccounts(selectableAccounts);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildInterface();
        populateFields();
        pack();
        setMinimumSize(new Dimension(470, 390));
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
        addField(form, 2, "Source", sourceField);
        addField(form, 3, "Account", accountComboBox);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        addField(form, 4, "Note", new JScrollPane(noteArea));

        SecondaryButton cancel = new SecondaryButton("Cancel");
        cancel.addActionListener(event -> dispose());
        PrimaryButton save = new PrimaryButton("Save Income");
        save.addActionListener(event -> saveIncome());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 18, 18, 18));
        actions.add(cancel);
        actions.add(save);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
    }

    private void populateFields() {
        if (incomeToEdit == null) {
            dateField.setText(LocalDate.now().toString());
            accountComboBox.setSelectedItem(preferredAccount);
            return;
        }
        dateField.setText(incomeToEdit.getDate().toString());
        amountField.setText(incomeToEdit.getAmount().toPlainString());
        sourceField.setText(incomeToEdit.getSource());
        accountComboBox.setSelectedItem(incomeToEdit.getAccount());
        noteArea.setText(incomeToEdit.getNote());
    }

    private void populateAccounts(List<Account> accounts) {
        Objects.requireNonNull(accounts);
        accounts.forEach(accountComboBox::addItem);
        if (incomeToEdit != null && !contains(incomeToEdit.getAccount())) {
            accountComboBox.addItem(incomeToEdit.getAccount());
        }
        if (accountComboBox.getItemCount() == 0) {
            throw new IllegalArgumentException("At least one account is required.");
        }
    }

    private boolean contains(Account account) {
        for (int index = 0; index < accountComboBox.getItemCount(); index++) {
            if (account.equals(accountComboBox.getItemAt(index))) {
                return true;
            }
        }
        return false;
    }

    private void saveIncome() {
        try {
            LocalDate date = LocalDate.parse(dateField.getText().strip());
            BigDecimal amount = new BigDecimal(amountField.getText().strip());
            Account account = (Account) accountComboBox.getSelectedItem();
            if (incomeToEdit == null) {
                incomeService.createIncome(
                        date, amount, sourceField.getText(), account,
                        noteArea.getText());
            } else {
                incomeService.updateIncome(
                        incomeToEdit.getId(), date, amount,
                        sourceField.getText(), account, noteArea.getText());
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
                "Unable to Save Income", JOptionPane.ERROR_MESSAGE);
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
