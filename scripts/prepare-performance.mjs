import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { randomUUID, createHmac } from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
class FixtureError extends Error {}
async function main() {
const args = process.argv.slice(2);
const option = (name) => {
  const index = args.indexOf(name);
  if (index < 0 || !args[index + 1]) throw new FixtureError(`Missing ${name}`);
  return args[index + 1];
};
const project = option('--project');
const envFile = path.resolve(root, option('--config'));
const output = path.resolve(root, option('--output'));
const base = new URL(option('--base-url'));
const local = path.join(root, '.local') + path.sep;
if (!/^campuseats-(?:perf|improvement(?:-acceptance)?|role-uat)(?:-[a-z0-9]+)*$/.test(project))
  throw new FixtureError('Only explicitly isolated CampusEats performance/acceptance projects are allowed.');
if (![envFile, output].every((value) => value.startsWith(local)))
  throw new FixtureError('Isolated configuration and token output must stay inside workspace .local.');
if (!['localhost', '127.0.0.1'].includes(base.hostname) || base.protocol !== 'http:' || !base.port || base.port === '18083'
    || base.username || base.password || base.pathname !== '/' || base.search || base.hash)
  throw new FixtureError('Use an explicit isolated loopback HTTP port, never the daily deployment.');
const metadataOutput = output.replace(/\.json$/, '') + '.metadata.json';
const growHistory = args.includes('--grow-history');
for (const target of growHistory ? [] : [output, metadataOutput]) {
  let exists = true;
  try { await fs.access(target); } catch (error) { if (error.code === 'ENOENT') exists = false; else throw error; }
  if (exists) throw new FixtureError('Use fresh private session output filenames; existing sessions are preserved.');
}

function docker(args, input, timeout = 20000) {
  try {
    return execFileSync('docker', args, { cwd: root, input, encoding: 'utf8', timeout, stdio: ['pipe', 'pipe', 'pipe'] }).trim();
  } catch {
    throw new FixtureError('Isolated Docker fixture command failed; inspect the selected project locally.');
  }
}
const compose = ['compose', '--env-file', envFile, '-p', project];
const containers = {};
for (const service of ['web', 'server', 'mysql', 'redis']) {
  const id = docker([...compose, 'ps', '-q', service]);
  if (!id) throw new FixtureError(`Missing isolated ${service} container.`);
  const [container] = JSON.parse(docker(['inspect', id]));
  const labels = container.Config.Labels;
  if (labels['com.docker.compose.project'] !== project || path.resolve(labels['com.docker.compose.project.working_dir'] || '') !== root)
    throw new FixtureError('Container ownership does not match this workspace/project.');
  containers[service] = container;
}
for (const [service, destination, volumeKey] of [['mysql', '/var/lib/mysql', 'mysql-data'], ['redis', '/data', 'redis-data'], ['server', '/app/uploads', 'uploads']]) {
  const mount = containers[service].Mounts.find((item) => item.Destination === destination);
  if (!mount || mount.Type !== 'volume' || mount.Name !== `${project}_${volumeKey}`)
    throw new FixtureError('Isolated data mounts must use their own project-scoped named volumes.');
  const [volume] = JSON.parse(docker(['volume', 'inspect', mount.Name]));
  if (volume.Labels?.['com.docker.compose.project'] !== project || volume.Labels?.['com.docker.compose.volume'] !== volumeKey)
    throw new FixtureError('Data volume ownership does not match the isolated project.');
}
const bindings = containers.web.NetworkSettings.Ports['80/tcp'] || [];
if (!bindings.some((binding) => binding.HostPort === base.port && ['127.0.0.1', '::1'].includes(binding.HostIp)))
  throw new FixtureError('Selected URL does not match the isolated web container loopback binding.');
const config = Object.fromEntries((await fs.readFile(envFile, 'utf8')).split(/\r?\n/)
  .filter((line) => line && !line.startsWith('#') && line.includes('='))
  .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
if (!config.JWT_ADMIN_SECRET || config.JWT_ADMIN_SECRET.length < 32)
  throw new FixtureError('Missing isolated signing configuration.');
const actual = containers.server.Config.Env.find((value) => value.startsWith('JWT_ADMIN_SECRET='));
if (actual !== `JWT_ADMIN_SECRET=${config.JWT_ADMIN_SECRET}`) throw new FixtureError('Isolated signing configuration does not match the running server.');
const sql = (statement, timeout) => docker([...compose, 'exec', '-T', 'mysql', 'sh', '-c',
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u campus sky_take_out -N'], statement, timeout);
if (growHistory) {
  const metadata = JSON.parse(await fs.readFile(metadataOutput, 'utf8'));
  const count = Number(option('--history-orders'));
  if (metadata.project !== project || metadata.baseUrl !== base.origin
      || !Number.isInteger(count) || count < 1 || count > 200000
      || !metadata.fixture || ['campusId', 'storeId', 'productId'].some(key => !/^[1-9]\d*$/.test(metadata.fixture[key]))
      || !Array.isArray(metadata.userIds) || metadata.userIds.length !== 30
      || metadata.userIds.some(id => !/^[1-9]\d*$/.test(id)))
    throw new FixtureError('History growth requires matching isolated metadata, 30 users and a target in 1..200000.');
  const { campusId, storeId } = metadata.fixture;
  const prefix = `perf-history-${campusId}-`;
  const previous = Number(sql(`SELECT COUNT(*) FROM orders WHERE campus_id=${campusId} AND number LIKE '${prefix}%';`));
  if (previous >= count) throw new FixtureError('History target must exceed the existing synthetic history.');
  if (sql(`SELECT COUNT(*) FROM store WHERE campus_id=${campusId};`) !== '205'
      || sql(`SELECT COUNT(*) FROM user WHERE id IN (${metadata.userIds.join(',')});`) !== '30')
    throw new FixtureError('History growth fixture ownership does not match the isolated database.');
  const digits = '(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)';
  const sequence = '1+a.n+10*b.n+100*c.n+1000*d.n+10000*e.n+100000*f.n';
  sql(`
    CREATE TEMPORARY TABLE perf_history_sequence AS SELECT ${sequence} n FROM ${digits} a CROSS JOIN ${digits} b CROSS JOIN ${digits} c CROSS JOIN ${digits} d CROSS JOIN ${digits} e CROSS JOIN ${digits} f WHERE ${sequence}>${previous} AND ${sequence}<=${count};
    CREATE TEMPORARY TABLE perf_history_stores AS SELECT id,ROW_NUMBER() OVER(ORDER BY id)-1 slot FROM store WHERE campus_id=${campusId};
    CREATE TEMPORARY TABLE perf_history_users AS SELECT id,ROW_NUMBER() OVER(ORDER BY id)-1 slot FROM user WHERE id IN (${metadata.userIds.join(',')});
    START TRANSACTION;
    UPDATE finance_cutover SET captured_at=LEAST(captured_at,DATE_SUB(NOW(),INTERVAL 120 DAY));
    INSERT INTO orders(number,status,user_id,address_book_id,order_time,checkout_time,pay_status,amount,business_date,snapshot_source,store_id,campus_id,fulfillment,ready,completed_at,paid_cents)
      SELECT CONCAT('${prefix}',n),5,u.id,0,DATE_SUB(CURDATE(),INTERVAL (1+MOD(FLOOR(n/30),90)) DAY)+INTERVAL 12 HOUR,DATE_SUB(CURDATE(),INTERVAL (1+MOD(FLOOR(n/30),90)) DAY)+INTERVAL 12 HOUR,1,10,DATE_SUB(CURDATE(),INTERVAL (1+MOD(FLOOR(n/30),90)) DAY),'PERF_FIXTURE',s.id,${campusId},'PICKUP',true,DATE_SUB(CURDATE(),INTERVAL (1+MOD(FLOOR(n/30),90)) DAY)+INTERVAL 13 HOUR,1000
      FROM perf_history_sequence q JOIN perf_history_users u ON u.slot=MOD(q.n,30)
      JOIN perf_history_stores s ON s.slot=IF(MOD(q.n,5)=0,MOD(q.n,204)+1,0);
    INSERT INTO order_detail(name,image,order_id,dish_id,number,amount,line_total_cents)
      SELECT 'Performance history','/demo-images/dish.svg',o.id,MIN(d.id),1,10,1000 FROM orders o JOIN dish d ON d.store_id=o.store_id
      WHERE o.campus_id=${campusId} AND o.number LIKE '${prefix}%' AND CAST(SUBSTRING(o.number,${prefix.length + 1}) AS UNSIGNED)>${previous} GROUP BY o.id;
    INSERT INTO finance_ledger(source_type,source_id,order_id,store_id,amount_cents,occurred_at)
      SELECT 'PAYMENT',o.id,o.id,o.store_id,1000,o.checkout_time FROM orders o WHERE o.campus_id=${campusId} AND o.number LIKE '${prefix}%' AND CAST(SUBSTRING(o.number,${prefix.length + 1}) AS UNSIGNED)>${previous};
    INSERT INTO payment_attempt(order_id,gateway_ref,created_at)
      SELECT o.id,CONCAT('PERF-',o.id),o.checkout_time FROM orders o WHERE o.campus_id=${campusId} AND o.number LIKE '${prefix}%' AND CAST(SUBSTRING(o.number,${prefix.length + 1}) AS UNSIGNED)>${previous};
    INSERT INTO order_reservation(order_id,dish_id,business_date,quantity,state)
      SELECT o.id,d.dish_id,o.business_date,1,'CONSUMED' FROM orders o JOIN order_detail d ON d.order_id=o.id WHERE o.campus_id=${campusId} AND o.number LIKE '${prefix}%' AND CAST(SUBSTRING(o.number,${prefix.length + 1}) AS UNSIGNED)>${previous};
    INSERT INTO daily_quota(business_date,dish_id,total,reserved,consumed)
      SELECT o.business_date,d.dish_id,10000,0,COUNT(*) FROM orders o JOIN order_detail d ON d.order_id=o.id WHERE o.campus_id=${campusId} AND o.number LIKE '${prefix}%' AND CAST(SUBSTRING(o.number,${prefix.length + 1}) AS UNSIGNED)>${previous} GROUP BY o.business_date,d.dish_id
      ON DUPLICATE KEY UPDATE consumed=daily_quota.consumed+VALUES(consumed);
    COMMIT;
    SELECT COUNT(*) FROM orders WHERE campus_id=${campusId} AND number LIKE '${prefix}%';
  `, 180000);
  const actual = Number(sql(`SELECT COUNT(*) FROM orders WHERE campus_id=${campusId} AND number LIKE '${prefix}%';`));
  if (actual !== count) throw new FixtureError('Historical fixture count does not match the requested target.');
  metadata.fixture.historyOrders = actual;
  metadata.fixture.historyHotStoreId = storeId;
  metadata.fixture.historyPolicy = 'Synthetic completed pickup orders over 90 days; 80% at the hot store; one detail, payment ledger, receipt and consumed reservation per order. Not a replay of real traffic.';
  await fs.writeFile(metadataOutput, JSON.stringify(metadata, null, 2) + '\n', { mode: 0o600 });
  console.log(`Prepared ${actual} synthetic historical orders in the verified isolated project.`);
  return;
}
const userCount = args.includes('--users') ? Number(option('--users')) : 30;
if (!Number.isInteger(userCount) || userCount < 1 || userCount > 30)
  throw new FixtureError('Fixture users must be in 1..30.');
const fixturePrefix = `perf-${randomUUID()}`;
const statements = [`INSERT INTO campus(name) VALUES('${fixturePrefix}'); SET @perf_campus=LAST_INSERT_ID();`];
for (let store = 0; store < 205; store++) {
  statements.push(`INSERT INTO store(campus_id,name,open,delivery_fee,packaging_fee,hours) VALUES(@perf_campus,'${fixturePrefix}-${store}',true,0,0,'["00:00-24:00"]'); SET @perf_store=LAST_INSERT_ID();`);
  statements.push("INSERT INTO category(store_id,name,type,sort,status) VALUES(@perf_store,'Performance',1,0,1); SET @perf_category=LAST_INSERT_ID();");
  statements.push("INSERT INTO dish(store_id,name,category_id,price,status,default_quota,image) VALUES " +
    Array.from({length: 5}, (_, dish) => `(@perf_store,'Performance dish ${dish}',@perf_category,10,1,10000,'/demo-images/dish.svg')`).join(',') + ';');
}
statements.push('SELECT @perf_campus,MIN(s.id),MIN(d.id),COUNT(DISTINCT s.id),COUNT(d.id) FROM store s JOIN dish d ON d.store_id=s.id WHERE s.campus_id=@perf_campus;');
const fixtureValues = sql(statements.join('\n')).split('\t');
if (fixtureValues.length !== 5 || fixtureValues.some(value => !/^\d+$/.test(value))
    || Number(fixtureValues[3]) !== 205 || Number(fixtureValues[4]) !== 1025)
  throw new FixtureError('Isolated dataset creation failed.');
const fixture = {campusId: fixtureValues[0], storeId: fixtureValues[1], productId: fixtureValues[2], stores: 205, products: 1025};
const sessions = [];
const prefix = `perf-${randomUUID()}`;
const issued = Math.floor(Date.now() / 1000);
const expires = issued + 7200;
const expiryShanghai = new Date((expires + 8 * 3600) * 1000).toISOString().slice(0, 19).replace('T', ' ');
async function identity(type, id) {
  const jti = randomUUID();
  sql(`INSERT INTO auth_session(jti,subject_type,subject_id,auth_version,expires_at) VALUES('${jti}','${type}',${id},0,'${expiryShanghai}');`);
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const body = `${encode({ alg: 'HS256' })}.${encode({ iss: 'campuseats', sub: `${type}:${id}`, jti, iat: issued, exp: expires })}`;
  const token = `${body}.${createHmac('sha256', config.JWT_ADMIN_SECRET).update(body).digest('base64url')}`;
  const response = await fetch(new URL(`/api/v1/${type === 'USER' ? 'user' : 'admin'}/auth/me`, base), { headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(5000) });
  const result = await response.json();
  if (!response.ok || result.code !== 'OK' || result.data.id !== id || result.data.type !== type)
    throw new FixtureError('Fixture session was rejected by normal authentication.');
  return token;
}
const staff = [];
if (args.includes('--lunch')) {
  for (let index = 0; index < 3; index++) {
    const id = sql(`INSERT INTO employee(name,username,password,role,store_id,campus_id,status,phone,sex,id_number,must_change_password)
      SELECT 'Performance manager','perf-${fixture.campusId}-${index}',password,'SHOP_MANAGER',${fixture.storeId},${fixture.campusId},1,'','','',false FROM employee WHERE id=1; SELECT LAST_INSERT_ID();`);
    staff.push({ id, token: await identity('ADMIN', id) });
  }
}
for (let index = 0; index < userCount; index++) {
  const openid = `${prefix}-${index}`;
  const id = sql(`INSERT INTO user(openid,name,create_time) VALUES('${openid}','Performance fixture ${index}',NOW()); SELECT LAST_INSERT_ID();`);
  if (!/^\d+$/.test(id)) throw new FixtureError('Invalid fixture subject identifier.');
  const token = await identity('USER', id);
  sessions.push({ id, token, ...(staff.length ? { staffId: staff[index % staff.length].id, staffToken: staff[index % staff.length].token } : {}) });
}
await fs.mkdir(path.dirname(output), { recursive: true });
await fs.writeFile(output, JSON.stringify(sessions, null, 2) + '\n', { mode: 0o600, flag: 'wx' });
await fs.writeFile(metadataOutput, JSON.stringify({
  baseUrl: base.origin, project, preparedAt: new Date().toISOString(), userIds: sessions.map((session) => session.id),
  fixture,
}, null, 2) + '\n', { mode: 0o600, flag: 'wx' });
console.log(`Prepared ${sessions.length} isolated database-fixture identities; private sessions saved to ${path.relative(root, output)}.`);
console.log('Identity setup bypasses login via an isolated database fixture; measurements do not measure login scalability.');
}
main().catch((error) => {
  console.error(error instanceof FixtureError ? error.message : 'Performance identity preparation failed. Inspect the isolated configuration locally; no credentials are printed.');
  process.exitCode = 1;
});
