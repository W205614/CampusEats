import { test, expect } from '@playwright/test';
test.use({ baseURL: process.env.CLIENT_URL || process.env.BASE_URL || 'http://localhost:18083' });

test('cancel appeals use the current order stage even when the order is outside loaded history', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('campus-user', JSON.stringify({
    id: '1', token: 'appeal-boundary', name: '边界用户', role: 'USER',
  })));
  const cases = [
    { id: 'preparing', kind: 'CANCEL', orderStatus: 3, eligible: true },
    { id: 'delivering', kind: 'CANCEL', orderStatus: 4, eligible: false },
    { id: 'completed', kind: 'CANCEL', orderStatus: 5, eligible: false },
    { id: 'normal-refund', kind: 'FULL', orderStatus: 5, eligible: true },
  ];
  const writes: string[] = [];
  await page.route('**/api/v1/user/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1/user', '');
    if (route.request().method() === 'POST') writes.push(path);
    const data = path === '/aftersales' ? cases.map(value => ({
      ...value, orderId: 'unloaded-' + value.id, state: 'REJECTED', version: '1', appealed: false,
      reason: '边界申请', amountCents: '1000', createdAt: '2026-10-26T12:00:00+08:00',
    })) : [];
    return route.fulfill({ json: { code: 'OK', data } });
  });
  await page.goto('/app/pages/orders/index');
  for (const value of cases) {
    const card = page.locator('.card').filter({ has: page.getByText('申请 ' + value.id, { exact: true }) });
    await expect(card).toBeVisible();
    await expect(card.getByText('申请平台介入', { exact: true })).toHaveCount(value.eligible ? 1 : 0);
    if (!value.eligible)
      await expect(card.getByText('订单已不在备餐阶段，无法介入取消；已完成订单可在7天内申请退款。', { exact: true })).toBeVisible();
  }
  expect(writes).toEqual([]);
});
