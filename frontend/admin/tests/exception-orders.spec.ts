import { test, expect, type Page } from '@playwright/test';

// Transport fixtures keep these focused UI checks independent of business data.
async function workspace(page: Page, role = 'PLATFORM_ADMIN') {
  let order = {
    id: '501', number: 'EXCEPTION-501', storeId: '1', version: '5', status: 4,
    fulfillment: 'DELIVERY', ready: true, courierId: '11', amount: '20.00',
    paidCents: '2000', refundedCents: '0', details: [], refunds: [], aftersales: [],
    delivery: { state: 'PICKED' },
  };
  const writes: { path: string; body: Record<string, unknown> }[] = [];
  const state = { failDetails: false, failWrites: false, conflict: false, release: undefined as (() => void) | undefined, hold: false };
  await page.addInitScript((role) => {
    sessionStorage.setItem('campus-admin', JSON.stringify({ token: 'ui-test', id: '1', name: '测试人员', role, mustChangePassword: false }));
    localStorage.setItem('campus-store', '1');
  }, role);
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    const ok = (data: unknown) => route.fulfill({ json: { code: 'OK', data } });
    if (path === '/merchant/stores/page') return ok({ records: [{ id: '1', name: '测试店铺', campusName: '校区' }], nextCursor: null, hasMore: false });
    if (path === '/merchant/stores/1/orders') return ok([order]);
    if (path === '/merchant/stores/1/couriers') return ok([{ id: '11', name: '原配送员' }, { id: '12', name: '接管配送员' }]);
    if (path === '/merchant/orders/501') return state.failDetails ? route.abort() : ok(order);
    if (path.startsWith('/platform/orders/501/')) {
      writes.push({ path, body: route.request().postDataJSON() });
      if (state.hold) await new Promise<void>((resolve) => { state.release = resolve; });
      if (state.failWrites) return route.abort();
      if (state.conflict) {
        order = { ...order, version: '6' };
        state.conflict = false;
        return route.fulfill({ status: 409, json: { code: 'VERSION_CONFLICT', message: '订单状态已变化，请刷新' } });
      }
      order = { ...order, version: String(Number(order.version) + 1), courierId: '12' };
      return ok(order);
    }
    // Disable websocket setup; Orders retains polling.
    return route.fulfill({ status: 503, json: { code: 'TEST_UNAVAILABLE', message: 'test unavailable' } });
  });
  await page.goto('/admin/orders');
  await page.getByRole('button', { name: '处理订单' }).click();
  return { writes, state };
}

test('platform reassign requires reason, replacement and picked handover; busy submit sends once', async ({ page }) => {
  const { writes, state } = await workspace(page);
  await page.getByRole('button', { name: '平台改派', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '平台改派' });
  const submit = dialog.getByRole('button', { name: '确认操作' });
  await expect(submit).toBeDisabled();
  await dialog.getByPlaceholder('必填：操作原因').fill('原配送员临时无法配送');
  await dialog.locator('.el-select').click();
  await expect(page.getByRole('option', { name: '原配送员', exact: true })).toHaveCount(0);
  await page.getByRole('option', { name: '接管配送员', exact: true }).click();
  await expect(submit).toBeDisabled();
  await dialog.getByText('我已安排餐品实际交接，配送计时及超时标记继续保留', { exact: true }).click();
  await expect(dialog.getByRole('checkbox')).toBeChecked();
  state.hold = true;
  await submit.click();
  await expect(submit).toBeDisabled();
  await expect.poll(() => writes.length).toBe(1);
  expect(writes[0]?.body).toEqual({ version: '5', courierId: '12', reason: '原配送员临时无法配送' });
  await expect.poll(() => !!state.release).toBeTruthy();
  state.release?.();
  await expect(dialog).not.toBeVisible();
});

test('merchant never receives platform exception controls', async ({ page }) => {
  await workspace(page, 'SHOP_MANAGER');
  await expect(page.getByRole('button', { name: '平台改派', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '异常取消并退款', exact: true })).toHaveCount(0);
});

test('version conflict refreshes details before the next explicit operation', async ({ page }) => {
  const { writes, state } = await workspace(page);
  state.conflict = true;
  const cancel = page.getByRole('button', { name: '异常取消并退款', exact: true });
  await cancel.click();
  let dialog = page.getByRole('dialog', { name: '异常取消订单' });
  await dialog.getByPlaceholder('必填：操作原因').fill('配送中断');
  await dialog.getByRole('button', { name: '确认操作' }).click();
  await expect(dialog).not.toBeVisible();
  expect(writes).toHaveLength(1);
  await cancel.click();
  dialog = page.getByRole('dialog', { name: '异常取消订单' });
  await dialog.getByPlaceholder('必填：操作原因').fill('重新确认后取消');
  await dialog.getByRole('button', { name: '确认操作' }).click();
  await expect(dialog).not.toBeVisible();
  expect(writes[1]?.body.version).toBe('6');
});

test('unconfirmed write disables more commands until a successful refresh', async ({ page }) => {
  const { writes, state } = await workspace(page);
  state.failWrites = true;
  state.failDetails = true;
  await page.getByRole('button', { name: '异常取消并退款', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '异常取消订单' });
  await dialog.getByPlaceholder('必填：操作原因').fill('取消');
  await dialog.getByRole('button', { name: '确认操作' }).click();
  await expect(dialog).not.toBeVisible();
  await expect(page.getByRole('button', { name: '异常取消并退款', exact: true })).toBeDisabled();
  expect(writes).toHaveLength(1);
  state.failWrites = state.failDetails = false;
  await page.getByRole('button', { name: '刷新订单', exact: true }).click();
  await expect(page.getByRole('button', { name: '异常取消并退款', exact: true })).toBeEnabled();
  expect(writes).toHaveLength(1);
});
