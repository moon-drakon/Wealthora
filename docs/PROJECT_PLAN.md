# SpendWise Expense Tracker Project Plan

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
- Monthly and category-based visual summaries drawn with Swing
- Warning indicators when spending approaches or exceeds a budget
- Recurring transaction templates that create user-confirmed entries
- Export of the current filtered summary to CSV
- Local backup and restore actions

Advanced items are proposals, not implemented features or fixed delivery promises.

## Proposed Swing screens

### Main frame

`MainFrame` will own the application window, navigation, and shared status area. It will switch between panels without creating multiple independent application windows.

### Dashboard

`DashboardPanel` will present the selected month's income, expenses, balance, budget progress, and concise category summaries.

### Transactions

`TransactionsPanel` will display transactions in a table and provide add, edit, delete, search, and filter actions. `TransactionDialog` will collect and validate transaction input.

### Budgets

`BudgetPanel` will allow the user to set a monthly spending limit and review current progress.

### Categories

`CategoryPanel` will display available categories and support safe creation, renaming, and deletion rules.

## Proposed Java package architecture

```text
com.spendwise.app
    SpendWiseApplication
com.spendwise.model
    Transaction, TransactionType, Category, Budget
com.spendwise.storage
    CsvDataStore, CsvCodec, DataStoreException
com.spendwise.service
    TransactionService, BudgetService, SummaryService
com.spendwise.ui
    MainFrame, DashboardPanel, TransactionsPanel, BudgetPanel,
    CategoryPanel, TransactionDialog
com.spendwise.validation
    InputValidator, ValidationException
```

This structure separates responsibilities without introducing unnecessary frameworks, dependency injection containers, or enterprise layers.

## Responsibility overview

### Model

Model classes will represent the application's data and basic invariants:

- `Transaction` will hold an identifier, date, type, amount, category, and note.
- `TransactionType` will distinguish income from expense.
- `Category` will represent a named transaction category.
- `Budget` will represent a spending limit for a particular month.

Money will use `BigDecimal`, and dates will use `LocalDate` and `YearMonth`.

### Storage

The storage package will translate model data to and from CSV files. It will:

- Keep file-format knowledge out of the UI.
- Write a header and a documented field order.
- Escape commas, quotes, and line breaks correctly.
- Use UTF-8 consistently.
- Report malformed rows with useful context.
- Prefer writing through a temporary file and replacing the target only after a successful save.

### Service

Service classes will coordinate use cases and calculations. They will validate operations, maintain identifiers, apply filters, calculate summaries, and call the storage layer. Swing components will not read or write CSV directly.

### UI

UI classes will create programmatic Swing components, translate user actions into service calls, and display results or validation messages. Swing startup and UI updates will run on the Event Dispatch Thread.

## CSV persistence plan

Separate CSV files are planned for transactions, categories, and budgets. Files will be stored in one application data directory selected during implementation rather than hard-coded to a developer-specific path.

The format will use UTF-8, a header row, stable column order, and explicit conversion for dates, transaction types, and decimal amounts. A small `CsvCodec` will handle quoting rules so parsing logic is not duplicated. Loading will validate each row before adding it to the active data set. Save operations will avoid leaving a partially written primary file after an error.

CSV is appropriate for the course scope because it is inspectable and requires no external database dependency. It is not intended for high-volume or multi-user data.

## Validation and error handling

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

- Plain Java assertion-based tests for model invariants, calculations, validation, filtering, and CSV round trips
- Temporary test directories for storage tests so real user data is not changed
- Edge cases for decimal amounts, empty notes, quoted CSV fields, invalid rows, and month boundaries
- Manual checks for navigation, table updates, dialogs, keyboard focus, resizing, and error messages
- A clean Ant build before each milestone is accepted
- Regression checks for previously completed workflows

No testing dependency will be introduced without explicit approval. If the team later wants JUnit, it will be considered separately.

## Milestones

1. **Foundation:** establish the Java 21 NetBeans project, repository baseline, documentation, and repeatable build.
2. **Domain model:** implement model classes, enums, validation rules, and calculation tests.
3. **CSV storage:** implement encoding, loading, saving, error handling, and round-trip tests.
4. **Service layer:** implement transaction operations, budget calculations, summaries, and filters.
5. **Transaction UI:** implement the main frame, transaction table, and add/edit/delete workflow.
6. **Dashboard and supporting UI:** add summaries, budget progress, and category management.
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
- No bank, payment-provider, or financial-account integration
- No authentication, shared accounts, or role management
- No encryption beyond protections provided by the local operating system
- No mobile or web client
- No claim of professional financial advice
- Advanced features depend on completion and testing of the MVP
