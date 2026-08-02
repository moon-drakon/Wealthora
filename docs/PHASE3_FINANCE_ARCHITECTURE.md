# SpendWise Phase 3 Finance Architecture

## Scope and compatibility

Phase 3 extends the existing Java 25, Ant, CSV-repository, service, and Swing
architecture. It does not replace Phase 1 or Phase 2 screens, transaction rules,
or data files. Existing ledgers remain the source of truth for account balances.

All money continues to use `BigDecimal` with two decimal places. Repositories
use staged writes and atomic replacement. No external dependency was added.

## Managed data migrations

The migration strategy is additive and lazy. New files are created only when a
feature is first changed; opening the application and reading screens does not
create or rewrite files.

### `budget-plans.csv`

Header:

```text
id,name,startDate,endDate,scope,category,amount,rollover,status
```

An overall row and zero or more category rows share a budget ID. Periods may be
monthly or custom. `NONE` and `CARRY_UNUSED` are supported rollover policies.
Archived plans remain available as budget history.

### `recurring.csv`

The legacy 13-column format is still readable. It is upgraded on the next
recurring write to:

```text
id,type,amount,description,category,sourceAccount,destinationAccount,frequency,interval,startDate,endDate,nextDueDate,kind,reminderDays,status
```

`kind` distinguishes a scheduled transaction, bill, and subscription.
Occurrence IDs are deterministic, so retrying generation cannot duplicate an
expense, income entry, or transfer for the same definition and due date.

### `savings-goals.csv`

Header:

```text
recordType,id,goalId,name,targetAmount,targetDate,account,date,amount,note,status
```

`GOAL` and `CONTRIBUTION` rows share one atomic file. Progress is calculated
from contribution history. Contributions are planning records: they do not
silently create transactions or modify a linked account balance.

### `debts.csv`

Header:

```text
recordType,id,debtId,direction,counterparty,originalAmount,dueDate,date,amount,note
```

`DEBT` and `REPAYMENT` rows support borrowed and lent balances. Remaining value
and `OPEN`, `PARTIALLY_REPAID`, `OVERDUE`, or `PAID` status are derived from
history. Repayments cannot exceed the remaining amount. These records are also
memo-only and do not silently change transaction ledgers.

## Reports and notifications

`PortfolioAnalyticsService` composes existing repository-backed reporting with
account balances, budget-plan performance, debt balances, and recurring
commitments. Transfers remain excluded from income and expense totals.

Net worth is calculated as:

```text
all account balances + outstanding lent - outstanding borrowed
```

Recurring commitment totals are nominal amounts per active definition; they are
not normalized to a monthly frequency. Notifications are evaluated while the
desktop application is open and include bill/subscription reminders, credit-card
due dates, budget warnings, and overdue debt records. They are not operating
system background notifications.

## Backup, restore, import, and export safety

- ZIP backup/restore continues to support all managed files.
- Versioned JSON backup format 1 stores an ordered allow-list of managed files,
  SHA-256 checksums, and Base64 content.
- Both restore paths validate every supplied CSV in an isolated temporary
  directory before changing application data.
- Restore makes a safety ZIP when current data exists and uses transactional
  rollback if replacement fails.
- CSV import accepts exact SpendWise expense, income, or transfer export
  headers. It validates all rows and duplicate IDs before mutation, then creates
  a safety ZIP. An application data file cannot be selected as an import source.
- PDF export uses a small standards-compatible, dependency-free PDF writer for a
  portfolio summary. CSV remains the full-detail export format.
- Existing backup/export destinations are never replaced without UI confirmation.

## Desktop UI boundaries

The Planning page calls only `AdvancedBudgetService`, `SavingsGoalService`, and
`DebtService`. Swing handlers do not write repositories or recalculate balances.
The notification page calls `FinanceNotificationService`. Advanced Reports uses
`PortfolioAnalyticsService`, while the existing report tabs remain available.

## Known limitations

- Persistence is transaction-safe per managed CSV, not one transaction spanning
  several independent CSV files.
- Goal contributions and debt repayments require the user to separately record
  any real account transaction when money actually moves.
- CSV import intentionally supports transaction exports only. Account, category,
  planning, and report CSV files are export/read formats and are not imported.
- The PDF summary uses the built-in Helvetica font and replaces characters
  outside printable ASCII; CSV/JSON retain UTF-8 data.
- Notifications are refreshed in the application and are not delivered while
  SpendWise is closed.
