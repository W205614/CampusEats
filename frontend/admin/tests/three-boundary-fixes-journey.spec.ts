import { randomUUID } from 'node:crypto';
import fs from 'node:fs/promises';
import { test, expect, call, password } from './helpers';
import type { BrowserContext, Locator, Page } from '@playwright/test';
import type { Session } from '../../contracts/models';
import type { Checkout, CursorPage, Order } from '../../contracts/platform';

interface WalletCoupon { id: string; name: string; state: string; endsAt: string }
interface DeliveryRead {
  id: string; status: number; version: string;
  deliveryStartedAt: string; deliveryOverdue: boolean;
}
interface Policy { deliveryMinutes: number; version: string }
interface HttpSummary { role: string; method: string; path: string; status: number; code: string }

test('three boundary fixes: real staff password rotation, delivery policy and expired wallet', async ({ browser, request, adminSession }, testInfo) => {
  test.setTimeout(360000);
  expect(process.env.CI === 'true' || new URL(String(testInfo.project.use.baseURL)).port !== '18083').toBe(true);
  const suffix = randomUUID().slice(0, 8), admin = adminSession.token;
  const names = {
    campus: '边界验收校区-' + suffix, store: '边界验收餐厅-' + suffix,
    building: '边界验收楼栋-' + suffix, product: '边界验收餐-' + suffix,
    coupon: '自然到期验收券-' + suffix, user: 'boundary_user_' + suffix,
  };
  const contexts: BrowserContext[] = [], roles = new Map<Page, string>();
  const pageErrors: { role: string; message: string }[] = [], unexpectedHttp: HttpSummary[] = [];
  const expectedDenials: HttpSummary[] = [], uiRequests: HttpSummary[] = [];
  const responseChecks: Promise<void>[] = [], checkpoints: { name: string; [key: string]: unknown }[] = [];
  const initialUserPassword = 'Boundary-UserInitial!' + randomUUID();
  const changedUserPassword = 'Boundary-UserChanged!' + randomUUID();
  const localTime = (value: number) => new Date(value + 8 * 3600000).toISOString().slice(0, 19);

  async function pageFor(role: string, mobile = false) {
    const context = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
      viewport: mobile ? { width: 390, height: 844 } : { width: 1440, height: 1000 }, timezoneId: 'Asia/Shanghai' });
    contexts.push(context);
    const page = await context.newPage(); roles.set(page, role);
    page.on('pageerror', error => pageErrors.push({ role, message: error.message }));
    page.on('response', response => {
      const path = new URL(response.url()).pathname;
      if (!path.startsWith('/api/v1/') || response.status() < 400) return;
      responseChecks.push((async () => {
        let code = 'UNREADABLE'; try { code = String((await response.json()).code); } catch {}
        const summary = { role, method: response.request().method(), path, status: response.status(), code };
        unexpectedHttp.push(summary);
      })());
    });
    return page;
  }
  async function capture(page: Page, name: string) {
    // Screenshots and persisted summaries must never contain the generated credentials.
    for (const field of await page.locator('input[type="password"]').all())
      if (await field.isVisible()) await field.fill('');
    const output = testInfo.outputPath(name + '.png');
    await page.screenshot({ path: output, fullPage: true });
    await testInfo.attach(name, { path: output, contentType: 'image/png' });
  }
  async function ui<T>(page: Page, path: string, method: string, action: () => Promise<unknown>) {
    const pending = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1' + path
      && response.request().method() === method);
    await action(); const response = await pending, result = await response.json();
    uiRequests.push({ role: roles.get(page)!, method, path: '/api/v1' + path, status: response.status(), code: String(result.code) });
    expect(response.ok(), path + ': ' + String(result.code)).toBe(true);
    expect(result.code).toBe('OK'); return result.data as T;
  }
  async function choose(page: Page, trigger: Locator, option: string) {
    await trigger.click(); await expect(page.locator('.uni-picker-custom.uni-picker-toggle')).toBeVisible();
    const confirm = page.locator('.uni-picker-action-confirm:visible');
    if (await confirm.count()) {
      const container = page.locator('.uni-picker-custom:visible'), options = container.locator('.uni-picker-select .uni-picker-item');
      const target = await options.evaluateAll((items, text) => items.findIndex(item => item.textContent?.includes(text)), option);
      let selected = await options.evaluateAll(items => items.findIndex(item => item.classList.contains('selected')));
      expect(target).toBeGreaterThanOrEqual(0); await container.locator('uni-picker-view-column').hover();
      while (selected !== target) {
        const direction = Math.sign(target - selected); await page.mouse.wheel(0, direction * 40); selected += direction;
        await expect(options.nth(selected)).toHaveClass(/selected/);
      }
      await confirm.click();
    } else await page.locator('.uni-picker-item:visible').filter({ hasText: option }).click();
  }
  async function staffLogin(page: Page, username: string, secret: string) {
    await page.goto('/admin/'); await page.getByLabel('账户', { exact: true }).fill(username);
    await page.getByLabel('密码', { exact: true }).fill(secret);
    return ui<Session>(page, '/admin/auth/login', 'POST', () => page.getByLabel('密码', { exact: true }).press('Enter'));
  }
  async function rotateStaff(page: Page, username: string, initial: string) {
    const initialSession = await staffLogin(page, username, initial);
    expect(initialSession.mustChangePassword).toBe(true);
    await page.getByLabel('原密码', { exact: true }).fill(initial);
    await page.getByLabel('新密码（12至128个字符）', { exact: true }).fill(initial);
    // The page rejects this locally. Do not wait for a POST which must not be sent.
    await page.getByRole('button', { name: '更新并重新登录', exact: true }).click();
    await expect(page.getByText('新密码不能与原密码相同', { exact: true })).toBeVisible();
    const unchanged = await call<Session>(request, '/admin/auth/me', initialSession.token);
    expect(unchanged.mustChangePassword).toBe(true);
    await capture(page, roles.get(page) + '-same-password-rejected');
    const changed = 'Boundary-StaffChanged!' + randomUUID();
    await page.getByLabel('原密码', { exact: true }).fill(initial);
    await page.getByLabel('新密码（12至128个字符）', { exact: true }).fill(changed);
    await ui(page, '/admin/auth/password', 'POST', () => page.getByRole('button', { name: '更新并重新登录', exact: true }).click());
    const expired = await request.get('/api/v1/admin/auth/me', { headers: { Authorization: 'Bearer ' + initialSession.token } });
    expect(expired.status()).toBe(401); const denied = await expired.json(); expect(denied.code).toBe('UNAUTHORIZED');
    expectedDenials.push({ role: roles.get(page)! + '-old-session-check', method: 'GET', path: '/api/v1/admin/auth/me', status: 401, code: denied.code });
    const renewed = await staffLogin(page, username, changed); expect(renewed.mustChangePassword).toBe(false);
    checkpoints.push({ name: 'staff rejects unchanged initial password, keeps restriction, then rotates and revokes old session', role: roles.get(page) });
    return renewed;
  }
  async function userLogin(page: Page, secret: string) {
    await page.goto('/app/pages/login/index'); await page.locator('uni-input[aria-label="独立账号"] input').fill(names.user);
    await page.locator('uni-input[aria-label="账号密码"] input').fill(secret);
    return ui<Session>(page, '/user/auth/login', 'POST', () => page.getByRole('button', { name: '登录独立账号', exact: true }).click());
  }

  try {
    // Only the basic catalog/personnel fixtures use API writes; business actions below use pages.
    const campus = await call<{ id: string }>(request, '/platform/campuses', admin, 'POST', { name: names.campus, enabled: true });
    const store = await call<{ id: string }>(request, '/platform/stores', admin, 'POST', { campusId: campus.id, name: names.store,
      enabled: true, open: true, deliveryFeeCents: 0, packagingFeeCents: 0, hours: ['00:00-24:00'], phone: '' });
    await call(request, '/platform/campuses/' + campus.id + '/buildings', admin, 'POST', { name: names.building, enabled: true });
    const category = await call<{ id: string }>(request, '/merchant/stores/' + store.id + '/categories', admin, 'POST', { name: '边界餐品', type: 1, sort: 0, enabled: true });
    const product = await call<{ id: string }>(request, '/merchant/stores/' + store.id + '/products/DISH', admin, 'POST', {
      name: names.product, categoryId: category.id, price: '10.00', enabled: true, description: '独立边界验收', image: '/demo-images/dish.svg', flavors: [], components: [],
    });
    const businessDate = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10);
    await call(request, '/merchant/stores/' + store.id + '/quotas?date=' + businessDate, admin, 'PUT', { dishId: product.id, total: 5, defaultQuota: true });
    const staff: Record<string, { username: string; initial: string }> = {};
    for (const [key, role] of [['manager', 'SHOP_MANAGER'], ['courier', 'DELIVERER']]) {
      const username = 'boundary_' + key + '_' + suffix, initial = 'Boundary-StaffInitial!' + randomUUID();
      await call(request, '/platform/staff', admin, 'POST', { username, name: '边界' + key + suffix, role,
        storeId: key === 'manager' ? store.id : null, campusId: key === 'courier' ? campus.id : null, enabled: true, password: initial });
      staff[key] = { username, initial };
    }
    const platform = await pageFor('platform'), manager = await pageFor('manager');
    const courier = await pageFor('courier'), customer = await pageFor('customer', true);
    await staffLogin(platform, 'admin', password()); await platform.goto('/admin/users');
    await platform.getByRole('button', { name: '创建独立测试用户', exact: true }).click();
    const create = platform.getByRole('dialog', { name: '创建独立测试用户', exact: true });
    await create.getByLabel('独立账号', { exact: true }).fill(names.user);
    await create.getByLabel('显示名称', { exact: true }).fill('边界测试用户' + suffix);
    await create.getByLabel('初始密码', { exact: true }).fill(initialUserPassword);
    const created = await ui<{ id: string }>(platform, '/platform/users', 'POST', () => create.getByRole('button', { name: '保存用户', exact: true }).click());
    await expect(create).toBeHidden();
    await rotateStaff(manager, staff.manager.username, staff.manager.initial);
    const courierSession = await rotateStaff(courier, staff.courier.username, staff.courier.initial);
    expect((await userLogin(customer, initialUserPassword)).mustChangePassword).toBe(true);
    await customer.locator('uni-input[aria-label="原密码"] input').fill(initialUserPassword);
    await customer.locator('uni-input[aria-label="新密码"] input').fill(changedUserPassword);
    await customer.locator('uni-input[aria-label="确认新密码"] input').fill(changedUserPassword);
    await ui(customer, '/user/auth/password', 'POST', () => customer.getByRole('button', { name: '保存新密码', exact: true }).click());
    const user = await userLogin(customer, changedUserPassword); expect(user.id).toBe(created.id);
    await expect(customer.getByText('校园好店，', { exact: false })).toBeVisible();
    const campusPicker = customer.locator('uni-picker').filter({ has: customer.getByText('校区', { exact: true }) });
    await expect(campusPicker).not.toContainText('请选择'); await choose(customer, campusPicker, names.campus);
    await expect(customer.getByText(names.store + ' ▾', { exact: true })).toBeVisible();

    await manager.goto('/admin/service-operations');
    const deliveryMinutes = manager.getByRole('spinbutton', { name: '配送时限', exact: true });
    await expect(deliveryMinutes).toBeEnabled(); await deliveryMinutes.fill('120');
    await ui(manager, '/merchant/stores/' + store.id + '/service-policy', 'PUT', () => manager.getByRole('button', { name: '保存服务时限', exact: true }).click());
    await expect(deliveryMinutes).toBeEnabled();
    expect((await call<Policy>(request, '/merchant/stores/' + store.id + '/service-policy', admin)).deliveryMinutes).toBe(120);
    await capture(manager, '01-manager-delivery-policy-120');

    // Give the user a real short-lived coupon after login/setup, not an already-expired DB row.
    const couponEnd = Date.now() + 75000;
    const coupon = await call<{ id: string }>(request, '/merchant/stores/' + store.id + '/coupons', admin, 'POST', {
      name: names.coupon, kind: 'REDUCTION', thresholdCents: 0, value: 100, capCents: null, total: 2, perUser: 1,
      startsAt: localTime(Date.now() - 3600000), endsAt: localTime(couponEnd), enabled: true,
    });
    await customer.goto('/app/pages/account/index');
    const claimCard = customer.locator('uni-view.card').filter({ has: customer.getByText(names.coupon + ' · 店铺券', { exact: true }) });
    await ui(customer, '/user/coupons/' + coupon.id + '/claim', 'POST', () => claimCard.getByText('领取', { exact: true }).click());
    const walletCard = () => customer.locator('uni-view.card').filter({ has: customer.getByText(names.coupon, { exact: true }) });
    await expect(walletCard().getByText('可使用', { exact: true })).toBeVisible();
    const wallet = await call<CursorPage<WalletCoupon>>(request, '/user/wallet/page?state=AVAILABLE', user.token);
    const owned = wallet.records.find(row => row.name === names.coupon)!; expect(owned).toBeTruthy();
    await capture(customer, '02-user-claimed-coupon-before-expiry');

    await customer.goto('/app/pages/addresses/index'); await customer.getByText('新增地址', { exact: true }).click();
    await choose(customer, customer.getByText('校区：', { exact: false }), names.campus);
    await choose(customer, customer.getByText('楼栋：', { exact: false }), names.building);
    const address = customer.locator('uni-view.modal input');
    await address.nth(0).fill('BOUNDARY-301'); await address.nth(1).fill('边界验收用户'); await address.nth(2).fill('13800000000');
    await ui(customer, '/user/addresses', 'POST', () => customer.getByText('保存地址', { exact: true }).click());
    await customer.goto('/app/pages/menu/index');
    const meal = customer.locator('uni-view.card').filter({ has: customer.getByText(names.product, { exact: true }) });
    await meal.getByText('选餐', { exact: true }).click();
    await ui(customer, '/user/stores/' + store.id + '/cart/items', 'POST', () => customer.getByText('加入本店购物车', { exact: true }).click());
    await customer.goto('/app/pages/cart/index');
    const cart = customer.locator('uni-view.card').filter({ has: customer.getByText(names.campus + ' · ' + names.store, { exact: true }) });
    await cart.getByText('选择此店', { exact: true }).click(); await customer.getByText('合并结算 1 家店', { exact: true }).click();
    await choose(customer, customer.getByText('到店自取 ▾', { exact: true }), '校区配送');
    await choose(customer, customer.getByText('选择本校区收货地址', { exact: false }), 'BOUNDARY-301');
    await ui(customer, '/user/checkouts/preview', 'POST', () => customer.getByText('计算报价', { exact: true }).click());
    const checkout = await ui<Checkout>(customer, '/user/checkouts', 'POST', () => customer.getByText('提交订单，随后模拟支付', { exact: true }).click());
    const checkoutCard = customer.locator('uni-view.card').filter({ has: customer.getByText('待支付结算单 ' + checkout.id, { exact: true }) });
    await ui(customer, '/user/checkouts/' + checkout.id + '/pay', 'POST', () => checkoutCard.getByText('整批模拟支付', { exact: true }).click());
    const paid = await call<Checkout>(request, '/user/checkouts/' + checkout.id, user.token);
    expect(paid.state).toBe('PAID'); const order: Order = paid.orders[0];
    await manager.goto('/admin/orders?orderId=' + order.id);
    for (const [action, label] of [['accept', '接单备餐'], ['ready', '备餐完成'], ['publish', '发布抢单任务']])
      await ui(manager, '/merchant/orders/' + order.id + '/actions/' + action, 'POST', () => manager.getByRole('button', { name: label, exact: true }).click());
    await courier.goto('/admin/orders');
    const poolRow = courier.locator('.el-table__body tr').filter({ hasText: names.store }).filter({ has: courier.getByRole('button', { name: '抢单', exact: true }) });
    const pool = await call<{ id: string; orderId: string }[]>(request, '/courier/pool', courierSession.token);
    const task = pool.find(row => row.orderId === order.id)!; expect(task).toBeTruthy();
    await ui(courier, '/courier/tasks/' + task.id + '/claim', 'POST', () => poolRow.getByRole('button', { name: '抢单', exact: true }).click());
    await courier.locator('.el-table__body tr').filter({ hasText: order.number }).getByRole('button', { name: '处理订单', exact: true }).click();
    await ui(courier, '/courier/orders/' + order.id + '/actions/pickup', 'POST', () => courier.getByRole('button', { name: '确认取餐', exact: true }).click());
    const beforePolicyEdit = await call<DeliveryRead>(request, '/courier/orders/' + order.id, courierSession.token);
    expect(beforePolicyEdit.status).toBe(4); expect(beforePolicyEdit.deliveryOverdue).toBe(false);
    await capture(courier, '03-courier-picked-up-under-120-minute-policy');
    await manager.goto('/admin/service-operations'); await expect(deliveryMinutes).toBeEnabled(); await deliveryMinutes.fill('30');
    await ui(manager, '/merchant/stores/' + store.id + '/service-policy', 'PUT', () => manager.getByRole('button', { name: '保存服务时限', exact: true }).click());
    await expect(deliveryMinutes).toBeEnabled();
    expect((await call<Policy>(request, '/merchant/stores/' + store.id + '/service-policy', admin)).deliveryMinutes).toBe(30);
    const afterPolicyEdit = await call<DeliveryRead>(request, '/courier/orders/' + order.id, courierSession.token);
    expect(afterPolicyEdit.status).toBe(4); expect(afterPolicyEdit.version).toBe(beforePolicyEdit.version);
    expect(afterPolicyEdit.deliveryStartedAt).toBe(beforePolicyEdit.deliveryStartedAt);
    expect(afterPolicyEdit.deliveryOverdue).toBe(false);
    await capture(manager, '04-manager-policy-30-for-future-stages');
    checkpoints.push({ name: 'real delivery entered under 120 minutes then merchant changed future policy to 30',
      storeId: store.id, orderId: order.id, orderNumber: order.number, enteredDeliveryPolicyMinutes: 120,
      currentPolicyMinutes: 30, deliveryStartedAt: beforePolicyEdit.deliveryStartedAt,
      orderVersionUnchanged: true, stageSnapshotVerification: 'Exact 120-minute frozen deadline requires the delivery integration test or read-only SQL; no new test API is exposed.' });

    await expect(async () => {
      await customer.goto('/app/pages/account/index');
      await expect(walletCard().getByText('已过期', { exact: true })).toBeVisible({ timeout: 3000 });
    }).toPass({ timeout: Math.max(15000, couponEnd - Date.now() + 20000), intervals: [3000, 5000] });
    await capture(customer, '05-user-expired-coupon-in-all-states');
    const expiredFilter = customer.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/v1/user/wallet/page' && url.searchParams.get('state') === 'EXPIRED';
    });
    await choose(customer, customer.getByText('筛选优惠券：全部状态 ▾', { exact: true }), '已过期');
    const expiredResponse = await expiredFilter, expiredBody = await expiredResponse.json();
    expect(expiredResponse.ok()).toBe(true); expect(expiredBody.code).toBe('OK');
    await expect(customer.getByText('筛选优惠券：已过期 ▾', { exact: true })).toBeVisible();
    await expect(walletCard().getByText('已过期', { exact: true })).toBeVisible();
    const expiredWallet = expiredBody.data as CursorPage<WalletCoupon>;
    expect(expiredWallet.records.find(row => row.id === owned.id)?.state).toBe('EXPIRED');
    await capture(customer, '06-user-expired-wallet-filter');
    const usableFilter = customer.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/v1/user/wallet/page' && url.searchParams.get('state') === 'AVAILABLE';
    });
    await choose(customer, customer.getByText('筛选优惠券：已过期 ▾', { exact: true }), '可用');
    const usableResponse = await usableFilter, usableBody = await usableResponse.json();
    expect(usableResponse.ok()).toBe(true); expect(usableBody.code).toBe('OK');
    await expect(customer.getByText('筛选优惠券：可使用 ▾', { exact: true })).toBeVisible();
    await expect(walletCard()).toHaveCount(0);
    const usableWallet = usableBody.data as CursorPage<WalletCoupon>;
    expect(usableWallet.records.some(row => row.id === owned.id)).toBe(false);
    checkpoints.push({ name: 'coupon was claimed via page, naturally expired, displayed as expired and filtered consistently',
      couponId: coupon.id, walletCouponId: owned.id, expiresAt: localTime(couponEnd), timeWasNotMocked: true });
    await Promise.all(responseChecks); expect(pageErrors).toEqual([]); expect(unexpectedHttp).toEqual([]);
    expect(contexts).toHaveLength(4);
  } catch (error) {
    for (let index = 0; index < contexts.length; index++) {
      const page = contexts[index].pages()[0]; if (page) await capture(page, 'failure-role-' + index).catch(() => {});
    }
    throw error;
  } finally {
    await Promise.all(responseChecks);
    const output = testInfo.outputPath('three-boundary-fixes-evidence.json');
    await fs.writeFile(output, JSON.stringify({ sourceRevision: process.env.CANDIDATE_SHA || process.env.GITHUB_SHA || 'local',
      fixtureBoundary: 'Only campus/store/building/product/quota/coupon/staff use setup APIs. Accounts, password changes, coupon claim/filter, cart/checkout/mock payment, policy changes and delivery transitions use pages. Other APIs are read-only checks. Stage deadlines are tested with a fixed Clock separately and may be inspected by read-only SQL.',
      browserContexts: contexts.length, browserRoles: [...roles.values()], pageErrors, unexpectedHttp, expectedDenials, uiRequests, checkpoints,
    }, null, 2));
    await testInfo.attach('three-boundary-fixes-evidence', { path: output, contentType: 'application/json' });
    for (const context of contexts) await context.close();
  }
});
