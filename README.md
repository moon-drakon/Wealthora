# Wealthora — Personal Expense Tracker

[![Desktop CI](https://github.com/moon-drakon/Wealthora/actions/workflows/desktop-ci.yml/badge.svg?branch=main)](https://github.com/moon-drakon/Wealthora/actions/workflows/desktop-ci.yml)

**CSE 215 Final Project — Group 5**

## Course Project Information

| Field | Details |
| --- | --- |
| Course | CSE 215 — Object-Oriented Programming |
| Section | 11 |
| Faculty | SAM3 |
| Registered Topic | Personal Expenses Tracker |
| Project Title | Wealthora — Personal Expense Tracker |

### Team Members

| Name | Student ID |
| --- | --- |
| Shibli Rahman Moon | 2534187012 |
| Md. Monimul Haque | 1821781042 |
| Md. Nafij Jaman Rabbi | 2513403642 |

## Overview

Wealthora is a Java 25 Swing desktop application for personal finance tracking.
It uses an offline-first architecture with local authentication, isolated
per-user CSV workspaces, service-layer validation, repository-based persistence,
and an optional narrowly scoped OTP relay for email verification.

The source code on the `main` branch is the primary reference for code review.

## Main Features

- Dashboard, income, expenses, transfers, accounts, categories, and search
- Budgets, recurring entries, reports, savings goals, loans, and debts
- Per-user CSV finance workspaces
- Local authentication with BCrypt-protected credentials and recovery answers
- Email OTP verification for registration and email password reset
- Offline recovery-question reset when email delivery is unavailable
- OWNER/ADMIN/USER authorization and audit history
- Backup/restore, validated CSV import, PDF/CSV export, and Money Manager import
- Optional Windows offline voice quick entry with review before save
- Light/dark themes and currency settings

## Architecture

The main application flow is:

```text
Swing UI -> Service -> Model -> Repository -> CSV
```

- `ui` contains Swing frames, panels, dialogs, tables, and charts.
- `service` contains application use cases and business coordination.
- `model` contains validated domain objects.
- `repository` defines persistence contracts and CSV implementations.
- `auth` contains local authentication, roles, sessions, audit, recovery, and OTP boundaries.
- `config` contains project-local path and runtime configuration.
- `voice` contains speech-provider abstractions and quick-entry parsing.

Swing classes call services rather than writing CSV files directly. Services
validate and coordinate operations, while repositories handle persistence.

See [Architecture](docs/ARCHITECTURE.md) for the detailed runtime design.

## Project Structure

```text
src/               Java Swing application source code
test/              desktop tests
otp-relay/         standalone Java OTP relay and tests
lib/               required libraries and notices
nbproject/         NetBeans Ant project metadata
docs/              architecture, OOP, security, and submission documentation
presentation-data/ non-sensitive presentation seed metadata
data/              generated local user state (ignored by Git)
```

The main source packages are under:

```text
src/com/spendwise/
  app/
  auth/
  config/
  imports/
  model/
  repository/
  service/
  ui/
  validation/
  voice/
```

## Requirements

- JDK 25
- Apache Ant for command-line builds/tests
- Apache NetBeans is optional; the repository contains a standard Ant project

Required libraries and license notices are already under `lib/`.

## Build, Test, and Run

From the project root:

```powershell
ant clean test-quality jar
java -jar dist\Wealthora.jar
```

In NetBeans:

1. Open the project.
2. Select **Clean and Build Project**.
3. Select **Run Project**.

The desktop JAR is generated at `dist/Wealthora.jar`.

## OOP Highlights

- **Abstraction:** `Transaction` defines shared transaction state and behavior;
  repository, export, voice, and OTP interfaces hide implementation details.
- **Inheritance:** `Income` and `Expense` extend `Transaction`.
- **Polymorphism:** both subclasses override `calculateImpact()`, while finance
  calculations call it through `Transaction` references.
- **Encapsulation:** domain state is private and protected by constructors,
  validation, and service methods.
- **Interfaces:** repository, export, speech-recognition, and OTP contracts allow
  implementations to vary independently.
- **Swing GUI:** typed table models, listeners, dialogs, panels, and Java2D
  charts implement the desktop interface.

See [OOP Mapping](docs/OOP_MAPPING.md) for the production class-to-requirement map.

## Data and Persistence

All mutable state is stored below the project-local `data/` directory:

```text
data/
  auth/          local user registry and audit history
  users/<id>/    isolated finance CSV workspace
  backups/       user-created backups
  settings/      local settings and migration decisions
  presentation/  presentation-data manifests
```

Only one Wealthora process can own the project data folder at a time. Repository
writes use safe replacement so the UI does not edit CSV files directly.

## Authentication and OTP Boundary

Normal finance use is offline. Existing users can sign in, manage finance data,
run reports, import/export, create backups, and use offline recovery without a
network connection.

Email registration and email password reset optionally use the standalone Java
OTP relay. The desktop does not store SMTP credentials and sends no finance data
to the relay. Relay secrets are configured outside tracked source files.

See [OTP Relay Setup](docs/OTP_RELAY_SETUP.md) and [Security Policy](SECURITY.md).

## Windows Quick Start

For a prebuilt Windows bundle, the repository includes launchers for the desktop
and optional OTP relay configuration:

- `Configure Wealthora OTP.cmd`
- `Start Wealthora.cmd`
- `Start OTP Relay for NetBeans.cmd`

For source-code review or development, NetBeans or the Ant commands above are the
recommended paths.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [OOP Mapping](docs/OOP_MAPPING.md)
- [OTP Relay Setup](docs/OTP_RELAY_SETUP.md)
- [GitHub and Submission Guide](docs/GITHUB_AND_SUBMISSION_GUIDE.md)
- [Security Policy](SECURITY.md)
- [OOP Requirement Traceability](docs/final/Wealthora_OOP_Requirement_Traceability.md)

These documents support architecture review, OOP viva preparation, security
review, and source-code traceability. Presentation-day logistics are intentionally
kept out of the main source README.

## Optional Release Downloads

GitHub releases may contain prebuilt runnable artifacts for convenience. For
source-code inspection, use the current `main` branch of this repository.

## Future Scope

- Durable shared OTP challenge storage for multiple relay nodes
- Provider-specific SMTP adapters and delivery observability
- Opt-in cross-device synchronization with conflict handling
- Additional import formats and accessibility improvements

No license is declared by this repository. Add one only after the project owner
chooses and approves its terms.
