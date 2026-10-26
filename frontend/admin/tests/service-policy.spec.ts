import { test, expect } from '@playwright/test';

// All API responses are fixtures. This is a component/accessibility regression,
// not a substitute for the separate real-backend four-role journey.
test('service policy becomes accessibly editable after loading and after each save', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('campus-admin', JSON.stringify({
      id: '9', token: 'service-policy-ui', name: '时限店长', role: 'SHOP_MANAGER',
      storeId: '1', mustChangePassword: false,
    }));
    localStorage.setItem('campus-store', '1');
  });
  const errors: string[] = [], writes: Record<string, unknown>[] = [];
  page.on('pageerror', error => errors.push(error.message));
  let releaseInitial!: () => void, releaseSave!: () => void;
  const initial = new Promise<void>(resolve => { releaseInitial = resolve; });
  const saving = new Promise<void>(resolve => { releaseSave = resolve; });
  let reads = 0;
  let policy = { acceptMinutes: 10, prepareMinutes: 30, dispatchMinutes: 15,
    pickupMinutes: 15, deliveryMinutes: 60, selfPickupMinutes: 120, version: '0' };
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const ok = (data: unknown) => route.fulfill({ json: { code: 'OK', data } });
    if (url.pathname.endsWith('/stores/page')) return ok({ records: [
      { id: '1', campusId: '1', name: '时限店铺', campusName: '时限校区' },
    ], nextCursor: null, hasMore: false });
    if (url.pathname.endsWith('/order-exceptions/page')) return ok({ records: [], nextCursor: null, hasMore: false });
    if (url.pathname.endsWith('/service-policy')) {
      if (route.request().method() === 'PUT') {
        const body = route.request().postDataJSON() as typeof policy;
        writes.push(body);
        if (writes.length === 1) await saving;
        policy = { ...body, version: String(Number(body.version) + 1) };
        return ok(null);
      }
      if (++reads === 1) await initial;
      return ok(policy);
    }
    return ok([]);
  });
  try {
    await page.goto('/admin/service-operations');
    const accept = page.getByRole('spinbutton').first();
    await expect(accept).toBeDisabled();
    releaseInitial();
    await expect(accept).toBeEnabled();
    await expect(page.getByLabel('接单时限', { exact: true })).toHaveValue('10');
    await accept.fill('1');
    const firstSave = page.waitForRequest(r => r.url().endsWith('/service-policy') && r.method() === 'PUT');
    await page.getByRole('button', { name: '保存服务时限', exact: true }).click();
    await firstSave;
    await expect(accept).toBeDisabled();
    releaseSave();
    await expect(accept).toBeEnabled();
    await expect(accept).toHaveValue('1');
    expect(writes[0]).toMatchObject({ acceptMinutes: 1, version: '0' });
    await accept.fill('2');
    const secondSave = page.waitForResponse(r => r.url().endsWith('/service-policy') && r.request().method() === 'PUT');
    await page.getByRole('button', { name: '保存服务时限', exact: true }).click();
    await secondSave;
    await expect(accept).toBeEnabled();
    await expect(accept).toHaveValue('2');
    expect(writes[1]).toMatchObject({ acceptMinutes: 2, version: '1' });
    expect(errors).toEqual([]);
  } finally { releaseInitial(); releaseSave(); }
});
