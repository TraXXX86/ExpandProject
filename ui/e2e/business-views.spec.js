import { test, expect } from '@playwright/test';
import { mkdir, readFile } from 'node:fs/promises';

test('saved views persist and apply; history, paths and quality render real model data', async ({ page }) => {
  test.skip(!process.env.E2E_ADMIN_PASSWORD, 'Set E2E_ADMIN_PASSWORD for an isolated API and Neo4j.');
  // External decorative fonts and the optional favicon are outside these workflow assertions.
  await page.route('https://fonts.googleapis.com/**', route => route.fulfill({ contentType: 'text/css', body: '' }));
  await page.route('**/favicon.ico', route => route.fulfill({ status: 204 }));
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => {
    if (message.type() === 'error' || /Failed to resolve component/.test(message.text())) errors.push(message.text());
  });
  const capture = async name => {
    if (!process.env.E2E_SCREENSHOT_DIR) return;
    await mkdir(process.env.E2E_SCREENSHOT_DIR, { recursive: true });
    await page.screenshot({ path: `${process.env.E2E_SCREENSHOT_DIR}/${name}.png`, fullPage: true });
  };
  const headers = { 'X-Requested-With': 'ExpandProject' };
  const modelName = `BusinessViews_${Date.now()}`;
  let modelKey, viewId;
  const json = async response => {
    const payload = await response.json();
    expect(response.ok(), JSON.stringify(payload)).toBe(true);
    return payload;
  };
  try {
    await json(await page.request.post('/api/auth/login', { headers, data: { username: process.env.E2E_ADMIN_USERNAME || 'admin', password: process.env.E2E_ADMIN_PASSWORD } }));
    const xml = (await readFile(new URL('../../importdata/src/main/resources/model/example_social_network_model.xml', import.meta.url), 'utf8')).replace('NAME="SocialNetworkModel"', `NAME="${modelName}"`);
    const model = await json(await page.request.post('/api/models', { headers, multipart: { modelFile: { name: 'model.xml', mimeType: 'application/xml', buffer: Buffer.from(xml) } } }));
    modelKey = model.key;
    const createPerson = async (surname, given) => json(await page.request.post('/api/objects', { headers, data: { modelKey, type: 'PERSONNE', attributes: [{ key: 'NOM', value: surname }, { key: 'PRENOM', value: given }] } }));
    const from = await createPerson('SourceBefore', 'Ada');
    const to = await createPerson('Destination', 'Grace');
    await createPerson('Isolated', 'Robin');
    await json(await page.request.post('/api/links', { headers, data: { modelKey, type: 'CONNAIT', fromId: from.id, toId: to.id, attributes: [{ key: 'TYPE_RELATION', value: 'AMI' }] } }));
    await json(await page.request.put(`/api/objects/${from.id}`, { headers, data: { modelKey, attributes: [{ key: 'NOM', value: 'SourceAfter' }] } }));

    await page.goto(`/#/user/table?model=${encodeURIComponent(modelKey)}`);
    await expect(page.getByRole('heading', { name: 'Listez les objets et filtrez rapidement.' })).toBeVisible();
    await page.getByLabel('Rechercher dans les champs du modèle', { exact: true }).fill('Ada');
    await expect(page.getByRole('cell', { name: 'Ada SourceAfter', exact: true })).toBeVisible();
    await page.getByLabel('Colonnes affichées (toutes par défaut)', { exact: true }).focus();
    await page.getByLabel('Colonnes affichées (toutes par défaut)', { exact: true }).press('Enter');
    await page.getByRole('option', { name: 'ID', exact: true }).click();
    await page.getByRole('option', { name: 'Type', exact: true }).click();
    await page.keyboard.press('Escape');
    await page.getByRole('columnheader', { name: /^Type/ }).click();
    await page.getByLabel('Nom de la vue', { exact: true }).fill('Mes personnes Ada');
    const savedResponse = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/api/views'));
    await page.getByRole('button', { name: 'Enregistrer une copie', exact: true }).click();
    const saved = await json(await savedResponse);
    viewId = saved.id;
    expect(saved.state.tableSearch).toBe('Ada');
    expect(saved.state.columns).toEqual(['id', 'type']);
    expect(saved.state.sortBy).toEqual([{ key: 'type', order: 'asc' }]);
    await expect(page.getByText('Vue enregistrée.', { exact: true })).toBeVisible();

    await page.reload();
    await page.getByLabel('Rechercher dans les champs du modèle', { exact: true }).fill('Grace');
    await page.getByLabel('Choisir une vue', { exact: true }).focus();
    await page.getByLabel('Choisir une vue', { exact: true }).press('Enter');
    await page.getByRole('option', { name: 'Mes personnes Ada · privée', exact: true }).click();
    await page.getByRole('button', { name: 'Appliquer', exact: true }).click();
    await expect(page.getByLabel('Rechercher dans les champs du modèle', { exact: true })).toHaveValue('Ada');
    await expect(page.getByRole('columnheader', { name: 'Aperçu', exact: true })).toHaveCount(0);
    await expect(page.getByRole('columnheader', { name: /^Type/ })).toBeVisible();
    await page.getByLabel('Partager avec les lecteurs du modèle', { exact: true }).check();
    const updatedResponse = page.waitForResponse(response => response.request().method() === 'PUT' && response.url().includes('/api/views/'));
    await page.getByRole('button', { name: 'Mettre à jour', exact: true }).click();
    expect((await json(await updatedResponse)).shared).toBe(true);
    await capture('saved-views');

    await page.getByRole('tab', { name: 'Historique', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Historique des modifications', exact: true })).toBeVisible();
    await page.getByLabel('Action', { exact: true }).focus();
    await page.getByLabel('Action', { exact: true }).press('Enter');
    await page.getByRole('option', { name: 'Modification', exact: true }).click();
    const historyResponse = page.waitForResponse(response => response.url().includes('/api/history?') && response.url().includes('action=UPDATE'));
    await page.getByRole('button', { name: 'Rechercher', exact: true }).click();
    expect((await json(await historyResponse)).items).toHaveLength(1);
    const entry = page.locator('.history-entry');
    await expect(entry).toHaveCount(1);
    await entry.locator('summary').click();
    await expect(entry.locator('pre').nth(0)).toContainText('SourceBefore');
    await expect(entry.locator('pre').nth(1)).toContainText('SourceAfter');
    await expect(entry).toContainText('Par admin');
    await capture('history');

    await page.getByRole('tab', { name: 'Chemins', exact: true }).click();
    await page.getByLabel('Objet de départ', { exact: true }).fill('SourceAfter');
    await page.getByRole('listbox', { name: 'Objet de départ', exact: true }).getByRole('option', { name: /SourceAfter/ }).click();
    await page.getByLabel('Objet d’arrivée', { exact: true }).fill('Destination');
    await page.getByRole('listbox', { name: 'Objet d’arrivée', exact: true }).getByRole('option', { name: /Destination/ }).click();
    await page.getByRole('button', { name: 'Chercher les chemins', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Chemin 1 · 1 lien(s)', exact: true })).toBeVisible();
    await expect(page.locator('.path-steps')).toContainText('SourceAfter');
    await expect(page.locator('.path-steps')).toContainText('Destination');
    await expect(page.locator('.path-link')).toContainText('↔');
    await capture('paths');

    await page.getByRole('tab', { name: 'Qualité', exact: true }).click();
    await expect(page.getByText('3 objets et 1 liens.', { exact: false })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Objets isolés', exact: true })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'PERSONNE', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Actualiser le diagnostic', exact: true }).click();
    await expect(page.getByRole('cell', { name: 'Objets isolés', exact: true })).toBeVisible();
    await capture('quality');
    expect(errors).toEqual([]);
  } finally {
    if (viewId) await json(await page.request.delete(`/api/views/${encodeURIComponent(viewId)}?modelKey=${encodeURIComponent(modelKey)}`, { headers }));
    if (modelKey) {
      const response = await page.request.delete(`/api/models/${encodeURIComponent(modelKey)}`, { headers });
      expect(response.ok(), await response.text()).toBe(true);
    }
  }
});
