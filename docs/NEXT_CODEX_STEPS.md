# Wealthora continuation

## Current state

- Branch: `feature/wealthora-online-auth-voice`
- Checkpoint base: `b6fa3ca`
- Current HEAD/new commit: `feat: add verified Google account linking` (this document is committed with it; confirm the hash with `git rev-parse --short HEAD`)
- Checkpoint completed: 5, real system-browser Google OAuth with safe PASSWORD/GOOGLE identity linking
- Java: OpenJDK `25.0.2`
- Desktop tests: `ant test-voice` passed its full chain, including 23 voice tests and 6 HTTP gateway tests
- Latest desktop build: `ant clean jar` passed with 247 production sources; output `dist\Wealthora.jar`
- Server tests/build: `server\mvnw.cmd clean package` passed 18 H2/Flyway tests and produced the Spring Boot JAR
- Live OAuth: configuration required on this host; no consent round trip was fabricated
- Web build: not applicable; `web/` has not been started
- Database migration version: `google-oauth-v3`

## Completed in this checkpoint

- Added backend-owned Google OAuth status, authorization start, callback, and one-time desktop polling endpoints.
- Added official Google authorization-code exchange and ID-token verification for signature, issuer, audience, expiry, nonce, verified email, `hd`, exact `northsouth.edu`, and stable `sub`.
- Added expiring OAuth flow persistence containing HMAC hashes rather than raw state, nonce, or polling secrets.
- Added transactional same-account linking by validated Google subject and exact email, with duplicate/conflict checks and existing account-status enforcement.
- Added Google-first registration under the current approval policy and retained the existing authenticated Set Password path, so an account can safely have both PASSWORD and GOOGLE identities.
- Added system-browser launch and background polling in both Swing authentication screens. The desktop accepts only an HTTPS `accounts.google.com` authorization URL and receives normal opaque Wealthora tokens only after a successful callback.
- Added endpoint and desktop gateway coverage for successful linking, Google-first password addition, invalid domain/state/secret, duplicate prevention, suspended accounts, browser launch, and single-use session delivery.

## Files changed

- Server OAuth: Google configuration, official identity gateway, flow domain/repository/service/controller, public security matchers, response identity mapping, Flyway V3 migration, environment example, and dependency configuration.
- Desktop OAuth: registration gateway browser/polling boundary, system browser launcher, local auth delegation, Google status, and Sign In/Sign Up background UI flows.
- Tests: server OAuth endpoint integration coverage and desktop HTTP gateway browser-flow coverage.
- Documentation: `README.md` and both continuation documents.

## Known limitations and configuration

- Live Google consent is CONFIGURATION REQUIRED. Configure a Google OAuth web client on the server with `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET`, and `GOOGLE_OAUTH_REDIRECT_URI`.
- Register the redirect URI exactly. Local development uses `http://127.0.0.1:8080/api/auth/google/callback`; deployment uses `https://<api-host>/api/auth/google/callback`.
- Set `WEALTHORA_SERVER_URL` for the desktop. No Google secret belongs in the Swing environment, source, or JAR.
- Default verified registrations, including Google-first accounts, remain pending until an administrator approves them when `REGISTRATION_REQUIRES_ADMIN_APPROVAL=true`.
- Remember Me still does not persist online tokens. Existing speech configuration requirements remain unchanged.
- Existing OWNER, local authentication data, finance workspaces, finance records, and backups were not changed or migrated.

## Exact next incomplete checkpoint

Checkpoint 6: remaining server-backed Admin Console functions.

## Exact smallest next task

Add an authenticated server contract for administrators to list pending verified registrations and approve or reject one account, with OWNER/ADMIN authorization, account-status validation, audit records, and endpoint tests. Then connect only that pending-registration slice to the existing desktop Admin Console; do not redesign or repeat its completed local administration features.

## Exact resume commands

```powershell
git status -sb
git log -3 --oneline
$env:JAVA_HOME = 'C:\DevelopmentTools\jdk-25\jdk-25.0.2'
& 'C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat' test-voice
& 'C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat' clean jar
cd server
.\mvnw.cmd clean package
```

## Ready-to-paste continuation prompt

Continue Wealthora on `feature/wealthora-online-auth-voice` from the verified Google OAuth identity-linking checkpoint. Read `docs/CURRENT_IMPLEMENTATION_STATUS.md` and `docs/NEXT_CODEX_STEPS.md`, inspect the clean HEAD once, and resume only checkpoint 6's exact smallest task: authenticated server-backed pending-registration listing and approval/rejection, followed by that narrow integration into the existing desktop Admin Console. Preserve the OWNER, all finance workspaces, OAuth validation/linking, BCrypt/HMAC protections, opaque session rotation, pending-approval policy, typed voice fallback, and confirm-before-save. Never expose secrets/tokens/audio, commit credentials/databases, repeat completed local admin features, push, or merge. Run focused desktop/server tests, `ant clean jar`, and `server\mvnw.cmd clean package`; create one verified commit and update both handoff documents.
