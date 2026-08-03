## Summary

Describe the problem and the result.

## Changes

- Describe the main change.

## Testing

- [ ] `ant test-auth`
- [ ] `ant clean jar`
- [ ] `server/mvnw test`
- [ ] `server/mvnw package`
- [ ] Other relevant checks are listed below

## Data migration impact

Describe any Flyway, CSV, backup, or compatibility impact. Write `None` when
there is no data change.

## Security impact

Describe authentication, authorization, secrets, logging, or privacy impact.
Write `None` when there is no security change.

## Screenshots

Include real screenshots only when the UI changed, with private data removed.
Otherwise write `Not applicable`.

## Checklist

- [ ] I reviewed the complete diff.
- [ ] I did not commit credentials, tokens, private data, or generated output.
- [ ] I kept migrations forward-only and did not rewrite existing data.
- [ ] I updated documentation for user-visible or operational changes.
- [ ] I recorded any remaining limitation.
