# Backend deployment

Only the Spring Boot server is prepared for deployment in this release. The
Swing desktop remains a local application, and no Next.js frontend exists yet.
No deployment is performed automatically by this repository.

## Production contract

The container starts the packaged server JAR with the `prod` Spring profile.
That profile:

- requires `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`;
- relies on the existing startup validation that requires a `TOKEN_PEPPER` of
  at least 32 characters;
- binds to `0.0.0.0` on `PORT`, with `10000` as the container fallback;
- validates Flyway migrations and disables Flyway clean;
- keeps Hibernate on `ddl-auto=validate` with DDL generation disabled;
- exposes health without component details; and
- uses graceful shutdown with a bounded shutdown phase.

SMTP and Google OAuth remain optional capabilities. The public provider-status
endpoint reports them as unavailable until their complete configuration is
present.

## Database

Use a new PostgreSQL database and a restricted application user. Keep the
username and password in separate environment variables. Neon URLs must use
JDBC form with TLS, for example:

```text
jdbc:postgresql://HOST/DATABASE?sslmode=require
```

Flyway applies V1-V4 during startup before the service accepts traffic. A
second startup validates the recorded checksums. Never edit an applied
migration, enable Flyway clean, or point a verification run at the desktop's
local finance data.

## Build the image

Run from `server/`:

```text
docker build --tag wealthora-server:local .
```

The multi-stage build uses the Maven Wrapper. The runtime stage contains only
the JRE and packaged server JAR, and runs as numeric user and group `10001`.
It does not copy test output, local databases, mail-sink output, audio, desktop
data, or environment files.

## Run the image locally

Set required variables in the host process, then pass their names to Docker so
their values do not appear in the command line:

```text
docker run --rm --name wealthora-server \
  --publish 8080:10000 \
  --env DATABASE_URL \
  --env DATABASE_USERNAME \
  --env DATABASE_PASSWORD \
  --env TOKEN_PEPPER \
  wealthora-server:local
```

Add the SMTP or Google variables in the same name-only form when testing those
providers. Do not place secret values in Docker build arguments or image
layers.

After startup, check `GET http://127.0.0.1:8080/actuator/health` and
`GET http://127.0.0.1:8080/api/auth/status`. A failed database connection or
invalid migration prevents startup; health responses and API errors do not
include component details or configured secret values.

## Release handling

Generated desktop and server JARs are ignored by Git. Publish reviewed release
artifacts through CI or GitHub Releases instead of committing binaries. See
`docs/RENDER_DEPLOYMENT.md` for the Render dashboard values.
