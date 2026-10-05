import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const headers = { 'X-Requested-With': 'ExpandProject' };
async function choose(page, label, option) {
  const field = page.getByLabel(label, { exact: true });
  await field.focus();
  await field.press('Enter');
  await page.getByRole('option', { name: option, exact: true }).click();
}
async function action(page, label, endpoint, expectedStatus = 200) {
  const pending = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith(`/api/imports/${endpoint}`));
  await page.getByRole('button', { name: label, exact: true }).click();
  const response = await pending;
  const result = await response.json();
  expect(response.status(), JSON.stringify(result)).toBe(expectedStatus);
  return result;
}
async function uploadCsv(page, contents) {
  await page.locator('input[type=file]').setInputFiles({ name: 'people.csv', mimeType: 'text/csv', buffer: Buffer.from(contents) });
  await action(page, 'Analyser le fichier', 'inspect');
}

test('imports CSV and Excel with reviewed differences, and requires a fresh preview after concurrent changes', async ({ page }) => {
  test.skip(!process.env.E2E_ADMIN_PASSWORD, 'Set E2E_ADMIN_PASSWORD for the isolated API.');
  test.setTimeout(120_000);
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  let modelKey;
  try {
    const login = await page.request.post('/api/auth/login', { headers, data: { username: process.env.E2E_ADMIN_USERNAME || 'admin', password: process.env.E2E_ADMIN_PASSWORD } });
    expect(login.ok()).toBe(true);
    const name = `E2E_Import_${Date.now()}`;
    const xml = (await readFile(new URL('../../importdata/src/main/resources/model/example_social_network_model.xml', import.meta.url), 'utf8')).replace('NAME="SocialNetworkModel"', `NAME="${name}"`);
    const imported = await page.request.post('/api/models', { headers, multipart: { modelFile: { name: 'model.xml', mimeType: 'application/xml', buffer: Buffer.from(xml) } } });
    const createdModel = await imported.json();
    expect(imported.ok(), JSON.stringify(createdModel)).toBe(true);
    modelKey = createdModel.key;
    await page.goto(`/#/user/import-data?model=${encodeURIComponent(modelKey)}`);
    await expect(page.getByRole('heading', { name: 'Du fichier aux données, en toute visibilité.' })).toBeVisible();
    await uploadCsv(page, 'ID,NOM,PRENOM\n1,Lovelace,Ada\n');
    await choose(page, 'Type d’objet à importer', 'PERSONNE');
    await expect(page.getByLabel('Colonne d’identifiant stable', { exact: true })).toHaveValue('ID');
    await expect(page.getByLabel('Nom *', { exact: true })).toHaveValue('NOM');
    const preview = await action(page, 'Prévisualiser les changements', 'preview');
    expect(preview.valid).toBe(true);
    expect(preview.summary.additions).toBe(1);
    await expect(page.getByText('1 ajouts', { exact: true })).toBeVisible();
    await action(page, 'Confirmer l’import', 'commit');
    await expect(page.getByText('Import terminé. Les données ont été enregistrées.', { exact: true })).toBeVisible();

    await uploadCsv(page, 'ID,NOM,PRENOM\n1,Byron,Ada\n');
    await choose(page, 'Comportement de l’import', 'Ajouter et mettre à jour');
    const changed = await action(page, 'Prévisualiser les changements', 'preview');
    expect(changed.summary.updates).toBe(1);
    expect(changed.rows[0].before.NOM).toBe('Lovelace');
    expect(changed.rows[0].after.NOM).toBe('Byron');
    await expect(page.getByRole('cell', { name: /NOM : Lovelace/ })).toBeVisible();
    await expect(page.getByRole('cell', { name: /NOM : Byron/ })).toBeVisible();

    // Another authorized edit makes the reviewed state obsolete before confirmation.
    const dataResponse = await page.request.get(`/api/data?modelKey=${encodeURIComponent(modelKey)}`);
    const data = await dataResponse.json();
    const object = data.objects[0];
    const edited = await page.request.put(`/api/objects/${object.id}`, { headers, data: { modelKey, attributes: [{ key: 'NOM', value: 'King' }] } });
    expect(edited.ok(), await edited.text()).toBe(true);
    await action(page, 'Confirmer l’import', 'commit', 409);
    await expect(page.getByText('Les données ont changé depuis la prévisualisation. Prévisualisez à nouveau avant de confirmer.', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Confirmer l’import', exact: true })).toHaveCount(0);
    const refreshed = await action(page, 'Prévisualiser les changements', 'preview');
    expect(refreshed.rows[0].before.NOM).toBe('King');
    await action(page, 'Confirmer l’import', 'commit');

    await page.locator('input[type=file]').setInputFiles(fileURLToPath(new URL('./fixtures/people.xlsx', import.meta.url)));
    const workbook = await action(page, 'Analyser le fichier', 'inspect');
    expect(workbook.sheets).toContain('Personnes');
    await choose(page, 'Feuille à importer', 'Personnes');
    await action(page, 'Analyser le fichier', 'inspect');
    await choose(page, 'Comportement de l’import', 'Ajouter uniquement');
    const excelPreview = await action(page, 'Prévisualiser les changements', 'preview');
    expect(excelPreview.valid).toBe(true);
    expect(excelPreview.summary.additions).toBe(1);
    expect(excelPreview.rows[0].after.NOM).toBe('Hopper');
    await action(page, 'Confirmer l’import', 'commit');
    const xmlData = '<DATAS><OBJECTS><OBJECT TYPE="PERSONNE" ID="3"><ATTRIBUTE KEY="NOM" VALUE="Hamilton"/><ATTRIBUTE KEY="PRENOM" VALUE="Margaret"/></OBJECT></OBJECTS><LINKS><LINK TYPE="CONNAIT"><ATTRIBUTE KEY="TYPE_RELATION" VALUE="COLLEGUE"/><OBJ_LINK_A ID="3" TYPE="PERSONNE"/><OBJ_LINK_B ID="1" TYPE="PERSONNE"/></LINK></LINKS></DATAS>';
    await page.locator('input[type=file]').setInputFiles({ name: 'data.xml', mimeType: 'application/xml', buffer: Buffer.from(xmlData) });
    const xmlPreview = await action(page, 'Prévisualiser les changements', 'preview');
    expect(xmlPreview.valid).toBe(true);
    expect(xmlPreview.summary.additions).toBe(2);
    await expect(page.getByText('PERSONNE #3 → PERSONNE #1', { exact: true })).toBeVisible();
    await action(page, 'Confirmer l’import', 'commit');
    const finalData = await (await page.request.get(`/api/data?modelKey=${encodeURIComponent(modelKey)}`)).json();
    expect(finalData.totalObjects).toBe(3);
    expect(finalData.totalLinks).toBe(1);
    expect(errors).toEqual([]);
  } finally {
    if (modelKey) await page.request.delete(`/api/models/${encodeURIComponent(modelKey)}`, { headers });
  }
});
