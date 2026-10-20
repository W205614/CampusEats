import { randomUUID } from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { test, expect, call, password } from './helpers';
import type { Page, BrowserContext } from '@playwright/test';
import type { Session } from '../../contracts/models';
import type { Aftersale, Cart, Checkout, Order, Quote, Coupon } from '../../contracts/platform';

test('browser journey: meal selection, delivery, partial refund and platform intervention', async ({
  browser, request, adminSession,
}, testInfo) => {
  test.setTimeout(180000);
  expect(process.env.CI === 'true' || new URL(String(testInfo.project.use.baseURL)).port !== '18083').toBe(true);
  const suffix = randomUUID().slice(0, 8);
  const names = {
    campus: '旅程校区-' + suffix,
    store: '旅程餐厅-' + suffix,
    building: '旅程楼栋-' + suffix,
    product: '旅程活动餐-' + suffix,
    shopCoupon: '旅程店铺券-' + suffix,
    platformCoupon: '旅程平台券-' + suffix,
  };
  const contexts: BrowserContext[] = [];
  const pageErrors: string[] = [];
  const httpErrors: { path: string; status: number }[] = [];
  const checkpoints: { name: string; [key: string]: unknown }[] = [];
  const a = adminSession.token;
  const localTime = (milliseconds: number) =>
    new Date(milliseconds + 8 * 3600000).toISOString().slice(0, 19);
  async function pageFor(mobile = false) {
    const context = await browser.newContext({
      baseURL: testInfo.project.use.baseURL,
      viewport: mobile ? { width: 390, height: 844 } : { width: 1440, height: 1000 },
      timezoneId: 'Asia/Shanghai',
    });
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
  async function postFromUI<T>(page: Page, endpoint: string, action: () => Promise<unknown>) {
    const received = page.waitForResponse((response) =>
      new URL(response.url()).pathname === '/api/v1' + endpoint &&
      response.request().method() === 'POST',
    );
    await action();
    const response = await received;
    const body = await response.json();
    expect(response.ok(), endpoint + ': ' + body.code).toBeTruthy();
    expect(body.code).toBe('OK');
    return body.data as T;
  }
  async function choose(page: Page, label: string, option: string) {
    await page.getByText(label, { exact: false }).click();
    await expect(page.locator('.uni-picker-custom.uni-picker-toggle')).toBeVisible();
    const confirm = page.locator('.uni-picker-action-confirm:visible');
    if (await confirm.count()) {
      // Mobile H5 uses a wheel; operate its real scroll control before confirming.
      const container = page.locator('.uni-picker-custom:visible');
      const options = container.locator('.uni-picker-select .uni-picker-item');
      const target = await options.evaluateAll((items, text) =>
        items.findIndex((item) => item.textContent?.includes(text)), option);
      let current = await options.evaluateAll((items) =>
        items.findIndex((item) => item.classList.contains('selected')));
      expect(target).toBeGreaterThanOrEqual(0);
      await container.locator('uni-picker-view-column').hover();
      while (current !== target) {
        const direction = Math.sign(target - current);
        await page.mouse.wheel(0, direction * 40);
        current += direction;
        await expect(options.nth(current)).toHaveClass(/selected/);
      }
      await confirm.click();
    } else {
      await page.locator('.uni-picker-item:visible').filter({ hasText: option }).click();
    }
  }
  async function capture(page: Page, name: string) {
    const file = testInfo.outputPath(name + '.png');
    await page.screenshot({ path: file, fullPage: true });
    await testInfo.attach(name, {
      path: file, contentType: 'image/png',
    });
  }
  async function staffLogin(page: Page, username: string, secret: string) {
    await page.goto('/admin/');
    await page.getByLabel('账户', { exact: true }).fill(username);
    await page.getByLabel('密码', { exact: true }).fill(secret);
    const session = await postFromUI<Session>(page, '/admin/auth/login', () =>
      page.getByLabel('密码', { exact: true }).press('Enter'),
    );
    await expect(page.getByRole('button', { name: '退出', exact: true })).toBeVisible();
    return session;
  }
  async function firstStaffLogin(page: Page, username: string, initial: string) {
    const session = await staffLogin(page, username, initial);
    expect(session.mustChangePassword).toBe(true);
    await expect(page.getByRole('heading', { name: '修改密码', exact: true })).toBeVisible();
    const updated = 'Journey2!' + randomUUID();
    await page.getByLabel('原密码', { exact: true }).fill(initial);
    await page.getByLabel('新密码（12至128个字符）', { exact: true }).fill(updated);
    await postFromUI(page, '/admin/auth/password', () =>
      page.getByRole('button', { name: '更新并重新登录', exact: true }).click(),
    );
    await expect(page.getByRole('heading', { name: '登录工作台', exact: true })).toBeVisible();
    const renewed = await staffLogin(page, username, updated);
    expect(renewed.mustChangePassword).toBe(false);
    return renewed;
  }
  try {
    // API prepares isolated resources only; no cart, checkout, fulfillment or refund API writes.
    const campus = await call<{ id: string }>(request, '/platform/campuses', a, 'POST', {
      name: names.campus, enabled: true,
    });
    const store = await call<{ id: string }>(request, '/platform/stores', a, 'POST', {
      campusId: campus.id, name: names.store, enabled: true, open: true,
      deliveryFeeCents: 600, packagingFeeCents: 100, hours: ['00:00-24:00'], phone: '13800000000',
    });
    await call(request, '/platform/campuses/' + campus.id + '/buildings', a, 'POST', {
      name: names.building, enabled: true,
    });
    const category = await call<{ id: string }>(request,
      '/merchant/stores/' + store.id + '/categories', a, 'POST', {
        name: '旅程餐品', type: 1, sort: 0, enabled: true,
      });
    const product = await call<{ id: string }>(request,
      '/merchant/stores/' + store.id + '/products/DISH', a, 'POST', {
        name: names.product, categoryId: category.id, price: '30.00', enabled: true,
        description: '独立浏览器旅程验收', image: '/demo-images/dish.svg', flavors: [], components: [],
      });
    const date = localTime(Date.now()).slice(0, 10);
    await call(request, '/merchant/stores/' + store.id + '/quotas?date=' + date, a, 'PUT', {
      dishId: product.id, total: 20, defaultQuota: true,
    });
    const time = {
      startsAt: localTime(Date.now() - 3600000), endsAt: localTime(Date.now() + 86400000),
    };
    await call(request, '/merchant/stores/' + store.id + '/promotions', a, 'POST', {
      itemType: 'DISH', itemId: product.id, priceCents: 2400, ...time, enabled: true,
    });
    const shopTemplate = await call<{ id: string }>(request,
      '/merchant/stores/' + store.id + '/coupons', a, 'POST', {
        name: names.shopCoupon, kind: 'REDUCTION', thresholdCents: 4000, value: 800,
        capCents: null, total: 5, perUser: 1, ...time, enabled: true,
      });
    const platformTemplate = await call<{ id: string }>(request, '/platform/coupons', a, 'POST', {
      name: names.platformCoupon, kind: 'DISCOUNT', thresholdCents: 4000, value: 8000,
      capCents: 1000, total: 5, perUser: 1, ...time, enabled: true,
    });
    const staff: Record<string, { id: string; username: string; initial: string }> = {};
    for (const [key, role] of [['manager', 'SHOP_MANAGER'], ['courier', 'DELIVERER']]) {
      const username = 'journey_' + key + '_' + suffix;
      const initial = 'Journey1!' + randomUUID();
      const value = await call<{ id: string }>(request, '/platform/staff', a, 'POST', {
        username, name: key === 'manager' ? '旅程店长' : '旅程配送员', role,
        storeId: key === 'manager' ? store.id : null,
        campusId: key === 'courier' ? campus.id : null, enabled: true, password: initial,
      });
      staff[key] = { id: value.id, username, initial };
    }

    const customer = await pageFor(true);
    await customer.goto('/');
    await expect(customer).toHaveURL(/\/app\//);
    await customer.getByRole('link', { name: '登录点餐', exact: true }).click();
    const user = await postFromUI<Session>(customer, '/user/auth/demo', () =>
      customer.getByRole('button', { name: '进入演示用户 2', exact: true }).click(),
    );
    const u = user.token;
    await expect(customer.getByText('校园好店，', { exact: false })).toBeVisible();
    await choose(customer, '默认校区 ▾', names.campus);
    await expect(customer.getByText(names.store + ' ▾', { exact: true })).toBeVisible();
    const meal = customer.locator('uni-view.card').filter({
      has: customer.getByText(names.product, { exact: true }),
    });
    await expect(meal.getByText('¥24.00', { exact: true })).toBeVisible();
    await expect(meal.getByText('¥30.00', { exact: true })).toBeVisible();
    for (let count = 0; count < 2; count++) {
      await meal.getByText('选餐', { exact: true }).click();
      await postFromUI(customer, '/user/stores/' + store.id + '/cart/items', () =>
        customer.getByText('加入本店购物车', { exact: true }).click(),
      );
      await expect(customer.locator('uni-view.modal')).toHaveCount(0);
    }
    const cart = await call<Cart>(request, '/user/stores/' + store.id + '/cart', u);
    expect(cart.items).toHaveLength(1);
    expect(cart.items[0].quantity).toBe(2);
    checkpoints.push({ name: 'customer login and two meals added entirely on page', storeId: store.id });

    await customer.goto('/app/pages/addresses/index');
    await customer.getByText('新增地址', { exact: true }).click();
    await choose(customer, '校区：', names.campus);
    await choose(customer, '楼栋：', names.building);
    const addressFields = customer.locator('uni-view.modal input');
    await addressFields.nth(0).fill('JOURNEY-401');
    await addressFields.nth(1).fill('旅程验收用户');
    await addressFields.nth(2).fill('13800000000');
    await postFromUI(customer, '/user/addresses', () =>
      customer.getByText('保存地址', { exact: true }).click(),
    );
    await expect(customer.locator('uni-view.card').filter({ hasText: names.building })
      .getByText('旅程验收用户 13800000000', { exact: true })).toBeVisible();
    await customer.goto('/app/pages/account/index');
    await expect(customer.getByText(names.store + ' ▾', { exact: true })).toBeVisible();
    for (const [name, id] of [[names.shopCoupon, shopTemplate.id], [names.platformCoupon, platformTemplate.id]]) {
      const coupon = customer.locator('uni-view.card').filter({
        has: customer.getByText(name + ' · ' + (id === shopTemplate.id ? '店铺券' : '平台券'), { exact: true }),
      });
      await postFromUI(customer, '/user/coupons/' + id + '/claim', () =>
        coupon.getByText('领取', { exact: true }).click(),
      );
    }
    checkpoints.push({ name: 'delivery address created and both coupons claimed on page' });

    await customer.goto('/app/pages/cart/index');
    const chosenCart = customer.locator('uni-view.card').filter({
      has: customer.getByText(names.campus + ' · ' + names.store, { exact: true }),
    });
    await chosenCart.getByText('选择此店', { exact: true }).click();
    await customer.getByText('合并结算 1 家店', { exact: true }).click();
    await choose(customer, '到店自取 ▾', '校区配送');
    await choose(customer, '选择本校区收货地址', 'JOURNEY-401');
    await choose(customer, '店铺优惠券：', names.shopCoupon);
    await choose(customer, '平台优惠券：', names.platformCoupon);
    const quote = await postFromUI<Quote>(customer, '/user/checkouts/preview', () =>
      customer.getByText('计算报价', { exact: true }).click(),
    );
    expect(quote.totalCents).toBe('4000');
    expect(quote.stores).toHaveLength(1);
    expect(quote.stores[0]).toMatchObject({
      subtotalCents: '4800', shopDiscountCents: '800', platformDiscountCents: '800',
      deliveryCents: '600', packagingCents: '200', totalCents: '4000',
    });
    await capture(customer, '01-customer-marketing-quote');
    const checkout = await postFromUI<Checkout>(customer, '/user/checkouts', () =>
      customer.getByText('提交订单，随后模拟支付', { exact: true }).click(),
    );
    const payment = customer.locator('uni-view.card').filter({
      has: customer.getByText('待支付结算单 ' + checkout.id, { exact: true }),
    });
    await postFromUI(customer, '/user/checkouts/' + checkout.id + '/pay', () =>
      payment.getByText('整批模拟支付', { exact: true }).click(),
    );
    const paid = await call<Checkout>(request, '/user/checkouts/' + checkout.id, u);
    expect(paid.state).toBe('PAID');
    expect(paid.orders).toHaveLength(1);
    const orderId = paid.orders[0].id;
    const getOrder = () => call<Order>(request, '/user/orders/' + orderId, u);
    const order = await getOrder();
    expect(order.paidCents).toBe('4000');
    expect(order.details[0].lineTotalCents).toBe('3200');
    expect((await call<Cart>(request, '/user/stores/' + store.id + '/cart', u)).items).toHaveLength(0);
    checkpoints.push({ name: 'page checkout and payment', checkoutId: checkout.id, orderId, paidCents: '4000' });

    const manager = await pageFor();
    await firstStaffLogin(manager, staff.manager.username, staff.manager.initial);
    await manager.getByRole('link', { name: '订单履约', exact: true }).click();
    await manager.locator('.el-table__body tr').filter({ hasText: order.number })
      .getByRole('button', { name: '处理订单', exact: true }).click();
    for (const [action, label] of [['accept', '接单备餐'], ['ready', '备餐完成'], ['publish', '发布抢单任务']])
      await postFromUI(manager, '/merchant/orders/' + orderId + '/actions/' + action, () =>
        manager.getByRole('button', { name: label, exact: true }).click(),
      );
    await capture(manager, '02-manager-published-delivery');
    await manager.locator('.el-drawer__close-btn').click();
    await expect(manager.locator('.el-overlay.is-drawer')).toBeHidden();
    const courier = await pageFor();
    const courierSession = await firstStaffLogin(courier, staff.courier.username, staff.courier.initial);
    const pool = await call<{ id: string; orderId: string; phone?: string; consignee?: string; address?: string }[]>(
      request, '/courier/pool', courierSession.token,
    );
    const task = pool.find((value) => value.orderId === orderId)!;
    expect(task).toBeTruthy();
    for (const field of ['phone', 'consignee', 'address']) expect(task).not.toHaveProperty(field);
    await postFromUI(courier, '/courier/tasks/' + task.id + '/claim', () =>
      courier.locator('.el-table__body tr').filter({ hasText: names.store })
        .getByRole('button', { name: '抢单', exact: true }).click(),
    );
    await courier.locator('.el-table__body tr').filter({ hasText: order.number })
      .getByRole('button', { name: '处理订单', exact: true }).click();
    await expect(courier.getByText('旅程验收用户 13800000000', { exact: false })).toBeVisible();
    await postFromUI(courier, '/courier/orders/' + orderId + '/actions/pickup', () =>
      courier.getByRole('button', { name: '确认取餐', exact: true }).click(),
    );
    expect((await getOrder()).status).toBe(4);
    await postFromUI(courier, '/courier/orders/' + orderId + '/actions/complete', () =>
      courier.getByRole('button', { name: '确认送达', exact: true }).click(),
    );
    expect(await getOrder()).toMatchObject({ status: 5, courierId: staff.courier.id });
    await capture(courier, '03-courier-delivered');
    checkpoints.push({ name: 'staff first password changes, merchant acceptance and complete courier delivery on page' });

    await customer.goto('/app/pages/orders/index');
    const orderCard = () => customer.locator('uni-view.card').filter({
      has: customer.getByText(new RegExp(order.number)),
    });
    await orderCard().getByText('部分退款', { exact: true }).click();
    const modal = () => customer.locator('uni-view.modal');
    await modal().locator('input').fill('1');
    await modal().locator('textarea').fill('旅程验收：一份餐品部分退款');
    const fileChooser = customer.waitForEvent('filechooser');
    await modal().getByText('添加举证图片（0 / 3）', { exact: true }).click();
    const chooser = await fileChooser;
    const upload = customer.waitForResponse((response) =>
      new URL(response.url()).pathname === '/api/v1/user/orders/' + orderId + '/evidence',
    );
    await chooser.setFiles(path.resolve('../client/tooling/fixtures/qr.png'));
    expect((await upload).ok()).toBeTruthy();
    await expect(modal().getByText('添加举证图片（1 / 3）', { exact: true })).toBeVisible();
    const partialCreated = await postFromUI<{ id: string }>(customer, '/user/orders/' + orderId + '/aftersales', () =>
      modal().getByText('确认提交', { exact: true }).click(),
    );
    const partial = (await call<Aftersale[]>(request, '/user/aftersales', u))
      .find((value) => value.id === partialCreated.id)!;
    expect(partial.amountCents).toBe('1600');
    expect(partial.evidence).toHaveLength(1);
    await manager.getByRole('link', { name: '售后处理', exact: true }).click();
    await manager.getByPlaceholder('完整订单号', { exact: true }).fill(order.number);
    const merchantFilter = manager.waitForResponse(response =>
      new URL(response.url()).pathname === '/api/v1/merchant/stores/' + store.id + '/aftersales' &&
      new URL(response.url()).searchParams.get('orderNumber') === order.number);
    await manager.getByRole('button', { name: '筛选', exact: true }).click();
    expect((await merchantFilter).ok()).toBeTruthy();
    const requestCard = (page: Page, id: string) =>
      page.locator('article').filter({ hasText: '申请 ' + id + ' ·' });
    await requestCard(manager, partial.id).getByRole('button', { name: '查看举证 1', exact: true }).click();
    const evidence = manager.getByRole('dialog').locator('img');
    await expect(evidence).toBeVisible();
    await expect.poll(() => evidence.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true);
    await capture(manager, '04-merchant-private-evidence');
    await manager.getByRole('dialog').getByRole('button', { name: '关闭此对话框', exact: true }).click();
    await manager.getByPlaceholder('本次处理说明', { exact: true }).fill('同意退一份商品实付，不退配送和包装费');
    await postFromUI(manager, '/merchant/aftersales/' + partial.id + '/decision', () =>
      requestCard(manager, partial.id).getByRole('button', { name: '同意退款 / 取消', exact: true }).click(),
    );
    await expect.poll(async () => (await getOrder()).refundedCents, { timeout: 20000 }).toBe('1600');
    expect((await getOrder()).details[0].refundedQuantity).toBe(1);
    checkpoints.push({ name: 'page image evidence and merchant partial refund', amountCents: '1600' });

    await customer.getByText('重新加载', { exact: true }).click();
    await orderCard().getByText('整单退款', { exact: true }).click();
    await modal().locator('textarea').fill('旅程验收：剩余一份和未退款费用');
    const fullCreated = await postFromUI<{ id: string }>(customer, '/user/orders/' + orderId + '/aftersales', () =>
      modal().getByText('确认提交', { exact: true }).click(),
    );
    const full = (await call<Aftersale[]>(request, '/user/aftersales', u))
      .find((value) => value.id === fullCreated.id)!;
    expect(full.amountCents).toBe('2400');
    await manager.getByRole('button', { name: '刷新', exact: true }).click();
    await manager.getByPlaceholder('本次处理说明', { exact: true }).fill('旅程验收商家拒绝，验证用户申请平台介入');
    await postFromUI(manager, '/merchant/aftersales/' + full.id + '/decision', () =>
      requestCard(manager, full.id).getByRole('button', { name: '拒绝并说明', exact: true }).click(),
    );
    await customer.getByText('重新加载', { exact: true }).click();
    const progress = customer.locator('uni-view.card').filter({
      has: customer.getByText('申请 ' + full.id, { exact: true }),
    });
    await expect(progress.getByText('已拒绝', { exact: true })).toBeVisible();
    await postFromUI(customer, '/user/aftersales/' + full.id + '/appeal', () =>
      progress.getByText('申请平台介入', { exact: true }).click(),
    );
    const platform = await pageFor();
    await staffLogin(platform, 'admin', password());
    await platform.getByRole('link', { name: '售后处理', exact: true }).click();
    await platform.getByPlaceholder('完整订单号', { exact: true }).fill(order.number);
    const platformFilter = platform.waitForResponse(response =>
      new URL(response.url()).pathname === '/api/v1/platform/aftersales' &&
      new URL(response.url()).searchParams.get('orderNumber') === order.number);
    await platform.getByRole('button', { name: '筛选', exact: true }).click();
    expect((await platformFilter).ok()).toBeTruthy();
    await expect(requestCard(platform, full.id).getByText('平台介入中', { exact: true })).toBeVisible();
    await platform.getByPlaceholder('本次处理说明', { exact: true }).fill('同意剩余实付和未退配送包装费，累计不超过原支付');
    await postFromUI(platform, '/platform/aftersales/' + full.id + '/decision', () =>
      requestCard(platform, full.id).getByRole('button', { name: '同意退款 / 取消', exact: true }).click(),
    );
    await expect.poll(async () => (await getOrder()).refundedCents, { timeout: 20000 }).toBe('4000');
    const final = await getOrder();
    expect(final.status).toBe(5);
    expect(final.details[0]).toMatchObject({ refundedQuantity: 2, reservedQuantity: 0 });
    const successful = final.refunds.filter((refund) => refund.state === 'SUCCEEDED');
    expect(successful.map((refund) => refund.amountCents).sort()).toEqual(['1600', '2400']);
    expect(successful.reduce((sum, refund) => sum + Number(refund.amountCents), 0)).toBe(Number(final.paidCents));
    const wallet = await call<Coupon[]>(request, '/user/wallet', u);
    for (const name of [names.shopCoupon, names.platformCoupon])
      expect(wallet.find((coupon) => coupon.name === name)?.state).toBe('USED');
    await customer.getByText('重新加载', { exact: true }).click();
    await expect(orderCard().getByText('已退 ¥40.00', { exact: true })).toBeVisible();
    // uni-app H5 renders a separate placeholder element beside the native input.
    await customer.locator('uni-view.card').filter({
      has: customer.getByText('筛选售后', { exact: true }),
    }).locator('input[type="text"]').fill(order.number);
    const customerFilter = customer.waitForResponse(response =>
      new URL(response.url()).pathname === '/api/v1/user/aftersales' &&
      new URL(response.url()).searchParams.get('orderNumber') === order.number);
    await customer.getByText('筛选售后', { exact: true }).click();
    expect((await customerFilter).ok()).toBeTruthy();
    await expect(customer.getByText('申请 ' + full.id, { exact: true })).toBeVisible();
    checkpoints.push({ name: 'real user, merchant and platform aftersale order-number filters keep the same scoped request' });
    await capture(customer, '05-customer-completed-and-fully-refunded');
    checkpoints.push({ name: 'page refusal, user appeal and platform approval', finalRefundedCents: '4000', fulfillmentStatus: 5 });
    await orderCard().getByText('评价订单', { exact: true }).click();
    await modal().locator('textarea').fill('验收：配送完成，售后已解决');
    await postFromUI(customer, '/user/orders/' + orderId + '/review', () =>
      modal().getByText('确认提交', { exact: true }).click(),
    );
    await expect(orderCard().getByText('已评价', { exact: true })).toBeVisible();
    await expect(orderCard().getByText('评价订单', { exact: true })).toHaveCount(0);
    await customer.getByText('重新加载', { exact: true }).click();
    await expect(orderCard().getByText('已评价', { exact: true })).toBeVisible();
    const duplicateReview = await request.post('/api/v1/user/orders/' + orderId + '/review', {
      headers: { Authorization: 'Bearer ' + u }, data: { rating: 5, content: '重复评价' },
    });
    expect(duplicateReview.status()).toBe(409);
    expect((await duplicateReview.json()).code).toBe('REVIEW_EXISTS');
    await capture(customer, '06-customer-reviewed-once');
    checkpoints.push({ name: 'review submitted on page, retained after refresh, duplicate API explicitly rejected' });
    expect(pageErrors).toEqual([]);
    expect(httpErrors).toEqual([]);
  } catch (error) {
    for (let index = 0; index < contexts.length; index++) {
      const page = contexts[index].pages()[0];
      if (page) await capture(page, 'failure-context-' + index).catch(() => {});
    }
    throw error;
  } finally {
    const file = testInfo.outputPath('journey-evidence.json');
    await fs.writeFile(file, JSON.stringify({
      fixtureBoundary: 'API only creates campus/store/building/product/quota/promotion/coupon/staff. Customer login, cart, address, coupon claim, checkout, payment, staff password changes, delivery and refund transitions use pages. API reads verify snapshots.',
      checkpoints, pageErrors, httpErrors,
    }, null, 2));
    await testInfo.attach('journey-evidence', {
      path: file, contentType: 'application/json',
    });
    for (const context of contexts) await context.close();
  }
});
