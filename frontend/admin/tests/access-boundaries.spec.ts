import { test, expect, call } from './helpers';
import type { Store } from '../../contracts/platform';

test('anonymous visitors can browse campuses and menus but cannot read private business data', async ({ request }) => {
  for (const path of ['/user/campuses', '/user/stores', '/user/stores/1/products', '/user/stores/1/categories']) {
    const response = await request.get('/api/v1' + path);
    expect(response.ok(), path).toBeTruthy();
    expect((await response.json()).code, path).toBe('OK');
  }
  for (const path of ['/user/stores/1/cart', '/user/orders', '/merchant/stores', '/platform/campuses', '/courier/pool']) {
    const response = await request.get('/api/v1' + path);
    expect(response.status(), path).toBe(401);
    expect((await response.json()).code, path).toBe('UNAUTHORIZED');
  }
});

test('user and workbench tokens retain their API role boundaries after switching entrances', async ({ request, adminSession, userSession }) => {
  for (const path of ['/merchant/stores', '/platform/campuses', '/courier/pool']) {
    const response = await request.get('/api/v1' + path, { headers: { Authorization: 'Bearer ' + userSession.token } });
    expect(response.status(), path).toBe(403);
    expect((await response.json()).code, path).toBe('FORBIDDEN');
  }
  for (const path of ['/user/stores/1/cart', '/user/orders', '/courier/pool']) {
    const response = await request.get('/api/v1' + path, { headers: { Authorization: 'Bearer ' + adminSession.token } });
    expect(response.status(), path).toBe(403);
    expect((await response.json()).code, path).toBe('FORBIDDEN');
  }
});

test('retired unscoped endpoints cannot bypass the upgrade and private evidence stays protected', async ({ request, userSession }) => {
  for (const path of ['/user/cart', '/user/menu/dishes', '/user/shop']) {
    const response = await request.get('/api/v1' + path, { headers: { Authorization: 'Bearer ' + userSession.token } });
    expect(response.status(), path).toBe(410);
    expect((await response.json()).code, path).toBe('API_UPGRADED');
  }
  const evidence = await request.get('/uploads/.private/00000000-0000-0000-0000-000000000000.png');
  expect(evidence.status()).toBe(401);
  for (const app of ['app', 'admin']) {
    const missing = await request.get('/' + app + '/assets/missing-security-boundary.js');
    expect(missing.status()).toBe(404);
    expect(missing.headers()['content-type']).not.toContain('javascript');
  }
});

test('expired workbench login clears only that session and returns to its login form', async ({ page, adminSession, userSession }) => {
  await page.goto('/');
  await page.evaluate(({ admin, user }) => {
    sessionStorage.setItem('campus-admin', JSON.stringify(admin));
    sessionStorage.setItem('campus-user', JSON.stringify(user));
  }, { admin: adminSession, user: userSession });
  await page.route('**/api/v1/merchant/stores/page?*', route => route.fulfill({
    status: 401,
    json: { code: 'UNAUTHORIZED', message: '登录已失效，请重新登录', data: null },
  }));
  await page.goto('/admin/');
  await expect(page.getByRole('button', { name: '登录工作台', exact: true })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('campus-admin'))).toBeNull();
  expect(await page.evaluate(token => JSON.parse(sessionStorage.getItem('campus-user')!).token === token, userSession.token)).toBeTruthy();
});

test('an expired user login recovers public browsing without clearing the workbench session', async ({ page, adminSession, userSession }) => {
  await page.addInitScript(({ admin, user }) => {
    sessionStorage.setItem('campus-admin', JSON.stringify(admin));
    sessionStorage.setItem('campus-user', JSON.stringify({ ...user, token: 'expired-public-browsing-test' }));
  }, { admin: adminSession, user: userSession });
  const campusReads: (string | undefined)[] = [];
  page.on('request', request => {
    if (new URL(request.url()).pathname === '/api/v1/user/campuses') campusReads.push(request.headers().authorization);
  });
  await page.goto('/app/');
  await expect(page.getByText('默认校区 ▾', { exact: true })).toBeVisible();
  await expect(page.getByText('番茄鸡蛋饭', { exact: true }).first()).toBeVisible();
  expect(campusReads).toEqual(['Bearer expired-public-browsing-test', undefined]);
  expect(await page.evaluate(() => sessionStorage.getItem('campus-user'))).toBeNull();
  expect(await page.evaluate(token => JSON.parse(sessionStorage.getItem('campus-admin')!).token === token, adminSession.token)).toBeTruthy();
});

test('demo login does not attach a stale user token and succeeds on its first request', async ({ page, userSession }) => {
  await page.addInitScript(user => {
    sessionStorage.setItem('campus-user', JSON.stringify({ ...user, token: 'expired-login-test' }));
  }, userSession);
  const reads: (string | undefined)[] = [];
  page.on('request', request => {
    if (new URL(request.url()).pathname === '/api/v1/user/auth/demo') reads.push(request.headers().authorization);
  });
  await page.goto('/app/pages/login/index');
  const login = page.waitForResponse(response => response.url().endsWith('/api/v1/user/auth/demo'));
  await page.getByText('进入演示用户 1', { exact: true }).click();
  const response = await login;
  expect(response.ok()).toBeTruthy();
  const result = await response.json();
  await expect(page.getByText(result.data.name, { exact: true })).toBeVisible();
  expect(reads).toEqual([undefined]);
  expect(await page.evaluate(token => JSON.parse(sessionStorage.getItem('campus-user')!).token === token, result.data.token)).toBeTruthy();
});

test('an unauthorized user mutation is surfaced once without anonymous replay', async ({ page, adminSession, userSession }) => {
  await page.addInitScript(({ admin, user }) => {
    sessionStorage.setItem('campus-admin', JSON.stringify(admin));
    sessionStorage.setItem('campus-user', JSON.stringify(user));
  }, { admin: adminSession, user: userSession });
  let attempts = 0;
  await page.route('**/api/v1/user/stores/1/favorites/STORE/1', route => {
    expect(route.request().method()).toBe('POST');
    expect(route.request().headers().authorization === 'Bearer ' + userSession.token).toBeTruthy();
    attempts++;
    return route.fulfill({ status: 401, json: { code: 'UNAUTHORIZED', message: '登录已失效，请重新登录', data: null } });
  });
  await page.goto('/app/');
  await page.getByText('收藏店铺', { exact: true }).click();
  await expect(page.getByText('登录已失效，请重新登录', { exact: true })).toBeVisible();
  expect(attempts).toBe(1);
  expect(await page.evaluate(() => sessionStorage.getItem('campus-user'))).toBeNull();
  expect(await page.evaluate(token => JSON.parse(sessionStorage.getItem('campus-admin')!).token === token, adminSession.token)).toBeTruthy();
});

test('a delayed unauthorized private read neither replays nor clears a newer user login', async ({ page }) => {
  const oldSession = { id: '901', token: 'delayed-old-user', name: '旧账号', role: 'USER' };
  const newSession = { id: '902', token: 'delayed-new-user', name: '新账号', role: 'USER' };
  const store = { id: '1', campusId: '1', campusName: '测试校区', name: '新账号店铺', enabled: true,
    open: true, accepting: true, hours: ['00:00-24:00'], deliveryFee: '0.00', packagingFee: '0.00', phone: '', version: '0' };
  await page.addInitScript(user => sessionStorage.setItem('campus-user', JSON.stringify(user)), oldSession);
  let unblock!: () => void, arrived!: () => void;
  const pending = new Promise<void>(resolve => { unblock = resolve; });
  const started = new Promise<void>(resolve => { arrived = resolve; });
  const reads = { old: 0, current: 0 }, unexpected: string[] = [];
  // Every API request is intercepted: this test never creates a real demo session or business write.
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace('/api/v1', '');
    const ok = (data: unknown) => route.fulfill({ json: { code: 'OK', data } });
    if (path === '/user/auth/demo' && request.method() === 'POST') return ok(newSession);
    if (request.method() !== 'GET') { unexpected.push(request.method() + ' ' + path); return route.abort(); }
    if (path === '/user/auth/capabilities') return ok({demoLoginEnabled:true,localLoginEnabled:true,wechatLoginEnabled:false,paymentMode:'MOCK',mode:'DEMO'});
    if (path === '/user/auth/account') return ok({localAccount:false,username:null});
    if (path === '/user/wallet/page') {
      if (request.headers().authorization === 'Bearer ' + oldSession.token) {
        reads.old++; arrived(); await pending;
        return route.fulfill({ status: 401, json: { code: 'UNAUTHORIZED', message: '旧登录已失效', data: null } });
      }
      reads.current++;
      return ok({records:[{ id: '71', name: '新账号专属券', state: 'AVAILABLE', endsAt: '2099-12-31T23:59:59+08:00' }],nextCursor:null,hasMore:false});
    }
    if (path === '/user/favorites/page') return ok({records:[{ targetType: 'STORE', targetId: '1', storeId: '1', storeName: store.name, name: '新账号收藏' }],nextCursor:null,hasMore:false});
    if (path === '/user/campuses') return ok([{ id: '1', name: '测试校区', enabled: true }]);
    if (path === '/user/stores/page') return ok({ records: [store], nextCursor: null, hasMore: false });
    if (path === '/user/stores/1') return ok(store);
    if (path === '/user/stores/1/products') return ok([{ id: '1', storeId: '1', itemType: 'DISH', categoryId: '1', name: '新账号餐品', price: '10.00', remaining: 10, flavors: [] }]);
    if (path === '/user/stores/1/cart') return ok({ storeId: '1', cartVersion: '0', items: [] });
    if (path === '/user/stores/1/categories') return ok([]);
    if (['/user/stores/1/coupons/page', '/user/coupons/page'].includes(path)) return ok({records:[],nextCursor:null,hasMore:false});
    unexpected.push(request.method() + ' ' + path);
    return route.abort();
  });
  await page.goto('/app/pages/account/index');
  await started;
  await page.getByText('账户与退出', { exact: true }).click();
  const login = page.waitForResponse(response => response.url().endsWith('/api/v1/user/auth/demo'));
  await page.getByText('进入演示用户 1', { exact: true }).click();
  const result = await (await login).json();
  await expect(page.getByText(result.data.name, { exact: true })).toBeVisible();
  await expect(page.getByText('新账号餐品', { exact: true })).toBeVisible();
  await page.getByText('领券与我的收藏', { exact: true }).click();
  await expect(page.getByText('新账号专属券', { exact: true })).toBeVisible();
  await expect(page.getByText('新账号收藏 · 新账号店铺', { exact: true })).toBeVisible();
  const oldFailure = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/user/wallet/page'
    && response.request().headers().authorization === 'Bearer ' + oldSession.token);
  unblock();
  const failed = await oldFailure;
  expect(failed.status()).toBe(401);
  await failed.finished();
  // Let the browser handle the completed response before examining the newer account.
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  await expect(page.getByText('旧登录已失效', { exact: true })).toHaveCount(0);
  await expect(page.getByText('新账号专属券', { exact: true })).toBeVisible();
  await expect(page.getByText('新账号收藏 · 新账号店铺', { exact: true })).toBeVisible();
  expect(reads).toEqual({ old: 1, current: 1 });
  expect(unexpected).toEqual([]);
  expect(await page.evaluate(token => JSON.parse(sessionStorage.getItem('campus-user')!).token === token, result.data.token)).toBeTruthy();
});

test('the account page replaces an unavailable saved store and explains empty coupons and favorites', async ({ page, request, userSession }) => {
  const stores = await call<Store[]>(request, '/user/stores', userSession.token);
  expect(stores.length).toBeGreaterThan(0);
  const first = stores[0]!;
  await page.addInitScript(user => {
    sessionStorage.setItem('campus-user', JSON.stringify(user));
    localStorage.setItem('store-choice', JSON.stringify({ type: 'string', data: '99999999999999999' }));
  }, userSession);
  for (const endpoint of ['wallet', 'favorites', 'coupons']) {
    await page.route('**/api/v1/user/' + endpoint + '/page*', route => route.fulfill({ json: { code: 'OK', data: {records:[],nextCursor:null,hasMore:false} } }));
  }
  const couponReads: string[] = [];
  await page.route('**/api/v1/user/stores/*/coupons/page*', async route => {
    couponReads.push(new URL(route.request().url()).pathname);
    // Verify the chosen store against the real server before stubbing its list empty.
    const response = await route.fetch();
    expect(response.ok()).toBeTruthy();
    await route.fulfill({ response, json: { code: 'OK', data: {records:[],nextCursor:null,hasMore:false} } });
  });
  await page.goto('/app/pages/account/index');
  await expect(page.getByText(first.name + ' ▾', { exact: true })).toBeVisible();
  for (const text of [
    '当前暂无可领取的优惠券，可以切换店铺看看。',
    '还没有优惠券，领取后会保存在这里。',
    '还没有收藏，去点餐页收藏喜欢的店铺和餐品吧。',
  ]) await expect(page.getByText(text, { exact: true })).toBeVisible();
  expect(couponReads).toEqual(['/api/v1/user/stores/' + first.id + '/coupons/page']);
});
