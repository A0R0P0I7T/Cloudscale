import test from 'node:test';
import assert from 'node:assert/strict';
import { summarizeLatencies, assertLocalBaseUrl } from './evaluation-benchmark.mjs';

test('summarizes nearest-rank latency percentiles and measured throughput', () => {
  const result = summarizeLatencies([1, 2, 3, 4, 5, 6, 7, 8], 2, 0.5, 10);

  assert.deepEqual(result, {
    requests: 10,
    succeeded: 8,
    failed: 2,
    errorRatePercent: 20,
    minMs: 1,
    maxMs: 8,
    p50Ms: 4,
    p90Ms: 8,
    p95Ms: 8,
    p99Ms: 8,
    throughputRps: 16,
    durationSeconds: 0.5,
  });
});

test('refuses non-local benchmark hosts', () => {
  assert.equal(assertLocalBaseUrl('http://127.0.0.1:8080'), 'http://127.0.0.1:8080');
  assert.equal(assertLocalBaseUrl('http://localhost:8080'), 'http://localhost:8080');
  assert.throws(() => assertLocalBaseUrl('https://example.invalid'), /localhost or loopback/);
});
