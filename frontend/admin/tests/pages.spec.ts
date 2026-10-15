import { test, expect, asSession } from './helpers';
test('anonymous admin login and H5 campus selector', async ({ page }) => {
  await page.goto('/admin/');
  await expect(page.getByRole('button', { name: '登录工作台' })).toBeVisible();
  await page.goto('/app/');
  await expect(page.getByText('校园好店，')).toBeVisible();
  await expect(page.getByText('默认校区 ▾', { exact: true })).toBeVisible();
});
test('platform manages campuses, stores, catalog and marketing pages', async ({
  page,
  adminSession,
}) => {
  await asSession(page, adminSession);
  await page.goto('/admin/');
  await expect(page.getByRole('heading', { name: '校区与店铺' })).toBeVisible();
  const switcher=page.locator('.topbar .el-select__wrapper');await expect.poll(async()=>Math.round((await switcher.boundingBox())?.width||0)).toBeGreaterThan(200);
  await expect(page.getByText('东校区', { exact: true }).first()).toBeVisible();
  await page.getByRole('button', { name: '新增店铺' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.getByRole('link', { name: '商品与配额' }).click();
  await expect(page.getByRole('heading', { name: '商品与每日供餐' })).toBeVisible();
  await page.getByRole('button', { name: '新增商品' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.getByRole('link', { name: '营销管理' }).click();
  await expect(page.getByRole('heading', { name: '营销管理' })).toBeVisible();
  await page.getByRole('button', { name: '新增优惠券' }).click();
  await expect(page.getByText('优惠券规则', { exact: true })).toBeVisible();
});
test('reports, employees, audit and aftersale navigation', async ({ page, adminSession }) => {
  await asSession(page, adminSession);
  for (const [path, title] of [
    ['/reports', '经营统计'],
    ['/operations/staff', '员工与角色'],
    ['/operations/aftersales', '售后与平台介入'],
    ['/operations/audit', '操作记录'],
  ]) {
    await page.goto('/admin' + path);
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
  }
});
test('H5 wallet, addresses, cart and order pages', async ({ page, userSession }) => {
  await asSession(page, userSession, true);
  for (const [path, title] of [
    ['/app/pages/account/index', '我的校园生活'],
    ['/app/pages/addresses/index', '校园收货地址'],
    ['/app/pages/cart/index', '我的购物车'],
    ['/app/pages/orders/index', '我的订单'],
  ]) {
    await page.goto(path);
    await expect(page.getByText(title, { exact: true }).last()).toBeVisible();
  }
});

test('platform switches the active store before catalog operations',async({page,adminSession})=>{
  await asSession(page,adminSession);await page.goto('/admin/');await page.locator('.topbar .el-select').click();await page.getByRole('option',{name:'东校区 / 东区食堂',exact:true}).click();await expect(page.locator('.topbar')).toContainText('东校区 / 东区食堂');const response=page.waitForResponse(r=>r.url().endsWith('/merchant/stores/3/products'));await page.getByRole('link',{name:'商品与配额'}).click();expect((await response).ok()).toBeTruthy();await expect(page.getByText('番茄鸡蛋饭',{exact:true}).first()).toBeVisible();
});
