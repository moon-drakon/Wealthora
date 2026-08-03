# Wealthora local setup progress

Verified on 2026-08-04 on branch
`feature/wealthora-online-auth-voice` with implementation baseline `febb7b5`.

## Completed in this boot

- Confirmed the repository began clean and three commits ahead of its remote
  tracking branch.
- Confirmed Windows 11 Pro 24H2, build 26100.8894, on x64 hardware.
- Confirmed an active Windows hypervisor and running virtualization-based
  security. Virtual Machine Platform is enabled.
- Confirmed WSL 2.7.11.0 with kernel 6.18.33.2-2 and default version 2. No
  Linux distribution is installed, and none was added.
- Ran `wsl.exe --update`; the installed WSL release was already current.
- Ran `wsl.exe --set-default-version 2`; it completed successfully.
- Verified the Winget manifest for `Docker.DockerDesktop` version 4.84.0 is
  published by Docker Inc. and points to Docker's official domain.
- Installed Docker Desktop 4.84.0 through Winget. The installed CLI reports
  Docker 29.6.2.

## Current platform result

- Windows booted at 2026-08-03 21:58:13 +06:00 and both checked
  pending-restart indicators are clear.
- WSL 2.7.11.0 and kernel 6.18.33.2-2 respond. Docker's managed
  `docker-desktop` distribution remains the only listed distribution.
- Docker Client and Server 29.6.2 responded to version and info checks.
- `docker run --rm hello-world` failed during container creation with a
  Docker Desktop HTTP 500.
- Docker Desktop was shut down cleanly, WSL was shut down, and Docker Desktop
  was relaunched. The relaunch failed while attaching the existing
  `docker_data.vhdx` with `Wsl/Service/AttachDisk/MountDisk/HCS/0x800705aa`
  (insufficient system resources).
- The host had about 1 GB of free physical and virtual memory and about
  4.5 GB free on the system drive when diagnosed. No Docker data or settings
  were reset.

## Completed after the restart

- The external environment file was validated without printing values.
- The secret-safe server and live-verification scripts are committed in
  `718e96a`.
- Live Neon TLS, Flyway V1-V5, the exact 26-table inventory, ownership
  constraints, restart validation, and stable data-count fingerprints passed.
- The disposable live authentication lifecycle passed and restored the
  database to its original count fingerprint.
- Real SMTP registration and OTP consumption passed. An anonymized read-only
  audit verified activation, USER role assignment, a password identity, an
  active CLOUD session, and the expected audit actions.
- The new desktop launcher verified the production-profile backend,
  authentication providers, Java runtime, and desktop JAR before opening the
  Swing authentication window on port 18080.
- A real SMTP password reset passed on a verified NSU account, including
  request, completion, one-time-value consumption, password update, and
  revocation of its pre-reset session.
- The recovered address also exists locally. Explicit CLOUD/LOCAL routing now
  prevents that local record from shadowing cloud sign-in; the full desktop
  authentication suite and clean JAR build pass.
- The rebuilt two-button client completed a successful post-reset CLOUD
  sign-in. An anonymized audit verified the successful attempt, clear cloud
  lock state, and active session.
- A pair of generated disposable users completed the full desktop CLOUD
  finance workflow against the production-profile Neon backend. The run
  covered finance CRUD/planning, dashboard/report totals, USER restrictions,
  second-user isolation, relogin persistence, logout clearing, real Swing
  construction, server-unavailable state, backend-restart persistence, and
  scoped cleanup.
- The desktop's CLOUD copy now identifies a private authenticated workspace.
  Its construction-scoped read snapshot removes duplicate startup GETs
  without creating a steady-state cache.
- All five current OWNER finance files match the pre-online-auth backup
  byte-for-byte. Authentication/audit files changed only through documented
  authentication activity.
- The Voice Quick Entry audit confirmed every non-manual Stage 5 control and
  added explicit microphone health, visible timeout guidance, cancellation
  interrupt handling, and success/failure audio-buffer wiping tests.
- `ant clean jar test-voice` passes with 24 voice tests. The full server package
  passes 39 tests with no failures or errors and two intended live-only skips.
- The official Google Cloud CLI 578.0.0 and Application Default Credentials
  are ready. Token-safe live calls prove Speech-to-Text is disabled and the
  current identity cannot enable it or set the quota project. No token or
  account detail was printed.

## Still pending

- Docker `hello-world`, image build, and container run
- Project-admin Speech-to-Text enablement, Service Usage Consumer access, and
  live speech recognition
- Google browser OAuth linking verification
- Administration console live verification
- Next.js web application and deployments

No local OWNER finance data was migrated or modified, and nothing was pushed
or merged.
