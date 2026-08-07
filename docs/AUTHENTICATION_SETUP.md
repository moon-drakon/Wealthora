# Wealthora authentication setup

The released Java 25 Swing application uses local authentication only. It
supports first-run OWNER setup, local user registration, protected recovery
answers, administrator-assisted reset, BCrypt password hashing, lockout, audit
events, and one isolated CSV finance workspace per user. It needs no server,
email service, environment variable, database, or internet connection.

The Spring Boot module in `server/` is experimental future work. It is not
started or contacted by the current desktop release.

## Experimental server requirements

- JDK 25
- PostgreSQL with an empty Wealthora database and a restricted application user
- SMTP credentials for production email verification
- A random `TOKEN_PEPPER` of at least 32 characters stored outside source control

Set the variables named in the root `.env.example` through the current process,
the IDE, or a secret manager. Do not rename `.env.example` to a tracked file
containing real credentials.

Build and test:

```powershell
cd server
.\mvnw.cmd clean package
```

Run with PostgreSQL and SMTP configured:

```powershell
.\mvnw.cmd spring-boot:run
```

The server applies Flyway migrations V1-V5 for authentication, password
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
V1-V5 migrations, while Hibernate runs with `ddl-auto: validate`; the server
does not recreate or reset a schema automatically.

## Development-only mail sink

The `dev-mail-sink` profile is an explicit local testing facility. It writes a
verification or password-reset message into `WEALTHORA_DEV_MAIL_DIR` instead
of sending SMTP. Those files contain one-time codes/tokens, are ignored by Git, and must never be used
in production, attached to issues, or copied into backups.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev-mail-sink'
$env:WEALTHORA_DEV_MAIL_DIR = Join-Path $env:TEMP 'wealthora-dev-mail'
.\mvnw.cmd spring-boot:run
```

PostgreSQL and `TOKEN_PEPPER` are still required in this profile. Automated
tests use isolated H2 storage and a temporary mail directory.

## Released desktop authentication

Run the desktop normally:

```powershell
java -jar '.\dist\Wealthora.jar'
```

1. On first launch, create the primary OWNER with an exact
   `@northsouth.edu` email, strong password, recovery question, safe hint, and
   recovery answer.
2. Later users choose **Create Account** and receive the `USER` role plus an
   isolated local finance directory. No email verification is claimed because
   this is an offline desktop project.
3. **Forgot Password?** shows only the stored question and non-secret hint.
   The answer is normalized and checked against a BCrypt-protected hash; it is
   never displayed or stored in plaintext.
4. Five failed sign-in or recovery attempts cause a 15-minute local lockout.
5. An OWNER or ADMIN can reset a normal user's password from **Admin Console →
   Users** after confirming the administrator's own password and recording an
   audit reason. Only the OWNER can reset another ADMIN; the OWNER account must
   use its own recovery answer or Security settings.

Passwords and recovery answers use BCrypt cost 12 over a SHA-256 pre-hash.
Existing 17-column local-user CSV files remain readable and are upgraded to the
recovery-aware schema on the next account save.

## Experimental online boundary

The unused server module retains endpoints, migrations, OAuth, mail, and token
experiments for future study. They are not part of the offline JAR or teacher
demo, and setting `WEALTHORA_SERVER_URL` does not activate them in this release.
Never place OAuth, SMTP, database, or token secrets in the desktop JAR.
