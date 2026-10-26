import { test, expect, type Page } from '@playwright/test';
import { mergeRecords } from '../../contracts/platform';

// These focused UI checks exercise real page code with transport fixtures.
// Database cursor correctness and four-role business acceptance are separate checks.
const shop = (id: string, name = '分页店铺' + id) => ({ id, campusId: '1', campusName: '分页校区', name,
  enabled: true, open: true, accepting: true, hours: ['00:00-24:00'], deliveryFee: '0.00', packagingFee: '0.00', phone: '', version: '0' });
const meal = (id: string, storeId = '1') => ({ id, storeId, name: '分页餐品' + id, itemType: 'DISH', categoryId: '1',
  price: '10.00', status: 1, description: '', image: '', remaining: 10, flavors: [], storeName: shop(storeId).name });
const envelope = (records: unknown[], nextCursor: string | null = null) => ({ records, nextCursor, hasMore: nextCursor !== null });
async function client(page: Page) {
  const state = { failNext: false, hold: false, release: undefined as (() => void) | undefined, queries: [] as URL[] };
  await page.addInitScript(() => sessionStorage.setItem('campus-user', JSON.stringify({ id: '1', token: 'page-user', role: 'USER', name: '分页用户' })));
  await page.route('**/api/v1/user/**', async route => {
    const url = new URL(route.request().url()), path = url.pathname.replace('/api/v1/user', '');
    const ok = (data: unknown) => route.fulfill({ json: { code: 'OK', data } });
    if (path === '/campuses') return ok([{ id: '1', name: '分页校区', enabled: true }]);
    if (path === '/stores/page') return ok(envelope([shop('1')], 'shops-next'));
    if (path === '/search/page') {
      state.queries.push(url);
      if (url.searchParams.has('cursor')) {
        if (state.failNext) { state.failNext = false; return route.abort(); }
        if (state.hold) await new Promise<void>(resolve => { state.release = resolve; });
        return ok(envelope([meal('1'), meal('205', '205')]));
      }
      return ok(envelope([meal(url.searchParams.get('query') === '新筛选' ? '9' : '1')], 'search-next'));
    }
    const detail = /^\/stores\/(\d+)$/.exec(path);
    if (detail) return ok(shop(detail[1]!));
    if (path.endsWith('/products')) return ok([meal(path.includes('/205/') ? '205' : '1', path.includes('/205/') ? '205' : '1')]);
    if (path.endsWith('/categories')) return ok([]);
    if (path.endsWith('/cart')) return ok({ storeId: '1', cartVersion: '0', items: [] });
    return ok([]);
  });
  return state;
}

test('search load-more keeps successful rows after failure and opens a store outside the selector page', async ({ page }, info) => {
  const state = await client(page);
  await page.goto('/app/');
  await page.getByText('搜本校区', { exact: true }).click();
  await expect(page.locator('.menu-product')).toHaveCount(1);
  state.failNext = true;
  await page.getByText('加载更多餐品', { exact: true }).click();
  await expect(page.getByText('网络连接失败，请重试', { exact: true })).toBeVisible();
  await expect(page.locator('.menu-product')).toHaveCount(1);
  await page.getByText('加载更多餐品', { exact: true }).click();
  await expect(page.locator('.menu-product')).toHaveCount(2);
  const card = page.locator('.menu-product').filter({ has: page.getByText('分页餐品205', { exact: true }) });
  await card.getByText('选餐', { exact: true }).click();
  await expect(page.getByText('分页店铺205 ▾', { exact: true })).toBeVisible();
  await expect(page.locator('uni-view.modal').getByText('分页餐品205', { exact: true })).toBeVisible();
  await page.screenshot({ path: info.outputPath('cross-page-selection.png'), fullPage: true });
});

test('new search discards an older pending page and starts without its cursor', async ({ page }) => {
  const state = await client(page);
  await page.goto('/app/');
  await page.getByText('搜本校区', { exact: true }).click();
  state.hold = true;
  await page.getByText('加载更多餐品', { exact: true }).click();
  await expect.poll(() => Boolean(state.release)).toBe(true);
  // uni-app places aria-label on its wrapper; the editable native input is inside it.
  await page.locator('[aria-label="搜索餐品或店铺名称"] input').fill('新筛选');
  await page.getByText('搜本校区', { exact: true }).click();
  await expect(page.locator('.menu-product').getByText('分页餐品9', { exact: true })).toBeVisible();
  expect(state.queries.at(-1)!.searchParams.has('cursor')).toBe(false);
  const oldResponse = page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.pathname === '/api/v1/user/search/page' && url.searchParams.has('cursor');
  });
  state.release!();
  await (await oldResponse).finished();
  await expect(page.locator('.menu-product')).toHaveCount(1);
  await expect(page.locator('.menu-product').getByText('分页餐品205', { exact: true })).toHaveCount(0);
});

test('workbench list loads more and a new filter resets its cursor', async ({ page }) => {
  const reads: URL[] = [];
  await page.addInitScript(() => {
    sessionStorage.setItem('campus-admin', JSON.stringify({ id: '1', token: 'page-admin', role: 'PLATFORM_ADMIN', name: '分页管理员', mustChangePassword: false }));
    localStorage.setItem('campus-store', '1');
  });
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url());
    let data: unknown = url.pathname.endsWith('/page') ? envelope([]) : [];
    if (url.pathname === '/api/v1/merchant/stores/page') {
      reads.push(url);
      data = url.searchParams.get('query') === '筛选店铺'
        ? envelope([shop('9', '筛选店铺')])
        : url.searchParams.has('cursor') ? envelope([shop('1'), shop('2')]) : envelope([shop('1')], 'next-store');
    }
    if (url.pathname.endsWith('/campuses')) data = [{ id: '1', name: '分页校区', enabled: true }];
    return route.fulfill({ json: { code: 'OK', data } });
  });
  await page.goto('/admin/');
  await page.getByRole('button', { name: '加载更多列表店铺', exact: true }).click();
  await expect(page.locator('.el-table__body-wrapper').getByText('分页店铺2', { exact: true })).toBeVisible();
  await expect(page.locator('.el-table__body-wrapper tbody tr')).toHaveCount(2);
  await page.getByPlaceholder('搜索店铺', { exact: true }).fill('筛选店铺');
  await page.getByRole('button', { name: '筛选店铺', exact: true }).click();
  await expect(page.locator('.el-table__body-wrapper').getByText('筛选店铺', { exact: true })).toBeVisible();
  expect(reads.at(-1)!.searchParams.has('cursor')).toBe(false);
  await expect(page.locator('.el-table__body-wrapper tbody tr')).toHaveCount(1);
});

test('page merge uses the complete product identity and keeps updated rows', () => {
  expect(mergeRecords([meal('1')], [meal('1'), { ...meal('1'), itemType: 'SETMEAL' }], row => row.storeId + ':' + row.itemType + ':' + row.id)).toHaveLength(2);
});

test('claiming a coupon preserves loaded store pages, selection and the next cursor', async ({ page }) => {
  const reads: (string | null)[] = [];
  let claimed = false;
  const coupon = { id: '99', name: '保持分页券', storeId: null, kind: 'REDUCTION', thresholdCents: '0', value: '100', endsAt: '2099-12-31T23:59:59+08:00', state: 'AVAILABLE' };
  await page.addInitScript(() => sessionStorage.setItem('campus-user', JSON.stringify({ id: '1', token: 'coupon-user', role: 'USER', name: '领券用户' })));
  await page.route('**/api/v1/user/**', route => {
    const url = new URL(route.request().url()), path = url.pathname.replace('/api/v1/user', '');
    let data: unknown = path.endsWith('/page') ? envelope([]) : [];
    if (path === '/stores/page') {
      const cursor = url.searchParams.get('cursor'); reads.push(cursor);
      data = cursor === 'after2' ? envelope([shop('3')]) : cursor === 'after1' ? envelope([shop('2')], 'after2') : envelope([shop('1')], 'after1');
    }
    if (path === '/coupons/page') data = envelope(claimed ? [] : [coupon]);
    if (path === '/wallet/page') data = envelope(claimed ? [coupon] : []);
    if (path === '/coupons/99/claim') { claimed = true; data = { id: '99' }; }
    return route.fulfill({ json: { code: 'OK', data } });
  });
  await page.goto('/app/pages/account/index');
  await expect(page.getByText('分页店铺1 ▾', { exact: true })).toBeVisible();
  await page.getByText('加载更多领券店铺', { exact: true }).click();
  await page.getByText('分页店铺1 ▾', { exact: true }).click();
  await page.locator('.uni-picker-item:visible').filter({ hasText: '分页店铺2' }).click();
  await expect(page.getByText('分页店铺2 ▾', { exact: true })).toBeVisible();
  await page.getByText('领取', { exact: true }).click();
  await expect(page.getByText('保持分页券', { exact: true })).toBeVisible();
  await expect(page.getByText('分页店铺2 ▾', { exact: true })).toBeVisible();
  expect(reads).toEqual([null, 'after1']);
  await page.getByText('加载更多领券店铺', { exact: true }).click();
  await expect(page.getByText('加载更多领券店铺', { exact: true })).toHaveCount(0);
  expect(reads).toEqual([null, 'after1', 'after2']);
});
