import { test, expect, type Page } from '@playwright/test';

// Focused transport fixtures only. Every API is intercepted, including login/logout;
// these cases neither contact a database nor replace real-role artifact acceptance.
type Role = 'PLATFORM_ADMIN' | 'SHOP_MANAGER' | 'DELIVERER';
const stores = [
  { id: '1', campusId: '11', campusName: '甲校区', name: '甲店', enabled: true },
  { id: '2', campusId: '22', campusName: '乙校区', name: '乙店', enabled: true },
];
async function fixture(page: Page, role: Role = 'PLATFORM_ADMIN', messageStore = '2') {
  const state = {
    readPosts: 0, failRead: false, holdRead: false, marked: false,
    releaseRead: undefined as (() => void) | undefined,
    orderPaths: [] as string[], courierPaths: [] as string[], messageUnreadFilters: [] as (string | null)[],
  };
  await page.addInitScript((role) => {
    sessionStorage.setItem('campus-admin', JSON.stringify({
      id: '90', token: 'inbox-initial', name: '消息交互测试', role, mustChangePassword: false,
    }));
    localStorage.setItem('campus-store', '1');
  }, role);
  const order = {
    id: '402', number: 'MESSAGE-402', storeId: messageStore, campusId: messageStore === '1' ? '11' : '22',
    storeName: messageStore === '1' ? '甲店' : '乙店', version: '3', status: 3,
    fulfillment: 'DELIVERY', ready: true, courierId: role === 'DELIVERER' ? '90' : null,
    amount: '10.00', paidCents: '1000', refundedCents: '0', details: [], refunds: [], aftersales: [],
  };
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    const ok = (data: unknown) => route.fulfill({ json: { code: 'OK', data } });
    if (path === '/merchant/stores/page') return ok({
      records: role === 'SHOP_MANAGER' ? [stores[0]] : stores, nextCursor: null, hasMore: false,
    });
    if (path === '/platform/campuses' || path === '/user/campuses')
      return ok([{ id: '11', name: '甲校区', enabled: true }, { id: '22', name: '乙校区', enabled: true }]);
    if (path === '/me/messages/page') {
      state.messageUnreadFilters.push(new URL(route.request().url()).searchParams.get('unread'));
      return ok({ records: [{
        id: '51', orderId: order.id, storeId: messageStore, summary: '待处理的订单消息',
        eventType: 'STAGE_OVERDUE', createdAt: '2026-10-27T10:00:00', readAt: state.marked ? '2026-10-27T10:01:00' : null,
      }], nextCursor: null, hasMore: false });
    }
    if (path === '/me/messages/unread') return ok({ count: state.marked ? '0' : '1' });
    if (path === '/me/messages/51/read') {
      state.readPosts++;
      if (state.failRead) return route.fulfill({ status: 503,
        json: { code: 'UNAVAILABLE', message: '消息暂时无法标记' } });
      if (state.holdRead && state.readPosts === 1)
        await new Promise<void>(resolve => { state.releaseRead = resolve; });
      state.marked = true;
      return ok(null);
    }
    if (/^\/merchant\/stores\/\d+\/orders$/.test(path) || path === '/courier/orders') {
      state.orderPaths.push(path);
      return ok(path === '/courier/orders' || path === '/merchant/stores/' + messageStore + '/orders' ? [order] : []);
    }
    if (/^\/merchant\/stores\/\d+\/couriers$/.test(path)) {
      state.courierPaths.push(path);
      return ok([{ id: path.includes('/2/') ? '202' : '101', name: path.includes('/2/') ? '乙校区配送员' : '甲校区配送员' }]);
    }
    if (path === '/merchant/orders/402' || path === '/courier/orders/402') return ok(order);
    if (path === '/courier/pool') return ok([]);
    if (path === '/admin/auth/logout') return ok(null);
    if (path === '/admin/auth/login') return ok({
      id: '91', token: 'inbox-next-account', name: '另一测试账号', role, mustChangePassword: false,
    });
    // In particular, websocket setup is rejected locally and cannot open a backend socket.
    return route.fulfill({ status: 503, json: { code: 'OFFLINE_FIXTURE', message: '离线夹具未启用此接口' } });
  });
  await page.goto('/admin/messages');
  await expect(page.getByText('待处理的订单消息', { exact: true })).toBeVisible();
  return state;
}

test('platform message switches the store before loading a cross-campus order and couriers', async ({ page }) => {
  const state = await fixture(page);
  const unread = page.getByRole('checkbox', { name: '只看未读', exact: true });
  await page.getByText('只看未读', { exact: true }).click();
  await expect(unread).toBeChecked();
  await expect.poll(() => state.messageUnreadFilters).toContain('true');
  await page.getByRole('button', { name: '前往订单履约', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/orders\?orderId=402$/);
  const drawer = page.getByRole('dialog', { name: '订单详情与操作', exact: true });
  await expect(drawer.getByText('MESSAGE-402', { exact: true })).toBeVisible();
  await expect(page.locator('.topbar .el-select__placeholder')).toHaveText('乙校区 / 乙店');
  await drawer.locator('.el-select').click();
  await expect(page.getByRole('option', { name: '乙校区配送员', exact: true })).toBeVisible();
  await expect(page.getByRole('option', { name: '甲校区配送员', exact: true })).toHaveCount(0);
  expect(state.orderPaths).toContain('/merchant/stores/2/orders');
  expect(state.courierPaths).toEqual(['/merchant/stores/2/couriers']);
  expect(await page.evaluate(() => localStorage.getItem('campus-store'))).toBe('2');
});

test('merchant message keeps its authorized store and opens the merchant order', async ({ page }) => {
  const state = await fixture(page, 'SHOP_MANAGER', '1');
  await page.getByRole('button', { name: '前往订单履约', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '订单详情与操作', exact: true })).toBeVisible();
  await expect(page.locator('.topbar .el-select__placeholder')).toHaveText('甲校区 / 甲店');
  expect(state.orderPaths).toContain('/merchant/stores/1/orders');
  expect(state.courierPaths).toEqual(['/merchant/stores/1/couriers']);
});

test('courier message opens the assigned order without changing global store selection', async ({ page }) => {
  const state = await fixture(page, 'DELIVERER');
  await page.getByRole('button', { name: '前往订单履约', exact: true }).click();
  const drawer = page.getByRole('dialog', { name: '订单详情与操作', exact: true });
  await expect(drawer.getByText('MESSAGE-402', { exact: true })).toBeVisible();
  await expect(drawer.getByRole('button', { name: '确认取餐', exact: true })).toBeVisible();
  expect(state.orderPaths).toContain('/courier/orders');
  expect(state.courierPaths).toEqual([]);
  expect(await page.evaluate(() => localStorage.getItem('campus-store'))).toBe('1');
});

test('failed mark-read retains message and store and allows one explicit retry', async ({ page }) => {
  const state = await fixture(page); state.failRead = true;
  await page.getByRole('button', { name: '前往订单履约', exact: true }).click();
  await expect(page.getByText('消息暂时无法标记', { exact: true })).toBeVisible();
  await expect(page).toHaveURL(/\/admin\/messages$/);
  expect(await page.evaluate(() => localStorage.getItem('campus-store'))).toBe('1');
  expect(state.readPosts).toBe(1); expect(state.orderPaths).toEqual([]);
  state.failRead = false;
  await page.getByRole('button', { name: '前往订单履约', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '订单详情与操作', exact: true })).toBeVisible();
  expect(state.readPosts).toBe(2);
  expect(state.orderPaths).toContain('/merchant/stores/2/orders');
});

test('a late previous-account mark-read cannot navigate or replace the next account store', async ({ page }) => {
  const state = await fixture(page); state.holdRead = true;
  await page.getByRole('button', { name: '前往订单履约', exact: true }).click();
  await expect.poll(() => !!state.releaseRead).toBe(true);
  await page.getByRole('button', { name: '退出', exact: true }).click();
  await page.getByLabel('账户', { exact: true }).fill('next-ui-account');
  await page.getByLabel('密码', { exact: true }).fill('Offline-Fixture!2026');
  await page.getByRole('button', { name: '登录工作台', exact: true }).click();
  await expect(page.locator('.topbar')).toContainText('另一测试账号 · 平台管理员');
  await page.getByRole('link', { name: '站内消息', exact: true }).click();
  await expect(page.getByText('待处理的订单消息', { exact: true })).toBeVisible();
  const previous = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/me/messages/51/read');
  state.releaseRead?.(); await previous;
  await page.getByRole('button', { name: '标记已读', exact: true }).click();
  await expect(page.getByText('未读 0 条', { exact: true })).toBeVisible();
  await expect(page).toHaveURL(/\/admin\/messages$/);
  expect(await page.evaluate(() => localStorage.getItem('campus-store'))).toBe('1');
  expect(state.orderPaths).toEqual([]);
});

test('an invalid message store never changes selection or navigates to order actions', async ({ page }) => {
  const state = await fixture(page, 'PLATFORM_ADMIN', '0');
  await page.getByRole('button', { name: '前往订单履约', exact: true }).click();
  await expect(page.getByText('消息店铺信息无效，请刷新后重试', { exact: true })).toBeVisible();
  await expect(page).toHaveURL(/\/admin\/messages$/);
  expect(await page.evaluate(() => localStorage.getItem('campus-store'))).toBe('1');
  expect(state.readPosts).toBe(0); expect(state.orderPaths).toEqual([]);
});
