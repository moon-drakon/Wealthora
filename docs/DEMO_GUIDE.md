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
   recovery answer, and safe hint; then sign in.
2. Use **Create Account** on the sign-in screen to show that another user can
   receive a separate local workspace. Use a demonstration account only.
3. Open Dashboard and note the current balance, income, and expense totals.
4. Open Transactions. Add an income and an expense with today's date.
5. Open **Entry → Voice Quick Entry** (`Ctrl+Shift+V`), speak an English or
   Banglish command, review the draft, and confirm it. If Windows speech is not
   installed, demonstrate the manual command parser in the same dialog.
6. Confirm the records appear in the `JTable` and dashboard totals change.
7. Select a row and use **Edit**. Select a row again and use **Delete**, then
   confirm the deletion.
8. Choose **Data → Export → Transactions**, save the CSV, and open it to show
   the exported rows.
9. As OWNER, open **Profile → Admin Console → Users** to show account status,
   ADMIN role management, and assisted password reset.
10. Sign out and open **Forgot Password?** to show the saved recovery question
    and non-secret hint. Do not disclose the answer during the demo.
11. Close and reopen Wealthora. Sign in and confirm the remaining data persists.
12. Briefly show `Transaction`, `Income`, `Expense`, `Exportable`, and
   `FinanceService` using [OOP_MAPPING.md](OOP_MAPPING.md) as a reference.

Use demonstration values only. Wealthora stores data under the current
Windows user's local application-data directory.

Voice recognition is offline and does not retain audio. The transaction is
saved only after the user reviews and confirms the parsed draft.
