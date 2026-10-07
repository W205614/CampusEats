import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import fs from 'node:fs';
import { randomBytes } from 'node:crypto';
const config = Object.fromEntries(
  fs
    .readFileSync('../../.env', 'utf8')
    .split(/\r?\n/)
    .filter((l) => l.includes('=') && !l.startsWith('#'))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]),
);
const password = process.env.ADMIN_PASSWORD || config.DEMO_ADMIN_PASSWORD;
let adminToken = '',
  courierId = '';
async function admin(request: APIRequestContext, path: string, method = 'GET', data?: unknown) {
  const response = await request.fetch('/api/v1/admin' + path, {
    method,
    data,
    headers: { Authorization: 'Bearer ' + adminToken },
  });
  const result = await response.json();
  expect(result.code).toBe('OK');
  return result.data;
}
test.beforeAll(async ({ request }) => {
  const r = await request.post('/api/v1/admin/auth/login', {
    data: { username: 'admin', password },
  });
  const login = await r.json();
  expect(login.code).toBe('OK');
  adminToken = login.data.token;
  await admin(request, '/shop', 'PUT', {
    open: true,
    deliveryFee: 6,
    packagingFee: 1,
    hours: ['00:00-24:00'],
    phone: '02912345678',
  });
  const employees = await admin(request, '/employees');
  let courier = employees.find((x: any) => x.username === 'e2e-courier');
  if (!courier)
    courier = await admin(request, '/employees', 'POST', {
      username: 'e2e-courier',
      name: '验收配送员',
      role: 'DELIVERER',
      enabled: true,
      password: randomBytes(24).toString('hex'),
    });
  else
    await admin(request, '/employees/' + courier.id, 'PUT', {
      username: 'e2e-courier',
      name: '验收配送员',
      role: 'DELIVERER',
      enabled: true,
      password: randomBytes(24).toString('hex'),
    });
  courierId = courier.id;
});
async function adminLogin(page: Page) {
  await page.goto('/admin/');
  await page.getByPlaceholder('员工账户').fill('admin');
  await page.getByPlaceholder('启动脚本生成的初始密码').fill(password!);
  await page.getByRole('button', { name: '登录工作台' }).click();
  await expect(page.getByRole('heading', { name: '今天的供餐，一目了然' })).toBeVisible();
}
async function userLogin(page: Page, account: number) {
  await page.goto('/app/pages/login/index');
  await page.getByText('进入演示用户 ' + account, { exact: true }).click();
  await expect(page.getByText('今天，也好好吃饭。', { exact: true })).toBeVisible();
}
async function addFood(page: Page) {
  await page.getByText('校园热餐', { exact: true }).click();
  const dish = page.locator('.card').filter({ hasText: '番茄鸡蛋饭' });
  await dish.getByText('选餐', { exact: true }).click();
  await page.getByText(/加入购物车/).click();
  await expect(page.getByText('查看购物车', { exact: true })).toBeVisible();
}
async function address(page: Page) {
  await page.goto('/app/pages/addresses/index');
  await page.getByText('新增地址', { exact: true }).click();
  await page.getByRole('textbox').nth(0).fill('305');
  await page.getByRole('textbox').nth(1).fill('校园验收用户');
  await page.getByRole('spinbutton').fill('13800000000');
  await page.getByText('保存地址', { exact: true }).click();
  await expect(page.getByText('校园验收用户　13800000000').first()).toBeVisible();
}
async function confirm(page: Page) {
  await page.getByText('确定', { exact: true }).click();
}
test('H5下单支付，管理端接单派送，用户收到送达状态', async ({ page, request, browser }) => {
  const userPage = await browser.newPage({
    viewport: { width: 390, height: 844 },
  });
  await userLogin(userPage, 1);
  await address(userPage);
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
  await page.getByText('验收配送员', { exact: true }).click();
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
  await userPage.close();
});
test('丢失提交响应后用原请求键恢复，不重复创建订单', async ({ page, request }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await userLogin(page, 2);
  await address(page);
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
  const current = await admin(request, '/orders/' + created.id);
  expect(current.id).toBe(created.id);
  const all = await admin(request, '/orders?size=100');
  expect(all.records.filter((o: any) => o.requestId === created.requestId)).toHaveLength(1);
});
test('H5取消已支付订单，等待模拟退款完成', async ({ page, request }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await userLogin(page, 3);
  await address(page);
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
    .poll(async () => (await admin(request, '/orders/' + order.id)).payStatus, {
      timeout: 15000,
    })
    .toBe(2);
  await page.getByText('刷新', { exact: true }).click();
  await expect(card.getByText('已退款', { exact: true })).toBeVisible();
});
