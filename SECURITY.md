# Security policy

## Reporting a vulnerability

Please report suspected vulnerabilities privately to the repository
maintainers. Do not open a public issue containing passwords, access tokens,
personal finance records, one-time codes, database URLs, or other private data.
Include a minimal reproduction and the impact without using a real user's
records.

This student project does not promise a formal response-time service level.

## Supported version

Security fixes target the current default branch and the latest teacher-demo
release. Old commits, local builds, and unofficial binaries are not separately
supported.

## Local data handling

- The teacher release works offline and does not require cloud credentials.
- Authentication records and finance files are stored in per-user local
  application-data directories.
- Passwords are stored as BCrypt hashes rather than plain text.
- Do not attach local CSV data, backups, logs, crash reports, or screenshots
  containing private finance information to issues or pull requests.
- Use isolated temporary data when testing persistence, import, backup, or
  restore behavior.

Cloud synchronization and server deployment are future scope and are not part
of the supported offline demonstration.
