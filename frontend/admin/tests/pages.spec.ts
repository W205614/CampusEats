import { test, expect, type Page, type Locator, type APIRequestContext } from '@playwright/test';
import fs from 'node:fs';
import { randomBytes } from 'node:crypto';

const config = Object.fromEntries(fs.readFileSync('../../.env', 'utf8').split(/\r?\n/)
  .filter(l => l.includes('=') && !l.startsWith('#'))
  .map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
const password = process.env.ADMIN_PASSWORD || config.DEMO_ADMIN_PASSWORD;
let token = '';
const catalogFixtures = new Set<string>();
async function admin(request: APIRequestContext, path: string, method = 'GET', data?: unknown) {
  const response = await request.fetch('/api/v1/admin' + path, {
    method, data, headers: { Authorization: 'Bearer ' + token },
  });
  const result = await response.json();
  expect(result.code).toBe('OK');
  return result.data;
}
test.beforeAll(async ({ request }) => {
  const response = await request.post('/api/v1/admin/auth/login', { data: { username: 'admin', password } });
  const login = await response.json();
  expect(login.code).toBe('OK');
  token = login.data.token;
});
test.afterEach(async ({ request }) => {
  for (const type of ['SETMEAL', 'DISH']) {
    for (const product of await admin(request, '/products/' + type)) {
      if (catalogFixtures.has(product.name)) {
        if (product.status === 1) await admin(request, '/products/' + type + '/' + product.id, 'PUT', { ...product, enabled: false, flavors: product.flavors || [], components: product.components || [] });
        await admin(request, '/products/' + type + '/' + product.id, 'DELETE');
      }
    }
  }
  for (const category of await admin(request, '/categories')) {
    if (catalogFixtures.has(category.name)) await admin(request, '/categories/' + category.id, 'DELETE');
  }
  catalogFixtures.clear();
});
async function login(page: Page, username = 'admin', secret = password!) {
  await page.goto('/admin/');
  await page.getByPlaceholder('员工账户').fill(username);
  await page.getByPlaceholder('启动脚本生成的初始密码').fill(secret);
  await page.getByRole('button', { name: '登录工作台' }).click();
}
async function userLogin(page: Page) {
  await page.goto('/app/pages/login/index');
  await page.getByText('进入演示用户 3', { exact: true }).click();
  await expect(page.getByText('今天，也好好吃饭。', { exact: true })).toBeVisible();
  await page.getByText('校园热餐', { exact: true }).click();
}
function field(root: Page | Locator, label: string) {
  return root.locator('.el-form-item').filter({ hasText: label });
}
async function select(root: Page | Locator, label: string, value: string, page: Page) {
  await field(root, label).locator('.el-select').click();
  await page.getByRole('option', { name: value, exact: true }).click();
}
async function save(page: Page) {
  await page.getByRole('dialog').getByRole('button', { name: '保存', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
}
function row(page: Page, name: string) {
  return page.locator('.el-table__row').filter({ hasText: name });
}
async function remove(page: Page, name: string) {
  await expect(row(page, name)).toBeVisible();
  if (await row(page, name).getByText('启用', { exact: true }).count()) {
    await row(page, name).getByRole('button', { name: '编辑' }).click();
    await field(page.getByRole('dialog'), '启用状态').locator('.el-switch').click();
    await save(page);
    await expect(row(page, name)).toContainText('停用');
  }
  await row(page, name).getByRole('button', { name: '删除', exact: true }).click();
  await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click();
  await expect(row(page, name)).toHaveCount(0);
}

test('管理端分类、菜品口味、图片、套餐及编辑删除，H5显示保存结果', async ({ page, browser, request }) => {
  const suffix = Date.now().toString(36);
  const category = '页面分类' + suffix, dish = '页面餐品' + suffix, meal = '页面套餐' + suffix;
  for (const name of [category, dish, meal]) catalogFixtures.add(name);
  await login(page);
  await page.getByRole('link', { name: '分类管理', exact: true }).click();
  await expect(page.getByRole('heading', { name: '分类管理', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '新增分类' }).click();
  await field(page.getByRole('dialog'), '名称').getByRole('textbox').fill(category);
  await save(page);
  await expect(row(page, category)).toBeVisible();
  await page.getByRole('link', { name: '菜品管理', exact: true }).click();
  await expect(page.getByRole('heading', { name: '菜品管理', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '新增菜品' }).click();
  const dialog = page.getByRole('dialog');
  await field(dialog, '名称').getByRole('textbox').fill(dish);
  await select(dialog, '所属分类', category, page);
  await field(dialog, '售价（元）').getByRole('spinbutton').fill('12.50');
  await page.getByRole('button', { name: '添加口味组' }).click();
  await page.getByPlaceholder('例如：辣度').fill('辣度');
  await page.getByPlaceholder('不辣、微辣、中辣').fill('不辣、微辣');
  const invalid = page.waitForResponse(r => r.url().endsWith('/uploads') && r.request().method() === 'POST');
  await dialog.locator('input[type=file]').setInputFiles({ name: 'fake.png', mimeType: 'image/png', buffer: Buffer.from('not an image') });
  expect((await invalid).status()).toBe(400);
  const uploaded = page.waitForResponse(r => r.url().endsWith('/uploads') && r.request().method() === 'POST');
  await dialog.locator('input[type=file]').setInputFiles({ name: 'pixel.png', mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jL1cAAAAASUVORK5CYII=', 'base64') });
  expect((await uploaded).status()).toBe(200);
  await expect(dialog.getByAltText('已上传图片')).toBeVisible();
  await save(page);
  await expect(row(page, dish)).toContainText('12.50');
  await row(page, dish).getByRole('button', { name: '编辑' }).click();
  await field(dialog, '售价（元）').getByRole('spinbutton').fill('13.50');
  await save(page);
  await expect(row(page, dish)).toContainText('13.50');
  const userPage = await browser.newPage({ viewport: { width: 390, height: 844 } });
  await userLogin(userPage);
  await userPage.getByText(category, { exact: true }).click();
  await expect(userPage.getByText(dish, { exact: true })).toBeVisible();
  await expect(userPage.getByText('¥13.50', { exact: true })).toBeVisible();
  await userPage.locator('.card').filter({ hasText: dish }).getByText('选餐', { exact: true }).click();
  await expect(userPage.getByText('微辣', { exact: true })).toBeVisible();
  await userPage.getByText('关闭', { exact: true }).click();
  await userPage.close();
  await page.getByRole('link', { name: '套餐管理', exact: true }).click();
  await page.getByRole('button', { name: '新增套餐' }).click();
  await field(dialog, '名称').getByRole('textbox').fill(meal);
  const categories = await admin(request, '/categories');
  await select(dialog, '所属分类', categories.find((x: any) => x.type === 2).name, page);
  await field(dialog, '售价（元）').getByRole('spinbutton').fill('20');
  await page.getByRole('button', { name: '添加菜品' }).click();
  await dialog.getByText('选择菜品', { exact: true }).click();
  await page.getByRole('option', { name: dish, exact: true }).click();
  await save(page);
  await expect(row(page, meal)).toBeVisible();
  await remove(page, meal);
  await page.getByRole('link', { name: '菜品管理', exact: true }).click();
  await remove(page, dish);
  await page.getByRole('link', { name: '分类管理', exact: true }).click();
  await remove(page, category);
});

test('楼栋启停、当日及默认配额、营业开关影响H5选餐', async ({ page, browser, request }) => {
  const name = '页面楼栋' + Date.now().toString(36);
  const shop = await admin(request, '/shop');
  const dishes = await admin(request, '/products/DISH');
  const source = dishes.find((x: any) => x.name === '番茄鸡蛋饭');
  const fixtureName = '页面餐品quota' + Date.now().toString(36);
  catalogFixtures.add(fixtureName);
  await admin(request, '/products/DISH', 'POST', { name: fixtureName, categoryId: source.categoryId,
    price: '12.00', enabled: true, description: '页面配额验收', image: '', flavors: [], components: [] });
  const target = (await admin(request, '/products/DISH')).find((x: any) => x.name === fixtureName);
  const date = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' });
  const quotas = await admin(request, '/quotas?date=' + date);
  const before = quotas.find((x: any) => x.dishId === target.id);
  await login(page);
  await page.getByRole('link', { name: '配送楼栋', exact: true }).click();
  await page.getByRole('button', { name: '新增楼栋' }).click();
  await field(page.getByRole('dialog'), '楼栋名称').getByRole('textbox').fill(name);
  await save(page);
  await expect(row(page, name)).toContainText('可配送');
  await row(page, name).getByRole('button', { name: '编辑' }).click();
  await field(page.getByRole('dialog'), '允许配送').locator('.el-switch').click();
  await save(page);
  await expect(row(page, name)).toContainText('暂停配送');
  await page.getByRole('link', { name: '每日供餐', exact: true }).click();
  await row(page, target.name).getByRole('button', { name: '调整配额' }).click();
  await page.getByRole('dialog').getByRole('spinbutton').fill(String(Number(before.reserved) + Number(before.consumed)));
  await save(page);
  const soldOut = await admin(request, '/quotas?date=' + date);
  expect(Number(soldOut.find((x: any) => x.dishId === target.id).remaining)).toBe(0);
  const userPage = await browser.newPage({ viewport: { width: 390, height: 844 } });
  try {
    await userLogin(userPage);
    const food = userPage.locator('.card').filter({ hasText: target.name });
    await expect(food.getByText('今日已售罄', { exact: true })).toBeVisible();
    await expect(food.getByText('选餐', { exact: true })).toHaveAttribute('disabled', /.*/);
    await row(page, target.name).getByRole('button', { name: '调整配额' }).click();
    await page.getByRole('dialog').getByRole('spinbutton').fill(String(before.total));
    await save(page);
    await row(page, target.name).getByRole('button', { name: '调整配额' }).click();
    await page.getByRole('dialog').getByRole('spinbutton').fill(String(Number(before.defaultQuota) + 1));
    await page.getByText('默认每日份数', { exact: true }).click();
    await save(page);
    const updated = await admin(request, '/products/DISH');
    expect(Number(updated.find((x: any) => x.id === target.id).defaultQuota)).toBe(Number(before.defaultQuota) + 1);
    await page.getByRole('link', { name: '营业规则', exact: true }).click();
    await field(page, '人工营业开关').locator('.el-switch').click();
    await page.getByRole('button', { name: '保存营业规则' }).click();
    await expect(page.getByText('营业规则已更新', { exact: true })).toBeVisible();
    await userPage.reload();
    await expect(userPage.getByText('暂时休息', { exact: true })).toBeVisible();
    await userPage.getByText('校园热餐', { exact: true }).click();
    await expect(userPage.locator('.card').filter({ hasText: target.name }).getByText('选餐', { exact: true })).toHaveAttribute('disabled', /.*/);
    await field(page, '人工营业开关').locator('.el-switch').click();
    const reopened = page.waitForResponse(r => r.url().endsWith('/api/v1/admin/shop') && r.request().method() === 'PUT');
    await page.getByRole('button', { name: '保存营业规则' }).click();
    expect((await reopened).status()).toBe(200);
    await userPage.reload();
    await expect(userPage.getByText('正在营业', { exact: true })).toBeVisible();
  } finally {
    await admin(request, '/shop', 'PUT', shop);
    await admin(request, '/quotas?date=' + date, 'PUT', { dishId: target.id, total: before.total, defaultQuota: false });
    await admin(request, '/quotas?date=' + date, 'PUT', { dishId: target.id, total: before.defaultQuota, defaultQuota: true });
    await userPage.close();
  }
});

test('H5地址编辑默认删除、再来一单、购物车数量与清空', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await userLogin(page);
  await page.goto('/app/pages/addresses/index');
  const name = '地址验收' + Date.now().toString(36);
  await page.getByText('新增地址', { exact: true }).click();
  await page.getByRole('textbox').nth(0).fill('901');
  await page.getByRole('textbox').nth(1).fill(name);
  await page.getByRole('spinbutton').fill('13800000000');
  await page.getByText('保存地址', { exact: true }).click();
  const card = page.locator('.card').filter({ hasText: name });
  await card.getByText('编辑', { exact: false }).click();
  await page.getByRole('textbox').nth(0).fill('902');
  await page.getByText('保存地址', { exact: true }).click();
  await expect(card).toContainText('902');
  await card.getByText('设为默认', { exact: true }).click();
  await expect(card.getByText('默认', { exact: true })).toBeVisible();
  await page.goto('/app/pages/cart/index');
  if (await page.getByText('清空', { exact: true }).count()) {
    await page.getByText('清空', { exact: true }).click();
    await page.getByText('确定', { exact: true }).click();
  }
  await page.goto('/app/');
  await page.getByText('校园热餐', { exact: true }).click();
  await page.locator('.card').filter({ hasText: '番茄鸡蛋饭' }).getByText('选餐', { exact: true }).click();
  await page.getByText(/加入购物车/).click();
  await page.goto('/app/pages/checkout/index');
  await expect(page.getByText('合计', { exact: true })).toBeVisible();
  await page.getByText('提交订单 · 稍后模拟支付', { exact: true }).click();
  await page.getByText('确定', { exact: true }).click();
  const order = page.locator('.card').filter({ hasText: '番茄鸡蛋饭' }).first();
  await order.getByText('取消', { exact: true }).click();
  await page.getByText('确定', { exact: true }).click();
  await expect(order.getByText('已取消', { exact: true })).toBeVisible();
  await order.getByText('再来一单', { exact: true }).click();
  await expect(page.locator('uni-page-body').getByText('我的购物车', { exact: true })).toBeVisible();
  const food = page.locator('.card').filter({ hasText: '番茄鸡蛋饭' });
  await expect(food).toBeVisible();
  const quantity = food.locator('.quantity uni-text');
  const old = Number(await quantity.textContent());
  await food.getByText('+', { exact: true }).click();
  await expect(quantity).toHaveText(String(old + 1));
  await food.getByText('−', { exact: true }).click();
  await expect(quantity).toHaveText(String(old));
  await page.getByText('清空', { exact: true }).click();
  await page.getByText('确定', { exact: true }).click();
  await expect(page.getByText('还没有选餐品', { exact: true })).toBeVisible();
  await page.goto('/app/pages/addresses/index');
  await card.getByText('删除', { exact: true }).click();
  await page.getByText('确定', { exact: true }).click();
  await expect(card).toHaveCount(0);
  await page.goto('/app/pages/orders/index');
  await page.locator('.card').filter({ hasText: '番茄鸡蛋饭' }).first().getByText('查看详情　›', { exact: true }).click();
  await expect(page.locator('.modal')).toContainText('902');
  await expect(page.locator('.modal')).toContainText(name);
});

for (const role of ['运营员', '配送员']) {
  test(`${role}页面登录、强制改密、隐藏管理入口及禁用后撤销会话`, async ({ page, browser }) => {
    const username = 'ui-' + randomBytes(5).toString('hex');
    const initial = randomBytes(24).toString('hex'), changed = randomBytes(24).toString('hex');
    await login(page);
    await page.getByRole('link', { name: '员工与角色', exact: true }).click();
    await page.getByRole('button', { name: '新增员工' }).click();
    const dialog = page.getByRole('dialog');
    await field(dialog, '姓名').getByRole('textbox').fill(username);
    await field(dialog, '账户').getByRole('textbox').fill(username);
    await select(dialog, '角色', role, page);
    await field(dialog, '初始密码（至少12个字符）').locator('input').fill(initial);
    await save(page);
    await expect(row(page, username)).toContainText(role);
    const staff = await browser.newPage();
    try {
      await login(staff, username, initial);
      await expect(staff.getByRole('heading', { name: '修改密码', exact: true })).toBeVisible();
      await field(staff, '原密码').locator('input').fill(initial);
      await field(staff, '新密码（12至128个字符）').locator('input').fill(changed);
      await staff.getByRole('button', { name: '更新并重新登录' }).click();
      await expect(staff.getByRole('button', { name: '登录工作台' })).toBeVisible();
      await login(staff, username, changed);
      await expect(staff.getByRole('heading', { name: '今天的供餐，一目了然' })).toBeVisible();
      await expect(staff.getByRole('link', { name: '员工与角色', exact: true })).toHaveCount(0);
      await expect(staff.getByRole('link', { name: '菜品管理', exact: true })).toHaveCount(0);
      const rejected = staff.waitForResponse(r => r.url().endsWith('/api/v1/admin/employees'));
      await staff.goto('/admin/operations/employees');
      expect((await rejected).status()).toBe(403);
      await expect(staff.getByText('需要管理员权限', { exact: true })).toBeVisible();
      await expect(staff.locator('.el-table__row')).toHaveCount(0);
      await row(page, username).getByRole('button', { name: '编辑' }).click();
      await field(dialog, '启用').locator('.el-switch').click();
      await save(page);
      await expect(row(page, username)).toContainText('禁用');
      await staff.goto('/admin/orders');
      await expect(staff.getByRole('button', { name: '登录工作台' })).toBeVisible();
    } finally {
      await staff.close();
    }
  });
}

test('经营统计实际下载XLSX，审计、退款和通知页面可查询', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: '经营统计', exact: true }).click();
  await expect(page.locator('.el-table__row')).toHaveCount(30);
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: '导出 Excel' }).click();
  const file = await download;
  expect(file.suggestedFilename()).toBe('campus-report.xlsx');
  const bytes = fs.readFileSync((await file.path())!);
  expect(bytes.subarray(0, 2).toString()).toBe('PK');
  expect(bytes.length).toBeGreaterThan(1000);
  for (const name of ['退款任务', '通知任务', '操作记录']) {
    const path = name === '退款任务' ? '/tasks/refund' : name === '通知任务' ? '/tasks/outbox' : '/audit?page=';
    const response = page.waitForResponse(r => r.url().includes('/api/v1/admin' + path));
    await page.getByRole('link', { name, exact: true }).click();
    await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
    const result = await response;
    expect(result.status()).toBe(200);
    const records = (await result.json()).data;
    await expect(page.locator('.el-table__row')).toHaveCount(records.length);
    if (name === '操作记录') expect(records.length).toBeGreaterThan(0);
  }
  await page.screenshot({ path: '../../.local/admin-audit.png', fullPage: true });
});
