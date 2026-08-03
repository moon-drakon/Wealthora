# Wealthora architecture

## System shape

Wealthora currently has two executable components:

```text
Java Swing desktop
  |-- local authentication and per-user CSV finance workspace
  |-- finance models, repositories, services, reports, backup, and export
  `-- HTTPS/loopback client for online authentication and speech
                         |
                         v
Spring Boot server
  |-- registration, verification, login, recovery, and opaque sessions
  |-- USER, ADMIN, and OWNER authorization plus audit events
  |-- Google OAuth and Google Cloud Speech provider boundaries
  `-- owner-scoped finance APIs on PostgreSQL managed by Flyway V1-V5
```

There is no web client yet. The desktop remains in the repository root; the
server is the separate Maven module under `server/`.

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

UI classes call services rather than editing CSV files directly. Repository
writes use same-directory temporary files and replacement after validation.
Each authenticated local user resolves to an isolated finance workspace.

CLOUD repositories are stateless HTTP adapters. During Swing workspace
construction, a narrowly scoped read snapshot coalesces identical GETs issued
by panels that need the same startup data. The snapshot ends as soon as frame
construction completes; ordinary refreshes then return to live server reads,
so this optimization cannot hide another device's later changes.

## Server layers

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

LOCAL desktop finance data stays in the operating system's application-data
area and is not copied into the server. CLOUD sessions use HTTP repository
adapters and PostgreSQL for the authenticated user's finance workspace.
PostgreSQL also stores online users, identities, roles, verification/reset
records, sessions, login attempts, audit entries, settings, and OAuth flows.

Flyway owns schema evolution. Migrations V1-V5 are forward-only, and Hibernate
uses `ddl-auto=validate`, so application startup does not recreate the schema.
V4 establishes user-owned accounts, categories, and transactions. V5 adds the
cloud finance, planning, goal, debt, and transfer model. Composite foreign
keys reject cross-user references.

## Trust boundaries

- Real credentials enter through process environment variables or a deployment
  secret store, never through committed configuration.
- Remote desktop connections require HTTPS; loopback HTTP is development-only.
- Online authorization is enforced by the server. An ADMIN does not receive
  cross-user finance access, and only the OWNER can manage ADMIN roles.
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
