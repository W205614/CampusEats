import { randomUUID } from 'node:crypto';
import fs from 'node:fs/promises';
import { test, expect, call, password } from './helpers';
import { selectAuthorizedStore, selectStaffOption } from './journey-selectors';
import type { BrowserContext, Locator, Page } from '@playwright/test';
import type { Session } from '../../contracts/models';
import type { Checkout, CursorPage, Order } from '../../contracts/platform';

interface StaffRead {
  id: string; username: string; name: string; role: string; status: number;
  mustChangePassword: boolean; storeId: string | null;
}
interface Person {
  id: string; username: string; name: string; role: string; initial: string;
}
interface InboxMessage { id: string; orderId: string; storeId: string; eventType: string; readAt?: string | null }
interface StageException {
  id: string; orderId: string; kind: string; stage: string; createdAt: string;
  deadlineAt: string | null; migrated: boolean;
}
interface HttpSummary { role: string; method: string; path: string; status: number; code: string; scenario?: string }

test('authorization and inbox context: real staff revocation and cross-campus message dispatch', async ({ browser, request }, testInfo) => {
  test.setTimeout(420000);
  const target = new URL(String(testInfo.project.use.baseURL));
  const sourceRevision = process.env.CANDIDATE_SHA || process.env.GITHUB_SHA || '';
  // Do not request the shared helper's adminSession fixture: that fixture logs in before
  // the body. Validate the isolated target before performing even a login write.
  if (process.env.CI !== 'true') {
    expect(['127.0.0.1', 'localhost']).toContain(target.hostname);
    expect(target.port).not.toBe('18083');
    expect(target.port).toMatch(/^\d+$/);
  }
  expect(sourceRevision).toMatch(/^[a-f0-9]{40}$/);
  const suffix = randomUUID().slice(0, 8);
  const names = {
    campusA: '权限甲校区-' + suffix, campusB: '权限乙校区-' + suffix,
    storeA: '权限甲店-' + suffix, storeB: '权限乙店-' + suffix,
    building: '权限验收楼栋-' + suffix, product: '权限验收餐-' + suffix,
  };
  const contexts: BrowserContext[] = [], roleFor = new Map<Page, string>();
  const pageErrors: { role: string; message: string }[] = [];
  const unexpectedHttp: HttpSummary[] = [], expectedDenials: HttpSummary[] = [], uiRequests: HttpSummary[] = [];
  const transportErrors: { role: string; method: string; path: string; failure: string }[] = [];
  const responseChecks: Promise<void>[] = [], checkpoints: { name: string; [key: string]: unknown }[] = [];
  let secondaryId = '', staleWriteScenario: string | null = null;

  async function pageFor(role: string, mobile = false) {
    const context = await browser.newContext({ baseURL: target.origin, timezoneId: 'Asia/Shanghai',
      viewport: mobile ? { width: 390, height: 844 } : { width: 1440, height: 1000 } });
    contexts.push(context);
    const page = await context.newPage(); roleFor.set(page, role);
    page.on('pageerror', error => pageErrors.push({ role, message: error.message }));
    page.on('requestfailed', failed => {
      const url = new URL(failed.url()), failure = failed.failure()?.errorText || 'UNKNOWN';
      if (url.pathname.startsWith('/api/v1/') && !failure.includes('ERR_ABORTED'))
        transportErrors.push({ role, method: failed.method(), path: url.pathname, failure });
    });
    page.on('response', response => {
      const path = new URL(response.url()).pathname, method = response.request().method();
      if (!path.startsWith('/api/v1/') || response.status() < 400) return;
      const scenario = role === 'platform-b' && method === 'PUT' && response.status() === 401
        && path === '/api/v1/platform/staff/' + secondaryId ? staleWriteScenario : null;
      responseChecks.push((async () => {
        let code = 'UNREADABLE'; try { code = String((await response.json()).code); } catch {}
        const summary = { role, method, path, status: response.status(), code, ...(scenario ? { scenario } : {}) };
        if (scenario) expectedDenials.push(summary); else unexpectedHttp.push(summary);
      })());
    });
    return page;
  }
  async function capture(page: Page, name: string) {
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
    uiRequests.push({ role: roleFor.get(page)!, method, path: '/api/v1' + path, status: response.status(), code: String(result.code) });
    expect(response.ok(), path + ': ' + String(result.code)).toBe(true);
    expect(result.code).toBe('OK'); return result.data as T;
  }
  async function staffLogin(page: Page, username: string, secret: string) {
    await page.goto('/admin/'); await page.getByLabel('账户', { exact: true }).fill(username);
    await page.getByLabel('密码', { exact: true }).fill(secret);
    return ui<Session>(page, '/admin/auth/login', 'POST', () => page.getByLabel('密码', { exact: true }).press('Enter'));
  }
  async function rotateStaff(page: Page, person: Person, initial: string, changed: string) {
    expect((await staffLogin(page, person.username, initial)).mustChangePassword).toBe(true);
    await page.getByLabel('原密码', { exact: true }).fill(initial);
    await page.getByLabel('新密码（12至128个字符）', { exact: true }).fill(changed);
    await ui(page, '/admin/auth/password', 'POST', () => page.getByRole('button', { name: '更新并重新登录', exact: true }).click());
    const renewed = await staffLogin(page, person.username, changed);
    expect(renewed.mustChangePassword).toBe(false); return renewed;
  }
  async function option(page: Page, dialog: Locator, label: string, choice: string) {
    await selectStaffOption(page, dialog, label, choice);
  }
  async function selectStore(page: Page, label: string) {
    await selectAuthorizedStore(page, label);
  }
  async function createStaff(page: Page, key: string, role: string, placement?: { store?: string; campus?: string }) {
    const person: Person = { id: '', username: 'auth_' + key + '_' + suffix, name: '权限' + key + '-' + suffix,
      role, initial: 'Auth-Initial!' + randomUUID() };
    await page.goto('/admin/operations/staff');
    await page.getByRole('button', { name: '新增员工', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: '员工与权限', exact: true });
    await dialog.getByLabel('账户', { exact: true }).fill(person.username);
    await dialog.getByLabel('姓名', { exact: true }).fill(person.name);
    const labels: Record<string, string> = { PLATFORM_ADMIN: '平台管理员', SHOP_MANAGER: '店长', OPERATOR: '运营员', DELIVERER: '配送员' };
    if (role !== 'OPERATOR') await option(page, dialog, '角色', labels[role]);
    if (placement?.store) await option(page, dialog, '归属店铺', placement.store);
    if (placement?.campus) await option(page, dialog, '配送校区', placement.campus);
    await dialog.getByLabel('初始密码，至少12个字符', { exact: true }).fill(person.initial);
    const created = await ui<{ id: string }>(page, roleFor.get(page) === 'manager-b' ? '/merchant/staff' : '/platform/staff', 'POST',
      () => dialog.getByRole('button', { name: '保存', exact: true }).click());
    await expect(dialog).toBeHidden(); person.id = created.id; return person;
  }
  async function editStaff(page: Page, person: Person) {
    await page.goto('/admin/operations/staff');
    await page.getByLabel('账号或姓名', { exact: true }).fill(person.username);
    await ui(page, '/platform/staff/page', 'GET', () => page.getByRole('button', { name: '查询员工', exact: true }).click());
    const row = page.locator('.el-table__body tr').filter({ hasText: person.username });
    await expect(row).toHaveCount(1); await row.getByRole('button', { name: '编辑 / 重置密码', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: '员工与权限', exact: true });
    await expect(dialog).toBeVisible(); return dialog;
  }
  async function chooseUser(page: Page, trigger: Locator, choice: string) {
    await trigger.click(); const picker = page.locator('.uni-picker-custom.uni-picker-toggle');
    await expect(picker).toBeVisible();
    const done = page.locator('.uni-picker-action-confirm:visible');
    if (await done.count()) {
      const container = page.locator('.uni-picker-custom:visible'), options = container.locator('.uni-picker-select .uni-picker-item');
      const targetIndex = await options.evaluateAll((items, text) => items.findIndex(item => item.textContent?.includes(text)), choice);
      let selectedIndex = await options.evaluateAll(items => items.findIndex(item => item.classList.contains('selected')));
      expect(targetIndex).toBeGreaterThanOrEqual(0); await container.locator('uni-picker-view-column').hover();
      while (selectedIndex !== targetIndex) {
        await page.mouse.wheel(0, Math.sign(targetIndex - selectedIndex) * 40);
        selectedIndex += Math.sign(targetIndex - selectedIndex); await expect(options.nth(selectedIndex)).toHaveClass(/selected/);
      }
      await done.click();
    } else await page.locator('.uni-picker-item:visible').filter({ hasText: choice }).click();
  }

  try {
    const a = await pageFor('platform-a'), b = await pageFor('platform-b');
    const manager = await pageFor('manager-b'), customer = await pageFor('customer', true);
    const admin = await staffLogin(a, 'admin', password());
    // API writes prepare only directory/catalog/quota fixtures. All staff changes,
    // policies, order actions, message navigation and dispatch below use visible pages.
    const campusA = await call<{ id: string }>(request, '/platform/campuses', admin.token, 'POST', { name: names.campusA, enabled: true });
    const campusB = await call<{ id: string }>(request, '/platform/campuses', admin.token, 'POST', { name: names.campusB, enabled: true });
    const stores: { id: string }[] = [];
    for (const [campus, name] of [[campusA.id, names.storeA], [campusB.id, names.storeB]])
      stores.push(await call<{ id: string }>(request, '/platform/stores', admin.token, 'POST', {
        campusId: campus, name, enabled: true, open: true, deliveryFeeCents: 0, packagingFeeCents: 0, hours: ['00:00-24:00'], phone: '' }));
    const [storeA, storeB] = stores;
    await call(request, '/platform/campuses/' + campusB.id + '/buildings', admin.token, 'POST', { name: names.building, enabled: true });
    const category = await call<{ id: string }>(request, '/merchant/stores/' + storeB.id + '/categories', admin.token, 'POST',
      { name: '权限验收餐品', type: 1, sort: 0, enabled: true });
    const product = await call<{ id: string }>(request, '/merchant/stores/' + storeB.id + '/products/DISH', admin.token, 'POST', {
      name: names.product, categoryId: category.id, price: '10.00', enabled: true, description: '仅本轮隔离验收使用', image: '/demo-images/dish.svg', flavors: [], components: [] });
    const businessDate = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10);
    await call(request, '/merchant/stores/' + storeB.id + '/quotas?date=' + businessDate, admin.token, 'PUT',
      { dishId: product.id, total: 5, defaultQuota: true });
    const secondary = await createStaff(a, 'adminB', 'PLATFORM_ADMIN'); secondaryId = secondary.id;
    const managerStaff = await createStaff(a, 'managerB', 'SHOP_MANAGER', { store: names.campusB + ' / ' + names.storeB });
    const courierA = await createStaff(a, 'courierA', 'DELIVERER', { campus: names.campusA });
    const courierB = await createStaff(a, 'courierB', 'DELIVERER', { campus: names.campusB });
    let secondaryPassword = 'Auth-Changed!' + randomUUID();
    await rotateStaff(b, secondary, secondary.initial, secondaryPassword);
    const managerSession = await rotateStaff(manager, managerStaff, managerStaff.initial, 'Auth-ManagerChanged!' + randomUUID());
    const operator = await createStaff(manager, 'operatorB', 'OPERATOR');
    const operatorRead = await call<CursorPage<StaffRead>>(request, '/merchant/stores/' + storeB.id + '/staff/page?query=' + operator.username, admin.token);
    expect(operatorRead.records.find(row => row.id === operator.id)?.role).toBe('OPERATOR');
    await capture(manager, '01-manager-created-own-store-operator');

    await manager.goto('/admin/service-operations');
    const dispatch = manager.getByRole('spinbutton', { name: '待分配时限', exact: true });
    await expect(dispatch).toBeEnabled(); await dispatch.fill('1');
    await ui(manager, '/merchant/stores/' + storeB.id + '/service-policy', 'PUT',
      () => manager.getByRole('button', { name: '保存服务时限', exact: true }).click());
    await expect(dispatch).toBeEnabled();
    await customer.goto('/app/pages/login/index');
    const user = await ui<Session>(customer, '/user/auth/demo', 'POST',
      () => customer.getByRole('button', { name: '进入演示用户 2', exact: true }).click());
    await expect(customer.getByText('校园好店，', { exact: false })).toBeVisible();
    const campusPicker = customer.locator('uni-picker').filter({ has: customer.getByText('校区', { exact: true }) });
    await expect(campusPicker).not.toContainText('请选择'); await chooseUser(customer, campusPicker, names.campusB);
    await customer.goto('/app/pages/addresses/index'); await customer.getByText('新增地址', { exact: true }).click();
    await chooseUser(customer, customer.getByText('校区：', { exact: false }), names.campusB);
    await chooseUser(customer, customer.getByText('楼栋：', { exact: false }), names.building);
    const address = customer.locator('uni-view.modal input');
    await address.nth(0).fill('AUTH-' + suffix); await address.nth(1).fill('权限验收用户'); await address.nth(2).fill('13800000000');
    await ui(customer, '/user/addresses', 'POST', () => customer.getByText('保存地址', { exact: true }).click());
    await customer.goto('/app/pages/menu/index');
    await customer.locator('uni-view.card').filter({ has: customer.getByText(names.product, { exact: true }) }).getByText('选餐', { exact: true }).click();
    await ui(customer, '/user/stores/' + storeB.id + '/cart/items', 'POST', () => customer.getByText('加入本店购物车', { exact: true }).click());
    await customer.goto('/app/pages/cart/index');
    await customer.locator('uni-view.card').filter({ has: customer.getByText(names.campusB + ' · ' + names.storeB, { exact: true }) }).getByText('选择此店', { exact: true }).click();
    await customer.getByText('合并结算 1 家店', { exact: true }).click();
    await chooseUser(customer, customer.getByText('到店自取 ▾', { exact: true }), '校区配送');
    await chooseUser(customer, customer.getByText('选择本校区收货地址', { exact: false }), 'AUTH-' + suffix);
    await ui(customer, '/user/checkouts/preview', 'POST', () => customer.getByText('计算报价', { exact: true }).click());
    const checkout = await ui<Checkout>(customer, '/user/checkouts', 'POST', () => customer.getByText('提交订单，随后模拟支付', { exact: true }).click());
    const checkoutCard = customer.locator('uni-view.card').filter({ has: customer.getByText('待支付结算单 ' + checkout.id, { exact: true }) });
    await ui(customer, '/user/checkouts/' + checkout.id + '/pay', 'POST', () => checkoutCard.getByText('整批模拟支付', { exact: true }).click());
    const paid = await call<Checkout>(request, '/user/checkouts/' + checkout.id, user.token);
    expect(paid.state).toBe('PAID'); const order: Order = paid.orders[0];
    await manager.goto('/admin/orders?orderId=' + order.id);
    for (const [name, label] of [['accept', '接单备餐'], ['ready', '备餐完成']])
      await ui(manager, '/merchant/orders/' + order.id + '/actions/' + name, 'POST', () => manager.getByRole('button', { name: label, exact: true }).click());
    const readyAt = Date.now();

    async function readSecondary() {
      const result = await call<CursorPage<StaffRead>>(request, '/platform/staff/page?query=' + secondary.username, admin.token);
      const row = result.records.find(record => record.id === secondary.id); expect(row).toBeTruthy(); return row!;
    }
    async function rejectOldForm(scenario: string) {
      staleWriteScenario = scenario;
      const pending = b.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/platform/staff/' + secondary.id
        && response.request().method() === 'PUT');
      await b.getByRole('dialog', { name: '员工与权限', exact: true }).getByRole('button', { name: '保存', exact: true }).click();
      const response = await pending, result = await response.json();
      expect(response.status()).toBe(401); expect(result.code).toBe('UNAUTHORIZED');
      await expect(b.getByRole('button', { name: '登录工作台', exact: true })).toBeVisible();
      await Promise.all(responseChecks); staleWriteScenario = null;
      await capture(b, 'revoked-session-' + scenario);
    }
    for (const scenario of ['disabled', 'demoted', 'password-reset']) {
      const oldDialog = await editStaff(b, secondary);
      await oldDialog.getByLabel('姓名', { exact: true }).fill('陈旧表单不得保存-' + suffix);
      if (scenario === 'password-reset')
        await oldDialog.getByLabel('重置密码，留空不修改', { exact: true }).fill('Auth-StaleWrite!' + randomUUID());
      const edit = await editStaff(a, secondary);
      const resetPassword = 'Auth-Reset!' + randomUUID();
      if (scenario === 'disabled') await edit.locator('.el-switch__core').click();
      if (scenario === 'demoted') {
        await option(a, edit, '角色', '运营员'); await option(a, edit, '归属店铺', names.campusA + ' / ' + names.storeA);
      }
      if (scenario === 'password-reset') await edit.getByLabel('重置密码，留空不修改', { exact: true }).fill(resetPassword);
      await ui(a, '/platform/staff/' + secondary.id, 'PUT', () => edit.getByRole('button', { name: '保存', exact: true }).click());
      await expect(edit).toBeHidden(); await rejectOldForm(scenario);
      const current = await readSecondary(); expect(current.name).toBe(secondary.name);
      expect(current.status).toBe(scenario === 'disabled' ? 0 : 1);
      expect(current.role).toBe(scenario === 'demoted' ? 'OPERATOR' : 'PLATFORM_ADMIN');
      if (scenario === 'password-reset') expect(current.mustChangePassword).toBe(true);
      checkpoints.push({ name: 'old staff form rejected after ' + scenario, employeeId: secondary.id,
        status: current.status, role: current.role, mustChangePassword: current.mustChangePassword, staleNameDiscarded: true,
        evidenceBoundary: 'The page request is sent after revocation and receives 401. Deterministic backend tests cover the separate already-authenticated request waiting on a database lock.' });
      if (scenario !== 'password-reset') {
        const restore = await editStaff(a, secondary);
        if (scenario === 'disabled') await restore.locator('.el-switch__core').click();
        else await option(a, restore, '角色', '平台管理员');
        await ui(a, '/platform/staff/' + secondary.id, 'PUT', () => restore.getByRole('button', { name: '保存', exact: true }).click());
        await expect(restore).toBeHidden(); expect((await staffLogin(b, secondary.username, secondaryPassword)).mustChangePassword).toBe(false);
      } else {
        secondaryPassword = 'Auth-ResetChanged!' + randomUUID();
        await rotateStaff(b, secondary, resetPassword, secondaryPassword);
      }
    }
    await capture(a, '02-platform-verified-personnel-revocation');

    await a.goto('/admin/messages'); await selectStore(a, names.campusA + ' / ' + names.storeA);
    const unreadResponse = a.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/v1/me/messages/page' && url.searchParams.get('unread') === 'true';
    });
    // Element Plus hides the native checkbox input. Operate its visible label,
    // then verify both the real query and the resulting native checked state.
    await a.getByText('只看未读', { exact: true }).click();
    expect((await unreadResponse).ok()).toBe(true);
    await expect(a.getByRole('checkbox', { name: '只看未读', exact: true })).toBeChecked();
    const exceptionMessage = () => a.locator('.el-table__body tr')
      .filter({ has: a.getByRole('cell', { name: order.id, exact: true }) }).filter({ hasText: '订单需要人工关注' });
    await expect(async () => {
      await a.getByRole('button', { name: '刷新消息', exact: true }).click();
      await expect(exceptionMessage()).toHaveCount(1, { timeout: 1500 });
    }).toPass({ timeout: 110000, intervals: [3000, 5000] });
    const messages = await call<CursorPage<InboxMessage>>(request, '/me/messages/page?limit=200', admin.token);
    const message = messages.records.find(row => row.orderId === order.id && row.eventType === 'ORDER_EXCEPTION');
    expect(message?.storeId).toBe(storeB.id);
    const exceptions = await call<CursorPage<StageException>>(request,
      '/platform/order-exceptions/page?storeId=' + storeB.id + '&state=OPEN', admin.token);
    const timedOut = exceptions.records.find(row => row.orderId === order.id && row.kind === 'STAGE_OVERDUE');
    expect(timedOut?.stage).toBe('WAIT_DISPATCH'); expect(timedOut?.migrated).toBe(false);
    expect(timedOut?.deadlineAt).toBeTruthy();
    expect(Date.parse(timedOut!.createdAt)).toBeGreaterThanOrEqual(Date.parse(timedOut!.deadlineAt!));
    await expect(a.locator('.topbar')).toContainText(names.campusA + ' / ' + names.storeA);
    await capture(a, '03-platform-inbox-at-other-campus-before-navigation');
    const destinationCouriers = a.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/merchant/stores/' + storeB.id + '/couriers');
    await ui(a, '/me/messages/' + message!.id + '/read', 'POST',
      () => exceptionMessage().getByRole('button', { name: '前往订单履约', exact: true }).click());
    await expect(a).toHaveURL(new RegExp('/admin/orders\\?orderId=' + order.id + '$'));
    await expect(a.locator('.topbar')).toContainText(names.campusB + ' / ' + names.storeB);
    await expect(a.getByRole('heading', { name: order.number, exact: true })).toBeVisible();
    const courierResponse = await destinationCouriers, courierBody = await courierResponse.json();
    expect(courierResponse.ok()).toBe(true); expect(courierBody.code).toBe('OK');
    const candidates = courierBody.data as { id: string; name: string }[];
    expect(candidates.some(row => row.id === courierB.id)).toBe(true);
    expect(candidates.some(row => row.id === courierA.id)).toBe(false);
    const drawer = a.getByRole('dialog', { name: '订单详情与操作', exact: true });
    await drawer.locator('.el-select').click();
    await expect(a.getByRole('option', { name: courierB.name, exact: true })).toBeVisible();
    await expect(a.getByRole('option', { name: courierA.name, exact: true })).toHaveCount(0);
    await capture(a, '04-destination-store-and-courier-options');
    await a.getByRole('option', { name: courierB.name, exact: true }).click();
    await ui(a, '/merchant/orders/' + order.id + '/actions/assign', 'POST', () => drawer.getByRole('button', { name: '指派配送', exact: true }).click());
    const assigned = await call<Order>(request, '/merchant/orders/' + order.id, admin.token);
    expect(assigned.courierId).toBe(courierB.id); expect(assigned.status).toBe(3);
    await capture(a, '05-platform-assigned-destination-campus-courier');
    checkpoints.push({ name: 'cross-campus inbox selected destination store and assigned only its courier',
      storeBefore: storeA.id, storeAfter: storeB.id, campusA: campusA.id, campusB: campusB.id,
      orderId: order.id, orderNumber: order.number, checkoutId: checkout.id,
      courierAId: courierA.id, courierBId: courierB.id, assignedCourierId: assigned.courierId,
      messageId: message!.id, exceptionId: timedOut!.id, exceptionStage: timedOut!.stage,
      deadlineAt: timedOut!.deadlineAt, exceptionCreatedAt: timedOut!.createdAt,
      naturalStageTimeout: true, clientObservedWaitMs: Date.now() - readyAt,
      orderStatus: assigned.status, expectedPaidCents: '1000' });

    await manager.goto('/admin/messages');
    const ownMessage = manager.locator('.el-table__body tr')
      .filter({ has: manager.getByRole('cell', { name: order.id, exact: true }) }).filter({ hasText: '餐品已备好，请关注取餐或配送' });
    const ownMessages = await call<CursorPage<InboxMessage>>(request, '/me/messages/page?limit=200', managerSession.token);
    const readyMessage = ownMessages.records.find(row => row.orderId === order.id && row.eventType === 'ORDER_READY');
    expect(readyMessage).toBeTruthy(); await expect(ownMessage).toHaveCount(1);
    await ui(manager, '/me/messages/' + readyMessage!.id + '/read', 'POST',
      () => ownMessage.getByRole('button', { name: '前往订单履约', exact: true }).click());
    await expect(manager).toHaveURL(new RegExp('/admin/orders\\?orderId=' + order.id + '$'));
    await expect(manager.locator('.topbar')).toContainText(names.campusB + ' / ' + names.storeB);
    await expect(manager.getByRole('heading', { name: order.number, exact: true })).toBeVisible();
    await capture(manager, '06-manager-opened-own-order-message');
    await Promise.all(responseChecks);
    expect(pageErrors).toEqual([]); expect(unexpectedHttp).toEqual([]); expect(transportErrors).toEqual([]);
    expect(expectedDenials).toHaveLength(3); expect(contexts).toHaveLength(4);
    checkpoints.push({ name: 'normal store manager can create own operator', managerId: managerStaff.id, storeId: storeB.id, operatorId: operator.id });
  } catch (error) {
    for (let index = 0; index < contexts.length; index++) {
      const page = contexts[index].pages()[0]; if (page) await capture(page, 'failure-role-' + index).catch(() => {});
    }
    throw error;
  } finally {
    await Promise.all(responseChecks);
    const output = testInfo.outputPath('authorization-context-evidence.json');
    await fs.writeFile(output, JSON.stringify({ sourceRevision, browserContexts: contexts.length,
      browserRoles: [...roleFor.values()], roleTypes: ['PLATFORM_ADMIN', 'SHOP_MANAGER', 'USER'],
      fixtureBoundary: 'API writes create only campuses, stores, building, category, product and quota. Staff creation/edit/revocation/reset, policy, login/password, order payment/preparation, message read/navigation and dispatch use pages. APIs otherwise only read results. The already-authenticated lock-wait race is verified by targeted backend tests, not claimed as a browser reproduction.',
      pageErrors, unexpectedHttp, transportErrors, expectedDenials, uiRequests, checkpoints,
    }, null, 2));
    await testInfo.attach('authorization-context-evidence', { path: output, contentType: 'application/json' });
    for (const context of contexts) await context.close();
  }
});
