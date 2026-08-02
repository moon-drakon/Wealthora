package com.spendwise.ui.voice;

import com.formdev.flatlaf.FlatClientProperties;
import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.PaymentMethod;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.TransactionType;
import com.spendwise.ui.component.PrimaryButton;
import com.spendwise.ui.component.SecondaryButton;
import com.spendwise.ui.component.StyledComboBox;
import com.spendwise.ui.component.StyledTextField;
import com.spendwise.ui.theme.AppColors;
import com.spendwise.ui.theme.AppFonts;
import com.spendwise.ui.theme.AppTheme;
import com.spendwise.voice.VoiceParseResult;
import com.spendwise.voice.VoiceTransactionDraft;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public final class VoiceDraftReviewPanel extends JPanel {

    private final StyledComboBox<TransactionType> type =
            new StyledComboBox<>(TransactionType.values());
    private final StyledTextField amount = new StyledTextField("Amount", 14);
    private final StyledTextField currency =
            new StyledTextField("Currency", 8);
    private final StyledComboBox<Account> source = new StyledComboBox<>();
    private final StyledComboBox<Account> destination = new StyledComboBox<>();
    private final StyledComboBox<Category> category = new StyledComboBox<>();
    private final StyledComboBox<Category> subcategory = new StyledComboBox<>();
    private final StyledTextField date = new StyledTextField("Date", 12);
    private final StyledTextField time = new StyledTextField("Time", 8);
    private final StyledComboBox<PaymentMethod> payment =
            new StyledComboBox<>(PaymentMethod.values());
    private final StyledTextField description =
            new StyledTextField("Description", 24);
    private final StyledTextField note = new StyledTextField("Note", 24);
    private final StyledTextField tags =
            new StyledTextField("Comma-separated tags", 24);
    private final JCheckBox recurring = new JCheckBox("Recurring transaction");
    private final StyledComboBox<RecurrenceFrequency> frequency =
            new StyledComboBox<>();
    private final StyledTextField nextDue =
            new StyledTextField("Next due date", 12);
    private final JLabel transcript = new JLabel(" ");
    private final JLabel reviewMessages = new JLabel(" ");
    private final Consumer<VoiceTransactionDraft> confirmAction;
    private List<Category> categories = List.of();
    private boolean loading;

    public VoiceDraftReviewPanel(
            Consumer<VoiceTransactionDraft> confirmAction,
            Runnable backAction,
            Runnable cancelAction) {
        super(new BorderLayout(0, 12));
        this.confirmAction = Objects.requireNonNull(confirmAction);
        Objects.requireNonNull(backAction);
        Objects.requireNonNull(cancelAction);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        AppTheme.mark(this, AppTheme.PAGE_ROLE);

        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.setOpaque(false);
        JLabel title = new JLabel("Review transaction draft");
        title.setFont(AppFonts.pageTitle());
        AppTheme.mark(title, AppTheme.PRIMARY_TEXT_ROLE);
        transcript.setFont(AppFonts.caption());
        AppTheme.mark(transcript, AppTheme.SECONDARY_TEXT_ROLE);
        heading.add(title, BorderLayout.NORTH);
        heading.add(transcript, BorderLayout.CENTER);
        add(heading, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        int row = 0;
        row = addRow(form, row, "Transaction type", type,
                "Amount", amount);
        row = addRow(form, row, "Currency", currency,
                "Source account", source);
        row = addRow(form, row, "Destination account", destination,
                "Category", category);
        row = addRow(form, row, "Subcategory", subcategory,
                "Date", date);
        row = addRow(form, row, "Time", time,
                "Payment method", payment);
        row = addRow(form, row, "Description", description,
                "Note", note);
        row = addRow(form, row, "Tags", tags,
                "Recurring", recurring);
        addRow(form, row, "Frequency", frequency,
                "Next due date", nextDue);
        JPanel formAndMessages = new JPanel(new BorderLayout(0, 10));
        formAndMessages.setOpaque(false);
        formAndMessages.add(form, BorderLayout.NORTH);
        reviewMessages.setFont(AppFonts.caption());
        reviewMessages.setForeground(AppColors.expense());
        formAndMessages.add(reviewMessages, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(formAndMessages);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        SecondaryButton back = new SecondaryButton("Edit Transcript");
        back.addActionListener(event -> backAction.run());
        PrimaryButton confirm = new PrimaryButton("Confirm and Add");
        confirm.addActionListener(event -> confirm());
        SecondaryButton cancel = new SecondaryButton("Cancel");
        cancel.addActionListener(event -> cancelAction.run());
        actions.add(back);
        actions.add(confirm);
        actions.add(cancel);
        add(actions, BorderLayout.SOUTH);

        category.addActionListener(event -> {
            if (!loading) refreshSubcategories(null);
        });
        type.addActionListener(event -> updateRelevantFields());
        recurring.addActionListener(event -> updateRelevantFields());
    }

    public void load(
            VoiceParseResult result,
            List<Account> accounts,
            List<Category> availableCategories) {
        loading = true;
        try {
            VoiceTransactionDraft draft = result.draft();
            this.categories = List.copyOf(availableCategories);
            transcript.setText("<html><b>Transcript:</b> "
                    + html(result.transcript()) + "</html>");
            replace(source, accounts, draft.getSourceAccount());
            replace(destination, accounts, draft.getDestinationAccount());
            replace(category,
                    categories.stream().filter(value -> !value.isSubcategory())
                            .toList(),
                    draft.getCategory());
            refreshSubcategories(draft.getSubcategory());
            type.setSelectedItem(draft.getTransactionType());
            amount.setText(draft.getAmount() == null
                    ? "" : draft.getAmount().toPlainString());
            currency.setText(draft.getCurrencyCode());
            date.setText(draft.getDate() == null
                    ? "" : draft.getDate().toString());
            time.setText(draft.getTime() == null
                    ? "" : draft.getTime().toString());
            payment.setSelectedItem(draft.getPaymentMethod());
            description.setText(draft.getDescription());
            note.setText(draft.getNote());
            tags.setText(String.join(", ", draft.getTags()));
            recurring.setSelected(draft.isRecurring());
            replace(frequency, List.of(RecurrenceFrequency.values()),
                    draft.getRecurringFrequency());
            nextDue.setText(draft.getNextDueDate() == null
                    ? "" : draft.getNextDueDate().toString());
            showReviewMessages(result.allReviewMessages());
            highlight(draft);
            updateRelevantFields();
        } finally {
            loading = false;
        }
    }

    private void confirm() {
        try {
            VoiceTransactionDraft draft = createDraft();
            List<String> problems = draft.findValidationProblems();
            if (!problems.isEmpty()) {
                showReviewMessages(problems);
                highlight(draft);
                return;
            }
            confirmAction.accept(draft);
        } catch (RuntimeException exception) {
            showReviewMessages(List.of(exception.getMessage() == null
                    ? "Review the highlighted draft fields."
                    : exception.getMessage()));
        }
    }

    private VoiceTransactionDraft createDraft() {
        VoiceTransactionDraft draft = new VoiceTransactionDraft();
        draft.setTransactionType((TransactionType) type.getSelectedItem());
        draft.setAmount(amount.getText().isBlank()
                ? null : new BigDecimal(amount.getText().strip()));
        draft.setCurrencyCode(currency.getText());
        draft.setSourceAccount((Account) source.getSelectedItem());
        draft.setDestinationAccount((Account) destination.getSelectedItem());
        draft.setCategory((Category) category.getSelectedItem());
        draft.setSubcategory((Category) subcategory.getSelectedItem());
        draft.setDate(date.getText().isBlank()
                ? null : LocalDate.parse(date.getText().strip()));
        draft.setTime(time.getText().isBlank()
                ? null : LocalTime.parse(time.getText().strip()));
        draft.setPaymentMethod((PaymentMethod) payment.getSelectedItem());
        draft.setDescription(description.getText());
        draft.setNote(note.getText());
        draft.setTags(tags.getText().isBlank() ? List.of()
                : List.of(tags.getText().split("\\s*,\\s*")));
        draft.setRecurring(recurring.isSelected());
        draft.setRecurringFrequency(
                (RecurrenceFrequency) frequency.getSelectedItem());
        draft.setNextDueDate(nextDue.getText().isBlank()
                ? null : LocalDate.parse(nextDue.getText().strip()));
        return draft;
    }

    private void refreshSubcategories(Category selection) {
        Category parent = (Category) category.getSelectedItem();
        List<Category> children = parent == null ? List.of()
                : categories.stream().filter(value -> value.isSubcategory()
                        && value.getParentIdentifier().orElse("")
                                .equals(parent.getIdentifier())).toList();
        replace(subcategory, children, selection);
    }

    private void updateRelevantFields() {
        TransactionType selected = (TransactionType) type.getSelectedItem();
        boolean expense = selected == TransactionType.EXPENSE;
        boolean transfer = selected == TransactionType.TRANSFER;
        category.setEnabled(expense);
        subcategory.setEnabled(expense && category.getSelectedItem() != null);
        destination.setEnabled(transfer);
        payment.setEnabled(!transfer);
        frequency.setEnabled(recurring.isSelected());
        nextDue.setEnabled(recurring.isSelected());
    }

    private void highlight(VoiceTransactionDraft draft) {
        for (JComponent component : List.of(type, amount, currency, source,
                destination, category, date, time, payment, description,
                frequency, nextDue)) {
            component.putClientProperty(FlatClientProperties.OUTLINE, null);
        }
        if (draft.getTransactionType() == null) error(type);
        if (draft.getAmount() == null || draft.getAmount().signum() <= 0) {
            error(amount);
        }
        if (draft.getCurrencyCode().isBlank()) error(currency);
        if (draft.getSourceAccount() == null) error(source);
        if (draft.getDate() == null) error(date);
        if (draft.getTime() == null) error(time);
        if (draft.getPaymentMethod() == null) error(payment);
        if (draft.getDescription().isBlank()) error(description);
        if (draft.getTransactionType() == TransactionType.EXPENSE
                && draft.getEffectiveCategory() == null) error(category);
        if (draft.getTransactionType() == TransactionType.TRANSFER
                && (draft.getDestinationAccount() == null
                || Objects.equals(draft.getSourceAccount(),
                        draft.getDestinationAccount()))) error(destination);
        if (draft.isRecurring() && draft.getRecurringFrequency() == null) {
            error(frequency);
        }
        if (draft.isRecurring() && draft.getNextDueDate() == null) error(nextDue);
    }

    private void showReviewMessages(List<String> messages) {
        List<String> all = messages.stream().filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .distinct().toList();
        reviewMessages.setText(all.isEmpty()
                ? "<html><font color='#22805f'>Draft is complete. Review every field before confirming.</font></html>"
                : "<html><b>Review required:</b><br>• "
                        + String.join("<br>• ", all.stream()
                                .map(VoiceDraftReviewPanel::html).toList())
                        + "</html>");
    }

    private static int addRow(
            JPanel panel,
            int row,
            String leftLabel,
            JComponent left,
            String rightLabel,
            JComponent right) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = row;
        constraints.insets = new Insets(4, 4, 4, 8);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        panel.add(new JLabel(leftLabel), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(left, constraints);
        constraints.gridx = 2;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(rightLabel), constraints);
        constraints.gridx = 3;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(right, constraints);
        return row + 1;
    }

    private static <T> void replace(
            StyledComboBox<T> box, List<T> items, T selection) {
        DefaultComboBoxModel<T> model = new DefaultComboBoxModel<>();
        model.addElement(null);
        items.forEach(model::addElement);
        box.setModel(model);
        box.setSelectedItem(selection);
    }

    private static void error(JComponent component) {
        component.putClientProperty(FlatClientProperties.OUTLINE, "error");
    }

    private static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
