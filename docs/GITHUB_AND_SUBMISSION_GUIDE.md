# GitHub and submission guide

This guide focuses on keeping the Wealthora source repository clean, reviewable, and safe for academic submission.

## 1. Verify the repository

From the project root:

```powershell
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git status --short --branch
git remote -v
```

Before submission, confirm the intended branch and remote, review all modified or untracked files, and avoid publishing unrelated local work.

## 2. Build and test

With JDK 25 and Apache Ant:

```powershell
ant clean test-quality jar
java -jar dist\Wealthora.jar
```

In Apache NetBeans, also use **Clean and Build Project** and **Run Project**.

## 3. Repository safety checks

Run:

```powershell
git diff --check
git diff --stat
git diff --name-status
git ls-files --others --exclude-standard
```

Review tracked and untracked files for credentials, private user data, runtime data, build output, backups, logs, or local IDE state.

Do not commit or submit:

- `data/`
- `build/`
- ordinary development `dist/` output unless a runnable bundle is explicitly required
- `nbproject/private/`
- `.env` files
- passwords, OTPs, recovery answers, SMTP credentials, signing secrets, or tokens
- keystores, private keys, logs, backups, crash dumps, audio, or private finance records
- `%LOCALAPPDATA%\Wealthora\otp-relay-config.json`

## 4. Source layout

The reviewed source project should contain the application and its supporting code:

```text
README.md
SECURITY.md
THIRD_PARTY_NOTICES.md
build.xml
manifest.mf
src/
test/
otp-relay/
lib/
nbproject/        (excluding nbproject/private)
docs/
presentation-data/
Configure Wealthora OTP.cmd
Start Wealthora.cmd
Start OTP Relay for NetBeans.cmd
scripts/launchers/
```

## 5. Code-review path

For source-code inspection, the current GitHub `main` branch is the primary reference.

Useful review points:

- `src/com/spendwise/app/` — application startup and dependency wiring
- `src/com/spendwise/ui/` — Swing UI
- `src/com/spendwise/service/` — application and finance logic
- `src/com/spendwise/model/` — domain models and OOP hierarchy
- `src/com/spendwise/repository/` — persistence contracts and CSV repositories
- `src/com/spendwise/auth/` — authentication, roles, sessions, recovery, and OTP boundary
- `docs/ARCHITECTURE.md` — architecture explanation
- `docs/OOP_MAPPING.md` — OOP requirement mapping

## 6. Final review

Before sending a repository or archive:

1. Confirm the source builds successfully.
2. Confirm the intended code is pushed to the correct branch.
3. Confirm no private or generated runtime data is included.
4. Confirm documentation links in `README.md` are valid.
5. Keep current presentation/report files in the designated submission location if the course requires them; they do not need to be mixed with source-review instructions.

Never force-push or replace a remote without verifying the destination first.
