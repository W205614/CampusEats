import { test, expect } from '@playwright/test';

// Every API call is intercepted; this exercises real components without business writes.
test('report ignores old responses and exports the last applied filters', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('campus-admin', JSON.stringify({ id: '1', token: 'report-ui', name: '店长', role: 'SHOP_MANAGER', storeId: '1', mustChangePassword: false }));
    localStorage.setItem('campus-store', '1');
  });
  let release: (() => void) | undefined;
  const delayed = new Promise<void>(resolve => { release = resolve; });
  const exports: string[] = [], errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const ok = (data: unknown) => route.fulfill({ json: { code: 'OK', data } });
    if (url.pathname.endsWith('/stores/page')) return ok({ records: [{ id: '1', name: '验收店铺', campusId: '1' }], hasMore: false, nextCursor: null });
    if (url.pathname.endsWith('/reports/export')) {
      exports.push(url.search);
      return route.fulfill({ contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', body: 'fixture' });
    }
    if (url.pathname.endsWith('/reports')) {
      const begin = url.searchParams.get('begin');
      if (begin === '2026-10-01') await delayed;
      return ok({ metrics: {}, daily: [{ date: begin, paidCents: '100', paymentCount: 1 }], stores: [] });
    }
    return ok([]);
  });
  await page.goto('/admin/reports');
  await expect(page.getByTestId('report-applied')).toBeVisible();
  const dates = page.locator('input[type=date]');
  await dates.nth(0).fill('2026-10-01');
  await dates.nth(1).fill('2026-10-20');
  const oldRequest = page.waitForRequest(r => r.url().includes('/reports?') && r.url().includes('begin=2026-10-01'));
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await oldRequest;
  await dates.nth(0).fill('2026-10-02');
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByTestId('report-applied')).toContainText('2026-10-02');
  const oldResponse = page.waitForResponse(r => r.url().includes('/reports?') && r.url().includes('begin=2026-10-01'));
  release!();
  await oldResponse;
  await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
  await expect(page.locator('.el-table').first()).toContainText('2026-10-02');
  await dates.nth(0).fill('2026-10-03');
  await page.getByRole('button', { name: '导出 Excel' }).click();
  await expect.poll(() => exports.length).toBe(1);
  expect(exports[0]).toContain('begin=2026-10-02');
  expect(errors).toEqual([]);
});
