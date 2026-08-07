# Wealthora shared-online core

This document describes the intentionally small online milestone on
`feature/shared-online-core`. The offline teacher release remains frozen at
`cse215-final-v1.1.1`.

## Architecture

```text
Wealthora Swing desktop
        |
      HTTPS
        |
Spring Boot API on Render
        |
Neon PostgreSQL
```

The desktop never connects to PostgreSQL. It contains one public HTTPS API URL
and no database username, password, token pepper, or OWNER password.

## Included workflow

- Central registration creates an activated USER account through the API.
- Passwords are protected on the server with the existing SHA-256 plus BCrypt
  encoder. Password hashes and session tokens are never returned to the client.
- Sign-in opens the authenticated cloud workspace.
- Accounts, income, expenses, transfers, transaction history, and dashboard
  totals are persisted centrally.
- OWNER and ADMIN can list safe user-account metadata in Admin Console.
- OWNER can grant and revoke ADMIN. Neither ADMIN nor OWNER gains access to
  another user's finance data.

Email verification is disabled for this no-SMTP milestone. Registration still
requires an exact `@northsouth.edu` email, terms acceptance, and the password
policy. Password recovery is deferred; an OWNER credential is provisioned once
through server-only deployment secrets. A narrowly scoped one-time recovery
claim is available only when the intended initial OWNER account already exists
and the database still has no OWNER.

## Privacy boundary

Every finance repository query derives `user_id` from the authenticated server
session. Finance request bodies contain no selectable user owner. PostgreSQL
composite foreign keys prevent accounts, categories, transactions, transfers,
and related records from referencing another user's records.

## Production configuration

Render uses [`render.yaml`](../render.yaml). These secrets are entered only in
Render:

- `DATABASE_URL` in JDBC PostgreSQL form with `sslmode=require`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `WEALTHORA_OWNER_NAME`
- `WEALTHORA_OWNER_EMAIL`
- `WEALTHORA_OWNER_PASSWORD`
- `WEALTHORA_OWNER_CLAIM_TOKEN` only for an existing-account recovery claim

Render generates `TOKEN_PEPPER`. The OWNER bootstrap runs only when the
database has no OWNER role, and the name/email/password values create only a
new account. An existing account is never promoted or password-reset during
startup. Instead, the existing Reset Password screen can consume the
high-entropy claim token once to reset the password explicitly and atomically
grant USER, ADMIN, and OWNER to the configured active, verified account. The
claim preserves the user ID and all finance data, revokes old sessions, and is
disabled as soon as any OWNER exists. Once an OWNER exists, startup ignores all
bootstrap password values, so later password changes cannot break a redeploy.
Flyway clean is disabled and Hibernate only validates the schema.

## Build and verification

Desktop:

```powershell
C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat clean test-quality jar
```

Server:

```powershell
cd .\server
.\mvnw.cmd --batch-mode --no-transfer-progress package
```

`SharedOnlineCoreEndpointTest` simulates two devices. It verifies registration,
server-side password hashing, logout and second-device login, accounts, income,
expense, transfer, history, dashboard totals, a second isolated user, OWNER
user-list visibility, USER ↔ ADMIN role changes, and OWNER/user finance
isolation.

## Friend-device run

Download and extract the successful **Desktop CI** artifact for
`feature/shared-online-core`. Keep `Wealthora.jar` and its `lib` directory
together, open PowerShell in that extracted folder, then run:

```powershell
java -jar .\Wealthora.jar
```

No environment variable or database configuration is required on the friend
device. Internet access is required. On Render's free plan, the first request
after an idle period can take longer while the service starts.

## Deferred

The web client, Google OAuth, SMTP/email verification, general online password
recovery, online backup/restore, cloud data migration, and new voice work are
outside this quick milestone. The one-time initial-OWNER recovery claim is the
only no-SMTP recovery exception.
