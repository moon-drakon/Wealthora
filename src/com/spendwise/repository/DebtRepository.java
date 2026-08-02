package com.spendwise.repository;

import com.spendwise.model.DebtRecord;
import com.spendwise.model.DebtRepayment;
import java.util.List;
import java.util.Optional;

public interface DebtRepository {
    List<DebtRecord> findAllDebts();
    Optional<DebtRecord> findDebtById(String identifier);
    List<DebtRepayment> findRepayments(String debtIdentifier);
    void addDebt(DebtRecord debt);
    void updateDebt(DebtRecord debt);
    void addRepayment(DebtRepayment repayment);
}
