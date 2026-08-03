# Wealthora continuation

## Current state

- Branch: `feature/wealthora-online-auth-voice`
- Verified authentication baseline: `f02fef4`
- Cloud finance API commit: `80f1b5a`
- Desktop CLOUD-mode commit: `9fb9c05`
- Live verification tooling commit: `718e96a`
- Real SMTP registration audit commit: `6af16c5`
- Explicit CLOUD/LOCAL sign-in commit: `614986a`
- Verified desktop launcher commit: `8cf4891`
- Real SMTP password-recovery audit commit: `967d640`
- The status-document commit follows that implementation commit; confirm the
  exact current hash with `git rev-parse --short HEAD`.
- Desktop and server tests/builds pass under Java 25.
- Repository hygiene, focused GitHub workflows, Render Docker readiness,
  authenticated finance APIs, and the explicit Swing LOCAL/CLOUD boundary are
  implemented.
- The cloud-finance milestone was not pushed, merged, or deployed. The remote
  branch is still at the earlier release-foundation checkpoint.
- Live Neon TLS, Flyway V1-V5, the 26-table inventory, ownership constraints,
  production-profile restart behavior, and unchanged data-count fingerprints
  have passed.
- A disposable live Neon authentication lifecycle passed and cleaned up all
  of its generated database and mail-sink state.
- Real SMTP registration, OTP consumption, account activation, password
  identity, USER role assignment, CLOUD session creation, and audit events
  passed through a private manual flow plus anonymized database audit.
- `scripts/Start-WealthoraDesktop.ps1` now validates server health, provider
  readiness, the desktop JAR, and Java before starting a child process with
  the configured cloud URL.
- Real SMTP recovery passed on one verified NSU account: request, completion,
  one-time-value consumption, password-identity update, and pre-reset session
  revocation are proven by an anonymized read-only audit.
- That recovered address also belongs to a local account. The previous
  desktop logic silently preferred the local record, so the replacement cloud
  password could not create a backend session.
- Password sign-in now has separate **Sign In to CLOUD** and **Sign In to
  LOCAL** actions. A regression test proves CLOUD bypasses a same-email local
  record while LOCAL still opens the OWNER workspace.
- Repeated attempts with the cloud replacement password were routed to the
  LOCAL OWNER and triggered its intended 15-minute lockout. The CLOUD account
  remains active and unlocked; the local password remains separate and
  unchanged.
- The rebuilt two-button UI then completed a successful post-reset CLOUD
  sign-in. The anonymized live audit confirmed a clear cloud lock state and an
  active server session. Real SMTP registration and password recovery are
  complete.
- The external configuration reports the core, SMTP, and Google OAuth groups
  as set. Google Cloud Application Default Credentials are unavailable.
- The Next.js frontend has not been started.
- Docker Desktop 4.84.0 and Docker CLI/Server 29.6.2 responded after the
  required Windows restart, but `hello-world` failed with an engine HTTP 500.
  A clean relaunch then failed to attach Docker's existing WSL data VHDX with
  `0x800705aa` because system resources were insufficient.
- WSL 2.7.11.0 is current and defaults to WSL 2. No Linux distribution is
  installed apart from Docker's managed `docker-desktop` WSL 2 distribution.
- The Windows pending-restart indicators are now clear. No Docker factory
  reset, unregister, VHDX deletion, image deletion, or volume deletion was
  attempted.

## Exact next task

Continue Stage 4 with the verified CLOUD account and production-profile
backend. Exercise account, category, income, expense, transfer, transaction
edit/delete, budget, recurring, goal, debt, and dashboard/report behavior.
Restart the desktop and backend to confirm persistence, then verify a second
user cannot access the first user's finance records. Confirm logout and Switch
Account clear cloud state, server stop/restart produces the documented
transport states, and the established LOCAL OWNER finance files remain
byte-for-byte unchanged.

If processes must be restarted, start the server with
`scripts/Start-WealthoraServer.ps1`, then launch the desktop with:

```powershell
.\scripts\Start-WealthoraDesktop.ps1 `
  -ServerUrl 'http://127.0.0.1:18080'
```

The password-recovery audit already reports the request, reset completion,
consumed token, password-identity update, pre-reset session revocation,
successful post-reset login, clear account lock, and an active cloud session.

After password-recovery SMTP passes, continue automatically with the desktop
CLOUD-mode finance smoke test, Google Cloud Speech ADC authorization, Google
OAuth browser flow, administration verification, Docker recovery, the
production container, and then the Next.js web application.

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
