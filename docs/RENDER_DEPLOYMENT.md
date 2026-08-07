# Render deployment setup

This guide covers the shared-online core deployment. The root `render.yaml`
contains the reviewed non-secret service configuration; Render prompts for the
secret values during the first Blueprint creation.

## Dashboard fields

Use these values when connecting the Git repository:

| Field | Value |
| --- | --- |
| Service Name | `wealthora-api` |
| Service type | Web Service |
| Root Directory | `server` |
| Language / Runtime | Docker |
| Dockerfile Path | `./Dockerfile` |
| Docker Build Context Directory | `.` |
| Docker Command | Leave blank; use the Dockerfile `ENTRYPOINT` |
| Health Check Path | `/actuator/health` |
| Auto-Deploy | After CI Checks Pass, once the deployment is approved |

The Dockerfile and build context paths are relative to the configured
`server` root directory. Docker services do not use a separate Build Command.
Render supplies `PORT` to web services; the production profile binds to that
value on `0.0.0.0`.

## Required environment variables

Add these as secret environment values in the Render dashboard:

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `WEALTHORA_OWNER_NAME`
- `WEALTHORA_OWNER_EMAIL`
- `WEALTHORA_OWNER_PASSWORD`

Use a JDBC PostgreSQL URL. A Neon URL must include `sslmode=require` or a
stricter verified TLS mode. Keep the username and password out of the URL.
`render.yaml` generates a unique `TOKEN_PEPPER` and fixes the production
profile, registration approval, and no-SMTP verification policy.

The OWNER bootstrap creates the protected initial OWNER only when the database
has no OWNER role. If the configured email already belongs to an active,
verified password account, it grants OWNER only when the configured password
matches that account; it never resets the password. It never changes an
existing OWNER. New public registrations can create only USER accounts. After
the first successful startup, the three OWNER bootstrap variables can be
removed from Render; the stored BCrypt-backed identity and roles remain in
PostgreSQL.

Do not configure `WEALTHORA_DEV_MAIL_DIR` or activate `dev-mail-sink` on
Render. SMTP and Google OAuth are deliberately not configured for this
milestone. Do not pass secrets as Docker build arguments.

## Safe startup expectations

On every new instance:

1. Required configuration resolves before the server becomes ready.
2. PostgreSQL connects with the SSL behavior in `DATABASE_URL`.
3. Flyway validates existing checksums and applies only pending V1-V5
   migrations under its migration lock.
4. Hibernate validates the mapped schema and never creates or drops it.
5. The one-time OWNER bootstrap either finds an existing OWNER or creates the
   configured OWNER without exposing credentials.
6. The health endpoint returns success before Render sends traffic.

If database access, migration validation, or schema validation fails, the
instance must fail startup. Fix the configuration or add a new forward-only
migration; never clean or recreate a production schema.

The desktop defaults to `https://wealthora-api.onrender.com`. Developers can
override it with `WEALTHORA_SERVER_URL` for an HTTPS staging service or an HTTP
localhost server. Repeat the registration, finance, isolation, and Admin
Console smoke checks before announcing availability.
