# Wealthora

Wealthora is an offline personal expense tracker built as a CSE215 Java OOP
semester project. It uses a programmatic Swing interface and keeps each local
user's finance records on the computer.

## Features

- Local owner setup and sign-in with BCrypt password hashing
- Dashboard totals for balance, income, expenses, and cash flow
- Income, expense, transfer, account, and category management
- Searchable transaction `JTable` with edit and delete actions
- Budgets, recurring entries, reports, goals, debts, and settings
- CSV export, data backup, restore, and local file persistence
- Light and dark themes

## OOP Concepts

- **Abstraction:** `Transaction` holds the state and behavior shared by money
  entering or leaving an account.
- **Inheritance:** `Income` and `Expense` extend `Transaction`.
- **Polymorphism:** both classes override `calculateImpact()`, which is used by
  balance and dashboard calculations.
- **Encapsulation:** model fields are private and are changed only through
  validated constructors or controlled methods.
- **Interface:** `ExportService` implements `Exportable` and its
  `generateCSV()` method powers the transaction export.

See [docs/OOP_MAPPING.md](docs/OOP_MAPPING.md) for the code mapping used in the
project demonstration.

## Technology

- Java 25
- Java Swing
- FlatLaf
- Apache Ant and NetBeans project files
- Local CSV persistence

## Build

```powershell
ant clean jar
```

If Ant is not on `PATH`, open the project in Apache NetBeans and use Clean and
Build.

## Run

```powershell
java -jar .\dist\Wealthora.jar
```

Windows users can also run:

```powershell
.\scripts\Run-Wealthora.ps1
```

On the first launch, create the local owner account with an NSU email address.
The password must be 8–128 characters and contain at least one English letter
and one number. No server or environment configuration is required.

## Project Structure

```text
src/        Application source code
test/       Local desktop tests
lib/        Desktop libraries and license notices
nbproject/  NetBeans and Ant project metadata
docs/       OOP mapping and demonstration notes
scripts/    Desktop launcher and future development helpers
server/     Experimental server module; not required by the desktop build
```

## Documentation

- [OOP concept mapping](docs/OOP_MAPPING.md)
- [Teacher demo guide](docs/DEMO_GUIDE.md)

## Future Work

Cloud synchronization, the Spring Boot deployment, Google Sign-In, online
backup, voice recognition, and a web application are intentionally deferred.
They are not required to build or run the offline desktop release.
