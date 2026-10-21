import { randomUUID } from 'node:crypto';
import path from 'node:path';
import { test, expect, call, asSession } from './helpers';
import type { Session } from '../../contracts/models';
import type { Store, Product, Cart, Quote, Checkout, Order, Aftersale } from '../../contracts/platform';

// API prepares an isolated order and staff account. The changed storage paths
// are exercised through the real merchant and customer upload controls.
test('image storage: merchant picture and private customer evidence use real pages', async ({
  browser, request, adminSession, userSession,
}, testInfo) => {
  expect(process.env.CI === 'true' || new URL(String(testInfo.project.use.baseURL)).port !== '18083').toBe(true);
  const suffix = randomUUID().slice(0, 8), a = adminSession.token, u = userSession.token;
  const initial = 'Upload1!' + randomUUID(), updated = 'Upload2!' + randomUUID();
  const username = 'image-' + suffix;
  await call(request, '/platform/staff', a, 'POST', {
    username, name: '图片验收店长', role: 'SHOP_MANAGER', storeId: '1', campusId: null,
    enabled: true, password: initial,
  });
  let manager = await call<Session>(request, '/admin/auth/login', '', 'POST', { username, password: initial });
  await call(request, '/admin/auth/password', manager.token, 'POST', { oldPassword: initial, password: updated });
  manager = await call<Session>(request, '/admin/auth/login', '', 'POST', { username, password: updated });
  const store = (await call<Store[]>(request, '/merchant/stores', a)).find(s => s.id === '1')!;
  await call(request, '/merchant/stores/1', a, 'PUT', {
    campusId: store.campusId, name: store.name, enabled: true, open: true,
    deliveryFeeCents: 600, packagingFeeCents: 100, hours: ['00:00-24:00'], phone: store.phone,
  });
  const product = (await call<Product[]>(request, '/user/stores/1/products', u))
    .find(p => p.itemType === 'DISH' && !p.flavors?.length && p.remaining > 0)!;
  expect(product).toBeTruthy();
  const managerContext = await browser.newContext({ baseURL: String(testInfo.project.use.baseURL) });
  const customerContext = await browser.newContext({ baseURL: String(testInfo.project.use.baseURL) });
  const merchant = await managerContext.newPage(), customer = await customerContext.newPage();
  try {
    await asSession(merchant, manager);
    await merchant.goto('/admin/catalog');
    await merchant.getByRole('row').filter({ hasText: product.name }).getByRole('button', { name: '编辑', exact: true }).click();
    const dialog = merchant.getByRole('dialog');
    const uploaded: string[] = [];
    for (let i = 0; i < 2; i++) {
      const response = merchant.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/merchant/stores/1/uploads');
      await dialog.locator('input[type="file"]').setInputFiles({
        name: 'picture-' + i + '.png', mimeType: 'image/png',
        buffer: await (await import('node:fs/promises')).readFile(path.resolve('../client/tooling/fixtures/qr.png')),
      });
      const result = await response;
      expect(result.ok()).toBe(true);
      uploaded.push((await result.json()).data.url);
      await expect(dialog.locator('img')).toHaveJSProperty('naturalWidth', 370);
    }
    expect(new Set(uploaded).size).toBe(2);
    const saved = merchant.waitForResponse(r => r.request().method() === 'PUT' && new URL(r.url()).pathname.endsWith('/products/DISH/' + product.id));
    await dialog.getByRole('button', { name: '保存', exact: true }).click();
    expect((await saved).ok()).toBe(true);
    expect((await call<Product[]>(request, '/user/stores/1/products', u)).find(p => p.id === product.id && p.itemType === 'DISH')!.image).toBe(uploaded[1]);
    const picture = await request.get(uploaded[1]!);
    expect(picture.ok()).toBe(true);
    expect(picture.headers()['content-type']).toContain('image/png');
    await merchant.screenshot({ path: testInfo.outputPath('merchant-picture.png'), fullPage: true });

    let cart = await call<Cart>(request, '/user/stores/1/cart', u);
    for (const item of [...cart.items]) cart = await call<Cart>(request, '/user/stores/1/cart/items', u, 'POST', {
      cartVersion: cart.cartVersion, itemType: item.itemType, itemId: item.itemId,
      flavors: item.flavors, delta: -item.quantity,
    });
    cart = await call<Cart>(request, '/user/stores/1/cart/items', u, 'POST', {
      cartVersion: cart.cartVersion, itemType: 'DISH', itemId: product.id, flavors: {}, delta: 1,
    });
    const input = { stores: [{ storeId: '1', cartVersion: cart.cartVersion, fulfillment: 'PICKUP', addressId: null, couponId: null }] };
    const quote = await call<Quote>(request, '/user/checkouts/preview', u, 'POST', input);
    let checkout = await call<Checkout>(request, '/user/checkouts', u, 'POST', { ...input, quoteHash: quote.quoteHash }, { 'Idempotency-Key': randomUUID() });
    checkout = await call<Checkout>(request, '/user/checkouts/' + checkout.id + '/pay', u, 'POST', { version: checkout.version });
    const order = checkout.orders[0]!;
    await call(request, '/merchant/orders/' + order.id + '/actions/accept', manager.token, 'POST', { version: order.version });
    const current = await call<Order>(request, '/user/orders/' + order.id, u);
    expect(current.status).toBe(3);
    await asSession(customer, userSession, true);
    await customer.goto('/app/pages/orders/index');
    await customer.locator('uni-view.card').filter({ has: customer.getByText(new RegExp(current.number)) }).getByText('申请取消', { exact: true }).click();
    const modal = customer.locator('uni-view.modal');
    await modal.locator('textarea').fill('图片存储定向验收');
    const chooserPromise = customer.waitForEvent('filechooser');
    await modal.getByText('添加举证图片（0 / 3）', { exact: true }).click();
    const chooser = await chooserPromise;
    const evidenceResponse = customer.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/user/orders/' + current.id + '/evidence');
    await chooser.setFiles(path.resolve('../client/tooling/fixtures/qr.png'));
    const evidenceResult = await evidenceResponse;
    expect(evidenceResult.ok()).toBe(true);
    const evidenceId = (await evidenceResult.json()).data.id as string;
    await expect(modal.getByText('添加举证图片（1 / 3）', { exact: true })).toBeVisible();
    const submit = customer.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/orders/' + current.id + '/aftersales'));
    await modal.getByText('确认提交', { exact: true }).click();
    expect((await submit).ok()).toBe(true);
    const aftersale = (await call<Aftersale[]>(request, '/user/aftersales', u)).find(r => r.orderId === current.id)!;
    expect(aftersale.evidence.map(e => e.id)).toContain(evidenceId);
    for (const token of [u, manager.token]) {
      const response = await request.get('/api/v1/evidence/' + evidenceId, { headers: { Authorization: 'Bearer ' + token } });
      expect(response.ok()).toBe(true);
      expect(response.headers()['content-type']).toContain('image/png');
    }
    const other = await call<Session>(request, '/user/auth/demo', '', 'POST', { account: 2 });
    // Ownership checks deliberately conceal another user's order with 404.
    expect((await request.get('/api/v1/evidence/' + evidenceId, { headers: { Authorization: 'Bearer ' + other.token } })).status()).toBe(404);
    expect((await request.get('/uploads/.private/' + evidenceId + '.png')).status()).toBe(401);
    await merchant.goto('/admin/operations/aftersales');
    const card = merchant.locator('article').filter({ hasText: '申请 ' + aftersale.id + ' ·' });
    const preview = merchant.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/evidence/' + evidenceId);
    await card.getByRole('button', { name: '查看举证 1', exact: true }).click();
    expect((await preview).ok()).toBe(true);
    await merchant.screenshot({ path: testInfo.outputPath('private-evidence.png'), fullPage: true });
    await testInfo.attach('upload-checkpoints', { contentType: 'application/json', body: JSON.stringify({
      boundary: 'Independent environment; staff/order/payment preparation uses API, uploads and evidence submission use real pages.',
      productId: product.id, uploadedPictures: uploaded.length, orderId: current.id, evidenceId, aftersaleId: aftersale.id,
      ownerAndMerchantRead: true, otherUserDenied: true, publicPrivatePathDenied: true,
    }) });
  } finally {
    await customerContext.close();
    await managerContext.close();
  }
});
