import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { test } from 'node:test';

const root = resolve(import.meta.dirname, '..');
const expected = await readFile(resolve(root, 'deploy/dish.svg'), 'utf8');

for (const [app, cli, port] of [
  ['admin', 'vite/bin/vite.js', 15173],
  ['client', '@dcloudio/vite-plugin-uni/bin/uni.js', 15174],
]) {
  test(`${app} development server serves the shared demo SVG`, { timeout: 90000 }, async () => {
    const cwd = resolve(root, 'frontend', app);
    const child = spawn(
      process.execPath,
      [resolve(cwd, 'node_modules', cli), '--host', '127.0.0.1', '--port', String(port), '--strictPort'],
      { cwd, env: process.env, stdio: ['ignore', 'pipe', 'pipe'] },
    );
    let output = '';
    child.stdout.on('data', (chunk) => { output += chunk; });
    child.stderr.on('data', (chunk) => { output += chunk; });
    try {
      const origin = `http://127.0.0.1:${port}`;
      let response;
      const deadline = Date.now() + 60000;
      while (Date.now() < deadline) {
        assert.equal(child.exitCode, null, output);
        try {
          response = await fetch(`${origin}/demo-images/dish.svg`, {
            signal: AbortSignal.timeout(1000),
          });
          break;
        } catch {
          await new Promise((done) => setTimeout(done, 200));
        }
      }
      assert.ok(response, output);
      assert.equal(response.status, 200);
      assert.match(response.headers.get('content-type') || '', /image\/svg\+xml/);
      assert.equal(await response.text(), expected);
      const head = await fetch(`${origin}/demo-images/dish.svg?preview=1`, {
        method: 'HEAD',
      });
      assert.equal(head.status, 200);
      assert.equal(await head.text(), '');
      const other = await fetch(`${origin}/demo-images/missing.svg`);
      assert.equal(other.status, 404);
    } finally {
      child.kill();
      await new Promise((done) =>
        child.exitCode !== null ? done() : child.once('exit', done),
      );
    }
  });
}
