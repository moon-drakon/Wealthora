# OOP mapping

## Official requirement traceability

| Official requirement | Production class/interface | Relevant member | Swing/runtime use | Automated evidence |
|---|---|---|---|---|
| Abstract transaction model | `src/com/spendwise/model/Transaction.java` | `abstract class Transaction`; private `amount` and `date`; final getters | Transaction rows shown by `TransactionsPanel` originate from the production model | `FinanceModelTest.transactionPolymorphism`, `SwingFoundationTest` |
| Income inheritance and positive impact | `src/com/spendwise/model/Income.java` | `extends Transaction`; overridden `calculateImpact()` returns the positive amount | `FinanceService` aggregates income through `Transaction` references and the dashboard/table display the result | `IncomeTest`, `FinanceModelTest.transactionPolymorphism`, `FinanceServiceTest` |
| Expense inheritance and negative impact | `src/com/spendwise/model/Expense.java` | `extends Transaction`; overridden `calculateImpact()` returns the negated amount | `FinanceService` aggregates expense through `Transaction` references and the dashboard/table display the result | `ExpenseTest`, `FinanceModelTest.transactionPolymorphism`, `FinanceServiceTest` |
| Runtime polymorphism | `src/com/spendwise/service/FinanceService.java` | Calls `transaction.calculateImpact()` while iterating parent-type references | One calculation path updates balances for both income and expense without a subtype decision chain | `FinanceModelTest.transactionPolymorphism`, `FinanceServiceTest` |
| Encapsulation of amount/date | `src/com/spendwise/model/Transaction.java` | Private immutable fields, constructor validation, final accessors | UI receives validated model values rather than mutating fields directly | `FinanceModelTest`, `ExpenseTest` |
| Functional export interface | `src/com/spendwise/service/Exportable.java`; `ExportService.java` | `generateCSV()`; `exportTransactions()` delegates to it | Data-management GUI invokes the production transaction export action | `ExportServiceTest.transactionExport` |
| Swing transaction table | `src/com/spendwise/ui/TransactionsPanel.java`; `TransactionTableModel.java`; `StyledTable.java` | `TransactionTableModel` installed on a `StyledTable`/`JTable`; rows map `Income`, `Expense`, and `Transfer` | Transactions screen displays the real ledger and supports selection/edit/delete actions | `SwingFoundationTest` plus finance GUI integration tests in the quality chain |

Every listed requirement is connected to the application workflow; none is an
isolated demonstration-only class.

| Concept | Wealthora implementation | Main class/file | Presentation evidence |
|---|---|---|---|
| Abstraction | Shared transaction state and the financial-impact contract live in an abstract class. | `model/Transaction.java` | Point out its private fields and abstract behavior. |
| Inheritance | Income and expense reuse the common transaction model. | `model/Income.java`, `model/Expense.java` | Show `extends Transaction`. |
| Polymorphism | Each subtype overrides `calculateImpact()`; totals call it through `Transaction` references. | `model/Income.java`, `model/Expense.java`, `service/FinanceService.java` | Add income and expense, then show the changed totals. |
| Encapsulation | Private state is changed through constructors, validators, and service methods. | `validation/FinanceValidator.java`, `validation/ExpenseValidator.java` | Try a zero/negative amount and show the validation message. |
| Interfaces | Repositories, `Exportable`, `SpeechRecognitionProvider`, and `EmailVerificationGateway` separate policy from implementation. | `repository/*Repository.java`, `service/Exportable.java`, `voice/SpeechRecognitionProvider.java`, `auth/otp/EmailVerificationGateway.java` | Compare an interface with its CSV, Windows, or HTTP implementation. |
| Composition | UI panels compose finance/auth services; `LocalDesktopAuthService` composes local repositories and credential services. | `app/SpendWiseApplication.java`, `auth/local/LocalDesktopAuthService.java` | Trace a button event into a service rather than a CSV file. |
| Collections | Services return typed lists and calculate balances in typed maps. | `service/FinanceService.java` | Show transaction aggregation and account balance maps. |
| Exception handling | Validation, repository, and relay failures become clear UI messages without partial account creation. | `repository/RepositoryException.java`, `auth/AuthenticationException.java`, `auth/ui/VerificationPanel.java` | Enter invalid input or use an unavailable relay. |
| File persistence | Per-user CSV repositories write through safe same-directory replacement. | `repository/CsvFileSupport.java`, `config/AppPaths.java`, `service/SafeFileSupport.java` | Save, restart, and show the same rows. |
| Swing GUI | Typed table models, listeners, dialogs, and Java2D charts implement the desktop. | `ui/TransactionsPanel.java`, `ui/TransactionTableModel.java`, `ui/OverviewPanel.java` | Select a row and use Edit/Delete. |

## Viva sequence

1. Open `Transaction`, `Income`, and `Expense`; explain common encapsulated state
   and subtype-specific impact.
2. Open `FinanceService.calculateBalances()` and show calls through transaction
   references without a subtype decision chain.
3. Add an income and expense, then show the table and dashboard changes.
4. Trace a save action from Swing, through validation/service logic, to a CSV
   repository.
5. Open `EmailVerificationGateway` and compare it with
   `HttpEmailVerificationGateway`; explain why the rest of authentication does
   not depend on HTTP details.
6. Close and reopen Wealthora to show local persistence, then export a CSV.

## Quick answers

**Where is abstraction used?** `Transaction` defines common behavior while
repository, export, voice, and OTP interfaces hide implementation details.

**Where is inheritance used?** `Income` and `Expense` extend `Transaction`.

**How does polymorphism work?** Each subclass supplies `calculateImpact()`, and
finance calculations call that method through the parent type.

**How is encapsulation used?** Fields remain private and validated operations
protect objects and stored data from invalid state.

**Why use interfaces?** They allow a consumer to depend on a contract, so CSV,
offline speech, and HTTP OTP implementations can change independently.

**How is data persisted?** Each verified user has a separate CSV workspace
under `<project-root>/data/users/<id>`; safe replacement protects updates.
