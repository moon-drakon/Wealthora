# Wealthora

Wealthora is a Java Swing personal finance manager developed for our CSE215
Object-Oriented Programming project. It brings income, expenses, accounts,
budgets, and other everyday finance records together in one offline desktop
application, with each user's data stored locally on the computer.

## Features

- Dashboard with current balance, monthly income, expenses, and cash flow
- Income, expense, transfer, account, and category management
- Add, edit, and delete actions for transactions
- Search and filters with a Swing `JTable` transaction view
- Budgets, recurring entries, reports, goals, loans, and debts
- Local owner setup and sign-in with BCrypt password hashing
- Offline user registration with a separate workspace for every account
- Protected recovery-question password reset with a non-secret hint
- OWNER/ADMIN user management and assisted local password reset
- Windows offline voice quick entry with review before saving
- Separate local finance records for each user
- Combined transaction CSV export plus dedicated finance exports
- Local backup, restore, and CSV import tools
- Light and dark themes, currency selection, and category settings
- Local CSV persistence across application restarts

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
- Local CSV file persistence

## Requirements

To run the prebuilt teacher release:

- Java 25
- The release `lib` directory beside `Wealthora.jar`

To build from source:

- Java 25
- Apache Ant

The offline demo does not require a server, external database, internet
connection, Docker, environment variables, or a cloud account. Voice capture
uses the speech recognizer installed in Windows; English (`en-US`) is used for
English, Banglish, and automatic input. Bangla voice needs the optional
`bn-BD` Windows speech language. Manual voice-command parsing remains available.

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

For a fresh GitHub download, use the **Complete offline bundle** from the
latest release, extract it, and run:

```powershell
java -jar .\Wealthora.jar
```

On the first launch, create the local OWNER account with an NSU email address,
password, recovery question, and a hint that does not reveal the answer.
Passwords must contain 8–128 characters with at least one English letter and
one number.

After OWNER setup, the sign-in screen provides:

- **Create Account** for a new local NSU user and private finance workspace.
- **Forgot Password?** for protected-answer recovery.
- OWNER/ADMIN-assisted reset under **Profile → Admin Console → Users**.

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
server/     Experimental future server work; not used by the offline demo
```

## Documentation

- [OOP concept mapping and viva reference](docs/OOP_MAPPING.md)
- [Teacher demo guide](docs/DEMO_GUIDE.md)

## Future Scope

Cloud synchronization, Google Sign-In, online backup, the experimental server,
and a web client are outside the current CSE215 desktop release. None of them
is needed for the teacher demo.
