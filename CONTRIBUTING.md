# Contributing

Thanks for looking at FlagBox. Small, focused changes are the easiest to review.

## Build and test

You need Java 21 and Docker (the tests start PostgreSQL with Testcontainers).

```bash
./mvnw -B verify
```

This runs the unit tests (`*Test`) and the integration tests (`*IT`). The JaCoCo report is in `target/site/jacoco/index.html`.

## Before you open a pull request

- Keep the change small and explain why in the description.
- Add a test for the behaviour you change. Evaluation changes need an engine test, and API changes need an integration test.
- Do not edit a Flyway migration that has already been released. Add a new `V<n>__...sql` file instead.
- Do not put keys, passwords or `.env` files in commits.
- The CI run must be green.

## Code style

Follow the code around you. Keep packages organised by feature. Use `.editorconfig` for whitespace.
