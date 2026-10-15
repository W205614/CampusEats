import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const root = fileURLToPath(new URL('../', import.meta.url));
const checker = path.join(root, 'scripts/check-client-audit.mjs');
const audit = JSON.parse(fs.readFileSync(path.join(root, '.local/audit-client.json'), 'utf8'));
const review = JSON.parse(fs.readFileSync(path.join(root, 'frontend/client/audit-exceptions.json'), 'utf8'));

function check(report, exception = review) {
  const fixture = fs.mkdtempSync(path.join(os.tmpdir(), 'campuseats-audit-'));
  try {
    fs.mkdirSync(path.join(fixture, '.local'));
    fs.mkdirSync(path.join(fixture, 'frontend/client'), { recursive: true });
    fs.writeFileSync(path.join(fixture, '.local/audit-client.json'), JSON.stringify(report));
    fs.writeFileSync(path.join(fixture, 'frontend/client/audit-exceptions.json'), JSON.stringify(exception));
    return spawnSync(process.execPath, [checker], { cwd: fixture, encoding: 'utf8', timeout: 10000 });
  } finally {
    assert.equal(path.dirname(path.resolve(fixture)), path.resolve(os.tmpdir()));
    assert.ok(path.basename(fixture).startsWith('campuseats-audit-'));
    fs.rmSync(fixture, { recursive: true, force: true });
  }
}

test('the reviewed current report passes', () => {
  const result = check(audit);
  assert.equal(result.status, 0, result.stderr);
});

test('a new high advisory on an allowed package is rejected', () => {
  const changed = structuredClone(audit);
  changed.vulnerabilities.braces.via = [{ severity: 'high', url: 'https://example.invalid/new-advisory' }];
  const result = check(changed);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Unreviewed high\/critical dependencies:.*braces/s);
});

test('an unreviewed high package cannot inherit the exception', () => {
  const changed = structuredClone(audit);
  changed.vulnerabilities['unexpected-tool'] = { name: 'unexpected-tool', severity: 'high', via: ['braces'] };
  const result = check(changed);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Unreviewed high\/critical dependencies:.*unexpected-tool/s);
});

test('critical severity is rejected even for an allowed package', () => {
  const changed = structuredClone(audit);
  changed.vulnerabilities.braces.severity = 'critical';
  const result = check(changed);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Unreviewed high\/critical dependencies:.*braces/s);
});

test('expired exceptions fail closed', () => {
  const result = check(audit, { ...review, expiresOn: '2000-01-01' });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /exceptions require review/);
});

test('missing audit results cannot pass', () => {
  const result = check({});
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Audit did not return a report/);
});
