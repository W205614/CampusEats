import { test as base, expect, type APIRequestContext, type Page } from '@playwright/test';
import fs from 'node:fs';
import { randomBytes } from 'node:crypto';
import type { Address, Building, Cart, Order, Page as OrderPage, Shop } from '../../contracts/models';

function adminPassword() {
  if (process.env.ADMIN_PASSWORD) return process.env.ADMIN_PASSWORD;
  const config = Object.fromEntries(fs.readFileSync('../../.env', 'utf8').split(/\r?\n/)
    .filter(line => line.includes('=') && !line.startsWith('#'))
    .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
  if (!config.DEMO_ADMIN_PASSWORD) throw new Error('Set ADMIN_PASSWORD or configure .env');
  return config.DEMO_ADMIN_PASSWORD;
}

type Admin = <T = any>(path: string, method?: string, data?: unknown) => Promise<T>;
// A full UI suite can legitimately exhaust the one-minute login window.
// Retry only the explicit limit response; all other errors remain failures.
async function loginWhenAllowed(send: () => Promise<{ json(): Promise<any> }>) {
  let result: any;
  let failure: unknown;
  await expect.poll(async () => {
    try {
      result = await (await send()).json();
      return result.code === 'RATE_LIMITED';
    } catch (error) {
      failure = error;
      return false;
    }
  }, { timeout: 65000, intervals: [1000, 2000, 5000] }).toBe(false);
  if (failure) throw failure;
  expect(result.code).toBe('OK');
  return result.data;
}

function authenticated(request: APIRequestContext, prefix: string, token: string): Admin {
  return async (path, method = 'GET', data) => {
    const response = await request.fetch(prefix + path, {
      method, data, headers: { Authorization: 'Bearer ' + token },
    });
    const result = await response.json();
    expect(response.ok(), `${method} ${path}: ${result.code}`).toBeTruthy();
    expect(result.code).toBe('OK');
    return result.data;
  };
}

type DemoData = { name: string; trackAccount(account: number): Promise<void> };
export const test = base.extend<{
  admin: Admin; shopState: void; demoData: DemoData;
}, { adminToken: string }>({
  adminToken: [async ({ playwright }, use) => {
    const request = await playwright.request.newContext({ baseURL: process.env.BASE_URL || 'http://localhost:18083' });
    try {
      const login = await loginWhenAllowed(() => request.post('/api/v1/admin/auth/login', {
        data: { username: 'admin', password: adminPassword() },
      }));
      try { await use(login.token); }
      finally { await authenticated(request, '/api/v1/admin', login.token)('/auth/logout', 'POST'); }
    } finally { await request.dispose(); }
  }, { scope: 'worker', timeout: 90000 }],
  admin: async ({ request, adminToken }, use) => {
    await use(authenticated(request, '/api/v1/admin', adminToken));
  },
  shopState: [async ({ admin }, use) => {
    const original = await admin<Shop>('/shop');
    try {
      await admin('/shop', 'PUT', { ...original, open: true, hours: ['00:00-24:00'], deliveryFee: 6, packagingFee: 1 });
      await use();
    } finally { await admin('/shop', 'PUT', original); }
  }, { auto: true }],
  // Opt in only for tests that write demo users' addresses, carts and orders.
  demoData: async ({ request, admin }, use) => {
    const name = 'e2e-' + randomBytes(6).toString('hex');
    const users: { api: Admin; defaultAddress?: string; createdDefault?: string }[] = [];
    const data: DemoData = {
      name,
      async trackAccount(account) {
        const login = await loginWhenAllowed(() => request.post('/api/v1/user/auth/demo', { data: { account } }));
        const api = authenticated(request, '/api/v1/user', login.token);
        const cart = await api<Cart>('/cart');
        expect(cart.items, 'Demo cart must be empty before browser acceptance').toHaveLength(0);
        const addresses = await api<Address[]>('/addresses');
        const user: (typeof users)[number] = { api, defaultAddress: addresses.find(address => address.isDefault)?.id };
        users.push(user);
        if (!addresses.some(address => address.isDefault)) {
          const buildings = await api<Building[]>('/buildings');
          expect(buildings.length, 'An enabled delivery building is required').toBeGreaterThan(0);
          const created = await api('/addresses', 'POST', { buildingId: buildings[0].id, room: '101', consignee: '默认地址验收-' + randomBytes(6).toString('hex'), phone: '13800000000' });
          user.createdDefault = created.id;
        }
      },
    };
    try { await use(data); }
    finally {
      for (const user of users) {
        try {
          let page = 1;
          let orders: OrderPage<Order>;
          do {
            orders = await user.api<OrderPage<Order>>('/orders?size=100&page=' + page++);
            for (const order of orders.records.filter(order => order.consignee === name && order.status < 5)) {
              await admin('/orders/' + order.id + '/cancel', 'POST', { version: order.version, reason: '浏览器验收收尾' });
              if (order.payStatus === 1) {
                await expect.poll(async () => (await admin<Order>('/orders/' + order.id)).payStatus, { timeout: 15000 }).toBe(2);
              }
            }
          } while ((page - 1) * 100 < Number(orders.total));
          const cart = await user.api<Cart>('/cart');
          if (cart.items.length) await user.api('/cart/clear', 'POST', { version: cart.cartVersion });
          if (user.defaultAddress) await user.api('/addresses/' + user.defaultAddress + '/default', 'POST');
          for (const address of await user.api<Address[]>('/addresses')) {
            if (address.consignee === name || address.id === user.createdDefault) await user.api('/addresses/' + address.id, 'DELETE');
          }
        } finally { await user.api('/auth/logout', 'POST'); }
      }
    }
  },
});
export { expect };

export async function adminLogin(page: Page, username = 'admin', password = adminPassword()) {
  await page.goto('/admin/');
  await page.getByPlaceholder('员工账户').fill(username);
  await page.getByPlaceholder('启动脚本生成的初始密码').fill(password);
  await loginWhenAllowed(async () => {
    const response = page.waitForResponse(response => response.url().endsWith('/api/v1/admin/auth/login') && response.request().method() === 'POST');
    await page.getByRole('button', { name: '登录工作台' }).click();
    return response;
  });
}

export async function userLogin(page: Page, account: number) {
  await page.goto('/app/pages/login/index');
  await loginWhenAllowed(async () => {
    const response = page.waitForResponse(response => response.url().endsWith('/api/v1/user/auth/demo') && response.request().method() === 'POST');
    await page.getByText('进入演示用户 ' + account, { exact: true }).click();
    return response;
  });
  await expect(page.getByText('今天，也好好吃饭。', { exact: true })).toBeVisible();
}
