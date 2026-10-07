import fs from 'node:fs/promises';
const config = Object.fromEntries(
  (await fs.readFile('.env', 'utf8'))
    .split(/\r?\n/)
    .filter((l) => l.includes('='))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]),
);
const base = 'http://localhost:18083';
let token = '';
async function call(path, method = 'GET', data) {
  const r = await fetch(base + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: data ? JSON.stringify(data) : undefined,
  });
  const value = await r.json();
  if (value.code !== 'OK') throw Error(value.code + ' ' + r.status);
  return value.data;
}
async function user() {
  const session = await call('/api/v1/user/auth/demo', 'POST', { account: 3 });
  token = session.token;
}
async function create() {
  await user();
  let cart = await call('/api/v1/user/cart');
  if (cart.items.length)
    cart = await call('/api/v1/user/cart/clear', 'POST', { version: cart.cartVersion });
  cart = await call('/api/v1/user/cart/items', 'POST', {
    cartVersion: cart.cartVersion,
    itemId: '2',
    itemType: 'DISH',
    delta: 1,
    flavors: {},
  });
  const address = await call('/api/v1/user/addresses', 'POST', {
    buildingId: '1',
    room: '恢复验证305',
    consignee: '故障验收用户',
    phone: '13800000000',
  });
  const quote = await call('/api/v1/user/checkout/preview', 'POST', {
    addressId: address.id,
    cartVersion: cart.cartVersion,
  });
  const key = crypto.randomUUID();
  let response = await fetch(base + '/api/v1/user/orders', {
    method: 'POST',
    headers: {
      Authorization: 'Bearer ' + token,
      'Content-Type': 'application/json',
      'Idempotency-Key': key,
    },
    body: JSON.stringify({
      addressId: address.id,
      cartVersion: cart.cartVersion,
      quoteHash: quote.quoteHash,
      remark: '恢复演练',
    }),
  }).then((r) => r.json());
  if (response.code !== 'OK') throw Error(response.code);
  let order = response.data;
  order = await call('/api/v1/user/orders/' + order.id + '/pay', 'POST', {
    version: order.version,
  });
  order = await call('/api/v1/user/orders/' + order.id + '/cancel', 'POST', {
    version: order.version,
    reason: '恢复演练',
  });
  await fs.writeFile('.local/recovery-order.json', JSON.stringify({ id: order.id }));
  console.log('Created paid cancellation, order ' + order.id);
  return order;
}
async function verifyRefund() {
  await user();
  const { id } = JSON.parse(await fs.readFile('.local/recovery-order.json', 'utf8'));
  let order;
  for (let n = 0; n < 40; n++) {
    order = await call('/api/v1/user/orders/' + id);
    if (order.payStatus === 2 && order.refund?.state === 'SUCCEEDED') break;
    await new Promise((r) => setTimeout(r, 500));
  }
  if (order.payStatus !== 2 || order.refund?.state !== 'SUCCEEDED')
    throw Error('Refund did not recover');
  console.log('Verified persisted refund success, order ' + id);
}
if (process.argv[2] === 'prepare') await create();
if (process.argv[2] === 'verify') await verifyRefund();
if (process.argv[2] === 'redis') {
  await create();
  await verifyRefund();
  const menu = await call('/api/v1/user/menu/items?categoryId=1&type=DISH');
  if (!menu.length) throw Error('Menu fallback failed');
  token = '';
  const admin = await call('/api/v1/admin/auth/login', 'POST', {
    username: 'admin',
    password: config.DEMO_ADMIN_PASSWORD,
  });
  token = admin.token;
  const response = await fetch(base + '/api/v1/admin/ws-ticket', {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + token },
  });
  const value = await response.json();
  if (response.status !== 503 || value.code !== 'REDIS_UNAVAILABLE')
    throw Error('Ticket did not fail closed');
  console.log(
    'Redis outage: order/payment/cancellation/refund/menu OK; new socket ticket rejected with 503.',
  );
}
