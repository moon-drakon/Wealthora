# Wealthora current implementation status

Verified on 2026-08-03 on branch `feature/wealthora-online-auth-voice`.

## COMPLETE

- Checkpoints 1-4 remain complete: NSU registration and verification, password security, opaque rotating sessions, desktop account/session UI, and authenticated Google Cloud Speech-to-Text V1 microphone recognition.
- Checkpoint 5 implementation is complete: Continue with Google starts a backend-controlled OAuth 2.0/OpenID Connect authorization-code flow and opens the system browser. Google credentials and authorization codes never enter Swing.
- The server validates a one-time state and nonce, the Google signature, issuer, audience, expiry, verified email, hosted-domain claim, exact `northsouth.edu` email domain, and stable Google subject before account access.
- OAuth flow records retain only HMAC hashes of the state, nonce, and desktop polling secret. The desktop receives a normal Wealthora opaque session once through the polling handoff; completed flows cannot be consumed twice.
- Existing password accounts link to the validated Google subject only when the exact email matches. Google-first accounts can add a password through the existing authenticated Set Password flow, and responses report `LOCAL`, `GOOGLE`, or `LOCAL_AND_GOOGLE` from the stored identities.
- Duplicate Google subjects, cross-account email/subject conflicts, invalid callbacks, and suspended or disabled accounts are rejected. Google-first registration follows the existing administrator-approval policy.
- Continue with Google runs outside the Swing event-dispatch thread and shows an honest configuration or flow error. The desktop also rejects non-HTTPS/non-Google authorization URLs returned by the server.
- Desktop verification: `ant test-voice` passed the full prerequisite chain, including 23 voice tests, 14 authentication policy tests, 21 local authentication tests, and 6 HTTP gateway tests; `ant clean jar` passed with 247 production sources.
- Server verification: `server\mvnw.cmd clean package` passed 18 H2/Flyway tests, including password-to-Google linking, Google-first Set Password, tampered state, wrong domain/poll secret, one-time polling, duplicate prevention, and suspended-user checks.

## PARTIAL

- None in checkpoint 5 source. A real Google consent round trip was not exercisable on this host because its OAuth configuration is absent; this is classified below rather than simulated.

## MISSING

- Checkpoint 6: remaining server-backed Admin Console functions, beginning with pending-registration review and approval/rejection.
- Checkpoint 7: final PostgreSQL/Neon operational readiness.
- Checkpoint 8: explicit LOCAL/CLOUD finance migration and sync state.
- Checkpoints 9-10: Next.js frontend and Vercel preparation.

## BROKEN

- No known broken checkpoint behavior. Existing focused desktop and server suites pass.

## CONFIGURATION REQUIRED

- Google OAuth needs server-only `GOOGLE_OAUTH_CLIENT_ID` and `GOOGLE_OAUTH_CLIENT_SECRET`, plus `GOOGLE_OAUTH_REDIRECT_URI`. The same redirect URI must be registered exactly in the Google Cloud OAuth web client.
- For local server development, the exact redirect URI is `http://127.0.0.1:8080/api/auth/google/callback`. A deployment must use its HTTPS API origin with the same `/api/auth/google/callback` path.
- The desktop needs `WEALTHORA_SERVER_URL` pointing to the configured server. It stores no Google client secret and requests no Google password.
- This host has no server URL, OAuth client ID, OAuth client secret, or OAuth redirect environment value. Wealthora must therefore report Google Sign-In as unavailable here.
- Speech configuration remains separate: `GOOGLE_CLOUD_PROJECT=wealthora-voice`, valid Application Default Credentials, and the enabled Speech-to-Text API are required for live transcription.

## Data safety

- Existing OWNER, authentication data, finance workspaces, and finance records were not modified or migrated.
- Existing safety backup: `C:\Users\Drakon\AppData\Local\SpendWiseExpenseTracker\backups\pre-online-auth-20260803-055935-298.zip`
- Backup SHA-256: `484BE61ABC27B2E5A06B21D5B27ACA3BEB092DDBB0E569818D50821DCEA14131`
- Server tests use isolated H2 storage and temporary development mail files.
- Authentication migrations are `authentication-foundation-v1`, `password-security-v2`, and `google-oauth-v3`. No local finance schema or data was changed.

The exact next checkpoint and resume commands are maintained in `docs/NEXT_CODEX_STEPS.md`.
