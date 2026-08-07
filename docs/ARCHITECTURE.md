# Wealthora architecture

## System shape

The released Wealthora application has one executable component:

```text
Java Swing desktop
  |-- local registration, authentication, recovery, and administration
  |-- isolated per-user CSV finance workspaces
  |-- finance models, services, reports, backup, import, and export
  `-- Windows offline speech recognition with confirm-before-save
```

There is no web client. The separate Maven module under `server/` and desktop
HTTP adapters are experimental future work and are not used by the release.

## Desktop layers

- `src/com/spendwise/model`: immutable or validated finance domain objects.
- `src/com/spendwise/repository`: repository interfaces and safe UTF-8 CSV
  implementations.
- `src/com/spendwise/service`: transaction, budget, reporting, recurring,
  backup, export, authentication, and administration behavior.
- `src/com/spendwise/ui`: programmatic Swing windows, panels, dialogs, tables,
  and Java2D charts.
- `src/com/spendwise/auth`: local OWNER authentication, server gateway,
  authorization, sessions, audit, and registration UI support.
- `src/com/spendwise/voice`: provider abstraction, Windows offline recognizer,
  microphone capture, and confirm-before-save command parsing.

UI classes call services rather than editing CSV files directly. Repository
writes use same-directory temporary files and replacement after validation.
Each authenticated local user resolves to an isolated finance workspace.

Every local user resolves to a stable private directory. The user registry
stores password and recovery hashes, role/status data, and lockout state; it
does not contain finance records.

## Experimental server layers

- `api`: request/response records and HTTP controllers.
- `service`: authentication, registration, recovery, OAuth, administration,
  and speech use cases.
- `domain` and `repository`: JPA entities and persistence access.
- `security`: password policy, backward-compatible BCrypt encoding, opaque
  token hashing, and stateless request authentication.
- `mail`, `oauth`, and `speech`: provider boundaries with explicit availability.
- `config`: Spring Security and typed application configuration.

The server stores only hashes of access, refresh, reset, verification, OAuth
state, and nonce values. Passwords use BCrypt cost 12 over a SHA-256 pre-hash;
legacy BCrypt hashes remain readable.

## Persistence boundaries

Desktop finance data stays in the operating system's application-data area and
is not copied into a server. The current release never opens a CLOUD session.
PostgreSQL schemas and CLOUD repositories belong to the experimental module.

Flyway owns schema evolution. Migrations V1-V5 are forward-only, and Hibernate
uses `ddl-auto=validate`, so application startup does not recreate the schema.
V4 establishes user-owned accounts, categories, and transactions. V5 adds the
cloud finance, planning, goal, debt, and transfer model. Composite foreign
keys reject cross-user references.

## Trust boundaries

- Real credentials enter through process environment variables or a deployment
  secret store, never through committed configuration.
- An ADMIN can manage account status and reset eligible passwords but never
  receives access to another user's finance workspace. Only the OWNER can
  grant or revoke ADMIN roles.
- Microphone PCM stays in memory, is passed only to the local Windows speech
  engine, and is cleared after recognition.
- Development mail output contains live one-time values and must stay outside
  source control and production.
- Google OAuth client secrets and Google Cloud credentials remain server-side.
- Live CLOUD verification uses generated disposable users and a development
  mail sink outside the repository. Cleanup is scoped to those generated user
  identifiers and temporary fixture paths, so established users and LOCAL
  OWNER finance storage are not altered.

## Current boundary

Automatic synchronization and migration from local CSV storage are not
implemented. The desktop exposes only a read-only migration preview. Any
future upload path must add explicit confirmation, verification, rollback,
conflict resolution, and offline behavior without mixing LOCAL and CLOUD data.
