import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';
const headers = { 'X-Requested-With': 'ExpandProject' };
const workflowXml = version => `<WORKFLOW ID="review" VERSION="${version}" LABEL="Validation" INITIAL_STATE="draft"><OBJECT_TYPES><TYPE_REF NAME="PERSONNE" INCLUDE_SUBTYPES="true"/></OBJECT_TYPES><STATES><STATE CODE="draft" LABEL="Brouillon"/><STATE CODE="done" LABEL="Validé" TERMINAL="true"/></STATES><TRANSITIONS><TRANSITION ID="approve" FROM="draft" TO="done" LABEL="Valider"/></TRANSITIONS></WORKFLOW>`;
async function choose(page, label, option) {
  const field = page.getByRole('combobox', { name: label, exact: true }); await field.focus(); await field.press('Enter');
  await page.getByRole('listbox', { name: label, exact: true }).getByRole('option', { name: option, exact: true }).click();
  await field.press('Tab');
}
async function action(page, label, endpoint, status = 200) {
  const pending = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith(endpoint));
  await page.getByRole('button', { name: label, exact: true }).click();
  const response = await pending, body = await response.json(); expect(response.status(), JSON.stringify(body)).toBe(status); return body;
}
test('administers independent workflow versions with reviewed initialization and migration', async ({ page }, testInfo) => {
  test.skip(!process.env.E2E_ADMIN_PASSWORD, 'Set E2E_ADMIN_PASSWORD for the isolated API.');
  test.setTimeout(120_000); let modelKey; const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.text().includes('Failed to resolve component')) errors.push(message.text()); });
  try {
    const login = await page.request.post('/api/auth/login', { headers, data: { username: process.env.E2E_ADMIN_USERNAME || 'admin', password: process.env.E2E_ADMIN_PASSWORD } }); expect(login.ok()).toBe(true);
    const name = `E2E_WorkflowAdmin_${Date.now()}`;
    const xml = (await readFile(new URL('../../importdata/src/main/resources/model/example_social_network_model.xml', import.meta.url), 'utf8')).replace('NAME="SocialNetworkModel"', `NAME="${name}"`);
    const imported = await page.request.post('/api/models', { headers, multipart: { modelFile: { name: 'model.xml', mimeType: 'application/xml', buffer: Buffer.from(xml) } } });
    const model = await imported.json(); expect(imported.ok(), JSON.stringify(model)).toBe(true); modelKey = model.key;
    const createObject = async (name, type = 'PERSONNE') => {
      const response = await page.request.post('/api/objects', { headers, data: { modelKey, type, attributes: [{ key: 'NOM', value: name }, { key: 'PRENOM', value: 'Test' }] } });
      expect(response.ok(), await response.text()).toBe(true); return response.json();
    };
    const existing = await createObject('Avant', 'EMPLOYE');
    await page.goto(`/#/model-admin/workflows?model=${encodeURIComponent(modelKey)}`);
    await expect(page.getByRole('heading', { name: 'Workflows des objets' })).toBeVisible();
    const upload = async version => {
      await page.locator('input[type=file]').setInputFiles({ name: `workflow-${version}.xml`, mimeType: 'application/xml', buffer: Buffer.from(workflowXml(version)) });
      const pending = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/api/workflows'));
      await page.getByRole('button', { name: 'Charger le workflow', exact: true }).click();
      const response = await pending; expect(response.ok(), await response.text()).toBe(true);
      await expect(page.getByText('Workflow chargé. Vous pouvez maintenant l’affecter à un type d’objet.', { exact: true })).toBeVisible();
    };
    await upload('1'); await choose(page, 'Workflow cible', 'Validation — 1');
    const activation = await action(page, 'Prévisualiser les changements', '/api/workflows/activation/preview'); expect(activation.affectsExistingObjects).toBe(false);
    await action(page, 'Confirmer les changements', '/api/workflows/activation/commit');
    await createObject('Après');
    await choose(page, 'Opération', 'Initialiser des objets sans workflow');
    const candidates = await action(page, 'Rechercher les objets concernés', '/api/workflows/migration/preview');
    expect(candidates.items.map(item => item.id)).toContain(existing.id); expect(candidates.items[0].objectType).toBe('EMPLOYE');
    await page.getByRole('row').filter({ has: page.getByRole('cell', { name: String(existing.id), exact: true }) }).getByRole('checkbox').check();
    const initialization = await action(page, 'Prévisualiser les changements', '/api/workflows/migration/preview'); expect(initialization.items).toHaveLength(1);
    await action(page, 'Confirmer les changements', '/api/workflows/migration/commit');
    await upload('2'); await choose(page, 'Workflow cible', 'Validation — 2'); await choose(page, 'Opération', 'Migrer des objets vers cette version');
    await choose(page, 'Workflow source', 'Validation — 1'); await choose(page, 'Remplacer le statut Brouillon par', 'Brouillon'); await choose(page, 'Remplacer le statut Validé par', 'Validé');
    const migrationCandidates = await action(page, 'Rechercher les objets concernés', '/api/workflows/migration/preview'); expect(migrationCandidates.items).toHaveLength(2);
    await page.getByRole('columnheader').filter({ has: page.getByRole('checkbox') }).getByRole('checkbox').check();
    const migration = await action(page, 'Prévisualiser les changements', '/api/workflows/migration/preview'); expect(migration.items).toHaveLength(2);
    await page.screenshot({ path: testInfo.outputPath('workflow-admin.png'), fullPage: true });
    await action(page, 'Confirmer les changements', '/api/workflows/migration/commit');
    await page.getByRole('button', { name: 'Validation — 2', exact: true }).click();
    await page.getByRole('button', { name: 'Supprimer cette version', exact: true }).click();
    const deleted = page.waitForResponse(response => response.request().method() === 'DELETE' && response.url().endsWith('/api/workflows'));
    await page.getByRole('button', { name: 'Confirmer la suppression', exact: true }).click(); expect((await deleted).status()).toBe(409);
    await expect(page.getByRole('alert').filter({ hasText: /used|utilis/i })).toBeVisible(); expect(errors).toEqual([]);
  } finally { if (modelKey) await page.request.delete(`/api/models/${encodeURIComponent(modelKey)}`, { headers }); }
});
