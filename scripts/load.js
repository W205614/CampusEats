import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import crypto from 'k6/crypto';
const users = JSON.parse(open('/data/users.json')),
  base = __ENV.BASE_URL || 'http://web';
const unexpected = new Rate('unexpected_failure'),
  menu = new Trend('menu_ms', true),
  orders = new Trend('orders_ms', true),
  submitTime = new Trend('submit_ms', true),
  rejections = new Counter('business_rejections'),
  duplicates = new Counter('duplicate_order_results'),
  failures = new Counter('unexpected_status');
export const options = {
  vus: Number(__ENV.VUS || 50),
  duration: __ENV.DURATION || '10m',
  thresholds: {
    unexpected_failure: ['rate<0.01'],
    menu_ms: ['p(95)<300'],
    orders_ms: ['p(95)<500'],
    submit_ms: ['p(95)<800'],
    duplicate_order_results: ['count==0'],
  },
  summaryTrendStats: ['avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
};
function request(method, path, input, user, extra = {}) {
  const response = http.request(method, base + path, input ? JSON.stringify(input) : null, {
    headers: {
      Authorization: 'Bearer ' + user.token,
      'Content-Type': 'application/json',
      ...extra,
    },
    tags: { name: path.replace(/[0-9]+/g, ':id') },
  });
  let data;
  try {
    data = response.json();
  } catch {
    unexpected.add(true);
    return null;
  }
  const business =
    response.status === 409 &&
    ['SOLD_OUT', 'CART_CHANGED', 'QUOTE_CHANGED', 'ORDER_CHANGED', 'ORDER_STATE'].includes(
      data.code,
    );
  if (business) rejections.add(1);
  unexpected.add(!(response.status >= 200 && response.status < 300) && !business);
  if (!(response.status >= 200 && response.status < 300) && !business) {
    failures.add(1, { status: String(response.status), code: data.code || 'UNKNOWN' });
    console.warn(
      'Unexpected ' +
        response.status +
        ' ' +
        (data.code || 'UNKNOWN') +
        ' ' +
        method +
        ' ' +
        path.split('?')[0].replace(/\/[0-9]+(?=\/|$)/g, '/:id'),
    );
  }
  return data.code === 'OK' ? data.data : null;
}
function uuid() {
  const h = crypto.randomBytes(16);
  const bytes = new Uint8Array(h);
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const s = Array.from(bytes, (x) => x.toString(16).padStart(2, '0')).join('');
  return (
    s.slice(0, 8) +
    '-' +
    s.slice(8, 12) +
    '-' +
    s.slice(12, 16) +
    '-' +
    s.slice(16, 20) +
    '-' +
    s.slice(20)
  );
}
export default function () {
  const user = users[(__VU - 1) % users.length];
  let t = Date.now();
  const m = request('GET', '/api/v1/user/menu/items?categoryId=1&type=DISH', null, user);
  menu.add(Date.now() - t);
  check(m, { 'menu returned': (x) => Array.isArray(x) });
  t = Date.now();
  const list = request('GET', '/api/v1/user/orders?page=1&size=20', null, user);
  orders.add(Date.now() - t);
  check(list, { 'orders returned': (x) => x && Array.isArray(x.records) });
  if (__ITER % 10 === 0) {
    let cart = request('GET', '/api/v1/user/cart', null, user);
    if (cart) {
      if (cart.items.length)
        cart = request(
          'POST',
          '/api/v1/user/cart/clear',
          { version: Number(cart.cartVersion) },
          user,
        );
      cart = request(
        'POST',
        '/api/v1/user/cart/items',
        {
          cartVersion: Number(cart.cartVersion),
          itemType: 'DISH',
          itemId: 2,
          flavors: {},
          delta: 1,
        },
        user,
      );
      if (cart) {
        const quote = request(
          'POST',
          '/api/v1/user/checkout/preview',
          { cartVersion: Number(cart.cartVersion), addressId: Number(user.addressId) },
          user,
        );
        if (quote) {
          const key = uuid(),
            input = {
              addressId: Number(user.addressId),
              cartVersion: Number(cart.cartVersion),
              quoteHash: quote.quoteHash,
              remark: '压测订单',
            };
          t = Date.now();
          const order = request('POST', '/api/v1/user/orders', input, user, {
            'Idempotency-Key': key,
          });
          submitTime.add(Date.now() - t);
          if (order) {
            const retried = request('POST', '/api/v1/user/orders', input, user, {
              'Idempotency-Key': key,
            });
            duplicates.add(!retried || retried.id !== order.id ? 1 : 0);
            request(
              'POST',
              '/api/v1/user/orders/' + order.id + '/cancel',
              { version: Number(order.version), reason: '压测取消' },
              user,
            );
          }
        }
      }
    }
  }
  sleep(1);
}
