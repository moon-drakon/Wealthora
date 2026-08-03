# Wealthora continuation

## Current state

- Branch: `feature/wealthora-online-auth-voice`
- Verified authentication baseline: `f02fef4`
- Cloud finance API commit: `80f1b5a`
- Desktop CLOUD-mode commit: `9fb9c05`
- The report update is committed after that implementation commit; confirm the
  exact current hash with `git rev-parse --short HEAD`.
- Desktop and server tests/builds pass under Java 25.
- Repository hygiene, focused GitHub workflows, Render Docker readiness,
  authenticated finance APIs, and the explicit Swing LOCAL/CLOUD boundary are
  implemented.
- The cloud-finance milestone was not pushed, merged, or deployed. The remote
  branch is still at the earlier release-foundation checkpoint.
- The Next.js frontend was not started.
- Docker Desktop 4.84.0 and Docker CLI 29.6.2 were installed through the
  official `Docker.DockerDesktop` Winget package. Windows then reported a
  pending Component Based Servicing restart. Docker Client and Server now
  respond, but `hello-world` verification has not run.
- WSL 2.7.11.0 is current and defaults to WSL 2. No Linux distribution is
  installed apart from Docker's managed `docker-desktop` WSL 2 distribution.
- The first resume check found that the reported restart had not produced a
  new Windows boot: the last boot time still preceded the Docker installation,
  and the Component Based Servicing restart flag remained set. Docker Client
  and Server responded, but `hello-world` was deliberately not started past
  this uncleared restart gate.

## Exact next task

Use **Start > Power > Restart** to perform a full Windows restart; do not use
Shut down because Fast Startup can preserve the current kernel session. Then
follow `docs/LOCAL_SETUP_PROGRESS.md` to confirm a new boot time, re-check WSL,
start Docker Desktop, and verify both Docker Client and Server plus
`hello-world`. Do not claim Docker complete until all three checks pass and
the Component Based Servicing restart flag has cleared.
After Docker is verified, validate the external environment file without
printing values, create or verify the secret-safe launcher, and only then run
Flyway V1-V5 plus the authentication, administration, and cloud-finance checks
against the configured isolated PostgreSQL or Neon database. Finish with a
bounded desktop CLOUD-mode smoke test. Do not use the desktop's local OWNER
workspace or any database containing real finance data.

The external file was reported to contain the four core values, but it was not
read or validated during this restart-gated checkpoint. Required names are:

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `TOKEN_PEPPER`

Set them only in the process environment or a secret manager. Never paste
their values into commands, files, screenshots, issues, reports, or commits.
For Neon, use a JDBC URL with verified TLS, such as `sslmode=require`.

## Live database sequence

1. Confirm the target database is empty and disposable.
2. Run `server\mvnw.cmd test` and `server\mvnw.cmd package` before the live
   check.
3. Start the server with the explicit development mail sink in a new temporary
   directory:

   ```powershell
   cd G:\Projects\SpendWiseExpenseTracker\server
   $env:SPRING_PROFILES_ACTIVE = 'dev-mail-sink'
   $env:WEALTHORA_DEV_MAIL_DIR = Join-Path $env:TEMP 'wealthora-dev-mail-live'
   .\mvnw.cmd spring-boot:run
   ```

4. Verify `GET /actuator/health` and `GET /api/auth/status` without exposing
   configuration values.
5. In the database console, verify `flyway_schema_history` contains successful
   V1, V2, V3, V4, and V5 rows in order. Confirm the authentication, account,
   category, transaction, transfer, finance-preference, budget, recurring,
   goal/contribution, and debt/repayment tables and ownership constraints
   exist.
6. Stop and restart the server. Confirm Flyway validates existing checksums,
   applies no duplicate migration, and preserves test records.
7. Run the isolated end-to-end checks:
   - exact `northsouth.edu` registration and rejection of other domains;
   - accepted and rejected 8-128 character passwords;
   - pending login rejection, development-code verification, and active login;
   - wrong-password rejection, duplicate registration, reset, refresh,
     per-session revocation, and logout-all;
   - USER, ADMIN, and OWNER restrictions;
   - user-owned account/category/transaction/planning isolation and rejection
     of cross-user references;
   - paged finance lists and consistent safe validation errors;
   - income and expense balance changes plus an atomic two-leg transfer;
   - duplicate and cross-currency transfer rejection with unchanged balances;
   - monthly/advanced budgets, recurring entries, goals, debts, and dashboard
     summary behavior; and
   - audit records for the security and administration actions.
8. The server has no public OWNER-creation route. For administration smoke
   checks, use only a dedicated identity in the disposable database and a
   controlled database fixture to assign its OWNER role. Do not copy or alter
   the desktop OWNER, and do not add a production OWNER-registration endpoint.
9. Sign into the Swing desktop with the disposable server user and verify:
   - the title/top bar say `CLOUD · Connected`;
   - account, category, income, expense, transfer, budget, recurring, goal,
     debt, transaction, and report views use only that server user's records;
   - an ADMIN or OWNER session still cannot see the first user's records;
   - stopping the server changes the displayed state to Server unavailable;
   - an expired/revoked session changes it to Unauthorized; and
   - the local OWNER file hashes remain identical. Do not use the migration
     preview to upload anything; no upload implementation exists.
10. Sign into the local OWNER separately and verify the title/top bar say
    `LOCAL · Offline` and the established CSV records are unchanged.
11. Run the desktop/server tests and both packages again after the live check.
   Record only
   pass/fail results, schema versions, safe error categories, and test-user
   identifiers that contain no private data.

Delete the temporary mail-sink output securely after recording the pass/fail
result. It contains live one-time codes and reset tokens and must never enter
Git or a test report.

## SMTP verification

After the development-sink flow passes, configure all six variables through a
secret store and repeat registration and password recovery with real SMTP:

- `SMTP_HOST`
- `SMTP_PORT`
- `SMTP_USERNAME`
- `SMTP_PASSWORD`
- `SMTP_FROM_ADDRESS`
- `SMTP_FROM_NAME`

Do not claim SMTP success until both messages arrive and their one-time values
work exactly once.

## Docker and CI follow-up

When Docker is available:

```powershell
cd G:\Projects\SpendWiseExpenseTracker\server
docker build --tag wealthora-server:local .
```

Run the image with name-only `--env` arguments as documented in
`docs/DEPLOYMENT.md`, then verify `/actuator/health`, provider status, graceful
termination, and the absence of source credentials or local data in the image.

After pushing is separately authorized, let all three GitHub workflows run on
the branch and confirm the desktop JAR artifact is uploaded. Do not deploy to
Render until live PostgreSQL, SMTP where required, Docker, and CI checks pass.

## Stop conditions

- If migration validation fails, stop and add a new forward-only migration;
  never edit V1-V5 or run Flyway clean on a non-disposable database.
- If any secret is logged or found in history, stop, identify only the affected
  file and commit, rotate the credential, and plan history cleanup without
  exposing the value.
- Do not push, merge, deploy, start the web frontend, or migrate desktop finance
  data without explicit authorization.
