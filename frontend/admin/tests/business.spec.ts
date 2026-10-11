import { type Page } from '@playwright/test';
import { test, expect, adminLogin, userLogin } from './helpers';
import { randomBytes } from 'node:crypto';
async function addFood(page: Page) {
  await page.getByText('校园热餐', { exact: true }).click();
  const dish = page.locator('.card').filter({ hasText: '番茄鸡蛋饭' });
  await dish.getByText('选餐', { exact: true }).click();
  await page.getByText(/加入购物车/).click();
  await expect(page.getByText('查看购物车', { exact: true })).toBeVisible();
}
async function address(page: Page, name: string) {
  await page.goto('/app/pages/addresses/index');
  await page.getByText('新增地址', { exact: true }).click();
  await page.getByRole('textbox').nth(0).fill('305');
  await page.getByRole('textbox').nth(1).fill(name);
  await page.getByRole('spinbutton').fill('13800000000');
  await page.getByText('保存地址', { exact: true }).click();
  const card = page.locator('.card').filter({ hasText: name });
  await expect(card).toBeVisible();
  if (await card.getByText('设为默认', { exact: true }).count())
    await card.getByText('设为默认', { exact: true }).click();
  await expect(card.getByText('默认', { exact: true })).toBeVisible();
}
async function confirm(page: Page) {
  await page.getByText('确定', { exact: true }).click();
}
test('H5下单支付，管理端接单派送，用户收到送达状态', async ({ page, admin, browser, demoData }) => {
  const courierName = '验收配送员-' + randomBytes(4).toString('hex');
  const courierInput = { username: 'e2e-' + randomBytes(6).toString('hex'), name: courierName, role: 'DELIVERER', enabled: true, password: randomBytes(24).toString('hex') };
  const courier = await admin('/employees', 'POST', courierInput);
  const userPage = await browser.newPage({
    viewport: { width: 390, height: 844 },
  });
  try {
    await demoData.trackAccount(1);
    await userLogin(userPage, 1);
    await address(userPage, demoData.name);
    await userPage.goto('/app/');
    await addFood(userPage);
    await userPage.goto('/app/pages/checkout/index');
    await expect(userPage.getByText('合计', { exact: true })).toBeVisible();
    const submitted = userPage.waitForResponse(
      (r) => r.url().endsWith('/api/v1/user/orders') && r.request().method() === 'POST',
    );
    await userPage.getByText('提交订单 · 稍后模拟支付', { exact: true }).click();
    await confirm(userPage);
    const order = (await (await submitted).json()).data;
    await expect(
      userPage.locator('uni-page-body').getByText('我的订单', { exact: true }),
    ).toBeVisible();
    const card = userPage.locator('.card').filter({ hasText: '番茄鸡蛋饭' }).first();
    await card.getByText('模拟支付', { exact: true }).click();
    await confirm(userPage);
    await expect(card.getByText('待接单', { exact: true })).toBeVisible();
    await adminLogin(page);
    await page.getByRole('link', { name: '订单与配送', exact: true }).click();
    await page.locator('.el-table__row').filter({ hasText: order.number }).click();
    await page.getByRole('button', { name: '接单备餐' }).click();
    await page.getByText('选择配送员', { exact: true }).click();
    await page.getByText(courierName, { exact: true }).click();
    await page.getByRole('button', { name: '派单', exact: true }).click();
    await page.getByRole('button', { name: '开始配送' }).click();
    await page.getByRole('button', { name: '确认送达' }).click();
    await expect(
      page.locator('.el-drawer__body').getByText('已送达', { exact: false }),
    ).toBeVisible();
    await userPage.getByText('刷新', { exact: true }).click();
    await expect(card.getByText('已送达', { exact: true })).toBeVisible();
    await page.screenshot({
      path: '../../.local/admin-orders.png',
      fullPage: true,
    });
    await userPage.screenshot({
      path: '../../.local/client-orders.png',
      fullPage: true,
    });
  } finally {
    await userPage.close();
    await admin('/employees/' + courier.id, 'PUT', { ...courierInput, password: null, enabled: false });
  }
});
test('丢失提交响应后用原请求键恢复，不重复创建订单', async ({ page, admin, demoData }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await demoData.trackAccount(2);
  await userLogin(page, 2);
  await address(page, demoData.name);
  await page.goto('/app/');
  await addFood(page);
  await page.goto('/app/pages/checkout/index');
  await expect(page.getByText('合计', { exact: true })).toBeVisible();
  let created: any;
  let first = true;
  await page.route('**/api/v1/user/orders', async (route) => {
    if (route.request().method() === 'POST' && first) {
      first = false;
      const response = await route.fetch();
      created = (await response.json()).data;
      await route.abort('failed');
    } else await route.continue();
  });
  await page.getByText('提交订单 · 稍后模拟支付', { exact: true }).dblclick();
  await confirm(page);
  await expect(page.getByText('上次提交结果待确认', { exact: true })).toBeVisible();
  // uni-button is a custom element: Playwright does not infer its disabled state.
  const recovery = page.getByText('查询并恢复订单', { exact: true });
  await expect(recovery).not.toHaveAttribute('disabled', /.*/);
  await recovery.click();
  await expect(page.locator('uni-page-body').getByText('我的订单', { exact: true })).toBeVisible();
  const current = await admin('/orders/' + created.id);
  expect(current.id).toBe(created.id);
  const all = await admin('/orders?size=100');
  expect(all.records.filter((o: any) => o.requestId === created.requestId)).toHaveLength(1);
});
test('H5取消已支付订单，等待模拟退款完成', async ({ page, admin, demoData }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await demoData.trackAccount(3);
  await userLogin(page, 3);
  await address(page, demoData.name);
  await page.goto('/app/');
  await addFood(page);
  await page.goto('/app/pages/checkout/index');
  await expect(page.getByText('合计', { exact: true })).toBeVisible();
  const submitted = page.waitForResponse(
    (r) => r.url().endsWith('/api/v1/user/orders') && r.request().method() === 'POST',
  );
  await page.getByText('提交订单 · 稍后模拟支付', { exact: true }).click();
  await confirm(page);
  const order = (await (await submitted).json()).data;
  await expect(page.locator('uni-page-body').getByText('我的订单', { exact: true })).toBeVisible();
  const card = page.locator('.card').filter({ hasText: '番茄鸡蛋饭' }).first();
  await card.getByText('模拟支付', { exact: true }).click();
  await confirm(page);
  await expect(card.getByText('待接单', { exact: true })).toBeVisible();
  await card.getByText('取消', { exact: true }).click();
  await confirm(page);
  await expect(card.getByText('已取消', { exact: true })).toBeVisible();
  await expect
    .poll(async () => (await admin('/orders/' + order.id)).payStatus, {
      timeout: 15000,
    })
    .toBe(2);
  await page.getByText('刷新', { exact: true }).click();
  await expect(card.getByText('已退款', { exact: true })).toBeVisible();
});
