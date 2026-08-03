# Wealthora autonomous execution state

Verified on 2026-08-04.

## Current stage

- Branch: `feature/wealthora-online-auth-voice`
- Recorded verification HEAD: `967d640`
- Active stage: Stage 4, desktop CLOUD-mode finance smoke testing
- Exact resume point: use the verified CLOUD account and production-profile
  backend to exercise finance CRUD, restart persistence, isolation, mode
  boundaries, session clearing, and server-state handling

## Completed stages

- Stage 0 repository and documentation recovery
- Stage 2 live Neon PostgreSQL and Flyway V1-V5 validation
- Stage 3 disposable development-mail authentication lifecycle
- Stage 3 real SMTP registration, OTP consumption, activation, and sign-in
- Stage 3 real SMTP password recovery and post-reset CLOUD sign-in

Stage 1 is partially complete: the Windows restart, WSL checks, and Docker
Client/Server checks passed. `hello-world` and Docker stability did not pass
because Docker's WSL VHDX could not be attached under current host resource
pressure. No destructive Docker recovery was attempted.

## Tests passed

- `ant test-auth`
- `ant clean jar`
- `server\mvnw.cmd package`: 35 tests, 0 failures, 0 errors, 2 live-only
  tests skipped by default
- Live PostgreSQL product, TLS, five-migration history/checksum, exact
  26-table inventory, and composite ownership-constraint audit
- Two production-profile Neon starts with unchanged Flyway history
- Disposable live authentication lifecycle and scoped cleanup
- Real SMTP registration plus anonymized activation/session/audit verification
- Real SMTP password-reset request, completion, one-time-value consumption,
  password-identity update, and pre-reset session revocation
- Desktop launcher health, authentication-provider, JAR, Java, and process
  checks against the production-profile server on port 18080
- An anonymized recovery diagnostic confirms the recovered account is
  verified, ACTIVE, unlocked, and also has a same-email local account
- Explicit CLOUD/LOCAL sign-in routing, including the same-email regression
  test; `ant test-auth` and `ant clean jar` pass
- Separate **Sign In to CLOUD** and **Sign In to LOCAL** actions replace the
  ambiguous checkbox/generic-button interaction
- Successful post-reset login, clear cloud lock state, and active CLOUD
  session verified by the anonymized live audit
- `server\mvnw.cmd package` passes after the recovery audit addition
- Five local OWNER finance files match the pre-online-auth backup byte-for-byte
- Identical database data-count fingerprint before and after live testing
- PowerShell syntax checks and `git diff --check`

## Tests failed or pending

- Docker `hello-world`: failed with Docker Desktop engine HTTP 500
- Docker clean relaunch: failed with WSL VHDX attach error `0x800705aa`
- The same-email LOCAL OWNER reached the intended temporary lockout after
  repeated attempts with the cloud replacement password; its original local
  password was not changed. The lock expires automatically before LOCAL-mode
  verification.
- Desktop CLOUD-mode GUI finance smoke test: pending
- Google Cloud Speech: pending Application Default Credentials
- Google OAuth: pending browser authorization
- Docker image/container, Render, web, Vercel, and GitHub-hosted CI: pending

## Manual gates

- No active manual gate. Later gates include Google authorization/billing if
  requested, Render/Vercel account connections and secret entry, and explicit
  push/merge/deploy authorization.

## Remaining stages

- Finish Stage 1 Docker stability
- Stage 4 desktop CLOUD mode
- Stage 5 Google Cloud Speech
- Stage 6 Google Sign-In
- Stage 7 administration console live verification
- Stage 8 production container and Render readiness verification
- Stage 9 Next.js web application
- Stage 10 Vercel readiness/deployment
- Stage 11 CI, repository hygiene, and final documentation
- Stage 12 final acceptance

No secret values, OTPs, tokens, passwords, email addresses, private finance
records, or local database paths are recorded in this file.
