import { randomUUID } from 'node:crypto';
import { test, expect, call, asSession } from './helpers';
import type { Store, Product, Cart, Checkout, Quote, Order } from '../../contracts/platform';
test('cross-campus mixed fulfillment, combined payment, pickup, refund and review', async ({
  request,
  page,
  adminSession,
  userSession,
}) => {
  const a = adminSession.token,
    u = userSession.token;
  const stores = await call<Store[]>(request, '/merchant/stores', a);
  for (const store of stores.filter((s) => ['1', '3'].includes(s.id)))
    await call(request, '/merchant/stores/' + store.id, a, 'PUT', {
      campusId: store.campusId,
      name: store.name,
      enabled: true,
      open: true,
      deliveryFeeCents: 600,
      packagingFeeCents: 100,
      hours: ['00:00-24:00'],
      phone: store.phone,
    });
  const selections = [];
  for (const storeId of ['1', '3']) {
    const products = await call<Product[]>(request, '/user/stores/' + storeId + '/products', u);
    const product = products.find(
      (p) => p.itemType === 'DISH' && !p.flavors?.length && p.remaining > 2,
    )!;
    expect(product).toBeTruthy();
    let cart = await call<Cart>(request, '/user/stores/' + storeId + '/cart', u);
    cart = await call<Cart>(request, '/user/stores/' + storeId + '/cart/items', u, 'POST', {
      cartVersion: cart.cartVersion,
      itemType: 'DISH',
      itemId: product.id,
      flavors: {},
      delta: 2,
    });
    let addressId: string | null = null;
    if (storeId === '3') {
      const buildings = await call<{ id: string }[]>(request, '/user/campuses/2/buildings', u);
      addressId = (
        await call<{ id: string }>(request, '/user/addresses', u, 'POST', {
          buildingId: buildings[0]!.id,
          room: 'E2E-301',
          consignee: '验收用户',
          phone: '13800000000',
        })
      ).id;
    }
    selections.push({
      storeId,
      cartVersion: cart.cartVersion,
      fulfillment: storeId === '1' ? 'PICKUP' : 'DELIVERY',
      addressId,
      couponId: null,
    });
  }
  const input = { stores: selections };
  const quote = await call<Quote>(request, '/user/checkouts/preview', u, 'POST', input);
  const body = { ...input, quoteHash: quote.quoteHash };
  const key = randomUUID();
  const c = await call<Checkout>(request, '/user/checkouts', u, 'POST', body, {
    'Idempotency-Key': key,
  });
  const repeated = await call<Checkout>(request, '/user/checkouts', u, 'POST', body, {
    'Idempotency-Key': key,
  });
  expect(repeated.id).toBe(c.id);
  await asSession(page, userSession, true);
  await page.goto('/app/pages/orders/index');
  const payment = page.waitForResponse((r) => r.url().endsWith('/checkouts/' + c.id + '/pay'));
  await page.getByText('整批模拟支付', { exact: true }).last().click();
  expect((await payment).ok()).toBeTruthy();
  const paid = await call<Checkout>(request, '/user/checkouts/' + c.id, u);
  expect(paid.state).toBe('PAID');
  expect(paid.orders).toHaveLength(2);
  const order = paid.orders.find((o) => o.storeId === '1')!;
  await call(request, '/merchant/orders/' + order.id + '/actions/accept', a, 'POST', {
    version: order.version,
  });
  let current = await call<Order>(request, '/user/orders/' + order.id, u);
  await call(request, '/merchant/orders/' + order.id + '/actions/ready', a, 'POST', {
    version: current.version,
  });
  current = await call<Order>(request, '/user/orders/' + order.id, u);
  expect(current.pickupCode).toMatch(/^\d{8}$/);
  await call(request, '/merchant/orders/' + order.id + '/actions/verify', a, 'POST', {
    version: current.version,
    code: current.pickupCode,
  });
  current = await call<Order>(request, '/user/orders/' + order.id, u);
  expect(current.status).toBe(5);
  const req = await call<{ id: string }>(
    request,
    '/user/orders/' + order.id + '/aftersales',
    u,
    'POST',
    {
      kind: 'PARTIAL',
      reason: '验收部分退款',
      items: [{ detailId: current.details[0]!.id, quantity: 1 }],
    },
  );
  await call(request, '/merchant/aftersales/' + req.id + '/decision', a, 'POST', {
    version: '0',
    approve: true,
    reason: '验收同意',
  });
  await expect
    .poll(
      async () => Number((await call<Order>(request, '/user/orders/' + order.id, u)).refundedCents),
      { timeout: 15000 },
    )
    .toBeGreaterThan(0);
  await call(request, '/user/orders/' + order.id + '/review', u, 'POST', {
    rating: 5,
    content: '跨校区合单验收评价',
  });
  const other = paid.orders.find((o) => o.storeId === '3')!;
  expect((await call<Order>(request, '/user/orders/' + other.id, u)).refundedCents).toBe('0');
});

test('user selects two campuses and mixed fulfillment entirely through H5 checkout', async ({
  page,
  request,
  adminSession,
  userSession,
}) => {
  const a = adminSession.token,
    u = userSession.token;
  for (const store of (await call<Store[]>(request, '/merchant/stores', a)).filter((s) =>
    ['1', '3'].includes(s.id),
  ))
    await call(request, '/merchant/stores/' + store.id, a, 'PUT', {
      campusId: store.campusId,
      name: store.name,
      enabled: true,
      open: true,
      deliveryFeeCents: 600,
      packagingFeeCents: 100,
      hours: ['00:00-24:00'],
      phone: store.phone,
    });
  const room='UI-'+randomUUID().slice(0,8);
  const buildings = await call<{ id: string }[]>(request, '/user/campuses/2/buildings', u);
  await call(request, '/user/addresses', u, 'POST', {
    buildingId: buildings[0]!.id,
    room,
    consignee: '页面验收用户',
    phone: '13800000000',
  });
  await asSession(page, userSession, true);
  await page.goto('/app/');
  async function addFood() {
    const card = page
      .locator('uni-view.card')
      .filter({ has: page.getByText('番茄鸡蛋饭', { exact: true }) });
    await card.getByText('选餐', { exact: true }).click();
    const response = page.waitForResponse(
      (r) => r.url().endsWith('/cart/items') && r.request().method() === 'POST',
    );
    await page.getByText('加入本店购物车', { exact: true }).click();
    expect((await response).ok()).toBeTruthy();
  }
  await addFood();
  await page.getByText('默认校区 ▾', { exact: true }).click();
  await page.locator('.uni-picker-item:visible').filter({ hasText: '东校区' }).click();
  await page.getByText('东区食堂 ▾', { exact: true }).waitFor();
  await expect(
    page.locator('uni-view.card').filter({ has: page.getByText('番茄鸡蛋饭', { exact: true }) }),
  ).toHaveCount(1);
  await addFood();
  await page.goto('/app/pages/cart/index');
  await expect(page.getByText('选择此店', { exact: true })).toHaveCount(2);
  await page.getByText('选择此店', { exact: true }).first().click();
  await page.getByText('选择此店', { exact: true }).first().click();
  await page.getByText('合并结算 2 家店', { exact: true }).click();
  await page.getByText('确认合并订单', { exact: true }).waitFor();
  const east = page
    .locator('uni-view.card')
    .filter({ has: page.getByText('东校区 · 东区食堂', { exact: true }) });
  await east.getByText('到店自取 ▾', { exact: true }).click();
  await page.locator('.uni-picker-item:visible').filter({ hasText: '校区配送' }).click();
  await page.getByText('选择本校区收货地址 ▾', { exact: true }).click();
  await page.locator('.uni-picker-item:visible').filter({ hasText: room }).click();
  const preview = page.waitForResponse((r) => r.url().endsWith('/checkouts/preview'));
  await page.getByText('计算报价', { exact: true }).click();
  const previewResponse = await preview;
  expect(previewResponse.ok()).toBeTruthy();
  const previewBody = previewResponse.request().postDataJSON();
  expect(previewBody.stores.map((x: { fulfillment: string }) => x.fulfillment)).toEqual([
    'PICKUP',
    'DELIVERY',
  ]);
  const submitted = page.waitForResponse(
    (r) => r.url().endsWith('/user/checkouts') && r.request().method() === 'POST',
  );
  await page.getByText('提交订单，随后模拟支付', { exact: true }).click();
  const created = await (await submitted).json();
  expect(created.code).toBe('OK');
  const c = created.data as Checkout;
  expect(c.orders).toHaveLength(2);
  await page.getByText('整批模拟支付', { exact: true }).last().click();
  await expect
    .poll(async () => (await call<Checkout>(request, '/user/checkouts/' + c.id, u)).state)
    .toBe('PAID');
});
test('merchant creates a coupon using the visible marketing form', async ({
  page,
  adminSession,
}) => {
  await asSession(page, adminSession);
  await page.goto('/admin/marketing');
  await page.getByRole('button', { name: '新增优惠券' }).click();
  const dialog = page.getByRole('dialog');
  const name = '页面券-' + Date.now();
  await dialog.getByLabel('名称', { exact: true }).fill(name);
  const response = page.waitForResponse(
    (r) => r.url().endsWith('/stores/1/coupons') && r.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: '保存', exact: true }).click();
  const sent = await response;
  expect(sent.ok()).toBeTruthy();
  expect(sent.request().postDataJSON().value).toBe(300);
  await expect(page.getByText(name, { exact: true })).toBeVisible();
});

test('favorite navigation changes the retained menu page to the favorite campus',async({page,request,userSession})=>{
  await call(request,'/user/stores/3/favorites/STORE/3',userSession.token,'POST');
  await asSession(page,userSession,true);await page.goto('/app/');await page.getByText('默认校区 ▾',{exact:true}).waitFor();await page.getByText('领券与我的收藏',{exact:true}).click();const favorite=page.locator('uni-view.card').filter({has:page.getByText('东区食堂 · 东区食堂',{exact:true})});await favorite.getByText('进入店铺',{exact:true}).click();await expect(page.getByText('东校区 ▾',{exact:true})).toBeVisible();await expect(page.getByText('东区食堂 ▾',{exact:true})).toBeVisible();
});
