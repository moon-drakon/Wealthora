# Contributing to Wealthora

## Before changing code

Use JDK 25. Keep the desktop compatible with Apache Ant and Apache NetBeans,
and use the Maven Wrapper for the Spring Boot server. Do not add dependencies,
licenses, generated binaries, or a web application without agreement from the
maintainers.

Create focused branches and commits. Preserve existing local finance data and
use temporary storage for tests and runtime checks.

## Development standards

- Keep Java code readable and explainable in a student viva.
- Preserve the existing package structure; do not use the default package.
- Prefer small classes and direct object-oriented design over extra layers.
- Keep user-facing language consistent and error messages safe.
- Add Flyway migrations as new forward-only files. Never edit an applied
  migration or enable destructive schema recreation.
- Never commit secrets, `.env` files, private test reports, databases, backups,
  audio, or generated `build`, `dist`, or `target` output.

## Required checks

From the repository root:

```text
ant test-auth
ant clean jar
```

From `server/`:

```text
./mvnw test
./mvnw package
```

On Windows, use `mvnw.cmd`. Run `git diff --check` and review the complete diff
before committing. Live PostgreSQL checks must use an empty isolated database;
never point verification at an existing finance database.

## Pull requests

Describe the behavior changed, tests run, migration and security impact, and
any remaining limitation. Include screenshots only for real UI changes and
remove private data from them. The pull request template provides the expected
checklist.
