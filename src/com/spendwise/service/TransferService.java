package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Transfer;
import com.spendwise.repository.TransferRepository;
import com.spendwise.validation.FinanceValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TransferService {

    private final TransferRepository repository;
    private final AccountService accountService;

    public TransferService(
            TransferRepository repository, AccountService accountService) {
        this.repository = Objects.requireNonNull(
                repository, "Transfer repository is required.");
        this.accountService = Objects.requireNonNull(
                accountService, "Account service is required.");
    }

    public List<Transfer> getAllTransfers() {
        return List.copyOf(repository.findAll());
    }

    public Optional<Transfer> findById(String id) {
        return repository.findById(validateId(id));
    }

    public Transfer createTransfer(
            LocalDate date,
            BigDecimal amount,
            Account sourceAccount,
            Account destinationAccount,
            String note) {
        Account validatedSource =
                accountService.requireSelectable(sourceAccount);
        Account validatedDestination =
                accountService.requireSelectable(destinationAccount);
        Transfer transfer = new Transfer(
                date,
                amount,
                validatedSource,
                validatedDestination,
                note);
        repository.add(transfer);
        return transfer;
    }

    Transfer createTransferWithId(
            String id,
            LocalDate date,
            BigDecimal amount,
            Account sourceAccount,
            Account destinationAccount,
            String note) {
        Account validatedSource =
                accountService.requireSelectable(sourceAccount);
        Account validatedDestination =
                accountService.requireSelectable(destinationAccount);
        Transfer transfer = new Transfer(
                id,
                date,
                amount,
                validatedSource,
                validatedDestination,
                note);
        repository.add(transfer);
        return transfer;
    }

    public Transfer updateTransfer(
            String id,
            LocalDate date,
            BigDecimal amount,
            Account sourceAccount,
            Account destinationAccount,
            String note) {
        String normalizedId = validateId(id);
        Transfer existing = repository.findById(normalizedId)
                .orElseThrow(() -> new FinanceNotFoundException(
                    "Transfer was not found."));
        Account validatedSource =
                accountService.requireSelectableOrHistorical(
                        sourceAccount, existing.getSourceAccount());
        Account validatedDestination =
                accountService.requireSelectableOrHistorical(
                        destinationAccount,
                        existing.getDestinationAccount());
        Transfer replacement = new Transfer(
                normalizedId,
                date,
                amount,
                validatedSource,
                validatedDestination,
                existing.getTags(),
                note);
        repository.update(replacement);
        return replacement;
    }

    public Transfer createTransfer(
            LocalDate date,
            BigDecimal amount,
            Account sourceAccount,
            Account destinationAccount,
            List<String> tags,
            String note) {
        Transfer transfer = new Transfer(date, amount,
                accountService.requireSelectable(sourceAccount),
                accountService.requireSelectable(destinationAccount),
                tags, note);
        repository.add(transfer);
        return transfer;
    }

    public Transfer updateTransfer(
            String id,
            LocalDate date,
            BigDecimal amount,
            Account sourceAccount,
            Account destinationAccount,
            List<String> tags,
            String note) {
        String normalizedId = validateId(id);
        Transfer existing = repository.findById(normalizedId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "Transfer was not found."));
        Account source = accountService.requireSelectableOrHistorical(
                sourceAccount, existing.getSourceAccount());
        Account destination = accountService.requireSelectableOrHistorical(
                destinationAccount, existing.getDestinationAccount());
        Transfer replacement = new Transfer(normalizedId, date, amount,
                source, destination, tags, note);
        repository.update(replacement);
        return replacement;
    }

    public boolean deleteTransfer(String id) {
        return repository.deleteById(validateId(id));
    }

    private static String validateId(String id) {
        return FinanceValidator.validateIdentifier(
                id, "Transfer", "TRANSFER_");
    }
}
