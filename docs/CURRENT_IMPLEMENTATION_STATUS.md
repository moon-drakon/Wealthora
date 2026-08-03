# Wealthora current implementation status

Verified on 2026-08-03 on branch `feature/wealthora-online-auth-voice`.

## Verified checkpoint

- Desktop: Java 25, Apache Ant, programmatic Swing, 237 production sources.
- Server: Spring Boot 4.1.0, Maven Wrapper, Java 25, PostgreSQL/Flyway production boundary, H2 tests.
- Authentication migrations: `authentication-foundation-v1` and `password-security-v2`.
- Required desktop verification passed: `ant test-auth` and `ant clean jar`.
- Required server verification passed: `server\mvnw.cmd clean package`, 11 tests with no failures or errors.

## Implemented authentication

- Secure local first-OWNER setup, BCrypt password storage, lockout, authorization, audit, and offline sign-in fallback.
- Exact `northsouth.edu` online registration, eight-digit email verification, resend cooldown, pending-approval policy, and SMTP/development mail boundaries.
- Password login with generic failures, hashed login evidence, opaque access/refresh tokens, refresh rotation/replay revocation, `/me`, logout, and logout-all.
- Generic forgot-password plus expiring single-use HMAC-hashed reset tokens and configurable request cooldown.
- Reset, authenticated change, and authenticated set-password operations with strong policy, reuse rejection, and revoke-all-sessions behavior.
- Authenticated active-session listing, current-session indication, owned single-session revocation, and sign-out-all.
- Desktop recovery and security UI backed by the real HTTP gateway. All server/network work runs off the Swing EDT.
- Local password change and local session listing/revocation remain available without the server and preserve the OWNER finance workspace.

## Implemented finance and voice foundations

- Account-based income, expenses, transfers, budgets, recurring items, savings goals, debts, reports, backup/restore, imports/exports, and user-scoped local workspaces remain implemented.
- Typed English/Bangla voice parsing, draft review, and confirm-before-save remain implemented.
- Microphone capture and cloud speech recognition are not yet implemented; the application does not simulate them.

## Data safety

- Existing OWNER and finance data were not modified during this checkpoint.
- Existing safety backup: `C:\Users\Drakon\AppData\Local\SpendWiseExpenseTracker\backups\pre-online-auth-20260803-055935-298.zip`
- Backup SHA-256: `484BE61ABC27B2E5A06B21D5B27ACA3BEB092DDBB0E569818D50821DCEA14131`
- Server tests use isolated H2 storage and temporary development mail files.
- Secrets, databases, finance files, backups, tokens, reset codes, and audio remain untracked.

## Remaining work

1. Authenticated Java Sound capture and Google Cloud Speech-to-Text V2 provider status/recognition.
2. Real system-browser Google OAuth and safe same-email identity linking.
3. Server-backed Admin Console registration approval, security, settings, backup, and database health functions.
4. Explicit LOCAL/SERVER finance migration and sync state.
5. Provider and team setup documentation/scripts.

The exact next checkpoint and resume commands are maintained in `docs/NEXT_CODEX_STEPS.md`.
