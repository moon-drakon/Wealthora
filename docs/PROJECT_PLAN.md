# Wealthora Project Plan

## Current implementation status

The project is complete through Step 19. It includes validated financial and recurring-entry models, storage-independent repositories, safe UTF-8 CSV persistence, and a programmatic Swing application with seven primary tabs. Recurring definitions use explicit, idempotent generation, while Quick Entry delegates directly to existing transaction services. The Data menu provides validated ZIP backup/restore and read-only CSV exports. The final chained dependency-free suite preserves every earlier test and adds account lifecycle, restart/reload, production-integrity, and isolated full-frame checks.

## Problem statement

People often record small daily expenses inconsistently and then struggle to understand where their money went. Spreadsheet-based tracking can work, but it may be inconvenient for users who want a focused desktop workflow with validation, summaries, and budget feedback.

Wealthora addresses this problem with a local Java Swing application that organizes personal transactions and produces useful summaries without requiring an online account or external service.

## Project objectives

- Apply core object-oriented programming concepts in a complete semester project.
- Provide a clear workflow for recording income and expenses.
- Organize transactions using meaningful categories and dates.
- Calculate balances, spending totals, and budget progress accurately.
- Persist data locally in a human-readable CSV format.
- Build a professional programmatic Swing interface using standard Java.
- Keep the code understandable, testable, and suitable for a student viva.

## Primary users

The primary user is an individual student or household member who wants a lightweight offline tool for basic personal expense tracking. The implementation is single-user and assumes one local data set per installation.

## MVP scope

The implemented minimum viable product includes:

- A main Swing window with navigation between core screens
- Income and expense transaction creation
- Transaction editing and deletion with confirmation
- Date, type, amount, category, and optional note fields
- A transaction table with basic sorting or filtering
- Category management with sensible defaults
- A monthly budget and budget-progress display
- Current balance, total income, and total expense summaries
- CSV save and load behavior
- Input validation and user-friendly error messages
- Graceful handling of missing or malformed data files

## Advanced scope

The implemented advanced scope includes:

- Combined date, category, account, and text filters
- Warning indicators when spending approaches or exceeds a budget
- Calendar activity and advanced read-only reports
- Explicit, idempotent recurring definitions and Quick Entry
- Export of transactions, account summaries, and filtered reports to CSV
- Validated local ZIP backup and restore actions
- Persisted default accounts, lifecycle controls, and exact account statements

## Swing screens

### Main frame

`SpendWiseFrame` owns the application window and constructs all seven primary panels once. Data views refresh when their tabs are selected and after related mutations without moving repository construction or business rules into the frame. Its Entry menu exposes the `Ctrl+Q` Quick Entry shortcut.

### Dashboard

`DashboardPanel` presents selected-month expense count, total, average, previous-month total, and signed change. Its Overview retains both Java2D charts and adds overall budget status plus the highest category warning. Its Monthly Report retains the expense table and shows spent, limit, remaining, and status for every category. Empty months remain visible as `0.00`.

### Expenses

`ExpensePanel` now displays service-supplied expenses, filtered summaries, search and filter controls, sorting, refresh, and selected-row actions. `ExpenseFormDialog` supports add and edit input while retaining service and model validation as the authoritative rules.

### Finance

`FinancePanel` provides account metadata and lifecycle controls, a persisted active default, active/archived filtering, exact account statements, income CRUD and search, transfer CRUD, and exact calculated balances. Expense entry includes an account selector, and editing a historical expense retains its resolved account unless the user changes it.

### Budgets

`BudgetPanel` allows direct editing of an optional overall limit and optional per-category limits for a selected month. It shows exact spending, remaining, percentage, and warning status, confirms clears, preserves unsuccessful edits, and treats warnings as informational.

### Categories

The Expenses header opens a modal category manager with Name, Type, and Status columns. It supports adding and renaming custom categories and archiving or restoring them without hard deletion. Built-ins remain protected, and referenced categories require confirmation before archiving.

### Calendar and reports

`CalendarPanel` presents a monthly activity grid and selected-day details from immutable reporting snapshots. `AdvancedReportsPanel` presents validated, optionally filtered date-range totals and breakdown tables. Both panels are read-only and delegate all calculations to `FinancialReportingService`.

### Recurring and Quick Entry

`RecurringPanel` manages typed schedules and invokes generation only through the visible **Generate Due Entries** action. `QuickEntryDialog` provides a compact expense, income, and transfer form from the Entry menu or `Ctrl+Q`. `VoiceQuickEntryDialog` adds English/Banglish parsing, manual fallback, editable draft review, and explicit confirmation through the same services; no speech provider is bundled. Opening or cancelling either workflow does not write data.

### Data protection and export

The Data menu exposes user-selected backup, restore, and export actions. It confirms replacement and restore operations, displays the validated archive summary, and refreshes panels only after a complete restore. The UI delegates archive validation, transaction-safe replacement, and CSV generation to `BackupService` and `ExportService`.

## Java package architecture

```text
com.spendwise.app
    SpendWiseApplication
com.spendwise.config
    AppPaths
com.spendwise.model
    Account, AccountType, Income, Transfer
    Expense, Category, MonthlyBudget
    RecurringEntry, RecurringEntryType, RecurrenceFrequency
com.spendwise.repository
    ExpenseRepository, CsvExpenseRepository, CsvExpenseCodec,
    AccountRepository, CsvAccountRepository,
    AccountPreferenceRepository, CsvAccountPreferenceRepository,
    IncomeRepository, CsvIncomeRepository,
    TransferRepository, CsvTransferRepository,
    BudgetRepository, CsvBudgetRepository,
    CategoryRepository, CsvCategoryRepository, RepositoryException
    RecurringEntryRepository, CsvRecurringEntryRepository
com.spendwise.service
    AccountService, IncomeService, TransferService, FinanceService
    AccountBalanceSnapshot, AccountStatementService,
    AccountStatementSnapshot, AccountArchiveResult, IncomeSortOrder
    BudgetService, BudgetUsage, BudgetStatusSnapshot, BudgetAlertLevel
    CategoryService
    ExpenseAnalyticsService, ExpenseAnalyticsSnapshot,
    FinancialReportingService, CalendarMonthSnapshot,
    DailyActivitySnapshot, AdvancedReportSnapshot,
    RecurringService, RecurringGenerationResult, QuickEntryService,
    BackupService, BackupInspection, RestoreResult, ExportService,
    ExpenseService, ExpenseSummary, ExpenseSortOrder,
    ExpenseNotFoundException
com.spendwise.ui
    SpendWiseFrame, ExpensePanel, ExpenseFormDialog, ExpenseTableModel
    DashboardPanel, MonthlyBarChartPanel, CategoryDonutChartPanel
    BudgetPanel, BudgetLimitTableModel,
    CategoryManagerDialog, CategoryTableModel
    FinancePanel, AccountTableModel, IncomeTableModel, TransferTableModel
    CalendarPanel, AdvancedReportsPanel, FinancialActivityTableModel
    RecurringPanel, RecurringEntryTableModel, QuickEntryDialog
    DataManagementActions
com.spendwise.validation
    ExpenseValidator, FinanceValidator, ValidationException
```

This structure separates responsibilities without introducing unnecessary frameworks, dependency injection containers, or enterprise layers.

## Responsibility overview

### Model

Model classes represent the application's data and basic invariants:

- `Expense` represents an occurred expense with an identifier, description, amount, date, category, account, and notes. Legacy constructors still resolve to the protected default account.
- `Account` is immutable and carries a stable ID, display name, type, exact opening balance, protection flag, and active/archive state.
- `Income` and `Transfer` are immutable, validated records with generated or persisted stable IDs.
- `Category` is now an immutable category definition with a stable identifier, display name, built-in/custom classification, and active/archived status. Its original built-in constants, identifiers, names, and order remain compatible.
- `MonthlyBudget` now represents one month, an optional overall limit, and configured category limits as immutable two-decimal values.

Money uses `BigDecimal`, and dates use `LocalDate` and `YearMonth`.

### Repository and storage

The repository package now provides a storage-independent expense contract and a CSV implementation. It:

- Keeps file-format knowledge out of the UI.
- Reads the legacy `id,description,amount,date,category,notes` expense format and writes the additive account-aware format.
- Escapes commas, doubled quotes, Unicode text, and quoted line breaks.
- Uses UTF-8, ISO `LocalDate` text, plain `BigDecimal` text, and stable category identifiers.
- Rejects malformed records and duplicate IDs without returning partial results.
- Writes through a same-directory temporary file and replaces the target only after a complete flush and close.
- Stores budgets separately with the exact `month,scope,category,amount` header, chronological months, and stable category order.
- Stores only custom category definitions in `categories.csv` with the exact `id,name,status` header; built-ins remain defined once in code.
- Refuses to overwrite malformed existing budget data.
- Resolves stable category identifiers through the current catalog and rejects unknown identifiers instead of using a fallback category.
- Stores custom accounts, income, and transfers in separate additive CSV files with exact decimal text and stable account IDs.
- Reads both legacy six-column expenses and account-aware seven-column expenses without startup migration.

### Service

`ExpenseService` now coordinates expense creation, lookup, replacement-based updates, deletion, combined querying, stable sorting, and summary calculations through the repository abstraction. It reuses model validation and has no CSV, file-path, or Swing knowledge. `ExpenseSummary` provides immutable two-decimal totals, averages, counts, and totals for every category. `ExpenseAnalyticsService` reads one current service snapshot per analysis and produces an immutable `ExpenseAnalyticsSnapshot` for a selected month, previous-month comparison, and an ordered trend without caching or writing data.

`BudgetService` stores or clears monthly plans through `BudgetRepository` and evaluates an existing analytics snapshot without reloading expenses. `BudgetUsage` calculates exact remaining amounts and two-decimal `HALF_UP` percentages, while exact comparisons classify within-limit, near-limit, reached, and over-limit states.

`CategoryService` merges the protected built-in catalog with persisted custom definitions and owns name uniqueness, add, rename, archive, restore, selectable-category, and stable-ID resolution rules. Archived categories remain resolvable for existing expense and budget history but are excluded from new selections.

`AccountService` combines the protected default Cash account with persisted custom accounts and owns uniqueness, stable-ID resolution, selection, rename, archive, and restore rules. `IncomeService` provides validated CRUD, combined search/filtering, and stable sorting. `TransferService` validates one atomic transfer record between different active accounts. `FinanceService` calculates exact balances as opening balance plus income minus expenses plus incoming transfers minus outgoing transfers.

`AccountService` also validates and persists one active default account. `AccountStatementService` derives read-only activity and exact account-specific income, expense, transfer-in, transfer-out, opening-balance, and current-balance totals from existing ledger services; no direct balance editing is permitted.

`FinancialReportingService` builds immutable calendar and date-range report snapshots from existing services. It calculates daily and monthly cash flow, category and source totals, account activity, ranked categories, and budget actuals with exact `BigDecimal` arithmetic. Transfers appear in activity details and account movement totals but never inflate income or expense totals.

`RecurringService` validates definitions, identifies due occurrences, and delegates posting to the established expense, income, and transfer services. An occurrence ID is derived from the stable definition ID and due date. Generation advances and saves the next due date after each occurrence; a retry detects an already-written occurrence and advances without duplication. `QuickEntryService` is a thin delegation boundary that reuses the same transaction services and validation.

`BackupService` includes only known managed CSV files, writes a versioned standard-library ZIP through a same-directory temporary file, and validates the manifest, safe entry names, size limits, and every included CSV through the real repositories before restore. It creates a safety backup when current managed data exists and rolls back prior bytes if staged replacement fails. `ExportService` builds escaped UTF-8 CSV snapshots for transactions, balances, and the current filtered advanced report without repository mutation.

### UI

The UI package creates programmatic Swing components, translates user actions into service calls, and displays results or validation messages. `SpendWiseFrame` exposes Expenses, Dashboard, Budgets, Finance, Calendar, Reports, and Recurring tabs, with Expenses still first. Financial mutations and successful restore operations refresh derived views while preserving relevant selections where practical.

## CSV persistence plan

All CSV persistence uses caller-supplied paths rather than a hard-coded developer location. `AppPaths` resolves sibling `expenses.csv`, `budgets.csv`, `categories.csv`, `accounts.csv`, `account-settings.csv`, `income.csv`, `transfers.csv`, and `recurring.csv` files below the platform data directory. Path resolution, startup, construction, viewing, and refresh do not create files; each file is created only by a successful mutation in its own data area.

The implemented format uses UTF-8, a required header, stable column order, ISO dates, stable category identifiers, and decimal amounts produced with `BigDecimal.toPlainString()`. `CsvExpenseCodec` owns quoting and parsing rules, including quoted line breaks, LF and CRLF input, and an optional UTF-8 BOM before the header. Loading constructs each `Expense` through existing model validation. Mutations prepare the full snapshot before writing and use same-directory temporary-file replacement.

CSV is appropriate for the course scope because it is inspectable and requires no external database dependency. It is not intended for high-volume or multi-user data.

Budget CSV rows use ISO `YearMonth`, `OVERALL` or `CATEGORY` scope, stable category identifiers, and exact two-decimal limits. Custom category rows use stable IDs, CSV-escaped display names, and `ACTIVE` or `ARCHIVED` status. Existing built-in category values require no migration. Other months survive replacement or deletion, and all repositories use complete same-directory temporary files before target replacement.

Account rows use `id,name,type,openingBalance,status`; income rows use `id,date,amount,source,account,note`; and transfer rows use `id,date,amount,sourceAccount,destinationAccount,note`. All account references use stable IDs. Legacy expenses retain `id,description,amount,date,category,notes`; account-aware files add an `account` column before `notes`.

Recurring rows store stable definition IDs, typed entry and frequency values, exact amounts, stable category/account references, interval, start/end/next-due dates, and active status. The repository rejects malformed rows, duplicate IDs, unsupported values, and unknown references without replacing the source file.

## Validation and error handling

`ExpenseValidator` now centralizes normalization and validation for expense IDs, descriptions, amounts, dates, categories, and notes. It rejects invalid values before an `Expense` is created or updated, and `Expense.updateDetails(...)` validates the complete proposed state before changing any field.

- Require a transaction type, date, category, and amount.
- Require amounts to be positive and within a practical numeric range.
- Parse dates through `java.time` rather than manual string splitting.
- Prevent blank or duplicate category names after trimming.
- Reject category control characters, invalid stable identifiers, and case-insensitive duplicate names across active, archived, and built-in definitions.
- Prevent invalid budget values.
- Treat blank budget fields as unconfigured limits and require configured limits to be positive with no more than two meaningful decimals.
- Classify budget warnings with exact comparisons: below 80%, 80% to below 100%, exactly 100%, and above 100%.
- Confirm destructive UI actions such as transaction deletion.
- Catch file and parsing errors at a boundary where they can be logged or converted into a clear user message.
- Avoid displaying raw stack traces to users.
- Preserve valid in-memory data when loading or saving fails.

## Testing strategy

Testing combines focused automated checks with repeatable manual GUI testing:

- Plain Java main-based tests with explicit `AssertionError` helpers for model invariants and validation
- Temporary test directories for storage tests so real user data is not changed
- Edge cases for decimal amounts, empty notes, quoted CSV fields, invalid rows, and month boundaries
- Headless Swing checks for table mapping, immutable snapshots, parsing, events, and empty summaries
- Deterministic analytics checks for month boundaries, previous-month change, chronological trends, immutable snapshots, and single-snapshot loading
- Headless `BufferedImage` rendering checks for empty and populated Java2D charts and dashboard refresh safety
- Budget model, CSV corruption, safe-replacement, calculation-boundary, and headless editor/status checks
- Category immutability, built-in compatibility, CSV corruption/replacement, service mutation, archived-history, selector-refresh, and headless manager-state checks
- Account, income, and transfer validation; legacy expense compatibility; repository safe writes and corruption; CRUD, search, stable sorting, exact balances, transfer neutrality, refresh warnings, and Swing table foundations
- Calendar alignment, leap years, daily totals and details, transfer exclusion, date-range validation, grouping, trends, budgets versus actuals, immutable read-only snapshots, and headless panel checks
- Daily, weekly, monthly, yearly, interval, leap-year, and month-end recurrence; optional end dates; inactive definitions; retry-safe generation; all three entry types; CSV corruption; Quick Entry delegation; and headless panel checks
- Complete and partial ZIP backups, manifest/content validation, overwrite protection, corrupted and unsupported archives, path traversal, validation-before-mutation, safety backup, successful restore, unrelated-file preservation, export escaping, exact money, filtered reports, and repository immutability
- Persisted default-account, lifecycle, exact statement, account-choice, CSV compatibility, and production-integrity checks
- A complete isolated create, restart, reload, exact-balance, and read-only-restart smoke test across all managed data files
- An isolated graphical full-frame smoke test that uses temporary paths and verifies read-only startup and tab navigation
- Isolated path-resolution checks that do not modify environment variables or production data
- Manual checks for navigation, table updates, dialogs, keyboard focus, resizing, and error messages
- A clean Ant build before each milestone is accepted
- Regression checks for previously completed workflows

No external testing dependency is required. Adding one later requires explicit approval.

Run the complete clean build with:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat clean jar
```

The implemented core-model tests can be run with:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-core
```

The persistence target reruns the core tests and then runs all CSV repository tests:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-persistence
```

The service target reruns both earlier suites and then runs the dependency-free service tests:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-service
```

The GUI target reruns all earlier suites, then runs path and headless Swing-foundation tests:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-gui
```

The analytics target reruns the complete earlier chain, then runs analytics-service and headless dashboard/chart tests:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-analytics
```

The budget target reruns the complete earlier chain, then runs the budget model, repository, service, and headless UI tests:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-budget
```

The category target reruns the complete earlier chain, then runs category model, repository, service, and headless UI integration tests:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-category
```

The finance target reruns the complete earlier chain, then runs the account, income, transfer, persistence, service, path, and headless Swing tests:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-finance
```

On a graphical desktop, the Finance GUI-smoke target reruns the complete chain and then opens the full frame against an isolated temporary data directory:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-finance-gui-smoke
```

The reports target chains after Finance and runs the calendar/report service and headless Swing suites. Its graphical variant also exercises all six frame tabs using temporary data:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-reports
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-reports-gui-smoke
```

The recurring target chains after reports and adds the recurring model, CSV repository, generation, Quick Entry integration, and headless Swing suites. Its graphical variant checks all seven tabs and menu wiring:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-recurring
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-recurring-gui-smoke
```

The data target chains after recurring and runs isolated backup/restore and export suites. Its graphical variant checks the Data menu without touching production data:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-data
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-data-gui-smoke
```

The accounts target chains after data management and adds default-account persistence, lifecycle, statement-total, compatibility, production-isolation, and headless Swing checks. Its graphical variant checks the complete advanced-account wiring:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-accounts
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat test-accounts-gui-smoke
```

## Milestones

1. **Foundation (complete):** establish the Java 25 NetBeans project, repository baseline, documentation, and repeatable build.
2. **Domain model (complete for implemented scope):** implement model classes, enums, validation rules, and calculation tests for expenses, categories, budgets, accounts, income, and transfers.
3. **CSV storage (complete for implemented scope):** implement encoding, loading, safe replacement, corruption handling, and round-trip tests for every current data area.
4. **Expense service layer (complete):** implement validated expense CRUD operations, combined text/category/date queries, stable sorting, and overall or filtered summaries.
5. **Expense-management UI (complete):** implement the main frame, expense table, add/edit/confirmed-delete workflows, filtering, sorting, refresh, and displayed-result summaries.
6. **Expense analytics dashboard (complete):** add selected-month summaries, previous-month comparison, six-month and category charts, and a read-only monthly report.
7. **Monthly budgets (complete):** persist monthly overall and category limits, calculate exact usage warnings, add the Budgets tab, and integrate status into Dashboard.
8. **Custom category management (complete):** persist stable custom definitions, support add, rename, archive, and restore, and preserve historical expense, analytics, and budget behavior.
9. **Income, accounts, and transfers (complete):** implement stable local accounts, income CRUD and querying, transfer workflows, exact balances, compatible expense account assignment, and safe additive CSV persistence.
10. **Calendar and advanced reports (complete):** add coherent date-based activity and reporting snapshots.
11. **Recurring entries and quick entry (complete):** add explicit, idempotent due-item posting and reviewed shortcuts.
12. **Backup, restore, and export (complete):** add validated offline data protection.
13. **Advanced account controls (complete):** add persisted defaults, safe metadata and lifecycle controls, exact statements, and account insights without direct balance editing.
14. **Quality pass (complete):** complete regression testing, usability fixes, documentation updates, and demo preparation.

### Execution Step 15 — Calendar and advanced reports

Step 15 implements roadmap milestone 10. It provides a calendar-based financial activity view and advanced read-only reporting from existing expense, income, transfer, account, budget, and category data.

The calendar:

- Display one month at a time with previous-month, next-month, and current-month navigation.
- Show correct calendar days and weekday alignment, including leap years.
- Show concise daily expense, income, and net-cash-flow totals and clearly distinguish days with activity.
- Allow day selection and show that day's entry type, amount, account, category where applicable, description, and date.
- Display transfers in day details without counting them as income or expense.
- Refresh after expense, income, or transfer changes while preserving the selected month where practical.
- Present a clear empty state and use `java.time` types.
- Keep calculations outside Swing event handlers.

Advanced reports provide:

- Income-versus-expense and net-cash-flow totals for a validated date range.
- Expense totals grouped by category and income totals grouped by source.
- Account activity summaries and monthly income/expense trends.
- Highest expense categories for the selected period.
- Budget-versus-actual information where existing monthly budget data permits it.
- Relevant account and category filters.
- Exact `BigDecimal` calculations with transfers excluded from income and expense totals.
- Read-only behavior that never mutates repositories.

The UI reuses the current Swing styling and reporting architecture with standard Swing tables and summary cards, without an external chart dependency.

Step 15 tests cover calendar alignment, leap years, daily totals, transfer exclusion, day details, empty months, date-range validation, income-versus-expense calculations, category and account grouping, monthly trends, budget-versus-actual calculations, repository immutability, and headless-compatible panel construction. Its Ant target chains after the existing Finance suite.

### Execution Step 16 — Recurring entries and quick entry

Step 16 implements roadmap milestone 11. It provides typed recurring definitions and a compact path for creating common financial entries.

A recurring definition has:

- A stable identifier and an expense, income, or transfer entry type.
- An exact `BigDecimal` amount, description, relevant category, source account, and transfer destination account where required.
- Daily, weekly, monthly, or yearly frequency with a positive interval.
- A start date, optional end date, next due date, and active/inactive status.
- Complete creation and update validation.

The recurring service:

- Determine due occurrences and generate them only through an explicit user action.
- Prevent duplicate occurrence generation and advance the next due date correctly.
- Handle month-end dates and leap years safely.
- Respect optional end dates and inactive definitions.
- Generate expenses, income, and compatible transfers through existing services.
- Persist definitions and posted-occurrence state using safe CSV behavior.
- Reject malformed data instead of replacing it.
- Use temporary locations during automated tests.

The recurring UI adds, edits, activates/deactivates, displays the next due date, and manually generates due entries. Definitions are deactivated instead of deleted so their relationship to generated history remains explainable.

Quick Entry:

- Create expenses, income, and transfers through existing services and validation.
- Avoid duplicating business logic.
- Retain safe, non-sensitive session defaults where practical.
- Support keyboard-focused navigation and a practical non-conflicting shortcut.
- Keep failed input intact, prevent double submission, show clear messages, and refresh affected panels after success.

Step 16 tests cover every frequency, intervals greater than one, leap-year and month-end behavior, optional end dates, inactive definitions, duplicate prevention, due-date advancement, each generated entry type, invalid accounts, CSV round trips and corruption, Quick Entry validation/service integration, and production-data isolation. Its Ant target chains after Step 15.

### Execution Step 17 — Backup, restore, and export

Step 17 implements roadmap milestone 12. It adds user-controlled local data protection and read-only exports.

A backup:

- Include every managed application CSV file that currently exists.
- Include a manifest with the backup format version, application name, creation timestamp, and included filenames.
- Use a user-selected destination and temporary output before final replacement.
- Never overwrite without explicit confirmation.
- Exclude source, Git data, build output, credentials, and unrelated files.
- Work when optional data files do not exist and clearly report success or failure.

A standard-library ZIP format is approved.

Restore:

- Require explicit backup selection and validate the manifest and every expected file before mutation.
- Reject unsupported, malformed, corrupted, and path-traversal archives.
- Show the restore summary and require explicit confirmation.
- Create a safety backup when current data exists.
- Use temporary files and safe same-directory replacement where practical.
- Avoid partial restore, preserve original data after any validation or restore failure, and never delete unrelated files.
- Refresh application state only after complete success.

Export supports:

- All expenses, income, and transfers as CSV.
- Account summaries as CSV.
- Date-range and compatible filtered Step 15 reports as CSV.
- Clear headers, correct escaping, and exact monetary text.
- User-selected destinations with explicit overwrite confirmation.
- Read-only behavior that never mutates repositories.

Step 17 tests use temporary directories exclusively and cover complete and partial backups, manifest correctness, existing destinations, corrupted/unsupported/path-traversal archives, validation before mutation, failure preservation, safety backups, successful restore, export headers and escaping, exact money, filtered exports, and production-data isolation.

### Execution Step 18 — Advanced account controls

Step 18 implements roadmap milestone 13. It improves account management without changing historical ledger meaning or allowing arbitrary balance edits.

Advanced account controls:

- Edit account names and other allowed non-destructive metadata while preserving stable IDs and opening-balance design.
- Set one active account as the user-selected default and persist that selection.
- Archive and restore accounts and provide a clear active/archived view or filter.
- Preserve all historical expense, income, and transfer references without rewriting old transactions.
- Exclude archived accounts from new expenses, income, and transfers while retaining them in historical displays.
- Continue rejecting same-account and archived-account transfers and duplicate names under current naming rules.
- Ensure the selected default remains active and choose a valid replacement with clear feedback when the current default is archived.
- Prevent archival from leaving no active account unless an equivalent safe rule exists.
- Preserve exact balances and expose account activity history.
- Show account-specific income, expense, incoming-transfer, outgoing-transfer, and current-balance totals.

No unrestricted direct balance editing will be introduced.

Step 18 tests cover rename and stable IDs, non-destructive metadata edits, duplicate names, default selection and persistence, archive/restore, historical compatibility, exclusion from new entries, replacement-default behavior, protected-Cash active-account safety, exact post-lifecycle balances, account activity totals, CSV backward compatibility, account-settings corruption handling, headless Swing integration, and production-data isolation. The Ant target chains after Step 17.

### Execution Step 19 — Quality pass

Step 19 completes roadmap milestone 14 without introducing another major feature.

The code-quality review checked duplicate and unused code, event handlers, package structure and naming, CSV safety, exact money calculations, validation boundaries, Swing/service separation, exception handling, stack-trace exposure, machine-specific paths, temporary/resource cleanup, production-data creation, JAR contents, dependency wiring, and cross-panel refresh behavior. The reachability review found no production class that could be safely removed. Cleanup failures are now reported or attached to the original failure, and the full-frame fixture uses the same persisted default-account repository wiring as production.

The UI-quality review covered spacing, labels, button names, empty states, validation messages, natural tab order, keyboard accessibility, resizing, clipping, duplicate submission, destructive confirmations, mutation refreshes, unrelated-refresh draft preservation, money formatting, archived indicators, and status feedback. Choice-loading failures in expense, Finance, recurring, and Quick Entry workflows now remain inside clear UI error boundaries.

Reliability validation includes:

- A clean build and every test target separately.
- The final chained target and isolated full-frame GUI smoke test.
- Expense, budget, category, Finance, calendar/report, recurring-entry, backup/restore/export, and advanced-account workflow smoke tests.
- An isolated restart-and-persistence smoke test.
- JAR-content validation and production-data integrity verification.

Maintained documentation describes the project, architecture, completed features, build and test commands, NetBeans and JAR execution, data-file behavior, backup/restore usage, limitations, project structure, safe development notes, and completion through Step 19. The only new quality regression suite is the high-value isolated restart/persistence smoke test; passing tests were not rewritten for style or assertion-count padding.

## Demo and viva preparation checklist

- Build the project successfully from a clean checkout.
- Demonstrate a complete add, edit, delete, save, restart, and reload workflow.
- Demonstrate input validation and recovery from one malformed-data scenario.
- Explain how encapsulation, composition, enums, and separation of responsibilities are used.
- Explain why `BigDecimal` and `java.time` types were selected.
- Identify the responsibilities of the model, storage, service, and UI packages.
- Show representative tests and describe important edge cases.
- Ensure every team member can explain the classes they present.
- Keep the demo data realistic and free of personal or sensitive information.
- State current limitations honestly.

## Known scope limits

- Single-user, offline desktop use only
- CSV storage rather than a relational database
- No cloud synchronization or multi-device support
- No multi-process CSV file locking
- No general-purpose data import beyond validated Wealthora backup restore
- No PDF or printable report layout; advanced reports are on-screen and exportable only as CSV data
- No bank, payment-provider, or financial-account integration
- No authentication, shared accounts, or role management
- No dark mode or theme switching
- No printing or website
- No encryption beyond protections provided by the local operating system
- No mobile or web client
- No claim of professional financial advice
