# Sanitization Statement

This evidence package intentionally contains no:

- passwords or password hints;
- OTP values or challenge identifiers;
- recovery answers;
- SMTP credentials or provider app passwords;
- signing secrets, tokens, private keys, keystores, or environment values;
- personal finance records, backups, runtime CSV files, or user-workspace contents;
- private account email addresses;
- `%LOCALAPPDATA%\Wealthora\otp-relay-config.json` or any DPAPI ciphertext copied
  from a real user configuration;
- screenshots containing authentication values or account identifiers.

Repository file paths, class names, build versions, non-sensitive artifact metadata, status counts, and cryptographic hashes are retained because they are required to reproduce the verification record.

Team member names and student IDs are retained only as required academic roster information. They are not authentication identifiers and are not used by the application runtime.

Automated launcher evidence was generated with random dummy values in isolated
temporary directories. It did not inspect or modify the user's real persistent
SMTP configuration.
