import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve, relative, dirname, extname, basename } from 'node:path';
import { randomUUID } from 'node:crypto';
import { performance } from 'node:perf_hooks';
import { fileURLToPath } from 'node:url';

// Only run against the disposable project prepared by prepare-performance.mjs.
// Tokens and passwords are read from files/environment and never written to reports.
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const date = () => new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10);
const percentile = values => values.length ? Math.round([...values].sort((a, b) => a - b)[Math.ceil(values.length * .95) - 1] * 100) / 100 : null;
function integer(name, fallback, min, max) {
  const value = Number(process.env[name] ?? fallback);
  if (!Number.isInteger(value) || value < min || value > max) throw new Error(`${name} must be an integer in ${min}..${max}`);
  return value;
}
function localFile(path, label) {
  if (!path) throw new Error(`${label} is required`);
  const absolute = resolve(root, path), inside = relative(resolve(root, '.local'), absolute);
  if (!inside || inside.startsWith('..') || /^[A-Za-z]:/.test(inside)) throw new Error(`${label} must be a file inside this repository .local directory`);
  return absolute;
}
async function main() {
  if (!process.env.BASE_URL || !process.env.ADMIN_PASSWORD || !process.env.PERF_PROJECT_NAME)
    throw new Error('BASE_URL, ADMIN_PASSWORD and PERF_PROJECT_NAME are required');
  const base = new URL(process.env.BASE_URL);
  if (base.protocol !== 'http:' || !['localhost', '127.0.0.1', '[::1]'].includes(base.hostname)
      || !base.port || Number(base.port) < 1024 || base.port === '18083' || base.username || base.password || base.pathname !== '/' || base.search || base.hash)
    throw new Error('BASE_URL must use a dedicated loopback HTTP port, never daily port 18083');
  const project = process.env.PERF_PROJECT_NAME;
  if (!/^[a-z0-9][a-z0-9_-]+$/.test(project) || ['campuseats-v1', 'campuseats'].includes(project))
    throw new Error('PERF_PROJECT_NAME must name a disposable isolated Compose project');
  const sessionsFile = localFile(process.env.PERF_USER_TOKENS_FILE, 'PERF_USER_TOKENS_FILE');
  const extension = extname(sessionsFile);
  const metadataFile = resolve(dirname(sessionsFile), basename(sessionsFile, extension) + '.metadata.json');
  const [sessions, metadata] = await Promise.all([sessionsFile, metadataFile].map(async file => JSON.parse(await readFile(file, 'utf8'))));
  if (metadata.baseUrl !== base.origin || metadata.project !== project || !Number.isFinite(Date.parse(metadata.preparedAt))
      || Date.now() - Date.parse(metadata.preparedAt) > 3600000 || Date.parse(metadata.preparedAt) > Date.now() + 60000)
    throw new Error('Fixture metadata does not match BASE_URL/project or is older than one hour; prepare fresh isolated sessions');
  const levels = (process.env.PERF_CONCURRENCY || '1,5,10').split(',').map(Number);
  if (!levels.length || levels.some(n => !Number.isInteger(n) || n < 1 || n > 10) || new Set(levels).size !== levels.length)
    throw new Error('PERF_CONCURRENCY requires distinct levels in 1..10');
  const max = Math.max(...levels);
  if (!Array.isArray(sessions) || sessions.length < max || sessions.some(s => !/^[1-9]\d*$/.test(s.id) || typeof s.token !== 'string' || s.token.length > 4096)
      || new Set(sessions.map(s => s.id)).size !== sessions.length || new Set(sessions.map(s => s.token)).size !== sessions.length
      || !Array.isArray(metadata.userIds) || sessions.some(s => !metadata.userIds.includes(s.id)))
    throw new Error('Fixture file requires distinct USER sessions and matching metadata userIds');
  const seconds = integer('PERF_SECONDS', 60, 5, 600);
  const pauseMs = integer('PERF_READ_PAUSE_MS', 100, 0, 5000);
  const output = localFile(process.env.PERF_OUTPUT || `.local/performance-${Date.now()}/report.json`, 'PERF_OUTPUT');
  const windows = new Map();
  const totals = { throttledRequests: 0, throttleWaitMs: 0 };
  let stage;
  async function budget(actor) {
    // Keep margin below the real server limit of 30 order writes per 60 seconds.
    let times = windows.get(actor) || [];
    times = times.filter(time => Date.now() - time < 61000);
    if (times.length >= 24) {
      const delay = Math.max(0, 61000 - (Date.now() - times[0]));
      totals.throttledRequests++; totals.throttleWaitMs += delay;
      await sleep(delay);
      times = times.filter(time => Date.now() - time < 61000);
    }
    times.push(Date.now()); windows.set(actor, times);
  }
  async function api(path, token, method = 'GET', data, label = path, headers = {}) {
    if (method !== 'GET' && (path.includes('/orders') || path.includes('/checkouts') || path.includes('/claim')))
      await budget(token);
    const start = performance.now();
    let response, envelope;
    try {
      response = await fetch(base.origin + '/api/v1' + path, {
        method, headers: { ...(token ? { Authorization: 'Bearer ' + token } : {}), ...(data ? { 'Content-Type': 'application/json' } : {}), ...headers },
        ...(data ? { body: JSON.stringify(data) } : {}), signal: AbortSignal.timeout(25000),
      });
      envelope = await response.json();
      if (!response.ok || envelope?.code !== 'OK' || !Object.hasOwn(envelope, 'data')) {
        const code = typeof envelope?.code === 'string' && /^[A-Z0-9_]{1,80}$/.test(envelope.code) ? envelope.code : 'INVALID_RESPONSE';
        const error = new Error(`Request failed: ${label} (${response.status}, ${code})`);
        error.code = code; throw error;
      }
      if (stage) stage.succeeded++;
      return envelope.data;
    } catch (error) {
      error.requestFailure = true;
      if (stage) {
        const code = error.code || (error.name === 'TimeoutError' ? 'TIMEOUT' : 'TRANSPORT');
        stage.unexpectedErrors++;
        stage.errors[code] = (stage.errors[code] || 0) + 1;
      }
      throw error;
    } finally {
      if (stage) {
        const ms = performance.now() - start;
        stage.latencies.push(ms);
        const operation = stage.operations[label] ||= { count: 0, latencies: [] };
        operation.count++; operation.latencies.push(ms);
      }
    }
  }
  // Verify every identity against the selected server before creating business fixtures.
  for (const user of sessions.slice(0, max)) {
    const identity = await api('/user/auth/me', user.token);
    if (String(identity.id) !== user.id || identity.type !== 'USER') throw new Error('A prepared session does not belong to the selected isolated server/user');
  }
  const admin = await api('/admin/auth/login', '', 'POST', { username: process.env.ADMIN_USERNAME || 'admin', password: process.env.ADMIN_PASSWORD });
  if (admin.role !== 'PLATFORM_ADMIN' || admin.mustChangePassword) throw new Error('An isolated platform administrator with completed initial password change is required');
  const suffix = randomUUID().slice(0, 8), a = admin.token;
  const campus = await api('/platform/campuses', a, 'POST', { name: '测量校区-' + suffix, enabled: true });
  const store = await api('/platform/stores', a, 'POST', { campusId: campus.id, name: '测量餐厅-' + suffix, enabled: true, open: true,
    deliveryFeeCents: 0, packagingFeeCents: 0, hours: ['00:00-24:00'], phone: '13800000000' });
  const category = await api(`/merchant/stores/${store.id}/categories`, a, 'POST', { name: '测量餐品', type: 1, sort: 0, enabled: true });
  const product = await api(`/merchant/stores/${store.id}/products/DISH`, a, 'POST', { name: '测量餐-' + suffix, categoryId: category.id, price: '10.00', enabled: true,
    description: '隔离性能夹具，仅模拟资金', image: '/demo-images/dish.svg', flavors: [], components: [] });
  await api(`/merchant/stores/${store.id}/quotas?date=${date()}`, a, 'PUT', { dishId: product.id, total: 10000, defaultQuota: true });
  async function ensureCart(user) {
    let cart = await api(`/user/stores/${store.id}/cart`, user.token, 'GET', undefined, 'cart.read');
    if (cart.items.length && (cart.items.length !== 1 || cart.items[0].itemId !== product.id || cart.items[0].quantity !== 1))
      throw new Error('Prepared user cart has unexpected contents; refusing to overwrite it');
    if (!cart.items.length) cart = await api(`/user/stores/${store.id}/cart/items`, user.token, 'POST', {
      cartVersion: cart.cartVersion, itemType: 'DISH', itemId: product.id, flavors: {}, delta: 1,
    }, 'cart.add');
    return { stores: [{ storeId: store.id, cartVersion: cart.cartVersion, fulfillment: 'PICKUP', addressId: null, couponId: null }], platformCouponId: null };
  }
  async function transaction(user, current) {
    const start = performance.now();
    const input = await ensureCart(user);
    const quote = await api('/user/checkouts/preview', user.token, 'POST', input, 'quote');
    const checkout = await api('/user/checkouts', user.token, 'POST', { ...input, quoteHash: quote.quoteHash }, 'checkout.submit', { 'Idempotency-Key': randomUUID() });
    const paid = await api(`/user/checkouts/${checkout.id}/pay`, user.token, 'POST', { version: checkout.version }, 'checkout.mockPay');
    if (paid.state !== 'PAID' || paid.orders.length !== 1) throw new Error('Simulated payment did not produce one paid order');
    let order = paid.orders[0];
    await api(`/merchant/orders/${order.id}/actions/accept`, a, 'POST', { version: order.version }, 'merchant.accept');
    const created = await api(`/user/orders/${order.id}/aftersales`, user.token, 'POST', { kind: 'CANCEL', reason: '隔离性能夹具取消并模拟退款', items: null, evidenceIds: [] }, 'aftersale.apply');
    const requests = await api('/user/aftersales?orderNumber=' + encodeURIComponent(order.number), user.token, 'GET', undefined, 'aftersale.read');
    const request = requests.find(value => value.id === created.id);
    if (!request) throw new Error('The created cancellation request is missing');
    await api(`/merchant/aftersales/${request.id}/decision`, a, 'POST', { version: request.version, approve: true, reason: '隔离性能夹具同意取消' }, 'merchant.approve');
    const deadline = Date.now() + 20000;
    do {
      order = await api(`/user/orders/${order.id}`, user.token, 'GET', undefined, 'refund.poll');
      if (order.refundedCents === order.paidCents && Number(order.paidCents) > 0 && order.refunds.some(refund => refund.state === 'SUCCEEDED')) {
        current.completedTransactions++;
        current.transactionLatencies.push(performance.now() - start);
        return;
      }
      await sleep(500);
    } while (Date.now() < deadline);
    throw new Error('Simulated refund did not complete within 20 seconds');
  }
  async function backlog() {
    const result = {};
    for (const [name, endpoint] of [['refunds', 'refunds'], ['notifications', 'events']]) {
      result[name] = {};
      for (const state of ['REQUESTED', 'PROCESSING', 'FAILED']) {
        const rows = await api(`/platform/${endpoint}?state=${state}`, a);
        result[name][state] = { count: rows.length, cappedAt100: rows.length === 100 };
      }
    }
    return result;
  }
  const report = {
    startedAt: new Date().toISOString(), baseUrl: base.origin, project,
    scope: 'Single-instance isolated HTTP measurement, simulated money only; no production capacity, HA or maximum transaction TPS claim.',
    identities: { source: 'Direct-DB fixture sessions prepared and HTTP-verified before load', distinctUsers: max },
    loadPolicy: { concurrency: levels, secondsPerStage: seconds, readPauseMs: pauseMs, quoteIntervalMs: 5000,
      transactionsPerWorkerPerStage: 1, orderWriteBudgetPerIdentityPer61Seconds: 24,
      note: 'One complete checkout, simulated payment, merchant acceptance, cancellation approval and asynchronous refund per worker per stage. Remaining traffic is menu/search/quote reads; existing server rate limits are retained. Sleeps and budget waits are excluded from individual HTTP latency, included in stage throughput.' },
    fixture: { campusId: campus.id, storeId: store.id, productId: product.id }, stages: [],
  };
  await mkdir(dirname(output), { recursive: true });
  for (const concurrency of levels) {
    const before = await backlog();
    const current = { concurrency, requestedSeconds: seconds, succeeded: 0, unexpectedErrors: 0, errors: {}, workerFailures: 0,
      completedTransactions: 0, latencies: [], transactionLatencies: [], operations: {} };
    stage = current;
    const start = performance.now(), stop = Date.now() + seconds * 1000;
    await Promise.all(sessions.slice(0, concurrency).map(async user => {
      try {
        await transaction(user, current);
        const input = await ensureCart(user);
        let nextQuote = Date.now();
        while (Date.now() < stop) {
          await api(`/user/stores/${store.id}/products`, user.token, 'GET', undefined, 'menu');
          await api(`/user/search?campusId=${campus.id}&query=${encodeURIComponent('测量餐')}`, user.token, 'GET', undefined, 'search');
          if (Date.now() >= nextQuote) {
            await api('/user/checkouts/preview', user.token, 'POST', input, 'quote');
            nextQuote = Date.now() + 5000;
          }
          if (pauseMs) await sleep(Math.min(pauseMs, Math.max(0, stop - Date.now())));
        }
      } catch (error) {
        // Never serialize arbitrary transport errors, requests or credential-bearing objects.
        current.workerFailures++;
        if (!error.requestFailure) {
          current.unexpectedErrors++;
          current.errors.BUSINESS_INVARIANT = (current.errors.BUSINESS_INVARIANT || 0) + 1;
        }
      }
    }));
    stage = undefined;
    const elapsed = (performance.now() - start) / 1000;
    const after = await backlog();
    report.stages.push({ concurrency, requestedSeconds: seconds, elapsedSeconds: Math.round(elapsed * 100) / 100,
      requests: current.latencies.length, succeeded: current.succeeded, unexpectedErrors: current.unexpectedErrors,
      workerFailures: current.workerFailures, errorsByCode: current.errors,
      requestThroughputPerSecond: Math.round(current.latencies.length / elapsed * 100) / 100,
      successfulRequestThroughputPerSecond: Math.round(current.succeeded / elapsed * 100) / 100,
      httpP95Ms: percentile(current.latencies), completedTransactions: current.completedTransactions,
      transactionP95Ms: percentile(current.transactionLatencies),
      operations: Object.fromEntries(Object.entries(current.operations).map(([name, value]) => [name, { count: value.count, httpP95Ms: percentile(value.latencies) }])),
      backlogBefore: before, backlogAfter: after,
    });
    report.completedAt = new Date().toISOString(); report.clientThrottling = { ...totals };
    await writeFile(output, JSON.stringify(report, null, 2), { mode: 0o600 });
    console.log(`concurrency=${concurrency}, requests=${current.latencies.length}, P95=${percentile(current.latencies)}ms, transactions=${current.completedTransactions}, errors=${current.unexpectedErrors}, failedWorkers=${current.workerFailures}`);
  }
  console.log('Report: ' + relative(root, output));
  if (report.stages.some(value => value.unexpectedErrors || value.workerFailures || value.completedTransactions !== value.concurrency)) process.exitCode = 1;
}
main().catch(() => {
  // Configuration and server failures deliberately omit raw payloads and secrets.
  console.error('Performance measurement stopped. Check isolated fixture metadata, required environment, server readiness and administrator role. No credentials are printed.');
  process.exitCode = 1;
});
