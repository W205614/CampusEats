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
if (!/^campuseats-(?:perf|improvement-acceptance|role-uat)(?:-[a-z0-9]+)*$/.test(project))
  throw new FixtureError('Only explicitly isolated CampusEats performance/acceptance projects are allowed.');
if (![envFile, output].every((value) => value.startsWith(local)))
  throw new FixtureError('Isolated configuration and token output must stay inside workspace .local.');
if (!['localhost', '127.0.0.1'].includes(base.hostname) || base.protocol !== 'http:' || !base.port || base.port === '18083'
    || base.username || base.password || base.pathname !== '/' || base.search || base.hash)
  throw new FixtureError('Use an explicit isolated loopback HTTP port, never the daily deployment.');
const metadataOutput = output.replace(/\.json$/, '') + '.metadata.json';
for (const target of [output, metadataOutput]) {
  let exists = true;
  try { await fs.access(target); } catch (error) { if (error.code === 'ENOENT') exists = false; else throw error; }
  if (exists) throw new FixtureError('Use fresh private session output filenames; existing sessions are preserved.');
}

function docker(args, input) {
  try {
    return execFileSync('docker', args, { cwd: root, input, encoding: 'utf8', timeout: 20000, stdio: ['pipe', 'pipe', 'pipe'] }).trim();
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
const sql = (statement) => docker([...compose, 'exec', '-T', 'mysql', 'sh', '-c',
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u campus sky_take_out -N'], statement);
const sessions = [];
const prefix = `perf-${randomUUID()}`;
const issued = Math.floor(Date.now() / 1000);
const expires = issued + 7200;
const expiryShanghai = new Date((expires + 8 * 3600) * 1000).toISOString().slice(0, 19).replace('T', ' ');
for (let index = 0; index < 10; index++) {
  const openid = `${prefix}-${index}`;
  const id = sql(`INSERT INTO user(openid,name,create_time) VALUES('${openid}','Performance fixture ${index}',NOW()); SELECT LAST_INSERT_ID();`);
  if (!/^\d+$/.test(id)) throw new FixtureError('Invalid fixture subject identifier.');
  const jti = randomUUID();
  sql(`INSERT INTO auth_session(jti,subject_type,subject_id,auth_version,expires_at) VALUES('${jti}','USER',${id},0,'${expiryShanghai}');`);
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const body = `${encode({ alg: 'HS256' })}.${encode({ iss: 'campuseats', sub: `USER:${id}`, jti, iat: issued, exp: expires })}`;
  const token = `${body}.${createHmac('sha256', config.JWT_ADMIN_SECRET).update(body).digest('base64url')}`;
  const response = await fetch(new URL('/api/v1/user/auth/me', base), { headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(5000) });
  const result = await response.json();
  if (!response.ok || result.code !== 'OK' || result.data.id !== id || result.data.role !== 'USER')
    throw new FixtureError('Fixture session was rejected by normal authentication.');
  sessions.push({ id, token });
}
await fs.mkdir(path.dirname(output), { recursive: true });
await fs.writeFile(output, JSON.stringify(sessions, null, 2) + '\n', { mode: 0o600, flag: 'wx' });
await fs.writeFile(metadataOutput, JSON.stringify({
  baseUrl: base.origin, project, preparedAt: new Date().toISOString(), userIds: sessions.map((session) => session.id),
}, null, 2) + '\n', { mode: 0o600, flag: 'wx' });
console.log(`Prepared ${sessions.length} isolated database-fixture identities; private sessions saved to ${path.relative(root, output)}.`);
console.log('Identity setup bypasses login via an isolated database fixture; measurements do not measure login scalability.');
}
main().catch((error) => {
  console.error(error instanceof FixtureError ? error.message : 'Performance identity preparation failed. Inspect the isolated configuration locally; no credentials are printed.');
  process.exitCode = 1;
});
