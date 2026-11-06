import { test, expect, type Page } from '@playwright/test';

async function fixture(page: Page) {
  const state = {
    intent: false,
    paymentState: 'PENDING',
    closeRequested: false,
    creates: [] as { key: string | undefined; body: unknown }[],
    cancels: [] as unknown[],
  };
  await page.addInitScript(() =>
    sessionStorage.setItem(
      'campus-user',
      JSON.stringify({ id: '301', name: '支付验收', role: 'USER', token: 'payment-fixture' }),
    ),
  );
  await page.route('**/api/v1/user/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1/user', '');
    const ok = (data: unknown) =>
      route.fulfill({ json: { code: 'OK', data, requestId: 'browser-fixture' } });
    const intent = () => ({
      id: 'payment-901',
      checkoutId: '901',
      channel: 'NATIVE',
      state: state.paymentState,
      expiresAt: '2026-12-01T12:00:00+08:00',
      action:
        state.paymentState === 'PENDING' && !state.closeRequested
          ? { type: 'qrCode', url: 'weixin://wxpay/bizpayurl?pr=fixture' }
          : {},
      closeRequested: state.closeRequested,
    });
    if (path === '/auth/capabilities')
      return ok({
        demoLoginEnabled: false,
        localLoginEnabled: false,
        wechatLoginEnabled: true,
        paymentMode: 'WECHAT',
        mode: 'PRODUCTION',
      });
    if (path === '/orders' || path === '/aftersales') return ok([]);
    if (path === '/checkouts')
      return ok([
        {
          id: '901',
          state:
            state.paymentState === 'SUCCEEDED'
              ? 'PAID'
              : state.paymentState === 'CLOSED'
                ? 'CANCELLED'
                : 'PENDING',
          paymentCloseRequested: state.closeRequested,
          totalCents: '1000',
          version: '2',
          orders: [],
        },
      ]);
    if (path === '/checkouts/901/payment')
      return state.intent
        ? ok(intent())
        : route.fulfill({
            status: 404,
            json: { code: 'PAYMENT_NOT_FOUND', message: '尚未发起支付' },
          });
    if (path === '/checkouts/901/payment-intents') {
      state.creates.push({
        key: route.request().headers()['idempotency-key'],
        body: route.request().postDataJSON(),
      });
      state.intent = true;
      return ok(intent());
    }
    if (path === '/checkouts/901/cancel') {
      state.cancels.push(route.request().postDataJSON());
      state.closeRequested = true;
      state.paymentState = 'CLOSING';
      return ok({
        id: '901',
        state: 'PENDING',
        paymentCloseRequested: true,
        totalCents: '1000',
        version: '3',
        orders: [],
      });
    }
    return route.fulfill({
      status: 404,
      json: { code: 'NOT_FOUND', message: 'fixture path ' + path },
    });
  });
  return state;
}

test('desktop renders a real payment QR and confirms only the server result', async ({ page }) => {
  const state = await fixture(page);
  await page.goto('/app/pages/orders/index');
  await page.getByText('整批付款', { exact: true }).click();
  await expect(page.getByText('支付状态：待支付', { exact: true })).toBeVisible();
  await expect(page.locator('img[src^="data:image/png"]')).toBeVisible();
  expect(state.creates).toEqual([
    { key: 'payment_901', body: { channel: 'NATIVE', version: '2' } },
  ]);
  state.paymentState = 'SUCCEEDED';
  await page.getByText('重新加载', { exact: true }).click();
  await expect(page.getByText('支付状态：支付已确认', { exact: true })).toBeVisible();
  await expect(page.locator('img[src^="data:image/png"]')).toHaveCount(0);
});

test('a repeated pay click recovers the original intent instead of creating another request', async ({
  page,
}) => {
  const state = await fixture(page);
  await page.goto('/app/pages/orders/index');
  const pay = page.getByText('整批付款', { exact: true });
  await pay.click();
  await expect(page.getByText('支付状态：待支付', { exact: true })).toBeVisible();
  await expect(pay).toBeEnabled();
  await pay.click();
  await expect(page.getByText('支付状态：待支付', { exact: true })).toBeVisible();
  expect(state.creates).toHaveLength(1);
});

test('unknown payments remain unresolved and never display payment success', async ({ page }) => {
  const state = await fixture(page);
  state.intent = true;
  state.paymentState = 'UNKNOWN';
  await page.goto('/app/pages/orders/index');
  await page.getByText('整批付款', { exact: true }).click();
  await expect(page.getByText('支付状态：结果确认中', { exact: true })).toBeVisible();
  await expect(page.getByText('支付状态：支付已确认', { exact: true })).toHaveCount(0);
  expect(state.creates).toHaveLength(0);
});

test('cancelling and closed payments remove the QR and never invite another scan', async ({
  page,
}) => {
  const state = await fixture(page);
  await page.goto('/app/pages/orders/index');
  await page.getByText('整批付款', { exact: true }).click();
  await expect(page.locator('img[src^="data:image/png"]')).toBeVisible();
  await expect(
    page.getByText('请使用微信扫码付款，付款后点击重新加载确认。', { exact: true }),
  ).toBeVisible();
  await page.getByText('取消整批', { exact: true }).click();
  await page.getByText('确定', { exact: true }).click();
  await expect(page.getByText('支付状态：正在关闭', { exact: true })).toBeVisible();
  await expect(
    page.getByText('取消处理中，确认渠道未付款后释放库存和优惠。', { exact: true }),
  ).toBeVisible();
  await expect(page.locator('img[src^="data:image/png"]')).toHaveCount(0);
  await expect(
    page.getByText('请使用微信扫码付款，付款后点击重新加载确认。', { exact: true }),
  ).toHaveCount(0);
  expect(state.cancels).toEqual([{ version: '2' }]);
  expect(state.creates).toHaveLength(1);
  state.paymentState = 'CLOSED';
  await page.getByText('重新加载', { exact: true }).click();
  await expect(page.getByText('支付状态：已关闭', { exact: true })).toBeVisible();
  await expect(
    page.getByText('取消处理中，确认渠道未付款后释放库存和优惠。', { exact: true }),
  ).toHaveCount(0);
  await expect(page.locator('img[src^="data:image/png"]')).toHaveCount(0);
  await expect(
    page.getByText('请使用微信扫码付款，付款后点击重新加载确认。', { exact: true }),
  ).toHaveCount(0);
  await expect(page.getByText('整批付款', { exact: true })).toHaveCount(0);
  await expect(page.getByText('支付状态：支付已确认', { exact: true })).toHaveCount(0);
});
