# Render deployment setup

This is a readiness guide, not a deployment record. Create a new Render Web
Service only after an isolated PostgreSQL/Neon verification has passed.

## Dashboard fields

Use these values when connecting the Git repository:

| Field | Value |
| --- | --- |
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
- `TOKEN_PEPPER`

Use a JDBC PostgreSQL URL. A Neon URL must include `sslmode=require` or a
stricter verified TLS mode. Keep the username and password out of the URL.

For production email verification and password recovery, also add all six:

- `SMTP_HOST`
- `SMTP_PORT`
- `SMTP_USERNAME`
- `SMTP_PASSWORD`
- `SMTP_FROM_ADDRESS`
- `SMTP_FROM_NAME`

Google Sign-In remains unavailable until all three server-only values exist:

- `GOOGLE_OAUTH_CLIENT_ID`
- `GOOGLE_OAUTH_CLIENT_SECRET`
- `GOOGLE_OAUTH_REDIRECT_URI`

Set the redirect URI to the final HTTPS Render origin followed by
`/api/auth/google/callback`, and register that exact URI in the Google OAuth
client. Google Cloud Speech additionally uses `GOOGLE_CLOUD_PROJECT` and
Application Default Credentials supplied through an approved secret mechanism.

Do not configure `WEALTHORA_DEV_MAIL_DIR` or activate `dev-mail-sink` on
Render. Do not pass secrets as Docker build arguments.

## Safe startup expectations

On every new instance:

1. Required configuration resolves before the server becomes ready.
2. PostgreSQL connects with the SSL behavior in `DATABASE_URL`.
3. Flyway validates existing checksums and applies only pending V1-V4
   migrations under its migration lock.
4. Hibernate validates the mapped schema and never creates or drops it.
5. The health endpoint returns success before Render sends traffic.

If database access, migration validation, or schema validation fails, the
instance must fail startup. Fix the configuration or add a new forward-only
migration; never clean or recreate a production schema.

After the first successful deploy, set the desktop's `WEALTHORA_SERVER_URL` to
the service's HTTPS origin and repeat the authentication/administration smoke
checks before announcing availability.
