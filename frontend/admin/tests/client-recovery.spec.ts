import { test, expect, type Page } from '@playwright/test';

const user = (id = '1') => ({ id, token: 'client-' + id, name: '验收用户' + id, role: 'USER' });
const cart = {
  storeId: '1', storeName: '原账户餐厅', campusId: '1', campusName: '测试校区', cartVersion: '1',
  items: [{ id: '1', itemId: '1', itemType: 'DISH', name: '原账户餐品', quantity: 1, price: '10.00', flavors: {} }],
};
const order = {
  id: '1', number: 'PRIVATE-ORDER-1', storeId: '1', storeName: '原账户餐厅', campusName: '测试校区',
  version: '1', status: 3, fulfillment: 'PICKUP', pickupCode: 'PRIVATE-CODE-1', amount: '10.00',
  paidCents: '1000', refundedCents: '0', details: [], refunds: [], aftersales: [],
};
const quote = { stores: [], totalCents: '1000', quoteHash: 'original-quote' };

async function fixture(page: Page) {
  const state = { fail: '', hold: '', release: undefined as (() => void) | undefined, previewFailure: false, aftersales: [] as object[] };
  await page.addInitScript((session) => {
    sessionStorage.setItem('campus-user', JSON.stringify(session));
    localStorage.setItem('checkout-stores', JSON.stringify({ type: 'object', data: ['1'] }));
  }, user());
  await page.route('**/api/v1/user/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1/user', '');
    const token = route.request().headers().authorization;
    const ok = (data: unknown) => route.fulfill({ json: { code: 'OK', data } });
    if (path === '/auth/demo') return ok(user(String(route.request().postDataJSON().account)));
    if (path === state.hold && token === 'Bearer client-1') {
      await new Promise<void>(resolve => { state.release = resolve; });
    }
    if (path === state.fail) return route.fulfill({ status: 401, json: { code: 'UNAUTHORIZED', message: '登录已失效' } });
    if (path === '/checkouts/preview') return state.previewFailure
      ? route.fulfill({ status: 409, json: { code: 'QUOTE_CHANGED', message: '请重新计算报价' } }) : ok(quote);
    if (path === '/carts') return ok(token === 'Bearer client-1' ? [cart] : []);
    if (path === '/orders') return ok(token === 'Bearer client-1' ? [order] : []);
    if (path === '/aftersales') return ok(state.aftersales);
    return ok([]);
  });
  return state;
}

test('checkout discards a delayed quote when the user changes their remark', async ({ page }) => {
  const state = await fixture(page);
  state.hold = '/checkouts/preview';
  await page.goto('/app/pages/checkout/index');
  await page.getByText('计算报价', { exact: true }).click();
  await expect.poll(() => Boolean(state.release)).toBe(true);
  await page.locator('textarea').fill('不要辣');
  const received = page.waitForResponse(r => r.url().endsWith('/checkouts/preview'));
  state.release!();
  await received;
  await expect(page.getByText('计算报价', { exact: true })).not.toHaveAttribute('disabled');
  await expect(page.getByText('提交订单，随后模拟支付', { exact: true })).toHaveCount(0);
});

test.describe('aftersale appeal eligibility', () => {
  // Server timestamps without an offset mean Shanghai time, wherever the browser runs.
  test.use({ timezoneId: 'America/New_York' });
  test('appeal appears after rejection or exactly 48 hours pending, and never after an appeal', async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-10-19T12:00:00+08:00'));
    const state = await fixture(page);
    const cases = [
      { id: 'new', state: 'PENDING', createdAt: '2026-10-19T11:00:00', eligible: false },
      { id: 'before48', state: 'PENDING', createdAt: '2026-10-17T12:00:00.001', eligible: false },
      { id: 'exact48', state: 'PENDING', createdAt: '2026-10-17T12:00:00', eligible: true },
      { id: 'after48', state: 'PENDING', createdAt: '2026-10-17T11:59:59.999', eligible: true },
      { id: 'utc48', state: 'PENDING', createdAt: '2026-10-17T04:00:00Z', eligible: true },
      { id: 'offset48', state: 'PENDING', createdAt: '2026-10-17T12:00:00+08:00', eligible: true },
      { id: 'negative48', state: 'PENDING', createdAt: '2026-10-17T00:00:00-04:00', eligible: true },
      { id: 'rejected', state: 'REJECTED', createdAt: '2026-10-19T11:00:00', eligible: true },
      { id: 'appealed', state: 'PENDING', createdAt: '2026-10-17T11:00:00', appealed: true, eligible: false },
      { id: 'rejectedAppealed', state: 'REJECTED', createdAt: '2026-10-19T11:00:00', appealed: true, eligible: false },
    ];
    state.aftersales = cases.map(value => ({ reason: '售后验收', amountCents: '100', version: '1', appealed: false, ...value }));
    await page.goto('/app/pages/orders/index');
    for (const value of cases) {
      const card = page.locator('.card').filter({ has: page.getByText('申请 ' + value.id, { exact: true }) });
      await expect(card).toBeVisible();
      await expect(card.getByText('申请平台介入', { exact: true })).toHaveCount(value.eligible ? 1 : 0);
      if (['new', 'before48'].includes(value.id))
        await expect(card.getByText('商家拒绝或超过48小时未处理时可申请平台介入', { exact: true })).toBeVisible();
    }
  });
});

test('a failed checkout recalculation removes the previous payable quote', async ({ page }) => {
  const state = await fixture(page);
  await page.goto('/app/pages/checkout/index');
  await page.getByText('计算报价', { exact: true }).click();
  await expect(page.getByText('提交订单，随后模拟支付', { exact: true })).toBeVisible();
  state.previewFailure = true;
  await page.getByText('计算报价', { exact: true }).click();
  await expect(page.getByText('请重新计算报价', { exact: true })).toBeVisible();
  await expect(page.getByText('提交订单，随后模拟支付', { exact: true })).toHaveCount(0);
});

for (const target of ['cart', 'orders'] as const) {
  test(`${target} clears private state when its account expires and another account logs in`, async ({ page }) => {
    const state = await fixture(page);
    const endpoint = target === 'cart' ? '/carts' : '/orders';
    const tab = target === 'cart' ? '购物车' : '订单';
    const privateText = target === 'cart' ? '原账户餐品' : 'PRIVATE-CODE-1';
    await page.goto('/app/pages/' + target + '/index');
    await expect(page.getByText(privateText, { exact: true })).toBeVisible();
    await page.locator('.uni-tabbar__item').filter({ hasText: '点餐' }).click();
    state.fail = endpoint;
    await page.locator('.uni-tabbar__item').filter({ hasText: tab }).click();
    await expect(page.getByText('登录已失效', { exact: true })).toBeVisible();
    await expect(page.getByText(privateText, { exact: true })).toHaveCount(0);
    // Re-entering this tab prompts login, retaining the cached tab component.
    await page.locator('.uni-tabbar__item').filter({ hasText: '点餐' }).click();
    await page.locator('.uni-tabbar__item').filter({ hasText: tab }).click();
    await page.getByText('进入演示用户 2', { exact: true }).click();
    await page.locator('.uni-tabbar__item').filter({ hasText: tab }).click();
    await expect(page.getByText(privateText, { exact: true })).toHaveCount(0);
  });
}

test('a delayed previous-account order load cannot overwrite a new account or keep it loading', async ({ page }) => {
  const state = await fixture(page);
  await page.goto('/app/pages/orders/index');
  await expect(page.getByText('PRIVATE-CODE-1', { exact: true })).toBeVisible();
  state.hold = '/orders';
  await page.getByText('重新加载', { exact: true }).click();
  await expect.poll(() => Boolean(state.release)).toBe(true);
  state.fail = '/carts';
  await page.locator('.uni-tabbar__item').filter({ hasText: '购物车' }).click();
  await expect(page.getByText('登录已失效', { exact: true })).toBeVisible();
  await page.locator('.uni-tabbar__item').filter({ hasText: '订单' }).click();
  await page.getByText('进入演示用户 2', { exact: true }).click();
  const newLoad = page.waitForResponse(r => r.url().endsWith('/orders') && r.request().headers().authorization === 'Bearer client-2');
  await page.locator('.uni-tabbar__item').filter({ hasText: '订单' }).click();
  await newLoad;
  const oldLoad = page.waitForResponse(r => r.url().endsWith('/orders') && r.request().headers().authorization === 'Bearer client-1');
  state.release!();
  await oldLoad;
  // Let the released transport callback and Vue's next render both finish.
  await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
  await expect(page.getByText('PRIVATE-CODE-1', { exact: true })).toHaveCount(0);
  await expect(page.getByText('还没有订单，先去看看校园好店吧。', { exact: true })).toBeVisible();
});
