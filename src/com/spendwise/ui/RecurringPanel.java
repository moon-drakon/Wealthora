package com.spendwise.ui;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.RecurringEntry;
import com.spendwise.model.RecurringEntryType;
import com.spendwise.model.RecurringKind;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.AccountService;
import com.spendwise.service.CategoryService;
import com.spendwise.service.RecurringGenerationResult;
import com.spendwise.service.RecurringService;
import com.spendwise.validation.ValidationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

public final class RecurringPanel extends JPanel {

    private final RecurringService recurringService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final Runnable quickEntryAction;
    private final Runnable mutationListener;
    private final RecurringEntryTableModel tableModel =
            new RecurringEntryTableModel();
    private final JTable table = new JTable(tableModel);
    private final JLabel statusLabel = new JLabel("Recurring entries not loaded.");

    public RecurringPanel(
            RecurringService recurringService,
            AccountService accountService,
            CategoryService categoryService,
            Runnable quickEntryAction,
            Runnable mutationListener) {
        requireEventDispatchThread();
        this.recurringService = Objects.requireNonNull(
                recurringService, "Recurring service is required.");
        this.accountService = Objects.requireNonNull(
                accountService, "Account service is required.");
        this.categoryService = Objects.requireNonNull(
                categoryService, "Category service is required.");
        this.quickEntryAction = Objects.requireNonNull(
                quickEntryAction, "Quick-entry action is required.");
        this.mutationListener = Objects.requireNonNull(
                mutationListener, "Recurring mutation listener is required.");
        buildInterface();
        refreshRecurringEntries();
    }

    public void refreshRecurringEntries() {
        requireEventDispatchThread();
        try {
            List<RecurringEntry> entries = recurringService.listAll();
            tableModel.replace(entries);
            int upcoming = recurringService.findUpcoming(LocalDate.now(), 30).size();
            statusLabel.setText(entries.isEmpty()
                    ? "No recurring definitions. Add one when needed."
                    : entries.size() + (entries.size() == 1
                        ? " recurring definition."
                        : " recurring definitions.")
                        + (upcoming == 0 ? ""
                            : " " + upcoming + " reminder(s) upcoming."));
        } catch (RepositoryException exception) {
            statusLabel.setText(
                    "Unable to load recurring entries: " + safeMessage(exception));
        }
    }

    int getRowCount() {
        return tableModel.getRowCount();
    }

    String getStatusText() {
        return statusLabel.getText();
    }

    void generateDueEntries() {
        try {
            RecurringGenerationResult result =
                    recurringService.generateDueEntries(LocalDate.now());
            refreshRecurringEntries();
            mutationListener.run();
            statusLabel.setText(result.processedCount() == 0
                    ? "No recurring entries are currently due."
                    : "Generated " + result.generatedCount()
                    + (result.generatedCount() == 1 ? " entry" : " entries")
                    + (result.recoveredOccurrenceCount() == 0
                        ? "."
                        : "; safely recovered "
                        + result.recoveredOccurrenceCount()
                        + " previously posted occurrence(s)."));
        } catch (ValidationException | RepositoryException exception) {
            statusLabel.setText(
                    "Generation failed: " + safeMessage(exception));
        }
    }

    private void buildInterface() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(new Color(244, 247, 250));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel title = new JLabel("Recurring Entries");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 23f));
        heading.add(title, BorderLayout.WEST);
        JButton quickEntry = new JButton("Quick Entry");
        quickEntry.setMnemonic('Q');
        quickEntry.addActionListener(event -> quickEntryAction.run());
        heading.add(quickEntry, BorderLayout.EAST);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(button("Add Definition", () -> editDefinition(null)));
        actions.add(button("Edit", this::editSelected));
        actions.add(button("Activate / Deactivate", this::toggleActive));
        actions.add(button("Generate Due Entries", this::generateDueEntries));

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.add(actions, BorderLayout.NORTH);
        south.add(statusLabel, BorderLayout.SOUTH);

        add(heading, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private void editDefinition(RecurringEntry existing) {
        DefinitionFields fields;
        try {
            fields = new DefinitionFields(existing);
        } catch (ValidationException | RepositoryException exception) {
            showError("Unable to load recurring-entry choices: "
                    + safeMessage(exception));
            return;
        }
        while (JOptionPane.showConfirmDialog(
                this,
                fields.panel,
                existing == null
                        ? "Add Recurring Definition"
                        : "Edit Recurring Definition",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                fields.save(existing);
                refreshRecurringEntries();
                statusLabel.setText(existing == null
                        ? "Recurring definition added."
                        : "Recurring definition updated.");
                return;
            } catch (NumberFormatException | DateTimeParseException exception) {
                showError("Enter valid dates, amount, and positive interval.");
            } catch (ValidationException | RepositoryException exception) {
                showError(safeMessage(exception));
            }
        }
    }

    private void editSelected() {
        RecurringEntry selected = selectedEntry();
        if (selected == null) {
            showInformation("Select a recurring definition to edit.");
            return;
        }
        editDefinition(selected);
    }

    private void toggleActive() {
        RecurringEntry selected = selectedEntry();
        if (selected == null) {
            showInformation("Select a recurring definition first.");
            return;
        }
        boolean activating = !selected.isActive();
        if (!activating && JOptionPane.showConfirmDialog(
                this,
                "Deactivate this recurring definition? Existing entries remain unchanged.",
                "Confirm Deactivation",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            recurringService.setActive(selected.getIdentifier(), activating);
            refreshRecurringEntries();
            statusLabel.setText(activating
                    ? "Recurring definition activated."
                    : "Recurring definition deactivated; history was preserved.");
        } catch (ValidationException | RepositoryException exception) {
            showError(safeMessage(exception));
        }
    }

    private RecurringEntry selectedEntry() {
        int selectedRow = table.getSelectedRow();
        return selectedRow < 0
                ? null
                : tableModel.getEntryAt(table.convertRowIndexToModel(selectedRow));
    }

    private List<Account> selectableAccounts(Account historical) {
        List<Account> accounts = new ArrayList<>(
                accountService.listSelectableAccounts());
        if (historical != null && !accounts.contains(historical)) {
            accounts.add(historical);
        }
        return List.copyOf(accounts);
    }

    private List<Category> selectableCategories(Category historical) {
        List<Category> categories = new ArrayList<>(
                categoryService.listSelectableCategories());
        if (historical != null && !categories.contains(historical)) {
            categories.add(historical);
        }
        return List.copyOf(categories);
    }

    private void showError(String message) {
        statusLabel.setText(message);
        JOptionPane.showMessageDialog(
                this,
                message,
                "Recurring Entry Failed",
                JOptionPane.ERROR_MESSAGE);
    }

    private void showInformation(String message) {
        JOptionPane.showMessageDialog(
                this, message, "SpendWise", JOptionPane.INFORMATION_MESSAGE);
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "The recurring operation could not be completed safely."
                : message;
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "RecurringPanel must be used on the Event Dispatch Thread.");
        }
    }

    private final class DefinitionFields {

        private final JComboBox<RecurringEntryType> type =
                new JComboBox<>(RecurringEntryType.values());
        private final JComboBox<RecurringKind> kind =
                new JComboBox<>(RecurringKind.values());
        private final JTextField amount = new JTextField();
        private final JTextField description = new JTextField();
        private final JComboBox<Category> category;
        private final JComboBox<Account> source;
        private final JComboBox<Account> destination;
        private final JComboBox<RecurrenceFrequency> frequency =
                new JComboBox<>(RecurrenceFrequency.values());
        private final JTextField interval = new JTextField("1");
        private final JTextField startDate =
                new JTextField(LocalDate.now().toString());
        private final JTextField endDate = new JTextField();
        private final JTextField nextDueDate =
                new JTextField(LocalDate.now().toString());
        private final JTextField reminderDays = new JTextField("3");
        private final JCheckBox active = new JCheckBox("Active", true);
        private final JPanel panel = new JPanel(new GridLayout(14, 2, 8, 8));

        private DefinitionFields(RecurringEntry existing) {
            Category historicalCategory = existing == null
                    ? null : existing.getCategory().orElse(null);
            Account historicalSource = existing == null
                    ? null : existing.getSourceAccount();
            Account historicalDestination = existing == null
                    ? null : existing.getDestinationAccount().orElse(null);
            category = new JComboBox<>(selectableCategories(historicalCategory)
                    .toArray(Category[]::new));
            source = new JComboBox<>(selectableAccounts(historicalSource)
                    .toArray(Account[]::new));
            destination = new JComboBox<>(selectableAccounts(historicalDestination)
                    .toArray(Account[]::new));

            if (existing != null) {
                type.setSelectedItem(existing.getType());
                kind.setSelectedItem(existing.getKind());
                amount.setText(existing.getAmount().toPlainString());
                description.setText(existing.getDescription());
                category.setSelectedItem(historicalCategory);
                source.setSelectedItem(historicalSource);
                destination.setSelectedItem(historicalDestination);
                frequency.setSelectedItem(existing.getFrequency());
                interval.setText(Integer.toString(existing.getInterval()));
                startDate.setText(existing.getStartDate().toString());
                endDate.setText(existing.getEndDate()
                        .map(LocalDate::toString).orElse(""));
                nextDueDate.setText(existing.getNextDueDate().toString());
                reminderDays.setText(Integer.toString(
                        existing.getReminderDays()));
                active.setSelected(existing.isActive());
            } else {
                source.setSelectedItem(accountService.getDefaultAccount());
                selectDifferentDestination();
            }
            nextDueDate.setEnabled(existing != null);
            add("Type", type);
            add("Kind", kind);
            add("Amount", amount);
            add("Description / Source", description);
            add("Expense category", category);
            add("Account / From", source);
            add("Transfer destination", destination);
            add("Frequency", frequency);
            add("Interval", interval);
            add("Start date (yyyy-MM-dd)", startDate);
            add("Optional end date", endDate);
            add("Next due date", nextDueDate);
            add("Reminder days", reminderDays);
            panel.add(new JLabel("Status:"));
            panel.add(active);
            type.addActionListener(event -> updateRelevantFields());
            updateRelevantFields();
        }

        private void selectDifferentDestination() {
            for (int index = 0; index < destination.getItemCount(); index++) {
                if (!Objects.equals(
                        destination.getItemAt(index),
                        source.getSelectedItem())) {
                    destination.setSelectedIndex(index);
                    return;
                }
            }
        }

        private void add(String label, java.awt.Component field) {
            panel.add(new JLabel(label + ":"));
            panel.add(field);
        }

        private void updateRelevantFields() {
            RecurringEntryType selected =
                    (RecurringEntryType) type.getSelectedItem();
            category.setEnabled(selected == RecurringEntryType.EXPENSE);
            destination.setEnabled(selected == RecurringEntryType.TRANSFER);
            kind.setEnabled(selected == RecurringEntryType.EXPENSE);
            if (selected != RecurringEntryType.EXPENSE) {
                kind.setSelectedItem(RecurringKind.SCHEDULED_TRANSACTION);
            }
        }

        private void save(RecurringEntry existing) {
            RecurringEntryType selectedType =
                    (RecurringEntryType) type.getSelectedItem();
            LocalDate parsedEnd = endDate.getText().isBlank()
                    ? null : LocalDate.parse(endDate.getText().strip());
            if (existing == null) {
                recurringService.addDefinition(
                        selectedType,
                        new BigDecimal(amount.getText().strip()),
                        description.getText(),
                        selectedType == RecurringEntryType.EXPENSE
                                ? (Category) category.getSelectedItem() : null,
                        (Account) source.getSelectedItem(),
                        selectedType == RecurringEntryType.TRANSFER
                                ? (Account) destination.getSelectedItem() : null,
                        (RecurrenceFrequency) frequency.getSelectedItem(),
                        Integer.parseInt(interval.getText().strip()),
                        LocalDate.parse(startDate.getText().strip()),
                        parsedEnd,
                        (RecurringKind) kind.getSelectedItem(),
                        Integer.parseInt(reminderDays.getText().strip()),
                        active.isSelected());
            } else {
                recurringService.updateDefinition(
                        existing.getIdentifier(),
                        selectedType,
                        new BigDecimal(amount.getText().strip()),
                        description.getText(),
                        selectedType == RecurringEntryType.EXPENSE
                                ? (Category) category.getSelectedItem() : null,
                        (Account) source.getSelectedItem(),
                        selectedType == RecurringEntryType.TRANSFER
                                ? (Account) destination.getSelectedItem() : null,
                        (RecurrenceFrequency) frequency.getSelectedItem(),
                        Integer.parseInt(interval.getText().strip()),
                        LocalDate.parse(startDate.getText().strip()),
                        parsedEnd,
                        LocalDate.parse(nextDueDate.getText().strip()),
                        (RecurringKind) kind.getSelectedItem(),
                        Integer.parseInt(reminderDays.getText().strip()),
                        active.isSelected());
            }
        }
    }
}
