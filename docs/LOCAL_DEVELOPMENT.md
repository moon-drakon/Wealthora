# Local development

## Requirements

- JDK 25
- Apache Ant for the desktop build
- Windows with an installed speech language for offline voice recognition

PostgreSQL and Docker are needed only when separately studying the
experimental server module. Google Cloud CLI is not needed by the released
desktop application.

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
ant clean test-quality jar
java -jar dist/Wealthora.jar
```

No environment variables are required. The first local OWNER email is entered
in the setup screen.

For runtime checks, point `LOCALAPPDATA` or the equivalent platform data root
at a new temporary directory. Never test startup against another user's real
finance workspace.

Verify the real Windows offline speech path without a microphone or network:

```text
ant test-windows-offline-speech
```

## Experimental server tests

From `server/`:

```text
./mvnw test
./mvnw package
```

Use `mvnw.cmd` on Windows. Tests activate isolated H2 storage in PostgreSQL
compatibility mode and a temporary development mail directory. They do not
require production secrets.

## Experimental server with PostgreSQL

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

## Legacy cloud speech experiment

The current desktop does not use this path; it uses Windows offline speech.
The opt-in legacy server test uses Google Cloud Speech-to-Text V1. Set
`GOOGLE_CLOUD_PROJECT` and authorize developer Application Default Credentials
outside the repository:

```text
gcloud auth application-default login
gcloud auth application-default set-quota-project wealthora-voice
gcloud services enable speech.googleapis.com --project wealthora-voice
```

The Google account must already be allowed to use the configured project.
Never commit the generated ADC file, print its access token, or copy it into
desktop configuration. The server reports unavailable status when ADC, API
access, or project configuration is missing; typed parsing remains available.

On Windows, the opt-in live verifier uses a disposable CLOUD user and an
external environment file, prefers a Stereo Mix/loopback capture endpoint,
requires an explicit editable draft before confirmation, and deletes the
fixture automatically:

```powershell
.\scripts\Test-WealthoraLiveSpeech.ps1 `
  -EnvironmentFile '<external-environment-file>' `
  -JavaHome $env:JAVA_HOME
```

The exact environment and JDK paths are local examples, not repository
configuration. The test records no transaction and must never be run against
an environment where its scoped synthetic-user cleanup is not permitted.

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
