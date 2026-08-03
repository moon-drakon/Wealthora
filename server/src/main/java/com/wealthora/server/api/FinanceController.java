package com.wealthora.server.api;

import com.wealthora.server.api.FinanceDtos.AccountRequest;
import com.wealthora.server.api.FinanceDtos.AccountResponse;
import com.wealthora.server.api.FinanceDtos.BudgetPlanRequest;
import com.wealthora.server.api.FinanceDtos.BudgetPlanResponse;
import com.wealthora.server.api.FinanceDtos.CategoryRequest;
import com.wealthora.server.api.FinanceDtos.CategoryResponse;
import com.wealthora.server.api.FinanceDtos.ContributionRequest;
import com.wealthora.server.api.FinanceDtos.ContributionResponse;
import com.wealthora.server.api.FinanceDtos.DashboardSummaryResponse;
import com.wealthora.server.api.FinanceDtos.DebtRequest;
import com.wealthora.server.api.FinanceDtos.DebtResponse;
import com.wealthora.server.api.FinanceDtos.DefaultAccountRequest;
import com.wealthora.server.api.FinanceDtos.DefaultAccountResponse;
import com.wealthora.server.api.FinanceDtos.ExpenseRequest;
import com.wealthora.server.api.FinanceDtos.GoalRequest;
import com.wealthora.server.api.FinanceDtos.GoalResponse;
import com.wealthora.server.api.FinanceDtos.IncomeRequest;
import com.wealthora.server.api.FinanceDtos.MonthlyBudgetRequest;
import com.wealthora.server.api.FinanceDtos.MonthlyBudgetResponse;
import com.wealthora.server.api.FinanceDtos.PageResponse;
import com.wealthora.server.api.FinanceDtos.RecurringRequest;
import com.wealthora.server.api.FinanceDtos.RecurringResponse;
import com.wealthora.server.api.FinanceDtos.RepaymentRequest;
import com.wealthora.server.api.FinanceDtos.RepaymentResponse;
import com.wealthora.server.api.FinanceDtos.TransactionResponse;
import com.wealthora.server.api.FinanceDtos.TransferRequest;
import com.wealthora.server.api.FinanceDtos.TransferResponse;
import com.wealthora.server.security.SessionPrincipal;
import com.wealthora.server.service.FinanceLedgerService;
import com.wealthora.server.service.FinancePlanningService;
import com.wealthora.server.service.FinanceWorkspaceService;
import jakarta.validation.Valid;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceWorkspaceService workspace;
    private final FinanceLedgerService ledger;
    private final FinancePlanningService planning;

    public FinanceController(FinanceWorkspaceService workspace,
            FinanceLedgerService ledger, FinancePlanningService planning) {
        this.workspace = workspace;
        this.ledger = ledger;
        this.planning = planning;
    }

    @GetMapping("/accounts")
    PageResponse<AccountResponse> accounts(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return workspace.accounts(principal, page, size);
    }

    @GetMapping("/accounts/default")
    DefaultAccountResponse defaultAccount(
            @AuthenticationPrincipal SessionPrincipal principal) {
        return workspace.defaultAccount(principal);
    }

    @PutMapping("/accounts/default")
    DefaultAccountResponse setDefaultAccount(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody DefaultAccountRequest request) {
        return workspace.setDefaultAccount(principal, request.externalId());
    }

    @GetMapping("/accounts/{externalId}")
    AccountResponse account(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId) {
        return workspace.account(principal, externalId);
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse createAccount(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody AccountRequest request) {
        return workspace.createAccount(principal, request);
    }

    @PutMapping("/accounts/{externalId}")
    AccountResponse updateAccount(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId,
            @Valid @RequestBody AccountRequest request) {
        return workspace.updateAccount(principal, externalId, request);
    }

    @GetMapping("/categories")
    PageResponse<CategoryResponse> categories(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return workspace.categories(principal, page, size);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryResponse createCategory(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody CategoryRequest request) {
        return workspace.createCategory(principal, request);
    }

    @PutMapping("/categories/{externalId}")
    CategoryResponse updateCategory(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId,
            @Valid @RequestBody CategoryRequest request) {
        return workspace.updateCategory(principal, externalId, request);
    }

    @GetMapping("/transactions")
    PageResponse<TransactionResponse> transactions(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ledger.transactions(principal, page, size);
    }

    @GetMapping("/expenses")
    PageResponse<TransactionResponse> expenses(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ledger.expenses(principal, page, size);
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse createExpense(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody ExpenseRequest request) {
        return ledger.createExpense(principal, request);
    }

    @PutMapping("/expenses/{externalId}")
    TransactionResponse updateExpense(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId,
            @Valid @RequestBody ExpenseRequest request) {
        return ledger.updateExpense(principal, externalId, request);
    }

    @DeleteMapping("/expenses/{externalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteExpense(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId) {
        ledger.deleteExpense(principal, externalId);
    }

    @GetMapping("/income")
    PageResponse<TransactionResponse> income(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ledger.income(principal, page, size);
    }

    @PostMapping("/income")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse createIncome(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody IncomeRequest request) {
        return ledger.createIncome(principal, request);
    }

    @PutMapping("/income/{externalId}")
    TransactionResponse updateIncome(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId,
            @Valid @RequestBody IncomeRequest request) {
        return ledger.updateIncome(principal, externalId, request);
    }

    @DeleteMapping("/income/{externalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteIncome(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId) {
        ledger.deleteIncome(principal, externalId);
    }

    @GetMapping("/transfers")
    PageResponse<TransferResponse> transfers(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ledger.transfers(principal, page, size);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    TransferResponse createTransfer(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody TransferRequest request) {
        return ledger.createTransfer(principal, request);
    }

    @PutMapping("/transfers/{externalId}")
    TransferResponse updateTransfer(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId,
            @Valid @RequestBody TransferRequest request) {
        return ledger.updateTransfer(principal, externalId, request);
    }

    @DeleteMapping("/transfers/{externalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTransfer(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId) {
        ledger.deleteTransfer(principal, externalId);
    }

    @GetMapping("/budgets/monthly")
    PageResponse<MonthlyBudgetResponse> monthlyBudgets(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return planning.monthlyBudgets(principal, page, size);
    }

    @PutMapping("/budgets/monthly/{month}")
    MonthlyBudgetResponse saveMonthlyBudget(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @Valid @RequestBody MonthlyBudgetRequest request) {
        return planning.saveMonthlyBudget(principal, month, request);
    }

    @DeleteMapping("/budgets/monthly/{month}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteMonthlyBudget(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        planning.deleteMonthlyBudget(principal, month);
    }

    @GetMapping("/budgets/plans")
    PageResponse<BudgetPlanResponse> budgetPlans(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return planning.budgetPlans(principal, page, size);
    }

    @PostMapping("/budgets/plans")
    @ResponseStatus(HttpStatus.CREATED)
    BudgetPlanResponse createBudgetPlan(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody BudgetPlanRequest request) {
        return planning.createBudgetPlan(principal, request);
    }

    @PutMapping("/budgets/plans/{externalId}")
    BudgetPlanResponse updateBudgetPlan(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId,
            @Valid @RequestBody BudgetPlanRequest request) {
        return planning.updateBudgetPlan(principal, externalId, request);
    }

    @GetMapping("/recurring")
    PageResponse<RecurringResponse> recurring(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return planning.recurring(principal, page, size);
    }

    @PostMapping("/recurring")
    @ResponseStatus(HttpStatus.CREATED)
    RecurringResponse createRecurring(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody RecurringRequest request) {
        return planning.createRecurring(principal, request);
    }

    @PutMapping("/recurring/{externalId}")
    RecurringResponse updateRecurring(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId,
            @Valid @RequestBody RecurringRequest request) {
        return planning.updateRecurring(principal, externalId, request);
    }

    @GetMapping("/goals")
    PageResponse<GoalResponse> goals(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return planning.goals(principal, page, size);
    }

    @PostMapping("/goals")
    @ResponseStatus(HttpStatus.CREATED)
    GoalResponse createGoal(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody GoalRequest request) {
        return planning.createGoal(principal, request);
    }

    @PutMapping("/goals/{externalId}")
    GoalResponse updateGoal(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId,
            @Valid @RequestBody GoalRequest request) {
        return planning.updateGoal(principal, externalId, request);
    }

    @GetMapping("/goals/{externalId}/contributions")
    PageResponse<ContributionResponse> contributions(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return planning.contributions(principal, externalId, page, size);
    }

    @PostMapping("/goals/{externalId}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    ContributionResponse addContribution(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId,
            @Valid @RequestBody ContributionRequest request) {
        return planning.addContribution(principal, externalId, request);
    }

    @GetMapping("/debts")
    PageResponse<DebtResponse> debts(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return planning.debts(principal, page, size);
    }

    @PostMapping("/debts")
    @ResponseStatus(HttpStatus.CREATED)
    DebtResponse createDebt(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody DebtRequest request) {
        return planning.createDebt(principal, request);
    }

    @PutMapping("/debts/{externalId}")
    DebtResponse updateDebt(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId,
            @Valid @RequestBody DebtRequest request) {
        return planning.updateDebt(principal, externalId, request);
    }

    @GetMapping("/debts/{externalId}/repayments")
    PageResponse<RepaymentResponse> repayments(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return planning.repayments(principal, externalId, page, size);
    }

    @PostMapping("/debts/{externalId}/repayments")
    @ResponseStatus(HttpStatus.CREATED)
    RepaymentResponse addRepayment(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable String externalId,
            @Valid @RequestBody RepaymentRequest request) {
        return planning.addRepayment(principal, externalId, request);
    }

    @GetMapping("/reports/dashboard")
    DashboardSummaryResponse dashboard(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ledger.dashboard(principal, month);
    }
}
