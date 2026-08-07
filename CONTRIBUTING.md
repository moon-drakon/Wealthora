# Contributing to Wealthora

## Before changing code

Use Java 25 and keep the desktop application compatible with Apache Ant and
Apache NetBeans. Wealthora uses a programmatic Swing interface, so changes
should follow the existing package structure and visual style.

Create focused branches and commits. Preserve local finance data and use
temporary, isolated directories for tests or manual verification.

## Development standards

- Keep Java code readable and explainable in a student viva.
- Use direct object-oriented design without unnecessary layers.
- Keep model fields encapsulated and validate data before changing state.
- Keep user-facing language consistent and error messages clear.
- Do not add dependencies, generated binaries, or unrelated features without
  agreement from the maintainers.
- Never commit secrets, `.env` files, private reports, databases, backups,
  audio, logs, or generated `build` and `dist` output.

## Required checks

From the repository root, run:

```text
ant clean test-quality jar
git diff --check
```

Review the complete diff before committing. Confirm that
`dist\Wealthora.jar` was produced and that documentation still matches the
implemented desktop behavior.

## Pull requests

Describe the result, tests run, storage or compatibility impact, and any
remaining limitation. Include screenshots only for real UI changes and remove
private data from them.
