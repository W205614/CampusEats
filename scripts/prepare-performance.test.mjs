import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
function rejected(options, message) {
  const result = spawnSync(process.execPath, ['scripts/prepare-performance.mjs',
    '--project', options.project || 'campuseats-improvement-acceptance',
    '--config', options.envFile || '.local/never-read.env',
    '--output', options.output || '.local/never-written.json',
    '--base-url', options.url || 'http://127.0.0.1:28086'], { cwd: root, encoding: 'utf8', timeout: 5000 });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, message);
}
test('performance identity preparation rejects the daily project before accessing Docker', () => {
  for (const project of ['campuseats-v1', 'campuseats', 'campuseats-v1-perf', 'other-project',
    'campuseats-improvement-acceptance--invalid', 'campuseats-improvement/20261025'])
    rejected({ project }, /Only explicitly isolated/);
});

test('dated isolated projects pass the allowlist but still reject the daily configuration before Docker', () => {
  for (const project of ['campuseats-improvement-20261025', 'campuseats-improvement-acceptance',
    'campuseats-perf-20261025', 'campuseats-role-uat-20261025'])
    rejected({ project, envFile: '.env' }, /must stay inside/);
});
test('performance identity preparation rejects daily and remote URLs', () => {
  for (const url of ['http://127.0.0.1:18083', 'http://example.com:28086', 'http://localhost', 'http://private:do-not-print@127.0.0.1:28086', 'http://127.0.0.1:28086/?secret=do-not-print'])
    rejected({ url }, /isolated loopback/);
});
test('performance identity files cannot use the daily configuration or leave protected local storage', () => {
  rejected({ envFile: '.env' }, /must stay inside/);
  rejected({ output: '../session.json' }, /must stay inside/);
});
