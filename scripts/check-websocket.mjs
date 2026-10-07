import fs from 'node:fs/promises';
import http from 'node:http';
import { randomBytes } from 'node:crypto';
const env = Object.fromEntries(
  (await fs.readFile('.env', 'utf8'))
    .split(/\r?\n/)
    .filter((l) => l.includes('=') && !l.startsWith('#'))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]),
);
const base = process.env.BASE_URL || `http://localhost:${env.WEB_PORT || 18083}`;
const origin = new URL(base).origin;
let token = '';
async function call(path, method = 'POST', body) {
  const r = await fetch(base + '/api/v1/admin' + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const result = await r.json();
  if (!r.ok || result.code !== 'OK') throw Error(`${path}: ${r.status} ${result.code}`);
  return result.data;
}
function connect(ticket, allowedOrigin) {
  return new Promise((resolve, reject) => {
    const req = http.request(new URL('/ws/orders?ticket=' + encodeURIComponent(ticket), base), {
      headers: {
        Connection: 'Upgrade',
        Upgrade: 'websocket',
        'Sec-WebSocket-Version': '13',
        'Sec-WebSocket-Key': randomBytes(16).toString('base64'),
        Origin: allowedOrigin,
      },
    });
    req.setTimeout(5000, () => req.destroy(Error('Handshake timed out')));
    req.on('error', reject);
    req.on('response', (res) => {
      res.resume();
      resolve({ status: res.statusCode });
    });
    req.on('upgrade', (res, socket) => resolve({ status: res.statusCode, socket }));
    req.end();
  });
}
const login = await call('/auth/login', 'POST', {
  username: process.env.ADMIN_USERNAME || 'admin',
  password: process.env.ADMIN_PASSWORD || env.DEMO_ADMIN_PASSWORD,
});
token = login.token;
const { ticket } = await call('/ws-ticket');
const wrongOrigin = await connect(ticket, 'https://untrusted.example');
if (wrongOrigin.status === 101) throw Error('Untrusted origin accepted');
const valid = await connect(ticket, origin);
if (valid.status !== 101) throw Error('Valid ticket rejected');
try {
  const reused = await connect(ticket, origin);
  if (reused.status === 101) throw Error('Ticket reused');
  const fake = await connect('00000000-0000-4000-8000-000000000000', origin);
  if (fake.status === 101) throw Error('Forged ticket accepted');
  await call('/auth/logout');
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(Error('Revoked session socket still open')), 25000);
    valid.socket.on('data', (data) => {
      if ((data[0] & 15) === 8) {
        clearTimeout(timer);
        resolve();
      }
    });
    valid.socket.on('close', () => {
      clearTimeout(timer);
      resolve();
    });
    valid.socket.on('error', reject);
  });
  console.log(
    'WebSocket handshake: allowed origin/single-use ticket accepted; foreign origin, replay and forged ticket rejected; logout closed connection.',
  );
} finally {
  valid.socket.destroy();
}
