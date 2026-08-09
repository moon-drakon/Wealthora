# Wealthora Final Submission Checklist

This checklist records the approved finalization sequence. The archive is created only from the committed allowlist and must pass fresh-extraction verification before submission.

## 1. Academic identity and formatting

- [x] Course recorded as CSE 215.
- [x] Section recorded as 11.
- [x] Faculty recorded as SAM3.
- [x] Demonstration date recorded as August 25, 2026.
- [x] Final supplied roster recorded as Shibli Rahman Moon (2534187012), Md. Nafij Jaman Rabbi (2513403642), and Md. Monimul Haque (1821781042).
- [x] Official spelling confirmed as `Md. Monimul Haque`; student ID remains `1821781042` across the final documentation.
- [x] No unsupported institution logo, declaration, acknowledgement, or plagiarism statement was invented.
- [x] Review and include the team-supplied final contribution allocation, with Shibli Rahman Moon identified as Project Lead for System Design and Integration.
- [x] Report is exactly six A4 pages total, including the cover page, as required.
- [ ] Print and carry the six-page report on August 25, 2026.

## 2. Source review

- [x] Work from the reviewed `feature/shared-online-core` branch and record the exact HEAD.
- [x] Review all modified, deleted, and untracked paths; do not discard another person's work.
- [x] Run `git diff --check` and inspect the complete diff.
- [x] Confirm no tracked `data/`, `build/`, `dist/`, `.env`, key, certificate-with-private-key, backup, log, crash, replay, or audio file will enter the source submission.
- [x] Confirm no password, OTP, recovery answer, SMTP value, signing secret, token, private finance record, or local account data is present.

## 3. Final build and artifacts

- [x] Run `ant clean test-quality jar` with Java 25 and Apache Ant.
- [x] Confirm all 47/current Java automated entry points pass.
- [x] Confirm `dist\Wealthora.jar` exists and launches from the project root.
- [x] Confirm `dist\otp-relay\wealthora-otp-relay.jar` exists.
- [x] Confirm `dist\lib` contains the required desktop libraries.
- [x] Record current SHA-256 hashes after the final approved build.
- [x] Privately verified one branded Create Account email and one branded Forgot
  Password email in Gmail; no code or credential was captured or recorded.
- [x] Run the isolated Windows launcher suite: syntax, DPAPI, no-plaintext,
  rollback, readiness, reuse, ownership cleanup, paths with spaces, and NetBeans.
- [x] Real one-time launcher gate completed privately: configuration validated,
  encrypted save succeeded, controlled OTP delivery worked, and normal/restart/
  NetBeans F6 launches reused the stored configuration without another prompt.

## 4. Report and presentation

- [x] Export `Wealthora_Final_Academic_Report.docx` with Microsoft Word and verify
  the editable document through its exactly six-page A4 PDF rendering.
- [x] Render and visually inspect every page of `Wealthora_Final_Academic_Report.pdf`.
- [x] Render and visually inspect all 17 slides of
  `Wealthora_CSE215_Project_Defense.pptx`; automated overflow check passed and
  all 17 speaker-note sections are present.
- [ ] Optionally open the deck in desktop PowerPoint for a final presenter-machine check.
- [ ] Rehearse using `Wealthora_Speaker_Notes.md` and keep the demo within the allocated time.
- [x] Confirm no sensitive screenshot, authentication value, or private financial record appears in any document.

## 5. Recommended submission-folder structure

```text
Wealthora-CSE215-Submission/
  00-Submission-README/
    README_SUBMISSION.txt
  01-Source/
    SpendWiseExpenseTracker/
      README.md
      SECURITY.md
      THIRD_PARTY_NOTICES.md
      build.xml
      manifest.mf
      src/
      test/
      otp-relay/
      lib/
      nbproject/           (exclude nbproject/private)
      docs/
      presentation-data/
      Configure Wealthora OTP.cmd
      Start Wealthora.cmd
      Start OTP Relay for NetBeans.cmd
      scripts/launchers/
  02-Report/
    Wealthora_Final_Academic_Report.docx
    Wealthora_Final_Academic_Report.pdf
  03-Presentation/
    Wealthora_CSE215_Project_Defense.pptx
    Wealthora_Speaker_Notes.md
  04-Evidence/
    README.md
    Academic_Details_and_Contribution_Record.md
    Manual_Verification_Summary.md
    Source_Traceability.md
    Build_Test_and_JAR_Evidence.txt
    Git_Integrity.txt
    Sanitization_Statement.md
    HTML_Email_Manual_Gate.md
    Persistent_OTP_Launcher_Manual_Gate.md
  05-Runnable/            (include only if the course requires binaries)
    Wealthora.jar
    lib/
    otp-relay/
      wealthora-otp-relay.jar
```

## 6. Required exclusions

Exclude `.git/`, `data/`, `build/`, ordinary development `dist/` unless a separate runnable bundle is required, `nbproject/private/`, `.vscode/`, `.claude/`, environment files, credentials, `%LOCALAPPDATA%\Wealthora\otp-relay-config.json`, DPAPI ciphertext copied from any real configuration, keystores, private keys, certificates containing private keys, logs, crash dumps, replay logs, audio, backups, prior archives, and local IDE state.

## 7. Final archive verification

- [ ] Confirm the final archive hash recorded in the evidence package.
- [ ] Upload the final ZIP and any separately requested soft-copy files.
- [ ] Open the submitted PDF and PPTX once on the presentation computer.
- [ ] Keep the configured laptop for the live OTP demonstration and an offline demo path available.
