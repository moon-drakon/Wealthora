# Wealthora current implementation status

Verified on 2026-08-03 on branch `feature/wealthora-online-auth-voice`.

## Recovery checkpoint

- Recovered base HEAD: `ebd8150c54edf227200a4d938fe4474e039d9d58`
  (`feat: complete Wealthora administration console`).
- The interrupted session left 36 modified files and four untracked source,
  migration, and test paths. They were inspected and continued in place; no
  partial work was discarded or restarted.
- The recovered unit contained the 8-128 password policy, six-digit email
  verification, reset-attempt tracking, provider-neutral SMTP updates,
  backward-compatible BCrypt pre-hashing, desktop connection reporting, and a
  Flyway V4 finance-ownership migration. Completion work added explicit
  provider availability, remaining 128-character bounds, secret-redacting
  request representations, and focused default-flow/isolation tests.
- Continuation commit: `feat: complete Wealthora authentication backend`
  (the commit containing this status file; confirm its hash with
  `git rev-parse --short HEAD`).

## COMPLETE

- **Password policy:** Create, reset, change, set-password, OWNER setup,
  desktop feedback, server validation, and tests use exactly 8-128 characters,
  at least one English letter and one number, and no leading/trailing
  whitespace. Uppercase and symbols are not required. The requested accepted
  and rejected examples pass.
- **Password storage:** New local and server passwords use BCrypt cost 12 over
  a SHA-256 pre-hash, avoiding BCrypt's 72-byte truncation. Existing BCrypt
  OWNER/password hashes remain usable. Password, reset-token, refresh-token,
  and verification request representations are explicitly redacted.
- **Create Account:** Full name, exact `northsouth.edu` email, optional student
  ID, password confirmation, Terms/Privacy acceptance, Back to Sign In, and an
  honest Google option are wired to the server. Duplicate email registration
  is rejected and no OWNER creation route was added.
- **Email verification:** Six-digit codes use `SecureRandom`, only HMAC hashes
  are stored, and expiry, single use, five-attempt limit, resend cooldown,
  resend action, generic failure wording, a verification screen, and code-free
  audit records are implemented. The development mail sink is explicitly
  labelled and profile-only.
- **Default new-user login:** The default approval policy is false. A focused
  endpoint test verifies pending-user denial, verification to `ACTIVE`, normal
  password login, and suspended-user denial. Optional administrator approval
  remains supported when explicitly enabled.
- **Sessions and recovery:** Opaque HMAC-hashed access/refresh tokens, refresh
  rotation/replay protection, expiry, failed-login lockout, generic recovery,
  HMAC-hashed one-time reset tokens, expiry, cooldown, five-attempt limit,
  password change/set/reset, session revocation, logout, logout-all, and
  per-session revocation are implemented.
- **Authorization and finance isolation:** Local finance workspaces are keyed
  by user ID; logout and Switch Account clear the session and active private
  data path. USER cannot use Admin Console, only OWNER can manage ADMIN roles,
  OWNER cannot be replaced/demoted, and ADMIN receives no cross-user finance
  privilege. Flyway V4 adds user-owned `accounts`, `categories`, and
  `transactions` with composite ownership foreign keys that reject cross-user
  transaction references.
- **Spring Boot backend:** The existing `server/` module is reused with Java
  25, Maven Wrapper, Spring Security, Validation, Data JPA, Mail, Actuator,
  PostgreSQL, Flyway V1-V4, BCrypt, and health/info endpoints. Hibernate uses
  `ddl-auto: validate`; no destructive schema recreation is configured.
- **Desktop/server honesty:** `WEALTHORA_SERVER_URL` remains the only desktop
  server setting. The sign-in screen separately shows Connected, Server
  unavailable, Server URL missing, Email provider unavailable/configured, and
  Google OAuth unavailable/configured. Public `/api/auth/status` exposes only
  non-secret capability booleans.
- **Google Sign-In:** The browser OAuth contract and PASSWORD/GOOGLE identity
  linking remain implemented. Issuer, audience, expiry, nonce,
  `email_verified`, exact NSU domain/hosted domain, subject, account status,
  and single-use flow checks remain enforced. Callback:
  `GET /api/auth/google/callback`. No Google password or desktop client secret
  is requested or stored.

## PARTIAL

- PostgreSQL/Neon is configuration-ready but not live-verified on this host.
  No `psql`, `pg_isready`, Docker command, or PostgreSQL service is available,
  and no database environment values are configured.
- Server-owned finance tables and database-level ownership constraints exist;
  the Swing application continues to use its established isolated local CSV
  workspaces. Explicit LOCAL/CLOUD finance migration and sync remain a later
  checkpoint.

## MISSING

- A live isolated PostgreSQL/Neon run of Flyway V1-V4, readiness, registration,
  login, and one authorized administration read.
- Real SMTP, Google OAuth, and remote server configuration.
- The Next.js web application and deployment; both are intentionally outside
  this checkpoint and were not started.

## BROKEN

- No known authentication or build breakage after the completed test/build
  runs.

## CONFIGURATION REQUIRED

- Server: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and a
  random `TOKEN_PEPPER` of at least 32 characters. Neon JDBC URLs must include
  TLS, such as `?sslmode=require`.
- Production email: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`,
  `SMTP_PASSWORD`, `SMTP_FROM_ADDRESS`, and `SMTP_FROM_NAME`.
- Desktop online auth: `WEALTHORA_SERVER_URL` (loopback HTTP for development;
  HTTPS otherwise).
- Google OAuth: server-only `GOOGLE_OAUTH_CLIENT_ID`,
  `GOOGLE_OAUTH_CLIENT_SECRET`, and `GOOGLE_OAUTH_REDIRECT_URI`.
- None of those variables was configured in the verification shell. No real
  values or secrets were added to Git.

## Verification results

- Java: Microsoft OpenJDK `25.0.2` was used for successful verification. The
  shell initially exposed Java 21; that environmental failure was corrected by
  setting command-scoped `JAVA_HOME` to the documented JDK 25 installation.
- Desktop tests: `ant test-auth` passed its full dependency chain, including
  14 authentication-policy tests, 22 local authentication/authorization
  tests, 8 HTTP gateway tests, and all finance, persistence, backup/restore,
  and data-portability suites.
- Desktop build: `ant clean jar` passed; JAR:
  `dist\Wealthora.jar`.
- Server tests: `server\mvnw.cmd test` passed all 30 H2/Flyway tests,
  including default activation/login, password/reset limits, SMTP
  availability, secret redaction, roles/sessions, OAuth, and finance ownership.
- Server build: `server\mvnw.cmd package` passed and produced
  `server\target\wealthora-auth-server-1.0.0-SNAPSHOT.jar`.
- Runtime: the desktop JAR was launched against isolated generated application
  storage, remained active for the smoke interval, exposed the expected
  `Wealthora Authentication` window title, and was then stopped.
- Existing local application storage still contains the pre-existing ten
  files (six CSV files). No existing OWNER/authentication/finance file was
  migrated, overwritten, or deleted during this run.

The exact remaining task and resume commands are in `docs/NEXT_CODEX_STEPS.md`.
