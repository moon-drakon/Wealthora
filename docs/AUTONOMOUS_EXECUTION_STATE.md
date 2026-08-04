# Wealthora autonomous execution state

Verified on 2026-08-04.

## Current stage

- Branch: `feature/wealthora-online-auth-voice`
- Recorded implementation HEAD: `0d14fe7`
- Active stage: Stage 6, Google OAuth linking and session verification
- Exact resume point: register the exact local callback on the configured
  Google OAuth web client, then rerun real authorization/linking without
  exposing tokens and verify repeat sign-in and password-identity coexistence.
  Continue through administration, container, web, CI, and acceptance after
  that proof. The automated redirect, claim, duplicate-linking, and
  client-secret audits are complete.

## Completed stages

- Stage 0 repository and documentation recovery
- Stage 2 live Neon PostgreSQL and Flyway V1-V5 validation
- Stage 3 disposable development-mail authentication lifecycle
- Stage 3 real SMTP registration, OTP consumption, activation, and sign-in
- Stage 3 real SMTP password recovery and post-reset CLOUD sign-in
- Stage 4 desktop CLOUD finance, isolation, persistence, session, transport,
  Swing construction, cleanup, and LOCAL-data-preservation verification
- Stage 5 Google Speech V1 implementation, locale/parser coverage, desktop
  workflow, privacy hardening, live provider/microphone recognition, and
  automatic scoped cleanup

Stage 1 is partially complete: the Windows restart, WSL checks, and Docker
Client/Server checks passed. `hello-world` and Docker stability did not pass
because Docker's WSL VHDX could not be attached under current host resource
pressure. No destructive Docker recovery was attempted.

## Tests passed

- `ant clean jar test-voice`: 24 voice tests and the full dependency chain
- `server\mvnw.cmd package`: 42 tests, 0 failures, 0 errors, 2 live-only
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
  configuration, quota project, Application Default Credentials, and the
  Speech V1 API are ready
- Production-profile live speech passed provider readiness, real Windows
  loopback capture, English recognition, multilingual parser handoff, complete
  editable draft validation, and confirm-before-save. It created no finance
  record and removed exactly one synthetic user with zero fixtures absent.
- Seven Google OAuth endpoint tests cover first-time and existing users,
  issuer, audience, expiry, verified-email, hosted-domain, nonce, and subject
  rejection, plus prevention of subject reassignment and second-subject links.
- The configured local redirect is the exact documented loopback callback.
  Tracked files and every desktop JAR entry contain neither the configured
  client secret nor a desktop OAuth-secret setting.
- A connected in-app browser reached the official Google authorization page.
  Google returned `redirect_uri_mismatch`, proving the exact local callback is
  absent from the configured client's authorized redirect URI list. The
  pending flow was not linked and was removed exactly with its temporary
  identifier file.

## Tests failed or pending

- Docker `hello-world`: failed with Docker Desktop engine HTTP 500
- Docker clean relaunch: failed with WSL VHDX attach error `0x800705aa`
- Google OAuth: automated verification complete; real browser authorization
  reached Google and is blocked by the OAuth client's missing local redirect
  registration
- Administration console live verification: pending
- Docker image/container, Render, Next.js web, Vercel, and GitHub-hosted CI:
  pending

## Manual gates

The active Stage 6 gate is a Google Cloud developer-account action. Sign in to
`https://console.cloud.google.com/apis/credentials`, open the OAuth 2.0 **Web
application** client whose ID matches the server-only configuration, preserve
its existing entries, add
`http://127.0.0.1:8080/api/auth/google/callback`, confirm the documented
production callback is also present, and save. The in-app browser is connected
but is not signed into Google Cloud; Chrome and Edge do not have the ChatGPT
browser extension. No account credential, token, generated ADC file, captured
microphone audio, or synthetic mail value is stored in the repository.

## Remaining stages

- Finish Stage 1 Docker stability when host resources permit
- Stage 6 Google Sign-In
- Stage 7 administration console live verification
- Stage 8 production container and Render readiness verification
- Stage 9 Next.js web application
- Stage 10 Vercel readiness/deployment
- Stage 11 CI, repository hygiene, and final documentation
- Stage 12 final acceptance

No secret values, OTPs, tokens, passwords, email addresses, private finance
records, or local database paths are recorded in this file.
