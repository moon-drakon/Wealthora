package com.spendwise.voice;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.PaymentMethod;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public final class VoiceTransactionDraft {

    private TransactionType transactionType;
    private BigDecimal amount;
    private String currencyCode = Account.DEFAULT_CURRENCY_CODE;
    private Account sourceAccount;
    private Account destinationAccount;
    private Category category;
    private Category subcategory;
    private LocalDate date;
    private LocalTime time = LocalTime.now().withSecond(0).withNano(0);
    private PaymentMethod paymentMethod = PaymentMethod.UNSPECIFIED;
    private String description = "";
    private String note = "";
    private List<String> tags = List.of();
    private boolean recurring;
    private RecurrenceFrequency recurringFrequency;
    private LocalDate nextDueDate;

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode == null
                ? "" : currencyCode.strip().toUpperCase(Locale.ROOT);
    }

    public Account getSourceAccount() {
        return sourceAccount;
    }

    public void setSourceAccount(Account sourceAccount) {
        this.sourceAccount = sourceAccount;
    }

    public Account getDestinationAccount() {
        return destinationAccount;
    }

    public void setDestinationAccount(Account destinationAccount) {
        this.destinationAccount = destinationAccount;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Category getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(Category subcategory) {
        this.subcategory = subcategory;
    }

    public Category getEffectiveCategory() {
        return subcategory == null ? category : subcategory;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = clean(description);
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = clean(note);
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        if (tags == null) {
            this.tags = List.of();
            return;
        }
        this.tags = tags.stream().map(VoiceTransactionDraft::clean)
                .filter(value -> !value.isEmpty()).distinct().toList();
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public RecurrenceFrequency getRecurringFrequency() {
        return recurringFrequency;
    }

    public void setRecurringFrequency(
            RecurrenceFrequency recurringFrequency) {
        this.recurringFrequency = recurringFrequency;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public void setNextDueDate(LocalDate nextDueDate) {
        this.nextDueDate = nextDueDate;
    }

    public List<String> findValidationProblems() {
        List<String> problems = new ArrayList<>();
        if (transactionType == null) problems.add("Transaction type is required.");
        if (amount == null) {
            problems.add("Amount is required.");
        } else if (amount.signum() <= 0) {
            problems.add("Amount must be greater than zero.");
        }
        if (!validCurrency(currencyCode)) {
            problems.add("A valid currency code is required.");
        }
        if (sourceAccount == null) problems.add("Source account is required.");
        if (date == null) problems.add("Date is required.");
        if (time == null) problems.add("Time is required.");
        if (paymentMethod == null) problems.add("Payment method is required.");
        if (description.isBlank()) problems.add("Description is required.");
        if (transactionType == TransactionType.EXPENSE
                && getEffectiveCategory() == null) {
            problems.add("Category is required for an expense.");
        }
        if (transactionType == TransactionType.TRANSFER) {
            if (destinationAccount == null) {
                problems.add("Destination account is required for a transfer.");
            } else if (destinationAccount.equals(sourceAccount)) {
                problems.add("Transfer accounts must be different.");
            }
        }
        if (subcategory != null && (!subcategory.isSubcategory()
                || category == null
                || !subcategory.getParentIdentifier().orElse("")
                        .equals(category.getIdentifier()))) {
            problems.add("Subcategory must belong to the selected category.");
        }
        if (sourceAccount != null && validCurrency(currencyCode)
                && !sourceAccount.getCurrencyCode().equals(currencyCode)) {
            problems.add("Currency must match the selected source account.");
        }
        if (transactionType == TransactionType.TRANSFER
                && destinationAccount != null && validCurrency(currencyCode)
                && !destinationAccount.getCurrencyCode().equals(currencyCode)) {
            problems.add("Currency must match the destination account.");
        }
        if (recurring) {
            if (recurringFrequency == null) {
                problems.add("Recurring frequency is required.");
            }
            if (nextDueDate == null) {
                problems.add("Next due date is required.");
            }
        }
        return List.copyOf(problems);
    }

    public boolean isComplete() {
        return findValidationProblems().isEmpty();
    }

    private static boolean validCurrency(String value) {
        try {
            Currency.getInstance(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
