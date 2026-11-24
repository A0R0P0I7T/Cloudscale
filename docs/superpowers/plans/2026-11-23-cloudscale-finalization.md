# CloudScale Finalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete a locally demonstrable, tenant-secured CloudScale MVP and produce a truthful verification and metrics report.

**Architecture:** Keep the existing six Spring Boot services and Angular client. Auth issues membership-derived HS256 tokens; the Gateway validates and routes them; each backend validates organization and role claims. Configuration writes an outbox row atomically, Evaluation keeps the revision-aware Redis cache, Audit consumes domain events idempotently, and Rollout owns deterministic assignment and lifecycle state.

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring Cloud 2024.0.1, Maven 3.9.11, PostgreSQL/Flyway, Redis, Kafka, Angular 22.3.0, Docker Compose, Prometheus, Grafana, JUnit, Testcontainers.

**Frontend version ruling:** The Angular dependency family was upgraded to 22.3.0 after reviewing the original dependency advisories; `npm audit` is now clean and `npm ci`, build, and browser tests pass. The change is locked in `frontend/package-lock.json`.

**Spec:** `docs/superpowers/specs/2026-11-23-cloudscale-finalization-design.md`

## Global Constraints

- Preserve the existing `master` checkout and the pre-existing `.env.example` change.
- Do not add runtime services or cross-service database access/foreign keys.
- PostgreSQL remains authoritative; Redis remains an eventually consistent Evaluation cache.
- Use deterministic hashing for rollout assignment.
- Treat Kafka as at-least-once; persist events with the owning DB transaction and make consumers idempotent.
- Do not run destructive database or Docker volume cleanup, deploy, publish, or send project data externally.
- Docker-dependent checks remain explicitly unverified when Docker is unavailable.
- Frontend work may use only the project-approved `impeccable` and `emil-design-eng` skills; do not use other frontend design skills.
- Do not claim automatic rollback, performance, coverage, or runtime metrics without direct evidence.

## Review Focus

1. A user presents an organization UUID belonging to another tenant; service APIs must deny the request. Pin with `rejectsCrossOrganizationRequest` in each service's security tests.
2. JWT secret is absent/short or issuer differs; services must fail securely rather than accept an unsafe default. Pin with `rejectsMissingOrWeakJwtSecret` and `rejectsInvalidIssuer`.
3. A Kafka send fails after a committed configuration write; the outbox must retain and retry the event. Pin with `retriesUnpublishedConfigurationOutboxRows`.
4. Duplicate, stale, or out-of-order events arrive; audit remains idempotent and Redis never regresses. Pin with `storesAuditEventOnlyOnce` and Redis integration tests for revision order.
5. A rollout receives repeated identities and unhealthy observations; assignment stays deterministic and a low-sample observation cannot pause it. Pin with deterministic-bucket and minimum-sample tests.

---

### Task 1: Repeatable build and CI

**Files:**
- Create: `.mvn/wrapper/maven-wrapper.properties`, `mvnw`, `mvnw.cmd` using Maven Wrapper 3.3.4 pinned to Maven 3.9.11.
- Modify: root `pom.xml` to centralize Testcontainers 1.21.3 and align all module test dependencies.
- Create: `.github/workflows/ci.yml`.
- Test: existing backend and frontend suites.

**Interfaces:**
- Produces: `./mvnw -B verify` as the canonical backend command.
- Produces: CI matrix using Temurin 21, Node 22, `npm ci`, `npm run build`, and `npm test`.

- [x] Generate the wrapper with the checksum-verified Maven 3.9.11 toolchain and confirm `./mvnw -version` reports Maven 3.9.11 / Java 21.
- [x] Add CI; run frontend locally and backend `./mvnw -B verify` with Docker-independent tests; ensure Testcontainers tests skip cleanly without Docker and run in Docker-enabled CI.

### Task 2: Auth Service tokens and membership APIs

**Files:**
- Modify: `auth-service/pom.xml`, `auth-service/src/main/resources/application.yml`.
- Create: `auth-service/src/main/java/com/cloudscale/auth/api/{AuthController,AuthRequests,AuthResponse}.java`.
- Create: `auth-service/src/main/java/com/cloudscale/auth/application/AuthService.java`.
- Create: `auth-service/src/main/java/com/cloudscale/auth/persistence/AuthRepository.java`.
- Create: `auth-service/src/main/java/com/cloudscale/auth/security/{AuthSecurityConfiguration,JwtTokenService}.java`.
- Test: `auth-service/src/test/java/com/cloudscale/auth/{AuthServiceTest,JwtTokenServiceTest}.java`, update `AuthServiceIntegrationTest`.

**Interfaces:**
- `POST /api/v1/auth/register`: `{organizationName, organizationSlug, fullName, email, password}`; creates organization, BCrypt-hashed user, and ADMIN membership in one transaction; returns organization ID and access token.
- `POST /api/v1/auth/login`: `{organizationSlug, email, password}`; returns the same token response only for a valid membership.
- Token claims: `sub` user UUID, `organization_id` UUID, `role` (`ADMIN|DEVELOPER|VIEWER`), configured `iss`, `iat`, `exp`, `jti`.
- JWT secret is required and at least 32 UTF-8 bytes; token lifetime defaults to 900 seconds.

- [x] Add tests for registration hashing, duplicate email/slug, invalid login, membership-required login, token signature/issuer/expiry/claims, and missing/weak secrets.
- [x] Run Auth unit tests and compile tests before implementing endpoints.
- [x] Implement registration/login and verify the same focused tests; integration tests use Testcontainers and skip only when Docker is absent.

### Task 3: Backend tenant/role enforcement and Gateway

**Files:**
- Modify: `gateway/pom.xml`, `gateway/src/main/resources/application.yml`, `gateway/src/main/java/com/cloudscale/gateway/GatewayApplication.java`.
- Create: `gateway/src/main/java/com/cloudscale/gateway/{GatewaySecurityConfiguration,CorrelationIdFilter}.java`.
- Modify: `config-service/pom.xml`, `evaluation-service/pom.xml`, `rollout-service/pom.xml`, `audit-service/pom.xml` and their application configuration.
- Create per service: `security/{ResourceServerConfiguration,TenantAuthorizationFilter}.java`.
- Modify: `evaluation-service/src/main/java/com/cloudscale/evaluation/{api/EvaluationController,client/ConfigurationClient,application/EvaluationApplicationService}.java`.
- Test: security unit tests in `gateway`, `config-service`, `evaluation-service`, `rollout-service`, and `audit-service`; update the Evaluation controller test.

**Interfaces:**
- Gateway routes: `/api/v1/auth/**` → Auth; evaluation `.../features/*/evaluate` → Evaluation; `/api/v1/organizations/*/rollouts/**` → Rollout; `/api/v1/organizations/*/audit-events/**` → Audit; remaining organization APIs → Configuration.
- Organization APIs require a valid bearer token and exact equality between the JWT `organization_id` and path organization UUID.
- Safe methods require a valid member role; mutating methods require ADMIN or DEVELOPER.
- Gateway replaces invalid/missing `X-Correlation-Id` with a UUID, forwards it, and returns it in the response.
- Evaluation forwards the caller's bearer token and correlation ID to Configuration Service on cache miss.

- [x] Write failing tests for missing/invalid tokens, cross-organization requests, viewer write denial, valid developer reads/writes, Gateway route destinations, and correlation ID propagation.
- [x] Implement backend JWT decoders and tenant/role filters; run focused tests with test keys.
- [x] Implement Gateway routes/security/correlation; run gateway tests and compile the complete reactor.

### Task 4: Transactional configuration outbox and Audit Service

**Files:**
- Create: `config-service/src/main/resources/db/migration/V2__create_configuration_event_outbox.sql`.
- Modify: `config-service/src/main/java/com/cloudscale/config/{events/ConfigurationEventPublisher.java,application/FeatureFlagService.java,application/FeatureVersionService.java}`.
- Create: `config-service/src/main/java/com/cloudscale/config/events/{ConfigurationOutboxRepository,ConfigurationOutboxDispatcher}.java`.
- Create: `contracts/src/main/java/com/cloudscale/contracts/rollout/{RolloutEvent,RolloutEventType}.java`.
- Create: `audit-service/src/main/java/com/cloudscale/audit/{security/*,events/*,persistence/*,api/*}` and `audit-service/src/main/resources/db/migration/V2__add_audit_query_indexes.sql` if needed.
- Modify: `audit-service/pom.xml`, `audit-service/src/main/resources/application.yml`, `audit-service/src/main/resources/db/migration/V1__create_audit_schema.sql` only if not applied (otherwise add a new version).
- Test: outbox repository/dispatcher tests, audit idempotency and tenant-query tests; Redis revision integration tests in Evaluation.

**Interfaces:**
- `ConfigurationEventPublisher.publish(ConfigurationEvent)` inserts a JSON payload keyed by `eventId` into the outbox inside the active configuration transaction.
- A scheduled dispatcher publishes pending rows to `cloudscale.configuration-events`, marks success only after Kafka acknowledgement, and retries failures with bounded backoff metadata.
- Audit consumes configuration and rollout events, persists event envelope/payload plus `processed_events` in one transaction, and serves `GET /api/v1/organizations/{organizationId}/audit-events`.
- Kafka event IDs are the idempotency key. Evaluation Lua invalidation only advances to a strictly newer revision; a duplicate event does not evict a fresh same-revision cache entry.

- [x] Add tests proving outbox inserts roll back with domain writes, failed sends remain pending, retries publish, audit duplicates do not duplicate rows, and stale events do not regress cache state.
- [x] Implement migrations, transactional outbox, dispatcher, consumer retry/error metrics, and audit query; run focused tests.
- [x] Add Testcontainers PostgreSQL/Kafka/Redis coverage that skips without Docker; Docker-backed execution remains unavailable in this environment.

### Task 5: Rollout lifecycle and deterministic assignment

**Files:**
- Create: `rollout-service/src/main/resources/db/migration/V2__extend_rollout_lifecycle.sql`.
- Create: `rollout-service/src/main/java/com/cloudscale/rollout/{api/*,application/*,domain/*,persistence/*,security/*,events/*}`.
- Modify: `rollout-service/pom.xml`, `rollout-service/src/main/resources/application.yml`.
- Test: rollout application unit tests and update `RolloutServiceIntegrationTest`.

**Interfaces:**
- `POST /api/v1/organizations/{organizationId}/rollouts` accepts a feature flag UUID, environment UUID, ascending stages (0–100, final stage 100), and optional health policy.
- Lifecycle actions: start, pause, resume, advance, and explicit rollback; invalid transitions return a structured 409.
- `POST /api/v1/organizations/{organizationId}/rollouts/{rolloutId}/observations` accepts bounded request count, error rate, latency, and observation timestamp. Insufficient samples never trigger a pause; a threshold breach may pause but never automatically roll back.
- `POST /api/v1/organizations/{organizationId}/rollouts/assignment` hashes stable user ID + feature UUID + environment UUID with SHA-256 and returns a deterministic bucket and assignment.

- [x] Add failing tests for stable repeated assignment, stable distribution over 10,000 identities, stage validation/order, invalid transitions, and minimum-sample health decisions.
- [x] Implement service, repository, migration, and protected REST endpoints; run unit tests and Docker-optional integration tests.

### Task 6: Angular management client

**Files:**
- Modify: `frontend/src/app/app.component.ts`, `frontend/src/styles.css`, `frontend/src/index.html`.
- Create: `frontend/src/app/{api,auth,models,components}/*` following the existing Angular standalone conventions.
- Test: component and service specs under `frontend/src/app/`.
- Modify: `frontend/package.json` / `package-lock.json` only for reviewed, compatible vulnerability fixes.

**Interfaces:**
- Register/login uses Auth Service; bearer token is sent only to same-origin `/api/v1` paths; logged-out state has no protected actions.
- Workflows: project/environment selection, feature listing/create/update, version draft/publish, evaluation/debug, rollout controls, and audit history.
- Every request has loading, empty, validation, and error states; no action displays success before a successful API response.

- [x] Add failing specs for login validation, protected API authorization headers, error/empty states, and successful publish/evaluate flows using mocked HTTP responses.
- [x] Build the responsive Angular UI with accessible labels and keyboard operation; run `npm test` and `npm run build`.
- [x] Run the approved UI design skill's available checks only if the skill is installed; do not substitute an unapproved frontend skill.

### Task 7: Compose, Docker, observability, docs, and final report

**Files:**
- Modify: all six service `Dockerfile`s, `docker-compose.yml`, `infrastructure/prometheus/prometheus.yml`, `README.md`, `docs/architecture.md`, `.env.example` only if the edit is known not to overwrite the pre-existing change.
- Create if useful: `infrastructure/grafana/provisioning/dashboards/*`, `.github/workflows/ci.yml`, `docs/metrics-report.md`.
- Test: Compose config validation, Maven Wrapper build, frontend build/tests, API/security checks, runtime/Prometheus checks when Docker is available.

**Interfaces:**
- Canonical workflow is full-stack Compose; only the frontend/Gateway and infrastructure ports documented as host-facing are published, and Prometheus targets use Compose service DNS.
- Container healthchecks invoke tools present in their images; actuator health details are not exposed.
- README setup and limitations match actual runtime behavior and the final measured report.

- [x] Add healthcheck/compose configuration tests or run `docker compose config` when the CLI exists; ensure Compose secrets have local-only documented values and no secrets are logged. Docker CLI is unavailable; Ruby YAML/invariant checks passed, with runtime config explicitly unverified.
- [x] Update README/architecture to describe only implemented APIs and true verification commands.
- [x] Run Maven Wrapper backend verification, frontend CI commands, `npm audit`, security denial checks, and safe runtime checks available in the environment.
- [x] Record test counts, skips, coverage, benchmark conditions/results, dependency advisories, event/rollout metrics, and unmeasured items in `docs/metrics-report.md` and the final response.
