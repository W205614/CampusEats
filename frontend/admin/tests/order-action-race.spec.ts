import { test, expect } from '@playwright/test';

test('an order action racing a background read sends once and retains its refreshed version', async ({ page }) => {
  await page.clock.install();
  await page.addInitScript(() => {
    sessionStorage.setItem('campus-admin', JSON.stringify({ token: 'order-race-ui', id: '1', name: '店长',
      role: 'SHOP_MANAGER', storeId: '1', mustChangePassword: false }));
    localStorage.setItem('campus-store', '1');
  });
  let order = { id: '501', number: 'ORDER-RACE-501', storeId: '1', version: '5', status: 3,
    fulfillment: 'DELIVERY', ready: true, courierId: null, paidCents: '1000', refundedCents: '0',
    details: [], refunds: [], aftersales: [] };
  let holdRead = false, releaseRead: (() => void) | undefined, releaseWrite: (() => void) | undefined;
  const writes: unknown[] = [];
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    const ok = (data: unknown) => route.fulfill({ json: { code: 'OK', data } });
    if (path === '/merchant/stores/page') return ok({ records: [{ id: '1', name: '测试店铺' }], hasMore: false, nextCursor: null });
    if (path === '/merchant/stores/1/orders') {
      const snapshot = { ...order };
      if (holdRead) await new Promise<void>(resolve => { releaseRead = resolve; });
      return ok([snapshot]);
    }
    if (path === '/merchant/stores/1/couriers') return ok([]);
    if (path === '/merchant/orders/501') return ok(order);
    if (path === '/merchant/orders/501/actions/publish') {
      writes.push(route.request().postDataJSON());
      await new Promise<void>(resolve => { releaseWrite = resolve; });
      order = { ...order, version: '6' };
      return ok(order);
    }
    return route.fulfill({ status: 503, json: { code: 'TEST_UNAVAILABLE', message: 'test unavailable' } });
  });
  await page.goto('/admin/orders?orderId=501');
  const publish = page.getByRole('button', { name: '发布抢单任务', exact: true });
  await expect(publish).toBeEnabled();
  holdRead = true;
  // Start a read and dispatch the already accepted click before Vue patches disabled.
  await page.evaluate(() => {
    const buttons = Array.from(document.querySelectorAll('button'));
    buttons.find(button => button.textContent?.trim() === '刷新')!.click();
    buttons.find(button => button.textContent?.trim() === '发布抢单任务')!.click();
  });
  await expect.poll(() => writes.length).toBe(1);
  await expect(publish).toBeDisabled();
  await publish.dispatchEvent('click');
  expect(writes).toEqual([{ version: '5', courierId: null, code: null }]);
  await expect.poll(() => !!releaseWrite && !!releaseRead).toBe(true);
  releaseWrite!();
  await expect(publish).toBeEnabled();
  const staleResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/merchant/stores/1/orders');
  releaseRead!();
  await staleResponse;
  await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
  await publish.click();
  await expect.poll(() => writes.length).toBe(2);
  expect(writes[1]).toEqual({ version: '6', courierId: null, code: null });
  releaseWrite!();
  await expect(publish).toBeEnabled();
});
