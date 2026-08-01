package com.spendwise.ui;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.RecurringEntryType;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.AccountService;
import com.spendwise.service.CategoryService;
import com.spendwise.service.QuickEntryService;
import com.spendwise.validation.ValidationException;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

final class QuickEntryDialog extends JDialog {

    private final QuickEntryService quickEntryService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final Runnable successListener;
    private final JComboBox<RecurringEntryType> typeBox =
            new JComboBox<>(RecurringEntryType.values());
    private final JTextField dateField = new JTextField(12);
    private final JTextField amountField = new JTextField(12);
    private final JTextField descriptionField = new JTextField(22);
    private final JComboBox<Category> categoryBox = new JComboBox<>();
    private final JComboBox<Account> sourceBox = new JComboBox<>();
    private final JComboBox<Account> destinationBox = new JComboBox<>();
    private final JLabel statusLabel = new JLabel("Enter a financial entry.");
    private final JButton saveButton = new JButton("Save Entry");
    private boolean submitting;

    QuickEntryDialog(
            Window owner,
            QuickEntryService quickEntryService,
            AccountService accountService,
            CategoryService categoryService,
            Runnable successListener) {
        super(owner, "Quick Entry", Dialog.ModalityType.APPLICATION_MODAL);
        requireEventDispatchThread();
        this.quickEntryService = Objects.requireNonNull(
                quickEntryService, "Quick-entry service is required.");
        this.accountService = Objects.requireNonNull(
                accountService, "Account service is required.");
        this.categoryService = Objects.requireNonNull(
                categoryService, "Category service is required.");
        this.successListener = Objects.requireNonNull(
                successListener, "Quick-entry success listener is required.");
        buildInterface();
    }

    void open() {
        requireEventDispatchThread();
        refreshChoices();
        dateField.setText(LocalDate.now().toString());
        statusLabel.setText("Enter a financial entry.");
        updateRelevantFields();
        pack();
        setLocationRelativeTo(getOwner());
        descriptionField.requestFocusInWindow();
        setVisible(true);
    }

    private void buildInterface() {
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        JPanel form = new JPanel(new GridLayout(6, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 14));
        form.add(new JLabel("Type:"));
        form.add(typeBox);
        form.add(new JLabel("Date (yyyy-MM-dd):"));
        form.add(dateField);
        form.add(new JLabel("Amount:"));
        form.add(amountField);
        form.add(new JLabel("Description / Source:"));
        form.add(descriptionField);
        form.add(new JLabel("Category:"));
        form.add(categoryBox);
        form.add(new JLabel("Account / From:"));
        form.add(sourceBox);

        JPanel destinationRow = new JPanel(new BorderLayout(8, 0));
        destinationRow.setBorder(BorderFactory.createEmptyBorder(0, 14, 8, 14));
        destinationRow.add(new JLabel("Transfer destination:"), BorderLayout.WEST);
        destinationRow.add(destinationBox, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(event -> setVisible(false));
        saveButton.addActionListener(event -> submit());
        actions.add(saveButton);
        actions.add(cancelButton);

        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createEmptyBorder(0, 14, 10, 14));
        south.add(statusLabel, BorderLayout.NORTH);
        south.add(actions, BorderLayout.SOUTH);

        typeBox.addActionListener(event -> updateRelevantFields());
        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(destinationRow, BorderLayout.NORTH);
        add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(saveButton);
        installEscapeAction(getRootPane());
    }

    private void refreshChoices() {
        Category selectedCategory = (Category) categoryBox.getSelectedItem();
        Account selectedSource = (Account) sourceBox.getSelectedItem();
        Account selectedDestination = (Account) destinationBox.getSelectedItem();
        replaceItems(
                categoryBox,
                categoryService.listSelectableCategories(),
                selectedCategory);
        List<Account> accounts = accountService.listSelectableAccounts();
        Account preferredSource = selectedSource != null
                && accounts.contains(selectedSource)
                ? selectedSource
                : accountService.getDefaultAccount();
        replaceItems(
                sourceBox,
                accounts,
                preferredSource);
        replaceItems(destinationBox, accounts, selectedDestination);
        if (destinationBox.getItemCount() > 1
                && Objects.equals(
                    destinationBox.getSelectedItem(),
                    sourceBox.getSelectedItem())) {
            destinationBox.setSelectedIndex(1);
        }
    }

    private void updateRelevantFields() {
        RecurringEntryType type = (RecurringEntryType) typeBox.getSelectedItem();
        categoryBox.setEnabled(type == RecurringEntryType.EXPENSE);
        destinationBox.setEnabled(type == RecurringEntryType.TRANSFER);
    }

    private void submit() {
        if (submitting) {
            return;
        }
        submitting = true;
        saveButton.setEnabled(false);
        try {
            RecurringEntryType type =
                    (RecurringEntryType) typeBox.getSelectedItem();
            quickEntryService.createEntry(
                    type,
                    LocalDate.parse(dateField.getText().strip()),
                    new BigDecimal(amountField.getText().strip()),
                    descriptionField.getText(),
                    type == RecurringEntryType.EXPENSE
                            ? (Category) categoryBox.getSelectedItem()
                            : null,
                    (Account) sourceBox.getSelectedItem(),
                    type == RecurringEntryType.TRANSFER
                            ? (Account) destinationBox.getSelectedItem()
                            : null);
            successListener.run();
            amountField.setText("");
            descriptionField.setText("");
            setVisible(false);
        } catch (NumberFormatException | DateTimeParseException exception) {
            statusLabel.setText("Enter a valid date and decimal amount.");
        } catch (ValidationException | RepositoryException exception) {
            statusLabel.setText(safeMessage(exception));
        } finally {
            submitting = false;
            saveButton.setEnabled(true);
        }
    }

    private void installEscapeAction(JRootPane rootPane) {
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        rootPane.getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                setVisible(false);
            }
        });
    }

    private static <T> void replaceItems(
            JComboBox<T> box, List<T> items, T previous) {
        box.removeAllItems();
        for (T item : items) {
            box.addItem(item);
        }
        if (previous != null && items.contains(previous)) {
            box.setSelectedItem(previous);
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "The quick entry could not be saved safely."
                : message;
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "QuickEntryDialog must be used on the Event Dispatch Thread.");
        }
    }
}
