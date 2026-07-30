# SpendWise Expense Tracker Project Plan

## Current implementation status

The project currently includes the core expense model and validation, a storage-independent `ExpenseRepository`, safe UTF-8 CSV persistence, an `ExpenseService`, and a programmatic Swing application with Expenses and Dashboard tabs. The expense workspace supports add, edit, confirmed delete, combined searching/filtering, stable sorting, refresh, and displayed-result summaries. The dashboard adds selected-month analytics, previous-month comparison, a chronological six-month trend, category distribution, and a read-only monthly report. Startup wiring and cross-platform per-user data-path resolution are implemented. The 23 model tests, 35 persistence tests, 60 service tests, 12 path tests, 25 GUI-foundation tests, 48 analytics-service tests, and 43 dashboard-foundation tests are dependency-free.

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

`SpendWiseFrame` now owns the application window and displays the expense-management panel. Future screens can be added without moving repository construction or business rules into the frame.

### Dashboard

`DashboardPanel` now presents selected-month expense count, total, average, previous-month total, and signed change. Its Overview tab contains Java2D six-month and category-distribution charts, while its Monthly Report tab contains read-only category and expense tables. Empty months remain visible as `0.00`. Income, balances, and budget progress remain outside the implemented dashboard scope.

### Expenses

`ExpensePanel` now displays service-supplied expenses, filtered summaries, search and filter controls, sorting, refresh, and selected-row actions. `ExpenseFormDialog` supports add and edit input while retaining service and model validation as the authoritative rules.

### Budgets

`BudgetPanel` will allow the user to set a monthly spending limit and review current progress.

### Categories

`CategoryPanel` will display available categories and support safe creation, renaming, and deletion rules.

## Proposed Java package architecture

```text
com.spendwise.app
    SpendWiseApplication
com.spendwise.config
    AppPaths
com.spendwise.model
    Expense, Category
    Additional income and budget models (planned)
com.spendwise.repository
    ExpenseRepository, CsvExpenseRepository, CsvExpenseCodec,
    RepositoryException
com.spendwise.service
    ExpenseAnalyticsService, ExpenseAnalyticsSnapshot,
    ExpenseService, ExpenseSummary, ExpenseSortOrder,
    ExpenseNotFoundException
    BudgetService (planned)
com.spendwise.ui
    SpendWiseFrame, ExpensePanel, ExpenseFormDialog, ExpenseTableModel
    DashboardPanel, MonthlyBarChartPanel, CategoryDonutChartPanel
    BudgetPanel, CategoryPanel (planned)
com.spendwise.validation
    ExpenseValidator, ValidationException
```

This structure separates responsibilities without introducing unnecessary frameworks, dependency injection containers, or enterprise layers.

## Responsibility overview

### Model

Model classes will represent the application's data and basic invariants:

- `Expense` now represents an occurred expense with an identifier, description, amount, date, category, and notes.
- `Category` now provides the supported expense categories and their readable display names.
- Additional income and budget models remain planned for later milestones.

Money will use `BigDecimal`, and dates will use `LocalDate` and `YearMonth`.

### Repository and storage

The repository package now provides a storage-independent expense contract and a CSV implementation. It:

- Keeps file-format knowledge out of the UI.
- Writes the exact `id,description,amount,date,category,notes` header and column order.
- Escapes commas, doubled quotes, Unicode text, and quoted line breaks.
- Uses UTF-8, ISO `LocalDate` text, plain `BigDecimal` text, and enum constant names.
- Rejects malformed records and duplicate IDs without returning partial results.
- Writes through a same-directory temporary file and replaces the target only after a complete flush and close.

### Service

`ExpenseService` now coordinates expense creation, lookup, replacement-based updates, deletion, combined querying, stable sorting, and summary calculations through the repository abstraction. It reuses model validation and has no CSV, file-path, or Swing knowledge. `ExpenseSummary` provides immutable two-decimal totals, averages, counts, and totals for every category. `ExpenseAnalyticsService` reads one current service snapshot per analysis and produces an immutable `ExpenseAnalyticsSnapshot` for a selected month, previous-month comparison, and an ordered trend without caching or writing data.

### UI

The UI package now creates programmatic Swing components, translates user actions into service calls, and displays results or validation messages. `ExpenseTableModel` keeps identifiers available for CRUD selection while showing only date, description, category, amount, and notes; the same safe non-editable model is reused in the monthly report. `SpendWiseFrame` retains the existing expense workspace and adds a Dashboard tab that refreshes when selected. `MonthlyBarChartPanel` and `CategoryDonutChartPanel` render with standard Java2D and accept only defensive data snapshots. Swing startup and UI updates run on the Event Dispatch Thread. Budget, income, category-management, and export screens remain future work.

## CSV persistence plan

Expense CSV persistence is implemented for a caller-supplied path rather than a hard-coded developer location. `AppPaths` resolves `%LOCALAPPDATA%\SpendWiseExpenseTracker\data\expenses.csv` on Windows, with a `user.home` fallback, and supports standard per-user macOS and Linux locations. Path resolution and read-only startup do not create files; the repository creates the parent directory and CSV only after the first successful mutation. Additional storage for future income, category, and budget models remains planned.

The implemented format uses UTF-8, a required header, stable column order, ISO dates, enum constant names, and decimal amounts produced with `BigDecimal.toPlainString()`. `CsvExpenseCodec` owns quoting and parsing rules, including quoted line breaks, LF and CRLF input, and an optional UTF-8 BOM before the header. Loading constructs each `Expense` through existing model validation. Mutations prepare the full snapshot before writing and use same-directory temporary-file replacement.

CSV is appropriate for the course scope because it is inspectable and requires no external database dependency. It is not intended for high-volume or multi-user data.

## Validation and error handling

`ExpenseValidator` now centralizes normalization and validation for expense IDs, descriptions, amounts, dates, categories, and notes. It rejects invalid values before an `Expense` is created or updated, and `Expense.updateDetails(...)` validates the complete proposed state before changing any field.

- Require a transaction type, date, category, and amount.
- Require amounts to be positive and within a practical numeric range.
- Parse dates through `java.time` rather than manual string splitting.
- Prevent blank or duplicate category names after trimming.
- Prevent invalid budget values.
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

## Milestones

1. **Foundation (complete):** establish the Java 21 NetBeans project, repository baseline, documentation, and repeatable build.
2. **Domain model (in progress):** implement model classes, enums, validation rules, and calculation tests. The expense model, category enum, reusable validation, and core tests are complete.
3. **CSV storage (complete for expenses):** implement encoding, loading, safe replacement, corruption handling, and round-trip tests.
4. **Expense service layer (complete):** implement validated expense CRUD operations, combined text/category/date queries, stable sorting, and overall or filtered summaries. Budget calculations remain planned.
5. **Expense-management UI (complete):** implement the main frame, expense table, add/edit/confirmed-delete workflows, filtering, sorting, refresh, and displayed-result summaries.
6. **Expense analytics dashboard (complete):** add selected-month summaries, previous-month comparison, six-month and category charts, and a read-only monthly report. Budget progress and category management remain planned.
7. **Advanced selection:** choose and implement only advanced features that fit the remaining schedule.
8. **Quality pass:** complete regression testing, usability fixes, documentation updates, and demo preparation.

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
- No budgets, income, recurring transactions, or category spending limits
- No dark mode or theme switching
- No backup/restore, import/export, printing, or website
- No encryption beyond protections provided by the local operating system
- No mobile or web client
- No claim of professional financial advice
- Advanced features depend on completion and testing of the MVP
