# Wealthora continuation

## Current state

- Branch: `feature/wealthora-online-auth-voice`
- Baseline HEAD: `8b2b226`
- Current HEAD: the verified registration checkpoint commit containing this document; confirm with `git rev-parse --short HEAD`
- Completed checkpoints: 0 (baseline verification) and 1 (verified NSU Create Account)
- Spring Boot checkpoint: foundation created; remaining authentication/session APIs are not yet complete
- Java: Microsoft OpenJDK `25.0.2`
- Desktop build: `ant clean jar` passed after compiling 236 production sources
- Desktop tests: `ant test-auth` passed the full prerequisite chain, 13 policy tests, and 18 local authentication/authorization tests
- Server build: `server\mvnw.cmd clean package` passed on Spring Boot 4.1.0 and Java 25
- Server tests: service registration lifecycle and real HTTP registration/verification tests passed with H2/Flyway and the explicit development mail sink
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

## Safety and data preservation

- Pre-stage safety backup: `C:\Users\Drakon\AppData\Local\SpendWiseExpenseTracker\backups\pre-online-auth-20260803-055935-298.zip`
- Backup SHA-256: `484BE61ABC27B2E5A06B21D5B27ACA3BEB092DDBB0E569818D50821DCEA14131`
- Existing local authentication database, OWNER account, finance workspace, and backups were not modified by this checkpoint.
- Server tests use only in-memory H2 data and a temporary development mail directory.
- No passwords, hashes, SMTP credentials, OAuth secrets, tokens, databases, finance records, backups, or audio are tracked.

## Remaining checkpoints

1. Complete server password login, opaque access/refresh sessions, logout/logout-all, `/me`, and desktop online-user login while retaining local OWNER fallback.
2. Complete forgot/reset/change/set-password and session list/revocation UI/API.
3. Add authenticated Java Sound capture and backend Google Cloud Speech-to-Text V2 status/recognition endpoints.
4. Add real system-browser Google OAuth and same-email identity linking.
5. Complete Pending Registrations, Verification, Security, Settings, Backup, and Database Health Admin Console functions.
6. Add explicit LOCAL/SERVER finance migration and sync status without mixing data.
7. Finish provider/team setup documents and safe helper scripts.

Known limitations:

- Users verified under the default policy remain `PENDING_APPROVAL`; the server approval API/UI belongs to the Admin Console checkpoint.
- The server currently implements register, verify-email, and resend-verification only. Other reserved schema/API work is intentionally not presented as complete.
- Google OAuth and microphone recognition remain honestly unconfigured.
- Additional verified users cannot sign into My Finance until the online login/session checkpoint is complete.
- No checkpoint source files are partially implemented.

## Exact next task

Implement opaque server sessions and password login: password verification,
failed-attempt lockout, active-status enforcement, access/refresh token hashing,
refresh rotation, logout/logout-all, `/api/auth/me`, an authorization filter,
desktop token handling, and safe online-user workspace selection. Add focused
service/HTTP/desktop tests without changing the existing OWNER database.

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

Continue Wealthora on `feature/wealthora-online-auth-voice` from the verified NSU registration checkpoint. Read `docs/NEXT_CODEX_STEPS.md`, inspect the clean HEAD once, and implement the next exact task: opaque password sessions and desktop online-user login. Preserve the existing local OWNER login, user finance workspaces, logout/switch-account behavior, exact `northsouth.edu` policy, BCrypt password hashes, HMAC token hashing, pending-approval policy, and all local data. Never fake Google or speech recognition, expose tokens/hashes, commit secrets or databases, push, or merge. Run `ant test-auth`, `ant clean jar`, and `server\mvnw.cmd clean package`; commit only verified work and update this file.
