# OOP Mapping

| Concept | Wealthora implementation | Main class/file | Demonstration |
|---|---|---|---|
| Abstraction | Shared transaction state and the impact contract are defined once in an abstract class. | `model/Transaction.java` | Open the class and point out its private fields and abstract methods. |
| Inheritance | Both transaction types reuse the common model. | `model/Expense.java`, `model/Income.java` | Show `extends Transaction` in both class declarations. |
| Polymorphism | Income returns a positive impact and expense returns a negative impact. Finance totals call the overridden method through `Transaction` references. | `model/Expense.java`, `model/Income.java`, `service/FinanceService.java`, `ui/OverviewPanel.java` | Add one income and one expense, then show the balance and dashboard totals. |
| Encapsulation | Financial fields are private. Constructors and validators reject invalid amounts, dates, categories, notes, and accounts. | `model/Transaction.java`, `validation/FinanceValidator.java`, `validation/ExpenseValidator.java` | Try to save a zero or negative amount and show the validation message. |
| Interface | `ExportService` implements `Exportable.generateCSV()` and writes the generated transaction CSV from the GUI. | `service/Exportable.java`, `service/ExportService.java` | Use **Data → Export → Transactions** and open the saved CSV. |
| Swing GUI | The transaction screen uses `JTable` through a typed table model and Swing event listeners. | `ui/TransactionsPanel.java`, `ui/TransactionTableModel.java`, `ui/component/StyledTable.java` | Open Transactions, select a row, and use Edit or Delete. |
| Collections | Services use typed `List<Transaction>` collections and account balances use `Map<Account, BigDecimal>`. | `service/FinanceService.java` | Show `getAllTransactions()` and the balance map calculation. |
| Exception handling | Validation and repository failures are caught and shown through clear Swing dialogs. | `ui/ExpenseFormDialog.java`, `ui/IncomeFormDialog.java`, `repository/RepositoryException.java` | Enter an invalid date or amount and show that the dialog stays open. |
| File persistence | Per-user repositories read and write local CSV files using safe file replacement. | `repository/CsvExpenseRepository.java`, `repository/CsvIncomeRepository.java`, `repository/CsvFileSupport.java`, `config/AppPaths.java` | Add data, close the app, reopen it, and show the same rows. |

## How to demonstrate this during viva

1. Open `Transaction.java`, `Income.java`, and `Expense.java`. Explain that the
   common data is encapsulated in the abstract parent while each subclass
   supplies its own financial impact.
2. Open `FinanceService.calculateBalances()` and show that it loops over
   `Transaction` objects and calls `calculateImpact()` without an
   `instanceof` chain.
3. Run Wealthora and add an income and an expense. Show the updated dashboard
   totals and the rows in the Transactions `JTable`.
4. Edit one row, delete another with confirmation, then export the combined
   transaction CSV from the Data menu.
5. Close and reopen the application to demonstrate local persistence. Briefly
   show `Exportable` and one CSV repository to connect the GUI behavior to the
   interface, collections, exception handling, and file storage.

## Viva Quick Reference

**Where is abstraction used?**

`Transaction` is abstract and contains the fields and behavior shared by
income and expense records.

**Where is inheritance used?**

`Income` and `Expense` both extend `Transaction` and reuse its common state.

**How does polymorphism work?**

Each subclass overrides `calculateImpact()`. Balance, dashboard, and reporting
code calls that method through `Transaction` references, producing a positive
income impact or a negative expense impact.

**How is encapsulation used?**

Financial fields are private. Constructors, validators, getters, and controlled
update methods protect the model from invalid state.

**Which interface is used?**

`ExportService` implements `Exportable`; its `generateCSV()` result is written
by the **Data → Export → Transactions** action.

**Why is `JTable` used?**

It gives the transaction screen a structured, sortable view backed by a typed
table model and supports row selection for editing and deletion.

**How is data persisted?**

Per-user CSV repositories save records below the local application-data
directory. They reload those records when the same user signs in again.
