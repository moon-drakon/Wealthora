# Wealthora Windows one-click launchers

## First-time setup

1. Build once with `ant clean test-quality jar`.
2. Double-click **`Configure Wealthora OTP.cmd`**.
3. Enter the Gmail or Google Workspace sender address locally.
4. Accept `Wealthora Security` or enter another safe sender name.
5. Paste the existing Google 16-character App Password into the masked prompt.
   Spaces are accepted. Never paste it into chat, documentation, screenshots, or
   source files.
6. The configurator checks Gmail STARTTLS authentication without sending an
   email. A successful candidate is then stored atomically under
   `%LOCALAPPDATA%\Wealthora\` using Windows DPAPI for the current user.

The success screen displays the configured address, never the App Password or
relay signing secret.

## Normal startup

Double-click **`Start Wealthora.cmd`**. With a valid configuration it does not
ask for Gmail details again. It detects Java in this order:

1. bundled `runtime\bin\javaw.exe` and `java.exe`;
2. a valid `JAVA_HOME`;
3. Java on `PATH`.

It verifies both distribution JARs, reuses a healthy local relay or starts one,
waits for `/health`, and launches Wealthora with the loopback relay origin. When
Wealthora closes, it stops only a relay process that it started. A pre-existing
healthy relay is preserved.

If the encrypted file is absent, damaged, tied to another Windows user, or no
longer decryptable, choose **Configure OTP now**, **Start Wealthora in offline
mode**, or **Exit**. Offline mode does not set a relay URL and does not affect
existing-user sign-in, finance operations, or offline recovery.

## Replace Gmail later

1. Create or obtain an App Password for the new Gmail account.
2. Run **`Configure Wealthora OTP.cmd`**.
3. Choose **Replace Gmail/App Password**.
4. Enter the new address and App Password privately.
5. If validation fails, the former working file is preserved byte-for-byte.
6. After success, restart Wealthora and verify OTP delivery.
7. Revoke the old Google App Password only after the new account works.

The configurator also offers **Keep current configuration**, confirmed removal,
and cancel. Removal deletes the single local configuration file and the launcher-
managed non-secret user relay URL.

## NetBeans F6 workflow

After first-time configuration, restart NetBeans once. Then double-click
**`Start OTP Relay for NetBeans.cmd`** and keep its window open while running the
project with F6. The launcher starts only the Java relay, detects and preserves
an existing healthy relay, and never asks for a saved Gmail credential again.

Press Enter or Ctrl+C in the relay window to stop a relay that launcher owns. If
the window says it reused an existing relay, closing the window does not stop
that other process.

## Storage and security boundary

- Configuration file: `%LOCALAPPDATA%\Wealthora\otp-relay-config.json`
- App Password: DPAPI `CurrentUser` ciphertext
- Relay signing secret: generated randomly, then DPAPI `CurrentUser` ciphertext
- SMTP validation: Gmail STARTTLS authentication only; no email is sent
- Relay launch: secrets in the child environment, never command-line arguments
- Desktop launch: receives only the non-secret loopback relay URL
- Repository/submission: contains launchers and documentation, never the local
  configuration file or any credential

The encrypted configuration is intentionally specific to one Windows user and
computer. Run the configuration launcher again after reinstalling Windows,
changing accounts, or moving to another computer.
