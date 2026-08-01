package com.spendwise.service;

import com.spendwise.model.Account;
import com.spendwise.model.Category;
import com.spendwise.model.Expense;
import com.spendwise.model.Income;
import com.spendwise.model.Transfer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

public final class ExportService {

    private final ExpenseService expenseService;
    private final IncomeService incomeService;
    private final TransferService transferService;
    private final AccountService accountService;
    private final FinanceService financeService;

    public ExportService(
            ExpenseService expenseService,
            IncomeService incomeService,
            TransferService transferService,
            AccountService accountService,
            FinanceService financeService) {
        this.expenseService = Objects.requireNonNull(
                expenseService, "Expense service is required.");
        this.incomeService = Objects.requireNonNull(
                incomeService, "Income service is required.");
        this.transferService = Objects.requireNonNull(
                transferService, "Transfer service is required.");
        this.accountService = Objects.requireNonNull(
                accountService, "Account service is required.");
        this.financeService = Objects.requireNonNull(
                financeService, "Finance service is required.");
    }

    public ExportResult exportExpenses(
            Path destination, boolean allowOverwrite) {
        StringBuilder csv = new StringBuilder(
                "id,date,description,amount,categoryId,categoryName,"
                + "accountId,accountName,notes\n");
        int rows = 0;
        for (Expense expense : expenseService.getAllExpenses()) {
            appendRow(csv,
                    expense.getId(),
                    expense.getDate().toString(),
                    expense.getDescription(),
                    expense.getAmount().toPlainString(),
                    expense.getCategory().getIdentifier(),
                    expense.getCategory().getDisplayName(),
                    expense.getAccount().getIdentifier(),
                    expense.getAccount().getDisplayName(),
                    expense.getNotes());
            rows++;
        }
        return write(destination, allowOverwrite, csv, rows, "expense export");
    }

    public ExportResult exportIncome(
            Path destination, boolean allowOverwrite) {
        StringBuilder csv = new StringBuilder(
                "id,date,amount,source,accountId,accountName,note\n");
        int rows = 0;
        for (Income income : incomeService.getAllIncome()) {
            appendRow(csv,
                    income.getId(),
                    income.getDate().toString(),
                    income.getAmount().toPlainString(),
                    income.getSource(),
                    income.getAccount().getIdentifier(),
                    income.getAccount().getDisplayName(),
                    income.getNote());
            rows++;
        }
        return write(destination, allowOverwrite, csv, rows, "income export");
    }

    public ExportResult exportTransfers(
            Path destination, boolean allowOverwrite) {
        StringBuilder csv = new StringBuilder(
                "id,date,amount,sourceAccountId,sourceAccountName,"
                + "destinationAccountId,destinationAccountName,note\n");
        int rows = 0;
        for (Transfer transfer : transferService.getAllTransfers()) {
            appendRow(csv,
                    transfer.getId(),
                    transfer.getDate().toString(),
                    transfer.getAmount().toPlainString(),
                    transfer.getSourceAccount().getIdentifier(),
                    transfer.getSourceAccount().getDisplayName(),
                    transfer.getDestinationAccount().getIdentifier(),
                    transfer.getDestinationAccount().getDisplayName(),
                    transfer.getNote());
            rows++;
        }
        return write(destination, allowOverwrite, csv, rows, "transfer export");
    }

    public ExportResult exportAccountSummary(
            Path destination, boolean allowOverwrite) {
        AccountBalanceSnapshot balances = financeService.calculateBalances();
        StringBuilder csv = new StringBuilder(
                "id,name,type,status,openingBalance,currentBalance\n");
        int rows = 0;
        for (Account account : accountService.listAllAccounts()) {
            appendRow(csv,
                    account.getIdentifier(),
                    account.getDisplayName(),
                    account.getType().name(),
                    account.isActive() ? "ACTIVE" : "ARCHIVED",
                    account.getOpeningBalance().toPlainString(),
                    balances.getBalance(account).toPlainString());
            rows++;
        }
        return write(
                destination, allowOverwrite, csv, rows, "account summary export");
    }

    public ExportResult exportReport(
            Path destination,
            boolean allowOverwrite,
            AdvancedReportSnapshot report) {
        AdvancedReportSnapshot snapshot = Objects.requireNonNull(
                report, "Report snapshot is required.");
        StringBuilder csv = new StringBuilder(
                "recordType,period,label,income,expenses,incomingTransfers,"
                + "outgoingTransfers,net,limit,actual,remaining\n");
        int rows = 0;
        appendRow(csv,
                "SUMMARY",
                snapshot.getStartDate() + " through " + snapshot.getEndDate(),
                "All matching entries",
                money(snapshot.getTotalIncome()),
                money(snapshot.getTotalExpenses()),
                "",
                "",
                money(snapshot.getNetCashFlow()),
                "",
                "",
                "");
        rows++;
        for (Map.Entry<Category, BigDecimal> entry
                : snapshot.getExpensesByCategory().entrySet()) {
            appendRow(csv,
                    "EXPENSE_CATEGORY", "", entry.getKey().getDisplayName(),
                    "", money(entry.getValue()), "", "", "", "", "", "");
            rows++;
        }
        for (Map.Entry<String, BigDecimal> entry
                : snapshot.getIncomeBySource().entrySet()) {
            appendRow(csv,
                    "INCOME_SOURCE", "", entry.getKey(),
                    money(entry.getValue()), "", "", "", "", "", "", "");
            rows++;
        }
        for (AccountActivitySummary activity
                : snapshot.getAccountActivity().values()) {
            appendRow(csv,
                    "ACCOUNT", "", activity.getAccount().getDisplayName(),
                    money(activity.getIncome()),
                    money(activity.getExpenses()),
                    money(activity.getIncomingTransfers()),
                    money(activity.getOutgoingTransfers()),
                    money(activity.getNetActivity()),
                    "", "", "");
            rows++;
        }
        for (MonthlyCashFlowSummary month
                : snapshot.getMonthlyTrend().values()) {
            appendRow(csv,
                    "MONTH", month.getMonth().toString(), "Cash flow",
                    money(month.getIncome()),
                    money(month.getExpenses()),
                    "", "", money(month.getNetCashFlow()), "", "", "");
            rows++;
        }
        for (BudgetActualSummary budget : snapshot.getBudgetActuals()) {
            String limit = budget.getOverallLimit()
                    .map(ExportService::money).orElse("");
            String remaining = budget.getOverallLimit()
                    .map(value -> money(value.subtract(
                        budget.getActualExpenses())))
                    .orElse("");
            appendRow(csv,
                    "BUDGET", budget.getMonth().toString(), "Overall",
                    "", "", "", "", "",
                    limit, money(budget.getActualExpenses()), remaining);
            rows++;
            for (Map.Entry<Category, BigDecimal> categoryLimit
                    : budget.getCategoryLimits().entrySet()) {
                BigDecimal actual = budget.getCategoryActuals()
                        .getOrDefault(categoryLimit.getKey(),
                                new BigDecimal("0.00"));
                appendRow(csv,
                        "BUDGET_CATEGORY",
                        budget.getMonth().toString(),
                        categoryLimit.getKey().getDisplayName(),
                        "", "", "", "", "",
                        money(categoryLimit.getValue()),
                        money(actual),
                        money(categoryLimit.getValue().subtract(actual)));
                rows++;
            }
        }
        return write(destination, allowOverwrite, csv, rows, "report export");
    }

    private static ExportResult write(
            Path destination,
            boolean allowOverwrite,
            StringBuilder csv,
            int rows,
            String dataName) {
        Path target = Objects.requireNonNull(
                destination, "Export destination is required.")
                .toAbsolutePath().normalize();
        SafeFileSupport.write(
                target,
                csv.toString().getBytes(StandardCharsets.UTF_8),
                allowOverwrite,
                ".spendwise-export-",
                dataName);
        return new ExportResult(target, rows);
    }

    private static String money(BigDecimal value) {
        return value.toPlainString();
    }

    private static void appendRow(StringBuilder csv, String... fields) {
        for (int index = 0; index < fields.length; index++) {
            if (index > 0) {
                csv.append(',');
            }
            appendField(csv, fields[index]);
        }
        csv.append('\n');
    }

    private static void appendField(StringBuilder csv, String value) {
        String field = Objects.requireNonNull(value, "Export field is required.");
        boolean quote = field.indexOf(',') >= 0
                || field.indexOf('"') >= 0
                || field.indexOf('\n') >= 0
                || field.indexOf('\r') >= 0;
        if (!quote) {
            csv.append(field);
            return;
        }
        csv.append('"');
        for (int index = 0; index < field.length(); index++) {
            char character = field.charAt(index);
            csv.append(character == '"' ? "\"\"" : character);
        }
        csv.append('"');
    }
}
