# Wealthora continuation

## Current state

- Branch: `feature/wealthora-online-auth-voice`
- Verified authentication baseline: `f02fef4`
- Latest release-foundation implementation commit: `334376f`
- The report update is committed after that implementation commit; confirm the
  exact current hash with `git rev-parse --short HEAD`.
- Desktop and server tests/builds pass under Java 25.
- Repository hygiene, focused GitHub workflows, and Render Docker readiness
  are implemented.
- Nothing was pushed, merged, or deployed.
- The Next.js frontend was not started.

## Exact next task

Run Flyway V1-V4 and the authentication/administration checks against a new,
empty, isolated PostgreSQL or Neon database. Do not use the desktop's local
OWNER workspace or any database containing real finance data.

The required variables are currently missing:

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
   V1, V2, V3, and V4 rows in order. Confirm the expected user, identity,
   verification, reset, session, audit, account, category, and transaction
   tables and constraints exist.
6. Stop and restart the server. Confirm Flyway validates existing checksums,
   applies no duplicate migration, and preserves test records.
7. Run the isolated end-to-end checks:
   - exact `northsouth.edu` registration and rejection of other domains;
   - accepted and rejected 8-128 character passwords;
   - pending login rejection, development-code verification, and active login;
   - wrong-password rejection, duplicate registration, reset, refresh,
     per-session revocation, and logout-all;
   - USER, ADMIN, and OWNER restrictions;
   - user-owned account/category/transaction isolation and rejection of
     cross-user references; and
   - audit records for the security and administration actions.
8. The server has no public OWNER-creation route. For administration smoke
   checks, use only a dedicated identity in the disposable database and a
   controlled database fixture to assign its OWNER role. Do not copy or alter
   the desktop OWNER, and do not add a production OWNER-registration endpoint.
9. Run the server tests and package again after the live check. Record only
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
  never edit V1-V4 or run Flyway clean on a non-disposable database.
- If any secret is logged or found in history, stop, identify only the affected
  file and commit, rotate the credential, and plan history cleanup without
  exposing the value.
- Do not push, merge, deploy, start the web frontend, or migrate desktop finance
  data without explicit authorization.
