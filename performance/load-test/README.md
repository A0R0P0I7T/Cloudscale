# Local Evaluation API benchmark

`evaluation-benchmark.mjs` is a dependency-free Node.js harness. It refuses non-local targets, registers a unique benchmark organization, creates a dedicated project/environment and published flags, verifies each response, and never stores or prints bearer tokens or passwords.

It measures end-to-end requests through the local Gateway at concurrency 1, 5, 10, and 25. Each warm scenario runs for at least 60 seconds and at least 1,000 successful evaluations; concurrency 10 is repeated three times for the primary median. It then reports a cold-cache sample using 20 unique published feature keys, each evaluated once. No cache is flushed.

Run only against a local Compose project, with the same restricted env file used to start it:

```bash
export CLOUDSCALE_COMPOSE_PROJECT=cloudscale-eval-bench
export CLOUDSCALE_COMPOSE_ENV_FILE="$HOME/.config/cloudscale/benchmark.env"
export CLOUDSCALE_BASE_URL=http://127.0.0.1:8080
node performance/load-test/evaluation-benchmark.mjs
```

The env file must exist and contain required local Compose secrets. Do not point the script at production/staging or check secrets into the repository. The harness has a 9-minute safety deadline, aborts on the first HTTP/result/health error, captures Prometheus counter deltas through `docker compose exec`, and writes raw latency samples plus summaries under `performance/load-test/results/` without credentials. `docker compose ps` health is checked before requests and after each run.

The benchmark organization and its records are intentionally retained in the benchmark-specific Compose database volume. There is no supported organization/project delete API, and the run must not remove database volumes. To stop benchmark containers without deleting data:

```bash
docker compose --project-name "$CLOUDSCALE_COMPOSE_PROJECT" \
  --env-file "$CLOUDSCALE_COMPOSE_ENV_FILE" down
```

The command does not use `-v`; data is preserved for inspection. Test the harness’s local-host guard and percentile math without a server:

```bash
node --test performance/load-test/evaluation-benchmark.test.mjs
```
