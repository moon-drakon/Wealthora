package com.spendwise.ui;

import com.spendwise.model.Category;
import com.spendwise.repository.RepositoryException;
import com.spendwise.service.CategoryService;
import com.spendwise.validation.ValidationException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;

final class CategoryManagerDialog extends JDialog {

    private final CategoryService categoryService;
    private final Predicate<Category> referenceChecker;
    private final Runnable categoryChangeListener;
    private final CategoryTableModel tableModel = new CategoryTableModel();
    private final JTable categoryTable = new JTable(tableModel);
    private final JButton renameButton = new JButton("Rename");
    private final JButton archiveButton = new JButton("Archive");
    private final JButton restoreButton = new JButton("Restore");
    private final JLabel statusLabel = new JLabel("Categories loaded.");

    CategoryManagerDialog(
            Window owner,
            CategoryService categoryService,
            Predicate<Category> referenceChecker,
            Runnable categoryChangeListener) {
        super(owner, "Manage Categories", ModalityType.APPLICATION_MODAL);
        requireEventDispatchThread();
        this.categoryService = Objects.requireNonNull(
                categoryService, "Category service is required.");
        this.referenceChecker = Objects.requireNonNull(
                referenceChecker, "Category reference checker is required.");
        this.categoryChangeListener = Objects.requireNonNull(
                categoryChangeListener, "Category change listener is required.");

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildInterface();
        refreshCategories(null, "Categories loaded.");
        setSize(new Dimension(620, 430));
        setMinimumSize(new Dimension(520, 360));
        setLocationRelativeTo(owner);
    }

    void showDialog() {
        setVisible(true);
    }

    CategoryTableModel getCategoryTableModel() {
        return tableModel;
    }

    static ActionState actionStateFor(Category category) {
        if (category == null || category.isBuiltIn()) {
            return new ActionState(false, false, false);
        }
        return category.isArchived()
                ? new ActionState(true, false, true)
                : new ActionState(true, true, false);
    }

    private void buildInterface() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JLabel guidance = new JLabel(
                "Built-in categories are protected. Archived categories remain in history.");
        content.add(guidance, BorderLayout.NORTH);

        categoryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryTable.setFillsViewportHeight(true);
        categoryTable.setRowHeight(24);
        categoryTable.getTableHeader().setReorderingAllowed(false);
        categoryTable.getSelectionModel().addListSelectionListener(
                this::selectionChanged);
        content.add(new JScrollPane(categoryTable), BorderLayout.CENTER);

        JButton addButton = new JButton("Add");
        addButton.addActionListener(event -> addCategory());
        renameButton.addActionListener(event -> renameSelectedCategory());
        archiveButton.addActionListener(event -> archiveSelectedCategory());
        restoreButton.addActionListener(event -> restoreSelectedCategory());
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(event -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(addButton);
        buttons.add(renameButton);
        buttons.add(archiveButton);
        buttons.add(restoreButton);
        buttons.add(closeButton);

        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        updateActionState();
    }

    private void addCategory() {
        String name = JOptionPane.showInputDialog(
                this, "Category name:", "Add Category", JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        try {
            Category added = categoryService.addCategory(name);
            mutationSucceeded(added, "Category added.");
        } catch (ValidationException | RepositoryException exception) {
            showFailure(exception);
        }
    }

    private void renameSelectedCategory() {
        Category selected = selectedCategory();
        if (!actionStateFor(selected).renameEnabled()) {
            return;
        }
        String name = (String) JOptionPane.showInputDialog(
                this,
                "New category name:",
                "Rename Category",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                selected.getDisplayName());
        if (name == null) {
            return;
        }
        try {
            Category renamed = categoryService.renameCategory(
                    selected.getIdentifier(), name);
            mutationSucceeded(renamed, "Category renamed.");
        } catch (ValidationException | RepositoryException exception) {
            showFailure(exception);
        }
    }

    private void archiveSelectedCategory() {
        Category selected = selectedCategory();
        if (!actionStateFor(selected).archiveEnabled()) {
            return;
        }
        try {
            if (referenceChecker.test(selected)) {
                int choice = JOptionPane.showConfirmDialog(
                        this,
                        "This category is already used by an expense or budget. "
                        + "Archive it for new entries while keeping its history?",
                        "Confirm Category Archive",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            Category archived = categoryService.archiveCategory(
                    selected.getIdentifier());
            mutationSucceeded(archived, "Category archived.");
        } catch (ValidationException | RepositoryException exception) {
            showFailure(exception);
        }
    }

    private void restoreSelectedCategory() {
        Category selected = selectedCategory();
        if (!actionStateFor(selected).restoreEnabled()) {
            return;
        }
        try {
            Category restored = categoryService.restoreCategory(
                    selected.getIdentifier());
            mutationSucceeded(restored, "Category restored.");
        } catch (ValidationException | RepositoryException exception) {
            showFailure(exception);
        }
    }

    private void mutationSucceeded(Category category, String message) {
        categoryChangeListener.run();
        refreshCategories(category.getIdentifier(), message);
    }

    private void refreshCategories(String selectedIdentifier, String message) {
        try {
            tableModel.replaceCategories(categoryService.listAllCategories());
            restoreSelection(selectedIdentifier);
            statusLabel.setText(message);
            updateActionState();
        } catch (RepositoryException exception) {
            showFailure(exception);
        }
    }

    private void restoreSelection(String identifier) {
        if (identifier == null) {
            categoryTable.clearSelection();
            return;
        }
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (tableModel.getCategoryAt(row).getIdentifier().equals(identifier)) {
                categoryTable.setRowSelectionInterval(row, row);
                return;
            }
        }
    }

    private void selectionChanged(ListSelectionEvent event) {
        if (!event.getValueIsAdjusting()) {
            updateActionState();
        }
    }

    private void updateActionState() {
        ActionState state = actionStateFor(selectedCategory());
        renameButton.setEnabled(state.renameEnabled());
        archiveButton.setEnabled(state.archiveEnabled());
        restoreButton.setEnabled(state.restoreEnabled());
    }

    private Category selectedCategory() {
        int row = categoryTable.getSelectedRow();
        return row < 0 ? null : tableModel.getCategoryAt(
                categoryTable.convertRowIndexToModel(row));
    }

    private void showFailure(RuntimeException exception) {
        String message = exception.getMessage();
        String safeMessage = message == null || message.isBlank()
                ? "The category operation could not be completed safely."
                : message;
        statusLabel.setText(safeMessage);
        JOptionPane.showMessageDialog(
                this,
                safeMessage,
                "Category Operation Failed",
                JOptionPane.ERROR_MESSAGE);
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "Category dialogs must be created on the Event Dispatch Thread.");
        }
    }

    record ActionState(
            boolean renameEnabled,
            boolean archiveEnabled,
            boolean restoreEnabled) {
    }
}
