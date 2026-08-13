# CloudScale Architecture

CloudScale is a distributed feature and service management platform. This document records the approved service boundaries and the implemented Phase 1 through Phase 3 foundation.

## Services

| Service | Owns | Communicates through |
| --- | --- | --- |
| API Gateway | Public routing and edge concerns | HTTP to backend services |
| Auth Service | Organizations, users, memberships, authentication | HTTP APIs and JWTs |
| Configuration Service | Projects, environments, flags, versions, targeting rules | REST and configuration Kafka events |
| Rollout Service | Rollouts, stages, health policies, progression, rollback state | REST and rollout Kafka events |
| Evaluation Service | Fast evaluation read path and Redis cache | HTTP fallback and configuration events |
| Audit Service | Immutable audit history and event processing state | Kafka consumers and audit REST APIs |

The initial deployment contains exactly these six services. Prometheus, Grafana, PostgreSQL, Redis, and Kafka are infrastructure components, not additional CloudScale services.

## Ownership Rules

Auth Service exclusively owns organizations, users, and memberships. Other services store `organization_id` as a tenant identifier but never query Auth Service tables or create cross-service database foreign keys.

Each service owns its database schema and Flyway migrations. PostgreSQL is authoritative. Service-to-service data access uses APIs or events, not shared database tables.

## Configuration and Evaluation Flow

```text
Management client
  -> API Gateway
  -> Configuration Service
  -> PostgreSQL
  -> cloudscale.configuration-events
  -> Evaluation Service
  -> Redis invalidation or refresh

Application
  -> API Gateway or Evaluation Service endpoint
  -> Redis
  -> Configuration Service on cache miss
  -> Redis refresh
```

Redis is an eventually consistent read cache. Cache entries contain a configuration version. Events and TTLs allow recovery from missed invalidations. Request coalescing and distributed locking are intentionally deferred until measurement shows a need.

## Kafka

Initial topics are limited to:

- `cloudscale.configuration-events`
- `cloudscale.rollout-events`

Audit Service consumes relevant domain events directly from these topics. There is no separate audit topic in the initial design.

Events use a versioned envelope containing `eventId`, `eventType`, `eventVersion`, `occurredAt`, `organizationId`, `entityId`, `entityType`, `source`, `correlationId`, and a typed payload. Consumers assume at-least-once delivery and use idempotency records or version checks where duplicate delivery matters.

## Authentication

1. Auth Service authenticates the user and issues a signed JWT.
2. The client sends the JWT to API Gateway.
3. Gateway validates the token and propagates correlation context.
4. Each backend service validates the token and enforces its own role and organization checks.

Roles are `ADMIN`, `DEVELOPER`, and `VIEWER`. Gateway validation is an edge optimization, not a replacement for service-level authorization.

## Rollout and Health

Rollout assignment uses a stable identifier and feature/environment identity:

```text
hash(stableUserIdentifier + featureIdentifier + environmentIdentifier) mod 100
```

The bucket is compared with the effective rollout percentage. A request never receives a random assignment.

Applications or services explicitly report health observations. The initial observation model will support error rate, request count, and latency. A rollout policy uses a minimum sample count, evaluation window, threshold, and cooldown before pausing or rolling back. CloudScale does not infer application health without reported observations.

## Observability

All services will expose health endpoints, structured logs, correlation IDs, OpenTelemetry traces where practical, and Prometheus metrics. Grafana dashboards will cover platform health, evaluation latency, cache behavior, Kafka consumers, rollout progress, and rollbacks.

## Phase Boundaries

## Phase 2 Local Runtime

Phase 2 runs infrastructure in Docker Compose and services locally through Maven. This is deliberately simpler than containerizing six empty applications before their runtime contracts exist.

Docker Compose provides:

- PostgreSQL 16 with separate empty databases for Auth, Configuration, Rollout, and Audit ownership
- Redis 7 for the Evaluation Service connection
- Kafka 3.9 in single-node KRaft mode
- `cloudscale.configuration-events` and `cloudscale.rollout-events`, created idempotently by `kafka-init`
- Prometheus scraping `/actuator/prometheus` on ports 8080 through 8085
- Grafana with a provisioned Prometheus datasource

The service bootstraps expose standard Actuator health, info, and Prometheus endpoints. Auth, Configuration, Rollout, and Audit configure PostgreSQL; Evaluation configures Redis; Configuration, Rollout, Evaluation, and Audit configure Kafka. No domain tables, migrations, event publishing, event consumers, authentication, or business endpoints exist yet.

Phase 1 established repository structure, build metadata, frontend workspace metadata, environment conventions, and architecture documentation. Domain migrations, Kafka behavior, security, rollout behavior, full observability implementation, and complete UI flows belong to later phases.

## Phase 3 Configuration Domain

Configuration Service is the authoritative owner of:

- Projects
- Environments
- Feature flags
- Feature versions
- Targeting-rule storage

Its data is stored only in `cloudscale_config`. Auth Service remains the exclusive owner of organizations, users, and memberships. Configuration Service stores `organization_id` as an opaque tenant identifier and does not create cross-service foreign keys or query Auth Service tables.

The Phase 3 schema consists of:

- `projects`
- `environments`
- `feature_flags`
- `feature_versions`
- `targeting_rules`

Feature versions have the states `DRAFT`, `PUBLISHED`, and `ARCHIVED`. A feature has at most one draft and one published version. Publishing locks the feature row, verifies draft ownership, archives the previous published version, publishes the requested version, updates `published_version_id`, and increments the feature revision in one PostgreSQL transaction.

The Configuration Service exposes direct REST APIs under `/api/v1/organizations/{organizationId}` for projects, environments, features, versions, and publishing. The Gateway is not changed in Phase 3 because authentication and gateway authorization are later concerns.

Targeting rules are stored as validated JSONB data for future evaluation work. No targeting evaluation or percentage rollout logic exists in this phase.

The `contracts` module now defines typed configuration event envelopes and event types. Phase 3 does not publish events, consume events, add an outbox, or couple Audit Service to Configuration Service storage. Kafka propagation and audit consumption remain later phases.

## Phase 4 Distributed Evaluation

Evaluation Service provides the low-latency feature evaluation path. It does not own configuration data and does not connect to PostgreSQL.

```text
Evaluation client
  -> Evaluation Service
  -> Redis configuration snapshot
  -> evaluate enabled state and targeting rules

Redis miss or read failure
  -> Configuration Service evaluation-config endpoint
  -> Redis snapshot write
  -> evaluate configuration
```

Configuration Service exposes the service-to-service read boundary:

```text
GET /api/v1/organizations/{organizationId}/projects/{projectKey}/environments/{environmentKey}/features/{featureKey}/evaluation-config
```

The response contains the feature state, published version, default value, targeting rules, revision, and update timestamp. A feature without a published version returns a valid negative snapshot so Evaluation Service can cache that state. Missing resources remain `404` responses.

Evaluation requests use:

```text
POST /api/v1/organizations/{organizationId}/projects/{projectKey}/environments/{environmentKey}/features/{featureKey}/evaluate
```

The request supplies a stable `userId` and optional attributes. Phase 4 supports only explicit targeting operators `EQUALS`, `NOT_EQUALS`, `IN`, and `NOT_IN`. Conditions in one rule use AND semantics; rules are evaluated by priority and the first valid match wins. No percentage rollout, hashing, or Rollout Service integration exists yet.

Redis caches configuration snapshots rather than user-specific evaluations. The key format is:

```text
cloudscale:evaluation-config:{organizationId}:{projectKey}:{environmentKey}:{featureKey}
```

The default TTL is 60 seconds. Because Kafka invalidation is not implemented, a published configuration may remain cached for up to the configured TTL. Request coalescing, distributed locks, and cache warming are intentionally deferred.

PostgreSQL remains authoritative. Configuration Service remains the canonical read boundary. Redis failures degrade the cache path and fall back to Configuration Service when possible. Configuration Service failure on a cache miss produces a controlled `503 CONFIGURATION_UNAVAILABLE` response.

Phase 4 adds bounded evaluation metrics for cache hits, cache misses, cache errors, configuration fetches, configuration fetch errors, and evaluation duration. Organization IDs, project keys, feature keys, and user IDs are not metric labels.

## Phase 5 Event-Driven Cache Invalidation

Configuration Service publishes `ConfigurationEvent` records to `cloudscale.configuration-events` after successfully committing a PostgreSQL transaction. This applies to publishing new versions and enabling or disabling feature flags. It uses the Spring `TransactionSynchronizationManager.registerSynchronization` with an `afterCommit` hook to guarantee no event is published if the transaction rolls back.

Evaluation Service acts as a Kafka consumer for `cloudscale.configuration-events`. When a configuration changes, the consumer invalidates the corresponding Redis cache entry. The consumer does not perform a synchronous fetch; it allows the next evaluation request to experience a cache miss and repopulate the snapshot using the existing fallback logic.

To prevent race conditions where an older HTTP cache-miss fetch might overwrite a newer event-driven invalidation (or vice-versa), Redis updates are revision-aware.

Redis stores a companion key:
```text
cloudscale:evaluation-config:{organizationId}:{projectKey}:{environmentKey}:{featureKey}:revision
```

Both cache updates (from HTTP fetches) and cache invalidations (from Kafka events) execute a Lua script in Redis:
- The script compares the incoming revision with the current companion revision key.
- If the incoming revision is greater than or equal to the current revision, the operation proceeds and updates the revision key's value and TTL.
- If the incoming revision is older, the operation is ignored as stale.

This strategy ensures that out-of-order deliveries or slow HTTP responses cannot downgrade the cache to an older state, and avoids the need for a relational database deduplication table in Evaluation Service.
