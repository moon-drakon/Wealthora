# Wealthora local setup progress

Verified on 2026-08-03 on branch
`feature/wealthora-online-auth-voice` at `9fa4701`.

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

## Still pending

- Real SMTP arrival and OTP use
- Live desktop CLOUD-mode GUI smoke testing
- Docker `hello-world`, image build, and container run
- Google Cloud ADC and browser OAuth authorization
- Next.js web application and deployments

No local OWNER finance data was migrated or modified, and nothing was pushed
or merged.
