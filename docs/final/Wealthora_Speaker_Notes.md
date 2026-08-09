# Wealthora CSE 215 Project Defense - Speaker Notes

These notes match the 17-slide presentation. Each slide also contains the same concise talk track in its PowerPoint speaker-notes pane.

## Slide 1 - Wealthora

Good morning. We are presenting Wealthora for CSE 215, Section 11, under faculty SAM3, with the demonstration scheduled for August 25, 2026. Wealthora is a Java 25 Swing desktop application designed to make everyday finance tracking clear, local, and explainable through object-oriented design. Our final baseline has no functional blockers: all 47 current automated entry points passed and both distribution JARs were verified.

## Slide 2 - Problem and objectives

The problem is not just data entry; it is fragmentation. A user should be able to answer where money went, whether a budget is at risk, and what the current balance is without combining several notebooks or spreadsheets. Wealthora therefore joins finance capability, local security, user isolation, portability, and an explainable OOP structure.

## Slide 3 - User journey

Authentication selects the local account. That identifier activates a dedicated workspace. Services validate and save activity, while dashboard and report screens calculate from the same records. Backup and export protect the workspace. There is no remote finance API in this design.

## Slide 4 - Architecture

Swing panels call application services; services coordinate validated models and repository interfaces; CSV implementations persist the active workspace. Only explicit email-code actions cross into `EmailVerificationGateway`. The standalone relay has no finance, login, administration, session, role, or recovery-answer access.

## Slide 5 - OOP design

`Transaction` encapsulates common state and defines an abstract impact contract. `Income` and `Expense` inherit that state and override the behavior with positive and negative impact. `FinanceService` aggregates parent-type references without a subtype decision chain. Repository, export, speech, and OTP interfaces provide further abstraction.

## Slide 6 - Finance engine

Balances are derived from opening value and ledger activity, which prevents a second stored balance from drifting. Transfers affect two accounts but not portfolio-wide income or expense. The surrounding modules support recording, organization, planning, search, analytics, reports, and net-worth review.

## Slide 7 - Authentication

Local authentication combines institutional email validation, six-to-128-character passwords, BCrypt protection, and a lockout after five failures. Recovery answers are normalized and protected. USER, ADMIN, and OWNER roles are enforced by explicit guards, including denial of cross-user finance access.

## Slide 8 - Email OTP

Registration and email reset share a narrow OTP lifecycle. The relay creates six digits with `SecureRandom`, retains only a keyed digest, and enforces ten-minute expiry, five attempts, a sixty-second resend cooldown, single use, and request limits. Local account creation or password replacement occurs only after verification succeeds.

## Slide 9 - Offline-first behavior

Wealthora is offline-first, not offline-only. Existing users can sign in and use the complete finance workspace without the relay. Registration and email reset need the relay. If it is unavailable, the UI reports an OTP-specific problem while finance and protected offline recovery remain usable.

## Slide 10 - Persistence and isolation

All mutable state is under the project `data` directory. Authentication data is local, while finance is under `data/users/<id>`. After sign-in, `AppPaths` activates the trusted identifier and `FinanceWorkspace` opens repositories over that directory. Manual verification confirmed separate records for two controlled users.

## Slide 11 - Data safety

Portability behaves as a transaction: inspect, validate, create a safety backup, apply, and roll back failure. Money Manager sources are read only and mapped into staging. Presentation data is explicit, OWNER-controlled, idempotent, per-user, and removable using exact manifest entries.

## Slide 12 - Manual verification

The completed manual phase covered paths that automated tests cannot prove alone. Registration waited for OTP verification; resend invalidated the older code; both reset methods changed local authentication correctly; persistence survived restart; two users remained isolated; the second-process warning appeared; and relay failure stayed scoped to OTP actions.

## Slide 13 - Automated quality evidence

The Ant quality chain covers models, repositories, GUI foundations, finance phases, authentication, the HTTP OTP gateway, relay policy, persistence, portability, import, presentation data, and voice parsing. All 47 current Java entry points passed. A separate Windows launcher suite passed syntax, DPAPI persistence across processes, replacement rollback, corruption and removal, Java discovery, paths with spaces, relay readiness, healthy-process reuse, command-line secret absence, NetBeans startup, and launcher-owned cleanup checks.

## Slide 14 - Build and demonstration

For the August 25 demonstration, carry the printed six-page report, run `ant clean test-quality jar`, then double-click `Start Wealthora.cmd`. The private persistence gate passed: Gmail configuration was entered once through `Configure Wealthora OTP.cmd`, and later normal and NetBeans F6 launches reused Windows user-specific DPAPI ciphertext without prompting again. Keep `Start OTP Relay for NetBeans.cmd` open for F6. Demonstrate controlled sign-in, presentation data, dashboard totals, disposable add/edit/delete, budgets, analytics, export/import safety, and restart persistence. Shibli Rahman Moon coordinates the presentation and live-demo preparation as Project Lead for System Design and Integration, while each member explains the assigned modules. Never expose a password, OTP, recovery answer, SMTP credential, signing secret, private record, or local encrypted configuration.

Defense explanation: Wealthora is developed entirely in Java using Swing and can be built and run directly from NetBeans. Its one-click launcher starts the Wealthora and OTP relay Java JARs automatically. The Gmail App Password is entered once and stored locally using Windows user-specific encryption; it is never stored in the source code or submission package.

## Slide 15 - Limitations and future work

The limitations are documented boundaries. CSV updates are atomic per file, planning records do not silently move money, imports are intentionally narrow, notifications are in-app, and relay state is in memory. Future work can add durable relay storage, safer delivery observability, opt-in synchronization, more import formats, and accessibility improvements.

## Slide 16 - Team contributions

The final team allocation is explicit. Shibli Rahman Moon serves as Project Lead for System Design and Integration, covering professional UI and Dashboard work, authentication and Email OTP, password recovery, multi-user workspace integration, overall system integration, quality assurance, 47/47 test verification, final build verification, documentation coordination, and presentation and live-demo preparation. Md. Nafij Jaman Rabbi owns the core finance modules: accounts, income and expense, transfers, budgets, goals, debts, finance-data validation, and related functional testing. Md. Monimul Haque owns the reports and productivity modules: reports, recurring transactions, calendar features, import/export, backup and restore, demo-data support, and related testing.

## Slide 17 - Conclusion

The implemented architecture, verified behavior, and evidence package are aligned. All 47 Java automated entry points passed, the separate Windows launcher suite passed, both distribution JARs were verified, manual security and persistence flows were completed, and the final academic report is exactly six A4 pages. Wealthora supports one-time Gmail setup with Windows user-specific DPAPI encryption and one-click normal or NetBeans OTP startup. It is ready for its CSE 215 Section 11 defense on August 25, 2026 and final submission review. The final archive is created only from the approved committed allowlist and independently reverified.
