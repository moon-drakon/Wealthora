package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.CardType;
import com.spendwise.model.PaymentCard;
import com.spendwise.repository.PaymentCardRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class PaymentCardService {

    private final PaymentCardRepository repository;
    private final AccountService accountService;

    public PaymentCardService(
            PaymentCardRepository repository, AccountService accountService) {
        this.repository = Objects.requireNonNull(repository);
        this.accountService = Objects.requireNonNull(accountService);
    }

    public List<PaymentCard> listAll() {
        return List.copyOf(repository.findAll());
    }

    public PaymentCard addCard(
            String displayName,
            String bankName,
            CardType type,
            String lastFourDigits,
            BigDecimal creditLimit,
            Integer billingDay,
            Integer dueDay,
            Account cardAccount,
            Account linkedPaymentAccount) {
        PaymentCard card = new PaymentCard(displayName, bankName, type,
                lastFourDigits, creditLimit, billingDay, dueDay,
                accountService.requireSelectable(cardAccount),
                linkedPaymentAccount == null ? null
                        : accountService.requireSelectable(linkedPaymentAccount),
                true);
        repository.add(card);
        return card;
    }

    public PaymentCard updateCard(
            String identifier,
            String displayName,
            String bankName,
            CardType type,
            String lastFourDigits,
            BigDecimal creditLimit,
            Integer billingDay,
            Integer dueDay,
            Account cardAccount,
            Account linkedPaymentAccount) {
        PaymentCard existing = repository.findById(identifier)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "Payment card was not found."));
        PaymentCard card = new PaymentCard(existing.getIdentifier(), displayName,
                bankName, type, lastFourDigits, creditLimit, billingDay, dueDay,
                accountService.requireSelectableOrHistorical(
                        cardAccount, existing.getCardAccount()),
                linkedPaymentAccount == null ? null
                        : accountService.requireSelectableOrHistorical(
                                linkedPaymentAccount,
                                existing.getLinkedPaymentAccount().orElse(null)),
                existing.isActive());
        repository.update(card);
        return card;
    }

    public PaymentCard setActive(String identifier, boolean active) {
        PaymentCard existing = repository.findById(identifier)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "Payment card was not found."));
        if (active) {
            accountService.requireSelectable(existing.getCardAccount());
            existing.getLinkedPaymentAccount()
                    .ifPresent(accountService::requireSelectable);
        }
        PaymentCard replacement = existing.withActive(active);
        repository.update(replacement);
        return replacement;
    }
}
