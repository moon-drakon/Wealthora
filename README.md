# Wealthora

**Take Control of Every Taka.**

**A Smart Personal Finance Management System**

Wealthora is built as a Java Swing desktop application for a CSE215 Object-Oriented Programming semester project.

## Team

- Moon
- Nafij
- Monimul

## Current status

The project is complete through Step 19. It includes validated financial and recurring-entry models, storage-independent repositories, safe UTF-8 CSV persistence, and a seven-tab programmatic Swing interface. Users can manage entries and categories, configure budgets, inspect calendar activity, run filtered reports, maintain recurring definitions, use Quick Entry, and manage persisted default accounts with read-only account statements. The Data menu adds validated ZIP backup/restore and read-only CSV exports. Restore validates every included CSV before mutation, creates a safety backup when current managed data exists, and rolls back staged replacement failures. The complete automated suite remains dependency-free and includes isolated restart/persistence and full-frame smoke tests.

Feature status in this document will be updated only after the corresponding behavior has been implemented and verified.

## Purpose

Wealthora helps an individual record income and expenses, organize transactions, monitor a budget, and understand personal spending patterns through a straightforward desktop interface.

## Core features

- Add, edit, and delete income and expense transactions (implemented)
- Assign dates, categories, amounts, and notes to transactions
- Add, rename, archive, and restore custom spending categories (implemented)
- Set and monitor monthly overall and optional category budgets (implemented)
- Create, rename, archive, and restore local financial accounts (implemented)
- Transfer funds between active accounts (implemented)
- View exact calculated account balances (implemented)
- Save and reload application data using local CSV files (implemented)
- Validate input and present clear error messages

## Advanced features

- Search, filter, and sort income records (implemented)
- Calendar activity and advanced financial reports (implemented)
- Recurring expense, income, and transfer definitions (implemented)
- Keyboard-accessible Quick Entry (implemented)
- Exportable financial data and filtered reports (implemented)
- Local ZIP backup and validated restore (implemented)
- Advanced account controls and account statements (implemented)

## Technology stack

- Java 25
- Java Swing and Java2D for the graphical interface and charts
- Java standard libraries, including `java.time`, `java.math`, and `java.nio`
- Apache Ant
- Apache NetBeans project structure
- UTF-8 CSV files for local expense, income, account, account-preference, transfer, budget, custom-category, and recurring-definition persistence

No external libraries are currently required.

## Prerequisites

- JDK 25 with both `java` and `javac` available
- Apache NetBeans with Java support, or Apache Ant for command-line builds

## Build with Apache NetBeans

1. Open the project root as an existing Apache NetBeans project.
2. Confirm that the project uses JDK 25.
3. Select **Run > Clean and Build Project**.
4. Select **Run > Run Project**, or press **F6**, to start the application.

The generated JAR is `dist/Wealthora.jar`.

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
$env:APP_OWNER_EMAIL = "shibli.moon.253@northsouth.edu"
& "C:\DevelopmentTools\jdk-25\jdk-25.0.2\bin\java.exe" -jar "dist\Wealthora.jar"
```

On the first launch, Wealthora opens the secure OWNER setup screen. The
OWNER email is locked to `APP_OWNER_EMAIL`; enter the owner's full name and a
password of at least 12 characters containing uppercase, lowercase, number,
and symbol characters. Existing finance CSV files are backed up and copied
byte-for-byte into the first owner's private workspace; the legacy originals
remain unchanged.

After the OWNER exists, use the same command whenever you run the app. Sign
Out and Switch Account are available from the account menu in the top-right
corner. Google Sign-In and self-service registration remain disabled until a
real authentication backend is configured.

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

## Run the backup, restore, and export tests

The data target chains after all recurring tests and adds isolated backup, restore, corruption, traversal, safety-backup, CSV escaping, exact-money, and filtered-export checks:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-data
```

The graphical variant also validates the Data menu wiring:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-data-gui-smoke
```

## Run the advanced account tests

The accounts target chains after all data-management tests and adds account-preference persistence, account lifecycle, exact statement totals, CSV compatibility, production-isolation, and headless Swing checks:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-accounts
```

The graphical variant validates the complete frame with advanced account controls wired:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-accounts-gui-smoke
```

## Run the final quality suite

The final chained target runs every dependency-free suite and then performs a complete isolated create, restart, reload, exact-balance, read-only-restart, and production-integrity smoke test:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-quality
```

On a graphical desktop, run the final chain plus the isolated seven-tab frame and menu smoke test with:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-quality-gui-smoke
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
%LOCALAPPDATA%\SpendWiseExpenseTracker\data\account-settings.csv
%LOCALAPPDATA%\SpendWiseExpenseTracker\data\income.csv
%LOCALAPPDATA%\SpendWiseExpenseTracker\data\transfers.csv
%LOCALAPPDATA%\SpendWiseExpenseTracker\data\recurring.csv
```

For compatibility with existing installations, Wealthora continues to use the `SpendWiseExpenseTracker` application-data directory. If `LOCALAPPDATA` is unavailable, it uses the equivalent location below `user.home\AppData\Local`. macOS uses `~/Library/Application Support`, while Linux and other Unix-like systems use `XDG_DATA_HOME` or the `~/.local/share` fallback.

Resolving these paths and starting the application are read-only operations. Each CSV file is created only by the first successful mutation for its own data area. Repository writes use complete UTF-8 snapshots and safe same-directory temporary-file replacement.

Opening or refreshing Expenses, Dashboard, Budgets, Manage Categories, Finance, Calendar, Reports, or Recurring is also read-only. These views do not create or rewrite any production CSV file. `recurring.csv` is created only after the first recurring-definition mutation, and `account-settings.csv` is created only when the user explicitly selects a default account.

## Accounts, income, and transfers

The Finance tab provides account, income, and transfer workflows. The protected Cash account represents legacy expenses and cannot be edited or archived. Custom accounts have stable IDs, exact two-decimal opening balances, editable names and types, and active or archived status. The Accounts view filters active or archived accounts, marks the persisted active default, chooses a safe replacement if that default is archived, and shows read-only activity plus exact income, expense, transfer-in, transfer-out, and calculated-balance totals. Opening balances remain immutable after account creation, and arbitrary balance editing is not supported.

The default account is preselected for new expenses, income, transfers, recurring definitions, and Quick Entry. Archived accounts remain resolvable and visible in historical records and statements but are excluded from all new-entry choices.

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

Voice Quick Entry is available on the Dashboard, Transactions screen, Quick Entry dialog, **Entry > Voice Quick Entry**, and `Ctrl+Shift+V`. English, বাংলা, and common Banglish commands are normalized into an editable draft containing type, exact `BigDecimal` amount, currency, accounts, category, date/time, payment method, notes, tags, and recurrence. Bangla digits and safe written scales are converted to canonical English numeric values; for example, `৫০০ টাকা` becomes `500 BDT` and `৩০ হাজার টাকা` becomes `30000 BDT`. The original transcript remains visible for review, while structured transaction fields use the application's English model values. Missing or ambiguous repository references are highlighted and never invented. A transaction is written through the existing services only after **Confirm and Add**.

Real microphone recognition uses Java Sound in the desktop and authenticated Google Cloud Speech-to-Text V1 calls in the Spring Boot server. The desktop includes microphone selection, Start/Stop/Cancel, a 30-second bound, duration, provider status, confidence, and an editable transcript before parsing; recognition and microphone work run outside the Swing EDT. Audio remains in memory only, is cleared after use, and is never placed in finance data or backups. The server requires `GOOGLE_CLOUD_PROJECT=wealthora-voice`, Application Default Credentials, and the Speech-to-Text API; no Google credential is bundled in the desktop JAR. When any prerequisite is unavailable, Start Listening is safely disabled while typed English, বাংলা, and Banglish commands remain fully usable.

## Backup, restore, and export

Use **Data > Create Backup** to choose a ZIP destination outside the application data directory. A backup contains a versioned manifest and every managed CSV file that currently exists; source, Git, build, credential, and unrelated files are never included. Existing destinations require explicit replacement confirmation.

Use **Data > Restore Backup** to inspect the archive timestamp and included filenames before confirming. Wealthora rejects unsupported versions, malformed manifests or CSV data, duplicate or unknown entries, corrupt ZIPs, and path traversal before modifying application data. Backups created before the rebrand remain supported. If managed data currently exists, a timestamped safety backup is written beside the selected archive. Restore applies the validated snapshot as one managed data set, removes managed files that were absent from that snapshot, never deletes unrelated files, and refreshes the application only after success.

The **Data > Export** submenu writes expenses, income, transfers, account summaries, or the currently selected filtered report to user-selected CSV files. Exports use clear headers, standard CSV escaping, and exact decimal text. They never mutate repositories and require confirmation before replacing a file.

## Project structure

The following tree shows the main package layout and representative production and test files:

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
|       |   |-- AccountPreferenceRepository.java
|       |   |-- AccountRepository.java
|       |   |-- BudgetRepository.java
|       |   |-- CategoryRepository.java
|       |   |-- CsvAccountPreferenceRepository.java
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
|       |   |-- AccountArchiveResult.java
|       |   |-- AccountBalanceSnapshot.java
|       |   |-- AccountService.java
|       |   |-- AccountStatementService.java
|       |   |-- AccountStatementSnapshot.java
|       |   |-- BackupService.java
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
|       |   |-- ExportService.java
|       |   |-- FinancialReportingService.java
|       |   |-- ManagedDataFiles.java
|       |   |-- QuickEntryResult.java
|       |   |-- QuickEntryService.java
|       |   |-- RecurringGenerationResult.java
|       |   |-- RecurringService.java
|       |   |-- SafeFileSupport.java
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
|       |   |-- DataManagementActions.java
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
|       |   |-- AccountPreferenceRepositoryTest.java
|       |   |-- CsvBudgetRepositoryTest.java
|       |   |-- CsvCategoryRepositoryTest.java
|       |   |-- CsvExpenseRepositoryTest.java
|       |   |-- FinanceRepositoryTest.java
|       |   `-- RecurringRepositoryTest.java
|       |-- service/
|       |   |-- AdvancedAccountServiceTest.java
|       |   |-- ApplicationPersistenceSmokeTest.java
|       |   |-- BudgetServiceTest.java
|       |   |-- BackupServiceTest.java
|       |   |-- CategoryServiceTest.java
|       |   |-- ExpenseAnalyticsServiceTest.java
|       |   |-- ExpenseServiceTest.java
|       |   |-- ExportServiceTest.java
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

## Safe development notes

Automated tests and smoke workflows use temporary directories and fingerprint the production data paths where relevant. Startup, path resolution, viewing, refresh, reporting, and restart checks are read-only. Do not edit generated `build/` or `dist/` files, commit machine-specific paths or credentials, or replace malformed CSV data silently. Use `ant clean jar` after meaningful changes and review `git diff` before a normal commit.

## Known limitations

Wealthora is currently a local-first, single-user desktop application. Its authentication screens and client contracts are integration-ready, but a real authentication backend, Google OAuth, cloud synchronization, and production session gating are not configured. Dark mode, validated CSV import, ZIP/JSON backup, CSV export, and dependency-free PDF summaries are available. Multi-process file locking, mobile clients, and website functionality are not implemented.
