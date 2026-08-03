# Wealthora current implementation status

Verified on 2026-08-03 on branch `feature/wealthora-online-auth-voice`.

## COMPLETE

- Checkpoints 1-5 remain complete: NSU registration and verification, password and session security, authenticated Google Cloud Speech-to-Text V1 recognition, and verified PASSWORD/GOOGLE identity linking.
- Checkpoint 6 is complete. The Spring Boot server now provides authenticated administration endpoints for overview counts, users, pending registrations, pending verification, audit logs, security policy, application settings, and database health.
- ADMIN can approve or reject pending registrations and activate, suspend, or disable normal users. Status transitions are validated, successful changes are audited, and non-active sessions are revoked when an account is blocked.
- ADMIN cannot manage OWNER or ADMIN accounts and USER cannot use any administration operation. OWNER can manage ADMIN assignments after current-password re-authentication; no operation can remove or modify the OWNER role.
- OWNER can change the persisted registration-approval policy after password re-authentication and a recorded reason. Password and Google registration paths both read the effective policy.
- The Swing Admin Console now has working Overview, Users, Pending Registrations, Verification, Administrators, Audit Logs, Security, Application Settings, Backup and Restore, and Database Health tabs. Server calls run outside the Swing event-dispatch thread and failures remain explicit.
- Verification administration never fabricates email verification. It permits rejection only; verification continues through the existing secure email workflow.
- Backup and Restore uses the existing versioned, inspected, safety-backup-aware finance data implementation. Administration responses expose no private finance records.
- Desktop verification: `ant test-auth` passed its full dependency chain, including 14 authentication policy tests, 21 local authentication/authorization tests, and 7 online HTTP gateway tests. `ant clean jar` passed with 251 production sources.
- Server verification: `server\mvnw.cmd test` and `server\mvnw.cmd package` each passed 21 H2/Flyway tests. The focused administration suite covers USER denial, ADMIN boundaries, pending approval/rejection, account controls, OWNER re-authentication, role changes, settings, audits, and OWNER protection.
- Runtime verification: `dist\Wealthora.jar` launched from isolated temporary storage, remained responsive, and showed `Wealthora Authentication`. Administration endpoint workflows ran over live random-port HTTP servers in the integration suite.

## PARTIAL

- None in checkpoint 6 source. A live desktop-to-production admin session was not run because this host has no configured server/PostgreSQL instance; that is configuration required, not simulated.

## MISSING

- Checkpoint 7: final PostgreSQL/Neon operational readiness and live database verification.
- Checkpoint 8: explicit LOCAL/CLOUD finance migration and sync state.
- Checkpoints 9-10: Next.js frontend and Vercel preparation.

## BROKEN

- No known broken checkpoint behavior. Focused desktop tests, the desktop JAR build, all server tests, and the server package pass.

## CONFIGURATION REQUIRED

- Server runtime requires a reachable PostgreSQL database through `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. The packaged server correctly failed closed on this host because no PostgreSQL service is running at the default local address.
- The desktop needs `WEALTHORA_SERVER_URL` for online authentication and server-backed administration.
- Google OAuth requires server-only `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET`, and `GOOGLE_OAUTH_REDIRECT_URI`. Local development uses `http://127.0.0.1:8080/api/auth/google/callback`; deployment uses the HTTPS API origin with the same path.
- Live speech requires `GOOGLE_CLOUD_PROJECT=wealthora-voice`, valid Application Default Credentials, and the enabled Speech-to-Text API.
- SMTP configuration is required for production verification and password-recovery delivery. The development mail sink remains development-profile-only.

## Data safety

- Existing OWNER, authentication data, roles, finance workspaces, finance records, preferences, and backups were not modified or migrated.
- No schema or ownership change was required, so no new safety backup was created. Existing safety backup remains `C:\Users\Drakon\AppData\Local\SpendWiseExpenseTracker\backups\pre-online-auth-20260803-055935-298.zip` with SHA-256 `484BE61ABC27B2E5A06B21D5B27ACA3BEB092DDBB0E569818D50821DCEA14131`.
- Server tests use isolated H2 storage and temporary development mail files.
- Database migration level remains `google-oauth-v3`; checkpoint 6 reuses the existing `application_settings` and audit schema.

The exact next checkpoint and resume commands are maintained in `docs/NEXT_CODEX_STEPS.md`.
