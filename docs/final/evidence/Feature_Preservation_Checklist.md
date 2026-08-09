# Wealthora Feature-Preservation and Non-Regression Checklist

Binding result: **PASS**. Finalization preserved the established Java Swing,
local/offline-first production architecture. No cloud/backend/deployment code
was restored, no production feature was replaced with a reduced demonstration,
and no disconnected OOP sample classes were introduced.

| Preserved area | Evidence checked | Result |
| --- | --- | --- |
| FlatLaf Swing shell, navigation, dashboard, themes and settings | `SpendWiseFrame`, `OverviewPanel`, theme/settings classes; GUI and dashboard quality targets | PASS |
| Accounts, income, expenses, transfers, edit/delete and calculations | Production models/services/table models; finance/model/service/Swing targets | PASS |
| Budgets, categories, reports, calendar, recurring and Quick Entry | Production services/panels; budget/category/report/recurring targets | PASS |
| Goals, debts, cards, backup/restore, export and safe import | Production services; Phase 3, data-portability and export targets | PASS |
| Money Manager import and source-integrity protections | `MoneyManagerImport`; sanitized repository fixture; four import tests | PASS |
| Presentation data and duplicate prevention | `PresentationDataService`; four idempotence/collision/manifest tests | PASS |
| Bangladesh presets and existing-data migration | BDT/Taka branding/presets; `LegacyAppDataImporter`; portable-data tests | PASS |
| OWNER/ADMIN/USER, Admin Console and account/session workflows | `UserRole`, `AdminService`, authorization/authentication tests | PASS |
| Per-user workspaces and local authentication records | `FinanceWorkspace`, `AppPaths`, local-auth and persistence tests | PASS |
| Registration/reset OTP and protected offline recovery | Desktop OTP interfaces/services, authentication tests, completed manual flows | PASS |
| OTP expiry, cooldown, newest-code, attempts, replay, limits and binding | `OtpRelayServiceTest` plus verified controlled Gmail flows | PASS |
| Offline-first finance and explicit-only OTP network boundary | Architecture/source audit, relay-unavailable manual test, offline launch | PASS |
| Project-local data, exclusive lock and safe legacy import | `AppPaths`, `ProjectDataLock`, migration/import tests and manual lock check | PASS |
| DPAPI one-time Gmail configuration and all three CMD launchers | Windows launcher suite and completed private persistence/F6 gate | PASS |
| NetBeans/Ant/Java 25 and both distribution JARs | Full Ant chain, NetBeans metadata, manifests, hashes and clean-export build | PASS |
| Academic identity and existing sanitized evidence | Roster/date consistency and privacy validators | PASS |

## Change-boundary verification

- The portability correction changes only `manifest.mf` and the `-post-jar`
  packaging target in `build.xml`.
- No production file below `src/` or `otp-relay/` changed during that correction;
  the existing launcher suite gained focused manifest/library-copy assertions.
- Stored-data formats, transfers, calculations, GUI workflows, authorization,
  authentication, and OTP policies are unchanged.
- The original 47/47 quality-chain entry points passed after the correction.
- The Windows launcher/security suite passed after the correction.
- A clean export with no `nbproject/private` and no prebuilt `dist` produced the
  desktop/relay JARs, copied both runtime libraries, opened the real
  `Wealthora Authentication` window, and closed normally.

Conclusion: all previously verified functionality remains within the final
baseline; the correction is additive build portability only.
