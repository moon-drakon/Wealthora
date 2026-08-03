# Wealthora

Wealthora is a local-first personal finance application built for a CSE215
Java object-oriented programming semester project. Its primary interface is a
programmatic Swing desktop application. A separate Spring Boot server provides
online NSU account registration, authentication, administration, Google OAuth
integration, and Google Cloud Speech integration.

The project was previously named SpendWise. Existing application-data paths
retain that name so upgrades do not silently abandon local finance records.

## Features

The desktop application provides:

- income, expense, transfer, account, category, and payment-card management;
- monthly and category budgets, savings goals, debts, recurring entries, and
  explicit due-entry generation;
- dashboard charts, calendar activity, account statements, portfolio views,
  and filtered reports;
- English, Bangla, and Banglish Quick Entry with editable voice transcripts;
- CSV import/export, validated ZIP and JSON backup/restore, and PDF summaries;
- per-user local finance workspaces with offline OWNER authentication; and
- server-backed registration, login, password recovery, session management,
  Google Sign-In, and role-aware administration.

The server provides:

- exact `northsouth.edu` registration and six-digit email verification;
- an 8-128 character password policy requiring an English letter and number;
- opaque access and refresh sessions, rotation, revocation, and lockout;
- USER, ADMIN, and OWNER authorization with protected audit records;
- Flyway V1-V4 for authentication and user-owned finance tables; and
- provider status that distinguishes unavailable SMTP, OAuth, and speech
  configuration from basic server connectivity.

Cloud synchronization of desktop finance data is not implemented. The server
finance schema currently establishes database ownership constraints for later
work.

## Architecture

The repository contains two builds:

```text
src/ and test/       Java 25 Swing desktop, Ant, NetBeans project metadata
server/              Java 25 Spring Boot server, Maven Wrapper, Flyway
docs/                Architecture, authentication, development, and deployment
lib/                 Reviewed desktop runtime libraries and license notices
.github/workflows/   Desktop, server, and repository checks
```

Desktop finance data stays in isolated local CSV workspaces. The server uses
PostgreSQL for online users, identities, roles, one-time verification/recovery
records, sessions, audit events, and server-owned finance tables. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for component and trust
boundaries.

## Requirements

Desktop:

- JDK 25
- Apache Ant or Apache NetBeans with Java support

Server:

- JDK 25
- PostgreSQL or an isolated Neon database for real runs
- SMTP credentials for production registration and recovery
- Maven is not required separately; use the committed wrapper

Docker is optional for validating the server deployment image.

## Run the desktop

Set the OWNER identifier before first launch, build, and run:

```powershell
$env:APP_OWNER_EMAIL = 'owner@northsouth.edu'
ant clean jar
java -jar .\dist\Wealthora.jar
```

The first launch opens OWNER setup. Existing legacy finance CSV files are
backed up and copied byte-for-byte into that OWNER's private workspace; the
legacy originals remain unchanged.

For online authentication, start the server and set:

```powershell
$env:WEALTHORA_SERVER_URL = 'http://127.0.0.1:8080'
```

Only loopback HTTP is accepted. Remote server URLs must use HTTPS. Existing
offline OWNER access remains available when the server is not configured.

## Run the server locally

Provide the required values from [`.env.example`](.env.example) through the
current process environment or a secret manager. Do not commit a populated
`.env` file. The minimum server variables are:

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `TOKEN_PEPPER` with at least 32 characters

For Neon, use a JDBC PostgreSQL URL with TLS enabled. From `server/`:

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS or Linux, use `./mvnw`. Production registration and password recovery
also require the six `SMTP_*` variables in the example file. Local end-to-end
testing can use the explicit `dev-mail-sink` profile described in
[`docs/LOCAL_DEVELOPMENT.md`](docs/LOCAL_DEVELOPMENT.md).

## Test and build

Run the complete desktop authentication dependency chain and package the JAR:

```powershell
ant test-auth
ant clean jar
```

Run and package the server from `server/`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

The generated artifacts are `dist/Wealthora.jar` and
`server/target/wealthora-auth-server-1.0.0-SNAPSHOT.jar`. Generated artifacts
are ignored by Git; CI uploads the desktop JAR for successful workflow runs.

More focused Ant targets remain available in `build.xml`, including
`test-core`, `test-persistence`, `test-finance`, `test-reports`, `test-data`,
`test-accounts`, `test-voice`, and `test-quality`.

## Data and security notes

On Windows, local data remains under
`%LOCALAPPDATA%\SpendWiseExpenseTracker`. macOS uses Application Support, and
Linux uses `XDG_DATA_HOME` or its standard fallback. Reads and ordinary view
refreshes do not create or rewrite CSV files; successful mutations create only
the files they own.

Never commit database credentials, SMTP credentials, OAuth secrets, Google
service-account files, token peppers, local finance data, backups, mail-sink
output, recordings, or private test reports. Review
[`SECURITY.md`](SECURITY.md) before reporting a vulnerability.

## Deployment overview

The Spring Boot server is the only deployable backend component. It is prepared
for a Docker-based Render web service using the `server/` directory, the
production Spring profile, PostgreSQL/Neon, and environment-managed secrets.
Deployment is intentionally not performed from this repository run. The Swing
desktop is distributed as a CI artifact or release asset, not as a committed
binary.

## Team

- Moon
- Nafij
- Monimul

## Documentation

- [`docs/LOCAL_DEVELOPMENT.md`](docs/LOCAL_DEVELOPMENT.md)
- [`docs/AUTHENTICATION_SETUP.md`](docs/AUTHENTICATION_SETUP.md)
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`CONTRIBUTING.md`](CONTRIBUTING.md)
- [`SECURITY.md`](SECURITY.md)

No project license has been granted. Third-party component notices are recorded
in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
