# Contributing

1. Start the infrastructure: `docker compose up -d`.
2. Branch from `main`: `feat/<short-name>`, `fix/<short-name>` or `docs/<short-name>`.
3. Keep commits small, with an imperative message (`add waitlist claim endpoint`).
4. Database changes go in a new Flyway file `backend/src/main/resources/db/migration/V<next>__<what>.sql`. Never edit a migration that is already on `main`.
5. Run `mvn verify` from the repository root before opening a pull request. Integration tests need Docker running (Testcontainers).
6. Errors leave the API as Problem Details (`application/problem+json`); reuse the exceptions in `common/error`.
