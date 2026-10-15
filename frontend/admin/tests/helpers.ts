import { test as base, expect, type Page, type APIRequestContext } from '@playwright/test';
import fs from 'node:fs';
import type { Session } from '../../contracts/models';
export function password() {
  if (process.env.ADMIN_PASSWORD) return process.env.ADMIN_PASSWORD;
  const line = fs
    .readFileSync('../../.env', 'utf8')
    .split(/\r?\n/)
    .find((l) => l.startsWith('DEMO_ADMIN_PASSWORD='));
  if (!line) throw new Error('Missing demo password');
  return line.slice(line.indexOf('=') + 1);
}
export async function call<T>(
  request: APIRequestContext,
  path: string,
  token: string,
  method = 'GET',
  data?: unknown,
  headers: Record<string, string> = {},
): Promise<T> {
  const r = await request.fetch('/api/v1' + path, {
    method,
    data,
    headers: { Authorization: 'Bearer ' + token, ...headers },
  });
  const result = await r.json();
  expect(r.ok(), path + ': ' + JSON.stringify(result)).toBeTruthy();
  expect(result.code).toBe('OK');
  return result.data;
}
export const test = base.extend<{}, { adminSession: Session; userSession: Session }>({
  adminSession: [
    async ({ playwright }, use) => {
      const r = await playwright.request.newContext({
        baseURL: process.env.BASE_URL || 'http://localhost:18083',
      });
      const session = await call<Session>(r, '/admin/auth/login', '', 'POST', {
        username: 'admin',
        password: password(),
      });
      await use(session);
      await r.dispose();
    },
    { scope: 'worker' },
  ],
  userSession: [
    async ({ playwright }, use) => {
      const r = await playwright.request.newContext({
        baseURL: process.env.BASE_URL || 'http://localhost:18083',
      });
      const session = await call<Session>(r, '/user/auth/demo', '', 'POST', { account: 3 });
      await use(session);
      await r.dispose();
    },
    { scope: 'worker' },
  ],
});
export async function asSession(page: Page, session: Session, user = false) {
  await page.addInitScript(
    ({ s, u }) => {
      sessionStorage.setItem(u ? 'campus-user' : 'campus-admin', JSON.stringify(s));
      localStorage.setItem('campus-store', '1');
    },
    { s: session, u: user },
  );
}
export { expect };
