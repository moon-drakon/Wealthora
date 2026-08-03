# Wealthora autonomous execution state

Verified on 2026-08-04.

## Current stage

- Branch: `feature/wealthora-online-auth-voice`
- Recorded implementation HEAD: `febb7b5`
- Active stage: Stage 5, live Google Cloud Speech authorization and verification
- Exact resume point: after the user completes Google Application Default
  Credentials authorization and replies `ready`, verify ADC without printing
  tokens, set the quota project if required, enable/check Speech-to-Text V1,
  run the production provider and real-microphone flow, then continue through
  OAuth, administration, container, web, CI, and final acceptance

## Completed stages

- Stage 0 repository and documentation recovery
- Stage 2 live Neon PostgreSQL and Flyway V1-V5 validation
- Stage 3 disposable development-mail authentication lifecycle
- Stage 3 real SMTP registration, OTP consumption, activation, and sign-in
- Stage 3 real SMTP password recovery and post-reset CLOUD sign-in
- Stage 4 desktop CLOUD finance, isolation, persistence, session, transport,
  Swing construction, cleanup, and LOCAL-data-preservation verification
- Stage 5 non-manual Speech V1 implementation, desktop workflow, privacy
  hardening, cancellation behavior, provider tests, and Google Cloud CLI setup

Stage 1 is partially complete: the Windows restart, WSL checks, and Docker
Client/Server checks passed. `hello-world` and Docker stability did not pass
because Docker's WSL VHDX could not be attached under current host resource
pressure. No destructive Docker recovery was attempted.

## Tests passed

- `ant clean jar test-voice`: 24 voice tests and the full dependency chain
- `server\mvnw.cmd package`: 39 tests, 0 failures, 0 errors, 2 live-only
  tests skipped by default
- Live PostgreSQL product, TLS, five-migration history/checksum, exact
  26-table inventory, and composite ownership-constraint audit
- Two production-profile Neon starts with unchanged Flyway history
- Disposable live authentication lifecycle and scoped cleanup
- Real SMTP registration plus anonymized activation/session/audit verification
- Real SMTP password-reset request, completion, one-time-value consumption,
  password-identity update, and pre-reset session revocation
- Explicit CLOUD/LOCAL sign-in routing, including the same-email regression
  test, separate buttons, and post-reset CLOUD session verification
- Synthetic live CLOUD account/category/income/expense/transfer operations,
  expense update/delete, monthly budget, recurring entry, goal, debt,
  dashboard, and report totals against the real Neon-backed server
- Normal USER administration restriction, second-user finance isolation,
  fresh-gateway login persistence, and logout token clearing
- Real Swing CLOUD workspace construction with correct connected-mode copy and
  startup-scoped duplicate-read coalescing
- Server-stop `SERVER_UNAVAILABLE` handling and session/data recovery after a
  production-profile backend restart
- Scoped cleanup removed both generated users and temporary mail/fixture
  state without changing an existing user row
- Five local OWNER finance files match the pre-online-auth backup byte-for-byte
- Desktop and server low-memory launchers, PowerShell syntax checks, and
  `git diff --check`
- Authenticated Speech V1 status/recognition endpoints, invalid-audio safety,
  success/failure buffer wiping, manual fallback, explicit confirm-before-add,
  microphone status, 30-second timeout, and cancellation interrupt handling
- Official Google Cloud CLI 578.0.0 installed and executable; project
  configuration is present and ADC is intentionally still absent

## Tests failed or pending

- Docker `hello-world`: failed with Docker Desktop engine HTTP 500
- Docker clean relaunch: failed with WSL VHDX attach error `0x800705aa`
- Google Cloud Speech: non-manual work complete; live recognition awaits ADC
  account authorization and any required Cloud billing/API approval
- Google OAuth: pending browser authorization and live linking verification
- Administration console live verification: pending
- Docker image/container, Render, Next.js web, Vercel, and GitHub-hosted CI:
  pending

## Manual gates

Active gate: Google account browser authorization must create Application
Default Credentials for the configured project. No account credential, token,
or generated ADC file is requested or stored in the repository. Later gates
may include Cloud billing/API approval, OAuth console changes, Render/Vercel
connections, and explicit push, merge, or deployment authorization.

## Remaining stages

- Finish Stage 1 Docker stability when host resources permit
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
