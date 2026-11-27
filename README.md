# CloudScale

CloudScale is a locally demonstrable, organization-scoped feature configuration and rollout platform. It includes a Java 21 / Spring Boot multi-module backend, an Angular management UI, PostgreSQL, Redis, Kafka, and Prometheus/Grafana monitoring.

## Current capabilities

- Auth Service registration/login with BCrypt password hashing and short-lived HS256 JWTs.
- Gateway routing for Auth, Configuration, Evaluation, Rollout, and Audit APIs.
- Organization and role enforcement at each backend service; `ADMIN` and `DEVELOPER` may mutate, while `VIEWER` is read-only except for evaluation and deterministic assignment checks.
- Project, environment, feature-flag, targeting-rule, and immutable version management.
- Redis-backed evaluation snapshots, targeting evaluation, configuration-service fallback, and revision-safe Kafka invalidation.
- Transactional Configuration and Rollout outboxes; idempotent Audit consumers and organization-scoped audit history.
- Deterministic rollout assignment, staged lifecycle controls, health observations, and automatic pause on qualifying unhealthy observations. Rollback is manual.
- Angular workspaces for Feature Flags, Evaluation, Rollouts, and Audit History.

Rollout assignment is currently diagnostic; it does not alter Evaluation results because the feature-value contract has no control/treatment variants. See [`docs/architecture.md`](docs/architecture.md) for service boundaries and known limitations.

The shared visual system is recorded in [`DESIGN.md`](DESIGN.md); the management surface’s release-timetable brief lives in `.impeccable/surfaces/`.

## Requirements

- Java 21
- Docker Desktop / Docker Engine with Compose v2 for the complete local runtime
- Node.js 22 and npm for frontend development and verification
- OpenSSL for generating local passwords/secrets

The Maven Wrapper pins Maven 3.9.11. Its first run needs network access to download Maven and dependencies.

## Local setup

Copy the example environment file and set local-only values:

```bash
cp .env.example .env
```

Generate separate values with `openssl rand -hex 24` for `POSTGRES_PASSWORD` and `GRAFANA_ADMIN_PASSWORD`, and `openssl rand -base64 48` for `JWT_SECRET`. Edit `.env` with those values. `JWT_SECRET` must be at least 32 UTF-8 bytes. Keep `.env` private and never commit it. Keep `JWT_ISSUER` identical for all services; the existing example issuer value is intentionally preserved.

Validate the Compose model without printing resolved environment values, then build and start the full stack:

```bash
docker compose --env-file .env config --quiet
docker compose --env-file .env up --build -d
docker compose --env-file .env ps
```

The Angular/Nginx container serves the UI and proxies same-origin `/api/` requests to the Gateway. The browser does not call backend service containers directly. Backend containers use the Maven Wrapper for their builds and include `curl` for their healthchecks.

Stop containers without deleting persistent local data:

```bash
docker compose --env-file .env down
```

Do not use `docker compose down -v` unless intentionally deleting all local PostgreSQL, Redis, Kafka, Prometheus, and Grafana data.

## Local URLs and published ports

Compose binds host ports to loopback only. Auth, Configuration, Rollout, Evaluation, and Audit service ports remain internal to the Compose network.

| Component | Local URL / port | Purpose |
| --- | --- | --- |
| Management UI | `http://localhost` | Angular client |
| API Gateway | `http://localhost:8080` | Direct API/health access for local development |
| PostgreSQL | `localhost:15432` | Local database tooling |
| Redis | `localhost:6379` | Local cache tooling |
| Kafka external listener | `localhost:29092` | Host-side Kafka clients |
| Prometheus | `http://localhost:9090/targets` | Scrape target status and PromQL |
| Grafana | `http://localhost:3000` | Prometheus data source; login uses `.env` values |

The individual services expose `/actuator/health`, `/actuator/info`, and `/actuator/prometheus` only inside the Compose network. Health details are not returned. Prometheus targets the Compose DNS names on ports 8080–8085.

## Frontend-only development

With the Gateway available on `localhost:8080`, run:

```bash
cd frontend
npm ci
npm start
```

The Angular dev-server proxy forwards `/api` to the local Gateway. The app keeps access tokens in memory; a page reload requires signing in again.

## API outline

All browser API calls use the Gateway's `/api/v1` paths:

| API | Purpose |
| --- | --- |
| `POST /api/v1/auth/register` | Create an organization, initial `ADMIN` membership, and session token |
| `POST /api/v1/auth/login` | Authenticate an organization member |
| `/api/v1/organizations/{organizationId}/projects/**` | Project, environment, feature, version, and publish operations |
| `POST /api/v1/organizations/{organizationId}/projects/{projectKey}/environments/{environmentKey}/features/{featureKey}/evaluate` | Evaluate a published feature for a stable user ID and attributes |
| `/api/v1/organizations/{organizationId}/rollouts/**` | Create/list rollouts, manage lifecycle, record observations, inspect assignment |
| `GET /api/v1/organizations/{organizationId}/audit-events` | Read organization-scoped event history |

Tokens are signed with HS256, expire after 15 minutes by default, and carry user, organization, role, issuer, issue/expiry, and token-ID claims. Each organization API checks that the token organization exactly matches the path organization. The Gateway also replaces invalid correlation IDs and propagates the accepted ID downstream.

Feature versions are immutable. Publishing a draft archives the previous published version and updates the published pointer/revision in one database transaction. Targeting conditions within a rule use AND semantics; rules are evaluated by ascending priority.

## Observability

Prometheus is configured to scrape the six service targets at `/actuator/prometheus`. Grafana is provisioned with the Prometheus data source; no prebuilt dashboard is included. Useful PromQL examples:

```promql
# Evaluation cache hit fraction over five minutes
sum(rate(cloudscale_evaluation_cache_hits_total[5m]))
/
(
  sum(rate(cloudscale_evaluation_cache_hits_total[5m]))
  + sum(rate(cloudscale_evaluation_cache_misses_total[5m]))
)

# Configuration fetch rate after cache misses/errors
sum(rate(cloudscale_evaluation_configuration_fetches_total[5m]))

# Mean observed evaluation duration (seconds)
sum(rate(cloudscale_evaluation_duration_seconds_sum[5m]))
/
sum(rate(cloudscale_evaluation_duration_seconds_count[5m]))

# Configuration event retry rate in Evaluation
sum(rate(cloudscale_evaluation_configuration_events_retries_total[5m]))
```

These are process-lifetime counters/timers and do not imply a measured service-level objective. Evaluation latency percentiles require a running, representative workload and histogram configuration; they are not reported as measured in this repository’s local audit.

## Verification

From the repository root:

```bash
./mvnw -B verify
```

If your shell cannot locate Java, set `JAVA_HOME` to a JDK 21 installation and prepend `$JAVA_HOME/bin` to `PATH` before invoking the wrapper. In environments with a conflicting Maven startup file, use `MAVEN_SKIP_RC=1`.

For a focused backend module:

```bash
./mvnw -B -pl rollout-service -am test
```

From `frontend/`:

```bash
npm ci
npm run build
npm test
npm test -- --code-coverage
npm audit
```

Testcontainers tests run when Docker is available and skip otherwise. Docker-backed Testcontainers integration checks remain unverified in this environment. Compose startup, service health, and Prometheus scraping were verified during the local evaluation benchmark; latency and throughput measurements and their limits are recorded in [`performance/load-test/results/evaluation-local-20261124.md`](performance/load-test/results/evaluation-local-20261124.md). See [`docs/metrics-report.md`](docs/metrics-report.md) for other measured and unmeasured metrics and [`docs/audit-report.md`](docs/audit-report.md) for the audit/status matrix.
