import { randomUUID } from 'node:crypto';
import fs from 'node:fs/promises';
import { test, expect, call, asSession } from './helpers';
import type { APIRequestContext, Browser, Page, TestInfo } from '@playwright/test';
import type { Session } from '../../contracts/models';
import type { Cart, Checkout, Order, Quote } from '../../contracts/platform';

test.beforeAll(async () => {
  if (process.env.CI === 'true') {
    // A full run has just exercised employee login in the preceding journey.
    // Respect the real 20/IP/minute limit without weakening the application.
    test.setTimeout(90000);
    await new Promise(resolve => setTimeout(resolve, 60000));
  }
});

// The fixtures create a ready paid delivery so each test can target a failure transition.
// The complete cart/payment/merchant journey is covered by fulfillment-journey.spec.ts.
async function deliveryFixture(request: APIRequestContext, admin: Session, user: Session) {
  const suffix = randomUUID().slice(0, 8), a = admin.token, u = user.token;
  const campus = await call<{ id: string }>(request, '/platform/campuses', a, 'POST', {
    name: '异常验收校区-' + suffix, enabled: true,
  });
  const storeName = '异常验收店-' + suffix;
  const createdStore = await call<{ id: string }>(request, '/platform/stores', a, 'POST', {
    campusId: campus.id, name: storeName, enabled: true, open: true,
    deliveryFeeCents: 600, packagingFeeCents: 100, hours: ['00:00-24:00'], phone: '',
  });
  const store = { ...createdStore, name: storeName };
  const building = await call<{ id: string }>(request, '/platform/campuses/' + campus.id + '/buildings', a, 'POST', {
    name: '验收楼-' + suffix, enabled: true,
  });
  const category = await call<{ id: string }>(request, '/merchant/stores/' + store.id + '/categories', a, 'POST', {
    name: '验收餐', type: 1, sort: 0, enabled: true,
  });
  const product = await call<{ id: string }>(request, '/merchant/stores/' + store.id + '/products/DISH', a, 'POST', {
    name: '异常验收餐-' + suffix, categoryId: category.id, price: '20.00', enabled: true,
    description: '定向验收', image: '/demo-images/dish.svg', flavors: [], components: [],
  });
  const date = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10);
  await call(request, '/merchant/stores/' + store.id + '/quotas?date=' + date, a, 'PUT', {
    dishId: product.id, total: 20, defaultQuota: true,
  });
  const address = await call<{ id: string }>(request, '/user/addresses', u, 'POST', {
    buildingId: building.id, room: 'UAT-401', consignee: '异常验收用户', phone: '13800000000',
  });
  let cart = await call<Cart>(request, '/user/stores/' + store.id + '/cart', u);
  cart = await call<Cart>(request, '/user/stores/' + store.id + '/cart/items', u, 'POST', {
    cartVersion: cart.cartVersion, itemType: 'DISH', itemId: product.id, flavors: {}, delta: 1,
  });
  const input = { stores: [{ storeId: store.id, cartVersion: cart.cartVersion,
    fulfillment: 'DELIVERY', addressId: address.id, couponId: null }] };
  const quote = await call<Quote>(request, '/user/checkouts/preview', u, 'POST', input);
  const checkout = await call<Checkout>(request, '/user/checkouts', u, 'POST', {
    ...input, quoteHash: quote.quoteHash,
  }, { 'Idempotency-Key': randomUUID() });
  const paid = await call<Checkout>(request, '/user/checkouts/' + checkout.id + '/pay', u, 'POST', { version: checkout.version });
  let order = paid.orders[0]!;
  for (const action of ['accept', 'ready', 'publish']) {
    await call(request, '/merchant/orders/' + order.id + '/actions/' + action, a, 'POST', { version: order.version });
    order = await call<Order>(request, '/user/orders/' + order.id, u);
  }
  async function courier(index: number) {
    const username = 'uat_' + index + '_' + suffix, secret = 'Uat1!' + randomUUID();
    const employee = await call<{ id: string }>(request, '/platform/staff', a, 'POST', {
      username, name: '异常验收配送' + index, role: 'DELIVERER', campusId: campus.id, enabled: true, password: secret,
    });
    const first = await call<Session>(request, '/admin/auth/login', '', 'POST', { username, password: secret });
    const changed = 'Uat2!' + randomUUID();
    await call(request, '/admin/auth/password', first.token, 'POST', { oldPassword: secret, password: changed });
    return { employee, session: await call<Session>(request, '/admin/auth/login', '', 'POST', { username, password: changed }) };
  }
  return { store, order, courier, getOrder: () => call<Order>(request, '/user/orders/' + order.id, u) };
}

async function pageFor(browser: Browser, info: TestInfo, session: Session, user = false, storeId?: string) {
  const context = await browser.newContext({ baseURL: info.project.use.baseURL, timezoneId: 'Asia/Shanghai' });
  const page = await context.newPage();
  await asSession(page, session, user);
  if (storeId) await page.addInitScript(id => localStorage.setItem('campus-store', id), storeId);
  await page.goto(user ? '/app/pages/orders/index' : '/admin/orders');
  return page;
}
async function postUI(page: Page, endpoint: string, action: () => Promise<unknown>, status = 200) {
  const received = page.waitForResponse(r => new URL(r.url()).pathname === '/api/v1' + endpoint && r.request().method() === 'POST');
  await action();
  const response = await received;
  expect(response.status()).toBe(status);
  return (await response.json()).data;
}
const card = (page: Page, number: string) => page.locator('uni-view.card').filter({ has: page.getByText(number + ' · 配送', { exact: true }) });
async function cancelOnPage(customer: Page, order: Order) {
  await card(customer, order.number).getByText('申请取消', { exact: true }).click();
  const modal = customer.locator('uni-view.modal');
  await modal.locator('textarea').fill('验收：备餐后需取消配送');
  return postUI(customer, '/user/orders/' + order.id + '/aftersales', () => modal.getByText('确认提交', { exact: true }).click()) as Promise<{ id: string }>;
}

test('pending cancellation blocks a stale courier claim, withdrawal restores the pool, platform approval refunds once', async ({ browser, request, adminSession, userSession }, info) => {
  test.setTimeout(120000);
  expect(process.env.CI === 'true' || new URL(String(info.project.use.baseURL)).port !== '18083').toBe(true);
  const fixture = await deliveryFixture(request, adminSession, userSession);
  const courier = await fixture.courier(1), pages: Page[] = [];
  try {
    const customer = await pageFor(browser, info, userSession, true); pages.push(customer);
    const rider = await pageFor(browser, info, courier.session); pages.push(rider);
    const pool = await call<{ id: string; orderId: string; version: string }[]>(request, '/courier/pool', courier.session.token);
    const task = pool.find(t => t.orderId === fixture.order.id)!;
    const row = rider.locator('.el-table__body tr').filter({ hasText: fixture.store.name });
    await expect(row.getByRole('button', { name: '抢单', exact: true })).toBeVisible();
    const pending = await cancelOnPage(customer, fixture.order);
    // Keep the already rendered task stale, as when cancellation races with a click.
    await postUI(rider, '/courier/tasks/' + task.id + '/claim', () => row.getByRole('button', { name: '抢单', exact: true }).click(), 409);
    await expect(rider.getByText('请先处理用户的取消申请', { exact: true })).toBeVisible();
    await expect(row).toHaveCount(0);
    expect((await fixture.getOrder()).courierId).toBeNull();
    const progress = (id: string) => customer.locator('uni-view.card').filter({ has: customer.getByText('申请 ' + id, { exact: true }) });
    await postUI(customer, '/user/aftersales/' + pending.id + '/withdraw', () => progress(pending.id).getByText('撤销申请', { exact: true }).click());
    await rider.getByRole('button', { name: '刷新', exact: true }).click();
    await expect(row.getByRole('button', { name: '抢单', exact: true })).toBeVisible();
    const appealed = await cancelOnPage(customer, fixture.order);
    await expect(progress(appealed.id).getByText('申请平台介入', { exact: true })).toHaveCount(0);
    const platform = await pageFor(browser, info, adminSession, false, fixture.store.id); pages.push(platform);
    await platform.getByRole('link', { name: '售后处理', exact: true }).click();
    const application = platform.locator('article').filter({ hasText: '申请 ' + appealed.id + ' ·' });
    await platform.getByPlaceholder('本次处理说明', { exact: true }).fill('验收：商家拒绝后允许用户申诉');
    await postUI(platform, '/merchant/aftersales/' + appealed.id + '/decision', () => application.getByRole('button', { name: '拒绝并说明', exact: true }).click());
    await customer.getByText('重新加载', { exact: true }).click();
    await postUI(customer, '/user/aftersales/' + appealed.id + '/appeal', () => progress(appealed.id).getByText('申请平台介入', { exact: true }).click());
    await rider.getByRole('button', { name: '刷新', exact: true }).click();
    await expect(row).toHaveCount(0);
    await platform.getByRole('button', { name: '刷新', exact: true }).click();
    await platform.getByPlaceholder('本次处理说明', { exact: true }).fill('验收：取消属实，同意模拟退款');
    await postUI(platform, '/platform/aftersales/' + appealed.id + '/decision', () => application.getByRole('button', { name: '同意退款 / 取消', exact: true }).click());
    await expect.poll(async () => (await fixture.getOrder()).refundedCents, { timeout: 20000 }).toBe(fixture.order.paidCents);
    const final = await fixture.getOrder();
    expect(final.status).toBe(6);
    expect(final.refunds.filter(r => r.state === 'SUCCEEDED')).toHaveLength(1);
    await customer.getByText('重新加载', { exact: true }).click();
    await expect(card(customer, final.number).getByText('已取消', { exact: true })).toBeVisible();
    await customer.screenshot({ path: info.outputPath('cancelled-and-refunded.png'), fullPage: true });
    await fs.writeFile(info.outputPath('cancellation-evidence.json'), JSON.stringify({ orderId: final.id, taskId: task.id, refundedCents: final.refundedCents, staleClaimStatus: 409, pendingAndEscalatedHidden: true, withdrawnRestores: true }));
  } finally { for (const page of pages) await page.context().close(); }
});

test('platform hands over picked delivery and cancels only that order with a simulated refund', async ({ browser, request, adminSession, userSession }, info) => {
  test.setTimeout(120000);
  expect(process.env.CI === 'true' || new URL(String(info.project.use.baseURL)).port !== '18083').toBe(true);
  const fixture = await deliveryFixture(request, adminSession, userSession);
  const first = await fixture.courier(1), second = await fixture.courier(2), pages: Page[] = [];
  try {
    const rider = await pageFor(browser, info, first.session); pages.push(rider);
    const pool = await call<{ id: string; orderId: string }[]>(request, '/courier/pool', first.session.token);
    const task = pool.find(t => t.orderId === fixture.order.id)!;
    await postUI(rider, '/courier/tasks/' + task.id + '/claim', () => rider.locator('.el-table__body tr').filter({ hasText: fixture.store.name }).getByRole('button', { name: '抢单', exact: true }).click());
    await rider.locator('.el-table__body tr').filter({ hasText: fixture.order.number }).getByRole('button', { name: '处理订单', exact: true }).click();
    await postUI(rider, '/courier/orders/' + fixture.order.id + '/actions/pickup', () => rider.getByRole('button', { name: '确认取餐', exact: true }).click());
    const platform = await pageFor(browser, info, adminSession, false, fixture.store.id); pages.push(platform);
    await platform.locator('.el-table__body tr').filter({ hasText: fixture.order.number }).getByRole('button', { name: '处理订单', exact: true }).click();
    await platform.getByRole('button', { name: '平台改派', exact: true }).click();
    const dialog = platform.getByRole('dialog', { name: '平台改派' });
    await dialog.getByPlaceholder('必填：操作原因').fill('验收：原配送员中途无法继续，已交接餐品');
    await dialog.locator('.el-select').click();
    await platform.getByRole('option', { name: '异常验收配送2', exact: true }).click();
    await expect(dialog.getByRole('button', { name: '确认操作' })).toBeDisabled();
    await dialog.getByText('我已安排餐品实际交接，配送计时及超时标记继续保留', { exact: true }).click();
    await expect(dialog.getByRole('checkbox')).toBeChecked();
    await postUI(platform, '/platform/orders/' + fixture.order.id + '/reassign', () => dialog.getByRole('button', { name: '确认操作' }).click());
    expect(await fixture.getOrder()).toMatchObject({ status: 4, courierId: second.employee.id, delivery: { state: 'PICKED' } });
    const denied = await request.post('/api/v1/courier/orders/' + fixture.order.id + '/actions/complete', { headers: { Authorization: 'Bearer ' + first.session.token }, data: { version: (await fixture.getOrder()).version } });
    expect(denied.status()).toBe(404);
    const replacement = await pageFor(browser, info, second.session); pages.push(replacement);
    await replacement.locator('.el-table__body tr').filter({ hasText: fixture.order.number }).getByRole('button', { name: '处理订单', exact: true }).click();
    await expect(replacement.getByRole('button', { name: '确认送达', exact: true })).toBeVisible();
    await expect(replacement.getByRole('button', { name: '确认取餐', exact: true })).toHaveCount(0);
    await platform.getByRole('button', { name: '异常取消并退款', exact: true }).click();
    const cancellation = platform.getByRole('dialog', { name: '异常取消订单' });
    await cancellation.getByPlaceholder('必填：操作原因').fill('验收：餐品损坏，无法继续履约');
    await postUI(platform, '/platform/orders/' + fixture.order.id + '/exception-cancel', () => cancellation.getByRole('button', { name: '确认操作' }).click());
    await expect.poll(async () => (await fixture.getOrder()).refundedCents, { timeout: 20000 }).toBe(fixture.order.paidCents);
    const customer = await pageFor(browser, info, userSession, true); pages.push(customer);
    await expect(card(customer, fixture.order.number).getByText('已取消', { exact: true })).toBeVisible();
    await customer.screenshot({ path: info.outputPath('handover-cancel-refunded.png'), fullPage: true });
    await fs.writeFile(info.outputPath('handover-evidence.json'), JSON.stringify({ orderId: fixture.order.id, retainedPicked: true, formerCourierRejected: 404, refundedCents: (await fixture.getOrder()).refundedCents }));
  } finally { for (const page of pages) await page.context().close(); }
});
