# SpendWise Development Instructions

## Project context

- This is a CSE215 Java OOP semester project.
- Use Java 21 and standard Java libraries.
- Use Apache Ant and maintain Apache NetBeans compatibility.
- Use a programmatic Swing GUI unless explicitly changed later.

## Code quality

- Keep code professional, readable, and explainable in a student viva.
- Apply proper object-oriented design without unnecessary advanced patterns or overengineering.
- Use meaningful class, method, and variable names.
- Add short comments only when the reason for the code is not obvious.
- Preserve the package structure and never add classes to the default package.
- Do not add external dependencies without explicit approval.

## Build and repository safety

- Never edit generated `build/` or `dist/` files manually.
- Run a full build after meaningful changes.
- If `ant` is unavailable by name, run:
  `C:\DevelopmentTools\apache-ant-1.10.17\bin\ant.bat clean jar`
- Review `git diff` before committing.
- Never use destructive Git commands, force push, or fabricated history.
- Never add secrets, credentials, machine-specific paths, or fabricated project claims.

## Documentation and reporting

- Keep documentation accurate to what is actually implemented.
- When reporting work, include changed files, build result, test result, and remaining limitations.
