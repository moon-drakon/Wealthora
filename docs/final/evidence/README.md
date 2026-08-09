# Wealthora Sanitized Evidence Package

This package indexes genuine repository, automated, manual, and team-supplied evidence for the final CSE 215 documentation and presentation phase.

## Evidence files

| File | Purpose |
| --- | --- |
| `Manual_Verification_Summary.md` | Sanitized results from the completed GUI/authentication phase |
| `Source_Traceability.md` | Maps important report and slide claims to current repository files |
| `Build_Test_and_JAR_Evidence.txt` | Exact final Ant, Java, JAR, and hash evidence |
| `Git_Integrity.txt` | Pre-commit branch, baseline, status classification, integrity, and exclusion checks |
| `Git_Change_Manifest.txt` | Exact reviewed M/D/A path inventory for the final commit |
| `Sanitization_Statement.md` | Explains evidence exclusions and privacy handling |
| `Academic_Details_and_Contribution_Record.md` | Records the confirmed academic metadata and declared contribution allocation |
| `HTML_Email_Manual_Gate.md` | Records automated HTML-email readiness and the completed controlled Gmail visual checks |
| `Persistent_OTP_Launcher_Manual_Gate.md` | Records launcher automation evidence and the completed private one-time Gmail persistence check |

## Evidence classes

- **Repository verified**: directly supported by current source or documentation.
- **Automated verified**: reproduced by the final `ant clean test-quality jar` run.
- **Manual verified**: confirmed during the completed GUI/user-flow phase.
- **Team supplied**: academic metadata or responsibility allocation explicitly provided for the final submission and not inferred from source authorship.

No screenshot has been recreated or presented as genuine evidence. Authentication captures available during testing were intentionally excluded because they contained account identifiers or one-time codes.

The launcher tests use generated dummy values in temporary directories. The
real `%LOCALAPPDATA%\Wealthora\otp-relay-config.json` file was used only by the
user during the private manual gate and is not read, copied, or included here.
