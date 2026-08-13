# Wealthora — Personal Expense Tracker

[![Desktop CI](https://github.com/moon-drakon/Wealthora/actions/workflows/desktop-ci.yml/badge.svg?branch=main)](https://github.com/moon-drakon/Wealthora/actions/workflows/desktop-ci.yml)

**For CSE 215 Presentation**

## Course Project Information

| Field | Details |
| --- | --- |
| Course | CSE 215 — Object-Oriented Programming |
| Section | 11 |
| Faculty | SAM3 |
| Registered Topic | Personal Expenses Tracker |
| Project Title | Wealthora — Personal Expense Tracker |
| Demonstration | August 16, 2026 |

### Team Members

| Name | Student ID |
| --- | --- |
| Shibli Rahman Moon | 2534187012 |
| Md. Monimul Haque | 1821781042 |
| Md. Nafij Jaman Rabbi | 2513403642 |

Wealthora is a Java 25 Swing desktop application for personal finance tracking.
It keeps authentication and finance records in an isolated, project-local data
folder, works offline for normal use, and connects to a narrowly scoped Java OTP
relay only when a user explicitly sends, resends, or verifies an email code.

## Features

- Dashboard, income, expenses, transfers, accounts, categories, and search
- Budgets, recurring entries, reports, savings goals, loans, and debts
- Per-user CSV workspaces with local BCrypt password and recovery-answer hashes
- Email OTP verification for ordinary registration and email password reset
- Offline recovery-question reset when email delivery is unavailable
- OWNER/ADMIN/USER authorization, account status controls, and audit history
- Versioned backup/restore, validated CSV import, PDF/CSV export, and Money
  Manager backup import
- Optional Windows offline voice quick entry with review before every save
- Light/dark themes, currency settings, and presentation-data controls

## Security and offline boundary

The desktop does not contain SMTP credentials and has no finance, login, or
administration API. Its only HTTP client is the OTP gateway. Without
`WEALTHORA_OTP_RELAY_URL`, ordinary email registration and email reset clearly
report that delivery is unavailable; existing users can still sign in and use
offline recovery.

The relay accepts exact `@northsouth.edu` recipients, generates six-digit codes
with `SecureRandom`, stores only keyed SHA-256 digests, and binds every challenge
to its normalized email and purpose. Challenges expire after 10 minutes, are
single-use, allow at most five failed attempts, enforce a 60-second resend
cooldown, and apply hourly email/IP request limits. Resending creates a new
challenge and invalidates the prior one only after successful SMTP delivery.

Registration and password-reset messages use separate Wealthora-branded
templates. Each is sent as UTF-8 `multipart/alternative` mail with a plain-text
fallback and a responsive HTML table layout with inline CSS. The HTML contains
no scripts, external images, remote fonts, tracking pixels, or other remote
resources, and the verification code never appears in the subject or logs.

Production relay traffic requires HTTPS. Plain HTTP is accepted only when the
relay explicitly binds to a loopback address for local development. Passwords,
recovery answers, SMTP secrets, and finance records are never sent to the relay.

## Requirements

- JDK 25 for running Wealthora
- Apache Ant only when building or testing from source
- Apache NetBeans (optional; the repository contains a standard Ant project)

The required desktop libraries and their license notices are already under
`lib/`. No Maven, database, Docker service, browser client, or web build is
required.

## Verified final release

The [latest GitHub release](https://github.com/moon-drakon/Wealthora/releases/latest)
provides the complete Windows and academic bundle, both runnable JARs, the final
six-page report, the defense presentation, and SHA-256 checksums. Source archives
remain available automatically from the same release page.

## Build, test, and run

From the project root:

```powershell
ant clean test-quality jar
java -jar dist\Wealthora.jar
```

The desktop JAR is written to `dist/Wealthora.jar`, its runtime libraries to
`dist/lib/`, and the standalone relay JAR to `dist/otp-relay/wealthora-otp-relay.jar`.
In NetBeans, use **Clean and Build Project**, then **Run Project**.

## Windows one-click Quick Start

The complete GitHub release bundle is prebuilt; Apache Ant and NetBeans are not
required for normal use:

1. On the latest release page, download **Complete Windows and academic
   bundle**—not GitHub's automatic **Source code (zip)**—and extract it fully.
2. Double-click **`Configure Wealthora OTP.cmd`** once. Enter the Gmail or
   Google Workspace sender address, an optional sender name (default:
   `Wealthora Security`), and the Google 16-character App Password privately.
   Spaces in the App Password are accepted.
3. The configurator performs a STARTTLS Gmail authentication check without
   sending mail. Only after that succeeds does it atomically save the address,
   sender name, and Windows DPAPI-encrypted credentials under
   `%LOCALAPPDATA%\Wealthora\`.
4. Double-click **`Start Wealthora.cmd`** for normal use. It reuses a healthy
   relay or starts one, waits for `/health`, launches the desktop JAR, and stops
   only the relay process it created when Wealthora closes. The extracted
   folder is a valid portable application root and stores runtime state in its
   local `data/` directory.

Subsequent normal launches—including after a computer restart—reuse the saved
Windows user-specific encrypted configuration without asking for the Gmail
address or App Password. If the configuration is missing or cannot be decrypted,
the launcher offers configuration, offline startup, or exit; it never silently
requests credentials.

To change Gmail later, run **`Configure Wealthora OTP.cmd`**, select **Replace
Gmail/App Password**, and enter the new account privately. The old working
configuration remains intact if validation fails. The same launcher also offers
confirmed removal. No plaintext credential fallback exists.

For NetBeans F6, configure once, restart NetBeans once so it inherits the saved
non-secret loopback relay URL, and run **`Start OTP Relay for NetBeans.cmd`**.
Keep that relay window open while using F6. The launcher prevents duplicates and
stops only a relay it started. See [one-click launchers](docs/ONE_CLICK_LAUNCHERS.md)
for the full workflow.

On Windows, the optional offline recognizer check is separate because it
depends on an installed Windows speech voice:

```powershell
ant test-windows-offline-speech
```

## First launch and accounts

1. Run Wealthora from the project root. Runtime state is created only below
   `<project-root>/data`.
2. On a fresh project copy, create the primary OWNER. This bootstrap stays
   offline so the application remains recoverable without email delivery.
3. Later users choose **Create Account**, receive an email code, and become a
   USER only after successful verification. No account or workspace is written
   before that verification succeeds.
4. **Forgot Password?** offers email OTP reset and the stored offline recovery
   question. A failed or interrupted reset leaves the current password valid.

Only one Wealthora process can own a project-local data folder at a time. A
second process stops with a clear message instead of risking concurrent CSV
writes. If an older OS application-data installation is detected while the new
store is empty, Wealthora asks before copying it, validates staged files, and
never overwrites existing project data.

## OTP relay setup

Build and test the standalone Java relay:

```powershell
ant compile test-otp-relay jar-otp-relay
```

Configure secrets in the relay process environment, never in tracked files:

- `WEALTHORA_OTP_SIGNING_SECRET` — at least 32 bytes
- `WEALTHORA_SMTP_HOST`, `WEALTHORA_SMTP_PORT`
- `WEALTHORA_SMTP_USERNAME`, `WEALTHORA_SMTP_PASSWORD`
- `WEALTHORA_SMTP_FROM` — optional sender mailbox
- `WEALTHORA_SMTP_FROM_NAME` — optional safe display name
- `WEALTHORA_RELAY_BIND_ADDRESS`, `WEALTHORA_RELAY_PORT`
- `WEALTHORA_RELAY_KEYSTORE`, `WEALTHORA_RELAY_KEYSTORE_PASSWORD` for HTTPS

Start the relay with:

```powershell
java -jar dist\otp-relay\wealthora-otp-relay.jar
```

Then set the desktop process to the relay origin, for example the placeholder
`https://otp.example.edu:8443`:

```powershell
$env:WEALTHORA_OTP_RELAY_URL = 'https://otp.example.edu:8443'
java -jar dist\Wealthora.jar
```

See [OTP relay setup](docs/OTP_RELAY_SETUP.md) for HTTPS, loopback testing, SMTP,
and endpoint details. This repository contains no live credentials, and a real
mailbox delivery check requires credentials supplied by the operator.

## Project structure

```text
src/               Swing application, domain, repositories, and services
test/              dependency-free desktop test programs
otp-relay/         standalone Java HTTPS/SMTP OTP component and tests
lib/               FlatLaf, BCrypt, and license notices
nbproject/         NetBeans Ant metadata
docs/              architecture, OOP, presentation, and submission guides
presentation-data/ tracked non-sensitive presentation metadata
data/              generated local user state (ignored by Git)
```

## OOP highlights

- `Transaction` is an abstract parent for `Income` and `Expense`.
- `calculateImpact()` supplies runtime polymorphism through transaction
  references.
- Private state plus validators and service methods enforce encapsulation.
- Repository, export, speech-recognition, and OTP gateway interfaces provide
  abstraction and substitutability.
- Swing panels compose services rather than editing CSV files directly.

See [OOP mapping](docs/OOP_MAPPING.md) for the viva-ready class map.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [OTP relay setup](docs/OTP_RELAY_SETUP.md)
- [OOP mapping](docs/OOP_MAPPING.md)
- [Presentation guide](docs/PRESENTATION_GUIDE.md)
- [GitHub and submission guide](docs/GITHUB_AND_SUBMISSION_GUIDE.md)
- [Security policy](SECURITY.md)
- [Final six-page academic report and defense materials](docs/final/README.md)

For the August 25, 2026 demonstration, carry a printed copy of the final
six-page A4 report. Use the configured presentation laptop for live OTP and keep
the fully offline sign-in/recovery demonstration path available.

## Future Scope

- Durable, horizontally shared OTP challenge storage for multiple relay nodes
- Provider-specific SMTP adapters and delivery observability without sensitive
  payload logging
- Cross-device sync designed as an explicit opt-in feature with conflict and
  rollback controls
- Additional import formats and accessibility refinements

No license is declared by this repository. Add one only after the project owner
chooses and approves its terms.
