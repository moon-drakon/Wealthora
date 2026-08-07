package com.spendwise.model;

import com.spendwise.validation.ExpenseValidator;
import com.spendwise.validation.FinanceValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Expense extends Transaction {

    private String description;

    public Expense(
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            String notes) {
        this(UUID.randomUUID().toString(), description, amount, date,
                category, Account.DEFAULT, notes);
    }

    public Expense(
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            Account account,
            String notes) {
        this(UUID.randomUUID().toString(), description, amount, date,
                category, account, PaymentMethod.UNSPECIFIED, List.of(), notes);
    }

    public Expense(
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            Account account,
            PaymentMethod paymentMethod,
            List<String> tags,
            String notes) {
        this(UUID.randomUUID().toString(), description, amount, date,
                category, account, paymentMethod, tags, notes);
    }

    public Expense(
            String id,
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            Account account,
            PaymentMethod paymentMethod,
            List<String> tags,
            String notes) {
        super(
                ExpenseValidator.validateId(id),
                ExpenseValidator.validateAmount(amount),
                ExpenseValidator.validateDate(date),
                ExpenseValidator.validateCategory(category),
                Objects.requireNonNull(account,
                        "Expense account is required."),
                Objects.requireNonNull(paymentMethod,
                        "Expense payment method is required."),
                FinanceValidator.validateTags(tags),
                ExpenseValidator.validateNotes(notes));
        this.description = ExpenseValidator.validateDescription(description);
    }

    public Expense(
            String id,
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            String notes) {
        this(id, description, amount, date, category, Account.DEFAULT, notes);
    }

    public Expense(
            String id,
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            Account account,
            String notes) {
        this(id, description, amount, date, category, account,
                PaymentMethod.UNSPECIFIED, List.of(), notes);
    }

    @Override
    public String getDescription() {
        return description;
    }

    public String getNotes() {
        return getNote();
    }

    @Override
    public TransactionType getType() {
        return TransactionType.EXPENSE;
    }

    @Override
    public BigDecimal calculateImpact() {
        return getAmount().negate();
    }

    public void updateDetails(
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            String notes) {
        updateDetails(description, amount, date, category, getAccount(), notes);
    }

    public void updateDetails(
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            Account account,
            String notes) {
        String validatedDescription =
                ExpenseValidator.validateDescription(description);
        BigDecimal validatedAmount = ExpenseValidator.validateAmount(amount);
        LocalDate validatedDate = ExpenseValidator.validateDate(date);
        Category validatedCategory =
                ExpenseValidator.validateCategory(category);
        Account validatedAccount = Objects.requireNonNull(
                account, "Expense account is required.");
        String validatedNotes = ExpenseValidator.validateNotes(notes);
        updateTransactionDetails(validatedAmount, validatedDate,
                validatedCategory, validatedAccount, getPaymentMethod(),
                getTags(), validatedNotes);
        this.description = validatedDescription;
    }

    public void updateDetails(
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            Account account,
            PaymentMethod paymentMethod,
            List<String> tags,
            String notes) {
        String validatedDescription =
                ExpenseValidator.validateDescription(description);
        BigDecimal validatedAmount = ExpenseValidator.validateAmount(amount);
        LocalDate validatedDate = ExpenseValidator.validateDate(date);
        Category validatedCategory =
                ExpenseValidator.validateCategory(category);
        Account validatedAccount = Objects.requireNonNull(
                account, "Expense account is required.");
        PaymentMethod validatedPaymentMethod = Objects.requireNonNull(
                paymentMethod, "Expense payment method is required.");
        List<String> validatedTags = FinanceValidator.validateTags(tags);
        String validatedNotes = ExpenseValidator.validateNotes(notes);
        updateTransactionDetails(validatedAmount, validatedDate,
                validatedCategory, validatedAccount, validatedPaymentMethod,
                validatedTags, validatedNotes);
        this.description = validatedDescription;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof Expense expense
                        && getId().equals(expense.getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public String toString() {
        return "Expense{"
                + "id='" + getId() + '\''
                + ", description='" + description + '\''
                + ", amount=" + getAmount()
                + ", date=" + getDate()
                + ", category=" + getCategory()
                + ", account=" + getAccount()
                + '}';
    }
}
