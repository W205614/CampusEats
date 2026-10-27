import { expect, type Locator, type Page } from '@playwright/test';

async function openListbox(page: Page, select: Locator) {
  await expect(select).toHaveCount(1);
  // Element Plus overlays the input with the displayed selection. Its combobox
  // still identifies the exact teleported list, even while another popper closes.
  const combobox = select.getByRole('combobox');
  await expect(combobox).toHaveAttribute('aria-controls', /\S+/);
  const id = await combobox.getAttribute('aria-controls');
  await select.click();
  const listbox = page.locator('[role="listbox"][id=' + JSON.stringify(id) + ']');
  await expect(listbox).toBeVisible();
  await expect(listbox.getByRole('option')).not.toHaveCount(0);
  return { combobox, listbox };
}

async function moreStores(page: Page, button: Locator, combobox: Locator, listbox: Locator) {
  const count = await listbox.getByRole('option', { includeHidden: true }).count();
  await combobox.press('Escape');
  await expect(combobox).toHaveAttribute('aria-expanded', 'false');
  await expect(button).toBeVisible();
  await expect(button).not.toHaveClass(/is-loading/);
  const pending = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/merchant/stores/page'
    && response.request().method() === 'GET');
  await button.click();
  const response = await pending;
  expect(response.ok()).toBe(true);
  expect((await response.json()).code).toBe('OK');
  // A received response precedes Vue's next render. Wait for the new options,
  // including when the last page removes the load-more button altogether.
  await expect.poll(() => listbox.getByRole('option', { includeHidden: true }).count()).toBeGreaterThan(count);
}

export async function selectStaffOption(page: Page, dialog: Locator, label: string, choice: string) {
  const select = dialog.locator('.el-select').filter({ has: page.getByLabel(label, { exact: true }) });
  for (let round = 0; round < 30; round++) {
    const { combobox, listbox } = await openListbox(page, select);
    const candidate = listbox.getByRole('option', { name: choice, exact: true });
    if (await candidate.count()) {
      await candidate.click();
      await expect(combobox).toHaveAttribute('aria-expanded', 'false');
      return;
    }
    expect(label, 'Only the staff store list supports pagination').toBe('归属店铺');
    await moreStores(page, dialog.getByRole('button', { name: '加载更多归属店铺', exact: true }), combobox, listbox);
  }
  throw new Error('Staff option exceeded the real store page budget: ' + choice);
}

export async function selectAuthorizedStore(page: Page, label: string, maxPages = 30) {
  // The topbar appears before its first request completes. App mounts the actual
  // route only after the authorized store sequence and initial selection settle.
  await expect(page.locator('.content').getByRole('heading', { level: 1 })).toBeVisible();
  const topbar = page.locator('.topbar'), select = topbar.locator('.el-select');
  for (let round = 0; round < maxPages; round++) {
    const { combobox, listbox } = await openListbox(page, select);
    const candidate = listbox.getByRole('option', { name: label, exact: true });
    if (await candidate.count()) {
      await candidate.click();
      await expect(combobox).toHaveAttribute('aria-expanded', 'false');
      await expect(topbar.locator('.el-select__selected-item').filter({ hasText: label })).toBeVisible();
      return;
    }
    await moreStores(page, topbar.getByRole('button', { name: '加载更多店铺', exact: true }), combobox, listbox);
  }
  throw new Error('Target store was not reachable through store pagination: ' + label);
}
