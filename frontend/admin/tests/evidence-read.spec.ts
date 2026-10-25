import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import path from 'node:path';

// Transport-only UI check: every API call is intercepted, with no real login or business write.
test('evidence read busy and budget errors stay distinct from authorization and allow explicit retry', async ({ page }) => {
  const session = { id: '90', token: 'evidence-read-ui', name: '举证读取验收', role: 'PLATFORM_ADMIN', mustChangePassword: false };
  await page.addInitScript(value => {
    sessionStorage.setItem('campus-admin', JSON.stringify(value));
    localStorage.setItem('campus-store', '1');
  }, session);
  const cases = [
    { status: 503, message: '举证读取繁忙，请稍后重试' },
    { status: 429, message: '举证读取过于频繁，请稍后重试' },
    { status: 403, message: '无权查看该订单举证' },
    { status: 503, message: null },
  ];
  const errors: string[] = [], requests: string[] = [], writes: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  const png = await readFile(path.resolve('../client/tooling/fixtures/qr.png'));
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url());
    if (request.method() !== 'GET') { writes.push(request.method() + ' ' + url.pathname); return route.abort(); }
    const ok = (data: unknown) => route.fulfill({ json: { code: 'OK', data } });
    if (url.pathname.startsWith('/api/v1/evidence/')) {
      requests.push(request.headers().authorization);
      const next = cases[requests.length - 1];
      if (next?.message) return route.fulfill({ status: next.status, json: { code: 'TEST_ERROR', message: next.message, data: null } });
      if (next) return route.fulfill({ status: next.status, contentType: 'text/plain', body: 'temporarily unavailable' });
      return route.fulfill({ contentType: 'image/png', headers: { 'Cache-Control': 'no-store' }, body: png });
    }
    if (url.pathname === '/api/v1/merchant/stores/page') return ok({ records: [{ id: '1', campusId: '1', campusName: '验收校区', name: '验收店铺' }], nextCursor: null, hasMore: false });
    if (url.pathname === '/api/v1/platform/aftersales') return ok([{
      id: '1', orderId: '8', storeId: '1', kind: 'FULL', state: 'PENDING', reason: '举证读取验收',
      amountCents: '100', createdAt: '2026-10-25T10:00:00', items: [], version: '0',
      evidence: [{ id: '12345678-1234-1234-1234-123456789abc' }],
    }]);
    return ok([]);
  });
  await page.goto('/admin/operations/aftersales');
  const button = page.getByRole('button', { name: '查看举证 1', exact: true });
  for (let i = 0; i < cases.length; i++) {
    await button.click();
    const message = cases[i]!.message || '举证读取繁忙，请稍后重试';
    await expect(page.locator('.el-message').filter({ hasText: message }).first()).toBeVisible();
    expect(requests.length).toBe(i + 1);
    await expect(page.getByRole('dialog', { name: '售后举证' })).toHaveCount(0);
  }
  await button.click();
  const dialog = page.getByRole('dialog', { name: '售后举证' });
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('img')).toHaveJSProperty('naturalWidth', 370);
  expect(requests).toEqual(Array(5).fill('Bearer ' + session.token));
  expect(writes).toEqual([]);
  expect(errors).toEqual([]);
  expect(await page.evaluate(() => JSON.parse(sessionStorage.getItem('campus-admin')!).token)).toBe(session.token);
});
