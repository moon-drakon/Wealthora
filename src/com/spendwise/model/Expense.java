package com.spendwise.model;

import com.spendwise.validation.ExpenseValidator;
import com.spendwise.validation.FinanceValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.List;
import java.util.UUID;

public class Expense {

    private final String id;
    private String description;
    private BigDecimal amount;
    private LocalDate date;
    private Category category;
    private Account account;
    private String notes;
    private PaymentMethod paymentMethod;
    private List<String> tags;

    public Expense(
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            String notes) {
        this(
                UUID.randomUUID().toString(),
                description,
                amount,
                date,
                category,
                Account.DEFAULT,
                notes);
    }

    public Expense(
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            Account account,
            String notes) {
        this(
                UUID.randomUUID().toString(),
                description,
                amount,
                date,
                category,
                account,
                PaymentMethod.UNSPECIFIED,
                List.of(),
                notes);
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
        this.id = ExpenseValidator.validateId(id);
        this.description = ExpenseValidator.validateDescription(description);
        this.amount = ExpenseValidator.validateAmount(amount);
        this.date = ExpenseValidator.validateDate(date);
        this.category = ExpenseValidator.validateCategory(category);
        this.account = Objects.requireNonNull(
                account, "Expense account is required.");
        this.paymentMethod = Objects.requireNonNull(
                paymentMethod, "Expense payment method is required.");
        this.tags = FinanceValidator.validateTags(tags);
        this.notes = ExpenseValidator.validateNotes(notes);
    }

    public Expense(
            String id,
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            String notes) {
        this(
                id,
                description,
                amount,
                date,
                category,
                Account.DEFAULT,
                notes);
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

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDate() {
        return date;
    }

    public Category getCategory() {
        return category;
    }

    public Account getAccount() {
        return account;
    }

    public String getNotes() {
        return notes;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public List<String> getTags() {
        return tags;
    }

    public void updateDetails(
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            String notes) {
        updateDetails(
                description, amount, date, category, account, notes);
    }

    public void updateDetails(
            String description,
            BigDecimal amount,
            LocalDate date,
            Category category,
            Account account,
            String notes) {
        String validatedDescription = ExpenseValidator.validateDescription(description);
        BigDecimal validatedAmount = ExpenseValidator.validateAmount(amount);
        LocalDate validatedDate = ExpenseValidator.validateDate(date);
        Category validatedCategory = ExpenseValidator.validateCategory(category);
        Account validatedAccount = Objects.requireNonNull(
                account, "Expense account is required.");
        String validatedNotes = ExpenseValidator.validateNotes(notes);

        this.description = validatedDescription;
        this.amount = validatedAmount;
        this.date = validatedDate;
        this.category = validatedCategory;
        this.account = validatedAccount;
        this.notes = validatedNotes;
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
        updateDetails(description, amount, date, category, account, notes);
        this.paymentMethod = Objects.requireNonNull(
                paymentMethod, "Expense payment method is required.");
        this.tags = FinanceValidator.validateTags(tags);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Expense expense)) {
            return false;
        }
        return id.equals(expense.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Expense{"
                + "id='" + id + '\''
                + ", description='" + description + '\''
                + ", amount=" + amount
                + ", date=" + date
                + ", category=" + category
                + ", account=" + account
                + '}';
    }
}
