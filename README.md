# SpendWise Expense Tracker

SpendWise Expense Tracker is a Java Swing desktop application for a CSE215 Object-Oriented Programming semester project.

## Team

- Moon
- Nafij
- Monimul

## Current status

The project now includes validated expense, income, account, transfer, category, budget, and recurring-entry models; storage-independent repositories; safe UTF-8 CSV persistence; and a programmatic Swing interface with Expenses, Dashboard, Budgets, Finance, Calendar, Reports, and Recurring tabs. Users can manage financial entries and categories, configure budgets, inspect calendar activity, run filtered reports, maintain recurring definitions, explicitly generate due entries, and open Quick Entry with `Ctrl+Q`. Deterministic recurring occurrence IDs prevent duplicate posting during safe retries. The complete automated suite remains dependency-free and includes an isolated full-frame smoke test.

Feature status in this document will be updated only after the corresponding behavior has been implemented and verified.

## Purpose

SpendWise is intended to help an individual record income and expenses, organize transactions, monitor a budget, and understand personal spending patterns through a straightforward desktop interface.

## Planned core features

- Add, edit, and delete income and expense transactions (implemented)
- Assign dates, categories, amounts, and notes to transactions
- Add, rename, archive, and restore custom spending categories (implemented)
- Set and monitor monthly overall and optional category budgets (implemented)
- Create, rename, archive, and restore local financial accounts (implemented)
- Transfer funds between active accounts (implemented)
- View exact calculated account balances (implemented)
- Save and reload application data using local CSV files (implemented)
- Validate input and present clear error messages

## Planned advanced features

These features are candidates after the core workflow is complete:

- Search, filter, and sort income records (implemented)
- Calendar activity and advanced financial reports (implemented)
- Recurring expense, income, and transfer definitions (implemented)
- Keyboard-accessible Quick Entry (implemented)
- Exportable filtered summaries
- Local data backup and restore

## Technology stack

- Java 21
- Java Swing and Java2D for the graphical interface and charts
- Java standard libraries, including `java.time`, `java.math`, and `java.nio`
- Apache Ant
- Apache NetBeans project structure
- UTF-8 CSV files for local expense, income, account, transfer, budget, custom-category, and recurring-definition persistence

No external libraries are currently required.

## Prerequisites

- JDK 21 with both `java` and `javac` available
- Apache NetBeans with Java support, or Apache Ant for command-line builds

## Build with Apache NetBeans

1. Open the project root as an existing Apache NetBeans project.
2. Confirm that the project uses JDK 21.
3. Select **Run > Clean and Build Project**.
4. Select **Run > Run Project**, or press **F6**, to start the application.

The generated JAR is `dist/SpendWiseExpenseTracker.jar`.

## Build from the command line

Run the following from the project root when Ant is available on `PATH`:

```powershell
ant clean jar
```

On the verified development machine, use this fallback when `ant` is unavailable by name:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat clean jar
```

Run the generated application JAR with:

```powershell
java -jar dist\SpendWiseExpenseTracker.jar
```

## Run the core-model tests

The core-model tests use a plain Java runner and require no external testing library. Run:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-core
```

## Run the persistence tests

The persistence target runs the existing core tests before the CSV repository tests:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-persistence
```

## Run the service tests

The service target runs the core and persistence suites before the expense-service tests:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-service
```

## Run the GUI-foundation tests

The GUI target runs all earlier suites, the path-resolution tests, and the Swing tests in headless mode:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-gui
```

## Run the analytics and dashboard tests

The analytics target runs all earlier suites before the analytics-service and headless dashboard/chart tests:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-analytics
```

## Run the budget tests

The budget target reruns the complete earlier chain before the budget model, repository, service, and headless Swing suites:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-budget
```

## Run the category-management tests

The category target reruns every earlier suite before the category model, persistence, service, and headless Swing integration suites:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-category
```

## Run the finance tests

The finance target reruns every earlier suite before the account, income, transfer, persistence, service, path, and headless Swing suites:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-finance
```

On a graphical desktop, run the complete chain plus the isolated full-frame smoke test with:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-finance-gui-smoke
```

The smoke test uses a temporary data directory and verifies that opening and navigating the frame creates no CSV files.

## Run the calendar and report tests

The reports target reruns every earlier suite before the calendar/report service and headless Swing tests:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-reports
```

On a graphical desktop, run the complete chain plus the updated full-frame smoke test with:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-reports-gui-smoke
```

## Run the recurring and Quick Entry tests

The recurring target chains after all report tests and adds recurring model, CSV, service, Quick Entry integration, and headless Swing checks:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-recurring
```

The graphical variant also checks the seven-tab frame and Quick Entry menu wiring:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-recurring-gui-smoke
```

## Application data location

On Windows, expense data is stored at:

```text
%LOCALAPPDATA%\SpendWiseExpenseTracker\data\expenses.csv
```

Budget settings are stored beside it at:

```text
%LOCALAPPDATA%\SpendWiseExpenseTracker\data\budgets.csv
```

Custom category definitions are stored beside both files at:

```text
%LOCALAPPDATA%\SpendWiseExpenseTracker\data\categories.csv
```

Account, income, and transfer records use these sibling files:

```text
%LOCALAPPDATA%\SpendWiseExpenseTracker\data\accounts.csv
%LOCALAPPDATA%\SpendWiseExpenseTracker\data\income.csv
%LOCALAPPDATA%\SpendWiseExpenseTracker\data\transfers.csv
%LOCALAPPDATA%\SpendWiseExpenseTracker\data\recurring.csv
```

If `LOCALAPPDATA` is unavailable, SpendWise uses the equivalent location below `user.home\AppData\Local`. macOS uses `~/Library/Application Support`, while Linux and other Unix-like systems use `XDG_DATA_HOME` or the `~/.local/share` fallback.

Resolving these paths and starting the application are read-only operations. Each CSV file is created only by the first successful mutation for its own data area. Repository writes use complete UTF-8 snapshots and safe same-directory temporary-file replacement.

Opening or refreshing Expenses, Dashboard, Budgets, Manage Categories, Calendar, Reports, or Recurring is also read-only. These views do not create or rewrite any production CSV file. `recurring.csv` is created only after the first recurring-definition mutation.

## Accounts, income, and transfers

The Finance tab provides account, income, and transfer workflows. The protected Cash account represents legacy expenses and cannot be renamed or archived. Custom accounts have stable IDs, exact two-decimal opening balances, and active or archived status; archived accounts remain resolvable for history but are excluded from new transactions.

Income supports add, edit, delete, text search, account and inclusive date filtering, and stable sorting through `IncomeService`. Transfers are stored once as a movement between two different active accounts. A transfer reduces the source balance and increases the destination balance by the same exact amount, so it does not inflate the overall balance.

Legacy six-column `expenses.csv` files remain readable without migration and resolve to the protected Cash account. Account-aware expense mutations use the additive header `id,description,amount,date,category,account,notes`. Startup and ordinary viewing never rewrite a legacy file.

## Custom category management

**Manage Categories** in the Expenses workspace lists every built-in and custom category with its current status. Built-in categories, including Other, keep their original identifiers, names, and ordering and cannot be renamed or archived. Custom categories can be added, renamed, archived, and restored; hard deletion is not supported.

Each custom category has a stable identifier stored in expenses and budgets. Renaming changes only its display name. Archiving removes it from new expense and budget choices, while historical expenses, filters, analytics, reports, and existing budget limits continue to resolve and display it. Archiving a referenced category requires confirmation. Legacy expense and budget files using the original built-in identifiers remain compatible.

## Expense analytics dashboard

The Dashboard tab analyzes a selected calendar month and shows:

- Expense count, total, two-decimal average, previous-month total, and signed change
- A six-month Java2D bar chart ending in the selected month
- A Java2D donut chart using the selected month's category totals
- Overall budget limit, spent, remaining, usage percentage, and warning status
- A read-only monthly report with category spending, limit, remaining, status, and expense tables

Months without expenses remain in the trend with `0.00`, so gaps do not disappear. The Dashboard refreshes when its main tab is selected and can also be refreshed with its own button. The charts use only the Java standard library; no external chart library is required.

## Monthly budgets

The Budgets tab supports one optional overall limit and optional per-category limits for each calendar month. Blank fields mean that a limit is not configured, and category limits are independent of the overall limit. Status shows exact two-decimal spent, limit, remaining, and percentage values.

Warnings are informational: below 80% is within the limit, 80% through below 100% is near the limit, exactly 100% is limit reached, and above 100% is over the limit. These warnings never block adding, editing, or deleting expenses.

## Calendar and advanced reports

The Calendar tab displays a Sunday-based monthly grid with previous, next, and current-month navigation. Activity days show exact expense, income, and net totals. Selecting a day opens its expense, income, and transfer details; transfers remain visible without being counted as income or expense. Empty months and empty days have explicit status messages.

The Reports tab accepts an inclusive date range plus optional account and expense-category filters. It shows total income, expenses, net cash flow, ranked expense categories, income sources, account activity (including transfer directions), month-by-month trends, and budget-versus-actual rows where limits exist. Reports load repository snapshots without writing data, and refreshes preserve the entered range and available filters.

## Recurring entries and Quick Entry

The Recurring tab supports expense, income, and transfer definitions with daily, weekly, monthly, or yearly schedules, positive intervals, optional end dates, visible next-due dates, and active/inactive status. Nothing is posted at startup. **Generate Due Entries** is the explicit posting action, and monthly/yearly schedules retain their original day anchor across shorter months and leap years.

Each occurrence has a deterministic ID derived from its definition and due date. Repeating the generation action cannot create the same occurrence twice, including recovery after a transaction was written before schedule advancement completed. Definitions are deactivated rather than deleted so posted history remains explainable.

Quick Entry is available from **Entry > Quick Entry**, the Recurring tab, or `Ctrl+Q`. It delegates expense, income, and transfer creation to the existing services, keeps safe account/category choices for the current session, preserves failed input, and disables repeat submission while saving.

## Project structure

```text
SpendWiseExpenseTracker/
|-- build.xml
|-- manifest.mf
|-- nbproject/
|   |-- build-impl.xml
|   |-- genfiles.properties
|   |-- project.properties
|   `-- project.xml
|-- src/
|   `-- com/spendwise/
|       |-- app/SpendWiseApplication.java
|       |-- config/AppPaths.java
|       |-- model/
|       |   |-- Account.java
|       |   |-- AccountType.java
|       |   |-- Category.java
|       |   |-- Expense.java
|       |   |-- Income.java
|       |   |-- MonthlyBudget.java
|       |   |-- RecurrenceFrequency.java
|       |   |-- RecurringEntry.java
|       |   |-- RecurringEntryType.java
|       |   `-- Transfer.java
|       |-- repository/
|       |   |-- AccountRepository.java
|       |   |-- BudgetRepository.java
|       |   |-- CategoryRepository.java
|       |   |-- CsvAccountRepository.java
|       |   |-- CsvBudgetRepository.java
|       |   |-- CsvCategoryRepository.java
|       |   |-- CsvExpenseCodec.java
|       |   |-- CsvExpenseRepository.java
|       |   |-- CsvFileSupport.java
|       |   |-- CsvIncomeRepository.java
|       |   |-- CsvRecurringEntryRepository.java
|       |   |-- CsvTransferRepository.java
|       |   |-- ExpenseRepository.java
|       |   |-- IncomeRepository.java
|       |   |-- RecurringEntryRepository.java
|       |   |-- TransferRepository.java
|       |   `-- RepositoryException.java
|       |-- service/
|       |   |-- AccountBalanceSnapshot.java
|       |   |-- AccountService.java
|       |   |-- BudgetAlertLevel.java
|       |   |-- BudgetService.java
|       |   |-- BudgetStatusSnapshot.java
|       |   |-- BudgetUsage.java
|       |   |-- CategoryService.java
|       |   |-- FinanceNotFoundException.java
|       |   |-- FinanceService.java
|       |   |-- IncomeService.java
|       |   |-- IncomeSortOrder.java
|       |   |-- TransferService.java
|       |   |-- ExpenseAnalyticsService.java
|       |   |-- ExpenseAnalyticsSnapshot.java
|       |   |-- ExpenseNotFoundException.java
|       |   |-- ExpenseService.java
|       |   |-- ExpenseSortOrder.java
|       |   |-- FinancialReportingService.java
|       |   |-- QuickEntryResult.java
|       |   |-- QuickEntryService.java
|       |   |-- RecurringGenerationResult.java
|       |   |-- RecurringService.java
|       |   `-- ExpenseSummary.java
|       |-- ui/
|       |   |-- AccountTableModel.java
|       |   |-- BudgetLimitTableModel.java
|       |   |-- BudgetPanel.java
|       |   |-- CalendarPanel.java
|       |   |-- CategoryDonutChartPanel.java
|       |   |-- CategoryManagerDialog.java
|       |   |-- CategoryTableModel.java
|       |   |-- DashboardPanel.java
|       |   |-- ExpenseFormDialog.java
|       |   |-- ExpensePanel.java
|       |   |-- ExpenseTableModel.java
|       |   |-- FinancePanel.java
|       |   |-- AdvancedReportsPanel.java
|       |   |-- FinancialActivityTableModel.java
|       |   |-- QuickEntryDialog.java
|       |   |-- RecurringEntryTableModel.java
|       |   |-- RecurringPanel.java
|       |   |-- IncomeTableModel.java
|       |   |-- MonthlyBarChartPanel.java
|       |   |-- SpendWiseFrame.java
|       |   `-- TransferTableModel.java
|       `-- validation/
|           |-- ExpenseValidator.java
|           |-- FinanceValidator.java
|           `-- ValidationException.java
|-- test/
|   `-- com/spendwise/
|       |-- config/
|       |   |-- AppPathsTest.java
|       |   `-- FinanceAppPathsTest.java
|       |-- model/
|       |   |-- CategoryTest.java
|       |   |-- ExpenseTest.java
|       |   |-- FinanceModelTest.java
|       |   |-- MonthlyBudgetTest.java
|       |   `-- RecurringEntryTest.java
|       |-- repository/
|       |   |-- CsvBudgetRepositoryTest.java
|       |   |-- CsvCategoryRepositoryTest.java
|       |   |-- CsvExpenseRepositoryTest.java
|       |   |-- FinanceRepositoryTest.java
|       |   `-- RecurringRepositoryTest.java
|       |-- service/
|       |   |-- BudgetServiceTest.java
|       |   |-- CategoryServiceTest.java
|       |   |-- ExpenseAnalyticsServiceTest.java
|       |   |-- ExpenseServiceTest.java
|       |   |-- FinancialReportingServiceTest.java
|       |   |-- FinanceServiceTest.java
|       |   `-- RecurringQuickEntryServiceTest.java
|       `-- ui/
|           |-- BudgetFoundationTest.java
|           |-- CategoryManagementTest.java
|           |-- DashboardFoundationTest.java
|           |-- FinanceFoundationTest.java
|           |-- FinanceFrameSmokeTest.java
|           |-- CalendarReportsFoundationTest.java
|           |-- RecurringFoundationTest.java
|           `-- SwingFoundationTest.java
|-- docs/
|   `-- PROJECT_PLAN.md
|-- .gitattributes
|-- .gitignore
|-- AGENTS.md
`-- README.md
```

The generated `build/` and `dist/` directories and the machine-specific `nbproject/private/` directory are intentionally excluded from version control.

The CSV repositories support commas, doubled quotes, Unicode, and quoted line breaks. Account rows use `id,name,type,openingBalance,status`, income rows use `id,date,amount,source,account,note`, and transfer rows use `id,date,amount,sourceAccount,destinationAccount,note`. Mutations write a complete temporary file in the destination directory and replace the previous file only after the temporary content is closed, flushed, and forced to storage. Corrupt data is never silently reset or overwritten.

Authentication, dark mode, backup/restore, import/export, printing, cloud synchronization, and website functionality are not implemented. Multi-process file locking also remains outside the current scope.
