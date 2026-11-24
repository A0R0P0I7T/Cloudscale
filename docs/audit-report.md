# CloudScale Repository Audit

**Baseline:** 2026-11-23, `master` at `d240e2e`. The working tree contained a user edit setting `JWT_ISSUER=cloudscale-authf`; that value is preserved.  
**Final pass:** 2026-11-24 on the same `master` checkout. Work remains uncommitted.

## Executive summary

The original skeleton gaps have been filled within the existing six-service architecture: Auth/JWT, Gateway routing and correlation IDs, per-service tenant/role enforcement, transactional outboxes, idempotent Audit ingestion, deterministic Rollout behavior, and an API-backed Angular management client. Compose now passes service DNS and shared local credentials, publishes host ports only on loopback, and proxies browser `/api` calls through Gateway. README and architecture docs now describe the implementation rather than the old phase plan.

The read-only whole-working-tree review found no Critical issues. Important findings were addressed with regression tests; Docker-dependent follow-through remains explicitly unverified.

The final code is not runtime-certified: Docker CLI/daemon are unavailable. Docker-backed integration tests, Compose image builds/startup, live Gateway requests, Redis/Kafka behavior, Prometheus targets, and request-load metrics therefore remain unverified. See [`metrics-report.md`](metrics-report.md) for exact commands, counts, measured values, and limitations.

## Final audit matrix

| Capability | Status | Evidence | Severity | Intended action |
| --- | --- | --- | --- | --- |
| Maven Wrapper and CI | complete | `.mvn/wrapper/maven-wrapper.properties` pins Maven 3.9.11 with checksum; `.github/workflows/ci.yml` uses Java 21 and Node 22; final clean verification passed 8/8 modules, 102 tests, 85 passed, 17 Docker skips. | Low | Keep using `./mvnw`; observe the first remote CI run when changes are pushed. |
| Auth registration/login and JWT issuance | complete | `auth-service/src/main/java/com/cloudscale/auth/`; BCrypt hashing, membership-derived HS256 tokens, issuer/secret validation, and Auth unit tests. | High | Run the Docker-backed Auth integration tests when Docker is available. |
| Gateway routes and correlation IDs | complete | `gateway/src/main/resources/application.yml`, `GatewaySecurityConfiguration`, `CorrelationIdFilter`, and gateway tests. Compose supplies service-DNS upstreams. | High | Verify the live Gateway-to-service path with Compose. |
| Tenant and role isolation | partial | Request-level security-chain tests now cover Configuration, Evaluation, Rollout, and Audit; decoder tests reject weak secrets and wrong issuers. Browser tokens are restricted to same-origin `/api/v1`; external-URL leakage has a regression test. | Critical | Run the Testcontainers-backed service HTTP checks when Docker is available. |
| Configuration lifecycle and migrations | partial | Version/draft/publish logic, transactional outbox, Flyway V2, and unit/integration test sources are present. | High | Execute PostgreSQL-backed migration/publish tests with Docker. |
| Evaluation and Redis consistency | partial | Targeting evaluator, cache fallback, metrics, and revision-aware Lua write/invalidation scripts are present; Redis tests exist. | High | Run Redis integration and cold/warm cache checks with Docker. |
| Kafka outbox and Audit | partial | Configuration/Rollout outboxes, versioned event contracts, Audit idempotency/query APIs, retry tests, and a PostgreSQL-backed persisted-row retry test are present. The persisted-row test is Docker-skipped locally. | High | Validate live broker acknowledgement, replay, consumer recovery, and end-to-end audit queries. Infinite retry can block a partition on a poison event; no DLT is configured under the two-topic limit. |
| Rollout assignment and lifecycle | partial | SHA-256 bucketing, stages, manual lifecycle operations, health observations, safe automatic pause, and Java tests. Assignment lookup retains the latest rolled-back rollout at zero exposure instead of falling back to older completed exposure. Missing stages return validation errors. | High | Run database-backed lifecycle/assignment/tenant tests. Assignment remains diagnostic and is not wired into Evaluation; rollback is manual. |
| Angular management client | partial | Auth, feature/version, evaluation, rollout, assignment, and audit workspaces; 15 browser specs cover auth validation/loading/errors, bearer scoping, empty/Viewer states, publish, evaluate, and Viewer write denial. | Medium | Perform authenticated full-stack browser checks after Compose is available. |
| Compose and container images | partial | Compose passes JWT/upstream config, requires local secrets, uses loopback-only published ports, and has service healthchecks; all six Java images use the Maven Wrapper and install `curl`. Ruby YAML/invariant validation passed. | High | Run `docker compose --env-file .env config --quiet`, build, and health-check on a machine with Docker. No image was built here. |
| Monitoring | partial | Prometheus has six Compose-DNS targets; Actuator exposes health/info/Prometheus without health details; Grafana has a Prometheus data source and README PromQL examples. | Medium | Check `/targets` and Grafana against a live runtime. No dashboard or scrape result is claimed. |
| Documentation and local secrets | complete | README/architecture and `PRODUCT.md` match current APIs, Angular 22.3.0, limits, ports, setup and verification. `.env.example` has blank required secrets; issuer edit is preserved; unused OTLP endpoint and stale actuator phase labels were removed. | Low | Fill `.env` with separate generated values before Compose startup; never commit `.env`. |
| Performance and live runtime | unverified | Docker-backed workload, service health, Prometheus, Redis/Kafka/DB counters, and request latency were not measured. Source coverage is generated separately with a one-off JaCoCo command. | Medium | Use the exact runtime/benchmark steps in `metrics-report.md` after installing Docker. |
| Code coverage | partial | Frontend: 40.04% lines; six backend service modules: 41.30% lines via one-off JaCoCo; Docker integration tests were skipped. | Medium | Add/maintain a project-owned coverage plugin and run coverage with Docker-enabled integration tests if a committed coverage gate is desired. |
| Visual finish | complete for logged-out surface | Impeccable screenshots at 1440×900 and 390×844; final `impeccable detect --json --changed` returned `[]`. Authenticated visual state was not captured without a live backend. | Low | Recheck authenticated workspaces and interaction states during the Compose run. |

## Visual finish verdict

The login surface was reviewed at desktop and phone sizes. The final pass removed an unnecessary auth eyebrow and decorative navigation numbering, improved the disabled sign-in contrast, and preserved the release-timetable direction. Captures: `/private/var/folders/t0/rlmgz1vd7jvf2097nywt16nr0000gn/T/opencode/cloudscale-ui-shots-final-3/1440x900.png` and `.../390x844.png`. The shared token/component guide is in [`../DESIGN.md`](../DESIGN.md); its source capture is the `.impeccable/surfaces/` brief. No raster assets ship.

## Historical baseline

The baseline audit found missing Auth/Gateway/Tenant authorization, non-durable Kafka publishing, empty Audit/Rollout services, a static Angular shell, absent Maven Wrapper/CI, Docker healthchecks using unavailable tools, unsafe default credentials, and stale phase documentation. Those implementation gaps were addressed; only runtime-dependent verification and the limitations called out above remain.
