# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Angular 22.3.0 / TypeScript frontend with Spring Boot services.

## Users

Assumption from the requested workflows: developers and product operators who manage an organization's feature configuration and rollout state.

## Product Purpose

CloudScale provides organization-scoped project/environment management, versioned feature configuration, targeting, evaluation, progressive rollout controls, and audit history. The current delivery goal is a locally demonstrable MVP that can be inspected and verified from source.

## Positioning

The product's implementation model uses PostgreSQL as the source of truth, Redis as an eventually consistent evaluation cache, and Kafka events to propagate configuration and rollout changes. This describes the requested architecture, not a market-leading or production-scale claim.

## Operating Context

An operator signs in, opens the Feature Flags workspace, selects an organization/project/environment, manages feature flags and versions, evaluates a flag for a user context, controls a rollout, and reviews audit events. The expected local demonstration uses the repository's Docker Compose runtime and the Gateway as the browser's API entry point.

## Capabilities and Constraints

- Organization membership and role claims govern access; organization IDs supplied in URLs are not authorization.
- Configuration versions are immutable and publishing is transactional.
- Evaluation uses targeting rules and a revision-aware Redis snapshot cache.
- Rollout assignment is deterministic. The current feature configuration contract does not define control/treatment variants, so assignment is reported by Rollout Service but is not applied to Evaluation responses.
- Health observations can safely pause a rollout when policy thresholds and minimum sample counts are met. Rollback is explicit, not automatic.
- Kafka delivery is at-least-once. Configuration events use a database outbox; Audit deduplicates by event ID.
- No public customer evidence, production metrics, logos, or product photographs are supplied. Do not invent claims, tenants, or performance results.
- The frontend must show loading, empty, validation, and error states, and must never report success unless the API confirms it.

## Evidence on Hand

The repository contains the service APIs, migrations, tests, Compose configuration, and frontend code. Runtime-dependent behavior must be checked against a running local environment; skipped tests and code inspection are not runtime evidence.

## Product Principles

- Tenant isolation is enforced at every service boundary.
- PostgreSQL owns durable configuration and event intent.
- A failed dependency degrades explicitly; it does not produce a false success.
- Operational metrics are reported only when measured.
- UI actions map to real API operations.

## Accessibility & Inclusion

No product-specific accessibility requirement was supplied. The web UI will use semantic controls, visible focus, keyboard operation, and responsive layouts as implementation baselines.
