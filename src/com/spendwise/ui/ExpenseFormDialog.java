package com.spendwise.ui;

import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.ExpenseNotFoundException;
import com.spendwise.service.ExpenseService;
import com.spendwise.validation.ValidationException;
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
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

final class ExpenseFormDialog extends JDialog {

    private final ExpenseService expenseService;
    private final Expense expenseToEdit;
    private final JTextField descriptionField = new JTextField(28);
    private final JTextField amountField = new JTextField(14);
    private final JTextField dateField = new JTextField(14);
    private final JComboBox<Category> categoryComboBox = new JComboBox<>();
    private final JTextArea notesArea = new JTextArea(5, 28);

    private boolean saved;

    ExpenseFormDialog(
            Window owner, ExpenseService expenseService, Expense expenseToEdit) {
        this(
                owner,
                expenseService,
                expenseToEdit,
                List.of(Category.values()));
    }

    ExpenseFormDialog(
            Window owner,
            ExpenseService expenseService,
            Expense expenseToEdit,
            List<Category> selectableCategories) {
        super(
                owner,
                expenseToEdit == null ? "Add Expense" : "Edit Expense",
                ModalityType.APPLICATION_MODAL);
        requireEventDispatchThread();
        this.expenseService = Objects.requireNonNull(
                expenseService, "Expense service is required.");
        this.expenseToEdit = expenseToEdit;
        populateCategoryChoices(selectableCategories);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildInterface();
        populateFields();
        pack();
        setMinimumSize(new Dimension(470, 430));
        setLocationRelativeTo(owner);
    }

    boolean showDialog() {
        setVisible(true);
        return saved;
    }

    int getCategoryChoiceCount() {
        return categoryComboBox.getItemCount();
    }

    Category getCategoryChoiceAt(int index) {
        return categoryComboBox.getItemAt(index);
    }

    static BigDecimal parseAmount(String amountText) {
        String normalizedAmount = amountText == null ? "" : amountText.trim();
        if (normalizedAmount.isEmpty()) {
            throw new NumberFormatException("Amount is required.");
        }
        return new BigDecimal(normalizedAmount);
    }

    static LocalDate parseDate(String dateText) {
        String normalizedDate = dateText == null ? "" : dateText.trim();
        if (normalizedDate.isEmpty()) {
            throw new DateTimeParseException("Date is required.", normalizedDate, 0);
        }
        return LocalDate.parse(normalizedDate);
    }

    private void buildInterface() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        addField(formPanel, 0, "Description", descriptionField);
        addField(formPanel, 1, "Amount", amountField);
        addField(formPanel, 2, "Date (yyyy-MM-dd)", dateField);
        addField(formPanel, 3, "Category", categoryComboBox);

        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        JScrollPane notesScrollPane = new JScrollPane(notesArea);
        notesScrollPane.setPreferredSize(new Dimension(320, 110));
        addField(formPanel, 4, "Notes", notesScrollPane);

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(event -> saveExpense());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(event -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        buttonPanel.add(cancelButton);
        buttonPanel.add(saveButton);

        setLayout(new BorderLayout());
        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(saveButton);
    }

    private void populateFields() {
        if (expenseToEdit == null) {
            dateField.setText(LocalDate.now().toString());
            categoryComboBox.setSelectedItem(Category.FOOD);
            return;
        }

        descriptionField.setText(expenseToEdit.getDescription());
        amountField.setText(expenseToEdit.getAmount().toPlainString());
        dateField.setText(expenseToEdit.getDate().toString());
        categoryComboBox.setSelectedItem(expenseToEdit.getCategory());
        notesArea.setText(expenseToEdit.getNotes());
    }

    private void populateCategoryChoices(List<Category> selectableCategories) {
        Objects.requireNonNull(
                selectableCategories, "Selectable categories are required.");
        for (Category category : selectableCategories) {
            categoryComboBox.addItem(Objects.requireNonNull(
                    category, "Selectable categories cannot contain null elements."));
        }
        if (expenseToEdit != null
                && !containsCategory(expenseToEdit.getCategory())) {
            categoryComboBox.addItem(expenseToEdit.getCategory());
        }
        if (categoryComboBox.getItemCount() == 0) {
            throw new IllegalArgumentException(
                    "At least one selectable category is required.");
        }
    }

    private boolean containsCategory(Category category) {
        for (int index = 0; index < categoryComboBox.getItemCount(); index++) {
            if (categoryComboBox.getItemAt(index).equals(category)) {
                return true;
            }
        }
        return false;
    }

    private void saveExpense() {
        try {
            BigDecimal amount = parseAmount(amountField.getText());
            LocalDate date = parseDate(dateField.getText());
            Category category = (Category) categoryComboBox.getSelectedItem();

            if (expenseToEdit == null) {
                expenseService.createExpense(
                        descriptionField.getText(),
                        amount,
                        date,
                        category,
                        notesArea.getText());
            } else {
                expenseService.updateExpense(
                        expenseToEdit.getId(),
                        descriptionField.getText(),
                        amount,
                        date,
                        category,
                        notesArea.getText());
            }

            saved = true;
            dispose();
        } catch (NumberFormatException exception) {
            showInputError("Enter a valid amount using decimal numbers.");
        } catch (DateTimeParseException exception) {
            showInputError("Enter the date in yyyy-MM-dd format.");
        } catch (ValidationException
                | ExpenseNotFoundException
                | RepositoryException exception) {
            showInputError(exception.getMessage());
        }
    }

    private void showInputError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Unable to Save Expense",
                JOptionPane.ERROR_MESSAGE);
    }

    private static void addField(
            JPanel panel, int row, String labelText, java.awt.Component component) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.NORTHWEST;
        labelConstraints.insets = new Insets(6, 0, 6, 12);

        JLabel label = new JLabel(labelText + ":");
        label.setLabelFor(component);
        panel.add(label, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.anchor = GridBagConstraints.NORTHWEST;
        fieldConstraints.insets = new Insets(6, 0, 6, 0);
        panel.add(component, fieldConstraints);
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "Expense dialogs must be created on the Event Dispatch Thread.");
        }
    }
}
