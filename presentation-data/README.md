# Presentation data

This tracked folder contains only non-sensitive presentation metadata and
documentation. Wealthora's classroom-ready seed definitions are immutable Java
constants in `PresentationDataService`; the records are added only when an
OWNER selects **Presentation Data → Load Presentation Data**.

Runtime manifests are stored per user under the ignored
`data/presentation/` directory. A manifest lists only fixed identifiers that
the service actually created, allowing removal to preserve manually entered or
preexisting records.

Do not place user CSV files, credentials, email codes, environment values, or
mailbox exports in this tracked folder.
