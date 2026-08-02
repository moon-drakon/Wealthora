# Wealthora Authentication Integration

Wealthora remains local-first. Settings opens a complete authentication preview,
but local development does not require login and the preview never unlocks data.
`UnconfiguredAuthApiClient` and `UnconfiguredGoogleAuthService` reject every
operation with a backend-configuration message.

## Two distinct policies

`Continue with Google` is one create-or-sign-in flow for any verified Google
account, including Gmail, `@northsouth.edu`, and other Google Workspace domains.
The future backend must verify issuer, audience, signature, expiry, and verified
email, then identify the account by Google's subject ID rather than email alone.

Password registration, sign-in, email verification, forgot-password, and reset
are restricted to the exact `northsouth.edu` domain on both client and backend.
Password sign-in is unavailable until the NSU email is verified.

## Desktop boundaries

- `GoogleAuthService` owns the future system-browser authorization flow.
- `AuthApiClient` represents `/api/auth` transport calls.
- `BackendAuthService` applies client-side validation and clears temporary
  password and Google authorization-code copies after each synchronous call.
- `AuthenticatedUser` models provider, status, verified email, and the stable
  Google subject ID. `UserSession` can only wrap an active verified account.
- `SessionManager` holds an authenticated session in memory only.
- `AuthFrame` provides sign-in, registration, verification, recovery, reset,
  and verified-profile screens. A valid backend session is required to reach
  the profile screen.

The future backend endpoints are:

```text
POST /api/auth/google
POST /api/auth/register
POST /api/auth/login
POST /api/auth/verify-email
POST /api/auth/resend-verification
POST /api/auth/forgot-password
POST /api/auth/reset-password
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me
```

For safe linking, a verified Google identity whose verified email exactly
matches an existing verified NSU password account may become
`LOCAL_AND_GOOGLE` only after backend-controlled linking records the Google
subject ID. The client never merges accounts. Unverified or unrelated providers
must not be linked, and a non-NSU Google email can never become a password
account.

The Google implementation must use the system browser with PKCE and a loopback
redirect or backend-approved device flow. OAuth client secrets, Google
passwords, plaintext passwords, and unprotected refresh tokens must never be
stored in the desktop repository or logged.
