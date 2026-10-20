import { test, expect, type Page } from '@playwright/test';

// Browser checks for the changed list UI with transport fixtures. These are not
// role acceptance or database pagination evidence; the real four-role journey
// remains in fulfillment-journey.spec.ts.
const session = (role: string) => ({ id: '90', token: 'history-ui-fixture', name: '历史列表验收', role, mustChangePassword: false });
const stores = [1, 2].map(id => ({ id: String(id), campusId: '1', campusName: '验收校区', name: '列表店铺' + id }));
async function fixture(page: Page, role = 'PLATFORM_ADMIN') {
  await page.addInitScript(value => {
    sessionStorage.setItem(value.role === 'USER' ? 'campus-user' : 'campus-admin', JSON.stringify(value));
    localStorage.setItem('campus-store', '1');
  }, session(role));
  await page.route('**/api/v1/**', route => route.fulfill({ json: { code: 'OK', data: [] } }));
  await page.route('**/api/v1/merchant/stores', route => route.fulfill({ json: { code: 'OK', data: stores } }));
}
const aftersale = (id: number) => ({ id: String(id), orderId: '8', storeId: '1', kind: 'FULL', state: 'PENDING', reason: '历史申请' + id, decision: '', amountCents: '100', createdAt: '2026-10-20T10:00:00', items: [], evidence: [], version: '0' });
for (const kind of ['aftersales', 'refunds', 'outbox']) {
  test(`history UI ${kind}: filters reset cursor, refresh keeps older pages, store switch resets`, async ({ page }) => {
    await fixture(page);
    const reads: URL[] = [];
    const path = '/api/v1/platform/' + (kind === 'outbox' ? 'events' : kind);
    await page.route('**' + path + '*', route => {
      const url = new URL(route.request().url()); reads.push(url);
      const ids = url.searchParams.has('beforeId') ? [200] : Array.from({ length: 100 }, (_, i) => 300 - i);
      const rows = ids.map(id => kind === 'aftersales' ? aftersale(id) : {
        id: String(id), state: 'FAILED', attempts: 1, lastError: '历史任务' + id,
        ...(kind === 'outbox' ? { aggregateId: '8', eventType: 'ORDER_PAID' } : { orderId: '8', amountCents: '1200' }),
      });
      return route.fulfill({ json: { code: 'OK', data: rows } });
    });
    await page.goto('/admin/operations/' + kind);
    await page.getByRole('button', { name: '加载更早记录', exact: true }).click();
    await expect(page.getByText(kind === 'aftersales' ? '历史申请200' : '历史任务200', { exact: true })).toBeVisible();
    expect(reads.at(-1)!.searchParams.get('beforeId')).toBe('201');
    const start = reads.length;
    await page.getByRole('button', { name: '刷新', exact: true }).click();
    await expect.poll(() => reads.length).toBeGreaterThanOrEqual(start + 2);
    await expect(page.getByText(kind === 'aftersales' ? '历史申请200' : '历史任务200', { exact: true })).toBeVisible();
    const filters = page.getByRole('region', { name: '历史记录筛选' });
    await filters.locator('.el-select').click();
    await page.getByRole('option', { name: kind === 'aftersales' ? '待处理' : '失败', exact: true }).click();
    await filters.getByPlaceholder('完整订单号', { exact: true }).fill('ORDER-8');
    if (kind !== 'aftersales') await filters.getByPlaceholder('完整任务编号', { exact: true }).fill('200');
    await filters.getByRole('button', { name: '筛选', exact: true }).click();
    await expect.poll(() => reads.at(-1)!.searchParams.get('orderNumber')).toBe('ORDER-8');
    expect(reads.at(-1)!.searchParams.has('beforeId')).toBe(false);
    expect(reads.at(-1)!.searchParams.get('state')).toBe(kind === 'aftersales' ? 'PENDING' : 'FAILED');
    if (kind !== 'aftersales') expect(reads.at(-1)!.searchParams.get('taskId')).toBe('200');
    await page.locator('.topbar .el-select').click();
    await page.getByRole('option', { name: '验收校区 / 列表店铺2', exact: true }).click();
    await expect(filters.getByPlaceholder('完整订单号', { exact: true })).toHaveValue('');
    await expect.poll(() => reads.at(-1)!.search).toBe('');
  });
}

test('customer aftersale history refreshes loaded pages and filters independently from orders', async ({ page }) => {
  await fixture(page, 'USER');
  const reads: URL[] = [];
  await page.route('**/api/v1/user/aftersales*', route => {
    const url = new URL(route.request().url()); reads.push(url);
    const ids = url.searchParams.has('beforeId') ? [200] : Array.from({ length: 100 }, (_, i) => 300 - i);
    return route.fulfill({ json: { code: 'OK', data: ids.map(aftersale) } });
  });
  await page.goto('/app/pages/orders/index');
  await page.getByText('加载更早售后', { exact: true }).click();
  await expect(page.getByText('申请 200', { exact: true })).toBeVisible();
  const start = reads.length;
  await page.getByText('重新加载', { exact: true }).click();
  await expect.poll(() => reads.length).toBeGreaterThanOrEqual(start + 2);
  await expect(page.getByText('申请 200', { exact: true })).toBeVisible();
  // uni-app H5 renders placeholder text outside the native input.
  await page.locator('uni-view.card').filter({
    has: page.getByText('筛选售后', { exact: true }),
  }).locator('input[type="text"]').fill('ORDER-8');
  await page.getByText('筛选售后', { exact: true }).click();
  await expect.poll(() => reads.at(-1)!.searchParams.get('orderNumber')).toBe('ORDER-8');
  expect(reads.at(-1)!.searchParams.has('beforeId')).toBe(false);
  await expect(page.getByText('申请 200', { exact: true })).toHaveCount(0);
  await page.getByText('清空售后筛选', { exact: true }).click();
  await expect.poll(() => reads.at(-1)!.search).toBe('');
});
