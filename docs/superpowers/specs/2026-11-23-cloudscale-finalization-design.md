# CloudScale Finalization Design

## Intent

Bring the existing CloudScale repository to a locally demonstrable, security-conscious MVP and leave behind a reproducible, evidence-based account of what works. The goal is coherent behavior across the existing six services, not production certification.

## Goals

- Preserve the existing service boundaries and the current working tree, including unrelated user edits.
- Provide password-hashed authentication, signed JWTs, tenant and role checks, reachable Gateway routes, and correlation IDs.
- Preserve Configuration Service's database ownership and Evaluation Service's Redis read-through behavior while fixing event-delivery reliability.
- Complete Audit and Rollout workflows only to the extent their contracts and runtime behavior can be verified.
- Make Compose, Dockerfiles, CI, the Angular client, and documentation agree on a safe local workflow.
- Measure only outcomes actually observed; clearly mark Docker-dependent or production-scale metrics that cannot be measured locally.

## Non-goals

- Production identity proofing, email verification, password reset, rate limiting, external deployment, or production performance claims.
- Automated rollback unless an implemented observation policy and its safety behavior can be exercised by tests.
- New runtime services, cross-service database access, cross-service foreign keys, or speculative request coalescing.

## Constraints and current findings

- Keep the work on the current `master` checkout. The pre-existing edit in `.env.example` (`JWT_ISSUER=cloudscale-authf`) is user work and must not be changed or reverted.
- Use only existing service ownership and the established PostgreSQL/Redis/Kafka stack. Add no new service.
- Java 21.0.13.1 and Maven 3.9.11 are available in a checksum-verified temporary toolchain; Docker is unavailable.
- The current configuration/evaluation code exists, but external requests are unauthenticated. Auth, Gateway, Rollout, Audit, and the Angular UI are largely skeletons.
- Configuration events are sent asynchronously after commit rather than persisted atomically; delivery can be lost.
- The frontend currently builds and its single existing test passes. npm reports 60 advisories in the locked dependency graph; avoid unreviewed major upgrades.
- Testcontainers integration tests cannot run without a Docker daemon. Keep them meaningful and separately report this limitation.

## Approaches considered

1. **Document-only finish.** Correct the phase claims and report missing work, but leave the product unauthenticated and the three service skeletons untouched. Rejected because the request requires implementation.
2. **Collapse behavior into the Gateway or Configuration Service.** This would shorten the work but blur Auth, Rollout, and Audit ownership and contradict the existing boundaries. Rejected.
3. **Incremental vertical completion within the existing six services.** Add the narrowest APIs and security needed to make the current configuration/evaluation path usable, then complete reliable events, rollout/audit integration, frontend, automation, and truthful docs. Selected because it preserves existing ownership while delivering a demonstrable system.

## Design decisions

- Auth Service is the authority for users, organizations, and memberships. It hashes passwords with BCrypt and issues short-lived HS256 JWTs whose organization and role are membership-derived.
- The Gateway validates tokens at the edge and routes the public API. Each data-owning service also validates tokens and compares the authenticated organization claim to the organization in the request path; URL tenant IDs are never trusted on their own.
- Configuration writes place versioned events into a PostgreSQL outbox in the same transaction as the configuration mutation. A retrying publisher delivers them at least once. Consumers are idempotent by event ID/revision.
- Evaluation retains the existing Redis snapshot and revision-aware Lua behavior. Configuration fallback remains synchronous on cache miss/error; no locks or coalescing are added.
- Rollout assignment uses a stable cryptographic hash and explicit stages. Automated health rollback is only enabled if health observations, thresholds, sample floors, and lifecycle transitions are implemented and covered.
- Audit persists versioned domain events idempotently and exposes organization-scoped queries.
- Compose is the canonical local runtime unless inspection during implementation shows a simpler documented split. No production claims will be made.
- The Angular client must call only real APIs. Any workflow without a backend operation is disabled or shown as unavailable, never reported as successful.

## Implementation stages

1. Add an evidence-based audit/status report and establish reproducible Maven/CI/frontend checks.
2. Add authentication, service-level tenant/role enforcement, Gateway routing, and correlation propagation.
3. Protect and verify configuration/evaluation lifecycle and cache behavior; address outbox delivery and Audit consumption.
4. Implement deterministic Rollout lifecycle and observation behavior where supportable by existing schema/contracts.
5. Build an Angular management UI against implemented APIs, with loading, empty, validation, and error states.
6. Align Compose, Dockerfiles, monitoring, setup instructions, and the final metrics/security report.

## Verification and evidence

- Run the Maven reactor build and Docker-independent unit tests using the temporary Java/Maven toolchain; run Testcontainers and Compose checks only if Docker becomes available.
- Run frontend install, production build, and browser tests; assess dependency advisories before changing the lockfile.
- Add tests before behavior changes, including tenant denial, token validation, outbox retry/idempotency, cache revision ordering, deterministic rollout assignment, and UI states where their workflows are implemented.
- Benchmark evaluation only against a running, measured local runtime. Include warm/cold cache conditions, load, sample size, percentiles, error rate, cache/fetch counters, and the exact evidence file. Mark runtime metrics not measured when services cannot run.
- Do not run destructive database or Docker cleanup. Do not publish or deploy.
