import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isolatedProject, percentile, stageSummary, acceptedStage } from './measure-performance.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

test('measurement uses the same dated isolated project allowlist as fixture preparation', () => {
  for (const name of ['campuseats-improvement-20261025', 'campuseats-perf', 'campuseats-role-uat-20261025'])
    assert.equal(isolatedProject(name), true);
  for (const name of ['campuseats', 'campuseats-v1', 'campuseats-v1-perf', 'other-project', 'campuseats-perf--unsafe'])
    assert.equal(isolatedProject(name), false);
});

test('daily measurement targets reject before reading credentials or accessing Docker', () => {
  for (const [BASE_URL, PERF_PROJECT_NAME] of [
    ['http://127.0.0.1:18083', 'campuseats-improvement-20261025'],
    ['http://127.0.0.1:28086', 'campuseats-v1'],
    ['http://example.com:28086', 'campuseats-perf'],
    ['http://user:secret@127.0.0.1:28086', 'campuseats-perf'],
  ]) {
    const result = spawnSync(process.execPath, ['scripts/measure-performance.mjs'], {
      cwd: root, encoding: 'utf8', timeout: 5000,
      env: { ...process.env, BASE_URL, PERF_PROJECT_NAME, ADMIN_PASSWORD: 'never-print-this-password',
        PERF_USER_TOKENS_FILE: '.local/never-read.json' },
    });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Performance measurement failed/);
    assert.doesNotMatch(result.stderr + result.stdout, /never-print-this-password|user:secret/);
  }
});

test('stage summaries separate virtual clients, actual in-flight requests, reads and full transactions', () => {
  const current = { kind: 'mixed', concurrency: 30, requestedSeconds: 300, latencies: [5, 10, 20, 100],
    readLatencies: [5, 10, 20], transactionLatencies: [1500, 2500], succeeded: 4,
    unexpectedErrors: 0, workerFailures: 0, errors: {}, peakInFlightRequests: 7,
    completedTransactions: 2, operations: { menu: { count: 3, latencies: [5, 10, 20] } } };
  const summary = stageSummary(current, 300);
  assert.equal(summary.concurrency, 30);
  assert.equal(summary.peakInFlightRequests, 7);
  assert.equal(summary.httpP95Ms, 100);
  assert.equal(summary.readP95Ms, 20);
  assert.equal(summary.transactionP95Ms, 2500);
  assert.equal(summary.requestThroughputPerSecond, 0.01);
  assert.deepEqual(summary.operations.menu, { count: 3, httpP95Ms: 20 });
  assert.equal(percentile([]), null);
});

test('acceptance fails closed for empty traffic, errors, slow reads and incomplete or slow mixed transactions', () => {
  const good = { kind: 'mixed', requests: 1000, unexpectedErrors: 0, workerFailures: 0,
    readP95Ms: 1000, completedTransactions: 15, transactionP95Ms: 10000 };
  assert.equal(acceptedStage(good), true);
  for (const delta of [{ requests: 0 }, { unexpectedErrors: 1 }, { workerFailures: 1 },
    { readP95Ms: null }, { readP95Ms: 1000.01 }, { completedTransactions: 14 },
    { completedTransactions: 16 }, { transactionP95Ms: null }, { transactionP95Ms: 10000.01 }])
    assert.equal(acceptedStage({ ...good, ...delta }), false);
  assert.equal(acceptedStage({ ...good, kind: 'read', completedTransactions: 0, transactionP95Ms: null }), true);
  assert.equal(acceptedStage({ ...good, expectedTransactions: 90, completedTransactions: 90 }), true);
  assert.equal(acceptedStage({ ...good, expectedTransactions: 90 }), false);
  assert.equal(acceptedStage({ ...good, operations: { 'finance.reconcile': { httpP95Ms: 1001 } } }), false);
});
