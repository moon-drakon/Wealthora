# OTP relay setup

Wealthora's relay is a standalone Java 25 component. It exposes only health,
code request, and code verification endpoints; it has no finance, login,
administration, recovery-answer, or user-database access.

## Build and test

From the repository root:

```powershell
ant compile test-otp-relay jar-otp-relay
```

The JAR is written to `dist/otp-relay/wealthora-otp-relay.jar`.

## Recommended Windows one-click setup

For the local single-computer workflow, use the launchers in the repository
root instead of manually setting relay variables:

1. Run **`Configure Wealthora OTP.cmd`** once.
2. Enter the Gmail/Google Workspace sender address, sender display name, and
   existing Google App Password only in the masked local prompt.
3. After the no-mail STARTTLS authentication check succeeds, run
   **`Start Wealthora.cmd`** for normal use.

The configuration is stored outside the repository at
`%LOCALAPPDATA%\Wealthora\otp-relay-config.json`. Non-secret metadata remains
readable, while the App Password and generated relay signing secret are Windows
DPAPI `CurrentUser` ciphertext. Replacement uses an atomic file operation only
after the candidate Gmail credentials authenticate successfully. Normal startup
decrypts secrets only in memory for the relay child environment and never asks
for them again while the configuration remains valid.

If configuration is missing, corrupted, or belongs to another Windows user or
computer, **`Start Wealthora.cmd`** offers **Configure OTP now**, **offline
mode**, or **Exit**. Offline mode launches the desktop without a relay URL; local
sign-in, finance features, and protected offline recovery remain available.

### Replace or remove Gmail configuration

1. Obtain a Google App Password for the replacement account.
2. Run **`Configure Wealthora OTP.cmd`**.
3. Select **Replace Gmail/App Password** and enter both privately.
4. The old file remains unchanged unless SMTP authentication succeeds and the
   encrypted replacement can be written atomically.
5. Restart Wealthora and verify OTP delivery before revoking the old Google App
   Password.

Select **Remove OTP configuration** in the same launcher and type the displayed
confirmation word to delete both encrypted and non-secret local settings.

### NetBeans F6

After first-time configuration, restart NetBeans once so it inherits the saved
non-secret `WEALTHORA_OTP_RELAY_URL`. Double-click **`Start OTP Relay for
NetBeans.cmd`**, keep its window open, and use F6 normally. It starts only the
Java relay, reuses an already healthy relay, and explains whether closing the
window will stop the launcher-owned process. Gmail credentials are not requested
again.

## Required environment

Store all real values in the relay host's environment or secret manager. Never
put them in source control, a desktop JAR, screenshots, or support output.

| Variable | Purpose |
|---|---|
| `WEALTHORA_OTP_SIGNING_SECRET` | Random secret of at least 32 bytes used for keyed code digests |
| `WEALTHORA_SMTP_HOST` | SMTP hostname |
| `WEALTHORA_SMTP_PORT` | STARTTLS SMTP port; default `587` |
| `WEALTHORA_SMTP_USERNAME` | SMTP login name |
| `WEALTHORA_SMTP_PASSWORD` | SMTP password or provider app password |
| `WEALTHORA_SMTP_FROM` | Optional plain sender mailbox; defaults to the SMTP username |
| `WEALTHORA_SMTP_FROM_NAME` | Optional safe display name; defaults to `Wealthora Security` |
| `WEALTHORA_RELAY_BIND_ADDRESS` | Listen address; default `127.0.0.1` |
| `WEALTHORA_RELAY_PORT` | Listen port; default `8443` |
| `WEALTHORA_RELAY_KEYSTORE` | PKCS12 file containing the HTTPS certificate and key |
| `WEALTHORA_RELAY_KEYSTORE_PASSWORD` | Keystore password |

Production startup fails unless the keystore and password are present. The
certificate must be trusted by desktop JVMs and match the hostname used in
`WEALTHORA_OTP_RELAY_URL`.

Start the relay:

```powershell
java -jar dist\otp-relay\wealthora-otp-relay.jar
```

Set the desktop process to the relay origin—no endpoint path, credentials,
query, or fragment is allowed:

```powershell
$env:WEALTHORA_OTP_RELAY_URL = 'https://otp.example.edu:8443'
java -jar dist\Wealthora.jar
```

The desktop rejects non-HTTPS origins, redirects, oversized responses, wrong
content types, unknown JSON fields, and malformed JSON. Connect and request
timeouts prevent the UI worker from waiting indefinitely.

## Loopback-only development mode

For isolated local verification, the relay may use plain HTTP only when both of
these are true:

- `WEALTHORA_RELAY_ALLOW_HTTP_LOOPBACK=true`
- `WEALTHORA_RELAY_BIND_ADDRESS` resolves to a loopback address

Then set the desktop URL to the matching `http://127.0.0.1:<port>` origin. The
relay refuses a non-loopback bind in this mode. This exception is not suitable
for traffic between machines.

## Endpoint contract

- `GET /health`
- `POST /otp/request` with exact JSON fields `email`, `purpose`, `challengeId`
- `POST /otp/verify` with exact JSON fields `email`, `purpose`, `challengeId`,
  `code`

Purposes are `REGISTRATION` and `PASSWORD_RESET`. Only exact normalized
`@northsouth.edu` recipient addresses are accepted. Request bodies are limited
to 4 KiB; responses use `application/json`, `Cache-Control: no-store`, and
`X-Content-Type-Options: nosniff`.

## OTP lifecycle

- Six numeric digits from `SecureRandom`
- Ten-minute expiry
- Five failed verification attempts
- Single-use successful verification
- Sixty-second resend cooldown
- Newest successfully delivered challenge wins
- Five requests per normalized email per hour
- Thirty requests per source IP per hour

If SMTP delivery of a resend fails, the previously delivered challenge remains
valid. Binding mismatches fail without consuming or invalidating the rightful
user's challenge. Relay restart clears all pending challenges.

## Email message format

The relay selects the message from the challenge purpose, including resend:

- `REGISTRATION`: **Verify your Wealthora email** with a Create Account / email
  verification heading.
- `PASSWORD_RESET`: **Reset your Wealthora password** with a Forgot Password /
  password-reset heading.

The six-digit code is present only in the message bodies. It is never included
in the subject or relay logs. Every message is UTF-8 `multipart/alternative`
with a plain-text fallback and a responsive HTML version. The HTML uses nested
presentation tables and inline CSS for email-client compatibility. It has no
JavaScript, external images, remote fonts, tracking pixels, or remote resource
references. Dynamic values are validated for their context and HTML-escaped
before rendering; MIME bodies use base64 transfer encoding.

Both bodies state the ten-minute expiry, warn the recipient not to share the
code, and explain that an unrequested message can be ignored.

## Operational check

After supplying operator-owned SMTP and HTTPS credentials:

1. Confirm `/health` over the intended HTTPS hostname.
2. Request a Create Account code using a controlled institutional mailbox and
   privately inspect both the HTML rendering and plain-text alternative.
3. Request a Forgot Password code for the same controlled account and privately
   inspect the purpose-specific HTML and plain-text alternative.
4. Verify that no account exists before the correct registration code is
   entered.
5. Check wrong, expired, replayed, and resent codes are rejected.
6. Remove any temporary account and mailbox message created for the check.

Automated tests use an in-memory mail adapter and do not prove delivery through
an external SMTP provider or rendering inside Gmail.

The one-click workflow additionally has Windows tests for PowerShell/CMD syntax,
DPAPI persistence across processes, masked-secret handling, replacement rollback,
corruption/removal behavior, Java discovery, path spaces, readiness, duplicate
reuse, NetBeans startup, process ownership, cleanup, and command-line secret
absence. These tests use generated dummy values and do not contact SMTP.
