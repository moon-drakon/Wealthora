# Wealthora continuation

## Current state

- Branch: `feature/wealthora-auth-profile-admin`
- Verified implementation HEAD: `b0be97f`
- Current HEAD: the documentation/migration record commits following `b0be97f`; confirm with `git rev-parse --short HEAD`
- Completed checkpoint: first local desktop authentication and administration checkpoint
- Java: Microsoft OpenJDK `25.0.2`
- Build: `ant clean jar` passed after compiling 232 production sources
- Tests: `ant test-auth` passed the complete prerequisite chain, 12 authentication policy tests, and 18 local authentication/authorization tests

## Completed work

- Secure first-run OWNER setup reads and locks `APP_OWNER_EMAIL` to `shibli.moon.253@northsouth.edu`.
- Missing configuration fails closed; no fallback account or password exists.
- Local NSU login uses exact `northsouth.edu` validation, BCrypt cost 12, generic credential failures, status checks, failed-attempt tracking, and a 15-minute lockout after five failures.
- `USER`, `ADMIN`, and `OWNER` capabilities are persisted and enforced in service methods. The single OWNER also has USER and ADMIN capabilities.
- Successful login opens My Finance. Finance repositories are constructed only after a trusted session selects `data/users/<user-id>`.
- Existing managed finance files are copied byte-for-byte to the first OWNER after a timestamped backup; legacy originals remain in place. A marker prevents duplicate assignment.
- The top-right account menu provides My Finance, My Profile, Security and Sessions, Switch Account, role-gated Admin Console, and Sign Out.
- Sign Out and Switch Account dispose user dialogs and the finance frame, clear the session and active data path, and return to Sign In.
- Admin Console includes working Overview, Users, OWNER-only Administrators, and Audit Logs screens. ADMIN cannot grant/revoke ADMIN or modify OWNER; OWNER password re-authentication and a reason are required.
- Google Sign-In, registration, verification, and password recovery fail honestly as unconfigured backend operations.
- Authentication audit records cover owner bootstrap, login success/failure, logout, switch account, status changes, administrator changes, safety backup, and legacy ownership assignment.
- jBCrypt 0.4 is vendored with its ISC license, notice, and SHA-256 provenance.

## Data and backup result

- Pre-change safety backup: `C:\Users\Drakon\AppData\Local\SpendWiseExpenseTracker\backups\pre-owner-auth-20260802-231513-221.zip`
- Production `expenses.csv`: 69 bytes; SHA-256 `9B62869B92C570D77CCE133EC6B3659C65E143F644820EDEB58D76B52EB947D8`
- Production `income.csv`: 54 bytes; SHA-256 `B4C013638932E0A0CDF01386338F1BDD631CC4CEA14580DA6E231A76E0775437`
- Both hashes remained unchanged after implementation, tests, build, and launch.
- The real JAR was launched with no owner store present; the user completed first-run setup successfully and the app opened My Finance.
- Production now contains exactly one active account with `USER|ADMIN|OWNER`. The owner workspace and migration marker exist.
- Production ownership backup: `pre-owner-assignment-20260802-234221-896.zip` (633 bytes).
- The owner-workspace copies of `expenses.csv` and `income.csv` exactly match the retained legacy hashes above.
- Automated migration tests verified the backup count, byte-for-byte copies, retained legacy originals, restart persistence, and duplicate-migration protection.

## Remaining work and known limitations

- Real Google OAuth is not configured and must not be simulated.
- Self-service registration, email delivery/verification, password reset, durable session revocation, and remote multi-device sessions require a real authentication backend.
- Security, Application Settings, Backup and Restore, and Database Health Admin Console tabs currently provide accurate routing/status empty states; their server-managed controls remain future work.
- Only the configured OWNER can be created through the local UI in this checkpoint. Additional verified users require a future trusted provisioning/verification flow.
- No source files are unfinished in this checkpoint.

## Exact next task

Design and implement the trusted authentication backend boundary for verified NSU users and account linking. Preserve the local OWNER bootstrap, make local and future Google identities resolve to one Wealthora user, add real verification/reset contracts, and keep Google disabled until genuine OAuth credentials and callback configuration exist.

## Validation and run commands

```powershell
git status -sb
git log -3 --oneline
$env:JAVA_HOME = 'C:\DevelopmentTools\jdk-25\jdk-25.0.2'
$env:APP_OWNER_EMAIL = 'shibli.moon.253@northsouth.edu'
& 'C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat' test-auth
& 'C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat' clean jar
& 'C:\DevelopmentTools\jdk-25\jdk-25.0.2\bin\java.exe' -jar '.\dist\Wealthora.jar'
```

## Ready-to-paste continuation prompt

Continue Wealthora on `feature/wealthora-auth-profile-admin` from the verified first authentication checkpoint. Read `docs/NEXT_CODEX_STEPS.md`, inspect the clean HEAD once, and implement the smallest safe backend-authentication boundary for verified NSU user provisioning and unified local/Google identity linking. Preserve the local OWNER bootstrap, exact `northsouth.edu` validation, BCrypt hashes, USER/ADMIN/OWNER authorization, per-user finance workspaces, audit trail, and existing data. Do not fake Google success, store tokens or passwords in source, alter production finance data destructively, push, or merge. Add focused tests, run `ant test-auth` and `ant clean jar`, commit only verified work, and update this continuation file.
