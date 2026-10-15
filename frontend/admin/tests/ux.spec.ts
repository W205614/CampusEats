import { randomUUID } from 'node:crypto';
import { test, expect, call, asSession, password } from './helpers';
import type { Session } from '../../contracts/models';
import type { Cart, Checkout, Order, Product, Quote, Store } from '../../contracts/platform';

test('courier login opens the delivery desk and protected routes return to it', async ({ page, request, adminSession }) => {
  const username = 'uxcourier' + randomUUID().slice(0, 8);
  const initial = password(), changed = 'Ux-' + randomUUID();
  await call(request, '/platform/staff', adminSession.token, 'POST', { username, name: '页面配送验收', role: 'DELIVERER', campusId: '1', enabled: true, password: initial });
  const first = await call<Session>(request, '/admin/auth/login', '', 'POST', { username, password: initial });
  await call(request, '/admin/auth/password', first.token, 'POST', { oldPassword: initial, password: changed });
  const failures: string[] = [];
  page.on('response', r => { if (r.status() >= 400 && r.url().includes('/api/v1/merchant')) failures.push(r.url()); });
  await page.goto('/admin/');
  await page.getByLabel('账户', { exact: true }).fill(username);
  await page.getByLabel('密码', { exact: true }).fill(changed);
  await page.getByRole('button', { name: '登录工作台' }).click();
  await expect(page.getByRole('heading', { name: '抢单与配送', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: '校区抢单池', exact: true })).toBeVisible();
  await page.goto('/admin/catalog');
  await expect(page).toHaveURL(/\/admin\/orders$/);
  expect(failures).toEqual([]);
});

test('recovery retains unknown results but releases a rejected original checkout', async ({ page, userSession }) => {
  await asSession(page, userSession, true);
  const key = randomUUID();
  await page.addInitScript(({ id, key }) => localStorage.setItem('checkout-pending-' + id, JSON.stringify({ type: 'object', data: { key, body: { stores: [] } } })), { id: userSession.id, key });
  await page.route('**/api/v1/user/checkouts/by-request/*', route => route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: '尚未创建', data: null } }));
  let attempts = 0;
  await page.route('**/api/v1/user/checkouts', route => route.fulfill({ status: ++attempts === 1 ? 503 : 409, json: { code: attempts === 1 ? 'UNAVAILABLE' : 'VERSION_CONFLICT', message: attempts === 1 ? '暂不可用' : '购物车已更新', data: null } }));
  await page.goto('/app/pages/checkout/index');
  const recovery = page.getByText('查询并恢复原请求', { exact: true });
  await recovery.click();
  await expect(page.getByText('暂不可用', { exact: true })).toBeVisible();
  await expect(recovery).toBeVisible();
  await recovery.click();
  await expect(recovery).toHaveCount(0);
  await expect(page.getByText('计算报价', { exact: true })).toBeVisible();
  expect(await page.evaluate(id => localStorage.getItem('checkout-pending-' + id), userSession.id)).toBeNull();
});

test('order page exposes reminder and reorder actions with working requests', async ({ request, page, adminSession, userSession }) => {
  const a = adminSession.token, u = userSession.token;
  const store = (await call<Store[]>(request, '/merchant/stores', a)).find(s => s.id === '1')!;
  await call(request, '/merchant/stores/1', a, 'PUT', { campusId: store.campusId, name: store.name, enabled: true, open: true, deliveryFeeCents: 600, packagingFeeCents: 100, hours: ['00:00-24:00'], phone: store.phone });
  const product = (await call<Product[]>(request, '/user/stores/1/products', u)).find(p => p.itemType === 'DISH' && !p.flavors?.length && p.remaining > 0)!;
  let cart = await call<Cart>(request, '/user/stores/1/cart', u);
  await call(request, '/user/stores/1/cart/clear', u, 'POST', { version: cart.cartVersion });
  cart = await call<Cart>(request, '/user/stores/1/cart', u);
  cart = await call<Cart>(request, '/user/stores/1/cart/items', u, 'POST', { cartVersion: cart.cartVersion, itemType: product.itemType, itemId: product.id, flavors: {}, delta: 1 });
  const input = { stores: [{ storeId: '1', cartVersion: cart.cartVersion, fulfillment: 'PICKUP', couponId: null, addressId: null }] };
  const quote = await call<Quote>(request, '/user/checkouts/preview', u, 'POST', input);
  const checkout = await call<Checkout>(request, '/user/checkouts', u, 'POST', { ...input, quoteHash: quote.quoteHash }, { 'Idempotency-Key': randomUUID() });
  const paid = await call<Checkout>(request, '/user/checkouts/' + checkout.id + '/pay', u, 'POST', { version: checkout.version });
  let order = paid.orders[0]!;
  await asSession(page, userSession, true);
  await page.goto('/app/pages/orders/index');
  const card = page.locator('uni-view.card').filter({ has: page.getByText(order.number + ' · 自取', { exact: true }) });
  const reminder = page.waitForResponse(r => r.url().endsWith('/orders/' + order.id + '/reminder'));
  await card.getByText('催单', { exact: true }).click();
  expect((await reminder).ok()).toBeTruthy();
  for (const action of ['accept', 'ready']) {
    await call(request, '/merchant/orders/' + order.id + '/actions/' + action, a, 'POST', { version: order.version });
    order = await call<Order>(request, '/user/orders/' + order.id, u);
  }
  await call(request, '/merchant/orders/' + order.id + '/actions/verify', a, 'POST', { version: order.version, code: order.pickupCode });
  await page.reload();
  const response = page.waitForResponse(r => r.url().endsWith('/orders/' + order.id + '/reorder'));
  await card.getByText('再来一单', { exact: true }).click();
  expect((await response).ok()).toBeTruthy();
  expect((await call<Cart>(request, '/user/stores/1/cart', u)).items.some(i => i.itemId === product.id)).toBeTruthy();
});

test('menu exposes category and campus opening filters and names search result stores', async ({ page }) => {
  await page.goto('/app/');
  await expect(page.getByText('分类：本店全部分类 ▾', { exact: true })).toBeVisible();
  await page.getByText('校区筛选：本校区全部店铺 ▾', { exact: true }).click();
  const search = page.waitForResponse(r => r.url().includes('/user/search?') && r.url().includes('open=true'));
  await page.locator('.uni-picker-item:visible').filter({ hasText: '仅营业中' }).click();
  expect((await search).ok()).toBeTruthy();
  await expect(page.getByText('校区筛选：仅营业中 ▾', { exact: true })).toBeVisible();
});

test('quota and report default dates follow the browser local day near midnight', async ({ page, adminSession }) => {
  await asSession(page, adminSession);
  await page.clock.install({ time: new Date('2026-10-15T17:30:00Z') });
  await page.goto('/admin/catalog');
  await expect(page.locator('input[type="date"]')).toHaveValue('2026-10-16');
  await page.goto('/admin/reports');
  await expect(page.locator('input[type="date"]').first()).toHaveValue('2026-10-10');
  await expect(page.locator('input[type="date"]').last()).toHaveValue('2026-10-16');
});

for (const user of [true, false]) {
  test(`${user ? 'user' : 'merchant'} can load older orders without polling dropping history`, async ({ page, adminSession, userSession }) => {
    await asSession(page, user ? userSession : adminSession, user);
    await page.clock.install();
    const records = Array.from({ length: 101 }, (_, i) => ({ id: String(1001 - i), number: 'UX-HISTORY-' + i, storeId: '1', storeName: '分页验收店铺', campusName: '默认校区', version: '0', status: 5, payStatus: 1, fulfillment: 'PICKUP', ready: true, amount: '12.00', paidCents: '1200', refundedCents: '0', details: [], aftersales: [], refunds: [] }));
    let olderReads = 0;
    const listPath = user ? '/user/orders' : '/merchant/stores/1/orders';
    await page.route('**/api/v1' + listPath + '*', route => {
      const before = new URL(route.request().url()).searchParams.get('beforeId');
      if (before) olderReads++;
      return route.fulfill({ json: { code: 'OK', data: before ? records.slice(100) : records.slice(0, 100) } });
    });
    if (user) await page.route('**/api/v1/user/checkouts', route => route.fulfill({ json: { code: 'OK', data: [] } }));
    await page.goto(user ? '/app/pages/orders/index' : '/admin/orders');
    const older = page.getByText(user ? 'UX-HISTORY-100 · 自取' : 'UX-HISTORY-100', { exact: true });
    await expect(older).toHaveCount(0);
    await page.getByText(user ? '加载更早订单与结算单' : '加载更早订单', { exact: true }).click();
    await expect(older).toBeVisible();
    await page.clock.fastForward(16000);
    await expect.poll(() => olderReads).toBeGreaterThan(1);
    await expect(older).toBeVisible();
    await page.getByText(user ? '重新加载' : '刷新', { exact: true }).click();
    await expect(older).toHaveCount(0);
  });
}

test('campus search finds a matching store even before it has any products', async ({ page, request, adminSession }) => {
  const name = '空店验收-' + randomUUID().slice(0, 8);
  await call(request, '/platform/stores', adminSession.token, 'POST', { campusId: '1', name, enabled: true, open: true, deliveryFeeCents: 0, packagingFeeCents: 0, hours: ['00:00-24:00'], phone: '' });
  await page.goto('/app/');
  // Uni-app renders placeholder text beside the native input in H5.
  await page.getByRole('textbox').fill(name);
  await page.getByText('搜本校区', { exact: true }).click();
  const store = page.locator('uni-view.card').filter({ hasText: name, has: page.getByText('进入店铺', { exact: true }) });
  await expect(store).toHaveCount(1);
  await expect(store).toBeVisible();
  await store.getByText('进入店铺', { exact: true }).click();
  await expect(page.getByText(name + ' ▾', { exact: true })).toBeVisible();
  await expect(page.getByText('暂无餐品，请切换店铺或搜索条件。', { exact: true })).toBeVisible();
});
