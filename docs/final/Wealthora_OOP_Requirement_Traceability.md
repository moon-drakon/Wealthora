# Wealthora Official OOP Requirement Traceability

All paths are relative to the committed `SpendWiseExpenseTracker` source
project. The mapping was checked against production code and the Ant quality
chain; no disconnected demonstration classes were added.

| Requirement | Production class/interface | Relevant member | Swing feature using it | Automated test |
|---|---|---|---|---|
| Abstract `Transaction` | `src/com/spendwise/model/Transaction.java` | Abstract class; private `amount` and `date`; final getters | Transactions screen rows derive from production transaction models | `FinanceModelTest.transactionPolymorphism`; `SwingFoundationTest` |
| `Income extends Transaction` | `src/com/spendwise/model/Income.java` | Overridden `calculateImpact()` returns a positive amount | Dashboard/balance calculation and transaction table | `IncomeTest`; `FinanceModelTest`; `FinanceServiceTest` |
| `Expense extends Transaction` | `src/com/spendwise/model/Expense.java` | Overridden `calculateImpact()` returns a negative amount | Dashboard/balance calculation and transaction table | `ExpenseTest`; `FinanceModelTest`; `FinanceServiceTest` |
| Runtime polymorphism | `src/com/spendwise/service/FinanceService.java` | Iterates `Transaction` and calls `calculateImpact()` | Shared balance workflow for income and expense | `FinanceModelTest.transactionPolymorphism`; `FinanceServiceTest` |
| Amount/date encapsulation | `src/com/spendwise/model/Transaction.java` | Private immutable state and safe final accessors | GUI reads validated values; it does not mutate fields | `FinanceModelTest`; `ExpenseTest` |
| `Exportable.generateCSV()` | `src/com/spendwise/service/Exportable.java`; `ExportService.java` | Implemented `generateCSV()`; `exportTransactions()` delegates to it | Data-management export action | `ExportServiceTest.transactionExport` |
| Swing `JTable` transaction display | `src/com/spendwise/ui/TransactionsPanel.java`; `TransactionTableModel.java`; `StyledTable.java` | Production table model installed on `StyledTable`, a `JTable` | Transactions screen displays income, expense, and transfer rows | `SwingFoundationTest` and finance GUI quality-chain tests |

Result: abstraction, inheritance, polymorphism, encapsulation, interface usage,
and Swing table integration are implemented and exercised by the real
application workflow.
