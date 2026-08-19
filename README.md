# CloudScale

CloudScale is a distributed feature and service management platform inspired by feature delivery and configuration platforms used in production systems.

## Current Status

Phase 5 adds event-driven cache invalidation using Kafka propagation. Configuration Service publishes configuration events, and Evaluation Service invalidates its Redis snapshot cache using atomic revision-aware Lua scripts. Authentication, progressive rollout, and the Angular management UI remain deferred.

## Architecture

The system has six independently deployable Spring Boot services:

- API Gateway
- Auth Service
- Configuration Service
- Rollout Service
- Evaluation Service
- Audit Service

See [`docs/architecture.md`](docs/architecture.md) for ownership boundaries, Kafka topics, Redis consistency, authentication, rollout health, and phase boundaries.

## Technology

- Java 21 and Spring Boot 3.x
- Maven
- PostgreSQL and Flyway
- Redis
- Apache Kafka
- Angular and TypeScript
- OpenTelemetry, Prometheus, and Grafana
- Docker and GitHub Actions
- JUnit, Spring Boot tests, and Testcontainers

## Repository Layout

```text
contracts/          Versioned event contract library
gateway/            API Gateway module
auth-service/       Identity and organization ownership
config-service/     Configuration source of truth
rollout-service/    Progressive rollout state and policies
evaluation-service/ Low-latency evaluation path
audit-service/      Kafka-driven audit history
frontend/           Angular application
docs/               Architecture and engineering documentation
```

## Local Configuration

Copy `.env.example` to `.env` for local values. Do not commit `.env` or any real credentials.

```bash
cp .env.example .env
docker compose --env-file .env up -d
```

On Windows PowerShell, copy the file with `Copy-Item .env.example .env`.

## Prerequisites

- Java 21 for running services locally
- Maven 3.9 or the Maven Wrapper once it is generated
- Docker Desktop with Compose v2
- Node.js and npm for frontend verification

The current development machine must provide Java 21; the repository does not lower its compiler target to accommodate older JDKs.

## Local Runtime

The recommended Phase 2 workflow runs infrastructure in Docker and Spring Boot services locally. Start infrastructure first:

```bash
docker compose --env-file .env up -d
docker compose ps
```

Start each service in a separate terminal:

```bash
mvn spring-boot:run -pl gateway
mvn spring-boot:run -pl auth-service
mvn spring-boot:run -pl config-service
mvn spring-boot:run -pl rollout-service
mvn spring-boot:run -pl evaluation-service
mvn spring-boot:run -pl audit-service
```

The service defaults use the local ports and infrastructure values in `.env.example`. When running through an IDE, configure the same variables from `.env` in each Spring Boot run configuration.

## Ports

| Component | Port | Verification |
| --- | ---: | --- |
| API Gateway | 8080 | `http://localhost:8080/actuator/health` |
| Auth Service | 8081 | `http://localhost:8081/actuator/health` |
| Configuration Service | 8082 | `http://localhost:8082/actuator/health` |
| Rollout Service | 8083 | `http://localhost:8083/actuator/health` |
| Evaluation Service | 8084 | `http://localhost:8084/actuator/health` |
| Audit Service | 8085 | `http://localhost:8085/actuator/health` |
| PostgreSQL | 15432 | `cloudscale` user and service databases. Note: Local CloudScale services must connect to `localhost:15432` mapped by Docker, and must not use native PostgreSQL running on `5432`. |
| Redis | 6379 | `redis-cli -h localhost ping` |
| Kafka external listener | 29092 | `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list` |
| Prometheus | 9090 | `http://localhost:9090/targets` |
| Grafana | 3000 | `http://localhost:3000` |

PostgreSQL creates `cloudscale_auth`, `cloudscale_config`, `cloudscale_rollout`, and `cloudscale_audit`. Auth, Rollout, and Audit remain empty until their owning phases; Configuration Service applies its Phase 3 schema through Flyway.

Configuration Service now applies its Flyway migrations to `cloudscale_config` and owns the `projects`, `environments`, `feature_flags`, `feature_versions`, and `targeting_rules` tables. The other service databases remain unchanged.

`POSTGRES_PASSWORD` is applied by PostgreSQL only when the data volume is first initialized. If that value changes later, update the existing `cloudscale` role or remove local volumes intentionally with `docker compose down -v` before starting again.

## Health and Monitoring

Every service exposes:

- `/actuator/health`
- `/actuator/info`
- `/actuator/prometheus`

Evaluation Service exposes Redis health through the standard Actuator health response. Auth, Configuration, Rollout, and Audit Services expose PostgreSQL health. Configuration, Rollout, Evaluation, and Audit Services also configure the Kafka bootstrap address for infrastructure verification.

Prometheus is provisioned to scrape all six local service ports. Grafana is provisioned with Prometheus at `http://prometheus:9090` and uses `admin` / the configured `GRAFANA_ADMIN_PASSWORD` for the local login.

Stop the local runtime with:

```bash
docker compose down
```

Use `docker compose down -v` only when intentionally deleting local PostgreSQL, Redis, Kafka, Prometheus, and Grafana data volumes.

## Configuration API

Configuration management is available directly on port `8082` under:

```text
/api/v1/organizations/{organizationId}
```

The Phase 3 API supports project and environment management, feature flag metadata, draft feature versions, version history, and publishing. Organization IDs are opaque tenant identifiers until Auth Service and JWT authorization are implemented.

Feature versions are immutable after creation. Publishing a draft archives the previous published version and updates the feature's published version pointer in one PostgreSQL transaction.

Configuration event contracts exist in the `contracts` module, and Configuration Service publishes `ConfigurationEvent` records after successfully committing database transactions. Audit integration remains an event-driven boundary for a later phase.

## Evaluation API

Evaluation is available directly on port `8084`:

```text
POST /api/v1/organizations/{organizationId}/projects/{projectKey}/environments/{environmentKey}/features/{featureKey}/evaluate
```

Example request:

```json
{
  "userId": "user-123",
  "attributes": {
    "plan": "pro"
  }
}
```

Evaluation Service reads a published configuration snapshot from Redis and calls Configuration Service synchronously on a cache miss. Snapshots use:

```text
cloudscale:evaluation-config:{organizationId}:{projectKey}:{environmentKey}:{featureKey}
```

The default TTL is 60 seconds and can be changed with `EVALUATION_CACHE_TTL_SECONDS`. Redis is an eventually consistent cache; PostgreSQL remains authoritative through Configuration Service. Redis read failures fall back to Configuration Service, while a Configuration Service failure on a cache miss returns `503 CONFIGURATION_UNAVAILABLE`.

Phase 4 supports only explicit targeting rules using `EQUALS`, `NOT_EQUALS`, `IN`, and `NOT_IN`. Conditions within one rule use AND semantics, and rules are evaluated by ascending priority. Percentage rollout and deterministic hashing are deferred.

Evaluation metrics include cache hits, cache misses, cache errors, configuration fetches, configuration fetch errors, evaluation duration, and Kafka event processing outcomes. Evaluation Service acts as a Kafka consumer that performs atomic, revision-aware Redis cache invalidations to reduce evaluation latency and data staleness.

Focused Configuration Service verification:

```bash
mvn -pl config-service -am clean verify
```

## Verification

From the repository root:

```bash
mvn validate
mvn clean verify
```

From `frontend/` after installing dependencies:

```bash
npm ci
npm run build
npm test
```

The Maven wrapper should be generated and committed before the first runtime implementation phase so all contributors use a pinned Maven version.
