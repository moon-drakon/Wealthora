# SpendWise Expense Tracker

SpendWise Expense Tracker is a planned Java Swing desktop application for a CSE215 Object-Oriented Programming semester project.

## Team

- Moon
- Nafij
- Monimul

## Current status

The project foundation has been initialized as a Java 21 Apache NetBeans project. The core expense model and validation are implemented together with a storage-independent `ExpenseRepository` abstraction and safe UTF-8 CSV persistence. Dependency-free core-model and persistence tests are available. The application entry point remains empty, and the service layer and Swing interface have not been implemented yet.

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

- Search and filter transactions by date, type, category, or text
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

The generated JAR is placed in `dist/`.

## Build from the command line

Run the following from the project root when Ant is available on `PATH`:

```powershell
ant clean jar
```

On the verified development machine, use this fallback when `ant` is unavailable by name:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat clean jar
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
|       |-- model/
|       |   |-- Category.java
|       |   `-- Expense.java
|       |-- repository/
|       |   |-- CsvExpenseCodec.java
|       |   |-- CsvExpenseRepository.java
|       |   |-- ExpenseRepository.java
|       |   `-- RepositoryException.java
|       `-- validation/
|           |-- ExpenseValidator.java
|           `-- ValidationException.java
|-- test/
|   `-- com/spendwise/
|       |-- model/ExpenseTest.java
|       `-- repository/CsvExpenseRepositoryTest.java
|-- docs/
|   `-- PROJECT_PLAN.md
|-- .gitattributes
|-- .gitignore
|-- AGENTS.md
`-- README.md
```

The generated `build/` and `dist/` directories and the machine-specific `nbproject/private/` directory are intentionally excluded from version control.

The CSV repository supports commas, doubled quotes, Unicode, and quoted line breaks. Mutations write a complete temporary file in the destination directory and replace the previous file only after the temporary content is closed and flushed. Multi-process file locking, user-facing backup, and import/export workflows are outside the current scope.
