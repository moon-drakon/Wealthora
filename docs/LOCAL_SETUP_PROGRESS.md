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

## Restart checkpoint

Windows now reports a pending Component Based Servicing restart. No restart
was initiated automatically. Docker Desktop was not started in this boot, so
the Docker Server and `hello-world` are not yet verified.

Restart Windows manually. After signing in, open a new PowerShell terminal and
run:

```powershell
cd G:\Projects\SpendWiseExpenseTracker
wsl.exe --status
wsl.exe --version
wsl.exe --list --verbose
Start-Process -FilePath 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
```

Complete any first-run Docker Desktop terms prompt, choose the WSL 2 backend if
asked, and do not sign in. Then resume Codex in this repository. The first
verification commands must use the installed CLI path until a new terminal is
confirmed to have Docker on `PATH`:

```powershell
& 'C:\Program Files\Docker\Docker\resources\bin\docker.exe' version
& 'C:\Program Files\Docker\Docker\resources\bin\docker.exe' info
& 'C:\Program Files\Docker\Docker\resources\bin\docker.exe' run --rm hello-world
```

## Not started at this checkpoint

- External environment-file validation
- The secret-safe PowerShell launcher
- Live Neon/PostgreSQL and Flyway verification
- Live SMTP and authentication end-to-end verification
- Live desktop CLOUD smoke testing
- Docker image build and run verification

No secret file was read or copied, no `web/` directory was created, no local
OWNER finance data was accessed or changed, and nothing was pushed or merged.
