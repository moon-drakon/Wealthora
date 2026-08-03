package com.spendwise.repository.cloud;

import com.spendwise.model.Account;
import com.spendwise.model.AccountType;
import com.spendwise.model.BudgetPlan;
import com.spendwise.model.BudgetRolloverMode;
import com.spendwise.model.Category;
import com.spendwise.model.DebtDirection;
import com.spendwise.model.DebtRecord;
import com.spendwise.model.DebtRepayment;
import com.spendwise.model.Expense;
import com.spendwise.model.GoalContribution;
import com.spendwise.model.Income;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.model.PaymentMethod;
import com.spendwise.model.RecurrenceFrequency;
import com.spendwise.model.RecurringEntry;
import com.spendwise.model.RecurringEntryType;
import com.spendwise.model.RecurringKind;
import com.spendwise.model.SavingsGoal;
import com.spendwise.model.Transfer;
import com.spendwise.repository.AccountPreferenceRepository;
import com.spendwise.repository.AccountRepository;
import com.spendwise.repository.BudgetPlanRepository;
import com.spendwise.repository.BudgetRepository;
import com.spendwise.repository.CategoryRepository;
import com.spendwise.repository.DebtRepository;
import com.spendwise.repository.ExpenseRepository;
import com.spendwise.repository.IncomeRepository;
import com.spendwise.repository.RecurringEntryRepository;
import com.spendwise.repository.SavingsGoalRepository;
import com.spendwise.repository.TransferRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CloudFinanceRepositories {

    private final CloudFinanceClient client;
    private final AccountRepository accounts = new CloudAccounts();
    private final AccountPreferenceRepository accountPreference =
            new CloudAccountPreference();
    private final CategoryRepository categories = new CloudCategories();
    private final ExpenseRepository expenses = new CloudExpenses();
    private final IncomeRepository income = new CloudIncome();
    private final TransferRepository transfers = new CloudTransfers();
    private final BudgetRepository budgets = new CloudBudgets();
    private final BudgetPlanRepository budgetPlans = new CloudBudgetPlans();
    private final RecurringEntryRepository recurring = new CloudRecurring();
    private final SavingsGoalRepository goals = new CloudGoals();
    private final DebtRepository debts = new CloudDebts();

    public CloudFinanceRepositories(CloudFinanceClient client) {
        this.client = Objects.requireNonNull(client,
                "Cloud finance client is required.");
    }

    public AccountRepository accounts() { return accounts; }
    public AccountPreferenceRepository accountPreference() {
        return accountPreference;
    }
    public CategoryRepository categories() { return categories; }
    public ExpenseRepository expenses() { return expenses; }
    public IncomeRepository income() { return income; }
    public TransferRepository transfers() { return transfers; }
    public BudgetRepository budgets() { return budgets; }
    public BudgetPlanRepository budgetPlans() { return budgetPlans; }
    public RecurringEntryRepository recurring() { return recurring; }
    public SavingsGoalRepository goals() { return goals; }
    public DebtRepository debts() { return debts; }

    private final class CloudAccounts implements AccountRepository {
        @Override
        public List<Account> findAll() {
            return client.getAll("/api/finance/accounts").stream()
                    .filter(value -> !Account.DEFAULT_IDENTIFIER.equals(
                            CloudFinanceClient.text(value, "externalId")))
                    .map(CloudFinanceRepositories.this::account).sorted().toList();
        }

        @Override
        public Optional<Account> findById(String identifier) {
            if (Account.DEFAULT_IDENTIFIER.equals(identifier)) {
                return Optional.of(Account.DEFAULT);
            }
            return findAll().stream().filter(account -> account.getIdentifier()
                    .equals(identifier)).findFirst();
        }

        @Override
        public void add(Account account) {
            client.post("/api/finance/accounts", accountRequest(account));
        }

        @Override
        public void update(Account account) {
            client.put("/api/finance/accounts/" + segment(
                    account.getIdentifier()), accountRequest(account));
        }
    }

    private final class CloudAccountPreference
            implements AccountPreferenceRepository {
        @Override
        public Optional<String> findDefaultAccountId() {
            return Optional.of(CloudFinanceClient.text(client.get(
                    "/api/finance/accounts/default"), "externalId"));
        }

        @Override
        public void saveDefaultAccountId(String identifier) {
            client.put("/api/finance/accounts/default",
                    map("externalId", identifier));
        }
    }

    private final class CloudCategories implements CategoryRepository {
        @Override
        public List<Category> findAll() {
            return client.getAll("/api/finance/categories").stream()
                    .filter(value -> !CloudFinanceClient.bool(value, "builtIn"))
                    .map(CloudFinanceRepositories.this::category)
                    .sorted().toList();
        }

        @Override
        public void add(Category category) {
            client.post("/api/finance/categories", categoryRequest(category));
        }

        @Override
        public void update(Category category) {
            client.put("/api/finance/categories/" + segment(
                    category.getIdentifier()), categoryRequest(category));
        }
    }

    private final class CloudExpenses implements ExpenseRepository {
        @Override
        public List<Expense> findAll() {
            return client.getAll("/api/finance/expenses").stream()
                    .map(CloudFinanceRepositories.this::expense).toList();
        }

        @Override
        public Optional<Expense> findById(String id) {
            return findAll().stream().filter(value -> value.getId().equals(id))
                    .findFirst();
        }

        @Override
        public void add(Expense expense) {
            client.post("/api/finance/expenses", expenseRequest(expense));
        }

        @Override
        public void update(Expense expense) {
            client.put("/api/finance/expenses/" + segment(expense.getId()),
                    expenseRequest(expense));
        }

        @Override
        public boolean deleteById(String id) {
            if (findById(id).isEmpty()) return false;
            client.delete("/api/finance/expenses/" + segment(id));
            return true;
        }
    }

    private final class CloudIncome implements IncomeRepository {
        @Override
        public List<Income> findAll() {
            return client.getAll("/api/finance/income").stream()
                    .map(CloudFinanceRepositories.this::income).toList();
        }

        @Override
        public Optional<Income> findById(String id) {
            return findAll().stream().filter(value -> value.getId().equals(id))
                    .findFirst();
        }

        @Override
        public void add(Income income) {
            client.post("/api/finance/income", incomeRequest(income));
        }

        @Override
        public void update(Income income) {
            client.put("/api/finance/income/" + segment(income.getId()),
                    incomeRequest(income));
        }

        @Override
        public boolean deleteById(String id) {
            if (findById(id).isEmpty()) return false;
            client.delete("/api/finance/income/" + segment(id));
            return true;
        }
    }

    private final class CloudTransfers implements TransferRepository {
        @Override
        public List<Transfer> findAll() {
            return client.getAll("/api/finance/transfers").stream()
                    .map(CloudFinanceRepositories.this::transfer).toList();
        }

        @Override
        public Optional<Transfer> findById(String id) {
            return findAll().stream().filter(value -> value.getId().equals(id))
                    .findFirst();
        }

        @Override
        public void add(Transfer transfer) {
            client.post("/api/finance/transfers", transferRequest(transfer));
        }

        @Override
        public void update(Transfer transfer) {
            client.put("/api/finance/transfers/" + segment(transfer.getId()),
                    transferRequest(transfer));
        }

        @Override
        public boolean deleteById(String id) {
            if (findById(id).isEmpty()) return false;
            client.delete("/api/finance/transfers/" + segment(id));
            return true;
        }
    }

    private final class CloudBudgets implements BudgetRepository {
        @Override
        public Optional<MonthlyBudget> findByMonth(YearMonth month) {
            return findAll().stream().filter(value -> value.getMonth()
                    .equals(month)).findFirst();
        }

        @Override
        public void save(MonthlyBudget budget) {
            client.put("/api/finance/budgets/monthly/" + budget.getMonth(),
                    monthlyBudgetRequest(budget));
        }

        @Override
        public boolean delete(YearMonth month) {
            if (findByMonth(month).isEmpty()) return false;
            client.delete("/api/finance/budgets/monthly/" + month);
            return true;
        }

        @Override
        public List<MonthlyBudget> findAll() {
            return client.getAll("/api/finance/budgets/monthly").stream()
                    .map(CloudFinanceRepositories.this::monthlyBudget).toList();
        }
    }

    private final class CloudBudgetPlans implements BudgetPlanRepository {
        @Override
        public List<BudgetPlan> findAll() {
            return client.getAll("/api/finance/budgets/plans").stream()
                    .map(CloudFinanceRepositories.this::budgetPlan).toList();
        }

        @Override
        public Optional<BudgetPlan> findById(String identifier) {
            return findAll().stream().filter(value -> value.getIdentifier()
                    .equals(identifier)).findFirst();
        }

        @Override
        public void add(BudgetPlan plan) {
            client.post("/api/finance/budgets/plans", budgetPlanRequest(plan));
        }

        @Override
        public void update(BudgetPlan plan) {
            client.put("/api/finance/budgets/plans/" + segment(
                    plan.getIdentifier()), budgetPlanRequest(plan));
        }
    }

    private final class CloudRecurring implements RecurringEntryRepository {
        @Override
        public List<RecurringEntry> findAll() {
            return client.getAll("/api/finance/recurring").stream()
                    .map(CloudFinanceRepositories.this::recurring).toList();
        }

        @Override
        public Optional<RecurringEntry> findById(String identifier) {
            return findAll().stream().filter(value -> value.getIdentifier()
                    .equals(identifier)).findFirst();
        }

        @Override
        public void add(RecurringEntry entry) {
            client.post("/api/finance/recurring", recurringRequest(entry));
        }

        @Override
        public void update(RecurringEntry entry) {
            client.put("/api/finance/recurring/" + segment(
                    entry.getIdentifier()), recurringRequest(entry));
        }
    }

    private final class CloudGoals implements SavingsGoalRepository {
        @Override
        public List<SavingsGoal> findAllGoals() {
            return client.getAll("/api/finance/goals").stream()
                    .map(CloudFinanceRepositories.this::goal).toList();
        }

        @Override
        public Optional<SavingsGoal> findGoalById(String identifier) {
            return findAllGoals().stream().filter(value -> value.getIdentifier()
                    .equals(identifier)).findFirst();
        }

        @Override
        public List<GoalContribution> findContributions(String goalIdentifier) {
            return client.getAll("/api/finance/goals/" + segment(goalIdentifier)
                    + "/contributions").stream()
                    .map(CloudFinanceRepositories.this::contribution).toList();
        }

        @Override
        public void addGoal(SavingsGoal goal) {
            client.post("/api/finance/goals", goalRequest(goal));
        }

        @Override
        public void updateGoal(SavingsGoal goal) {
            client.put("/api/finance/goals/" + segment(goal.getIdentifier()),
                    goalRequest(goal));
        }

        @Override
        public void addContribution(GoalContribution contribution) {
            client.post("/api/finance/goals/" + segment(
                    contribution.getGoalIdentifier()) + "/contributions",
                    contributionRequest(contribution));
        }
    }

    private final class CloudDebts implements DebtRepository {
        @Override
        public List<DebtRecord> findAllDebts() {
            return client.getAll("/api/finance/debts").stream()
                    .map(CloudFinanceRepositories.this::debt).toList();
        }

        @Override
        public Optional<DebtRecord> findDebtById(String identifier) {
            return findAllDebts().stream().filter(value -> value.getIdentifier()
                    .equals(identifier)).findFirst();
        }

        @Override
        public List<DebtRepayment> findRepayments(String debtIdentifier) {
            return client.getAll("/api/finance/debts/" + segment(debtIdentifier)
                    + "/repayments").stream()
                    .map(CloudFinanceRepositories.this::repayment).toList();
        }

        @Override
        public void addDebt(DebtRecord debt) {
            client.post("/api/finance/debts", debtRequest(debt));
        }

        @Override
        public void updateDebt(DebtRecord debt) {
            client.put("/api/finance/debts/" + segment(debt.getIdentifier()),
                    debtRequest(debt));
        }

        @Override
        public void addRepayment(DebtRepayment repayment) {
            client.post("/api/finance/debts/" + segment(
                    repayment.getDebtIdentifier()) + "/repayments",
                    repaymentRequest(repayment));
        }
    }

    private Account account(Map<String, Object> value) {
        String identifier = CloudFinanceClient.text(value, "externalId");
        if (Account.DEFAULT_IDENTIFIER.equals(identifier)) return Account.DEFAULT;
        String opened = CloudFinanceClient.nullableText(value, "openedOn");
        return Account.restoreCustom(identifier,
                CloudFinanceClient.text(value, "name"),
                AccountType.valueOf(CloudFinanceClient.text(value, "accountType")),
                CloudFinanceClient.decimal(value, "openingBalance"),
                CloudFinanceClient.text(value, "iconName"),
                CloudFinanceClient.text(value, "colorHex"),
                CloudFinanceClient.text(value, "currencyCode"),
                CloudFinanceClient.text(value, "institutionName"),
                opened == null ? Optional.empty()
                        : Optional.of(LocalDate.parse(opened)),
                CloudFinanceClient.bool(value, "archived"));
    }

    private Category category(Map<String, Object> value) {
        String identifier = CloudFinanceClient.text(value, "externalId");
        if (CloudFinanceClient.bool(value, "builtIn")) {
            return Category.valueOf(identifier);
        }
        String parent = CloudFinanceClient.nullableText(
                value, "parentExternalId");
        return parent == null
                ? Category.createCustom(identifier,
                        CloudFinanceClient.text(value, "name"),
                        CloudFinanceClient.bool(value, "archived"))
                : Category.createSubcategory(identifier,
                        CloudFinanceClient.text(value, "name"), parent,
                        CloudFinanceClient.bool(value, "archived"));
    }

    private Expense expense(Map<String, Object> value) {
        return new Expense(CloudFinanceClient.text(value, "externalId"),
                CloudFinanceClient.text(value, "description"),
                CloudFinanceClient.decimal(value, "amount"),
                LocalDate.parse(CloudFinanceClient.text(value, "date")),
                resolveCategory(CloudFinanceClient.text(
                        value, "categoryExternalId")),
                resolveAccount(CloudFinanceClient.text(
                        value, "accountExternalId")),
                PaymentMethod.valueOf(CloudFinanceClient.text(
                        value, "paymentMethod")),
                CloudFinanceClient.strings(value, "tags"),
                nullable(value, "note"));
    }

    private Income income(Map<String, Object> value) {
        return new Income(CloudFinanceClient.text(value, "externalId"),
                LocalDate.parse(CloudFinanceClient.text(value, "date")),
                CloudFinanceClient.decimal(value, "amount"),
                CloudFinanceClient.text(value, "description"),
                resolveAccount(CloudFinanceClient.text(
                        value, "accountExternalId")),
                PaymentMethod.valueOf(CloudFinanceClient.text(
                        value, "paymentMethod")),
                CloudFinanceClient.strings(value, "tags"),
                nullable(value, "note"));
    }

    private Transfer transfer(Map<String, Object> value) {
        return new Transfer(CloudFinanceClient.text(value, "externalId"),
                LocalDate.parse(CloudFinanceClient.text(value, "date")),
                CloudFinanceClient.decimal(value, "amount"),
                resolveAccount(CloudFinanceClient.text(
                        value, "sourceAccountExternalId")),
                resolveAccount(CloudFinanceClient.text(
                        value, "destinationAccountExternalId")),
                CloudFinanceClient.strings(value, "tags"),
                nullable(value, "note"));
    }

    private MonthlyBudget monthlyBudget(Map<String, Object> value) {
        return new MonthlyBudget(YearMonth.parse(
                CloudFinanceClient.text(value, "month")),
                Optional.ofNullable(CloudFinanceClient.nullableDecimal(
                        value, "overallLimit")), categoryLimits(value));
    }

    private BudgetPlan budgetPlan(Map<String, Object> value) {
        return new BudgetPlan(CloudFinanceClient.text(value, "externalId"),
                CloudFinanceClient.text(value, "name"),
                LocalDate.parse(CloudFinanceClient.text(value, "startDate")),
                LocalDate.parse(CloudFinanceClient.text(value, "endDate")),
                CloudFinanceClient.nullableDecimal(value, "overallLimit"),
                categoryLimits(value), BudgetRolloverMode.valueOf(
                        CloudFinanceClient.text(value, "rolloverMode")),
                CloudFinanceClient.bool(value, "active"));
    }

    private RecurringEntry recurring(Map<String, Object> value) {
        String categoryId = CloudFinanceClient.nullableText(
                value, "categoryExternalId");
        String destinationId = CloudFinanceClient.nullableText(
                value, "destinationAccountExternalId");
        String end = CloudFinanceClient.nullableText(value, "endDate");
        return new RecurringEntry(
                CloudFinanceClient.text(value, "externalId"),
                RecurringEntryType.valueOf(CloudFinanceClient.text(
                        value, "entryType")),
                CloudFinanceClient.decimal(value, "amount"),
                CloudFinanceClient.text(value, "description"),
                categoryId == null ? null : resolveCategory(categoryId),
                resolveAccount(CloudFinanceClient.text(
                        value, "sourceAccountExternalId")),
                destinationId == null ? null : resolveAccount(destinationId),
                RecurrenceFrequency.valueOf(CloudFinanceClient.text(
                        value, "frequency")),
                CloudFinanceClient.integer(value, "interval"),
                LocalDate.parse(CloudFinanceClient.text(value, "startDate")),
                end == null ? null : LocalDate.parse(end),
                LocalDate.parse(CloudFinanceClient.text(value, "nextDueDate")),
                RecurringKind.valueOf(CloudFinanceClient.text(
                        value, "recurringKind")),
                CloudFinanceClient.integer(value, "reminderDays"),
                CloudFinanceClient.bool(value, "active"));
    }

    private SavingsGoal goal(Map<String, Object> value) {
        return new SavingsGoal(CloudFinanceClient.text(value, "externalId"),
                CloudFinanceClient.text(value, "name"),
                CloudFinanceClient.decimal(value, "targetAmount"),
                LocalDate.parse(CloudFinanceClient.text(value, "targetDate")),
                resolveAccount(CloudFinanceClient.text(
                        value, "linkedAccountExternalId")),
                CloudFinanceClient.bool(value, "active"));
    }

    private GoalContribution contribution(Map<String, Object> value) {
        return new GoalContribution(
                CloudFinanceClient.text(value, "externalId"),
                CloudFinanceClient.text(value, "goalExternalId"),
                LocalDate.parse(CloudFinanceClient.text(value, "date")),
                CloudFinanceClient.decimal(value, "amount"),
                nullable(value, "note"));
    }

    private DebtRecord debt(Map<String, Object> value) {
        return new DebtRecord(CloudFinanceClient.text(value, "externalId"),
                DebtDirection.valueOf(CloudFinanceClient.text(
                        value, "direction")),
                CloudFinanceClient.text(value, "counterparty"),
                CloudFinanceClient.decimal(value, "originalAmount"),
                LocalDate.parse(CloudFinanceClient.text(value, "dueDate")),
                nullable(value, "note"));
    }

    private DebtRepayment repayment(Map<String, Object> value) {
        return new DebtRepayment(CloudFinanceClient.text(value, "externalId"),
                CloudFinanceClient.text(value, "debtExternalId"),
                LocalDate.parse(CloudFinanceClient.text(value, "date")),
                CloudFinanceClient.decimal(value, "amount"),
                nullable(value, "note"));
    }

    private Account resolveAccount(String identifier) {
        if (Account.DEFAULT_IDENTIFIER.equals(identifier)) return Account.DEFAULT;
        return accounts.findById(identifier).orElseThrow(() ->
                new IllegalStateException("Cloud account reference is missing."));
    }

    private Category resolveCategory(String identifier) {
        if (Category.isBuiltInIdentifier(identifier)) {
            return Category.valueOf(identifier);
        }
        return categories.findAll().stream().filter(value ->
                value.getIdentifier().equals(identifier)).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Cloud category reference is missing."));
    }

    private Map<Category, BigDecimal> categoryLimits(
            Map<String, Object> value) {
        Map<String, Object> supplied = CloudFinanceClient.object(
                value.get("categoryLimits"));
        LinkedHashMap<Category, BigDecimal> result = new LinkedHashMap<>();
        supplied.forEach((identifier, amount) -> {
            if (!(amount instanceof BigDecimal decimal)) {
                throw new IllegalStateException(
                        "Cloud category limit is invalid.");
            }
            result.put(resolveCategory(identifier), decimal);
        });
        return result;
    }

    private static Map<String, Object> accountRequest(Account value) {
        return map("externalId", value.getIdentifier(), "name",
                value.getDisplayName(), "accountType", value.getType().name(),
                "currencyCode", value.getCurrencyCode(), "openingBalance",
                value.getOpeningBalance(), "iconName", value.getIconName(),
                "colorHex", value.getColorHex(), "institutionName",
                value.getInstitutionName(), "openedOn",
                value.getCreatedDate().orElse(null), "archived",
                value.isArchived());
    }

    private static Map<String, Object> categoryRequest(Category value) {
        return map("externalId", value.getIdentifier(), "name",
                value.getDisplayName(), "parentExternalId",
                value.getParentIdentifier().orElse(null), "archived",
                value.isArchived());
    }

    private static Map<String, Object> expenseRequest(Expense value) {
        return map("externalId", value.getId(), "description",
                value.getDescription(), "amount", value.getAmount(), "date",
                value.getDate(), "accountExternalId",
                value.getAccount().getIdentifier(), "categoryExternalId",
                value.getCategory().getIdentifier(), "paymentMethod",
                value.getPaymentMethod().name(), "tags", value.getTags(),
                "note", value.getNotes());
    }

    private static Map<String, Object> incomeRequest(Income value) {
        return map("externalId", value.getId(), "source", value.getSource(),
                "amount", value.getAmount(), "date", value.getDate(),
                "accountExternalId", value.getAccount().getIdentifier(),
                "paymentMethod", value.getPaymentMethod().name(), "tags",
                value.getTags(), "note", value.getNote());
    }

    private static Map<String, Object> transferRequest(Transfer value) {
        return map("externalId", value.getId(), "amount", value.getAmount(),
                "date", value.getDate(), "sourceAccountExternalId",
                value.getSourceAccount().getIdentifier(),
                "destinationAccountExternalId",
                value.getDestinationAccount().getIdentifier(), "tags",
                value.getTags(), "note", value.getNote());
    }

    private static Map<String, Object> monthlyBudgetRequest(
            MonthlyBudget value) {
        return map("month", value.getMonth(), "overallLimit",
                value.getOverallLimit().orElse(null), "categoryLimits",
                categoryLimitRequest(value.getCategoryLimits()));
    }

    private static Map<String, Object> budgetPlanRequest(BudgetPlan value) {
        return map("externalId", value.getIdentifier(), "name", value.getName(),
                "startDate", value.getStartDate(), "endDate",
                value.getEndDate(), "overallLimit",
                value.getOverallLimit().orElse(null), "categoryLimits",
                categoryLimitRequest(value.getCategoryLimits()),
                "rolloverMode", value.getRolloverMode().name(), "active",
                value.isActive());
    }

    private static Map<String, Object> recurringRequest(RecurringEntry value) {
        return map("externalId", value.getIdentifier(), "entryType",
                value.getType().name(), "amount", value.getAmount(),
                "description", value.getDescription(), "categoryExternalId",
                value.getCategory().map(Category::getIdentifier).orElse(null),
                "sourceAccountExternalId",
                value.getSourceAccount().getIdentifier(),
                "destinationAccountExternalId", value.getDestinationAccount()
                        .map(Account::getIdentifier).orElse(null),
                "frequency", value.getFrequency().name(), "interval",
                value.getInterval(), "startDate", value.getStartDate(),
                "endDate", value.getEndDate().orElse(null), "nextDueDate",
                value.getNextDueDate(), "recurringKind", value.getKind().name(),
                "reminderDays", value.getReminderDays(), "active",
                value.isActive());
    }

    private static Map<String, Object> goalRequest(SavingsGoal value) {
        return map("externalId", value.getIdentifier(), "name", value.getName(),
                "targetAmount", value.getTargetAmount(), "targetDate",
                value.getTargetDate(), "linkedAccountExternalId",
                value.getLinkedAccount().getIdentifier(), "active",
                value.isActive());
    }

    private static Map<String, Object> contributionRequest(
            GoalContribution value) {
        return map("externalId", value.getIdentifier(), "date", value.getDate(),
                "amount", value.getAmount(), "note", value.getNote());
    }

    private static Map<String, Object> debtRequest(DebtRecord value) {
        return map("externalId", value.getIdentifier(), "direction",
                value.getDirection().name(), "counterparty",
                value.getCounterparty(), "originalAmount",
                value.getOriginalAmount(), "dueDate", value.getDueDate(),
                "note", value.getNote());
    }

    private static Map<String, Object> repaymentRequest(DebtRepayment value) {
        return map("externalId", value.getIdentifier(), "date", value.getDate(),
                "amount", value.getAmount(), "note", value.getNote());
    }

    private static Map<String, Object> categoryLimitRequest(
            Map<Category, BigDecimal> supplied) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        supplied.forEach((category, amount) -> result.put(
                category.getIdentifier(), amount));
        return result;
    }

    private static String nullable(Map<String, Object> value, String name) {
        String text = CloudFinanceClient.nullableText(value, name);
        return text == null ? "" : text;
    }

    private static String segment(String value) {
        return CloudFinanceClient.segment(value);
    }

    private static Map<String, Object> map(Object... values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }
}
