import { test, expect, type Page } from '@playwright/test';
import { readPages } from '../../contracts/paging';

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
  await page.route('**/api/v1/merchant/stores/page?*', route => route.fulfill({ json: { code: 'OK', data: { records: stores, nextCursor: null, hasMore: false } } }));
}
const aftersale = (id: number) => ({ id: String(id), orderId: '8', storeId: '1', kind: 'FULL', state: 'PENDING', reason: '历史申请' + id, decision: '', amountCents: '100', createdAt: '2026-10-20T10:00:00', items: [], evidence: [], version: '0' });
for (const kind of ['aftersales', 'refunds', 'outbox']) {
  test(`history UI ${kind}: filters reset cursor, refresh keeps older pages, store switch resets`, async ({ page }) => {
    await fixture(page);
    const reads: URL[] = [];
    const path = '/api/v1/platform/' + (kind === 'outbox' ? 'events' : kind);
    await page.route('**' + path + '*', route => {
      const url = new URL(route.request().url()); reads.push(url);
      const before = Number(url.searchParams.get('beforeId') || Number.MAX_SAFE_INTEGER);
      const limit = Number(url.searchParams.get('limit') || 100);
      const ids = Array.from({ length: 101 }, (_, i) => 300 - i).filter(id => id < before).slice(0, limit);
      const rows = ids.map(id => kind === 'aftersales' ? aftersale(id) : {
        id: String(id), state: 'FAILED', attempts: 1, lastError: '历史任务' + id,
        ...(kind === 'outbox' ? { aggregateId: '8', eventType: 'ORDER_PAID' } : { orderId: '8', amountCents: '1200' }),
      });
      return route.fulfill({ json: { code: 'OK', data: rows } });
    });
    await page.goto('/admin/operations/' + kind);
    await page.getByRole('button', { name: '加载更早记录', exact: true }).click();
    await expect(page.getByText(kind === 'aftersales' ? '历史申请200' : '历史任务200', { exact: true })).toBeVisible();
    expect(reads.at(-1)!.searchParams.get('limit')).toBe('200');
    expect(reads.at(-1)!.searchParams.has('beforeId')).toBe(false);
    const start = reads.length;
    await page.getByRole('button', { name: '刷新', exact: true }).click();
    await expect.poll(() => reads.length).toBeGreaterThanOrEqual(start + 1);
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
    await expect.poll(() => reads.at(-1)!.search).toBe('?limit=100');
  });
}

test('customer aftersale history refreshes loaded pages and filters independently from orders', async ({ page }) => {
  await fixture(page, 'USER');
  const reads: URL[] = [];
  await page.route('**/api/v1/user/aftersales*', route => {
    const url = new URL(route.request().url()); reads.push(url);
    const before = Number(url.searchParams.get('beforeId') || Number.MAX_SAFE_INTEGER);
      const limit = Number(url.searchParams.get('limit') || 100);
      const ids = Array.from({ length: 101 }, (_, i) => 300 - i).filter(id => id < before).slice(0, limit);
    return route.fulfill({ json: { code: 'OK', data: ids.map(aftersale) } });
  });
  await page.goto('/app/pages/orders/index');
  await page.getByText('加载更早售后', { exact: true }).click();
  await expect(page.getByText('申请 200', { exact: true })).toBeVisible();
  const start = reads.length;
  await page.getByText('重新加载', { exact: true }).click();
  await expect.poll(() => reads.length).toBeGreaterThanOrEqual(start + 1);
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
  await expect.poll(() => reads.at(-1)!.search).toBe('?limit=100');
});

// Request-count assertions exercise the actual shared reader, independently of DOM size.
test('history reader refreshes ten pages in one request and eleven in two bounded requests', async () => {
  const records = Array.from({ length: 1200 }, (_, i) => ({ id: String(2000 - i) }));
  const reads: { before?: string; limit: number }[] = [];
  const fetch = async (before: string | undefined, limit: number) => {
    reads.push({ before, limit });
    return records.filter(row => !before || Number(row.id) < Number(before)).slice(0, limit);
  };
  const ten = await readPages(fetch, 10);
  expect(reads).toEqual([{ before: undefined, limit: 1000 }]);
  expect(ten.items).toEqual(records.slice(0, 1000));
  expect(ten.hasMore).toBe(true);
  reads.length = 0;
  const eleven = await readPages(fetch, 11);
  expect(reads).toEqual([{ before: undefined, limit: 1000 }, { before: '1001', limit: 100 }]);
  expect(eleven.items).toEqual(records.slice(0, 1100));
  expect(eleven.hasMore).toBe(true);
  let current = true;
  reads.length = 0;
  await readPages(async (before, limit) => { current = false; return fetch(before, limit); }, 11, () => current);
  expect(reads).toHaveLength(1);
});

test('history polling updates older states, removes filtered rows, inserts new rows and retains data on failure', async ({ page }) => {
  await fixture(page);
  await page.clock.install();
  let records = Array.from({ length: 101 }, (_, i) => aftersale(300 - i));
  let failed = false;
  let reads = 0;
  await page.route('**/api/v1/platform/aftersales*', route => {
    reads++;
    if (failed) return route.fulfill({ status: 503, json: { code: 'UNAVAILABLE', message: 'fixture unavailable' } });
    const url = new URL(route.request().url());
    const state = url.searchParams.get('state');
    const before = Number(url.searchParams.get('beforeId') || Number.MAX_SAFE_INTEGER);
    const result = records.filter(row => Number(row.id) < before && (!state || row.state === state))
      .slice(0, Number(url.searchParams.get('limit') || 100));
    return route.fulfill({ json: { code: 'OK', data: result } });
  });
  await page.goto('/admin/operations/aftersales');
  await page.getByRole('button', { name: '加载更早记录', exact: true }).click();
  await expect(page.getByText('历史申请200', { exact: true })).toBeVisible();
  records = records.map(row => row.id === '200' ? { ...row, state: 'REFUNDED' } : row);
  records.unshift(aftersale(301));
  await page.clock.fastForward(16000);
  await expect(page.locator('article').filter({ has: page.getByText('历史申请200', { exact: true }) }).getByText('已退款', { exact: true })).toBeVisible();
  await expect(page.getByText('历史申请301', { exact: true })).toBeVisible();
  failed = true;
  const start = reads;
  await page.clock.fastForward(16000);
  await expect.poll(() => reads).toBeGreaterThan(start);
  await expect(page.getByText('历史申请200', { exact: true })).toBeVisible();
  failed = false;
  const filters = page.getByRole('region', { name: '历史记录筛选' });
  await filters.locator('.el-select').click();
  await page.getByRole('option', { name: '待处理', exact: true }).click();
  await filters.getByRole('button', { name: '筛选', exact: true }).click();
  await page.getByRole('button', { name: '加载更早记录', exact: true }).click();
  await expect(page.getByText('历史申请201', { exact: true })).toBeVisible();
  records = records.map(row => row.id === '201' ? { ...row, state: 'REFUNDED' } : row);
  await page.clock.fastForward(16000);
  await expect(page.getByText('历史申请201', { exact: true })).toHaveCount(0);
});

test('merchant history discards a slow response from the previous store', async ({ page }) => {
  await fixture(page, 'SHOP_MANAGER');
  let release: (() => void) | undefined;
  let slowStarted = false;
  let slow = false;
  await page.route('**/api/v1/merchant/stores/*/aftersales*', async route => {
    const store = new URL(route.request().url()).pathname.includes('/stores/2/');
    if (!store && slow) {
      slowStarted = true;
      await new Promise<void>(resolve => { release = resolve; });
    }
    await route.fulfill({ json: { code: 'OK', data: [aftersale(store ? 222 : 111)] } });
  });
  await page.goto('/admin/operations/aftersales');
  await expect(page.getByText('历史申请111', { exact: true })).toBeVisible();
  slow = true;
  await page.getByRole('button', { name: '刷新', exact: true }).click();
  await expect.poll(() => slowStarted).toBe(true);
  await page.locator('.topbar .el-select').click();
  await page.getByRole('option', { name: '验收校区 / 列表店铺2', exact: true }).click();
  await expect(page.getByText('历史申请222', { exact: true })).toBeVisible();
  release!();
  await expect(page.getByText('历史申请111', { exact: true })).toHaveCount(0);
  await expect(page.getByText('历史申请222', { exact: true })).toBeVisible();
});
