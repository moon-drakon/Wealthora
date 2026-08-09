# GitHub and submission guide

This guide is intentionally procedural. Review every change before publishing;
do not stage, commit, push, deploy, or create a submission archive from an
unreviewed working tree.

## 1. Verify the local project and repository

```powershell
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git status --short
git remote -v
```

- `git rev-parse --show-toplevel` proves which directory is the repository root;
  it should be the same project root opened in NetBeans and prepared for
  submission.
- `git branch --show-current` proves which local branch is being reviewed.
- `git rev-parse HEAD` prints the exact local commit identifier that can later
  be compared with GitHub.
- `git status --short` exposes modified, deleted, staged, and untracked paths
  that must be reviewed before publication or submission.
- `git remote -v` shows the configured fetch and push destinations; verify every
  URL before pushing.

Record the output with the review notes. Then inspect the complete working-tree
baseline:

```powershell
git status --short --branch
git log -1 --oneline
git diff --stat
git diff --name-status
git ls-files --others --exclude-standard
```

Separate intended edits from unrelated local work. Do not discard another
person's modified or untracked files.

## 2. Build and test

With JDK 25 and Apache Ant available:

```powershell
ant clean test-quality jar
```

Then run from the project root:

```powershell
java -jar dist\Wealthora.jar
```

In Apache NetBeans, also perform **Clean and Build Project** and **Run Project**.
Record a check as passed only if you actually ran it successfully.

## 3. Repository audits

```powershell
git diff --check
$credentialPattern = '(AKIA[0-9A-Z]{16}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9]{20,})'
git grep -I -q -E $credentialPattern
if ($LASTEXITCODE -eq 0) {
    throw 'A high-confidence credential signature was found.'
}
if ($LASTEXITCODE -gt 1) {
    throw "Credential scan failed with exit code $LASTEXITCODE."
}
git ls-files | Select-String -Pattern '(^|/)(data|build|dist)/|\.env$|\.p12$|\.pfx$|\.pem$|\.key$'
git ls-files '*.ps1' '*.cmd' '*.bat' '*.sh' | Where-Object { Test-Path -LiteralPath $_ }
```

Expected results before publication:

- `git diff --check`: no whitespace errors
- high-confidence credential scan: no matches
- mutable/secret filename scan: no tracked runtime data or secret containers
- tracked launcher inventory: review the three root CMD launchers and their
  internal `scripts/launchers/` PowerShell implementation; confirm no credential
  value or local configuration file is present

Ordinary academic terms such as `demo` and `demonstration` are valid when they
describe presentation data or the live project review. Repository hygiene must
be based on concrete generated-file, credential, and private-data signatures,
not a global substring ban.

Also inspect all untracked paths and the complete diff:

```powershell
git diff -- .
git ls-files --others --exclude-standard
```

## 4. Manual security checks

- Create an ordinary account and confirm no user exists before the correct email
  code is verified.
- Check wrong, expired, replayed, and older resent codes fail.
- Confirm relay failure does not block existing local sign-in or offline
  recovery.
- Confirm a failed reset leaves the old password valid.
- Start a second Wealthora process against the same project data and confirm it
  stops without writing.
- Load/remove presentation records and confirm manual entries remain.
- Review relay and desktop output for absence of codes, passwords, SMTP secrets,
  recovery answers, and finance content.

## 5. Commit workflow

After review, stage exact paths rather than the entire tree:

```powershell
git add -- README.md SECURITY.md build.xml manifest.mf lib nbproject src test otp-relay docs presentation-data .gitignore
git status --short
git diff --cached --check
git diff --cached --stat
git diff --cached --name-status
```

Inspect `git diff --cached` before committing. Confirm whether `origin` already
exists:

```powershell
git remote -v
git remote get-url origin
```

If and only if `git remote get-url origin` reports that no `origin` remote
exists, add the reviewed GitHub destination using placeholders first:

```powershell
git remote add origin https://github.com/<username>/<repository>.git
git remote -v
```

Do not replace an existing `origin` implicitly. Verify the account and
repository before adding or changing any remote. After the remote, branch, and
staged diff are approved, use the final reviewed release message and push the
reviewed branch:

```powershell
git branch --show-current
git commit -m "feat: finalize Wealthora CSE215 academic release"
git push origin <reviewed-branch-name>
```

Replace the placeholder only after verifying the destination. Never force-push
unless the repository owner explicitly authorizes it.

## 6. Submission layout

A final source submission should contain one top-level
`Wealthora_CSE215_Final_Submission` folder with the clean source project,
runnable JARs and launchers, the exactly six-page DOCX/PDF report, final PPTX,
speaker notes, checklist, and sanitized evidence. The source project should
contain:

```text
SpendWiseExpenseTracker/
  README.md
  SECURITY.md
  build.xml
  manifest.mf
  src/
  test/
  otp-relay/
  lib/
  nbproject/        (exclude nbproject/private)
  docs/
  presentation-data/
```

Exclude `.git/`, `data/`, `build/`, `dist/`, `nbproject/private/`, IDE state,
environment files, credentials, keystores, certificates with private keys,
logs, audio, backups, and any prior archive. A separate presentation bundle may
include a freshly verified `dist/Wealthora.jar`, `dist/lib/`, and the relay JAR,
but must still exclude `data/` and every secret.

The one-click launchers themselves belong in the source submission. The local
`%LOCALAPPDATA%\Wealthora\otp-relay-config.json` file never belongs in the
repository or submission package, even though its credential fields use DPAPI.

The final report is exactly six A4 pages including its cover. Carry its printed
hard copy on August 25, 2026. The configured presentation laptop provides the
live OTP path; retain the offline path as a fallback.

### Prepare a reviewed archive in Windows PowerShell

Run these commands only after the working tree, build, and intended submission
contents have been reviewed. They deliberately refuse to overwrite an existing
staging directory or archive:

```powershell
$projectRoot = (Get-Location).Path
$parentRoot = Split-Path -Parent $projectRoot
$submissionRoot = Join-Path $parentRoot 'Wealthora-Submission-Staging'
$archivePath = Join-Path $parentRoot 'Wealthora-CSE215-Submission.zip'

if (Test-Path -LiteralPath $submissionRoot) {
    throw "Choose a new staging path: $submissionRoot already exists."
}
if (Test-Path -LiteralPath $archivePath) {
    throw "Choose a new archive path: $archivePath already exists."
}

New-Item -ItemType Directory -Path $submissionRoot | Out-Null
$excludedDirectories = @(
    (Join-Path $projectRoot '.git')
    (Join-Path $projectRoot 'data')
    (Join-Path $projectRoot 'build')
    (Join-Path $projectRoot 'dist')
    (Join-Path $projectRoot 'nbproject\private')
)
robocopy $projectRoot $submissionRoot /E /XD $excludedDirectories `
    /XF *.zip .env .env.* wealthora.local.env otp-relay-config.json `
        .otp-relay-config-*.tmp .otp-relay-config-*.bak `
        *.key *.pem *.p12 *.pfx `
        *.log hs_err_pid* replay_pid*
if ($LASTEXITCODE -ge 8) {
    throw "Robocopy failed with exit code $LASTEXITCODE."
}

Get-ChildItem -LiteralPath $submissionRoot -Force -Recurse |
    Select-Object FullName
Get-ChildItem -LiteralPath $submissionRoot -Force -Recurse -File |
    Select-String -Pattern `
        'BEGIN PRIVATE KEY|gh[pousr]_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}' `
        -ErrorAction SilentlyContinue

Compress-Archive -LiteralPath $submissionRoot -DestinationPath $archivePath
```

Review the full listing and investigate every scan result before running
`Compress-Archive`. The command documents a future workflow; it must not be run
until the submission contents have been approved.

### Extract and verify the archive elsewhere

The final archive must be extracted to a different directory, not back over the
project or staging folder:

```powershell
$parentRoot = Split-Path -Parent (Get-Location).Path
$archivePath = Join-Path $parentRoot 'Wealthora-CSE215-Submission.zip'
$verificationRoot = Join-Path $parentRoot 'Wealthora-Submission-Verification'

if (Test-Path -LiteralPath $verificationRoot) {
    throw "Choose a new verification path: $verificationRoot already exists."
}

Expand-Archive -LiteralPath $archivePath -DestinationPath $verificationRoot
Get-ChildItem -LiteralPath $verificationRoot -Force -Recurse |
    Select-Object FullName

$extractedProject = Join-Path $verificationRoot `
    'Wealthora-Submission-Staging'
Set-Location -LiteralPath $extractedProject
ant clean test-quality jar
java -jar dist\Wealthora.jar
```

Also open `$extractedProject` as a project in NetBeans, perform **Clean and Build
Project** and **Run Project**, and repeat the filename, secret, and personal-data
review against the extracted copy. Do not submit the archive if
the extracted copy contains an excluded path, fails to build, or uses data
outside its own project folder.
