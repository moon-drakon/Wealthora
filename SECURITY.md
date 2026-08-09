# Security policy

## Reporting a vulnerability

Report suspected vulnerabilities privately to the repository maintainers. Do
not open a public issue containing passwords, access values, finance records,
email codes, signing secrets, SMTP credentials, local backups, or private user
data. Use a minimal reproduction with temporary records.

This student project does not promise a formal response-time service level.

## Supported version

Security fixes target the current default branch and current presentation
release. Old commits, local modifications, and unofficial binaries are not
separately supported.

## Data handling

- Mutable authentication and finance state stays below the project's ignored
  `data/` directory.
- Passwords and recovery answers use BCrypt over a SHA-256 pre-hash; plaintext
  values are not persisted.
- Finance repositories are isolated per verified local user.
- Existing sign-in, finance, administration, backup, import/export, voice
  parsing, and recovery-question reset are offline.
- The desktop sends only email/purpose/challenge/code fields to the configured
  OTP relay after an explicit user action.
- The standalone relay keeps only keyed code digests and reads SMTP/HTTPS
  secrets from its process environment.
- The Windows one-click configurator stores the Gmail App Password and local
  relay signing secret only as Windows DPAPI `CurrentUser` ciphertext under
  `%LOCALAPPDATA%\Wealthora\`. It validates replacements before atomically
  changing the prior configuration and has no plaintext fallback.
- Launchers pass secrets to the relay only through its child-process environment,
  never through command-line arguments, tracked files, or logs. The persistent
  user environment contains only the non-secret loopback relay URL needed by
  NetBeans F6.
- Never attach local CSV data, backups, logs, keystores, environment files, or
  screenshots containing private information to issues or pull requests.

Production relay deployment must use HTTPS with a trusted hostname-valid
certificate. Its SMTP connection uses STARTTLS with endpoint identification.
Plain HTTP is restricted to explicit loopback-only development mode.

The DPAPI configuration is tied to the current Windows user and computer. Run
the configuration launcher again after changing Windows accounts, reinstalling
Windows, or moving the project to another computer. Never copy
`otp-relay-config.json` into Git or a submission package, even though its secret
fields are encrypted.
