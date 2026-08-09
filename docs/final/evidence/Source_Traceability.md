# Source Traceability

All paths are relative to `G:\Projects\SpendWiseExpenseTracker`.

| Claim | Primary source evidence |
| --- | --- |
| Product name, version, tagline | `src/com/spendwise/config/AppBrand.java` |
| Java Swing navigation and implemented pages | `src/com/spendwise/ui/SpendWiseFrame.java` |
| Layered architecture and network boundary | `docs/ARCHITECTURE.md` |
| Project-local data root and per-user workspace | `src/com/spendwise/config/AppPaths.java` |
| Exclusive application lock | `src/com/spendwise/config/ProjectDataLock.java` |
| Password length and composition | `src/com/spendwise/auth/PasswordService.java` |
| BCrypt/SHA-256 credential protection | `src/com/spendwise/auth/PasswordService.java` |
| Recovery-answer protection | `src/com/spendwise/auth/RecoveryAnswerService.java` |
| USER/ADMIN/OWNER roles | `src/com/spendwise/auth/UserRole.java` |
| Cross-user access denial | `src/com/spendwise/auth/AuthorizationService.java` |
| Registration/reset transaction and local sign-in | `src/com/spendwise/auth/local/LocalDesktopAuthService.java` |
| OTP gateway boundary | `src/com/spendwise/auth/otp/EmailVerificationGateway.java` |
| OTP expiry, attempts, resend, HMAC, and rate limits | `otp-relay/src/com/wealthora/otp/relay/OtpRelayService.java` |
| Purpose-specific branded OTP email templates and escaping | `otp-relay/src/com/wealthora/otp/relay/OtpEmailTemplate.java` |
| UTF-8 multipart/alternative SMTP construction | `otp-relay/src/com/wealthora/otp/relay/SmtpMailDelivery.java`, `OtpEmailTemplate.java` |
| OTP email MIME, fallback, HTML, escaping, and resend-purpose tests | `otp-relay/test/com/wealthora/otp/relay/OtpRelayServiceTest.java` |
| HTTPS/SMTP environment boundary | `otp-relay/src/com/wealthora/otp/relay/RelayConfiguration.java`, `docs/OTP_RELAY_SETUP.md` |
| Configurable validated SMTP sender display name | `otp-relay/src/com/wealthora/otp/relay/RelayConfiguration.java`, `OtpEmailTemplate.java`, `SmtpMailDelivery.java` |
| DPAPI CurrentUser configuration and atomic save/replace | `Configure Wealthora OTP.cmd`, `scripts/launchers/Configure-WealthoraOtp.ps1`, `WealthoraLauncher.psm1` |
| One-click normal startup, relay health/reuse, and owned cleanup | `Start Wealthora.cmd`, `scripts/launchers/Start-Wealthora.ps1`, `WealthoraLauncher.psm1` |
| NetBeans F6 relay startup and cleanup | `Start OTP Relay for NetBeans.cmd`, `scripts/launchers/Start-OtpRelayForNetBeans.ps1` |
| Launcher syntax, DPAPI, lifecycle, rollback, path, and secret tests | `test/launchers/WealthoraLauncherTest.ps1` |
| Transaction abstraction and polymorphism | `src/com/spendwise/model/Transaction.java`, `Income.java`, `Expense.java` |
| Official OOP/Swing requirement-to-test matrix | `docs/OOP_MAPPING.md`, `docs/final/Wealthora_OOP_Requirement_Traceability.md` |
| Account balance rules | `docs/PHASE2_FINANCE_ARCHITECTURE.md`, `src/com/spendwise/service/FinanceService.java` |
| Planning/report limitations | `docs/PHASE3_FINANCE_ARCHITECTURE.md` |
| Safe CSV writes | `src/com/spendwise/repository/CsvFileSupport.java`, `src/com/spendwise/service/SafeFileSupport.java` |
| Validated CSV import and safety backup | `src/com/spendwise/service/CsvImportService.java` |
| ZIP/JSON backup and rollback | `src/com/spendwise/service/BackupService.java`, `JsonBackupService.java` |
| Money Manager read-only import mapping | `src/com/spendwise/imports/ForeignBackupDetector.java`, `MoneyManagerImport.java` |
| Presentation-data ownership and idempotence | `src/com/spendwise/service/PresentationDataService.java` |
| Team roster and course context | `README.md` |
| Defense responsibility allocation | `docs/PRESENTATION_GUIDE.md` |
| Final academic details and contribution allocation | Team-supplied final academic details dated 2026-08-09; reconciled in `Academic_Details_and_Contribution_Record.md` |
| Automated test dependency chain | `build.xml` |
| Final six-page report and defense deliverables | `docs/final/README.md` and the allowlisted files below it |
