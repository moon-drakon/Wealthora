# Teacher Demo Guide

## Requirements

- Windows
- Java 25

No server, database, Docker installation, internet connection, or environment
configuration is needed.

## Run the prebuilt JAR

```powershell
java -jar dist\Wealthora.jar
```

The JAR is produced locally in `dist` and uses the libraries in `dist\lib`.

## Build from source

```powershell
ant clean jar
```

You can also use **Clean and Build** in Apache NetBeans.

## Recommended 3–5 minute demonstration

1. Launch Wealthora. On a new installation, create the local owner account,
   then sign in.
2. Open Dashboard and note the current balance, income, and expense totals.
3. Open Transactions. Add an income and an expense with today's date.
4. Confirm both records appear in the `JTable` and the dashboard totals change.
5. Select a row and use **Edit**. Select a row again and use **Delete**, then
   confirm the deletion.
6. Choose **Data → Export → Transactions**, save the CSV, and open it to show
   the exported rows.
7. Open Settings to show the theme and category controls.
8. Close and reopen Wealthora. Sign in and confirm the remaining data persists.
9. Briefly show `Transaction`, `Income`, `Expense`, `Exportable`, and
   `FinanceService` using [OOP_MAPPING.md](OOP_MAPPING.md) as a reference.

Use demonstration values only. Wealthora stores data under the current
Windows user's local application-data directory.
