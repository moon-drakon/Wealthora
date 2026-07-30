# SpendWise Expense Tracker

SpendWise Expense Tracker is a Java Swing desktop application for a CSE215 Object-Oriented Programming semester project.

## Team

- Moon
- Nafij
- Monimul

## Current status

The project now includes the core expense model, reusable validation, storage-independent repository abstraction, safe UTF-8 CSV persistence, `ExpenseService` business workflows, and a programmatic Swing expense-management interface. Users can add, edit, delete, search, filter by category or inclusive dates, sort, refresh, and view summaries for the displayed expenses. Application startup wires the CSV repository into the service and main window. Core-model, persistence, service, path-resolution, and GUI-foundation tests are dependency-free.

Feature status in this document will be updated only after the corresponding behavior has been implemented and verified.

## Purpose

SpendWise is intended to help an individual record income and expenses, organize transactions, monitor a budget, and understand personal spending patterns through a straightforward desktop interface.

## Planned core features

- Add, edit, and delete income and expense transactions
- Assign dates, categories, amounts, and notes to transactions
- Manage practical spending categories
- Set and monitor monthly budgets
- View balances and monthly income and expense summaries
- Save and reload application data using local CSV files
- Validate input and present clear error messages

## Planned advanced features

These features are candidates after the core workflow is complete:

- Search and filter across future income and expense records
- Category-based and monthly spending visualizations
- Budget warning indicators
- Recurring transaction templates
- Exportable filtered summaries
- Local data backup and restore

## Technology stack

- Java 21
- Java Swing for the planned graphical interface
- Java standard libraries, including `java.time`, `java.math`, and `java.nio`
- Apache Ant
- Apache NetBeans project structure
- UTF-8 CSV files for local expense persistence

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

## Application data location

On Windows, expense data is stored at:

```text
%LOCALAPPDATA%\SpendWiseExpenseTracker\data\expenses.csv
```

If `LOCALAPPDATA` is unavailable, SpendWise uses the equivalent location below `user.home\AppData\Local`. macOS uses `~/Library/Application Support`, while Linux and other Unix-like systems use `XDG_DATA_HOME` or the `~/.local/share` fallback.

Resolving the path and starting the application are read-only operations. The CSV file and its parent directory are created only after the first successful expense mutation. Repository writes continue to use safe same-directory temporary-file replacement.

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
|       |   `-- Expense.java
|       |-- repository/
|       |   |-- CsvExpenseCodec.java
|       |   |-- CsvExpenseRepository.java
|       |   |-- ExpenseRepository.java
|       |   `-- RepositoryException.java
|       |-- service/
|       |   |-- ExpenseNotFoundException.java
|       |   |-- ExpenseService.java
|       |   |-- ExpenseSortOrder.java
|       |   `-- ExpenseSummary.java
|       |-- ui/
|       |   |-- ExpenseFormDialog.java
|       |   |-- ExpensePanel.java
|       |   |-- ExpenseTableModel.java
|       |   `-- SpendWiseFrame.java
|       `-- validation/
|           |-- ExpenseValidator.java
|           `-- ValidationException.java
|-- test/
|   `-- com/spendwise/
|       |-- config/AppPathsTest.java
|       |-- model/ExpenseTest.java
|       |-- repository/CsvExpenseRepositoryTest.java
|       |-- service/ExpenseServiceTest.java
|       `-- ui/SwingFoundationTest.java
|-- docs/
|   `-- PROJECT_PLAN.md
|-- .gitattributes
|-- .gitignore
|-- AGENTS.md
`-- README.md
```

The generated `build/` and `dist/` directories and the machine-specific `nbproject/private/` directory are intentionally excluded from version control.

The CSV repository supports commas, doubled quotes, Unicode, and quoted line breaks. Mutations write a complete temporary file in the destination directory and replace the previous file only after the temporary content is closed and flushed. The service layer remains independent of CSV details and provides validated CRUD, combined description/notes/category text matching, category and inclusive date filtering, stable sorting, and exact `BigDecimal` summaries.

Charts, budgets, income, authentication, advanced reports, backup UI, and import/export UI are not implemented. Multi-process file locking also remains outside the current scope.
