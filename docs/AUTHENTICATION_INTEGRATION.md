# Wealthora Authentication Integration

## Current desktop release

Wealthora is local-first and offline. `AuthFrame` exposes local sign-in,
registration, first-run OWNER setup, and recovery. `LocalDesktopAuthService`
coordinates the CSV user repository, password and recovery hashing, per-user
workspace selection, lockout, sessions, and audit records.

Password accounts require an exact `@northsouth.edu` address. Because the
desktop has no trusted email server, a locally created account is marked active
without claiming that an email message was verified. Its scope is this Windows
user profile and this computer.

The important boundaries are:

- `AuthService` provides sign-in and authenticated account operations.
- `LocalAccountService` provides offline registration and protected-answer
  recovery.
- `OwnerSetupService` protects the one-time primary OWNER bootstrap.
- `SessionManager` keeps the current desktop session in memory.
- `AdminService` enforces OWNER/ADMIN authorization and audit reasons.
- `CsvLocalUserRepository` atomically persists hashes, roles, status, lockout,
  and recovery metadata while reading the previous schema safely.

Neither plaintext passwords nor recovery answers are written to CSV or audit
logs. Finance data remains separated by the stable local user identifier.

## Experimental online code

`BackendAuthService`, HTTP gateways, Google OAuth boundaries, and the
Spring Boot `server/` module remain future-scope experiments. The released app
does not construct a configured gateway, display cloud sign-in controls, or
contact a server. Online account linking, email verification, and cloud finance
must not be presented as current release features.
