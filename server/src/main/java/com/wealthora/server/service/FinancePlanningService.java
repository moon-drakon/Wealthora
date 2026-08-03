package com.wealthora.server.service;

import static com.wealthora.server.api.FinanceDtos.BudgetPlanRequest;
import static com.wealthora.server.api.FinanceDtos.BudgetPlanResponse;
import static com.wealthora.server.api.FinanceDtos.ContributionRequest;
import static com.wealthora.server.api.FinanceDtos.ContributionResponse;
import static com.wealthora.server.api.FinanceDtos.DebtRequest;
import static com.wealthora.server.api.FinanceDtos.DebtResponse;
import static com.wealthora.server.api.FinanceDtos.GoalRequest;
import static com.wealthora.server.api.FinanceDtos.GoalResponse;
import static com.wealthora.server.api.FinanceDtos.MonthlyBudgetRequest;
import static com.wealthora.server.api.FinanceDtos.MonthlyBudgetResponse;
import static com.wealthora.server.api.FinanceDtos.PageResponse;
import static com.wealthora.server.api.FinanceDtos.RecurringRequest;
import static com.wealthora.server.api.FinanceDtos.RecurringResponse;
import static com.wealthora.server.api.FinanceDtos.RepaymentRequest;
import static com.wealthora.server.api.FinanceDtos.RepaymentResponse;

import com.wealthora.server.domain.BudgetPlanRecord;
import com.wealthora.server.domain.DebtRecordEntity;
import com.wealthora.server.domain.DebtRepaymentRecord;
import com.wealthora.server.domain.FinanceAccount;
import com.wealthora.server.domain.FinanceCategory;
import com.wealthora.server.domain.GoalContributionRecord;
import com.wealthora.server.domain.MonthlyBudgetRecord;
import com.wealthora.server.domain.RecurringFinanceRecord;
import com.wealthora.server.domain.SavingsGoalRecord;
import com.wealthora.server.repository.BudgetPlanRecordRepository;
import com.wealthora.server.repository.DebtRecordEntityRepository;
import com.wealthora.server.repository.DebtRepaymentRecordRepository;
import com.wealthora.server.repository.GoalContributionRecordRepository;
import com.wealthora.server.repository.MonthlyBudgetRecordRepository;
import com.wealthora.server.repository.RecurringFinanceRepository;
import com.wealthora.server.repository.SavingsGoalRecordRepository;
import com.wealthora.server.security.SessionPrincipal;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancePlanningService {

    private static final Set<String> ROLLOVER_MODES =
            Set.of("NONE", "CARRY_UNUSED");
    private static final Set<String> RECURRING_TYPES =
            Set.of("EXPENSE", "INCOME", "TRANSFER");
    private static final Set<String> FREQUENCIES =
            Set.of("DAILY", "WEEKLY", "MONTHLY", "YEARLY");
    private static final Set<String> RECURRING_KINDS =
            Set.of("SCHEDULED_TRANSACTION", "BILL", "SUBSCRIPTION");
    private static final Set<String> DEBT_DIRECTIONS =
            Set.of("BORROWED", "LENT");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final FinanceWorkspaceService workspace;
    private final MonthlyBudgetRecordRepository monthlyBudgets;
    private final BudgetPlanRecordRepository budgetPlans;
    private final RecurringFinanceRepository recurring;
    private final SavingsGoalRecordRepository goals;
    private final GoalContributionRecordRepository contributions;
    private final DebtRecordEntityRepository debts;
    private final DebtRepaymentRecordRepository repayments;
    private final Clock clock;

    public FinancePlanningService(
            FinanceWorkspaceService workspace,
            MonthlyBudgetRecordRepository monthlyBudgets,
            BudgetPlanRecordRepository budgetPlans,
            RecurringFinanceRepository recurring,
            SavingsGoalRecordRepository goals,
            GoalContributionRecordRepository contributions,
            DebtRecordEntityRepository debts,
            DebtRepaymentRecordRepository repayments,
            Clock clock) {
        this.workspace = workspace;
        this.monthlyBudgets = monthlyBudgets;
        this.budgetPlans = budgetPlans;
        this.recurring = recurring;
        this.goals = goals;
        this.contributions = contributions;
        this.debts = debts;
        this.repayments = repayments;
        this.clock = clock;
    }

    @Transactional
    public PageResponse<MonthlyBudgetResponse> monthlyBudgets(
            SessionPrincipal principal, int page, int size) {
        workspace.ensureWorkspace(principal.userId());
        Page<MonthlyBudgetRecord> result = monthlyBudgets.findByUserId(
                principal.userId(), FinanceWorkspaceService.page(
                        page, size, "budgetMonth"));
        return FinanceWorkspaceService.page(result.map(this::response));
    }

    @Transactional
    public MonthlyBudgetResponse saveMonthlyBudget(
            SessionPrincipal principal, YearMonth month,
            MonthlyBudgetRequest request) {
        workspace.ensureWorkspace(principal.userId());
        if (month == null || request.month() == null
                || !month.equals(request.month())) {
            throw FinanceValidation.invalid("Budget month cannot be changed.");
        }
        workspace.validateCategoryLimits(
                principal.userId(), request.categoryLimits());
        BigDecimal overall = request.overallLimit() == null ? null
                : FinanceValidation.positiveAmount(
                        request.overallLimit(), "Overall budget limit");
        if (overall == null && request.categoryLimits().isEmpty()) {
            throw FinanceValidation.invalid(
                    "A monthly budget requires at least one limit.");
        }
        String encoded = FinanceValidation.encodeLimits(
                request.categoryLimits());
        Instant now = clock.instant();
        MonthlyBudgetRecord record = monthlyBudgets
                .findByUserIdAndBudgetMonth(
                        principal.userId(), month.toString())
                .orElseGet(() -> new MonthlyBudgetRecord(
                        UUID.randomUUID(), principal.userId(), month.toString(),
                        overall, encoded, now));
        record.update(overall, encoded, now);
        monthlyBudgets.save(record);
        return response(record);
    }

    @Transactional
    public void deleteMonthlyBudget(
            SessionPrincipal principal, YearMonth month) {
        workspace.ensureWorkspace(principal.userId());
        MonthlyBudgetRecord record = monthlyBudgets
                .findByUserIdAndBudgetMonth(
                        principal.userId(), month.toString())
                .orElseThrow(FinanceValidation::missing);
        monthlyBudgets.delete(record);
    }

    @Transactional
    public PageResponse<BudgetPlanResponse> budgetPlans(
            SessionPrincipal principal, int page, int size) {
        workspace.ensureWorkspace(principal.userId());
        Page<BudgetPlanRecord> result = budgetPlans.findByUserId(
                principal.userId(), FinanceWorkspaceService.page(
                        page, size, "startDate", "externalId"));
        return FinanceWorkspaceService.page(result.map(this::response));
    }

    @Transactional
    public BudgetPlanResponse createBudgetPlan(
            SessionPrincipal principal, BudgetPlanRequest request) {
        workspace.ensureWorkspace(principal.userId());
        String externalId = FinanceValidation.externalId(
                request.externalId(), "BUDGET_");
        if (budgetPlans.existsByUserIdAndExternalId(
                principal.userId(), externalId)) {
            throw FinanceValidation.duplicate();
        }
        ValidatedBudgetPlan value = validateBudgetPlan(
                principal.userId(), request);
        BudgetPlanRecord record = new BudgetPlanRecord(
                UUID.randomUUID(), principal.userId(), externalId,
                value.name(), value.startDate(), value.endDate(),
                value.overallLimit(), value.categoryLimits(),
                value.rolloverMode(), request.active(), clock.instant());
        budgetPlans.save(record);
        return response(record);
    }

    @Transactional
    public BudgetPlanResponse updateBudgetPlan(
            SessionPrincipal principal, String externalId,
            BudgetPlanRequest request) {
        workspace.ensureWorkspace(principal.userId());
        BudgetPlanRecord record = budgetPlans.findByUserIdAndExternalId(
                principal.userId(), FinanceValidation.externalId(
                        externalId, "BUDGET_"))
                .orElseThrow(FinanceValidation::missing);
        if (!record.getExternalId().equals(request.externalId())) {
            throw FinanceValidation.invalid(
                    "Budget identifier cannot be changed.");
        }
        ValidatedBudgetPlan value = validateBudgetPlan(
                principal.userId(), request);
        record.update(value.name(), value.startDate(), value.endDate(),
                value.overallLimit(), value.categoryLimits(),
                value.rolloverMode(), request.active(), clock.instant());
        return response(record);
    }

    @Transactional
    public PageResponse<RecurringResponse> recurring(
            SessionPrincipal principal, int page, int size) {
        workspace.ensureWorkspace(principal.userId());
        Page<RecurringFinanceRecord> result = recurring.findByUserId(
                principal.userId(), FinanceWorkspaceService.page(
                        page, size, "nextDueDate", "externalId"));
        return FinanceWorkspaceService.page(result.map(
                record -> response(principal.userId(), record)));
    }

    @Transactional
    public RecurringResponse createRecurring(
            SessionPrincipal principal, RecurringRequest request) {
        workspace.ensureWorkspace(principal.userId());
        String externalId = FinanceValidation.externalId(
                request.externalId(), "RECURRING_");
        if (recurring.existsByUserIdAndExternalId(
                principal.userId(), externalId)) {
            throw FinanceValidation.duplicate();
        }
        ValidatedRecurring value = validateRecurring(
                principal.userId(), request);
        RecurringFinanceRecord record = new RecurringFinanceRecord(
                UUID.randomUUID(), principal.userId(), externalId,
                value.type(), value.amount(), value.description(),
                value.categoryId(), value.sourceId(), value.destinationId(),
                value.frequency(), value.interval(), value.startDate(),
                value.endDate(), value.nextDueDate(), value.kind(),
                value.reminderDays(), request.active(), clock.instant());
        recurring.save(record);
        return response(principal.userId(), record);
    }

    @Transactional
    public RecurringResponse updateRecurring(
            SessionPrincipal principal, String externalId,
            RecurringRequest request) {
        workspace.ensureWorkspace(principal.userId());
        RecurringFinanceRecord record = recurring.findByUserIdAndExternalId(
                principal.userId(), FinanceValidation.externalId(
                        externalId, "RECURRING_"))
                .orElseThrow(FinanceValidation::missing);
        if (!record.getExternalId().equals(request.externalId())) {
            throw FinanceValidation.invalid(
                    "Recurring identifier cannot be changed.");
        }
        ValidatedRecurring value = validateRecurring(
                principal.userId(), request);
        record.update(value.type(), value.amount(), value.description(),
                value.categoryId(), value.sourceId(), value.destinationId(),
                value.frequency(), value.interval(), value.startDate(),
                value.endDate(), value.nextDueDate(), value.kind(),
                value.reminderDays(), request.active(), clock.instant());
        return response(principal.userId(), record);
    }

    @Transactional
    public PageResponse<GoalResponse> goals(
            SessionPrincipal principal, int page, int size) {
        workspace.ensureWorkspace(principal.userId());
        Page<SavingsGoalRecord> result = goals.findByUserId(
                principal.userId(), FinanceWorkspaceService.page(
                        page, size, "targetDate", "externalId"));
        return FinanceWorkspaceService.page(result.map(
                record -> response(principal.userId(), record)));
    }

    @Transactional
    public GoalResponse createGoal(
            SessionPrincipal principal, GoalRequest request) {
        workspace.ensureWorkspace(principal.userId());
        String externalId = FinanceValidation.externalId(
                request.externalId(), "GOAL_");
        if (goals.existsByUserIdAndExternalId(
                principal.userId(), externalId)) {
            throw FinanceValidation.duplicate();
        }
        FinanceAccount account = workspace.ownedAccount(
                principal.userId(), request.linkedAccountExternalId());
        requireActive(account, "Linked savings account");
        SavingsGoalRecord record = new SavingsGoalRecord(
                UUID.randomUUID(), principal.userId(), externalId,
                FinanceValidation.requiredText(request.name(),
                        "Savings goal name", 160),
                FinanceValidation.positiveAmount(request.targetAmount(),
                        "Savings target amount"),
                requiredDate(request.targetDate(), "Savings target date"),
                account.getId(), request.active(), clock.instant());
        goals.save(record);
        return response(principal.userId(), record);
    }

    @Transactional
    public GoalResponse updateGoal(
            SessionPrincipal principal, String externalId,
            GoalRequest request) {
        workspace.ensureWorkspace(principal.userId());
        SavingsGoalRecord record = goals.findByUserIdAndExternalId(
                principal.userId(), FinanceValidation.externalId(
                        externalId, "GOAL_"))
                .orElseThrow(FinanceValidation::missing);
        if (!record.getExternalId().equals(request.externalId())) {
            throw FinanceValidation.invalid("Goal identifier cannot be changed.");
        }
        FinanceAccount account = workspace.ownedAccount(
                principal.userId(), request.linkedAccountExternalId());
        if (request.active()) requireActive(account, "Linked savings account");
        record.update(FinanceValidation.requiredText(
                        request.name(), "Savings goal name", 160),
                FinanceValidation.positiveAmount(request.targetAmount(),
                        "Savings target amount"),
                requiredDate(request.targetDate(), "Savings target date"),
                account.getId(), request.active(), clock.instant());
        return response(principal.userId(), record);
    }

    @Transactional
    public PageResponse<ContributionResponse> contributions(
            SessionPrincipal principal, String goalExternalId,
            int page, int size) {
        workspace.ensureWorkspace(principal.userId());
        SavingsGoalRecord goal = ownedGoal(principal.userId(), goalExternalId);
        Page<GoalContributionRecord> result =
                contributions.findByUserIdAndGoalId(
                        principal.userId(), goal.getId(),
                        FinanceWorkspaceService.page(
                                page, size, "date", "externalId"));
        return FinanceWorkspaceService.page(result.map(
                item -> response(goal.getExternalId(), item)));
    }

    @Transactional
    public ContributionResponse addContribution(
            SessionPrincipal principal, String goalExternalId,
            ContributionRequest request) {
        workspace.ensureWorkspace(principal.userId());
        SavingsGoalRecord goal = ownedGoal(principal.userId(), goalExternalId);
        if (!goal.isActive()) {
            throw FinanceValidation.invalid(
                    "Archived savings goals cannot receive contributions.");
        }
        String externalId = FinanceValidation.externalId(
                request.externalId(), "CONTRIBUTION_");
        if (contributions.existsByUserIdAndExternalId(
                principal.userId(), externalId)) {
            throw FinanceValidation.duplicate();
        }
        GoalContributionRecord record = new GoalContributionRecord(
                UUID.randomUUID(), principal.userId(), externalId,
                goal.getId(), FinanceValidation.postedDate(
                        request.date(), "Contribution date"),
                FinanceValidation.positiveAmount(
                        request.amount(), "Contribution amount"),
                FinanceValidation.optionalText(
                        request.note(), "Contribution note", 500),
                clock.instant());
        contributions.save(record);
        return response(goal.getExternalId(), record);
    }

    @Transactional
    public PageResponse<DebtResponse> debts(
            SessionPrincipal principal, int page, int size) {
        workspace.ensureWorkspace(principal.userId());
        Page<DebtRecordEntity> result = debts.findByUserId(
                principal.userId(), FinanceWorkspaceService.page(
                        page, size, "dueDate", "externalId"));
        return FinanceWorkspaceService.page(result.map(
                record -> response(principal.userId(), record)));
    }

    @Transactional
    public DebtResponse createDebt(
            SessionPrincipal principal, DebtRequest request) {
        workspace.ensureWorkspace(principal.userId());
        String externalId = FinanceValidation.externalId(
                request.externalId(), "DEBT_");
        if (debts.existsByUserIdAndExternalId(
                principal.userId(), externalId)) {
            throw FinanceValidation.duplicate();
        }
        DebtRecordEntity record = new DebtRecordEntity(
                UUID.randomUUID(), principal.userId(), externalId,
                FinanceValidation.enumValue(request.direction(),
                        "Debt direction", DEBT_DIRECTIONS),
                FinanceValidation.requiredText(request.counterparty(),
                        "Counterparty", 160),
                FinanceValidation.positiveAmount(request.originalAmount(),
                        "Original debt amount"),
                requiredDate(request.dueDate(), "Debt due date"),
                FinanceValidation.optionalText(request.note(),
                        "Debt note", 500), clock.instant());
        debts.save(record);
        return response(principal.userId(), record);
    }

    @Transactional
    public DebtResponse updateDebt(
            SessionPrincipal principal, String externalId,
            DebtRequest request) {
        workspace.ensureWorkspace(principal.userId());
        DebtRecordEntity record = ownedDebt(principal.userId(), externalId);
        if (!record.getExternalId().equals(request.externalId())) {
            throw FinanceValidation.invalid("Debt identifier cannot be changed.");
        }
        BigDecimal amount = FinanceValidation.positiveAmount(
                request.originalAmount(), "Original debt amount");
        BigDecimal repaid = totalRepaid(principal.userId(), record.getId());
        if (amount.compareTo(repaid) < 0) {
            throw FinanceValidation.invalid(
                    "Original amount cannot be below recorded repayments.");
        }
        record.update(FinanceValidation.enumValue(request.direction(),
                        "Debt direction", DEBT_DIRECTIONS),
                FinanceValidation.requiredText(request.counterparty(),
                        "Counterparty", 160), amount,
                requiredDate(request.dueDate(), "Debt due date"),
                FinanceValidation.optionalText(
                        request.note(), "Debt note", 500), clock.instant());
        return response(principal.userId(), record);
    }

    @Transactional
    public PageResponse<RepaymentResponse> repayments(
            SessionPrincipal principal, String debtExternalId,
            int page, int size) {
        workspace.ensureWorkspace(principal.userId());
        DebtRecordEntity debt = ownedDebt(principal.userId(), debtExternalId);
        Page<DebtRepaymentRecord> result = repayments.findByUserIdAndDebtId(
                principal.userId(), debt.getId(),
                FinanceWorkspaceService.page(page, size, "date", "externalId"));
        return FinanceWorkspaceService.page(result.map(
                item -> response(debt.getExternalId(), item)));
    }

    @Transactional
    public RepaymentResponse addRepayment(
            SessionPrincipal principal, String debtExternalId,
            RepaymentRequest request) {
        workspace.ensureWorkspace(principal.userId());
        DebtRecordEntity debt = ownedDebt(principal.userId(), debtExternalId);
        String externalId = FinanceValidation.externalId(
                request.externalId(), "REPAYMENT_");
        if (repayments.existsByUserIdAndExternalId(
                principal.userId(), externalId)) {
            throw FinanceValidation.duplicate();
        }
        BigDecimal amount = FinanceValidation.positiveAmount(
                request.amount(), "Repayment amount");
        BigDecimal remaining = debt.getOriginalAmount().subtract(
                totalRepaid(principal.userId(), debt.getId()));
        if (amount.compareTo(remaining) > 0) {
            throw FinanceValidation.invalid(
                    "Repayment cannot exceed the remaining amount.");
        }
        DebtRepaymentRecord record = new DebtRepaymentRecord(
                UUID.randomUUID(), principal.userId(), externalId,
                debt.getId(), FinanceValidation.postedDate(
                        request.date(), "Repayment date"), amount,
                FinanceValidation.optionalText(
                        request.note(), "Repayment note", 500), clock.instant());
        repayments.save(record);
        return response(debt.getExternalId(), record);
    }

    private ValidatedBudgetPlan validateBudgetPlan(
            UUID userId, BudgetPlanRequest request) {
        LocalDate start = requiredDate(request.startDate(), "Budget start date");
        LocalDate end = requiredDate(request.endDate(), "Budget end date");
        if (end.isBefore(start)) {
            throw FinanceValidation.invalid(
                    "Budget end date must not be before its start date.");
        }
        workspace.validateCategoryLimits(userId, request.categoryLimits());
        BigDecimal overall = request.overallLimit() == null ? null
                : FinanceValidation.positiveAmount(
                        request.overallLimit(), "Overall budget limit");
        if (overall == null && request.categoryLimits().isEmpty()) {
            throw FinanceValidation.invalid(
                    "A budget plan requires an overall or category limit.");
        }
        return new ValidatedBudgetPlan(
                FinanceValidation.requiredText(request.name(),
                        "Budget name", 160), start, end, overall,
                FinanceValidation.encodeLimits(request.categoryLimits()),
                FinanceValidation.enumValue(request.rolloverMode(),
                        "Budget rollover mode", ROLLOVER_MODES));
    }

    private ValidatedRecurring validateRecurring(
            UUID userId, RecurringRequest request) {
        String type = FinanceValidation.enumValue(
                request.entryType(), "Recurring entry type", RECURRING_TYPES);
        FinanceAccount source = workspace.ownedAccount(
                userId, request.sourceAccountExternalId());
        requireActive(source, "Recurring source account");
        FinanceCategory category = request.categoryExternalId() == null
                || request.categoryExternalId().isBlank() ? null
                : workspace.ownedCategory(userId, request.categoryExternalId());
        FinanceAccount destination = request.destinationAccountExternalId() == null
                || request.destinationAccountExternalId().isBlank() ? null
                : workspace.ownedAccount(
                        userId, request.destinationAccountExternalId());
        if ("EXPENSE".equals(type)) {
            if (category == null || category.isArchived() || destination != null) {
                throw FinanceValidation.invalid(
                        "Recurring expenses require an active category and no destination account.");
            }
        } else if ("TRANSFER".equals(type)) {
            if (category != null || destination == null
                    || destination.isArchived()
                    || source.getId().equals(destination.getId())
                    || !source.getCurrencyCode().equals(
                            destination.getCurrencyCode())) {
                throw FinanceValidation.invalid(
                        "Recurring transfers require different active accounts with the same currency.");
            }
        } else if (category != null || destination != null) {
            throw FinanceValidation.invalid(
                    "Recurring income cannot have a category or destination account.");
        }
        if (request.interval() < 1) {
            throw FinanceValidation.invalid(
                    "Recurrence interval must be greater than zero.");
        }
        LocalDate start = requiredDate(request.startDate(),
                "Recurring start date");
        LocalDate end = request.endDate();
        LocalDate next = requiredDate(request.nextDueDate(), "Next due date");
        if ((end != null && end.isBefore(start)) || next.isBefore(start)
                || request.active() && end != null && next.isAfter(end)) {
            throw FinanceValidation.invalid("Recurring dates are invalid.");
        }
        if (request.reminderDays() < 0 || request.reminderDays() > 365) {
            throw FinanceValidation.invalid(
                    "Reminder days must be from 0 through 365.");
        }
        String kind = FinanceValidation.enumValue(request.recurringKind(),
                "Recurring kind", RECURRING_KINDS);
        if (!"EXPENSE".equals(type) && !"SCHEDULED_TRANSACTION".equals(kind)) {
            throw FinanceValidation.invalid(
                    "Bills and subscriptions must be recurring expenses.");
        }
        return new ValidatedRecurring(type,
                FinanceValidation.positiveAmount(
                        request.amount(), "Recurring amount"),
                FinanceValidation.requiredText(request.description(),
                        "Recurring description", 160),
                category == null ? null : category.getId(), source.getId(),
                destination == null ? null : destination.getId(),
                FinanceValidation.enumValue(request.frequency(),
                        "Recurrence frequency", FREQUENCIES),
                request.interval(), start, end, next, kind,
                request.reminderDays());
    }

    private MonthlyBudgetResponse response(MonthlyBudgetRecord record) {
        return new MonthlyBudgetResponse(YearMonth.parse(record.getBudgetMonth()),
                record.getOverallLimit(),
                FinanceValidation.decodeLimits(record.getCategoryLimits()));
    }

    private BudgetPlanResponse response(BudgetPlanRecord record) {
        return new BudgetPlanResponse(record.getExternalId(), record.getName(),
                record.getStartDate(), record.getEndDate(),
                record.getOverallLimit(),
                FinanceValidation.decodeLimits(record.getCategoryLimits()),
                record.getRolloverMode(), record.isActive());
    }

    private RecurringResponse response(
            UUID userId, RecurringFinanceRecord record) {
        return new RecurringResponse(record.getExternalId(),
                record.getEntryType(), record.getAmount(),
                record.getDescription(),
                workspace.categoryExternalId(userId, record.getCategoryId()),
                workspace.accountExternalId(userId, record.getSourceAccountId()),
                record.getDestinationAccountId() == null ? null
                        : workspace.accountExternalId(
                                userId, record.getDestinationAccountId()),
                record.getFrequency(), record.getRecurrenceInterval(),
                record.getStartDate(), record.getEndDate(),
                record.getNextDueDate(), record.getRecurringKind(),
                record.getReminderDays(), record.isActive());
    }

    private GoalResponse response(UUID userId, SavingsGoalRecord record) {
        return new GoalResponse(record.getExternalId(), record.getName(),
                record.getTargetAmount(), record.getTargetDate(),
                workspace.accountExternalId(userId, record.getLinkedAccountId()),
                record.isActive(), totalContributed(userId, record.getId()));
    }

    private ContributionResponse response(
            String goalExternalId, GoalContributionRecord record) {
        return new ContributionResponse(record.getExternalId(), goalExternalId,
                record.getDate(), record.getAmount(), record.getNote());
    }

    private DebtResponse response(UUID userId, DebtRecordEntity record) {
        BigDecimal repaid = totalRepaid(userId, record.getId());
        return new DebtResponse(record.getExternalId(), record.getDirection(),
                record.getCounterparty(), record.getOriginalAmount(),
                record.getDueDate(), record.getNote(), repaid,
                record.getOriginalAmount().subtract(repaid));
    }

    private RepaymentResponse response(
            String debtExternalId, DebtRepaymentRecord record) {
        return new RepaymentResponse(record.getExternalId(), debtExternalId,
                record.getDate(), record.getAmount(), record.getNote());
    }

    private SavingsGoalRecord ownedGoal(UUID userId, String externalId) {
        return goals.findByUserIdAndExternalId(userId,
                FinanceValidation.externalId(externalId, "GOAL_"))
                .orElseThrow(FinanceValidation::missing);
    }

    private DebtRecordEntity ownedDebt(UUID userId, String externalId) {
        return debts.findByUserIdAndExternalId(userId,
                FinanceValidation.externalId(externalId, "DEBT_"))
                .orElseThrow(FinanceValidation::missing);
    }

    private BigDecimal totalContributed(UUID userId, UUID goalId) {
        BigDecimal total = contributions.totalForGoal(userId, goalId);
        return total == null ? ZERO : total;
    }

    private BigDecimal totalRepaid(UUID userId, UUID debtId) {
        BigDecimal total = repayments.totalForDebt(userId, debtId);
        return total == null ? ZERO : total;
    }

    private static LocalDate requiredDate(LocalDate date, String field) {
        if (date == null) throw FinanceValidation.invalid(field + " is required.");
        return date;
    }

    private static void requireActive(FinanceAccount account, String field) {
        if (account.isArchived()) {
            throw FinanceValidation.invalid(field + " cannot be archived.");
        }
    }

    private record ValidatedBudgetPlan(
            String name, LocalDate startDate, LocalDate endDate,
            BigDecimal overallLimit, String categoryLimits,
            String rolloverMode) {
    }

    private record ValidatedRecurring(
            String type, BigDecimal amount, String description,
            UUID categoryId, UUID sourceId, UUID destinationId,
            String frequency, int interval, LocalDate startDate,
            LocalDate endDate, LocalDate nextDueDate, String kind,
            int reminderDays) {
    }
}
