# Local Evaluation API Benchmark

**Result:** completed through Gateway; no failed requests. Local-development measurements only, not production capacity estimates.

The previously blocked startup was resolved in this run: both `RolloutRules` and `StableRolloutBucketer` are now provided by `RolloutDomainConfiguration`. The focused configuration test passed and the healthy Rollout container confirms the application context can wire `RolloutApplicationService`.

Root cause: both stateless collaborators were plain Java classes, while `RolloutApplicationService` constructor-injects them and neither a component annotation nor configuration bean definitions existed. Added explicit `@Bean` registrations and `RolloutDomainConfigurationTest`; the test failed before bean definitions and passed after. `./mvnw -B clean verify` then passed all 8 modules: 103 tests, 86 passed, 17 Testcontainers skips, 0 failures/errors.

## Environment and method

- Host: macOS 27.0.1, arm64, 10 logical CPUs, 24 GiB RAM; Node process reported 73.8 MiB free host memory at benchmark start.
- Runtime: Docker Desktop Engine 29.9.2, Compose 5.6.1; 10 CPUs and 7.748 GiB assigned to the Docker engine; Java Temurin 21.0.13.1; Node 24.22.0.
- Topology: Node 24 built-in `fetch` → `http://127.0.0.1:8080` Gateway → Evaluation Service → Redis, with PostgreSQL-backed Configuration Service fallback.
- Data: new benchmark-only organization, project `evaluation-benchmark`, environment `benchmark`, stable user ID, and a published enabled flag with a deterministic JSON value. Every response was checked for feature key, enabled state, and value. The account token/password are not stored in the artifact.
- Warm: one hundred warm-up evaluations; each measured run also had two preflight checks, with the second confirmed as a Prometheus cache hit and no config fetch. The configured cache TTL is 60 seconds, equal to each 60-second measurement window. The measured runs recorded misses/fetches equal to concurrency (1, 5, 10, 10, 10, 25); these counts are retained and mean the runs are **warm-start, not hit-only**.
- Cold: twenty unique published feature keys that had never been evaluated; one sequential measured request per key. No Redis flush or shared cache mutation was used.
- Warm samples: six sequential runs, each at least 60 seconds and 1,000 successful requests; concurrency 10 repeated three times. No request failures, timeouts, or health changes occurred.
- Instrumentation: before/after Evaluation Service Prometheus counter snapshots via `docker compose exec`; Docker stats snapshots were also captured. No benchmark tool or runtime package was installed.

## Warm-cache runs

Latency values are milliseconds; throughput is successful end-to-end requests per second.

| Run | Concurrency | Requests | Min | p50 | p90 | p95 | p99 | Max | Throughput | Errors |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Warm 1 | 1 | 69,759 | 0.563 | 0.739 | 1.056 | 1.435 | 3.053 | 22.115 | 1,162.63 req/s | 0 (0%) |
| Warm 2 | 5 | 281,631 | 0.584 | 0.966 | 1.282 | 1.594 | 3.276 | 18.347 | 4,693.82 req/s | 0 (0%) |
| Primary 1 | 10 | 390,350 | 0.642 | 1.403 | 2.008 | 2.492 | 4.291 | 24.216 | 6,505.77 req/s | 0 (0%) |
| Primary 2 | 10 | 395,482 | 0.648 | 1.383 | 1.974 | 2.450 | 4.345 | 22.665 | 6,591.29 req/s | 0 (0%) |
| Primary 3 | 10 | 401,703 | 0.571 | 1.370 | 1.911 | 2.338 | 4.361 | 32.326 | 6,694.96 req/s | 0 (0%) |
| Warm 3 | 25 | 464,137 | 0.656 | 2.886 | 4.879 | 5.893 | 8.691 | 52.102 | 7,735.39 req/s | 0 (0%) |

All runs lasted approximately 60.001 seconds. Across the three primary concurrency-10 runs (1,187,535 requests), the median of the run-level percentiles/throughputs was:

| Min | p50 | p90 | p95 | p99 | Max | Throughput | Error rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 0.642 ms | 1.383 ms | 1.974 ms | 2.450 ms | 4.345 ms | 24.216 ms | 6,591.29 requests/s | 0% |

## Cold-cache run

| Scenario | Concurrency | Requests | Min | p50 | p90 | p95 | p99 | Max | Throughput | Errors |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 20 unique cold snapshots, sequential | 1 | 20 | 6.894 ms | 7.909 ms | 9.710 ms | 10.620 ms | 118.486 ms | 118.486 ms | 73.26 requests/s | 0 (0%) |

The p99 equals the slowest request because the sample has only 20 requests. Prometheus recorded 20 cache misses, 20 successful Configuration fetches, and zero cache/fetch errors. The request exceeded the ordinary warm-cache duration because each cold request exercised Configuration Service and cache population.

## Prometheus and runtime health

| Scope | Cache hits | Cache misses | Successful config fetches | Fetch errors | Eval timer count | Eval timer sum |
|---|---:|---:|---:|---:|---:|---:|
| Cold sample | 0 | 20 | 20 | 0 | 20 | 0.1319 s |
| Warm measured windows plus preflight checks | 2,003,013 | 61 | 61 | 0 | 2,003,074 | 460.3120 s |

Warm metric totals include 12 preflight cache-verification requests in addition to 2,003,062 measured warm requests. There were zero cache read/write errors; 21 stale/duplicate configuration events were ignored, with zero processed-event errors or retries. These are process-level counters, not per-request latency measurements.

Before and after the benchmark, all six Prometheus targets (`gateway`, `auth-service`, `config-service`, `rollout-service`, `evaluation-service`, `audit-service`) reported `up`. Gateway health returned `UP`; Grafana `/api/health` returned database `ok`, version 11.5.2; Prometheus `/-/healthy` returned healthy. All required Compose services stayed healthy.

Docker stats snapshots (not peaks or averages): Evaluation Service memory rose from 461.6 MiB to 749.8 MiB; Gateway from 449.0 MiB to 696.5 MiB. The after-run CPU snapshots were 0.37% for Evaluation and 0.14% for Gateway, taken after measured requests had ended; they must not be interpreted as CPU usage during the load. The host’s low reported free memory (73.8 MiB at start) may have influenced this local run.

## Reproduction and artifacts

Run with a local-only Compose env file and a running stack:

```bash
export CLOUDSCALE_COMPOSE_PROJECT=cloudscale-eval-bench
export CLOUDSCALE_COMPOSE_ENV_FILE="$HOME/.config/cloudscale/benchmark.env"
export CLOUDSCALE_BASE_URL=http://127.0.0.1:8080
node performance/load-test/evaluation-benchmark.mjs
```

The harness enforces loopback-only URLs, a nine-minute deadline, 10-second per-request timeouts, result validation, health checks, and no output of tokens/secrets. Harness tests: `node --test performance/load-test/evaluation-benchmark.test.mjs` (3/3 passed).

- Raw request latencies, run summaries, Prometheus deltas, Docker stats and health snapshot: [`evaluation-2026-11-24T151843349Z-38ead2.json`](evaluation-2026-11-24T151843349Z-38ead2.json)
- Harness: [`../evaluation-benchmark.mjs`](../evaluation-benchmark.mjs)
- Harness instructions: [`../README.md`](../README.md)
- Startup-blocker note from the earlier attempt (superseded): [`evaluation-local-blocked-20261124.json`](evaluation-local-blocked-20261124.json)

No containers are running at final inspection; an attempted `docker compose down` reported no project resources to remove after Docker's state reset during backend verification. All five volumes for this project and the five volumes from the prior blocked attempt remain present; no `down -v` or volume deletion was used. Benchmark records persist in the current run's PostgreSQL volume. No performance tuning was performed.
