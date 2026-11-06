import fs from 'node:fs/promises';
import path from 'node:path';
import http from 'node:http';
import { randomBytes, randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const project = process.env.CLUSTER_PROJECT || '';
const configPath = path.resolve(root, process.env.CLUSTER_CONFIG || '');
const a = new URL(process.env.CLUSTER_NODE_A || '');
const b = new URL(process.env.CLUSTER_NODE_B || '');
if (!/^campuseats-improvement-cluster-[a-z0-9-]+$/.test(project) ||
    !configPath.startsWith(path.join(root, '.local') + path.sep)) throw Error('Use an isolated cluster project and private configuration.');
for (const base of [a, b]) {
  if (base.protocol !== 'http:' || base.hostname !== '127.0.0.1' || !base.port ||
      base.port === '18083' || base.pathname !== '/' || base.search || base.hash || base.username || base.password)
    throw Error('Use explicit isolated loopback ports.');
}
function docker(args) {
  try { return execFileSync('docker', args, { cwd: root, encoding: 'utf8', timeout: 30000, stdio: ['pipe', 'pipe', 'pipe'] }).trim(); }
  catch { throw Error('Isolated cluster Docker check failed.'); }
}
const containers = {};
for (const [service, base] of [['server', a], ['server-b', b], ['mysql', null], ['redis', null]]) {
  const ids = docker(['ps', '-a', '-q', '--filter', `label=com.docker.compose.project=${project}`, '--filter', `label=com.docker.compose.service=${service}`]);
  if (!ids || ids.includes('\n')) throw Error('Expected one isolated container per service.');
  const [container] = JSON.parse(docker(['inspect', ids]));
  if (path.resolve(container.Config.Labels['com.docker.compose.project.working_dir'] || '') !== root)
    throw Error('Isolated container does not belong to this workspace.');
  if (base && !container.NetworkSettings.Ports['8080/tcp']?.some(p => p.HostIp === '127.0.0.1' && p.HostPort === base.port))
    throw Error('URL does not match the isolated container binding.');
  containers[service] = container;
}
for (const [service, destination, volume] of [['mysql', '/var/lib/mysql', 'mysql-data'], ['redis', '/data', 'redis-data'], ['server', '/app/uploads', 'uploads'], ['server-b', '/app/uploads', 'uploads']]) {
  const mount = containers[service].Mounts.find(m => m.Destination === destination);
  if (mount?.Type !== 'volume' || mount.Name !== `${project}_${volume}`) throw Error('Refusing non-isolated data mounts.');
}
const env = Object.fromEntries((await fs.readFile(configPath, 'utf8')).split(/\r?\n/)
    .filter(line => line && !line.startsWith('#') && line.includes('='))
    .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
for (const name of ['server', 'server-b']) {
  if (!containers[name].Config.Env.includes(`JWT_ADMIN_SECRET=${env.JWT_ADMIN_SECRET}`) ||
      !containers[name].Config.Env.includes('CAMPUS_CLUSTER_ENABLED=true')) throw Error('Cluster configuration does not match both containers.');
}
async function call(base, route, token, method = 'GET', body, key) {
  const response = await fetch(new URL('/api/v1' + route, base), {
    method, signal: AbortSignal.timeout(15000),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(key ? { 'Idempotency-Key': key } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const result = await response.json();
  if (!response.ok || result.code !== 'OK') throw Error(`${route}: ${response.status} ${result.code}`);
  return result.data;
}
function socket(base, ticket) {
  return new Promise((resolve, reject) => {
    const request = http.request(new URL('/ws/orders?ticket=' + encodeURIComponent(ticket), base), {
      headers: { Connection: 'Upgrade', Upgrade: 'websocket', 'Sec-WebSocket-Version': '13', 'Sec-WebSocket-Key': randomBytes(16).toString('base64'), Origin: base.origin },
    });
    request.setTimeout(5000, () => request.destroy(Error('WebSocket handshake timed out')));
    request.on('error', reject);
    request.on('response', response => { response.resume(); resolve({ status: response.statusCode }); });
    request.on('upgrade', (response, connection) => {
      const events = [];
      let buffer = Buffer.alloc(0);
      connection.on('data', bytes => {
        buffer = Buffer.concat([buffer, bytes]);
        while (buffer.length >= 2) {
          let length = buffer[1] & 127, offset = 2;
          if (length === 126) { if (buffer.length < 4) return; length = buffer.readUInt16BE(2); offset = 4; }
          else if (length === 127) { connection.destroy(); return; }
          if (buffer.length < offset + length) return;
          const opcode = buffer[0] & 15, payload = buffer.subarray(offset, offset + length);
          buffer = buffer.subarray(offset + length);
          if (opcode === 1) { try { events.push(JSON.parse(payload.toString('utf8'))); } catch {} }
        }
      });
      connection.on('error', () => {});
      resolve({ status: response.statusCode, connection, events });
    });
    request.end();
  });
}
async function until(check, message) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    if (await check()) return;
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw Error(message);
}
const admin = await call(a, '/admin/auth/login', null, 'POST', { username: 'admin', password: env.DEMO_ADMIN_PASSWORD });
await call(b, '/admin/auth/me', admin.token);
const campuses = await call(a, '/platform/campuses', admin.token);
const suffix = randomUUID().slice(0, 8);
const store = await call(a, '/platform/stores', admin.token, 'POST', { campusId: campuses[0].id, name: `cluster-${suffix}`, enabled: true, open: true, deliveryFeeCents: 0, packagingFeeCents: 0, hours: ['00:00-24:00'], phone: '' });
const category = await call(a, `/merchant/stores/${store.id}/categories`, admin.token, 'POST', { name: `cluster-${suffix}`, type: 1, sort: 1, enabled: true });
const original = { name: `before-${suffix}`, categoryId: category.id, price: '12.50', enabled: true, image: '', description: '', flavors: [], components: [] };
const product = await call(a, `/merchant/stores/${store.id}/products/DISH`, admin.token, 'POST', original);
await call(b, `/user/stores/${store.id}/products`);
await call(a, `/merchant/stores/${store.id}/products/DISH/${product.id}`, admin.token, 'PUT', { ...original, name: `after-${suffix}` });
const changed = await call(b, `/user/stores/${store.id}/products`);
if (!changed.some(item => item.name === `after-${suffix}`)) throw Error('Other node returned a stale catalog after commit.');
const ticketA = await call(a, '/admin/ws-ticket', admin.token, 'POST');
const ticketB = await call(b, '/admin/ws-ticket', admin.token, 'POST');
const sockets = [await socket(b, ticketA.ticket), await socket(a, ticketB.ticket)];
try {
  if (sockets.some(value => value.status !== 101)) throw Error('Cross-node WebSocket ticket was rejected.');
  if ((await socket(a, ticketA.ticket)).status === 101) throw Error('Cross-node ticket replay succeeded.');
  const user = await call(a, '/user/auth/demo', null, 'POST', { account: 1 });
  const cart = await call(b, `/user/stores/${store.id}/cart`, user.token);
  const added = await call(a, `/user/stores/${store.id}/cart/items`, user.token, 'POST', { cartVersion: cart.cartVersion, itemType: 'DISH', itemId: product.id, flavors: {}, delta: 1 });
  const input = { stores: [{ storeId: store.id, cartVersion: added.cartVersion, fulfillment: 'PICKUP' }] };
  const quote = await call(b, '/user/checkouts/preview', user.token, 'POST', input);
  const key = randomUUID();
  const checkout = await call(a, '/user/checkouts', user.token, 'POST', { ...input, quoteHash: quote.quoteHash }, key);
  const repeated = await call(b, '/user/checkouts', user.token, 'POST', { ...input, quoteHash: quote.quoteHash }, key);
  if (repeated.id !== checkout.id) throw Error('Cross-node idempotency created another checkout.');
  await call(b, `/user/checkouts/${checkout.id}/pay`, user.token, 'POST', { version: checkout.version });
  const order = checkout.orders[0].id;
  await until(() => sockets.every(value => value.events.some(event => event.event?.orderId === order && event.event?.type === 'ORDER_PAID')), 'Paid event did not reach sockets on both nodes.');
  docker(['stop', '--time', '30', containers.server.Id]);
  const paid = await call(b, `/user/checkouts/${checkout.id}`, user.token);
  if (paid.state !== 'PAID') throw Error('Surviving node lost the paid checkout.');
  await call(b, '/admin/auth/me', admin.token);
  await until(async () => !(await call(b, '/platform/events', admin.token)).some(e => String(e.aggregateId) === order && e.state !== 'SUCCEEDED'), 'Surviving worker did not drain the checkout events.');
  console.log(JSON.stringify({ result: 'PASSED', crossNodeSession: true, sharedCatalogVersion: true, crossNodeSingleUseTickets: true, websocketFanout: true, checkoutIdempotency: true, paidStateAfterNodeStop: true, survivingWorker: true, note: 'Isolated demo payment and local upload volume only; cloud adapters require their separate contract/live tests.' }));
} finally {
  for (const value of sockets) value.connection?.destroy();
}
