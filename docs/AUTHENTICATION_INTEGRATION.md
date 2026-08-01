# Future Authentication Integration

SpendWise currently remains a local-first desktop application. The Phase 1
interface does not require a login, accept credentials, or simulate an
authenticated user. The `Local workspace` label in the top bar describes this
state and is intentionally not an account control.

When the real Spring Boot backend is introduced, keep authentication behind
small Swing-facing boundaries:

- `com.spendwise.auth.ui.AuthFrame` owns registration, verification, login,
  password-reset, and system-browser Google sign-in screens.
- `com.spendwise.auth.AuthService` exposes asynchronous authentication
  operations and delegates network calls to the API client. It must never
  contain hardcoded users or secrets.
- `com.spendwise.auth.SessionManager` keeps short-lived access tokens in memory
  and stores refresh credentials only through an approved operating-system
  credential store. It publishes session-state changes to the application
  shell.
- `com.spendwise.api.SpendWiseApiClient` is the only HTTP boundary. It applies
  timeouts, maps backend errors to user-safe messages, refreshes expired
  sessions through `SessionManager`, and never logs tokens or passwords.

The production startup sequence should become:

```text
SpendWiseApplication
  -> load local preferences and encrypted session metadata
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
