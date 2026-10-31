const { test } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { once } = require('node:events');
const path = require('node:path');
const fs = require('node:fs');
const { applyPatches } = require('../../client/tooling/apply-compat-patches.cjs');
const patches = require('./compat-patches.json');
const { readFileFromUrl } = require('../node_modules/@redocly/openapi-core/lib/utils.js');

test('Redocly reads a real HTTP document and selects headers through patched minimatch', async () => {
  const requests = [];
  const server = http.createServer((request, response) => {
    requests.push(request.headers);
    response.setHeader('Content-Type', 'application/json');
    response.end('{"openapi":"3.0.3"}');
  });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const authority = '127.0.0.1:' + server.address().port;
  try {
    for (const matches of [`http://${authority}/schemas/**`, `${authority}/schemas/**`]) {
      const document = await readFileFromUrl(`http://${authority}/schemas/order.json`, {
        headers: [
          { matches, name: 'X-Matching-Fixture', value: 'selected' },
          { matches: 'https://not-this-server.invalid/**', name: 'X-Other-Fixture', value: 'excluded' },
        ],
      });
      assert.deepEqual(JSON.parse(document.body), { openapi: '3.0.3' });
    }
    assert.equal(requests.length, 2);
    for (const headers of requests) {
      assert.equal(headers['x-matching-fixture'], 'selected');
      assert.equal(headers['x-other-fixture'], undefined);
    }
  } finally {
    await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  }
});

test('a repeated install leaves the reviewed Redocly file unchanged', () => {
  const root = path.resolve(__dirname, '..');
  const file = path.join(root, 'node_modules/@redocly/openapi-core/lib/utils.js');
  const before = fs.readFileSync(file);
  assert.equal(applyPatches(root, false, patches), 0);
  assert.deepEqual(fs.readFileSync(file), before);
});
