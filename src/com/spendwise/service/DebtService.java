package com.spendwise.service;

import com.spendwise.model.DebtDirection;
import com.spendwise.model.DebtRecord;
import com.spendwise.model.DebtRepayment;
import com.spendwise.repository.DebtRepository;
import com.spendwise.validation.FinanceValidator;
import com.spendwise.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class DebtService {
    private final DebtRepository repository;

    public DebtService(DebtRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public List<DebtProgress> listProgress(LocalDate statusDate) {
        LocalDate required = Objects.requireNonNull(statusDate);
        return repository.findAllDebts().stream()
                .map(debt -> DebtProgress.from(debt,
                        repository.findRepayments(debt.getIdentifier()), required))
                .sorted(java.util.Comparator
                        .comparing((DebtProgress item) ->
                                item.status() == com.spendwise.model.DebtStatus.PAID)
                        .thenComparing(item -> item.debt().getDueDate())
                        .thenComparing(item -> item.debt().getIdentifier()))
                .toList();
    }

    public DebtRecord addDebt(
            DebtDirection direction, String counterparty,
            BigDecimal originalAmount, LocalDate dueDate, String note) {
        DebtRecord debt = DebtRecord.create(direction, counterparty,
                originalAmount, dueDate, note);
        repository.addDebt(debt);
        return debt;
    }

    public DebtRecord updateDebt(
            String identifier, DebtDirection direction, String counterparty,
            BigDecimal originalAmount, LocalDate dueDate, String note) {
        DebtRecord existing = requireDebt(identifier);
        BigDecimal alreadyRepaid = getProgress(existing.getIdentifier(),
                LocalDate.now()).repaidAmount();
        BigDecimal normalizedOriginal = FinanceValidator.validatePositiveAmount(
                originalAmount, "Original debt amount");
        if (normalizedOriginal.compareTo(alreadyRepaid) < 0) {
            throw new ValidationException(
                    "Original amount cannot be below recorded repayments.");
        }
        DebtRecord replacement = new DebtRecord(existing.getIdentifier(),
                direction, counterparty, normalizedOriginal, dueDate, note);
        repository.updateDebt(replacement);
        return replacement;
    }

    public DebtRepayment addRepayment(
            String debtIdentifier, LocalDate date,
            BigDecimal amount, String note) {
        DebtProgress progress = getProgress(debtIdentifier, date);
        BigDecimal normalized = FinanceValidator.validatePositiveAmount(
                amount, "Repayment amount");
        if (normalized.compareTo(progress.remainingAmount()) > 0) {
            throw new ValidationException(
                    "Repayment cannot exceed the remaining amount.");
        }
        DebtRepayment repayment = DebtRepayment.create(
                progress.debt().getIdentifier(), date, normalized, note);
        repository.addRepayment(repayment);
        return repayment;
    }

    public DebtProgress getProgress(String identifier, LocalDate statusDate) {
        DebtRecord debt = requireDebt(identifier);
        return DebtProgress.from(debt,
                repository.findRepayments(debt.getIdentifier()),
                Objects.requireNonNull(statusDate));
    }

    private DebtRecord requireDebt(String identifier) {
        String required = FinanceValidator.validateIdentifier(
                identifier, "Debt", "DEBT_");
        return repository.findDebtById(required).orElseThrow(() ->
                new FinanceNotFoundException("Debt record was not found."));
    }
}
