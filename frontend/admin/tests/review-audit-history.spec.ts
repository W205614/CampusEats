import { test, expect, type Page } from '@playwright/test';

// Transport-only coverage for the changed history UI; no real login or business writes.
async function fixture(page: Page, user = false) {
  await page.addInitScript(({ user }) => {
    sessionStorage.setItem(user ? 'campus-user' : 'campus-admin', JSON.stringify({
      id: '90', token: 'review-audit-ui-fixture', name: '历史查询',
      role: user ? 'USER' : 'PLATFORM_ADMIN', mustChangePassword: false,
    }));
    localStorage.setItem('campus-store', '1');
  }, { user });
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url());
    const stores = [1, 2].map(id => ({
      id: String(id), campusId: '1', campusName: '查询校区', name: '查询店铺' + id,
      enabled: true, accepting: true, open: true, hours: ['00:00-24:00'],
      deliveryFee: '0.00', packagingFee: '0.00', phone: '', version: '0',
    }));
    const data = url.pathname.endsWith('/campuses') ? [{ id: '1', name: '查询校区', enabled: true }]
      : url.pathname.endsWith('/stores/page') ? { records: stores, nextCursor: null, hasMore: false }
      : [];
    return route.fulfill({ json: { code: 'OK', data } });
  });
}
const rows = (before: number, limit: number) =>
  Array.from({ length: 105 }, (_, i) => 300 - i).filter(id => id < before).slice(0, limit);

test('merchant reviews load older records and retain loaded results when refresh fails', async ({ page }) => {
  await fixture(page);
  let failed = false;
  const reads: URL[] = [];
  await page.route('**/api/v1/merchant/stores/*/reviews*', route => {
    const url = new URL(route.request().url()); reads.push(url);
    if (failed) return route.fulfill({ status: 503, json: { code: 'UNAVAILABLE', message: '评价读取暂不可用' } });
    const records = rows(Number(url.searchParams.get('beforeId') || Number.MAX_SAFE_INTEGER),
      Number(url.searchParams.get('limit') || 100)).map(id => ({
      id: String(id), orderId: '8', rating: 5, content: '历史评价' + id, hidden: false,
    }));
    return route.fulfill({ json: { code: 'OK', data: { stats: { total: 105, rating: 5 }, records } } });
  });
  await page.goto('/admin/operations/reviews');
  await page.getByRole('button', { name: '加载更早记录', exact: true }).click();
  await expect(page.getByText('历史评价196', { exact: true })).toBeVisible();
  expect(reads.at(-1)!.searchParams.get('limit')).toBe('200');
  failed = true;
  await page.getByRole('button', { name: '刷新', exact: true }).click();
  await expect(page.getByText('评价读取暂不可用', { exact: true })).toBeVisible();
  await expect(page.getByText('历史评价196', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '加载更早记录', exact: true })).toHaveCount(0);
});

test('audit history loads older records and applies actor/action filters before resetting the window', async ({ page }) => {
  await fixture(page);
  const reads: URL[] = [];
  await page.route('**/api/v1/platform/audit*', route => {
    const url = new URL(route.request().url()); reads.push(url);
    const filtered = url.searchParams.get('actorId') === '17' && url.searchParams.get('action') === 'STORE_SAVE';
    const records = filtered ? [{ id: '999', actorId: '17', action: 'STORE_SAVE', detail: '筛选命中', createdAt: '2026-10-26T12:00:00' }]
      : rows(Number(url.searchParams.get('beforeId') || Number.MAX_SAFE_INTEGER),
        Number(url.searchParams.get('limit') || 100)).map(id => ({
        id: String(id), actorId: '11', action: 'STORE_SAVE', detail: '历史操作' + id, createdAt: '2026-10-26T12:00:00',
      }));
    return route.fulfill({ json: { code: 'OK', data: records } });
  });
  await page.goto('/admin/operations/audit');
  await page.getByRole('button', { name: '加载更早记录', exact: true }).click();
  await expect(page.getByText('历史操作196', { exact: true })).toBeVisible();
  await page.getByPlaceholder('完整操作人编号', { exact: true }).fill('17');
  await page.getByPlaceholder('例如 STORE_SAVE', { exact: true }).fill('STORE_SAVE');
  await page.getByRole('button', { name: '筛选', exact: true }).click();
  await expect(page.getByText('筛选命中', { exact: true })).toBeVisible();
  expect(reads.at(-1)!.searchParams.get('actorId')).toBe('17');
  expect(reads.at(-1)!.searchParams.get('action')).toBe('STORE_SAVE');
  expect(reads.at(-1)!.searchParams.get('limit')).toBe('100');
  expect(reads.at(-1)!.searchParams.has('beforeId')).toBe(false);
  await page.getByRole('button', { name: '清空筛选', exact: true }).click();
  await expect(page.getByText('历史操作300', { exact: true })).toBeVisible();
  expect(reads.at(-1)!.searchParams.has('actorId')).toBe(false);
  expect(reads.at(-1)!.searchParams.has('action')).toBe(false);
});

test('customer review dialog pages by cursor without requiring private review fields', async ({ page }) => {
  await fixture(page, true);
  const reads: URL[] = [];
  await page.route('**/api/v1/user/stores/*/reviews*', route => {
    const url = new URL(route.request().url()); reads.push(url);
    const records = rows(Number(url.searchParams.get('beforeId') || Number.MAX_SAFE_INTEGER),
      Number(url.searchParams.get('limit') || 100)).map(id => ({
      id: String(id), rating: 5, content: '公开评价' + id, reply: null, createdAt: '2026-10-26T12:00:00',
    }));
    return route.fulfill({ json: { code: 'OK', data: { stats: { total: 105, rating: 5 }, records } } });
  });
  await page.goto('/app/pages/menu/index');
  await page.getByText('店铺评价', { exact: true }).click();
  await page.getByText('加载更早评价', { exact: true }).click();
  await expect(page.getByText('公开评价196', { exact: true })).toBeVisible();
  expect(reads.at(-1)!.searchParams.get('beforeId')).toBe('201');
  await expect(page.getByText('加载更早评价', { exact: true })).toHaveCount(0);
});
