# Wealthora architecture

## Runtime components

```text
Java Swing desktop
  |-- local authentication, roles, audit, and recovery-question reset
  |-- one CSV finance workspace per verified local user
  |-- finance, reports, backup, import, export, and presentation data
  |-- optional Windows offline speech recognition
  `-- OTP gateway (send/resend/verify requests only)
               |
               `-- standalone Java OTP relay -- STARTTLS SMTP provider
```

The desktop remains useful without a network. Existing sign-in, finance,
administration, import/export, backup, reports, voice parsing, and offline
recovery do not call the relay. Network activity happens only after an explicit
email-code action.

## Desktop layers

- `model`: validated finance domain objects.
- `repository`: interfaces and UTF-8 CSV implementations with safe replacement.
- `service`: finance use cases, reporting, portability, presentation records,
  and transaction coordination.
- `auth`: local BCrypt credentials, roles, sessions, audit, recovery, and the
  narrow `EmailVerificationGateway` boundary.
- `ui`: programmatic Swing windows, panels, tables, dialogs, and Java2D charts.
- `voice`: provider abstraction, command parsing, and optional Windows offline
  recognition with confirm-before-save behavior.

Swing classes call services rather than writing CSV directly. Services validate
input before repository mutation. Repository writes use a same-directory
temporary file, flush it, and replace the destination.

## Portable persistence

`AppPaths` locates the project through `build.xml` and
`nbproject/project.xml`, or through an explicit `wealthora.project.root` system
property / `WEALTHORA_PROJECT_ROOT` environment value. All mutable state is
under `<project-root>/data`:

```text
data/
  auth/          local user registry and audit history
  users/<id>/    isolated finance CSV workspace
  backups/       user-created local backups
  settings/      portable migration decisions
  presentation/  per-user seeded-record manifests
```

An exclusive file lock prevents concurrent processes from owning this folder.
Startup verifies write access before authentication initializes. Older
OS-specific data is only a migration source: the user must approve the copy,
staged content is parsed before installation, existing portable data blocks the
flow, and a failed copy rolls back files installed by that attempt.

## Authentication transactions

The first OWNER is an offline bootstrap. Ordinary registration follows:

```text
validate local fields -> request REGISTRATION code -> verify challenge
-> hash password/recovery answer -> prepare user workspace -> atomically save user
```

Pending registration state is in memory and contains a one-way password hash
plus validated profile inputs until verification, cancellation, expiry, or
application exit. Password character arrays are cleared after hashing. No local
user is created on send, resend, failed verification, expiry, or mail-delivery
failure.

Email password reset uses a `PASSWORD_RESET` challenge. The old BCrypt hash is
replaced only after code verification and a successful atomic repository save.
The offline recovery-question path remains independent of the relay.

## Relay trust boundary

The relay receives only normalized email, purpose, opaque challenge ID, and—on
verification—the six-digit code. It never receives passwords, recovery answers,
roles, sessions, or finance data. The relay:

- generates codes and challenge identifiers with `SecureRandom`;
- retains only an HMAC-SHA-256 code digest in process memory;
- performs constant-time digest comparison;
- binds each challenge to email and purpose;
- enforces expiry, attempt, resend, per-email, and per-IP limits;
- returns generic errors and never logs codes or SMTP credentials;
- requires HTTPS except for explicit loopback-only development mode;
- upgrades SMTP using STARTTLS with certificate hostname validation;
- selects a registration or password-reset email from the bound OTP purpose;
- sends UTF-8 `multipart/alternative` mail with plain-text and responsive HTML
  table-layout bodies, escaped dynamic content, and no external resources.

Relay restart intentionally invalidates all pending challenges. Shared durable
challenge storage is future scope if multiple relay instances are ever needed.

## Windows launcher boundary

The root CMD files contain no credential logic. They resolve their companion
PowerShell scripts relative to `%~dp0`, so project paths may contain spaces and
may be on any drive. The internal launcher module:

- validates Gmail credentials with STARTTLS authentication without sending an
  email before saving or replacing configuration;
- stores one atomic JSON document under `%LOCALAPPDATA%\Wealthora\`, with the
  App Password and relay signing secret protected by Windows DPAPI for the
  current user;
- detects Java in bundled `runtime\bin`, then `JAVA_HOME`, then `PATH`;
- verifies both distribution JARs, prevents duplicate relays through the exact
  loopback health contract, and tracks process ownership for cleanup; and
- places secrets only in the relay child environment. The desktop child receives
  only the non-secret relay origin.

The non-secret loopback origin is also saved in the Windows user environment so
NetBeans can pass it to an F6-launched desktop after NetBeans is restarted once.
No application data, user password, recovery answer, OTP, or finance record is
handled by these launchers.

## Presentation-data ownership

Presentation records are never added automatically. An OWNER selects
**Presentation Data → Load Presentation Data**. Fixed identifiers and a strict
per-user manifest record only items actually created by that action. Removal
deletes those recorded transactions and archives only recorded accounts whose
identifier and expected display name still match. Name or identifier collisions
that predate loading are preserved. Corrupt or unexpected manifest content
fails closed.
