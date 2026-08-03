# Wealthora current implementation status

Verified on 2026-08-03 on branch
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

- **Desktop tests:** `ant test-auth` passed the full dependency chain,
  including 14 authentication-policy, 22 local authentication/authorization,
  9 online-gateway, and 4 cloud-finance repository/migration-preview tests.
- **Desktop build:** `ant clean jar` passed under Microsoft OpenJDK 25.0.2 and
  produced `dist/Wealthora.jar`.
- **Desktop runtime:** the JAR opened `Wealthora Authentication` using a new
  temporary `LOCALAPPDATA`; the spawned process was then stopped.
- **Server tests:** `server\mvnw.cmd package` passed 35 tests with no failures
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
- **Existing data:** the established application-data fingerprint was
  identical before and after verification: ten files, including six CSV
  files. No existing OWNER, authentication, or finance file changed.

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
- Real SMTP is configured but message arrival and one-time-value consumption
  still require a real NSU recipient and manual OTP entry. SMTP is not yet
  claimed as a live pass.

## Remaining configuration and limitations

- Complete one real SMTP registration and password-recovery delivery check.
- Google OAuth is configured server-side, but browser authorization and live
  account linking remain unverified.
- Google Cloud Speech has a project ID but no available Application Default
  Credentials on this host.
- Docker and GitHub-hosted workflow execution remain unverified in this
  environment.
- Flyway emitted a non-failing warning that test-only H2 2.4.240 is newer than
  the H2 release it has verified. All H2 tests passed, but they do not replace
  the required real PostgreSQL run.
- A live desktop-to-server CLOUD-mode GUI smoke test remains pending.
- Cloud card storage, currency preferences, import, backup/restore,
  synchronization, and automatic local-data migration are intentionally not
  implemented. The existing local tools remain available only in LOCAL mode.
- The Next.js web frontend was intentionally not started.

The exact next task and safe commands are in `docs/NEXT_CODEX_STEPS.md`.
