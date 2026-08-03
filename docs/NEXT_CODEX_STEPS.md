# Wealthora continuation

## Current state

- Branch: `feature/wealthora-online-auth-voice`
- Checkpoint base: `40789d8`
- Current HEAD/new commit: `feat: add Google Cloud Speech V1 recognition` (this document is committed with it; confirm the hash with `git rev-parse --short HEAD`)
- Checkpoint completed: 4, real Google Cloud Speech-to-Text V1 microphone recognition
- Java: Microsoft OpenJDK `25.0.2`
- Desktop tests: `ant test-voice` passed its full chain, including 23 voice tests and 5 HTTP gateway tests
- Latest desktop build: `ant clean jar` passed with 244 production sources; output `dist\Wealthora.jar`
- Runtime: desktop JAR launch passed; Java Sound enumerated three devices and opened the selected microphone; missing online/cloud configuration produced the required blocked state
- Server tests: `server\mvnw.cmd test` passed 14 H2/Flyway tests
- Latest server build: `server\mvnw.cmd clean package` passed with 14 tests and produced the Spring Boot JAR
- Web build: not applicable; `web/` has not been started
- Database migration version: `password-security-v2`; checkpoint 4 adds no schema migration

## Completed in this checkpoint

- Added bounded Java Sound capture for 16 kHz, 16-bit, mono little-endian PCM with microphone selection, Stop, Cancel, timeout, retry, and duration.
- Added an authenticated desktop speech client and server-only Google Cloud Speech-to-Text V1 integration. No Google dependency or credential is placed in the desktop JAR.
- Added authenticated `/api/speech/status` and `/api/speech/recognize` endpoints with Base64/size/sample validation and honest service-unavailable responses.
- Added `en-US`, `bn-BD`, and English/Bangla alternative-language requests for Banglish/automatic input.
- Kept provider checks, capture, recognition, and microphone tests outside the Swing EDT.
- Recognition returns an editable transcript, detected language, and confidence before the existing parser and explicit review/confirm path. It never auto-saves.
- Audio is held only in bounded memory and cleared after cancellation or submission.

## Files changed

- Desktop voice/provider: `src/com/spendwise/voice/`, `src/com/spendwise/ui/voice/`, `SettingsPanel`, `SpendWiseFrame`, and application wiring.
- Authenticated desktop transport: registration gateway and local auth service speech boundary.
- Server: speech API/service/gateway/configuration, security matcher, Google Cloud V1 dependency, and environment example.
- Tests: desktop voice/HTTP gateway and server speech endpoint coverage.
- Documentation: `README.md` and both continuation documents.

## Known limitations and configuration

- Live cloud transcription is CONFIGURATION REQUIRED: set `GOOGLE_CLOUD_PROJECT=wealthora-voice`, install valid ADC outside source control, and enable Google Cloud Speech-to-Text V1 on the server project.
- The desktop also needs `WEALTHORA_SERVER_URL`, an active online Wealthora session, and a compatible microphone. With any missing prerequisite, typed multilingual parsing remains available.
- This host has no ADC, project environment value, or `gcloud`, so the real Google call was not fabricated; server contract tests verify the integration boundary and unavailable state is manually checked.
- Google OAuth is MISSING. Remember Me does not persist online tokens. Default verified registrations remain pending until the Admin Console approval checkpoint.
- Existing OWNER, local authentication data, finance workspaces, finance records, and backups were not changed or migrated.

## Exact next incomplete checkpoint

Checkpoint 5: real browser-based Google OAuth with exact `northsouth.edu` enforcement and safe PASSWORD/GOOGLE account linking.

## Exact smallest next task

Add the backend-controlled Google OAuth configuration/status and authorization start/callback contract, including state protection and issuer/audience/expiry/verified-email validation. Keep Continue with Google honestly unavailable until configuration exists; do not put a client secret in Swing or create/link a Wealthora user until the validated identity-linking transaction is implemented in the same checkpoint.

## Exact resume commands

```powershell
git status -sb
git log -3 --oneline
$env:JAVA_HOME = 'C:\DevelopmentTools\jdk-25\jdk-25.0.2'
& 'C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat' test-voice
& 'C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat' clean jar
cd server
.\mvnw.cmd clean package
```

## Ready-to-paste continuation prompt

Continue Wealthora on `feature/wealthora-online-auth-voice` from the verified Google Cloud Speech-to-Text V1 checkpoint. Read `docs/CURRENT_IMPLEMENTATION_STATUS.md` and `docs/NEXT_CODEX_STEPS.md`, inspect the clean HEAD once, and resume only checkpoint 5's exact smallest task: backend-controlled browser Google OAuth configuration/start/callback with strict token and exact `northsouth.edu` validation, followed by safe PASSWORD/GOOGLE identity linking within that checkpoint. Preserve the OWNER, all finance workspaces, typed voice fallback, confirm-before-save, BCrypt/HMAC protections, opaque session rotation, and pending-approval policy. Never fake OAuth, request a Google password, expose tokens/secrets/audio, commit credentials/databases, push, or merge. Run focused desktop/server tests, `ant clean jar`, and `server\mvnw.cmd clean package`; create one verified commit and update both handoff documents.
