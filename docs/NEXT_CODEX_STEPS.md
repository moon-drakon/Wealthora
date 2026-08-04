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
- Verified desktop CLOUD workflow commit: `9252ee1`
- Google Cloud Speech hardening commit: `febb7b5`
- Live Google Speech verification commit: `7bc7738`
- Deterministic speech loopback commit: `cc6d244`
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
- Two generated disposable users completed the real Neon-backed desktop CLOUD
  finance workflow, including full finance/planning coverage, USER
  restrictions, second-user isolation, relogin persistence, logout clearing,
  real Swing construction, server-unavailable handling, backend-restart
  persistence, and scoped cleanup.
- The five established OWNER finance files match the pre-online-auth backup
  byte-for-byte. No automatic migration or synchronization ran.
- The external configuration reports the core, SMTP, and Google OAuth groups
  as set. Google Application Default Credentials are now valid.
- The desktop exposes the complete safe Voice Quick Entry workflow, including
  explicit microphone status and timeout guidance. Cancellation honours thread
  interruption, and both desktop and server tests prove audio-buffer wiping on
  successful and failed recognition.
- `ant clean jar test-voice` passes with 24 voice tests. The server package
  passes 42 tests with no failures or errors and two intended live-only skips.
- The official Google Cloud CLI 578.0.0, ADC, quota project, and Speech V1 API
  are ready. A production-profile live run passed the real Windows loopback,
  authenticated English recognition, parser completion, confirm-before-save,
  and strict cleanup. Exact `bn-BD` and mixed-locale requests plus native
  Bengali/Banglish parsing are covered by deterministic tests; no native
  Bengali TTS voice is installed on this verifier.
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

Complete the Google Cloud console gate for Stage 6. Sign in at
`https://console.cloud.google.com/apis/credentials` with the developer account
that owns the configured OAuth **Web application** client. Select the client
whose ID matches the server-only configuration, preserve its existing entries,
add `http://127.0.0.1:8080/api/auth/google/callback`, verify the documented
production callback is present, and save. Google's official authorization page
currently returns `redirect_uri_mismatch`; the failed pending flow was removed
exactly and no account was linked.

Then rerun the token-safe browser authorization flow and verify first sign-in,
repeat sign-in, password/Google identity coexistence, CLOUD session creation,
logout/revocation, and scoped cleanup. Do not print authorization codes,
provider tokens, email addresses, or session values. The identity-linking
contract, invalid-claim rejection, duplicate prevention, and desktop
client-secret isolation already pass.

Then continue through live administration, Docker/container recovery, and the
Next.js web application in dependency order.

## Completed live database sequence

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
- Do not push, merge, deploy, or migrate desktop finance data without explicit
  authorization. Local web implementation and testing are authorized by the
  user's request; external deployment remains a separate gate.
