# Wealthora authentication setup

Wealthora keeps the Java 25 Swing/Ant desktop build separate from the Spring
Boot server in `server/`. Existing OWNER login and local finance data continue
to work when the server is offline. Online registration and password login are enabled only when
the desktop process has `WEALTHORA_SERVER_URL` configured.

## Server requirements

- JDK 25
- PostgreSQL with an empty Wealthora database and a restricted application user
- SMTP credentials for production email verification
- A random `TOKEN_PEPPER` of at least 32 characters stored outside source control

Set the variables named in `server/.env.example` through PowerShell, the IDE,
or a secret manager. Do not rename `.env.example` to a tracked file containing
real credentials.

Build and test:

```powershell
$env:JAVA_HOME = 'C:\DevelopmentTools\jdk-25\jdk-25.0.2'
cd G:\Projects\SpendWiseExpenseTracker\server
.\mvnw.cmd clean package
```

Run with PostgreSQL and SMTP configured:

```powershell
.\mvnw.cmd spring-boot:run
```

The server applies Flyway migrations V1-V4 for authentication, password
security, Google OAuth, and the user-owned finance schema, and exposes
only `health` and `info` Actuator endpoints. The current registration policy is
controlled by `REGISTRATION_REQUIRES_ADMIN_APPROVAL` and defaults to `false`.
Access tokens expire after 15 minutes and refresh tokens after 30 days by
default. `ACCESS_TOKEN_EXPIRY`, `REFRESH_TOKEN_EXPIRY`,
`LOGIN_LOCK_DURATION`, and `MAXIMUM_FAILED_LOGIN_ATTEMPTS` can change those
values using ISO-8601 durations and a positive attempt count.
`PASSWORD_RESET_EXPIRY`, `PASSWORD_RESET_REQUEST_COOLDOWN`, and
`MAXIMUM_PASSWORD_RESET_ATTEMPTS` control reset token lifetime, per-account
request cooldown, and the single-token attempt limit.

The implemented authentication endpoints are:

- `POST /api/auth/register`, `/verify-email`, and `/resend-verification`
- public `GET /api/auth/status` for non-secret email/Google availability
- `POST /api/auth/login` and `/refresh`
- `POST /api/auth/forgot-password` and `/reset-password`
- authenticated `GET /api/auth/me`
- authenticated `POST /api/auth/change-password`, `/set-password`, `/logout`, and `/logout-all`
- authenticated `GET /api/auth/sessions` and `DELETE /api/auth/sessions/{sessionIdentifier}`

Access and refresh values are random opaque tokens. Only HMAC-SHA-256 hashes
are stored in PostgreSQL. Refresh rotates both tokens, reuse of a consumed
refresh token revokes its session, and five failed password attempts trigger a
15-minute account lock by default. New password hashes use BCrypt cost 12 over
a SHA-256 pre-hash so the 128-character policy is safe from BCrypt truncation;
legacy BCrypt hashes remain valid.

For Neon, keep `DATABASE_URL` in JDBC form and require TLS, for example
`jdbc:postgresql://HOST/DATABASE?sslmode=require`. The database username and
password remain separate environment variables. Flyway applies only forward
V1-V4 migrations, while Hibernate runs with `ddl-auto: validate`; the server
does not recreate or reset a schema automatically.

## Development-only mail sink

The `dev-mail-sink` profile is an explicit local testing facility. It writes a
verification or password-reset message into `WEALTHORA_DEV_MAIL_DIR` instead
of sending SMTP. Those files contain one-time codes/tokens, are ignored by Git, and must never be used
in production, attached to issues, or copied into backups.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev-mail-sink'
$env:WEALTHORA_DEV_MAIL_DIR = 'G:\Projects\SpendWiseExpenseTracker\server\dev-mail'
.\mvnw.cmd spring-boot:run
```

PostgreSQL and `TOKEN_PEPPER` are still required in this profile. Automated
tests use isolated H2 storage and a temporary mail directory.

## Desktop registration and login

Start the server first, then use a new PowerShell window:

```powershell
cd G:\Projects\SpendWiseExpenseTracker
$env:APP_OWNER_EMAIL = 'shibli.moon.253@northsouth.edu'
$env:WEALTHORA_SERVER_URL = 'http://localhost:8080'
& 'C:\DevelopmentTools\jdk-25\jdk-25.0.2\bin\java.exe' -jar '.\dist\Wealthora.jar'
```

HTTP is accepted only for loopback development. A non-loopback server URL must
use HTTPS. Create Account produces a pending user, delivers a six-digit
one-time code, verifies the email, and activates the user under the default
policy. Optional administrator approval can instead leave the user in
`PENDING_APPROVAL`. Registration never starts a session before activation.

An `ACTIVE`, verified online account can sign in from the same screen. The
desktop keeps its access and refresh tokens only in process memory, sends them
only to the configured HTTPS/loopback server, rotates them through the refresh
endpoint, and revokes the server session on Sign Out or Switch Account. It does
not persist a bearer token for Remember Me yet. Local OWNER sign-in remains the
offline fallback and is selected before online authentication for that OWNER
email.

To require approval after verification, set
`REGISTRATION_REQUIRES_ADMIN_APPROVAL=true` before starting the server or use
the OWNER-protected Application Settings control. The normal student-project
flow keeps this disabled.

Password recovery and authenticated password/session management use the same
configured server connection. Forgot-password responses are generic, reset
tokens are single-use HMAC-hashed values, and successful password changes
revoke every active session. The signed-in offline OWNER can also change the
local BCrypt password; email recovery applies only to online accounts.

Google Sign-In uses the server callback
`GET /api/auth/google/callback` and remains honestly unavailable until the
server-only OAuth variables are configured; no success is simulated.
Production email delivery similarly remains unavailable until `SMTP_HOST`,
`SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM_ADDRESS`, and
`SMTP_FROM_NAME` are supplied. The desktop reports these provider states
separately from basic server connectivity.
