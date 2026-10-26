import { randomUUID } from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import type { BrowserContext, Page, Locator } from '@playwright/test';
import { test, expect, call, asSession } from './helpers';
import type { Session } from '../../contracts/models';
import type { Aftersale, Cart, Checkout, Coupon, Order, Quote } from '../../contracts/platform';

test('browser cross-campus mixed fulfillment preserves coupon cents, repeated partial refunds and role isolation', async ({
  browser, request, adminSession, userSession,
}, testInfo) => {
  test.setTimeout(240000);
  // This test creates actual transactions; never accidentally target the daily deployment.
  expect(process.env.CI === 'true' || new URL(String(testInfo.project.use.baseURL)).port !== '18083').toBe(true);
  await test.step('Respect the real 60-second IP login window after preceding browser tests', async () => {
    await new Promise((resolve) => setTimeout(resolve, 60000));
  });
  const suffix = randomUUID().slice(0, 8);
  const contexts: BrowserContext[] = [];
  const pageErrors: string[] = [];
  const httpErrors: { path: string; status: number }[] = [];
  const checkpoints: Record<string, unknown>[] = [];
  const isolation: Record<string, unknown>[] = [];
  const token = adminSession.token;
  const localTime = (ms: number) => new Date(ms + 8 * 3600000).toISOString().slice(0, 19);
  const time = { startsAt: localTime(Date.now() - 3600000), endsAt: localTime(Date.now() + 86400000) };
  const names = {
    campusA: '合单北校-' + suffix, campusB: '合单南校-' + suffix,
    storeA: '北校配送店-' + suffix, storeB: '南校自取店-' + suffix,
    mainA: '尾差活动餐-' + suffix, sideA: '尾差配菜-' + suffix, mainB: '南校自取餐-' + suffix,
    shopA: '北店301分券-' + suffix, shopB: '南店113分券-' + suffix, platform: '平台778分券-' + suffix,
    building: '合单验收楼-' + suffix,
  };
  async function newPage(mobile = false) {
    const context = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
      viewport: mobile ? { width: 390, height: 844 } : { width: 1440, height: 1000 }, timezoneId: 'Asia/Shanghai' });
    contexts.push(context);
    const page = await context.newPage();
    page.on('pageerror', (error) => pageErrors.push(error.message));
    page.on('response', (response) => {
      const url = new URL(response.url());
      if (response.status() >= 400 && url.pathname.startsWith('/api/v1/'))
        httpErrors.push({ path: url.pathname, status: response.status() });
    });
    return page;
  }
  async function postUI<T>(page: Page, endpoint: string, action: () => Promise<unknown>) {
    const pending = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/v1' + endpoint && r.request().method() === 'POST');
    await action();
    const response = await pending;
    const body = await response.json();
    expect(response.ok(), endpoint + ': ' + body.code).toBe(true);
    expect(body.code).toBe('OK');
    return body.data as T;
  }
  async function choose(page: Page, trigger: Locator, option: string) {
    await trigger.click();
    const container = page.locator('.uni-picker-custom.uni-picker-toggle');
    await expect(container).toBeVisible();
    const confirm = container.locator('.uni-picker-action-confirm:visible');
    if (await confirm.count()) {
      const options = container.locator('.uni-picker-select .uni-picker-item');
      const target = await options.evaluateAll((items, text) => items.findIndex((item) => item.textContent?.includes(text)), option);
      let current = await options.evaluateAll((items) => items.findIndex((item) => item.classList.contains('selected')));
      expect(target).toBeGreaterThanOrEqual(0);
      await container.locator('uni-picker-view-column').hover();
      while (current !== target) {
        const direction = Math.sign(target - current);
        await page.mouse.wheel(0, direction * 40);
        current += direction;
        await expect(options.nth(current)).toHaveClass(/selected/);
      }
      await confirm.click();
    } else await container.locator('.uni-picker-item:visible').filter({ hasText: option }).click();
  }
  async function capture(page: Page, name: string) {
    const file = testInfo.outputPath(name + '.png');
    await page.screenshot({ path: file, fullPage: true });
    await testInfo.attach(name, { path: file, contentType: 'image/png' });
  }
  async function loadCouponStores(page: Page) {
    const more = page.getByText('加载更多领券店铺', { exact: true });
    let pages = 0;
    while (await more.count()) {
      // uni-app renders the button as a custom element; wait for its real disabled class.
      await expect(more).not.toHaveClass(/uni-button-disabled/);
      const pending = page.waitForResponse(response => {
        const url = new URL(response.url());
        return url.pathname === '/api/v1/user/stores/page' && url.searchParams.has('cursor');
      });
      await more.click();
      const response = await pending;
      expect(response.ok()).toBe(true);
      const result = await response.json();
      expect(result.code).toBe('OK');
      await response.finished();
      if (result.data.hasMore) await expect(more).not.toHaveClass(/uni-button-disabled/);
      else await expect(more).toHaveCount(0);
      if (++pages > 20) throw new Error('Coupon store pagination did not terminate');
    }
  }
  async function staffPage(username: string, initial: string) {
    const page = await newPage();
    const login = async (secret: string) => {
      await page.goto('/admin/');
      await page.getByLabel('账户', { exact: true }).fill(username);
      await page.getByLabel('密码', { exact: true }).fill(secret);
      return postUI<Session>(page, '/admin/auth/login', () => page.getByLabel('密码', { exact: true }).press('Enter'));
    };
    expect((await login(initial)).mustChangePassword).toBe(true);
    await expect(page.getByRole('heading', { name: '修改密码', exact: true })).toBeVisible();
    const updated = 'Mixed2!' + randomUUID();
    await page.getByLabel('原密码', { exact: true }).fill(initial);
    await page.getByLabel('新密码（12至128个字符）', { exact: true }).fill(updated);
    await postUI(page, '/admin/auth/password', () => page.getByRole('button', { name: '更新并重新登录', exact: true }).click());
    await expect(page.getByRole('heading', { name: '登录工作台', exact: true })).toBeVisible();
    const session = await login(updated);
    expect(session.mustChangePassword).toBe(false);
    await expect(page.getByRole('button', { name: '退出', exact: true })).toBeVisible();
    return { page, session };
  }
  async function denied(page: Page, session: Session, endpoint: string, status: number, method = 'GET', data?: unknown) {
    // Browser-context requests exercise the real authorization filter; deliberate denials are recorded separately.
    const response = await page.context().request.fetch('/api/v1' + endpoint, {
      method, data, headers: { Authorization: 'Bearer ' + session.token },
    });
    expect(response.status(), endpoint).toBe(status);
    isolation.push({ endpoint, method, expectedStatus: status, actualStatus: response.status() });
  }
  const orderCard = (page: Page, number: string) => page.locator('uni-view.card').filter({ has: page.getByText(new RegExp(number)) });
  const requestCard = (page: Page, id: string) => page.locator('article').filter({ hasText: '申请 ' + id + ' ·' });
  try {
    // Only isolated catalog/identity fixtures use API writes. All customer and fulfillment transitions below use pages.
    const campuses: { id: string }[] = [];
    const stores: { id: string }[] = [];
    for (const [index, name] of [names.campusA, names.campusB].entries()) {
      const campus = await call<{ id: string }>(request, '/platform/campuses', token, 'POST', { name, enabled: true });
      campuses.push(campus);
      stores.push(await call<{ id: string }>(request, '/platform/stores', token, 'POST', {
        campusId: campus.id, name: index ? names.storeB : names.storeA, enabled: true, open: true,
        deliveryFeeCents: index ? 999 : 601, packagingFeeCents: index ? 41 : 37, hours: ['00:00-24:00'], phone: '13800000000',
      }));
    }
    const [storeA, storeB] = stores;
    await call(request, '/platform/campuses/' + campuses[0].id + '/buildings', token, 'POST', { name: names.building, enabled: true });
    const products: { id: string; name: string; storeId: string; quantity: number }[] = [];
    for (const [index, store] of stores.entries()) {
      const category = await call<{ id: string }>(request, '/merchant/stores/' + store.id + '/categories', token, 'POST', {
        name: '合单验收餐品', type: 1, sort: 0, enabled: true,
      });
      const choices = index ? [[names.mainB, '12.07', 2]] : [[names.mainA, '10.01', 3], [names.sideA, '7.03', 1]];
      for (const [name, price, quantity] of choices) {
        const product = await call<{ id: string }>(request, '/merchant/stores/' + store.id + '/products/DISH', token, 'POST', {
          name, categoryId: category.id, price, enabled: true, description: '独立跨校区合单验收', image: '/demo-images/dish.svg', flavors: [], components: [],
        });
        products.push({ id: product.id, name: String(name), storeId: store.id, quantity: Number(quantity) });
        await call(request, '/merchant/stores/' + store.id + '/quotas?date=' + localTime(Date.now()).slice(0, 10), token, 'PUT', {
          dishId: product.id, total: 30, defaultQuota: true,
        });
      }
    }
    await call(request, '/merchant/stores/' + storeA.id + '/promotions', token, 'POST', {
      itemType: 'DISH', itemId: products[0].id, priceCents: 999, ...time, enabled: true,
    });
    const coupons: { id: string; name: string; storeId?: string }[] = [];
    for (const [index, store] of stores.entries()) {
      const name = index ? names.shopB : names.shopA;
      const coupon = await call<{ id: string }>(request, '/merchant/stores/' + store.id + '/coupons', token, 'POST', {
        name, kind: 'REDUCTION', thresholdCents: index ? 2400 : 3700, value: index ? 113 : 301,
        capCents: null, total: 5, perUser: 1, ...time, enabled: true,
      });
      coupons.push({ ...coupon, name, storeId: store.id });
    }
    const platform = await call<{ id: string }>(request, '/platform/coupons', token, 'POST', {
      name: names.platform, kind: 'REDUCTION', thresholdCents: 5700, value: 778,
      capCents: null, total: 5, perUser: 1, ...time, enabled: true,
    });
    coupons.push({ ...platform, name: names.platform });
    const staff: Record<string, { id: string; username: string; initial: string }> = {};
    for (const [key, role, index] of [['managerA', 'SHOP_MANAGER', 0], ['managerB', 'SHOP_MANAGER', 1], ['courierA', 'DELIVERER', 0], ['courierB', 'DELIVERER', 1]] as const) {
      const initial = 'Mixed1!' + randomUUID();
      const username = 'mixed_' + key + '_' + suffix;
      const created = await call<{ id: string }>(request, '/platform/staff', token, 'POST', {
        username, name: key, role, password: initial, enabled: true,
        storeId: role === 'SHOP_MANAGER' ? stores[index].id : null,
        campusId: role === 'DELIVERER' ? campuses[index].id : null,
      });
      staff[key] = { ...created, username, initial };
    }

    const customer = await newPage(true);
    await customer.goto('/');
    await customer.getByRole('link', { name: '登录点餐', exact: true }).click();
    const user = await postUI<Session>(customer, '/user/auth/demo', () => customer.getByRole('button', { name: '进入演示用户 2', exact: true }).click());
    const u = user.token;
    await expect(customer.getByText('校园好店，', { exact: false })).toBeVisible();
    let currentCampus = '默认校区';
    for (const [index, campusName] of [names.campusA, names.campusB].entries()) {
      await choose(customer, customer.getByText(currentCampus + ' ▾', { exact: true }), campusName);
      currentCampus = campusName;
      await expect(customer.getByText((index ? names.storeB : names.storeA) + ' ▾', { exact: true })).toBeVisible();
      for (const product of products.filter((p) => p.storeId === stores[index].id)) {
        const meal = customer.locator('uni-view.card').filter({ has: customer.getByText(product.name, { exact: true }) });
        if (product.name === names.mainA) {
          await expect(meal.getByText('¥9.99', { exact: true })).toBeVisible();
          await expect(meal.getByText('¥10.01', { exact: true })).toBeVisible();
        }
        for (let count = 0; count < product.quantity; count++) {
          await meal.getByText('选餐', { exact: true }).click();
          await postUI(customer, '/user/stores/' + product.storeId + '/cart/items', () => customer.getByText('加入本店购物车', { exact: true }).click());
          await expect(customer.locator('uni-view.modal')).toHaveCount(0);
        }
      }
    }
    for (const store of stores) {
      const cart = await call<Cart>(request, '/user/stores/' + store.id + '/cart', u);
      expect(cart.items.map((i) => [i.name, i.quantity])).toEqual(products.filter((p) => p.storeId === store.id).map((p) => [p.name, p.quantity]));
    }
    await customer.goto('/app/pages/addresses/index');
    await customer.getByText('新增地址', { exact: true }).click();
    await choose(customer, customer.getByText('校区：', { exact: false }), names.campusA);
    await choose(customer, customer.getByText('楼栋：', { exact: false }), names.building);
    const fields = customer.locator('uni-view.modal input');
    await fields.nth(0).fill('MIXED-401'); await fields.nth(1).fill('合单验收用户'); await fields.nth(2).fill('13800000000');
    await postUI(customer, '/user/addresses', () => customer.getByText('保存地址', { exact: true }).click());
    await expect(customer.locator('uni-view.modal')).toHaveCount(0);
    await customer.goto('/app/pages/account/index');
    await expect(customer.getByText(names.storeB + ' ▾', { exact: true })).toBeVisible();
    for (const coupon of coupons) {
      await loadCouponStores(customer);
      if (coupon.storeId === storeA.id)
        await choose(customer, customer.getByText(names.storeB + ' ▾', { exact: true }), names.storeA);
      if (coupon.storeId === storeB.id)
        await choose(customer, customer.getByText(names.storeA + ' ▾', { exact: true }), names.storeB);
      const card = customer.locator('uni-view.card').filter({ has: customer.getByText(coupon.name + ' · ' + (coupon.storeId ? '店铺券' : '平台券'), { exact: true }) });
      // Claim refreshes wallet and coupons while preserving the loaded store sequence.
      const currentCouponStore = coupon.storeId === storeA.id ? storeA : storeB;
      // Wait for the action's coupon refresh before selecting the next store.
      const reloaded = customer.waitForResponse(response =>
        new URL(response.url()).pathname === '/api/v1/user/stores/' + currentCouponStore.id + '/coupons/page');
      await postUI(customer, '/user/coupons/' + coupon.id + '/claim', () => card.getByText('领取', { exact: true }).click());
      await (await reloaded).finished();
      await expect(customer.getByText((currentCouponStore.id === storeA.id ? names.storeA : names.storeB) + ' ▾', { exact: true })).toBeVisible();
    }
    await customer.goto('/app/pages/cart/index');
    for (const [index, store] of stores.entries()) {
      const cart = customer.locator('uni-view.card').filter({ has: customer.getByText((index ? names.campusB + ' · ' + names.storeB : names.campusA + ' · ' + names.storeA), { exact: true }) });
      await cart.getByText('选择此店', { exact: true }).click();
    }
    await customer.getByText('合并结算 2 家店', { exact: true }).click();
    const delivery = customer.locator('uni-view.card').filter({ has: customer.getByText(names.campusA + ' · ' + names.storeA, { exact: true }) });
    const pickup = customer.locator('uni-view.card').filter({ has: customer.getByText(names.campusB + ' · ' + names.storeB, { exact: true }) });
    await choose(customer, delivery.getByText('到店自取 ▾', { exact: true }), '校区配送');
    await choose(customer, delivery.getByText('选择本校区收货地址', { exact: false }), 'MIXED-401');
    await expect(pickup.getByText('到店自取 ▾', { exact: true })).toBeVisible();
    await choose(customer, delivery.getByText('店铺优惠券：', { exact: false }), names.shopA);
    await choose(customer, pickup.getByText('店铺优惠券：', { exact: false }), names.shopB);
    await choose(customer, customer.getByText('平台优惠券：', { exact: false }), names.platform);
    const quote = await postUI<Quote>(customer, '/user/checkouts/preview', () => customer.getByText('计算报价', { exact: true }).click());
    // Independently calculated literal cents: A shop 301 -> line discounts 244/57;
    // platform 778 * [2753,646,2301] / 5700 -> floors [375,88,314], largest remainder gives A main +1.
    expect(quote.totalCents).toBe('5753');
    expect(quote.stores).toHaveLength(2);
    expect(quote.stores[0]).toMatchObject({ storeId: storeA.id, fulfillment: 'DELIVERY', subtotalCents: '3700', shopDiscountCents: '301', platformDiscountCents: '464', deliveryCents: '601', packagingCents: '148', totalCents: '3684' });
    expect(quote.stores[1]).toMatchObject({ storeId: storeB.id, fulfillment: 'PICKUP', subtotalCents: '2414', shopDiscountCents: '113', platformDiscountCents: '314', deliveryCents: '0', packagingCents: '82', totalCents: '2069' });
    expect(quote.stores.flatMap((s) => s.items.map((i) => i.paidCents))).toEqual(['2377', '558', '1987']);
    expect(quote.stores.reduce((sum, s) => sum + Number(s.totalCents), 0)).toBe(5753);
    expect(quote.stores.reduce((sum, s) => sum + Number(s.platformDiscountCents), 0)).toBe(778);
    for (const part of quote.stores)
      expect(part.items.reduce((sum, i) => sum + Number(i.paidCents), 0) + Number(part.deliveryCents) + Number(part.packagingCents)).toBe(Number(part.totalCents));
    await capture(customer, '01-cross-campus-mixed-quote');
    checkpoints.push({ name: 'three lines, two shop coupons, platform largest remainder allocation', quote });
    const checkout = await postUI<Checkout>(customer, '/user/checkouts', () => customer.getByText('提交订单，随后模拟支付', { exact: true }).click());
    const payment = customer.locator('uni-view.card').filter({ has: customer.getByText('待支付结算单 ' + checkout.id, { exact: true }) });
    await postUI(customer, '/user/checkouts/' + checkout.id + '/pay', () => payment.getByText('整批模拟支付', { exact: true }).click());
    const paid = await call<Checkout>(request, '/user/checkouts/' + checkout.id, u);
    expect(paid).toMatchObject({ state: 'PAID', totalCents: '5753' }); expect(paid.orders).toHaveLength(2);
    const aId = paid.orders.find((o) => o.storeId === storeA.id)!.id;
    const bId = paid.orders.find((o) => o.storeId === storeB.id)!.id;
    const getA = () => call<Order>(request, '/user/orders/' + aId, u);
    const getB = () => call<Order>(request, '/user/orders/' + bId, u);
    const aOrder = await getA(), bOrder = await getB();
    expect(aOrder.paidCents).toBe('3684'); expect(bOrder.paidCents).toBe('2069');
    expect(aOrder.details.map((d) => d.lineTotalCents)).toEqual(['2377', '558']);
    expect(bOrder.details[0].lineTotalCents).toBe('1987');
    for (const store of stores) expect((await call<Cart>(request, '/user/stores/' + store.id + '/cart', u)).items).toHaveLength(0);

    const managerA = await staffPage(staff.managerA.username, staff.managerA.initial);
    const managerB = await staffPage(staff.managerB.username, staff.managerB.initial);
    for (const [manager, order] of [[managerA, aOrder], [managerB, bOrder]] as const) {
      await manager.page.getByRole('link', { name: '订单履约', exact: true }).click();
      const table = manager.page.locator('.el-table__body tr');
      await expect(table.filter({ hasText: order.number })).toHaveCount(1);
      await expect(table.filter({ hasText: order.id === aId ? bOrder.number : aOrder.number })).toHaveCount(0);
      await table.filter({ hasText: order.number }).getByRole('button', { name: '处理订单', exact: true }).click();
      for (const [action, label] of [['accept', '接单备餐'], ['ready', '备餐完成']])
        await postUI(manager.page, '/merchant/orders/' + order.id + '/actions/' + action, () => manager.page.getByRole('button', { name: label, exact: true }).click());
    }
    await denied(managerA.page, managerA.session, '/merchant/orders/' + bId, 403);
    await denied(managerB.page, managerB.session, '/merchant/orders/' + aId, 403);
    await denied(managerA.page, managerA.session, '/merchant/stores/' + storeB.id + '/orders', 403);
    const today = localTime(Date.now()).slice(0, 10);
    await denied(managerA.page, managerA.session, '/merchant/reports?storeId=' + storeB.id + '&begin=' + today + '&end=' + today, 403);
    await denied(managerA.page, managerA.session, '/merchant/reports/export?storeId=' + storeB.id + '&begin=' + today + '&end=' + today, 403);
    await postUI(managerA.page, '/merchant/orders/' + aId + '/actions/publish', () => managerA.page.getByRole('button', { name: '发布抢单任务', exact: true }).click());

    const courierA = await staffPage(staff.courierA.username, staff.courierA.initial);
    const courierB = await staffPage(staff.courierB.username, staff.courierB.initial);
    const pool = await call<{ id: string; orderId: string; version: string }[]>(request, '/courier/pool', courierA.session.token);
    const task = pool.find((t) => t.orderId === aId)!; expect(task).toBeTruthy();
    for (const key of ['phone', 'consignee', 'address']) expect(task).not.toHaveProperty(key);
    expect((await call<{ id: string }[]>(request, '/courier/pool', courierB.session.token)).some((t) => t.id === task.id)).toBe(false);
    await expect(courierB.page.locator('.el-table__body tr').filter({ hasText: names.storeA })).toHaveCount(0);
    await denied(courierB.page, courierB.session, '/courier/tasks/' + task.id + '/claim', 403, 'POST', { version: task.version });
    await denied(courierB.page, courierB.session, '/courier/orders/' + aId, 404);
    await denied(courierA.page, courierA.session, '/courier/orders/' + aId, 404);
    await postUI(courierA.page, '/courier/tasks/' + task.id + '/claim', () => courierA.page.locator('.el-table__body tr').filter({ hasText: names.storeA }).getByRole('button', { name: '抢单', exact: true }).click());
    await courierA.page.locator('.el-table__body tr').filter({ hasText: aOrder.number }).getByRole('button', { name: '处理订单', exact: true }).click();
    await expect(courierA.page.getByText('合单验收用户 13800000000', { exact: false })).toBeVisible();
    for (const [action, label] of [['pickup', '确认取餐'], ['complete', '确认送达']])
      await postUI(courierA.page, '/courier/orders/' + aId + '/actions/' + action, () => courierA.page.getByRole('button', { name: label, exact: true }).click());
    expect(await getA()).toMatchObject({ status: 5, courierId: staff.courierA.id });
    expect((await getB()).status).toBe(3);
    await capture(courierA.page, '02-north-campus-delivered');
    await customer.goto('/app/pages/orders/index');
    const codeText = await orderCard(customer, bOrder.number).getByText('取餐码：', { exact: false }).innerText();
    const code = codeText.match(/\d{8}/)?.[0]; expect(code).toMatch(/^\d{8}$/);
    await managerB.page.getByPlaceholder('输入用户出示的8位核销码', { exact: true }).fill(code!);
    await postUI(managerB.page, '/merchant/orders/' + bId + '/actions/verify', () => managerB.page.getByRole('button', { name: '核销', exact: true }).click());
    expect((await getB()).status).toBe(5);
    await capture(managerB.page, '03-south-campus-pickup-completed');
    await managerA.page.locator('.el-drawer__close-btn').click();
    await managerB.page.locator('.el-drawer__close-btn').click();
    checkpoints.push({ name: 'same checkout independently delivered and pickup verified on pages', checkoutId: checkout.id, orders: [aId, bId] });
    await denied(customer, user, '/user/orders/' + aId + '/aftersales', 400, 'POST', {
      kind: 'PARTIAL', reason: '使用另一子单明细应拒绝', items: [{ detailId: bOrder.details[0].id, quantity: 1 }],
    });
    for (const order of [await getA(), await getB()]) {
      expect(order.refundedCents).toBe('0');
      expect(order.aftersales).toHaveLength(0);
      for (const detail of order.details) expect(detail).toMatchObject({ refundedQuantity: 0, reservedQuantity: 0 });
    }

    async function applyRefund(order: Order, kind: 'PARTIAL' | 'FULL', evidence = false) {
      await customer.goto('/app/pages/orders/index');
      await orderCard(customer, order.number).getByText(kind === 'PARTIAL' ? '部分退款' : '整单退款', { exact: true }).click();
      const modal = customer.locator('uni-view.modal');
      if (kind === 'PARTIAL') await modal.locator('input').first().fill('1');
      await modal.locator('textarea').fill('跨校区合单验收：原实付分摊退款');
      if (evidence) {
        const chooserEvent = customer.waitForEvent('filechooser');
        await modal.getByText('添加举证图片（0 / 3）', { exact: true }).click();
        const chooser = await chooserEvent;
        const uploaded = customer.waitForResponse((r) => new URL(r.url()).pathname === '/api/v1/user/orders/' + order.id + '/evidence');
        await chooser.setFiles(path.resolve('../client/tooling/fixtures/qr.png'));
        expect((await uploaded).ok()).toBe(true);
        await expect(modal.getByText('添加举证图片（1 / 3）', { exact: true })).toBeVisible();
      }
      const created = await postUI<{ id: string }>(customer, '/user/orders/' + order.id + '/aftersales', () => modal.getByText('确认提交', { exact: true }).click());
      return (await call<Aftersale[]>(request, '/user/aftersales', u)).find((r) => r.id === created.id)!;
    }
    async function approve(manager: { page: Page }, sale: Aftersale) {
      await manager.page.getByRole('link', { name: '售后处理', exact: true }).click();
      await manager.page.getByRole('button', { name: '刷新', exact: true }).click();
      await manager.page.getByPlaceholder('本次处理说明', { exact: true }).fill('按原商品实付退款，不重新分摊合单优惠');
      await postUI(manager.page, '/merchant/aftersales/' + sale.id + '/decision', () => requestCard(manager.page, sale.id).getByRole('button', { name: '同意退款 / 取消', exact: true }).click());
    }
    const bPartial = await applyRefund(bOrder, 'PARTIAL', true);
    expect(bPartial.amountCents).toBe('994'); expect(bPartial.evidence).toHaveLength(1);
    await denied(managerA.page, managerA.session, '/merchant/stores/' + storeB.id + '/aftersales', 403);
    await denied(managerA.page, managerA.session, '/merchant/aftersales/' + bPartial.id + '/decision', 403, 'POST', { version: bPartial.version, approve: true, reason: '跨店越权应拒绝' });
    await denied(managerA.page, managerA.session, '/evidence/' + bPartial.evidence[0].id, 403);
    await denied(courierA.page, courierA.session, '/evidence/' + bPartial.evidence[0].id, 403);
    await managerB.page.getByRole('link', { name: '售后处理', exact: true }).click();
    await requestCard(managerB.page, bPartial.id).getByRole('button', { name: '查看举证 1', exact: true }).click();
    const img = managerB.page.getByRole('dialog').locator('img');
    await expect(img).toBeVisible();
    await expect.poll(() => img.evaluate((image: HTMLImageElement) => image.complete && image.naturalWidth > 0)).toBe(true);
    await capture(managerB.page, '04-authorized-south-store-evidence');
    await managerB.page.getByRole('dialog').getByRole('button', { name: '关闭此对话框', exact: true }).click();
    await approve(managerB, bPartial);
    await expect.poll(async () => (await getB()).refundedCents, { timeout: 20000 }).toBe('994');
    const bSnapshot = await getB();
    const aFirst = await applyRefund(aOrder, 'PARTIAL'); expect(aFirst.amountCents).toBe('793');
    await approve(managerA, aFirst);
    await expect.poll(async () => (await getA()).refundedCents, { timeout: 20000 }).toBe('793');
    expect((await getA()).details[0]).toMatchObject({ refundedQuantity: 1, reservedQuantity: 0 });
    const aSecond = await applyRefund(aOrder, 'PARTIAL'); expect(aSecond.amountCents).toBe('792');
    await approve(managerA, aSecond);
    await expect.poll(async () => (await getA()).refundedCents, { timeout: 20000 }).toBe('1585');
    expect((await getA()).details[0]).toMatchObject({ refundedQuantity: 2, reservedQuantity: 0 });
    const aFull = await applyRefund(aOrder, 'FULL'); expect(aFull.amountCents).toBe('2099');
    await approve(managerA, aFull);
    await expect.poll(async () => (await getA()).refundedCents, { timeout: 20000 }).toBe('3684');
    const finalA = await getA(); const finalB = await getB();
    expect(finalA.status).toBe(5);
    expect(finalA.details.map((d) => [d.lineTotalCents, d.refundedQuantity, d.reservedQuantity])).toEqual([['2377', 3, 0], ['558', 1, 0]]);
    expect(finalA.refunds.filter((r) => r.state === 'SUCCEEDED').map((r) => r.amountCents).sort()).toEqual(['2099', '792', '793']);
    expect(finalA.refunds.reduce((sum, r) => sum + Number(r.amountCents), 0)).toBe(3684);
    expect(finalB).toEqual(bSnapshot);
    const wallet = await call<Coupon[]>(request, '/user/wallet', u);
    for (const coupon of coupons) expect(wallet.find((c) => c.name === coupon.name)?.state).toBe('USED');
    const other = await newPage();
    await asSession(other, userSession, true);
    await other.goto('/app/pages/orders/index');
    const otherUser = userSession;
    await denied(other, otherUser, '/user/checkouts/' + checkout.id, 404);
    await denied(other, otherUser, '/user/orders/' + aId, 404);
    await denied(other, otherUser, '/evidence/' + bPartial.evidence[0].id, 404);
    await customer.goto('/app/pages/orders/index');
    await expect(orderCard(customer, aOrder.number).getByText('已退 ¥36.84', { exact: true })).toBeVisible();
    await expect(orderCard(customer, bOrder.number).getByText('已退 ¥9.94', { exact: true })).toBeVisible();
    await capture(customer, '05-both-orders-refund-snapshots');
    checkpoints.push({ name: 'two partial refunds retain cent tail, final refund includes remaining fees, other store unchanged', refundsA: ['793', '792', '2099'], refundedA: finalA.refundedCents, refundedB: finalB.refundedCents, isolationChecks: isolation.length });
    expect(pageErrors).toEqual([]); expect(httpErrors).toEqual([]);
  } catch (error) {
    for (let index = 0; index < contexts.length; index++) {
      const page = contexts[index].pages()[0];
      if (page) await capture(page, 'failure-context-' + index).catch(() => {});
    }
    throw error;
  } finally {
    const file = testInfo.outputPath('mixed-fulfillment-evidence.json');
    await fs.writeFile(file, JSON.stringify({ fixtureBoundary: 'API prepares independent campus/store/building/catalog/quota/activity/coupon/staff only. User login, cart additions, address, coupon claims, checkout/payment, employee password changes, merchant acceptance, courier claim/delivery, pickup code verification, image upload, refund requests and approvals use real pages. API reads verify money; browser-context requests verify deliberate authorization denials.', checkpoints, isolation, pageErrors, httpErrors }, null, 2));
    await testInfo.attach('mixed-fulfillment-evidence', { path: file, contentType: 'application/json' });
    for (const context of contexts) await context.close();
  }
});
