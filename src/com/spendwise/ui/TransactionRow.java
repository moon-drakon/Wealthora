package com.spendwise.ui;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Transfer;
import java.math.BigDecimal;
import java.time.LocalDate;

final class TransactionRow {

    enum Type {
        INCOME("Income"), EXPENSE("Expense"), TRANSFER("Transfer");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Type type;
    private final Object source;
    private final String identifier;
    private final LocalDate date;
    private final String description;
    private final Category category;
    private final Account account;
    private final Account destinationAccount;
    private final BigDecimal amount;

    private TransactionRow(
            Type type,
            Object source,
            String identifier,
            LocalDate date,
            String description,
            Category category,
            Account account,
            Account destinationAccount,
            BigDecimal amount) {
        this.type = type;
        this.source = source;
        this.identifier = identifier;
        this.date = date;
        this.description = description;
        this.category = category;
        this.account = account;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
    }

    static TransactionRow from(Expense expense) {
        return new TransactionRow(Type.EXPENSE, expense, expense.getId(),
                expense.getDate(), expense.getDescription(),
                expense.getCategory(), expense.getAccount(), null,
                expense.getAmount());
    }

    static TransactionRow from(Income income) {
        return new TransactionRow(Type.INCOME, income, income.getId(),
                income.getDate(), income.getSource(), null,
                income.getAccount(), null, income.getAmount());
    }

    static TransactionRow from(Transfer transfer) {
        return new TransactionRow(Type.TRANSFER, transfer, transfer.getId(),
                transfer.getDate(), "Account transfer", null,
                transfer.getSourceAccount(), transfer.getDestinationAccount(),
                transfer.getAmount());
    }

    Type type() {
        return type;
    }

    Object source() {
        return source;
    }

    String identifier() {
        return identifier;
    }

    LocalDate date() {
        return date;
    }

    String description() {
        return description;
    }

    Category category() {
        return category;
    }

    Account account() {
        return account;
    }

    Account destinationAccount() {
        return destinationAccount;
    }

    BigDecimal amount() {
        return amount;
    }

    String accountDisplay() {
        return destinationAccount == null
                ? account.getDisplayName()
                : account.getDisplayName() + "  →  "
                        + destinationAccount.getDisplayName();
    }

    String categoryDisplay() {
        return category == null ? "—" : category.getDisplayName();
    }

    String amountDisplay() {
        return switch (type) {
            case INCOME -> "+" + amount.toPlainString();
            case EXPENSE -> "−" + amount.toPlainString();
            case TRANSFER -> amount.toPlainString();
        };
    }

    boolean involves(Account filter) {
        return filter == null || account.equals(filter)
                || filter.equals(destinationAccount);
    }
}
