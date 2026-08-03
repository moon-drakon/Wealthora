# Wealthora continuation

## Current state

- Branch: `feature/wealthora-online-auth-voice`
- Baseline HEAD: `8b2b226`
- Current HEAD: the verified online-session checkpoint commit containing this document; confirm with `git rev-parse --short HEAD`
- Completed checkpoints: 0 (baseline verification), 1 (verified NSU Create Account), and the password-login/opaque-session subcheckpoint
- Spring Boot checkpoint: registration and core session APIs are complete; recovery, OAuth, administration, speech, and sync APIs remain
- Java: Microsoft OpenJDK `25.0.2`
- Desktop build: `ant clean jar` passed after compiling 236 production sources
- Desktop tests: `ant test-auth` passed the full prerequisite chain, 13 policy tests, and 19 local/hybrid authentication/authorization tests
- Server build: `server\mvnw.cmd clean package` passed on Spring Boot 4.1.0 and Java 25
- Server tests: registration plus real HTTP login, `/me`, refresh rotation/replay, lockout evidence, and logout tests passed with H2/Flyway
- Migration version: `authentication-foundation-v1`

## Completed in this checkpoint

- Create Account is enabled and includes full name, exact NSU email, optional Student ID, strong password/confirmation, live strength feedback, terms acceptance, Google placeholder, and Back to Sign In.
- Registration and verification run off the Swing Event Dispatch Thread with clear working, failure, cooldown, pending-verification, and pending-approval states.
- Desktop server URLs require HTTPS except loopback HTTP development and are read only from `WEALTHORA_SERVER_URL`.
- Existing local OWNER login remains operational if the server is missing or offline.
- Separate Spring Boot 4.1.0/Maven Wrapper module targets Java 25 and includes Web MVC, Security, Validation, Data JPA, PostgreSQL, Flyway, restricted Actuator, Mail, and tests.
- Flyway creates provider-neutral users, identities, roles, user roles, email verification, reset token, session, refresh token, login attempt, audit, settings, and migration tables.
- Registration normalizes exact `northsouth.edu`, prevents duplicates, requires terms, rejects weak/common/admin-style passwords, and stores BCrypt cost-12 hashes.
- Verification uses a cryptographically generated eight-digit code and stores only an HMAC-SHA-256 hash protected by `TOKEN_PEPPER`.
- SMTP is the production delivery boundary. Missing delivery configuration fails honestly; the explicit `dev-mail-sink` profile is documented and never logs a code.
- Five wrong verification attempts, ten-minute expiry, one-minute resend cooldown, configurable post-verification approval, and safe audit events are implemented.
- Registration responses never contain passwords, password hashes, or verification codes.
- Password login enforces verified `ACTIVE` status, a generic failure message, BCrypt verification, five-attempt/15-minute default lockout, hashed attempted-email/remote-address evidence, and audit events.
- Server sessions use random opaque access/refresh tokens with only HMAC hashes stored. Refresh rotates both values; consumed-token replay revokes the session.
- Authenticated `/api/auth/me`, `/logout`, and `/logout-all` are guarded by a stateless bearer filter. Debug representations redact verification and session secrets.
- The desktop performs server I/O off the Swing EDT, keeps tokens in memory only, revokes them on logout/switch-account, and creates a separate finance workspace for an online user.
- The local OWNER account remains preferred for its email and continues to work without the server.

## Safety and data preservation

- Pre-stage safety backup: `C:\Users\Drakon\AppData\Local\SpendWiseExpenseTracker\backups\pre-online-auth-20260803-055935-298.zip`
- Backup SHA-256: `484BE61ABC27B2E5A06B21D5B27ACA3BEB092DDBB0E569818D50821DCEA14131`
- Existing local authentication database, OWNER account, finance workspace, and backups were not modified by this checkpoint.
- Server tests use only in-memory H2 data and a temporary development mail directory.
- No passwords, hashes, SMTP credentials, OAuth secrets, tokens, databases, finance records, backups, or audio are tracked.

## Remaining checkpoints

1. Complete forgot/reset/change/set-password and session list/revocation UI/API.
2. Add authenticated Java Sound capture and backend Google Cloud Speech-to-Text V2 status/recognition endpoints.
3. Add real system-browser Google OAuth and same-email identity linking.
4. Complete Pending Registrations, Verification, Security, Settings, Backup, and Database Health Admin Console functions.
5. Add explicit LOCAL/SERVER finance migration and sync status without mixing data.
6. Finish provider/team setup documents and safe helper scripts.

Known limitations:

- Users verified under the default policy remain `PENDING_APPROVAL`; the server approval API/UI belongs to the Admin Console checkpoint.
- Password recovery, password change, persisted Remember Me, and user-visible session management are not implemented yet.
- Google OAuth and microphone recognition remain honestly unconfigured.
- Additional verified users can sign into My Finance only after becoming `ACTIVE`; the default policy still requires the future Admin Console approval flow.
- No checkpoint source files are partially implemented.

## Exact next task

Implement password security and recovery end to end: generic forgot-password,
single-use hashed reset tokens with expiry, SMTP/dev-sink reset delivery,
reset/change/set-password rules, revoke-all-sessions after password changes,
authenticated session listing and single-session revocation, and real Swing
recovery/security UI running network work off the EDT. Preserve local OWNER
fallback and do not expose whether an email exists.

## Exact resume commands

```powershell
git status -sb
git log -3 --oneline
$env:JAVA_HOME = 'C:\DevelopmentTools\jdk-25\jdk-25.0.2'
& 'C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat' test-auth
& 'C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat' clean jar
cd server
.\mvnw.cmd clean package
```

## Ready-to-paste continuation prompt

Continue Wealthora on `feature/wealthora-online-auth-voice` from the verified online password-session checkpoint. Read `docs/NEXT_CODEX_STEPS.md`, inspect the clean HEAD once, and implement the exact next task: password recovery/change plus session-list/revocation API and Swing UI. Preserve local OWNER fallback, online opaque-token rotation, user finance workspaces, exact `northsouth.edu` policy, BCrypt hashes, HMAC token hashing, pending-approval policy, and all local data. Never reveal account existence, fake Google or speech recognition, expose tokens/hashes, commit secrets or databases, push, or merge. Run `ant test-auth`, `ant clean jar`, and `server\mvnw.cmd clean package`; commit only verified work and update this file.
