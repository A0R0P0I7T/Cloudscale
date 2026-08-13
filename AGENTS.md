# AGENTS.md - CloudScale

## Repository State
- The repository is a Maven multi-module Java 21 project with six Spring Boot services and one Angular application.
- `docs/architecture.md` is the source of truth for service ownership, event flow, caching, rollout, and observability decisions.
- Do not add another service without documenting and proving a genuine architectural boundary.

## Build and Test
- Use the Maven Wrapper once it is generated and committed; until then use `mvn` from the repository root.
- `mvn clean verify` builds all backend modules and runs their checks.
- `mvn test -pl <module> -am` runs a focused module test with required upstream modules.
- Frontend commands run from `frontend/`: `npm ci`, `npm start`, `npm test`, and `npm run build`.
- Testcontainers-based tests require a running Docker daemon.

## Boundaries and Data
- Auth Service exclusively owns organizations, users, and memberships.
- Other services may store `organization_id`, but must not query Auth Service tables or create cross-service foreign keys.
- PostgreSQL is authoritative; Redis is an eventually consistent evaluation cache.
- Each service owns its schema and migrations. Never edit an applied Flyway migration; add a new version.
- Kafka is initially limited to `cloudscale.configuration-events` and `cloudscale.rollout-events`.

## Implementation Rules
- Use Java 21, Spring Boot 3.x, Maven, PostgreSQL, Redis, Kafka, Flyway, Angular, OpenTelemetry, Prometheus, Grafana, Docker, and GitHub Actions.
- Use DTOs at REST boundaries and keep controllers focused on HTTP concerns.
- Assume Kafka at-least-once delivery; event consumers must be idempotent where duplicate delivery matters.
- Use deterministic hashing for rollout assignment; never use per-request randomness.
- Evaluation may call Configuration Service synchronously on a Redis miss; do not add request coalescing or distributed locks before measuring a problem.
- Rollout health is explicitly reported by clients/services as observations such as error rate, request count, and latency.
- Keep comments rare and limited to non-obvious business, concurrency, event, cache, or rollout decisions.
- Frontend design and engineering may use only the `impeccable` and `emil-design-eng` skills.
- Use `full-output-enforcement` for substantial implementation work and use `find-skills` before adopting a missing specialized skill.
