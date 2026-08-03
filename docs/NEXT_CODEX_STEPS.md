# Wealthora continuation

## Current state

- Branch: `feature/wealthora-online-auth-voice`
- Checkpoint base: `0f7791c`
- Current HEAD: the verified password-security checkpoint commit containing this document; confirm with `git rev-parse --short HEAD`
- Completed checkpoints: baseline verification, verified NSU Create Account, password login/opaque sessions, and password recovery/security/session management
- Spring Boot checkpoint: registration, core sessions, password recovery, password changes, and user session management are complete; OAuth, administration, speech, and sync APIs remain
- Java: Microsoft OpenJDK `25.0.2`
- Desktop build: `ant clean jar` passed after compiling 237 production sources
- Desktop tests: `ant test-auth` passed its full prerequisite chain, 14 policy/UI tests, 21 local/hybrid authentication/authorization tests, and 4 real HTTP gateway tests
- Server build: `server\mvnw.cmd clean package` passed on Spring Boot 4.1.0 and Java 25
- Server tests: 11 H2/Flyway tests passed, including real HTTP recovery, password change/set, session listing/revocation, token rotation/replay, lockout, logout, and registration
- Migration version: `password-security-v2`

## Completed in this checkpoint

- Forgot-password always returns a generic accepted response and does not reveal whether an account exists.
- Reset tokens are 256-bit random values; only HMAC-SHA-256 hashes are stored. Tokens expire, are single-use, observe a request cooldown, and invalidate older outstanding tokens.
- Production reset delivery uses SMTP. The explicit `dev-mail-sink` profile writes development-only reset messages without logging tokens.
- Reset, change, and set-password endpoints enforce matching strong passwords, reject password reuse where applicable, and revoke all user sessions after success.
- A second Flyway migration enforces one identity per provider/user and unique reset-token hashes.
- Authenticated users can list active sessions, identify the current session, revoke one owned session, or sign out all sessions.
- The desktop HTTP gateway implements recovery, password security, session listing, DELETE revocation, and logout-all while keeping access/refresh tokens only in memory.
- Forgot/reset panels and every security/session operation run blocking work outside the Swing Event Dispatch Thread.
- The Security and Sessions dialog now provides password change/set controls, a real session table, single-session revocation, refresh, and sign-out-all.
- Local OWNER and local user password changes remain supported with BCrypt, revoke the local in-memory session, and do not alter finance data. Local session listing/revocation also works.
- Desktop and server password policies reject weak, common, and administrator-themed new passwords.

## Safety and data preservation

- Pre-stage safety backup remains: `C:\Users\Drakon\AppData\Local\SpendWiseExpenseTracker\backups\pre-online-auth-20260803-055935-298.zip`
- Backup SHA-256: `484BE61ABC27B2E5A06B21D5B27ACA3BEB092DDBB0E569818D50821DCEA14131`
- Existing local authentication data, OWNER account, finance workspace, and backups were not read-modified or migrated by this checkpoint.
- Server tests used only in-memory H2 data and a temporary development mail directory.
- No passwords, hashes, SMTP credentials, OAuth secrets, tokens, databases, finance records, backups, or audio are tracked.

## Remaining checkpoints

1. Add authenticated Java Sound capture and backend Google Cloud Speech-to-Text V2 status/recognition endpoints.
2. Add real system-browser Google OAuth and same-email identity linking.
3. Complete Pending Registrations, Verification, Security, Settings, Backup, and Database Health Admin Console functions.
4. Add explicit LOCAL/SERVER finance migration and sync status without mixing data.
5. Finish provider/team setup documents and safe helper scripts.

Known limitations:

- Users verified under the default policy remain `PENDING_APPROVAL`; the server approval API/UI belongs to the Admin Console checkpoint.
- Google OAuth and microphone recognition remain honestly unconfigured.
- Remember Me does not persist server tokens.
- Forgot-password targets online accounts. The offline local OWNER can change a password while signed in, but has no email-based offline recovery channel.
- The set-password UI/API is ready for a future authenticated Google-only identity, but Google OAuth is not implemented yet.
- No checkpoint source files are partially implemented.

## Exact next task

Implement authenticated voice recognition end to end: capture microphone audio
with Java Sound off the EDT, provide cancel/retry and explicit recording state,
send bounded audio only over the authenticated server connection, and add
backend Google Cloud Speech-to-Text V2 provider status and recognition
endpoints with honest unconfigured behavior. Preserve the existing typed voice
parser/review/confirm-before-save flow, local OWNER fallback, all finance data,
and the current password/session guarantees.

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

Continue Wealthora on `feature/wealthora-online-auth-voice` from the verified password recovery/security/session-management checkpoint. Read `docs/CURRENT_IMPLEMENTATION_STATUS.md` and `docs/NEXT_CODEX_STEPS.md`, inspect the clean HEAD once, and implement only the exact next task: authenticated Java Sound capture plus backend Google Cloud Speech-to-Text V2 status/recognition endpoints. Preserve local OWNER fallback, all local finance workspaces, typed voice parsing and confirm-before-save, exact `northsouth.edu` policy, BCrypt/HMAC protections, opaque session rotation, and pending-approval policy. Never fake recognition, expose tokens/hashes/audio, commit credentials/databases, push, or merge. Run `ant test-auth`, relevant voice tests, `ant clean jar`, and `server\mvnw.cmd clean package`; commit only verified work and update both handoff documents.
