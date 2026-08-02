package com.spendwise.model;

import com.spendwise.validation.FinanceValidator;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PaymentCard {

    private static final String ID_PREFIX = "CARD_";

    private final String identifier;
    private final String displayName;
    private final String bankName;
    private final CardType cardType;
    private final String lastFourDigits;
    private final BigDecimal creditLimit;
    private final Integer billingDay;
    private final Integer dueDay;
    private final Account cardAccount;
    private final Account linkedPaymentAccount;
    private final boolean active;

    public PaymentCard(
            String displayName,
            String bankName,
            CardType cardType,
            String lastFourDigits,
            BigDecimal creditLimit,
            Integer billingDay,
            Integer dueDay,
            Account cardAccount,
            Account linkedPaymentAccount,
            boolean active) {
        this(ID_PREFIX + UUID.randomUUID().toString().replace("-", "")
                .toUpperCase(Locale.ROOT), displayName, bankName, cardType,
                lastFourDigits, creditLimit, billingDay, dueDay, cardAccount,
                linkedPaymentAccount, active);
    }

    public PaymentCard(
            String identifier,
            String displayName,
            String bankName,
            CardType cardType,
            String lastFourDigits,
            BigDecimal creditLimit,
            Integer billingDay,
            Integer dueDay,
            Account cardAccount,
            Account linkedPaymentAccount,
            boolean active) {
        this.identifier = FinanceValidator.validateIdentifier(
                identifier, "Card", ID_PREFIX);
        this.displayName = FinanceValidator.validateRequiredText(
                displayName, "Card name", FinanceValidator.MAX_NAME_LENGTH);
        this.bankName = FinanceValidator.validateRequiredText(
                bankName, "Bank name", FinanceValidator.MAX_NAME_LENGTH);
        this.cardType = Objects.requireNonNull(cardType, "Card type is required.");
        this.lastFourDigits = validateLastFour(lastFourDigits);
        this.cardAccount = validateCardAccount(cardAccount, cardType);
        this.linkedPaymentAccount = validatePaymentAccount(
                linkedPaymentAccount, this.cardAccount);
        if (cardType == CardType.CREDIT) {
            this.creditLimit = FinanceValidator.validatePositiveAmount(
                    creditLimit, "Credit limit");
            this.billingDay = validateDay(billingDay, "Billing day");
            this.dueDay = validateDay(dueDay, "Due day");
        } else {
            if (creditLimit != null || billingDay != null || dueDay != null) {
                throw new ValidationException(
                        "Debit cards cannot have a credit limit, billing day, or due day.");
            }
            this.creditLimit = null;
            this.billingDay = null;
            this.dueDay = null;
        }
        this.active = active;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBankName() {
        return bankName;
    }

    public CardType getCardType() {
        return cardType;
    }

    public String getLastFourDigits() {
        return lastFourDigits;
    }

    public Optional<BigDecimal> getCreditLimit() {
        return Optional.ofNullable(creditLimit);
    }

    public Optional<Integer> getBillingDay() {
        return Optional.ofNullable(billingDay);
    }

    public Optional<Integer> getDueDay() {
        return Optional.ofNullable(dueDay);
    }

    public Account getCardAccount() {
        return cardAccount;
    }

    public Optional<Account> getLinkedPaymentAccount() {
        return Optional.ofNullable(linkedPaymentAccount);
    }

    public boolean isActive() {
        return active;
    }

    public PaymentCard withActive(boolean newActive) {
        return new PaymentCard(identifier, displayName, bankName, cardType,
                lastFourDigits, creditLimit, billingDay, dueDay, cardAccount,
                linkedPaymentAccount, newActive);
    }

    private static String validateLastFour(String digits) {
        if (digits == null || !digits.matches("[0-9]{4}")) {
            throw new ValidationException(
                    "Card last four digits must contain exactly four numbers.");
        }
        return digits;
    }

    private static Integer validateDay(Integer day, String fieldName) {
        if (day == null || day < 1 || day > 31) {
            throw new ValidationException(fieldName + " must be from 1 to 31.");
        }
        return day;
    }

    private static Account validateCardAccount(
            Account account, CardType cardType) {
        Account required = Objects.requireNonNull(
                account, "Card account is required.");
        AccountType requiredType = cardType == CardType.CREDIT
                ? AccountType.CREDIT_CARD : AccountType.DEBIT_CARD;
        if (required.getType() != requiredType) {
            throw new ValidationException(
                    "The card account type must be "
                    + requiredType.getDisplayName() + ".");
        }
        return required;
    }

    private static Account validatePaymentAccount(
            Account paymentAccount, Account cardAccount) {
        if (paymentAccount != null && paymentAccount.equals(cardAccount)) {
            throw new ValidationException(
                    "The linked payment account must differ from the card account.");
        }
        return paymentAccount;
    }
}
