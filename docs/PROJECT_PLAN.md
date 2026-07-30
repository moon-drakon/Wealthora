# SpendWise Expense Tracker Project Plan

## Current implementation status

The project currently includes validated expense, category, and monthly-budget models; storage-independent repositories; safe UTF-8 CSV persistence; expense, category, and budget services; and a programmatic Swing application with Expenses, Dashboard, and Budgets tabs. The expense workspace and analytics dashboard retain their existing CRUD, query, summary, chart, and monthly-report behavior. Monthly budgets and custom-category add, rename, archive, restore, historical resolution, and selector refresh are implemented. The dependency-free suites contain 23 expense-model, 35 expense-persistence, 60 expense-service, 18 path, 25 Swing-foundation, 48 analytics-service, 43 dashboard-foundation, 20 budget-model, 35 budget-repository, 40 budget-service, 40 budget-foundation, 18 category-model, 29 category-persistence, 23 category-service, and 18 category-management tests.

## Problem statement

People often record small daily expenses inconsistently and then struggle to understand where their money went. Spreadsheet-based tracking can work, but it may be inconvenient for users who want a focused desktop workflow with validation, summaries, and budget feedback.

SpendWise will address this problem with a local Java Swing application that organizes personal transactions and produces useful summaries without requiring an online account or external service.

## Project objectives

- Apply core object-oriented programming concepts in a complete semester project.
- Provide a clear workflow for recording income and expenses.
- Organize transactions using meaningful categories and dates.
- Calculate balances, spending totals, and budget progress accurately.
- Persist data locally in a human-readable CSV format.
- Build a professional programmatic Swing interface using standard Java.
- Keep the code understandable, testable, and suitable for a student viva.

## Primary users

The primary user is an individual student or household member who wants a lightweight offline tool for basic personal expense tracking. The planned version is single-user and assumes one local data set per installation.

## MVP scope

The minimum viable product will include:

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

Advanced work will begin only after the MVP is stable. The team may select a feasible subset of:

- Combined date, type, category, and text filters
- Warning indicators when spending approaches or exceeds a budget
- Recurring transaction templates that create user-confirmed entries
- Export of the current filtered summary to CSV
- Local backup and restore actions

Advanced items are proposals, not implemented features or fixed delivery promises.

## Proposed Swing screens

### Main frame

`SpendWiseFrame` owns the application window and constructs the Expenses, Dashboard, and Budgets panels once. Dashboard and budget status refresh when their tabs are selected without moving repository construction or business rules into the frame.

### Dashboard

`DashboardPanel` presents selected-month expense count, total, average, previous-month total, and signed change. Its Overview retains both Java2D charts and adds overall budget status plus the highest category warning. Its Monthly Report retains the expense table and shows spent, limit, remaining, and status for every category. Empty months remain visible as `0.00`.

### Expenses

`ExpensePanel` now displays service-supplied expenses, filtered summaries, search and filter controls, sorting, refresh, and selected-row actions. `ExpenseFormDialog` supports add and edit input while retaining service and model validation as the authoritative rules.

### Budgets

`BudgetPanel` allows direct editing of an optional overall limit and optional per-category limits for a selected month. It shows exact spending, remaining, percentage, and warning status, confirms clears, preserves unsuccessful edits, and treats warnings as informational.

### Categories

The Expenses header opens a modal category manager with Name, Type, and Status columns. It supports adding and renaming custom categories and archiving or restoring them without hard deletion. Built-ins remain protected, and referenced categories require confirmation before archiving.

## Proposed Java package architecture

```text
com.spendwise.app
    SpendWiseApplication
com.spendwise.config
    AppPaths
com.spendwise.model
    Expense, Category, MonthlyBudget
    Additional income models (planned)
com.spendwise.repository
    ExpenseRepository, CsvExpenseRepository, CsvExpenseCodec,
    BudgetRepository, CsvBudgetRepository,
    CategoryRepository, CsvCategoryRepository, RepositoryException
com.spendwise.service
    BudgetService, BudgetUsage, BudgetStatusSnapshot, BudgetAlertLevel
    CategoryService
    ExpenseAnalyticsService, ExpenseAnalyticsSnapshot,
    ExpenseService, ExpenseSummary, ExpenseSortOrder,
    ExpenseNotFoundException
com.spendwise.ui
    SpendWiseFrame, ExpensePanel, ExpenseFormDialog, ExpenseTableModel
    DashboardPanel, MonthlyBarChartPanel, CategoryDonutChartPanel
    BudgetPanel, BudgetLimitTableModel,
    CategoryManagerDialog, CategoryTableModel
com.spendwise.validation
    ExpenseValidator, ValidationException
```

This structure separates responsibilities without introducing unnecessary frameworks, dependency injection containers, or enterprise layers.

## Responsibility overview

### Model

Model classes will represent the application's data and basic invariants:

- `Expense` now represents an occurred expense with an identifier, description, amount, date, category, and notes.
- `Category` is now an immutable category definition with a stable identifier, display name, built-in/custom classification, and active/archived status. Its original built-in constants, identifiers, names, and order remain compatible.
- `MonthlyBudget` now represents one month, an optional overall limit, and configured category limits as immutable two-decimal values.
- Additional income models remain planned for later milestones.

Money will use `BigDecimal`, and dates will use `LocalDate` and `YearMonth`.

### Repository and storage

The repository package now provides a storage-independent expense contract and a CSV implementation. It:

- Keeps file-format knowledge out of the UI.
- Writes the exact `id,description,amount,date,category,notes` header and column order.
- Escapes commas, doubled quotes, Unicode text, and quoted line breaks.
- Uses UTF-8, ISO `LocalDate` text, plain `BigDecimal` text, and stable category identifiers.
- Rejects malformed records and duplicate IDs without returning partial results.
- Writes through a same-directory temporary file and replaces the target only after a complete flush and close.
- Stores budgets separately with the exact `month,scope,category,amount` header, chronological months, and stable category order.
- Stores only custom category definitions in `categories.csv` with the exact `id,name,status` header; built-ins remain defined once in code.
- Refuses to overwrite malformed existing budget data.
- Resolves stable category identifiers through the current catalog and rejects unknown identifiers instead of using a fallback category.

### Service

`ExpenseService` now coordinates expense creation, lookup, replacement-based updates, deletion, combined querying, stable sorting, and summary calculations through the repository abstraction. It reuses model validation and has no CSV, file-path, or Swing knowledge. `ExpenseSummary` provides immutable two-decimal totals, averages, counts, and totals for every category. `ExpenseAnalyticsService` reads one current service snapshot per analysis and produces an immutable `ExpenseAnalyticsSnapshot` for a selected month, previous-month comparison, and an ordered trend without caching or writing data.

`BudgetService` stores or clears monthly plans through `BudgetRepository` and evaluates an existing analytics snapshot without reloading expenses. `BudgetUsage` calculates exact remaining amounts and two-decimal `HALF_UP` percentages, while exact comparisons classify within-limit, near-limit, reached, and over-limit states.

`CategoryService` merges the protected built-in catalog with persisted custom definitions and owns name uniqueness, add, rename, archive, restore, selectable-category, and stable-ID resolution rules. Archived categories remain resolvable for existing expense and budget history but are excluded from new selections.

### UI

The UI package creates programmatic Swing components, translates user actions into service calls, and displays results or validation messages. `SpendWiseFrame` exposes Expenses, Dashboard, and Budgets tabs. The Expenses area also opens the category manager and refreshes expense selectors and filters after mutations. `BudgetLimitTableModel` permits editing active-category limits while keeping archived categories visible when historical spending or a configured limit requires them. Dashboard reports and charts resolve current display names without losing archived data. Income and export screens remain future work.

## CSV persistence plan

Expense, budget, and category CSV persistence use caller-supplied paths rather than a hard-coded developer location. `AppPaths` resolves sibling `expenses.csv`, `budgets.csv`, and `categories.csv` files below `%LOCALAPPDATA%\SpendWiseExpenseTracker\data` on Windows, with the existing `user.home` fallback and standard per-user macOS and Linux locations. Path resolution, startup, construction, viewing, and refresh do not create files. `categories.csv` is created only after the first successful custom-category mutation.

The implemented format uses UTF-8, a required header, stable column order, ISO dates, stable category identifiers, and decimal amounts produced with `BigDecimal.toPlainString()`. `CsvExpenseCodec` owns quoting and parsing rules, including quoted line breaks, LF and CRLF input, and an optional UTF-8 BOM before the header. Loading constructs each `Expense` through existing model validation. Mutations prepare the full snapshot before writing and use same-directory temporary-file replacement.

CSV is appropriate for the course scope because it is inspectable and requires no external database dependency. It is not intended for high-volume or multi-user data.

Budget CSV rows use ISO `YearMonth`, `OVERALL` or `CATEGORY` scope, stable category identifiers, and exact two-decimal limits. Custom category rows use stable IDs, CSV-escaped display names, and `ACTIVE` or `ARCHIVED` status. Existing built-in category values require no migration. Other months survive replacement or deletion, and all repositories use complete same-directory temporary files before target replacement.

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

Testing will combine focused automated checks with repeatable manual GUI testing:

- Plain Java main-based tests with explicit `AssertionError` helpers for model invariants and validation
- Temporary test directories for storage tests so real user data is not changed
- Edge cases for decimal amounts, empty notes, quoted CSV fields, invalid rows, and month boundaries
- Headless Swing checks for table mapping, immutable snapshots, parsing, events, and empty summaries
- Deterministic analytics checks for month boundaries, previous-month change, chronological trends, immutable snapshots, and single-snapshot loading
- Headless `BufferedImage` rendering checks for empty and populated Java2D charts and dashboard refresh safety
- Budget model, CSV corruption, safe-replacement, calculation-boundary, and headless editor/status checks
- Category immutability, built-in compatibility, CSV corruption/replacement, service mutation, archived-history, selector-refresh, and headless manager-state checks
- Isolated path-resolution checks that do not modify environment variables or production data
- Manual checks for navigation, table updates, dialogs, keyboard focus, resizing, and error messages
- A clean Ant build before each milestone is accepted
- Regression checks for previously completed workflows

No testing dependency will be introduced without explicit approval. If the team later wants JUnit, it will be considered separately.

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

## Milestones

1. **Foundation (complete):** establish the Java 21 NetBeans project, repository baseline, documentation, and repeatable build.
2. **Domain model (in progress):** implement model classes, enums, validation rules, and calculation tests. The expense model, category enum, reusable validation, and core tests are complete.
3. **CSV storage (complete for expenses):** implement encoding, loading, safe replacement, corruption handling, and round-trip tests.
4. **Expense service layer (complete):** implement validated expense CRUD operations, combined text/category/date queries, stable sorting, and overall or filtered summaries.
5. **Expense-management UI (complete):** implement the main frame, expense table, add/edit/confirmed-delete workflows, filtering, sorting, refresh, and displayed-result summaries.
6. **Expense analytics dashboard (complete):** add selected-month summaries, previous-month comparison, six-month and category charts, and a read-only monthly report.
7. **Monthly budgets (complete):** persist monthly overall and category limits, calculate exact usage warnings, add the Budgets tab, and integrate status into Dashboard.
8. **Custom category management (complete):** persist stable custom definitions, support add, rename, archive, and restore, and preserve historical expense, analytics, and budget behavior.
9. **Advanced selection:** choose and implement only advanced features that fit the remaining schedule.
10. **Quality pass:** complete regression testing, usability fixes, documentation updates, and demo preparation.

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
- No user-facing backup or import/export workflow
- No exported, printable, or advanced financial reports beyond the implemented on-screen monthly expense report
- No bank, payment-provider, or financial-account integration
- No authentication, shared accounts, or role management
- No income or recurring transactions
- No dark mode or theme switching
- No backup/restore, import/export, printing, or website
- No encryption beyond protections provided by the local operating system
- No mobile or web client
- No claim of professional financial advice
- Advanced features depend on completion and testing of the MVP
