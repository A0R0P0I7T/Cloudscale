# CloudScale Architecture

CloudScale is a locally demonstrable feature-configuration and rollout platform. The runtime contains six Spring Boot services, an Angular client, and infrastructure components. `contracts` is a shared Java module, not a runtime service.

## Service ownership

| Service | Owns | Calls / events |
| --- | --- | --- |
| Auth Service | Organizations, users, memberships, login and JWT issuance | HTTPS/HTTP API |
| API Gateway | Public API routing, JWT validation, CORS, correlation IDs | HTTP to backend services |
| Configuration Service | Projects, environments, flags, versions, targeting rules, configuration outbox | PostgreSQL; configuration-events |
| Evaluation Service | Feature evaluation path, Redis snapshots, configuration-event invalidation | Redis; Configuration API fallback; configuration-events |
| Rollout Service | Rollouts, stages, policies, observations, rollout outbox, assignment | PostgreSQL; rollout-events |
| Audit Service | Immutable event history and idempotency records | PostgreSQL; configuration-events and rollout-events |

Auth Service exclusively owns organizations, users, and memberships. Other services store `organization_id` as a tenant identifier but never query Auth tables or create cross-service foreign keys. Each service owns its database schema and Flyway migrations. PostgreSQL is authoritative; Redis is an eventually consistent Evaluation cache.

The only application entry points published by Compose are the frontend/Nginx and Gateway, both bound to loopback. Nginx proxies `/api/` to Gateway so browser requests remain same-origin. Backend service ports are internal to the Compose network.

## Request security and routing

Auth Service exposes public `POST /api/v1/auth/register` and `/api/v1/auth/login`. Registration creates the organization, BCrypt-hashed user, and initial `ADMIN` membership in one transaction. Successful login/registration returns a signed HS256 token with `sub`, `organization_id`, `role`, `iss`, `iat`, `exp`, `jti`, and email claims. The default token lifetime is 900 seconds.

The same JWT issuer and a secret of at least 32 UTF-8 bytes must be configured in Auth, Gateway, and all resource servers. Compose requires these local values in `.env`; no token or password is stored in the frontend. The Angular client keeps its token in memory and adds it only to same-origin `/api/v1` requests, excluding Auth endpoints.

Gateway route precedence is:

1. `/api/v1/auth/**` → Auth Service
2. `.../features/*/evaluate` → Evaluation Service
3. `/api/v1/organizations/*/rollouts/**` → Rollout Service
4. `/api/v1/organizations/*/audit-events/**` → Audit Service
5. Remaining `/api/v1/organizations/**` → Configuration Service

Gateway validates tokens as an edge check. Every backend independently validates issuer/signature and enforces tenant/role policy. The authenticated organization claim must exactly match the organization UUID in the URL. All organization reads require a member; writes require `ADMIN` or `DEVELOPER`. `VIEWER` may call only the read-like Evaluation and rollout-assignment POST operations.

Gateway replaces missing or malformed `X-Correlation-Id` values with a UUID, passes it to downstream services, and returns it in the response. Evaluation forwards the caller token and correlation ID to Configuration Service on a cache miss.

## Configuration writes and events

Configuration Service owns project/environment/feature/version data. A feature has at most one draft and one published version. Drafts are immutable after creation. Publishing locks the feature row, archives any prior published version, publishes the selected draft, advances the feature revision, updates the published pointer, and inserts an outbox event in one PostgreSQL transaction.

The Configuration outbox dispatcher claims pending rows with `FOR UPDATE SKIP LOCKED`, publishes to `cloudscale.configuration-events`, and marks a row published only after Kafka acknowledgement. Failed sends remain durable and retry with bounded exponential backoff (up to one hour).

Rollout Service writes state and its versioned event to its own outbox transactionally and publishes to `cloudscale.rollout-events`. The initial Kafka topic limit is exactly:

- `cloudscale.configuration-events`
- `cloudscale.rollout-events`

Consumers assume at-least-once delivery. Audit stores event envelopes and a `(event_id, consumer_group)` idempotency record transactionally, then serves organization-scoped paginated history. Evaluation retries failed configuration-event processing and uses revision-aware Redis invalidation.

There is no dead-letter topic because the current architecture is limited to the two domain topics. Audit and Evaluation retry indefinitely; a permanently malformed event can block its partition until the record or consumer is corrected. This is a known operational limitation, not a claim of poison-message recovery.

## Evaluation and Redis consistency

```text
Client → Gateway → Evaluation → Redis snapshot
                              ↘ Configuration Service on miss/read failure

Configuration transaction → outbox → configuration-events
                                      → Evaluation invalidation
```

Evaluation caches published configuration snapshots, not user-specific results. The key is:

```text
cloudscale:evaluation-config:{organizationId}:{projectKey}:{environmentKey}:{featureKey}
```

Each snapshot has a companion `:revision` key and a configurable 60-second default TTL. Cache writes use a Lua script that refuses revisions lower than the stored revision. Configuration-event invalidation removes the snapshot and advances the revision only for a strictly newer event; duplicate/equal or stale events do not evict a fresh same-revision snapshot. PostgreSQL remains authoritative. Redis read failures fall back to Configuration Service; a failed Configuration fetch returns a controlled `503`.

Targeting supports `EQUALS`, `NOT_EQUALS`, `IN`, and `NOT_IN`. Conditions within a rule use AND semantics; rules are evaluated by ascending priority. Evaluation returns the selected published configuration value and reason.

## Rollout behavior

Rollout assignment hashes the stable user ID, feature UUID, and environment UUID (separated by U+001F) with SHA-256, maps the integer digest to a bucket from 0 through 9,999, then compares that bucket with the active percentage. It is deterministic across requests; it does not use per-request randomness.

The lifecycle supports draft, start, pause, resume, advance, completion, and explicit rollback. Stage durations count active time; pausing shifts the current stage’s start time on resume. Clients/services explicitly submit request count, error rate, latency, and observation time. A configured policy can pause a rollout only when its minimum sample floor, time window, cooldown, and threshold rules qualify. Automatic rollback is not implemented.

Rollout assignment remains a separate diagnostic API. It does not influence Evaluation because the current feature value contract has no control/treatment variants; coupling percentage exposure into evaluation would invent behavior.

## Runtime and observability

`docker-compose.yml` runs PostgreSQL 16, Redis 7, Kafka 3.9 in single-node KRaft mode, all six services, the Angular/Nginx container, Prometheus, and Grafana. Compose creates the two Kafka topics idempotently. Application image builds use the pinned Maven Wrapper. Java service healthchecks call Actuator with `curl` installed in the runtime image.

All services expose `/actuator/health`, `/actuator/info`, and `/actuator/prometheus`; health details are hidden. Prometheus uses Compose DNS names (`gateway:8080`, `auth-service:8081`, `config-service:8082`, `rollout-service:8083`, `evaluation-service:8084`, and `audit-service:8085`) and scrapes `/actuator/prometheus`. Grafana has a provisioned Prometheus data source. No dashboard is bundled; README documents useful PromQL expressions.

Evaluation instruments cache hit/miss/read/write errors, Configuration fetches/errors, and duration tagged only with bounded reason/source values. Configuration/Rollout outboxes and Evaluation/Audit Kafka consumers expose published, processed, stale, duplicate, retry, and error counters. Tenant IDs and user IDs are not metric labels.

## Verification limits

The source includes Docker-optional Testcontainers coverage for PostgreSQL, Kafka, and Redis. When Docker is unavailable those integration tests skip, so local unit/build success does not prove database migrations, live Kafka behavior, Prometheus scraping, Compose health, or end-to-end routing. A one-off JaCoCo run generated coverage, but the Maven build has no committed coverage plugin or threshold. There is no representative runtime load test or measured evaluation/cache/Kafka/database performance data. See [`metrics-report.md`](metrics-report.md) for evidence and explicit “not measured” entries.
