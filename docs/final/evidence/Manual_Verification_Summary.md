# Manual Verification Summary

The following outcomes are carried forward from the completed manual GUI and final gap-closure phases. Values, credentials, account identifiers, OTPs, recovery answers, and financial records are omitted.

| Area | Status | Sanitized observed result |
| --- | --- | --- |
| Startup and branding | VERIFIED | Built desktop JAR launched; Wealthora authentication and finance windows rendered. |
| Core finance workflow | VERIFIED | Dashboard, income, expense, edit/delete, totals, budgets, analytics, search, and settings were exercised. |
| Registration + OTP | VERIFIED | Controlled account was created only after correct code verification. |
| OTP resend/replay | VERIFIED | Older code was rejected; replacement code was accepted. |
| Email password reset | VERIFIED | Previous password was rejected; replacement password authenticated. |
| Protected offline recovery | VERIFIED | Pre-reset password was rejected; offline-reset password authenticated. |
| Restart persistence | VERIFIED | The same account returned to its authenticated dashboard after restart. |
| Two-user isolation | VERIFIED | Controlled accounts received separate finance workspaces; unique records did not cross. |
| Exclusive application lock | VERIFIED | The second-process warning was visible; the first instance remained healthy. |
| Relay unavailable | VERIFIED | A clear OTP-service message appeared; offline features remained unaffected. |

No manual authentication flow was repeated during the documentation phase.
