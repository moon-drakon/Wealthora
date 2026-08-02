package com.spendwise.service;

import com.spendwise.model.DebtRecord;
import com.spendwise.model.DebtRepayment;
import com.spendwise.model.DebtStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record DebtProgress(
        DebtRecord debt,
        BigDecimal repaidAmount,
        BigDecimal remainingAmount,
        DebtStatus status,
        List<DebtRepayment> repayments) {

    public DebtProgress {
        Objects.requireNonNull(debt); Objects.requireNonNull(repaidAmount);
        Objects.requireNonNull(remainingAmount); Objects.requireNonNull(status);
        repayments = List.copyOf(Objects.requireNonNull(repayments));
    }

    public static DebtProgress from(
            DebtRecord debt, List<DebtRepayment> repayments,
            LocalDate statusDate) {
        BigDecimal repaid = repayments.stream().map(DebtRepayment::getAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal remaining = debt.getOriginalAmount().subtract(repaid);
        if (remaining.signum() < 0) remaining = BigDecimal.ZERO.setScale(2);
        DebtStatus status;
        if (remaining.signum() == 0) status = DebtStatus.PAID;
        else if (debt.getDueDate().isBefore(statusDate)) status = DebtStatus.OVERDUE;
        else if (repaid.signum() > 0) status = DebtStatus.PARTIALLY_REPAID;
        else status = DebtStatus.OPEN;
        return new DebtProgress(debt, repaid, remaining, status, repayments);
    }
}
