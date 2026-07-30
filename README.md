# SpendWise Expense Tracker

SpendWise Expense Tracker is a Java Swing desktop application for a CSE215 Object-Oriented Programming semester project.

## Team

- Moon
- Nafij
- Monimul

## Current status

The project now includes the core expense model, reusable validation, storage-independent repositories, safe UTF-8 CSV persistence, expense, budget, and category services, and a programmatic Swing interface with Expenses, Dashboard, and Budgets tabs. Users can manage expenses and custom categories, analyze a selected month, and configure an overall monthly budget plus optional independent category limits. The Dashboard shows budget status beside the existing charts and monthly report. All automated tests remain dependency-free.

Feature status in this document will be updated only after the corresponding behavior has been implemented and verified.

## Purpose

SpendWise is intended to help an individual record income and expenses, organize transactions, monitor a budget, and understand personal spending patterns through a straightforward desktop interface.

## Planned core features

- Add, edit, and delete income and expense transactions
- Assign dates, categories, amounts, and notes to transactions
- Add, rename, archive, and restore custom spending categories (implemented)
- Set and monitor monthly overall and optional category budgets (implemented)
- View balances and monthly income and expense summaries
- Save and reload application data using local CSV files
- Validate input and present clear error messages

## Planned advanced features

These features are candidates after the core workflow is complete:

- Search and filter across future income and expense records
- Additional financial planning views
- Recurring transaction templates
- Exportable filtered summaries
- Local data backup and restore

## Technology stack

- Java 21
- Java Swing and Java2D for the graphical interface and charts
- Java standard libraries, including `java.time`, `java.math`, and `java.nio`
- Apache Ant
- Apache NetBeans project structure
- UTF-8 CSV files for local expense, budget, and custom-category persistence

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

If `LOCALAPPDATA` is unavailable, SpendWise uses the equivalent location below `user.home\AppData\Local`. macOS uses `~/Library/Application Support`, while Linux and other Unix-like systems use `XDG_DATA_HOME` or the `~/.local/share` fallback.

Resolving these paths and starting the application are read-only operations. `expenses.csv` is created only after the first successful expense mutation, `budgets.csv` only after the first successful budget save, and `categories.csv` only after the first successful custom-category mutation. Repository writes use safe same-directory temporary-file replacement.

Opening or refreshing Expenses, Dashboard, Budgets, or Manage Categories is also read-only. These views do not create or rewrite any production CSV file.

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
|       |   |-- Category.java
|       |   |-- Expense.java
|       |   `-- MonthlyBudget.java
|       |-- repository/
|       |   |-- BudgetRepository.java
|       |   |-- CategoryRepository.java
|       |   |-- CsvBudgetRepository.java
|       |   |-- CsvCategoryRepository.java
|       |   |-- CsvExpenseCodec.java
|       |   |-- CsvExpenseRepository.java
|       |   |-- ExpenseRepository.java
|       |   `-- RepositoryException.java
|       |-- service/
|       |   |-- BudgetAlertLevel.java
|       |   |-- BudgetService.java
|       |   |-- BudgetStatusSnapshot.java
|       |   |-- BudgetUsage.java
|       |   |-- CategoryService.java
|       |   |-- ExpenseAnalyticsService.java
|       |   |-- ExpenseAnalyticsSnapshot.java
|       |   |-- ExpenseNotFoundException.java
|       |   |-- ExpenseService.java
|       |   |-- ExpenseSortOrder.java
|       |   `-- ExpenseSummary.java
|       |-- ui/
|       |   |-- BudgetLimitTableModel.java
|       |   |-- BudgetPanel.java
|       |   |-- CategoryDonutChartPanel.java
|       |   |-- CategoryManagerDialog.java
|       |   |-- CategoryTableModel.java
|       |   |-- DashboardPanel.java
|       |   |-- ExpenseFormDialog.java
|       |   |-- ExpensePanel.java
|       |   |-- ExpenseTableModel.java
|       |   |-- MonthlyBarChartPanel.java
|       |   `-- SpendWiseFrame.java
|       `-- validation/
|           |-- ExpenseValidator.java
|           `-- ValidationException.java
|-- test/
|   `-- com/spendwise/
|       |-- config/AppPathsTest.java
|       |-- model/
|       |   |-- CategoryTest.java
|       |   |-- ExpenseTest.java
|       |   `-- MonthlyBudgetTest.java
|       |-- repository/
|       |   |-- CsvBudgetRepositoryTest.java
|       |   |-- CsvCategoryRepositoryTest.java
|       |   `-- CsvExpenseRepositoryTest.java
|       |-- service/
|       |   |-- BudgetServiceTest.java
|       |   |-- CategoryServiceTest.java
|       |   |-- ExpenseAnalyticsServiceTest.java
|       |   `-- ExpenseServiceTest.java
|       `-- ui/
|           |-- BudgetFoundationTest.java
|           |-- CategoryManagementTest.java
|           |-- DashboardFoundationTest.java
|           `-- SwingFoundationTest.java
|-- docs/
|   `-- PROJECT_PLAN.md
|-- .gitattributes
|-- .gitignore
|-- AGENTS.md
`-- README.md
```

The generated `build/` and `dist/` directories and the machine-specific `nbproject/private/` directory are intentionally excluded from version control.

The expense CSV repository supports commas, doubled quotes, Unicode, and quoted line breaks. Budget settings use the separate `month,scope,category,amount` format, while custom categories use `id,name,status`. Mutations write a complete temporary file in the destination directory and replace the previous file only after the temporary content is closed, flushed, and forced to storage. Corrupt expense, budget, or category data is never silently reset or overwritten.

Income, recurring transactions, authentication, dark mode, backup/restore, import/export, printing, cloud synchronization, and website functionality are not implemented. Exported reports and multi-process file locking also remain outside the current scope.
