# Wealthora current implementation status

Verified on 2026-08-04 on branch
`feature/wealthora-online-auth-voice`.

## Release checkpoint

- Verified authentication baseline: `f02fef4` (`feat: complete Wealthora
  authentication backend`). The completed authentication audit and
  implementation were not repeated or redesigned.
- Repository hygiene and documentation: `076535a`.
- GitHub desktop, server, and repository checks: `4523ad0`, followed by the
  transparent whitespace correction `5fa6996` and change-range correction
  `779360a`.
- Render Docker readiness: `d747f8f`, followed by the Linux line-ending
  contract `334376f`.
- Owner-scoped cloud finance APIs and Flyway V5: `80f1b5a`.
- Explicit desktop LOCAL/CLOUD finance mode and API-backed repositories:
  `9fb9c05`.
- Secret-safe live Neon and disposable authentication verification tooling:
  `718e96a`.
- Anonymized real SMTP registration verification: `6af16c5`.
- Explicit same-email CLOUD/LOCAL sign-in routing: `614986a`.
- Verified desktop cloud launcher: `8cf4891`.
- Anonymized real SMTP password-recovery verification: `967d640`.
- Verified desktop CLOUD workflows and low-memory launchers: `9252ee1`.
- Google Cloud Speech robustness and privacy verification: `febb7b5`.
- Live Google Speech verification and low-memory harness: `7bc7738`.
- Deterministic Windows loopback verification: `cc6d244`.
- The commit containing this report follows those implementation commits; use
  `git rev-parse --short HEAD` for its exact hash.
- The cloud-finance milestone commits were not pushed or merged. The remote
  branch remains at the previously published release-foundation checkpoint.

## Complete

### Authentication foundation

The verified authentication foundation remains complete: exact NSU
registration, the 8-128 character password policy, six-digit verification,
default activation after verification, password recovery, opaque sessions,
revocation, USER/ADMIN/OWNER authorization, audit events, Google OAuth
boundaries, SMTP capability reporting, and per-user finance isolation.
Flyway V1-V5 remain forward-only, and Hibernate remains configured with
`ddl-auto=validate`.

### Owner-scoped cloud finance milestone

- Authenticated `/api/finance/**` endpoints now cover accounts, categories,
  expenses, income, transfers, the combined transaction ledger, monthly and
  advanced budgets, recurring entries, savings goals and contributions,
  debts and repayments, and the dashboard summary.
- Every finance lookup takes ownership from the authenticated
  `SessionPrincipal`. Finance requests contain no owner/user authorization
  field. USER, ADMIN, and OWNER records remain private to their own user ID;
  elevated roles do not imply access to somebody else's finances.
- Amounts, dates, ISO currency codes, account/category references, archived
  state, recurrence rules, and transfer currencies are validated. Growing
  lists use bounded pagination and errors use the safe shared API envelope.
- Income, expense, and transfer changes lock owned accounts and update ledger
  rows and balances in one database transaction. A transfer has one transfer
  record and exactly two ledger legs; duplicate identifiers are rejected and
  failed transfers leave both balances unchanged.
- Flyway V5 extends the ownership scaffolding with external desktop IDs and
  forward-only finance, planning, preference, goal, and debt tables. Composite
  foreign keys enforce that referenced accounts, categories, goals, and debts
  belong to the same user. Cascades preserve safe user lifecycle cleanup.
- Routine private finance operations do not create security/administrative
  audit records.

### Explicit desktop data modes

- Local password/OWNER sessions remain `LOCAL` and continue to use the exact
  existing per-user CSV repositories and services. The local construction
  path and existing OWNER data layout were not migrated or rewritten.
- Server-authenticated password and Google sessions are `CLOUD`. They no
  longer create or activate a local finance workspace. The Swing application
  uses authenticated HTTP repository adapters for all required finance and
  planning areas while retaining the existing service and UI layers.
- The window title and top bar show `LOCAL` or `CLOUD`. Cloud transport state
  distinguishes Connected, Offline, Unauthorized, and Server unavailable.
  Access and refresh tokens stay inside the authentication gateway.
- CLOUD mode does not instantiate local finance CSV, import, backup, card, or
  currency-preference storage. Unsupported local-only tools are disabled or
  shown as unavailable instead of silently mixing datasets.
- The Cloud Data menu provides a read-only preview of the local OWNER
  workspace. It lists candidate files and bytes but has no upload, automatic
  migration, or synchronization action.

### Google Cloud Speech foundation

- Authenticated CLOUD sessions use the server-side Google Cloud
  Speech-to-Text V1 gateway. English uses `en-US`, Bangla uses `bn-BD`, and
  automatic/Banglish recognition permits both before the multilingual parser.
- The Swing flow exposes microphone selection and health, provider state,
  language, Start/Stop/Cancel, a 30-second timeout and duration, editable
  transcript and structured fields, confidence, validation warnings, manual
  typed fallback, and explicit **Confirm and Add**. Recognition and status
  checks execute outside the Swing event thread.
- Microphone buffers are never persisted and are wiped after successful or
  failed recognition. Cancellation now also honours worker interruption during
  an active Java Sound capture.
- The production-profile live harness passed real Windows loopback capture,
  authenticated Speech V1 recognition, multilingual parser handoff, a complete
  editable English expense draft, and explicit confirm-before-save without
  creating a finance record. Synthetic playback is wiped after every attempt;
  the generated CLOUD user and mail fixture are removed automatically.

### Repository hygiene and documentation

- `.gitignore` now excludes environment files other than examples,
  credential and service-account files, local databases and dumps, backups,
  recordings, private reports, IDE-local state, build output, future web
  output, and generated runtime storage.
- Generated `build/`, `dist/`, and `server/target/` artifacts are untracked.
  The only tracked JARs are the two reviewed desktop libraries and Maven
  Wrapper bootstrap; no distributable application JAR is tracked.
- A filename and high-confidence content scan found no secret candidate in
  the current repository or any reachable commit. The scan never printed
  matching values.
- `README.md`, `SECURITY.md`, `CONTRIBUTING.md`, `.env.example`, and the
  architecture, local-development, deployment, and Render guides describe
  only implemented behavior and current limitations. No license was added.
- Shared NetBeans project files remain in place. The desktop project was not
  moved.

### GitHub CI

- Desktop CI sets up Java 25, runs `ant test-auth`, runs `ant clean jar`, and
  uploads `dist/Wealthora.jar` after success.
- Server CI sets up Java 25, uses the Maven Wrapper, runs tests, and packages
  without production secrets.
- Repository checks reject sensitive/generated tracked files, unexpected
  JARs, high-confidence secret signatures, and whitespace errors introduced
  by the current push or pull request.
- The pull-request template covers summary, changes, testing, migration,
  security, screenshots, and a concise checklist.
- Workflow action majors were checked against their official repositories.
  The workflows have not run on GitHub because nothing was pushed.

### Render readiness

- `server/Dockerfile` is a multi-stage Java 25 build. The runtime stage copies
  only the executable Spring Boot JAR, runs as numeric user/group `10001`,
  uses an exec-form entry point, and handles `SIGTERM` for graceful shutdown.
- The Docker copy uses the exact executable JAR name. This avoids accidentally
  matching Maven's additional `.jar.original` file.
- `application-prod.yml` binds to `0.0.0.0:${PORT:10000}`, requires the three
  database variables, validates Flyway and Hibernate state, disables Flyway
  clean and DDL generation, hides health details, and bounds graceful
  shutdown.
- Database, SMTP, Google OAuth, and speech configuration continues to come
  only from environment variables. No credential is copied into an image.
- Render dashboard fields and safe startup expectations are documented in
  `docs/RENDER_DEPLOYMENT.md`. No deployment was attempted.

## Verification results

- **Desktop tests:** `ant clean jar test-voice` passed the full dependency chain,
  including authentication-policy, local authentication/authorization,
  online-gateway, five cloud-finance repository/migration-preview tests, and
  24 multilingual voice/privacy tests.
- **Desktop build:** `ant clean jar` passed under Microsoft OpenJDK 25.0.2 and
  produced `dist/Wealthora.jar`.
- **Desktop runtime:** the JAR opened `Wealthora Authentication` using a new
  temporary `LOCALAPPDATA`; the spawned process was then stopped.
- **Server tests:** `server\mvnw.cmd package` passed 42 tests with no failures
  or errors. The two tests that require explicit live-environment flags were
  skipped during the ordinary package build. The isolated
  H2/PostgreSQL-compatibility suite applied Flyway V1-V5 and exercised
  registration, verification, login, password recovery, sessions, role
  restrictions, audit behavior, OAuth boundaries, private finance role
  isolation, pagination, planning APIs, safe errors, and atomic transfers.
- **Server build:** the package build produced
  `server/target/wealthora-auth-server-1.0.0-SNAPSHOT.jar`.
- **Production failure safety:** a bounded `prod` run against an unreachable
  loopback database failed startup as expected. None of the synthetic database
  URL, username, password, or token-pepper canaries appeared in captured logs.
- **Diff and documentation checks:** `git diff --check`, generated-file
  assertions, tracked-JAR assertions, and local documentation-link checks
  passed. No local YAML parser was installed; workflows received a manual
  syntax review and still require their first GitHub run.
- **Docker:** Client and Server version/info responded after the required
  Windows restart, but `hello-world` failed at container creation with a
  Docker Desktop HTTP 500. A clean Docker shutdown, `wsl --shutdown`, and
  relaunch then exposed WSL VHDX attach error `0x800705aa` (insufficient
  system resources). No image, volume, VHDX, or Docker setting was reset.
- **Existing finance data:** all five finance entries in the pre-online-auth
  backup match the current OWNER finance files byte-for-byte. Authentication
  and audit files changed only through the documented authentication actions;
  no OWNER finance file was migrated or rewritten.

## Live Neon and authentication verification

- The external environment file is outside the repository. The four required
  database/security variables, all six SMTP variables, and all three Google
  OAuth variables are set. Only status and invariant results were reported;
  no values were printed or copied.
- The JDBC URL is PostgreSQL, contains no embedded credentials, requires TLS,
  and completed direct PostgreSQL TLS negotiation with certificate
  verification.
- A read-only live audit verified PostgreSQL, five successful Flyway SQL
  history rows with checksums, the exact 26-table public inventory, and all
  composite finance-ownership constraints.
- Two production-profile starts against Neon passed health and provider
  readiness. The second start validated existing Flyway checksums and applied
  no migration.
- The data-count fingerprint was identical before startup, after restart, and
  after the disposable live authentication run:
  `13a7999f25ba8051d2ee8d95490ebbf334409e887ccb651d1e2659c9773bf42e`.
- The disposable live authentication run passed domain/password/terms
  validation, duplicate rejection, pending-login rejection, verification
  expiry and resend controls, wrong-code handling, activation, wrong-password
  handling, refresh rotation and replay defense, individual/global
  revocation, password change and recovery, reset-token single use, failed
  login protection, suspended/disabled rejection, session expiry, USER
  restrictions, and audit coverage.
- The generated user, authentication child rows, audit rows, login attempts,
  verification/reset values, and temporary mail files were removed after the
  run. No existing user or finance row was changed.
- Real SMTP registration passed with a private NSU recipient. The anonymized
  read-only audit verified a consumed email verification, ACTIVE account,
  password identity, USER role, active CLOUD session, and the expected
  registration, verification, and login audit actions. No address, OTP,
  password, or token was read or printed.
- Real SMTP password recovery passed for one verified NSU account. The
  anonymized read-only audit proves request delivery, reset completion,
  one-time reset-value consumption, password-identity update, pre-reset
  session revocation, ACTIVE status, and a clear lock state.
- The cloud-safe desktop launcher passed server health, email-provider, Google
  OAuth, JAR, Java, and process checks against port 18080.
- The configured Google OAuth server passed readiness with the exact local
  loopback callback. Seven endpoint tests independently reject invalid issuer,
  audience, expiry, verified-email, hosted-domain, nonce, and subject claims;
  they also prove verified-email password-account linking, new-user creation,
  duplicate prevention, subject immutability, and one Google subject per user.
- A value-aware scan found no configured Google client secret in any tracked
  file or desktop JAR entry. The desktop JAR contains no OAuth client-secret
  setting name. A controlled pending live flow was removed without linking or
  changing any user.
- The recovered address also has a same-email local account. The prior
  local-first routing prevented a cloud password from reaching the backend.
  Sign-in now exposes separate **Sign In to CLOUD** and **Sign In to LOCAL**
  actions; a regression test proves that CLOUD bypasses a same-email local
  record while explicit LOCAL sign-in still works.
- Repeated use of the cloud replacement password against the LOCAL OWNER
  triggered the intended temporary local lockout. This does not affect the
  active, unlocked CLOUD account and did not change the local password.
- The rebuilt desktop and production-profile server completed a private
  post-reset CLOUD sign-in successfully. The anonymized audit proves
  a successful login attempt, clear cloud lock state, and active server
  session. Stage 3 real SMTP authentication is complete.

## Live desktop CLOUD verification

- A fully generated pair of disposable NSU-format users exercised the real
  production-profile server, Neon PostgreSQL, development mail sink, desktop
  authentication gateway, cloud repositories, services, and Swing workspace.
  Passwords, one-time values, and tokens remained memory-only.
- The first user created and read accounts, categories, income, expenses,
  transfers, a monthly budget, recurring entry, savings goal, and debt. It
  also updated and deleted an expense and verified dashboard/report totals.
- A normal USER was denied administrative access. A second authenticated user
  could not read the first user's records, including through referenced
  finance resources.
- A fresh authentication gateway could sign in again and read the persisted
  records. Logout cleared access and refresh state.
- The real CLOUD Swing frame constructed successfully and displayed
  **Private CLOUD workspace**, authenticated sync copy, and Connected state.
  A construction-scoped read snapshot coalesces identical startup GETs; it is
  discarded immediately so later refreshes observe other-device changes.
- Stopping the backend produced the documented `SERVER_UNAVAILABLE` state.
  Restarting the same production-profile backend preserved the session and
  finance data and returned the desktop transport state to Connected.
- Scoped cleanup deleted both generated users and their dependent rows plus
  the temporary mail/fixture state. No existing user row was changed.
- All five established LOCAL OWNER finance files still match the
  pre-online-auth backup byte-for-byte.

## Remaining configuration and limitations

- Google OAuth is configured server-side and its automated security/linking
  contract passes, but browser authorization, repeat live sign-in, and live
  account linking remain unverified. The browser connector reported zero
  available browsers; no substitute or simulated success was used.
- Google Cloud Speech has its project ID, Google Cloud CLI 578.0.0, valid
  Application Default Credentials, quota-project access, and an enabled Speech
  V1 API. Live English recognition passes. Exact Bangla (`bn-BD`) and
  Banglish/mixed request construction plus Bengali/Banglish parsing remain
  covered by the 24 deterministic voice tests; this machine has only en-US
  synthetic voices, so the automated live fixture does not claim native
  Bengali speech synthesis.
- Docker and GitHub-hosted workflow execution remain unverified in this
  environment.
- Flyway emitted a non-failing warning that test-only H2 2.4.240 is newer than
  the H2 release it has verified. All H2 tests passed, but they do not replace
  the required real PostgreSQL run.
- Cloud card storage, currency preferences, import, backup/restore,
  synchronization, and automatic local-data migration are intentionally not
  implemented. The existing local tools remain available only in LOCAL mode.
- The Next.js web frontend was intentionally not started.

The exact next task and safe commands are in `docs/NEXT_CODEX_STEPS.md`.
