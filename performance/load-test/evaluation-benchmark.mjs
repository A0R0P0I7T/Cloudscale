import { execFileSync, spawnSync } from 'node:child_process';
import { randomBytes, randomUUID } from 'node:crypto';
import { cpus, freemem, platform, release, totalmem } from 'node:os';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import process from 'node:process';

const DEFAULT_BASE_URL = 'http://127.0.0.1:8080';
const MIN_REQUESTS = 1_000;
const MIN_DURATION_MS = 60_000;
const MAX_BENCHMARK_MS = 9 * 60_000;
const COLD_FEATURES = 20;
const WARMUP_REQUESTS = 100;
const PRIMARY_CONCURRENCY = 10;
const WARM_CONCURRENCIES = [1, 5, PRIMARY_CONCURRENCY, PRIMARY_CONCURRENCY, PRIMARY_CONCURRENCY, 25];
const SERVICE = 'evaluation-service';
const METRICS_ENDPOINT = 'http://localhost:8084/actuator/prometheus';

export function assertLocalBaseUrl(value) {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol)
    || !['localhost', '127.0.0.1', '::1', '[::1]'].includes(url.hostname)) {
    throw new Error('Benchmark target must use localhost or loopback.');
  }
  return url.origin;
}

export function summarizeLatencies(latenciesMs, failures, durationSeconds, requests = latenciesMs.length + failures) {
  const sorted = [...latenciesMs].sort((left, right) => left - right);
  const percentile = (fraction) => sorted.length === 0
    ? null
    : sorted[Math.max(0, Math.ceil(fraction * sorted.length) - 1)];
  const succeeded = sorted.length;
  return {
    requests,
    succeeded,
    failed: failures,
    errorRatePercent: requests === 0 ? 0 : (failures / requests) * 100,
    minMs: sorted[0] ?? null,
    maxMs: sorted.at(-1) ?? null,
    p50Ms: percentile(0.5),
    p90Ms: percentile(0.9),
    p95Ms: percentile(0.95),
    p99Ms: percentile(0.99),
    throughputRps: durationSeconds <= 0 ? 0 : succeeded / durationSeconds,
    durationSeconds,
  };
}

function projectRoot() {
  return resolve(dirname(fileURLToPath(import.meta.url)), '../..');
}

function dockerCommand() {
  const binary = process.env.DOCKER_CLI
    ?? '/Applications/Docker.app/Contents/Resources/bin/docker';
  return binary;
}

function compose(args) {
  const envFile = process.env.CLOUDSCALE_COMPOSE_ENV_FILE;
  const project = process.env.CLOUDSCALE_COMPOSE_PROJECT;
  if (!envFile || !project) {
    throw new Error('Set CLOUDSCALE_COMPOSE_ENV_FILE and CLOUDSCALE_COMPOSE_PROJECT for local container metrics.');
  }
  const common = ['compose', '--project-name', project, '--env-file', envFile];
  return execFileSync(dockerCommand(), [...common, ...args], {
    cwd: projectRoot(),
    encoding: 'utf8',
    env: {
      ...process.env,
      PATH: `/Applications/Docker.app/Contents/Resources/bin:${process.env.PATH ?? ''}`,
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}

async function requestJson(baseUrl, path, { token, method = 'GET', body } = {}) {
  const headers = {
    Accept: 'application/json',
    'X-Correlation-Id': randomUUID(),
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  const response = await fetch(new URL(path, baseUrl), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    redirect: 'error',
    signal: AbortSignal.timeout(10_000),
  });
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = text.slice(0, 500);
  }
  if (!response.ok) {
    throw new Error(`${method} ${path} returned HTTP ${response.status}: ${JSON.stringify(payload).slice(0, 500)}`);
  }
  return payload;
}

async function post(baseUrl, path, body, token) {
  return requestJson(baseUrl, path, { token, method: 'POST', body });
}

function organizationPath(organizationId) {
  return `/api/v1/organizations/${encodeURIComponent(organizationId)}`;
}

function projectPath(organizationId, projectKey) {
  return `${organizationPath(organizationId)}/projects/${encodeURIComponent(projectKey)}`;
}

function environmentPath(organizationId, projectKey, environmentKey) {
  return `${projectPath(organizationId, projectKey)}/environments/${encodeURIComponent(environmentKey)}`;
}

function featurePath(organizationId, projectKey, environmentKey, featureKey) {
  return `${environmentPath(organizationId, projectKey, environmentKey)}/features/${encodeURIComponent(featureKey)}`;
}

async function createProjectContext(baseUrl, token, organizationId, projectKey, environmentKey) {
  await post(baseUrl, `${organizationPath(organizationId)}/projects`, {
    key: projectKey,
    name: 'Local Evaluation Benchmark',
    description: 'Dedicated local performance measurement data.',
  }, token);
  await post(baseUrl, `${projectPath(organizationId, projectKey)}/environments`, {
    key: environmentKey,
    name: 'Benchmark',
    description: 'Dedicated local performance measurement environment.',
  }, token);
}

async function createAndPublishFeature(baseUrl, token, organizationId, projectKey, environmentKey, featureKey) {
  const value = { variant: 'benchmark', enabled: true };
  await post(baseUrl, `${environmentPath(organizationId, projectKey, environmentKey)}/features`, {
    key: featureKey,
    name: 'Local evaluation benchmark',
    description: 'Dedicated benchmark flag.',
    enabled: true,
    value,
    targetingRules: [],
  }, token);
  await post(baseUrl, `${featurePath(organizationId, projectKey, environmentKey, featureKey)}/versions/1/publish`,
    {}, token);
  return value;
}

async function evaluate(baseUrl, token, context, featureKey) {
  const path = `${featurePath(context.organizationId, context.projectKey, context.environmentKey, featureKey)}/evaluate`;
  const response = await post(baseUrl, path, {
    userId: 'evaluation-benchmark-stable-user',
    attributes: { benchmark: true },
  }, token);
  if (response.featureKey !== featureKey || response.enabled !== true
    || JSON.stringify(response.value) !== JSON.stringify(context.value)) {
    throw new Error(`Evaluation result changed for benchmark feature ${featureKey}.`);
  }
  return response;
}

async function measureScenario({ baseUrl, token, context, featureKey, concurrency, label, deadline }) {
  const latenciesMs = [];
  let requests = 0;
  let failures = 0;
  let firstError = null;
  const startedAt = performance.now();
  const worker = async () => {
    while (!firstError) {
      const elapsed = performance.now() - startedAt;
      if (elapsed >= MIN_DURATION_MS && requests >= MIN_REQUESTS) return;
      if (Date.now() >= deadline) {
        firstError = new Error('Total benchmark reached the 9-minute safety deadline.');
        return;
      }
      const requestStarted = performance.now();
      requests += 1;
      try {
        await evaluate(baseUrl, token, context, featureKey);
        latenciesMs.push(performance.now() - requestStarted);
      } catch (error) {
        failures += 1;
        firstError = error;
      }
    }
  };
  await Promise.all(Array.from({ length: concurrency }, () => worker()));
  const durationSeconds = (performance.now() - startedAt) / 1000;
  const summary = summarizeLatencies(latenciesMs, failures, durationSeconds, requests);
  const result = {
    scenario: label,
    concurrency,
    ...summary,
    latencySamplesMs: latenciesMs,
    error: firstError?.message ?? null,
  };
  if (firstError) {
    throw Object.assign(new Error(`${label} stopped: ${firstError.message}`), { partialResult: result });
  }
  return result;
}

function parsePrometheus(text) {
  const metrics = {};
  for (const line of text.split(/\r?\n/)) {
    const match = line.match(/^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{[^}]*\})?\s+([-+0-9.eE]+)$/);
    if (match) metrics[match[1] + (match[2] ?? '')] = Number(match[3]);
  }
  const sum = (prefix) => Object.entries(metrics)
    .filter(([name]) => name === prefix || name.startsWith(`${prefix}{`))
    .reduce((total, [, value]) => total + value, 0);
  return {
    cacheHits: sum('cloudscale_evaluation_cache_hits_total'),
    cacheMisses: sum('cloudscale_evaluation_cache_misses_total'),
    cacheReadErrors: sum('cloudscale_evaluation_cache_read_errors_total'),
    cacheWriteErrors: sum('cloudscale_evaluation_cache_write_errors_total'),
    configurationFetchesSuccess: sum('cloudscale_evaluation_configuration_fetches_total{result="success"}'),
    configurationFetchesError: sum('cloudscale_evaluation_configuration_fetches_total{result="error"}'),
    configurationFetchErrors: sum('cloudscale_evaluation_configuration_fetch_errors_total'),
    evaluationDurationCount: sum('cloudscale_evaluation_duration_seconds_count'),
    evaluationDurationSumSeconds: sum('cloudscale_evaluation_duration_seconds_sum'),
    eventProcessed: sum('cloudscale_evaluation_configuration_events_processed_total'),
    eventStale: sum('cloudscale_evaluation_configuration_events_stale_total'),
    eventErrors: sum('cloudscale_evaluation_configuration_events_errors_total'),
    eventRetries: sum('cloudscale_evaluation_configuration_events_retries_total'),
  };
}

function metricsSnapshot() {
  const text = compose(['exec', '-T', SERVICE, 'curl', '-fsS', METRICS_ENDPOINT]);
  return parsePrometheus(text);
}

function difference(before, after) {
  return Object.fromEntries(Object.keys(before).map((key) => [key, after[key] - before[key]]));
}

function median(values) {
  const sorted = [...values].sort((left, right) => left - right);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
}

function medianSummary(runs) {
  const fields = ['p50Ms', 'p90Ms', 'p95Ms', 'p99Ms', 'minMs', 'maxMs', 'throughputRps', 'errorRatePercent'];
  return Object.fromEntries(fields.map((field) => [field, median(runs.map((run) => run[field]))]));
}

function dockerStats() {
  try {
    return compose(['stats', '--no-stream', '--format', '{{json .}}']);
  } catch {
    return 'unavailable';
  }
}

function serviceHealth() {
  const raw = compose(['ps', '--format', 'json']).trim();
  if (!raw) return [];
  let services;
  try {
    const parsed = JSON.parse(raw);
    services = Array.isArray(parsed) ? parsed : [parsed];
  } catch {
    services = raw.split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line));
  }
  return services.map((service) => ({
    name: service.Service ?? service.Name ?? null,
    state: service.State ?? service.Status ?? null,
    health: service.Health ?? null,
  }));
}

function verifyRuntimeHealth() {
  const health = serviceHealth();
  const byName = new Map(health.map((service) => [service.name, service]));
  const required = ['postgres', 'redis', 'kafka', 'auth-service', 'config-service',
    'rollout-service', 'evaluation-service', 'audit-service', 'gateway', 'frontend'];
  const unhealthy = required.filter((name) => {
    const service = byName.get(name);
    return !service || service.state !== 'running' || service.health !== 'healthy';
  });
  if (unhealthy.length) {
    throw new Error(`Required services are not healthy: ${unhealthy.join(', ')}`);
  }
  return health;
}

function javaVersion() {
  const javaPath = process.env.JAVA_HOME
    ? `${process.env.JAVA_HOME}/bin/java`
    : 'java';
  const result = spawnSync(javaPath, ['-version'], { encoding: 'utf8' });
  if (result.error || result.status !== 0) return 'unavailable';
  return `${result.stdout}${result.stderr}`.trim().split(/\r?\n/)[0];
}

async function main() {
  const baseUrl = assertLocalBaseUrl(process.env.CLOUDSCALE_BASE_URL ?? DEFAULT_BASE_URL);
  const benchmarkStartedAt = Date.now();
  const deadline = benchmarkStartedAt + MAX_BENCHMARK_MS;
  const runId = `${new Date().toISOString().replaceAll(':', '').replaceAll('.', '')}-${randomBytes(3).toString('hex')}`;
  const slug = `bench-${randomBytes(5).toString('hex')}`;
  const projectKey = 'evaluation-benchmark';
  const environmentKey = 'benchmark';
  const tokenLifetimePassword = randomBytes(24).toString('base64url');
  const resultsPath = resolve(process.env.BENCHMARK_RESULTS
    ?? `performance/load-test/results/evaluation-${runId}.json`);
  const output = {
    runId,
    createdAt: new Date().toISOString(),
    environment: {
      baseUrl,
      topology: 'end-to-end through Gateway',
      host: { platform: platform(), release: release(), arch: process.arch, logicalCpuCount: cpus().length },
      memory: { totalBytes: totalmem(), freeBytesAtStart: freemem() },
      node: process.version,
      javaVersion: javaVersion(),
      projectKey,
      environmentKey,
      dockerProject: process.env.CLOUDSCALE_COMPOSE_PROJECT,
    },
    setup: {},
    coldCache: null,
    warmup: { requested: WARMUP_REQUESTS, completed: 0 },
    warmRuns: [],
    primaryWarmMedian: null,
    metrics: {},
    serviceHealth: [],
    dockerStatsBefore: null,
    dockerStatsAfter: null,
    safetyDeadlineSeconds: MAX_BENCHMARK_MS / 1000,
  };

  try {
    output.serviceHealthBefore = verifyRuntimeHealth();
    const session = await post(baseUrl, '/api/v1/auth/register', {
      organizationName: `Local Evaluation Benchmark ${runId}`,
      organizationSlug: slug,
      fullName: 'Local Benchmark Operator',
      email: `operator-${slug}@example.invalid`,
      password: tokenLifetimePassword,
    });
    const { organizationId, accessToken: token } = session;
    output.setup = { organizationId, organizationSlug: slug, projectKey, environmentKey };
    const context = { organizationId, projectKey, environmentKey, value: { variant: 'benchmark', enabled: true } };
    await createProjectContext(baseUrl, token, organizationId, projectKey, environmentKey);

    const coldKeys = Array.from({ length: COLD_FEATURES }, (_, index) =>
      `cold-${String(index + 1).padStart(2, '0')}`);
    for (const key of coldKeys) {
      if (Date.now() >= deadline) throw new Error('Benchmark reached its 9-minute safety deadline during setup.');
      await createAndPublishFeature(baseUrl, token, organizationId, projectKey, environmentKey, key);
    }
    output.setup.coldFeatureKeys = coldKeys;
    const coldMetricsBefore = metricsSnapshot();
    const coldLatencies = [];
    const coldStarted = performance.now();
    for (const key of coldKeys) {
      if (Date.now() >= deadline) throw new Error('Benchmark reached its 9-minute safety deadline during cold-cache run.');
      const requestStarted = performance.now();
      await evaluate(baseUrl, token, context, key);
      coldLatencies.push(performance.now() - requestStarted);
    }
    const coldDurationSeconds = (performance.now() - coldStarted) / 1000;
    output.coldCache = {
      cacheEstablishment: `${COLD_FEATURES} unique feature snapshot keys; each was published but never evaluated before its single measured request; no Redis flush`,
      featureCount: COLD_FEATURES,
      ...summarizeLatencies(coldLatencies, 0, coldDurationSeconds, COLD_FEATURES),
      latencySamplesMs: coldLatencies,
      metrics: difference(coldMetricsBefore, metricsSnapshot()),
    };

    const warmFeature = `warm-${randomBytes(4).toString('hex')}`;
    output.setup.warmFeatureKey = warmFeature;
    context.value = await createAndPublishFeature(
      baseUrl, token, organizationId, projectKey, environmentKey, warmFeature);
    const verification = await evaluate(baseUrl, token, context, warmFeature);
    output.setup.expectedEvaluation = {
      featureKey: verification.featureKey,
      enabled: verification.enabled,
      value: verification.value,
      reason: verification.reason,
      versionNumber: verification.versionNumber,
    };
    for (let index = 0; index < WARMUP_REQUESTS; index += 1) {
      if (Date.now() >= deadline) throw new Error('Benchmark reached its 9-minute safety deadline during warm-up.');
      await evaluate(baseUrl, token, context, warmFeature);
      output.warmup.completed += 1;
    }

    output.metrics.warmBefore = metricsSnapshot();
    output.dockerStatsBefore = dockerStats();
    const repeats = new Map();
    for (const concurrency of WARM_CONCURRENCIES) {
      const prewarmMetricsBefore = metricsSnapshot();
      await evaluate(baseUrl, token, context, warmFeature);
      const prewarmMetricsAfterFirst = metricsSnapshot();
      await evaluate(baseUrl, token, context, warmFeature);
      const prewarmMetricsAfterSecond = metricsSnapshot();
      const prewarmFirstDelta = difference(prewarmMetricsBefore, prewarmMetricsAfterFirst);
      const prewarmSecondDelta = difference(prewarmMetricsAfterFirst, prewarmMetricsAfterSecond);
      if (prewarmSecondDelta.cacheHits < 1 || prewarmSecondDelta.configurationFetchesSuccess !== 0) {
        throw new Error('Could not verify a warm-cache hit immediately before a measured run.');
      }
      const runMetricsBefore = metricsSnapshot();
      const repeat = (repeats.get(concurrency) ?? 0) + 1;
      repeats.set(concurrency, repeat);
      const run = await measureScenario({
        baseUrl,
        token,
        context,
        featureKey: warmFeature,
        concurrency,
        label: `warm-cache-c${concurrency}-r${repeat}`,
        deadline,
      });
      run.cacheStateVerification = {
        prewarmFirstRequest: prewarmFirstDelta,
        confirmedWarmRequest: prewarmSecondDelta,
      };
      run.prometheusDelta = difference(runMetricsBefore, metricsSnapshot());
      output.warmRuns.push(run);
      output.serviceHealth = verifyRuntimeHealth();
      if (concurrency === PRIMARY_CONCURRENCY) {
        output.primaryWarmMedian = {
          concurrency,
          repeats: output.warmRuns.filter((item) => item.concurrency === concurrency).length,
          ...medianSummary(output.warmRuns.filter((item) => item.concurrency === concurrency)),
        };
      }
    }
    output.metrics.warmAfter = metricsSnapshot();
    output.metrics.warmDelta = difference(output.metrics.warmBefore, output.metrics.warmAfter);
    output.dockerStatsAfter = dockerStats();
    output.serviceHealth = serviceHealth();
    output.finishedAt = new Date().toISOString();
    output.totalBenchmarkDurationSeconds = (Date.now() - benchmarkStartedAt) / 1000;
    await mkdir(dirname(resultsPath), { recursive: true });
    await writeFile(resultsPath, `${JSON.stringify(output, null, 2)}\n`, { mode: 0o600 });
    console.log(JSON.stringify({
      status: 'complete',
      resultsPath,
      coldCache: { ...output.coldCache, latencySamplesMs: '[saved in JSON artifact]' },
      warmRuns: output.warmRuns.map(({ latencySamplesMs, ...summary }) => summary),
      primaryWarmMedian: output.primaryWarmMedian,
      metrics: output.metrics,
      serviceHealth: output.serviceHealth,
      dockerStatsBefore: output.dockerStatsBefore,
      dockerStatsAfter: output.dockerStatsAfter,
    }, null, 2));
  } catch (error) {
    if (error.partialResult) output.warmRuns.push(error.partialResult);
    output.error = error.message;
    output.finishedAt = new Date().toISOString();
    await mkdir(dirname(resultsPath), { recursive: true });
    await writeFile(resultsPath, `${JSON.stringify(output, null, 2)}\n`, { mode: 0o600 });
    console.error(JSON.stringify({ status: 'stopped', error: output.error, resultsPath }, null, 2));
    process.exitCode = 1;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
