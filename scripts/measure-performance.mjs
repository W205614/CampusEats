import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve, relative, dirname, extname, basename } from 'node:path';
import { randomUUID } from 'node:crypto';
import { performance } from 'node:perf_hooks';
import { fileURLToPath } from 'node:url';
import { execFile } from 'node:child_process';

// All writes and SQL fixtures belong to the explicitly selected disposable project.
// Reports never contain sessions, secrets, arbitrary response bodies or transport errors.
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
export const percentile = values => values.length ? Math.round([...values].sort((a, b) => a - b)[Math.ceil(values.length * .95) - 1] * 100) / 100 : null;
export function isolatedProject(value) {
  return /^campuseats-(?:perf|improvement(?:-acceptance)?|role-uat)(?:-[a-z0-9]+)*$/.test(value);
}
export function stageSummary(current, elapsedSeconds) {
  return {
    kind: current.kind, concurrency: current.concurrency, requestedSeconds: current.requestedSeconds,
    elapsedSeconds: Math.round(elapsedSeconds * 100) / 100,
    requests: current.latencies.length, succeeded: current.succeeded,
    unexpectedErrors: current.unexpectedErrors, workerFailures: current.workerFailures,
    errorsByCode: current.errors, peakInFlightRequests: current.peakInFlightRequests,
    requestThroughputPerSecond: Math.round(current.latencies.length / elapsedSeconds * 100) / 100,
    successfulRequestThroughputPerSecond: Math.round(current.succeeded / elapsedSeconds * 100) / 100,
    httpP95Ms: percentile(current.latencies), readP95Ms: percentile(current.readLatencies),
    completedTransactions: current.completedTransactions, transactionP95Ms: percentile(current.transactionLatencies),
    operations: Object.fromEntries(Object.entries(current.operations).map(([name, value]) =>
      [name, { count: value.count, httpP95Ms: percentile(value.latencies) }])),
  };
}
export function acceptedStage(value) {
  const reads = new Set(['menu', 'search.page', 'search.miss', 'orders.history', 'merchant.history', 'ledger.history', 'finance.reconcile']);
  return value.unexpectedErrors === 0 && value.workerFailures === 0 && value.requests > 0
    && value.readP95Ms !== null && value.readP95Ms <= 1000
    && Object.entries(value.operations ?? {}).filter(([name]) => reads.has(name))
      .every(([, operation]) => operation.httpP95Ms !== null && operation.httpP95Ms <= 1000)
    && (value.kind !== 'mixed' || value.completedTransactions === (value.expectedTransactions ?? 15) && value.transactionP95Ms !== null && value.transactionP95Ms <= 10000);
}
function integer(name, fallback, min, max) {
  const value = Number(process.env[name] ?? fallback);
  if (!Number.isInteger(value) || value < min || value > max) throw new Error('Invalid measurement integer');
  return value;
}
function localFile(path) {
  if (!path) throw new Error('Missing protected local file');
  const absolute = resolve(root, path), inside = relative(resolve(root, '.local'), absolute);
  if (!inside || inside.startsWith('..') || /^[A-Za-z]:/.test(inside)) throw new Error('Measurement files must stay in .local');
  return absolute;
}
function docker(args, input) {
  return new Promise((resolveCommand, reject) => {
    const child = execFile('docker', args, { cwd: root, encoding: 'utf8', timeout: 20000, maxBuffer: 8 * 1024 * 1024 },
      (error, stdout) => error ? reject(new Error('DOCKER_READ_FAILED')) : resolveCommand(stdout.trim()));
    child.stdin.on('error', () => {});
    child.stdin.end(input || '');
  });
}

export async function main() {
  let output, report, stage, samplingTimer, sampleRunning, containers, outputCreated = false;
  let inFlight = 0;
  const totals = { throttledRequests: 0, throttleWaitMs: 0 };
  const persist = async () => {
    if (report && output && outputCreated) {
      report.updatedAt = new Date().toISOString();
      report.clientThrottling = { ...totals };
      await writeFile(output, JSON.stringify(report, null, 2), { mode: 0o600 });
    }
  };
  try {
    if (!process.env.BASE_URL || !process.env.ADMIN_PASSWORD || !process.env.PERF_PROJECT_NAME)
      throw new Error('Missing measurement configuration');
    const base = new URL(process.env.BASE_URL), project = process.env.PERF_PROJECT_NAME;
    if (base.protocol !== 'http:' || !['localhost', '127.0.0.1'].includes(base.hostname)
        || !base.port || Number(base.port) < 1024 || base.port === '18083' || base.username || base.password
        || base.pathname !== '/' || base.search || base.hash || !isolatedProject(project))
      throw new Error('An explicit isolated loopback project is required');
    output = localFile(process.env.PERF_OUTPUT || `.local/performance-${Date.now()}/report.json`);
    const sessionsFile = localFile(process.env.PERF_USER_TOKENS_FILE);
    const extension = extname(sessionsFile);
    const metadataFile = resolve(dirname(sessionsFile), basename(sessionsFile, extension) + '.metadata.json');
    const [sessions, metadata] = await Promise.all([sessionsFile, metadataFile].map(async file => JSON.parse(await readFile(file, 'utf8'))));
    if (metadata.baseUrl !== base.origin || metadata.project !== project || !Number.isFinite(Date.parse(metadata.preparedAt))
        || Date.now() - Date.parse(metadata.preparedAt) > 3600000 || Date.parse(metadata.preparedAt) > Date.now() + 60000)
      throw new Error('Fixture metadata is stale or does not match the selected server');
    const fixture = metadata.fixture;
    if (!fixture || ['campusId', 'storeId', 'productId'].some(key => !/^[1-9]\d*$/.test(String(fixture[key])))
        || fixture.stores < 205 || fixture.products < 1000)
      throw new Error('A prepared large discovery fixture is required');
    const levels = (process.env.PERF_CONCURRENCY || '1,5,10,30').split(',').map(Number);
    if (!levels.length || levels.some(n => !Number.isInteger(n) || n < 1 || n > 30) || new Set(levels).size !== levels.length)
      throw new Error('PERF_CONCURRENCY requires distinct levels in 1..30');
    if (!Array.isArray(sessions) || sessions.length < 30 || sessions.some(s => !/^[1-9]\d*$/.test(s.id) || typeof s.token !== 'string' || s.token.length > 4096)
        || new Set(sessions.map(s => s.id)).size !== sessions.length || new Set(sessions.map(s => s.token)).size !== sessions.length
        || !Array.isArray(metadata.userIds) || sessions.some(s => !metadata.userIds.includes(s.id)))
      throw new Error('Thirty distinct verified fixture identities are required');
    const seconds = integer('PERF_SECONDS', 60, 5, 600);
    const pauseMs = integer('PERF_READ_PAUSE_MS', 1000, 1, 5000);
    if (process.env.PERF_SCENARIO && !['standard', 'lunch'].includes(process.env.PERF_SCENARIO))
      throw new Error('Unknown performance scenario');
    const lunch = process.env.PERF_SCENARIO === 'lunch', writers = lunch ? 30 : 5;
    const expectedTransactions = writers * 3;
    if (lunch && sessions.some(user => !/^[1-9]\d*$/.test(user.staffId) || typeof user.staffToken !== 'string'))
      throw new Error('Lunch scenario requires the prepared store staff identities');
    report = {
      startedAt: new Date().toISOString(), baseUrl: base.origin, project, status: 'RUNNING',
      scope: 'Isolated single-instance simulated-money scenario; virtual clients are not simultaneous in-flight requests; no production capacity, maximum TPS, real payment or long-duration reliability claim.',
      identities: { source: 'Direct-DB fixture sessions verified through normal HTTP authentication', distinctUsers: 30 },
      fixture: { campusId: fixture.campusId, storeId: fixture.storeId, productId: fixture.productId,
        stores: fixture.stores, products: fixture.products, historyOrders: fixture.historyOrders ?? 0,
        historyPolicy: fixture.historyPolicy ?? 'No synthetic history.' },
      backlogScope: { storeId: fixture.storeId, note: 'All transaction workers use the newly prepared fixture store; unrelated role-test fixtures are excluded.' },
      loadPolicy: { concurrency: lunch ? [30] : levels, secondsPerReadStage: lunch ? 0 : seconds, readPauseMs: pauseMs,
        scenario: lunch ? 'lunch' : 'standard', mixedClients: 30, mixedSeconds: 300, mixedTransactionWorkers: writers, transactionsPerWriter: 3,
        workbenchObservers: lunch ? 1 : 0,
        transactionStartOffsetsSeconds: [0, 90, 180], orderWriteBudgetPerIdentityPer61Seconds: 24,
        note: (lunch ? 'Thirty users start full transactions together at 0/90/180 seconds, with three store staff identities. Between bursts they read menus, matched/unmatched searches and order history; a workbench observer reads merchant history, ledger and reconciliation every 15 seconds. ' : 'Each worker alternates menu and paged search. Mixed writers browse between transactions. ')
          + 'Real server rate limits remain enabled; HTTP latency excludes client pacing and write-budget waits, while transaction and elapsed time include them.' },
      acceptanceTargets: { readP95Ms: 1000, transactionP95Ms: 10000, completedMixedTransactions: expectedTransactions,
        unexpectedErrors: 0, workerFailures: 0, drainSeconds: 60, failedTasks: 0, oomKills: 0, unexpectedRestarts: 0 },
      stages: [], resourceSamples: [], controlErrors: [], acceptance: false,
    };
    await mkdir(dirname(output), { recursive: true });
    await writeFile(output, JSON.stringify(report, null, 2), { mode: 0o600, flag: 'wx' });
    outputCreated = true;

    const ids = (await docker(['ps', '-aq', '--filter', `label=com.docker.compose.project=${project}`])).split(/\s+/).filter(Boolean);
    if (ids.length !== 4) throw new Error('Expected exactly four isolated service containers');
    containers = JSON.parse(await docker(['inspect', ...ids]));
    for (const service of ['server', 'web', 'mysql', 'redis']) {
      const found = containers.filter(c => c.Config.Labels?.['com.docker.compose.service'] === service);
      if (found.length !== 1 || found[0].Config.Labels?.['com.docker.compose.project'] !== project
          || !found[0].State.Running || found[0].State.Health?.Status !== 'healthy'
          || resolve(found[0].Config.Labels?.['com.docker.compose.project.working_dir'] || '').toLowerCase() !== root.toLowerCase())
        throw new Error('Isolated ownership or health check failed');
    }
    const web = containers.find(c => c.Config.Labels['com.docker.compose.service'] === 'web');
    const bindings = web.NetworkSettings.Ports['80/tcp'];
    if (!Array.isArray(bindings) || bindings.length !== 1 || bindings[0].HostIp !== '127.0.0.1' || bindings[0].HostPort !== base.port)
      throw new Error('Selected base URL does not match the isolated loopback binding');
    const containerState = values => values.map(c => ({ id: c.Id, name: c.Name.replace(/^\//, ''),
      service: c.Config.Labels['com.docker.compose.service'], imageId: c.Image,
      running: c.State.Running, oomKilled: c.State.OOMKilled, restartCount: c.RestartCount, health: c.State.Health?.Status }));
    report.containersBefore = containerState(containers);
    const sample = () => {
      if (sampleRunning) return sampleRunning;
      sampleRunning = (async () => {
        try {
          const raw = await docker(['stats', '--no-stream', '--format', '{{json .}}', ...ids]);
          const values = raw.split(/\r?\n/).filter(Boolean).map(line => JSON.parse(line));
          report.resourceSamples.push({ at: new Date().toISOString(), containers: values.map(value => ({
            name: value.Name, cpuPercent: value.CPUPerc, memoryUsage: value.MemUsage,
            memoryPercent: value.MemPerc, pids: value.PIDs, netIO: value.NetIO, blockIO: value.BlockIO })) });
        } catch { report.controlErrors.push('RESOURCE_SAMPLE_FAILED'); }
      })().finally(() => { sampleRunning = undefined; });
      return sampleRunning;
    };
    await sample();
    samplingTimer = setInterval(() => { void sample(); }, 10000);
    const mysql = containers.find(c => c.Config.Labels['com.docker.compose.service'] === 'mysql');
    async function invariants() {
      const campus = fixture.campusId;
      const sql = [
        `SELECT 'stores',COUNT(*) FROM store WHERE campus_id=${campus};`,
        `SELECT 'products',COUNT(*) FROM dish d JOIN store s ON s.id=d.store_id WHERE s.campus_id=${campus};`,
        `SELECT 'orders',COUNT(*) FROM orders WHERE campus_id=${campus};`,
        `SELECT 'succeededRefunds',COUNT(*) FROM refund_request r JOIN orders o ON o.id=r.order_id WHERE o.campus_id=${campus} AND r.state='SUCCEEDED';`,
        `SELECT 'quotaErrors',COUNT(*) FROM daily_quota q JOIN dish d ON d.id=q.dish_id JOIN store s ON s.id=d.store_id WHERE s.campus_id=${campus} AND (q.reserved<0 OR q.consumed<0 OR q.reserved+q.consumed>q.total);`,
        `SELECT 'refundErrors',COUNT(*) FROM orders WHERE campus_id=${campus} AND (refunded_cents<0 OR reserved_refund_cents<0 OR refunded_cents+reserved_refund_cents>paid_cents);`,
        `SELECT 'quantityErrors',COUNT(*) FROM order_detail d JOIN orders o ON o.id=d.order_id WHERE o.campus_id=${campus} AND (d.refunded_quantity<0 OR d.reserved_quantity<0 OR d.refunded_quantity+d.reserved_quantity>d.number);`,
      ].join('\n');
      const raw = await docker(['exec', '-i', mysql.Id, 'sh', '-c', 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u campus sky_take_out -N -B'], sql);
      const result = Object.fromEntries(raw.split(/\r?\n/).filter(Boolean).map(line => {
        const [key, value] = line.split('\t');
        if (!/^[a-zA-Z]+$/.test(key) || !/^\d+$/.test(value)) throw new Error('Invalid invariant output');
        return [key, Number(value)];
      }));
      if (Object.keys(result).length !== 7) throw new Error('Incomplete invariant snapshot');
      return result;
    }
    report.invariantsBefore = await invariants();
    if (report.invariantsBefore.stores < 205 || report.invariantsBefore.products < 1000)
      throw new Error('Prepared dataset does not match actual database rows');
    const windows = new Map(), budgetQueues = new Map();
    async function budget(actor) {
      const previous = budgetQueues.get(actor) || Promise.resolve();
      let release;
      budgetQueues.set(actor, new Promise(resolveBudget => { release = resolveBudget; }));
      await previous;
      try {
        let times = (windows.get(actor) || []).filter(time => Date.now() - time < 61000);
        if (times.length >= 24) {
          const delay = Math.max(0, 61000 - (Date.now() - times[0]));
          totals.throttledRequests++; totals.throttleWaitMs += delay;
          await sleep(delay);
          times = times.filter(time => Date.now() - time < 61000);
        }
        times.push(Date.now()); windows.set(actor, times);
      } finally { release(); }
    }
    async function api(path, token, method = 'GET', data, label = 'control', headers = {}, read = false) {
      if (method !== 'GET' && (path.includes('/orders') || path.includes('/checkouts') || path.includes('/claim'))) await budget(token);
      const current = stage, started = performance.now();
      inFlight++;
      if (current) current.peakInFlightRequests = Math.max(current.peakInFlightRequests, inFlight);
      try {
        const response = await fetch(base.origin + '/api/v1' + path, {
          method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(data ? { 'Content-Type': 'application/json' } : {}), ...headers },
          ...(data ? { body: JSON.stringify(data) } : {}), signal: AbortSignal.timeout(25000),
        });
        const envelope = await response.json();
        if (!response.ok || envelope?.code !== 'OK' || !Object.hasOwn(envelope, 'data')) {
          const error = new Error('HTTP_REQUEST_FAILED');
          error.code = typeof envelope?.code === 'string' && /^[A-Z0-9_]{1,80}$/.test(envelope.code) ? envelope.code : 'INVALID_RESPONSE';
          throw error;
        }
        if (current) current.succeeded++;
        return envelope.data;
      } catch (error) {
        error.requestFailure = true;
        if (current) {
          const code = error.code || (error.name === 'TimeoutError' ? 'TIMEOUT' : 'TRANSPORT');
          current.unexpectedErrors++; current.errors[code] = (current.errors[code] || 0) + 1;
        }
        throw error;
      } finally {
        inFlight--;
        if (current) {
          const ms = performance.now() - started;
          current.latencies.push(ms);
          if (read) current.readLatencies.push(ms);
          const operation = current.operations[label] ||= { count: 0, latencies: [] };
          operation.count++; operation.latencies.push(ms);
        }
      }
    }
    for (const user of sessions.slice(0, 30)) {
      const identity = await api('/user/auth/me', user.token);
      if (String(identity.id) !== user.id || identity.type !== 'USER') throw new Error('Fixture HTTP identity mismatch');
    }
    if (lunch) for (const user of [...new Map(sessions.map(user => [user.staffId, user])).values()]) {
      const staff = await api('/admin/auth/me', user.staffToken);
      if (String(staff.id) !== user.staffId || staff.role !== 'SHOP_MANAGER'
          || String(staff.storeId) !== String(fixture.storeId) || staff.mustChangePassword)
        throw new Error('Fixture HTTP identity mismatch');
    }
    const admin = await api('/admin/auth/login', '', 'POST', { username: process.env.ADMIN_USERNAME || 'admin', password: process.env.ADMIN_PASSWORD });
    if (admin.role !== 'PLATFORM_ADMIN' || admin.mustChangePassword) throw new Error('An isolated administrator with a changed password is required');
    const a = admin.token, store = { id: String(fixture.storeId) }, product = { id: String(fixture.productId) };
    async function ensureCart(user) {
      let cart = await api(`/user/stores/${store.id}/cart`, user.token, 'GET', undefined, 'cart.read');
      if (cart.items.length && (cart.items.length !== 1 || cart.items[0].itemId !== product.id || cart.items[0].quantity !== 1))
        throw new Error('Unexpected fixture cart contents');
      if (!cart.items.length) cart = await api(`/user/stores/${store.id}/cart/items`, user.token, 'POST', {
        cartVersion: cart.cartVersion, itemType: 'DISH', itemId: product.id, flavors: {}, delta: 1,
      }, 'cart.add');
      return { stores: [{ storeId: store.id, cartVersion: cart.cartVersion, fulfillment: 'PICKUP', addressId: null, couponId: null }], platformCouponId: null };
    }
    async function transaction(user, current) {
      const merchant = lunch ? user.staffToken : a;
      const start = performance.now(), input = await ensureCart(user);
      const quote = await api('/user/checkouts/preview', user.token, 'POST', input, 'quote');
      const checkout = await api('/user/checkouts', user.token, 'POST', { ...input, quoteHash: quote.quoteHash }, 'checkout.submit', { 'Idempotency-Key': randomUUID() });
      const paid = await api(`/user/checkouts/${checkout.id}/pay`, user.token, 'POST', { version: checkout.version }, 'checkout.mockPay');
      if (paid.state !== 'PAID' || paid.orders.length !== 1) throw new Error('Unexpected simulated payment result');
      let order = paid.orders[0];
      await api(`/merchant/orders/${order.id}/actions/accept`, merchant, 'POST', { version: order.version }, 'merchant.accept');
      const created = await api(`/user/orders/${order.id}/aftersales`, user.token, 'POST', { kind: 'CANCEL', reason: '隔离性能夹具取消并模拟退款', items: null, evidenceIds: [] }, 'aftersale.apply');
      const requests = await api('/user/aftersales?orderNumber=' + encodeURIComponent(order.number), user.token, 'GET', undefined, 'aftersale.read');
      const request = requests.find(value => value.id === created.id);
      if (!request) throw new Error('Created cancellation request is missing');
      await api(`/merchant/aftersales/${request.id}/decision`, merchant, 'POST', { version: request.version, approve: true, reason: '隔离性能夹具同意取消' }, 'merchant.approve');
      const deadline = Date.now() + 20000;
      do {
        order = await api(`/user/orders/${order.id}`, user.token, 'GET', undefined, 'refund.poll');
        if (order.refundedCents === order.paidCents && Number(order.paidCents) > 0 && order.refunds.some(refund => refund.state === 'SUCCEEDED')) {
          current.completedTransactions++; current.transactionLatencies.push(performance.now() - start); return;
        }
        await sleep(500);
      } while (Date.now() < deadline);
      throw new Error('Simulated refund did not complete');
    }
    async function backlog() {
      const result = {};
      for (const endpoint of ['refunds', 'events']) {
        result[endpoint] = {};
        for (const state of ['REQUESTED', 'PROCESSING', 'FAILED']) {
          let count = 0, beforeId;
          do {
            const rows = await api(`/merchant/stores/${store.id}/${endpoint}?state=${state}&limit=1000` + (beforeId ? `&beforeId=${beforeId}` : ''), a);
            count += rows.length;
            if (count > 100000) throw new Error('Task backlog exceeds bounded measurement scan');
            beforeId = rows.length === 1000 ? rows.at(-1).id : null;
          } while (beforeId);
          result[endpoint][state] = count;
        }
      }
      return result;
    }
    const failed = value => Object.values(value).some(states => states.FAILED > 0);
    const pending = value => Object.values(value).some(states => states.REQUESTED > 0 || states.PROCESSING > 0);
    report.backlogBefore = await backlog();
    if (failed(report.backlogBefore) || pending(report.backlogBefore)) throw new Error('Fixture already contains unfinished tasks');
    async function browse(user, iteration) {
      if (lunch && iteration % 5 === 3)
        await api('/user/orders?limit=100', user.token, 'GET', undefined, 'orders.history', {}, true);
      else if (lunch && iteration % 5 === 2)
        await api(`/user/search/page?campusId=${fixture.campusId}&query=NoMatchingLunchItem&limit=50`, user.token, 'GET', undefined, 'search.miss', {}, true);
      else if (iteration % 2 === 0)
        await api(`/user/stores/${store.id}/products`, user.token, 'GET', undefined, 'menu', {}, true);
      else
        await api(`/user/search/page?campusId=${fixture.campusId}&query=Performance&limit=50`, user.token, 'GET', undefined, 'search.page', {}, true);
    }
    async function runStage(kind, concurrency, duration) {
      const current = { kind, concurrency, requestedSeconds: duration, succeeded: 0, unexpectedErrors: 0,
        errors: {}, workerFailures: 0, peakInFlightRequests: 0, completedTransactions: 0,
        latencies: [], readLatencies: [], transactionLatencies: [], operations: {}, expectedTransactions };
      const before = await backlog(), start = performance.now(), stop = Date.now() + duration * 1000;
      stage = current;
      try {
        const users = sessions.slice(0, concurrency).map(async (user, index) => {
          let iteration = index % 2, round = 0;
          try {
            while (Date.now() < stop) {
              if (kind === 'mixed' && index < writers && round < 3 && performance.now() - start >= round * 90000) {
                await transaction(user, current); round++;
              } else {
                await browse(user, iteration++);
                await sleep(Math.min(pauseMs, Math.max(0, stop - Date.now())));
              }
            }
            if (kind === 'mixed' && index < writers && round !== 3) throw new Error('Incomplete mixed transaction schedule');
          } catch (error) {
            current.workerFailures++;
            if (!error.requestFailure) {
              current.unexpectedErrors++; current.errors.BUSINESS_INVARIANT = (current.errors.BUSINESS_INVARIANT || 0) + 1;
            }
          }
        });
        if (lunch) users.push((async () => {
          const today = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10);
          const begin = new Date(Date.now() - 90 * 86400000 + 8 * 3600000).toISOString().slice(0, 10);
          try {
            while (Date.now() < stop) {
              await api(`/merchant/stores/${store.id}/orders?limit=100`, a, 'GET', undefined, 'merchant.history', {}, true);
              await api(`/platform/ledger/page?storeId=${store.id}&limit=100`, a, 'GET', undefined, 'ledger.history', {}, true);
              const reconciliation = await api(`/platform/finance/reconciliation?storeId=${store.id}&begin=${begin}&end=${today}`, a, 'GET', undefined, 'finance.reconcile', {}, true);
              if (Number(reconciliation.differenceCount) !== 0) throw new Error('Reconciliation difference');
              await sleep(Math.min(15000, Math.max(0, stop - Date.now())));
            }
          } catch (error) {
            current.workerFailures++;
            if (!error.requestFailure) {
              current.unexpectedErrors++; current.errors.BUSINESS_INVARIANT = (current.errors.BUSINESS_INVARIANT || 0) + 1;
            }
          }
        })());
        await Promise.all(users);
      } finally {
        stage = undefined;
        const summary = stageSummary(current, (performance.now() - start) / 1000);
        summary.expectedTransactions = expectedTransactions;
        summary.invariantsAfter = await invariants();
        summary.backlogBefore = before;
        report.stages.push(summary);
        await persist();
      }
      const summary = report.stages.at(-1);
      summary.backlogAfter = await backlog();
      summary.acceptance = acceptedStage(summary) && !failed(summary.backlogAfter);
      await persist();
      console.log(`${kind} clients=${concurrency}, requests=${summary.requests}, readP95=${summary.readP95Ms}ms, peakInFlight=${summary.peakInFlightRequests}, transactions=${summary.completedTransactions}, errors=${summary.unexpectedErrors}, failedWorkers=${summary.workerFailures}`);
      if (!summary.acceptance) throw new Error('Stage acceptance failed');
    }
    // Catch cross-identity fixture mistakes before timing the sustained workload.
    const preparedProducts = await api(`/user/stores/${store.id}/products`, sessions[0].token);
    if (!preparedProducts.some(value => value.id === product.id && Number(value.remaining) >= 15))
      throw new Error('Fixture product unavailable');
    if (!lunch) for (const concurrency of levels) await runStage('read', concurrency, seconds);
    await runStage('mixed', 30, 300);
    const drainStarted = Date.now(), drainDeadline = drainStarted + 60000;
    do {
      report.backlogAfter = await backlog();
      if (failed(report.backlogAfter)) throw new Error('Background task failed');
      if (!pending(report.backlogAfter)) break;
      await sleep(1000);
    } while (Date.now() < drainDeadline);
    report.drainElapsedMs = Date.now() - drainStarted;
    if (pending(report.backlogAfter)) throw new Error('Background tasks did not drain');
    report.invariantsAfter = await invariants();
    const before = report.invariantsBefore, after = report.invariantsAfter;
    if (after.orders - before.orders !== expectedTransactions || after.succeededRefunds - before.succeededRefunds !== expectedTransactions
        || after.quotaErrors || after.refundErrors || after.quantityErrors)
      throw new Error('Database business invariants failed');
    report.status = 'COMPLETED';
  } catch (error) {
    if (report) {
      report.status = 'FAILED';
      report.controlErrors.push('MEASUREMENT_STOPPED');
      const safeReasons = new Set(['Stage acceptance failed', 'Fixture product unavailable', 'Background task failed',
        'Background tasks did not drain', 'Database business invariants failed', 'Fixture already contains unfinished tasks',
        'Fixture HTTP identity mismatch', 'An isolated administrator with a changed password is required',
        'Isolated ownership or health check failed', 'Selected base URL does not match the isolated loopback binding',
        'Expected exactly four isolated service containers', 'Prepared dataset does not match actual database rows', 'DOCKER_READ_FAILED']);
      report.stopReason = safeReasons.has(error.message) ? error.message : 'SETUP_OR_CONTROL_REQUEST_FAILED';
      if (typeof error.code === 'string' && /^[A-Z0-9_]{1,80}$/.test(error.code)) report.failureCode = error.code;
    }
    process.exitCode = 1;
    console.error('Performance measurement failed. Inspect the partial report and isolated environment locally; no credentials are printed.');
  } finally {
    clearInterval(samplingTimer);
    if (sampleRunning) await sampleRunning;
    if (report && containers) {
      try {
        const after = JSON.parse(await docker(['inspect', ...containers.map(c => c.Id)]));
        report.containersAfter = after.map(c => ({ id: c.Id, name: c.Name.replace(/^\//, ''),
          service: c.Config.Labels['com.docker.compose.service'], imageId: c.Image,
          running: c.State.Running, oomKilled: c.State.OOMKilled, restartCount: c.RestartCount, health: c.State.Health?.Status }));
        report.containerAcceptance = report.containersAfter.every(c => {
          const before = report.containersBefore?.find(b => b.id === c.id);
          return before && c.running && c.health === 'healthy' && !before.oomKilled && !c.oomKilled && c.restartCount === before.restartCount;
        });
      } catch { report.controlErrors.push('FINAL_CONTAINER_INSPECTION_FAILED'); report.containerAcceptance = false; }
    }
    if (report && outputCreated) {
      report.completedAt = new Date().toISOString();
      report.acceptance = report.status === 'COMPLETED' && report.containerAcceptance === true
        && report.controlErrors.length === 0 && report.stages.every(value => value.acceptance === true);
      if (!report.acceptance) { report.status = 'FAILED'; process.exitCode = 1; }
      await persist();
      console.log('Report: ' + relative(root, output));
    }
  }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(() => { console.error('Measurement report could not be saved; inspect protected output storage.'); process.exitCode = 1; });
}
