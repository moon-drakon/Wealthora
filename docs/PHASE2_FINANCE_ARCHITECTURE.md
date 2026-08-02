# Phase 2 Finance Architecture

SpendWise remains a local-first Java 25 Swing application backed by CSV files.
Phase 2 does not introduce or pretend to use a relational database. Each CSV
repository writes a complete validated snapshot to a temporary file, flushes it,
and atomically replaces the destination when the operating system supports it.
Transfers are one immutable ledger record, so posting, editing, or deleting a
transfer never requires two account-file writes. Account balances are derived
from the ledger and are never persisted as a second source of truth.

## Balance rules

```text
current balance
  = opening balance
  + income assigned to the account
  - expenses assigned to the account
  + incoming transfers
  - outgoing transfers
```

Transfers do not affect total income, total expense, net cash flow, or total
balance across all accounts. Editing and deleting a transaction changes the next
derived balance snapshot automatically. Amount validation uses `BigDecimal` at
two decimal places and rejects zero or negative transaction amounts.

## Versioned CSV schemas

- `accounts.csv`: `id,name,type,openingBalance,status,icon,color,currency,institution,createdDate`
- `expenses.csv`:
  `id,description,amount,date,category,account,paymentMethod,tags,notes`
- `income.csv`:
  `id,date,amount,source,account,paymentMethod,tags,note`
- `transfers.csv`:
  `id,date,amount,sourceAccount,destinationAccount,tags,note`
- `categories.csv`: `id,name,parent,status`
- `cards.csv`:
  `id,name,bank,type,lastFour,creditLimit,billingDay,dueDay,cardAccount,paymentAccount,status`
- `currency-settings.csv`: `currency`

Tags use a validated pipe-separated representation inside their quoted CSV
field. Tags cannot contain `|`, are unique ignoring case, and are limited to ten
values of thirty characters each.

## Migration behavior

Legacy account, expense, income, transfer, and category headers remain readable.
Before an older account file is first upgraded, SpendWise keeps a sibling
`.pre-metadata-backup` copy and never overwrites that safety copy.
Reading alone never rewrites a user's file. The next successful mutation of that
specific file writes the complete in-memory snapshot using the current schema,
so existing records migrate without being dropped. Legacy expenses are assigned
to the protected Cash account, with `UNSPECIFIED` payment method and no tags.
Legacy `MOBILE_WALLET` accounts map to `MOBILE_BANKING`; legacy `CARD` accounts
map to `DEBIT_CARD` because the old format did not distinguish credit cards.

## Card security boundary

`PaymentCard` accepts exactly four numeric last digits. No model, service,
repository, or UI field exists for a full card number, PIN, CVV, online-banking
credential, or authentication secret. Card deletion is deliberately unavailable;
profiles are activated or deactivated to retain historical relationships.
