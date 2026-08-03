# Wealthora continuation

## Current state

- Branch: `feature/wealthora-online-auth-voice`
- Recovered base HEAD: `ebd8150`
- Current checkpoint commit: `feat: complete Wealthora authentication backend`
  (this handoff is committed with it; use `git rev-parse --short HEAD` for the
  exact hash).
- Authentication foundation is complete through secure NSU registration,
  verification, default activation/login, sessions/recovery, role/workspace
  isolation, Spring Boot, Flyway V1-V4 ownership constraints, provider status,
  and honest Google OAuth preparation.
- Desktop artifact: `dist\Wealthora.jar`
- Server artifact:
  `server\target\wealthora-auth-server-1.0.0-SNAPSHOT.jar`
- Web application: not started, as required.

## Previous partial work recovered

The interrupted working tree was continued rather than discarded. Its password
policy, verification, reset-attempt, SMTP, BCrypt compatibility, connection
status, V4 migration, and tests now form one complete verified checkpoint.
No generated file, database, OWNER record, or finance workspace was reset.

## Verification summary

- `ant test-auth`: passed the complete desktop dependency chain, including
  14 policy, 22 local authentication/authorization, and 8 HTTP gateway tests.
- `ant clean jar`: passed under JDK 25.
- `server\mvnw.cmd test`: passed all 30 isolated H2/Flyway tests.
- `server\mvnw.cmd package`: passed and packaged the executable server JAR.
- `dist\Wealthora.jar`: launched with isolated temporary `LOCALAPPDATA` and
  reached a responsive authentication window.
- Live PostgreSQL/Neon: not run because this host has no PostgreSQL tooling or
  service and no database credentials. This is configuration required, not a
  simulated pass.

## Remaining configuration and limitations

- Configure database variables plus `TOKEN_PEPPER` outside source control.
- Configure all six SMTP variables for production email. Without them,
  registration/recovery remain unavailable and existing local OWNER login
  stays functional.
- Configure the three server-only Google OAuth variables. Callback:
  `GET /api/auth/google/callback`.
- Configure `WEALTHORA_SERVER_URL` for desktop online authentication.
- Server finance tables are ownership-safe, but local/cloud finance migration
  and sync have not been designed or run.
- Do not start the Next.js app until the live PostgreSQL checkpoint below is
  complete.

## Exact next smallest task

Provide or start an isolated empty PostgreSQL database, set the already
documented database variables and `TOKEN_PEPPER` only in the process
environment, then run the packaged server. Verify Flyway V1-V4, Actuator
health/readiness, `/api/auth/status`, one NSU register/verify/login flow using
the development mail sink, and one authorized admin read. Record concrete
PostgreSQL/Neon SSL or pooling gaps only if the live run exposes them. Do not
migrate desktop OWNER or finance data.

## Exact resume commands

```powershell
cd G:\Projects\SpendWiseExpenseTracker
git status -sb
git log -3 --oneline
$env:JAVA_HOME = 'C:\DevelopmentTools\jdk-25\jdk-25.0.2'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& 'C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat' test-auth
& 'C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat' clean jar
cd server
.\mvnw.cmd test
.\mvnw.cmd package
```

For the live database task, first set `DATABASE_URL`, `DATABASE_USERNAME`,
`DATABASE_PASSWORD`, and `TOKEN_PEPPER` in the current shell without writing
their values to the repository. For Neon, use a JDBC URL with
`sslmode=require`. Then:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev-mail-sink'
$env:WEALTHORA_DEV_MAIL_DIR = Join-Path $env:TEMP 'wealthora-dev-mail'
.\mvnw.cmd spring-boot:run
```

Do not commit the temporary development mail output; it contains one-time
codes and tokens. Do not push or merge.
