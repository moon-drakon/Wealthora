# Local development

## Requirements

- JDK 25
- Apache Ant for the desktop build
- PostgreSQL for a real server run
- Docker only when testing the deployment image

The Maven Wrapper is committed under `server/`; a separate Maven installation
is not required. Confirm the active tools before building:

```text
java -version
javac -version
ant -version
```

## Desktop

From the repository root:

```text
ant test-auth
ant clean jar
java -jar dist/Wealthora.jar
```

Set `APP_OWNER_EMAIL` before the first local OWNER setup. Set
`WEALTHORA_SERVER_URL=http://127.0.0.1:8080` only when a local server is
running. The desktop accepts loopback HTTP for development and requires HTTPS
for non-loopback servers.

For runtime checks, point `LOCALAPPDATA` or the equivalent platform data root
at a new temporary directory. Never test startup against another user's real
finance workspace.

## Server tests

From `server/`:

```text
./mvnw test
./mvnw package
```

Use `mvnw.cmd` on Windows. Tests activate isolated H2 storage in PostgreSQL
compatibility mode and a temporary development mail directory. They do not
require production secrets.

## Server with PostgreSQL

Copy the variable names from `.env.example` into the current process
environment. At minimum, provide:

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `TOKEN_PEPPER` with at least 32 characters

Use an empty, isolated database. For Neon, the URL must be JDBC form and use
TLS, for example `jdbc:postgresql://HOST/DATABASE?sslmode=require`. Start the
server from `server/`:

```text
./mvnw spring-boot:run
```

Production email additionally requires all six `SMTP_*` values shown in the
example. Without them, the server reports email delivery as unavailable.

## Explicit development mail sink

For local end-to-end authentication without SMTP, activate only the
`dev-mail-sink` profile and set `WEALTHORA_DEV_MAIL_DIR` to a new temporary
directory:

```text
SPRING_PROFILES_ACTIVE=dev-mail-sink
WEALTHORA_DEV_MAIL_DIR=<temporary-directory>
```

The output contains one-time verification codes and reset tokens. Do not use
this profile in production, commit its files, attach them to issues, or include
them in backups.

## Local endpoints

- Health: `GET http://127.0.0.1:8080/actuator/health`
- Provider status: `GET http://127.0.0.1:8080/api/auth/status`
- Google callback: `GET http://127.0.0.1:8080/api/auth/google/callback`

See `docs/AUTHENTICATION_SETUP.md` for the endpoint and security behavior.
