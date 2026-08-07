# Wealthora

Wealthora is a Java Swing personal finance manager developed for our CSE215
Object-Oriented Programming project. This `feature/shared-online-core` branch
connects the desktop application over HTTPS to the Wealthora Spring Boot API
and a central Neon PostgreSQL database.

The submitted teacher release remains frozen at `cse215-final-v1.1.1`. That
tag is the complete offline version and is not changed by this branch.

## Features

- Dashboard with current balance, monthly income, expenses, and cash flow
- Income, expense, transfer, account, and category management
- Add, edit, and delete actions for transactions
- Search and filters with a Swing `JTable` transaction view
- Budgets, recurring entries, reports, goals, loans, and debts
- Central account registration and sign-in with server-side password hashing
- Default USER registration with protected USER / ADMIN / OWNER roles
- OWNER Admin Console user list and USER ↔ ADMIN role management
- Windows offline voice quick entry with review before saving
- Server-enforced private finance records for each user
- Cross-device persistence through one HTTPS API and central database
- Combined transaction CSV export plus dedicated finance exports
- Local backup, restore, and CSV import tools
- Light and dark themes, currency selection, and category settings
- Central PostgreSQL persistence across application restarts and devices

## Object-Oriented Programming

- **Abstraction:** the abstract `Transaction` class defines shared transaction
  state and behavior.
- **Inheritance:** `Income` and `Expense` extend `Transaction`.
- **Polymorphism:** each transaction type overrides `calculateImpact()`, and
  finance totals call it through `Transaction` references.
- **Encapsulation:** financial fields are private and are accessed or changed
  through validated class methods.
- **Interface:** `ExportService` implements `Exportable.generateCSV()` for the
  transaction export available from the GUI.
- **Swing GUI:** typed table models, event listeners, dialogs, and `JTable`
  provide the desktop interface.

See [docs/OOP_MAPPING.md](docs/OOP_MAPPING.md) for the implementation map and
viva quick reference.

## Technologies

- Java 25
- Java Swing
- Apache Ant with Apache NetBeans project files
- FlatLaf 3.7.2
- Spring Boot API with Flyway migrations
- Neon PostgreSQL

## Requirements

To run this shared-online branch:

- Java 25
- Internet access to the configured Wealthora HTTPS API
- The generated `dist\lib` directory beside `dist\Wealthora.jar`

To build from source:

- Java 25
- Apache Ant

Friend devices do not need Neon credentials or any database configuration.
The desktop JAR contains only the public API URL; database credentials and the
token pepper remain on the server. For the no-server teacher demo, use the
frozen `cse215-final-v1.1.1` release instead.

## Run Wealthora

From the repository root after building, or from the prepared submission
folder:

```powershell
java -jar .\dist\Wealthora.jar
```

Windows users can also run the checked launcher:

```powershell
.\scripts\Run-Wealthora.ps1
```

For a friend device, download the successful **Desktop CI** artifact for this
branch, extract it, keep `lib` next to `Wealthora.jar`, and run from that
folder:

```powershell
java -jar .\Wealthora.jar
```

Choose **Create Account**, register with an exact `@northsouth.edu` address,
then sign in. New accounts always receive the USER role. Passwords must contain
8–128 characters with at least one English letter and one number. An OWNER can
open **Profile → Admin Console** to view central users and grant or revoke the
ADMIN role.

Voice Quick Entry is available from **Entry → Voice Quick Entry** or
`Ctrl+Shift+V`. Audio remains in memory only, recognition runs locally on
Windows, and every draft must be reviewed before it can be saved.

## Build and Test

Build the desktop JAR:

```powershell
ant clean jar
```

Run the complete desktop quality suite:

```powershell
ant clean test-quality jar
```

On Windows, verify the real offline recognizer without a microphone or network:

```powershell
ant test-windows-offline-speech
```

The application is created at `dist\Wealthora.jar`; its required libraries are
copied to `dist\lib`. Apache NetBeans users can also use **Clean and Build**.

## Project Structure

```text
src/        Desktop application source code
test/       Desktop test programs
lib/        Desktop libraries and license notices
nbproject/  NetBeans and Ant project metadata
docs/       OOP mapping, architecture, and demonstration notes
scripts/    Desktop launcher and development utilities
server/     Spring Boot HTTPS API and Flyway-managed PostgreSQL backend
```

## Documentation

- [OOP concept mapping and viva reference](docs/OOP_MAPPING.md)
- [Teacher demo guide](docs/DEMO_GUIDE.md)

## Shared-online scope

See [docs/SHARED_ONLINE_CORE.md](docs/SHARED_ONLINE_CORE.md) for architecture,
deployment, privacy, validation, and friend-device instructions. Web, Google
OAuth, SMTP recovery, and online backup are deliberately deferred. They remain
unnecessary for the frozen teacher release.
