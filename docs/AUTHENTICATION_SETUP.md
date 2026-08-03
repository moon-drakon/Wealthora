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

The server applies Flyway migration `authentication-foundation-v1` and exposes
only `health` and `info` Actuator endpoints. The current registration policy is
controlled by `REGISTRATION_REQUIRES_ADMIN_APPROVAL` and defaults to `true`.
Access tokens expire after 15 minutes and refresh tokens after 30 days by
default. `ACCESS_TOKEN_EXPIRY`, `REFRESH_TOKEN_EXPIRY`,
`LOGIN_LOCK_DURATION`, and `MAXIMUM_FAILED_LOGIN_ATTEMPTS` can change those
values using ISO-8601 durations and a positive attempt count.

The implemented authentication endpoints are:

- `POST /api/auth/register`, `/verify-email`, and `/resend-verification`
- `POST /api/auth/login` and `/refresh`
- authenticated `GET /api/auth/me`
- authenticated `POST /api/auth/logout` and `/logout-all`

Access and refresh values are random opaque tokens. Only HMAC-SHA-256 hashes
are stored in PostgreSQL. Refresh rotates both tokens, reuse of a consumed
refresh token revokes its session, and five failed password attempts trigger a
15-minute account lock by default. Passwords use BCrypt cost 12.

## Development-only mail sink

The `dev-mail-sink` profile is an explicit local testing facility. It writes a
verification message into `WEALTHORA_DEV_MAIL_DIR` instead of sending SMTP.
Those files contain one-time codes, are ignored by Git, and must never be used
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
use HTTPS. Create Account produces a pending user, delivers an eight-digit
one-time code, verifies the email, and leaves the user in `PENDING_APPROVAL`
under the default policy. It never starts a session before activation.

An `ACTIVE`, verified online account can sign in from the same screen. The
desktop keeps its access and refresh tokens only in process memory, sends them
only to the configured HTTPS/loopback server, rotates them through the refresh
endpoint, and revokes the server session on Sign Out or Switch Account. It does
not persist a bearer token for Remember Me yet. Local OWNER sign-in remains the
offline fallback and is selected before online authentication for that OWNER
email.

For an end-to-end local development registration without the future Admin
Console approval screen, set
`REGISTRATION_REQUIRES_ADMIN_APPROVAL=false` before starting a disposable
development server. Production should keep the default approval policy.

Google Sign-In and password recovery remain disabled until their later
checkpoints are implemented and configured; no success is simulated.
