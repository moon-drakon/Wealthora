# Wealthora Sanitized Evidence Package

This package indexes repository, automated, manual, and team-supplied evidence supporting the final CSE 215 source review.

## Evidence files

| File | Purpose |
| --- | --- |
| `Manual_Verification_Summary.md` | Sanitized results from completed GUI/authentication checks |
| `Source_Traceability.md` | Maps important project claims to current repository files |
| `Build_Test_and_JAR_Evidence.txt` | Ant, Java, JAR, and hash evidence |
| `Git_Integrity.txt` | Repository status, integrity, and exclusion checks |
| `Git_Change_Manifest.txt` | Reviewed path inventory |
| `Feature_Preservation_Checklist.md` | Non-regression review across established production features |
| `Sanitization_Statement.md` | Evidence exclusions and privacy handling |
| `Academic_Details_and_Contribution_Record.md` | Confirmed academic metadata and declared contribution allocation |
| `HTML_Email_Manual_Gate.md` | HTML-email readiness and controlled Gmail visual checks |
| `Persistent_OTP_Launcher_Manual_Gate.md` | Launcher automation and private one-time Gmail persistence checks |

## Evidence classes

- **Repository verified**: directly supported by current source or documentation.
- **Automated verified**: reproduced by the documented build/test workflow.
- **Manual verified**: confirmed during controlled GUI/user-flow checks.
- **Team supplied**: academic metadata or responsibility allocation explicitly provided by the team and not inferred from source authorship.

No recreated screenshot is presented as genuine evidence. Authentication captures containing account identifiers or one-time codes are intentionally excluded.

Launcher tests use generated dummy values in temporary directories. The real
`%LOCALAPPDATA%\Wealthora\otp-relay-config.json` file is not read, copied, or
included in this repository evidence package.
