# Security policy

## Reporting a vulnerability

Please report suspected vulnerabilities privately to the repository maintainers.
Do not open a public issue containing credentials, access tokens, personal
financial data, one-time verification codes, database URLs, or exploit details.
Include the affected component, a minimal reproduction, and the impact without
including real user data.

The maintainers will confirm receipt, investigate the report, and coordinate a
fix before public disclosure. This student project does not promise a formal
response-time service level.

## Supported code

Security fixes target the current default branch and active release work. Old
commits, local builds, and unofficial binaries are not separately supported.

## Credential handling

- Keep database, SMTP, OAuth, Google Cloud, and token-pepper values outside Git.
- Use process environment variables or the deployment platform's secret store.
- Treat development mail-sink files as sensitive because they contain one-time
  codes or reset tokens.
- Do not attach local CSV data, backups, database dumps, logs with private data,
  or audio recordings to issues or pull requests.
- Rotate an exposed credential immediately. Removing it from the latest commit
  is not enough because Git history may still contain it.

## Security boundaries

The desktop is local-first and stores finance data in per-user application-data
directories. The server owns online identity, session, role, and audit records.
The desktop/server finance synchronization boundary is not implemented. See
`docs/ARCHITECTURE.md` for the current trust boundaries and limitations.
