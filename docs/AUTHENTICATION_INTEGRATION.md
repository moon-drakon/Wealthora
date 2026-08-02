# Future Authentication Integration

Wealthora remains a local-first desktop application. The Settings page opens a
desktop authentication preview, but local development does not require a login
and the preview never simulates an authenticated user. Its unconfigured API
client rejects every operation with a clear backend-configuration message.
Passwords exist only in temporary character arrays that are cleared after each
synchronous call.

When the real Spring Boot backend is introduced, keep authentication behind
small Swing-facing boundaries:

- `com.spendwise.auth.ui.AuthFrame` owns sign-in, registration, verification,
  forgot-password, reset-password, and Google sign-in preview screens.
- `com.spendwise.auth.AuthService` is the Swing-facing authentication boundary;
  `BackendAuthService` applies NSU email, password, and verification rules.
- `com.spendwise.auth.AuthApiClient` is the future transport boundary.
  `UnconfiguredAuthApiClient` performs no network activity and never reports
  success.
- `com.spendwise.auth.SessionManager` currently keeps only a verified
  `UserSession` in memory. A future token implementation must use an approved
  operating-system credential store and must never log tokens or passwords.

The production startup sequence should become:

```text
SpendWiseApplication
  -> load local preferences and protected session metadata
  -> SessionManager attempts a backend refresh
     -> valid session: construct SpendWiseFrame and synchronize data
     -> no session: show AuthFrame
  -> AuthService completes email/password or browser-based Google OAuth
  -> backend verifies the email and configured allowed domain
  -> SessionManager publishes the authenticated session
  -> construct SpendWiseFrame and start synchronization
```

Google OAuth must open the user's system browser and return through a loopback
redirect or backend-approved device flow. The backend must verify the Google ID
token, issuer, audience, email verification status, and the configurable
allowed domain (initially `northsouth.edu`). The Swing client must not embed an
OAuth client secret or infer authorization from an email string.

Keep the current service and repository interfaces available during the backend
transition. A later API-backed repository layer can implement the same
application operations, while the existing CSV repositories remain the offline
cache until synchronization and conflict rules are tested. Database, SMTP,
OAuth, JWT, and refresh-token secrets belong in backend environment variables or
a deployment secret manager and must never enter this repository.
