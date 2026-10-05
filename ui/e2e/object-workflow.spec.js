import { test, expect } from '@playwright/test';
import { readFile, mkdir } from 'node:fs/promises';

test('object workflow transitions, concurrent edit recovery, terminal state and persisted status filters', async ({ page }) => {
  test.skip(!process.env.E2E_ADMIN_PASSWORD, 'Requires isolated API and Neo4j.');
  await page.route('https://fonts.googleapis.com/**', route => route.fulfill({ contentType: 'text/css', body: '' }));
  await page.route('**/favicon.ico', route => route.fulfill({ status: 204 }));
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (/Failed to resolve component/.test(message.text())) errors.push(message.text()); });
  const headers = { 'X-Requested-With': 'ExpandProject' };
  const json = async response => { const value = await response.json(); expect(response.ok(), JSON.stringify(value)).toBe(true); return value; };
  let modelKey;
  try {
    await json(await page.request.post('/api/auth/login', { headers, data: { username: process.env.E2E_ADMIN_USERNAME || 'admin', password: process.env.E2E_ADMIN_PASSWORD } }));
    const xml = (await readFile(new URL('../../importdata/src/main/resources/model/example_social_network_model.xml', import.meta.url), 'utf8')).replace('NAME="SocialNetworkModel"', `NAME="WorkflowObject_${Date.now()}"`);
    modelKey = (await json(await page.request.post('/api/models', { headers, multipart: { modelFile: { name: 'model.xml', mimeType: 'application/xml', buffer: Buffer.from(xml) } } }))).key;
    const workflowXml = '<WORKFLOW ID="approval" VERSION="1" LABEL="Validation" INITIAL_STATE="draft"><OBJECT_TYPES><TYPE_REF NAME="PERSONNE" /></OBJECT_TYPES><STATES><STATE CODE="draft" LABEL="Brouillon" /><STATE CODE="review" LABEL="À valider" /><STATE CODE="approved" LABEL="Validé" TERMINAL="true" /></STATES><TRANSITIONS><TRANSITION ID="submit" FROM="draft" TO="review" LABEL="Soumettre" /><TRANSITION ID="approve" FROM="review" TO="approved" LABEL="Valider" /><TRANSITION ID="return" FROM="review" TO="draft" LABEL="Corriger" /></TRANSITIONS></WORKFLOW>';
    await json(await page.request.post('/api/workflows', { headers, multipart: { modelKey, workflowFile: { name: 'workflow.xml', mimeType: 'application/xml', buffer: Buffer.from(workflowXml) } } }));
    const activation = { modelKey, id: 'approval', version: '1' };
    const preview = await json(await page.request.post('/api/workflows/activation/preview', { headers, data: activation }));
    await json(await page.request.post('/api/workflows/activation/commit', { headers, data: { ...activation, previewHash: preview.previewHash } }));
    const object = await json(await page.request.post('/api/objects', { headers, data: { modelKey, type: 'PERSONNE', attributes: [{ key: 'NOM', value: 'WorkflowCase' }, { key: 'PRENOM', value: 'Ada' }] } }));
    await page.goto(`/#/user/table?model=${encodeURIComponent(modelKey)}`);
    await page.getByRole('button', { name: 'Voir', exact: true }).click();
    const section = page.getByRole('region', { name: 'Workflow de l’objet' });
    await expect(section).toContainText('Brouillon');
    await section.getByRole('button', { name: 'Soumettre', exact: true }).click();
    await expect(page.getByRole('dialog')).toContainText('Brouillon');
    await expect(page.getByRole('dialog')).toContainText('À valider');
    // Another operator transitions after the user opened the confirmation.
    const current = await json(await page.request.get(`/api/objects/${object.id}/workflow?modelKey=${encodeURIComponent(modelKey)}`));
    await json(await page.request.post(`/api/objects/${object.id}/workflow/transitions`, { headers, data: { modelKey, transitionId: 'submit', expectedRevision: current.workflow.revision, objectUuid: current.objectUuid } }));
    await page.getByRole('dialog').getByRole('button', { name: 'Confirmer', exact: true }).click();
    await expect(section).toContainText('Cet objet a changé');
    await expect(section).toContainText('À valider');
    await section.getByRole('button', { name: 'Changer le statut', exact: true }).click();
    await page.getByText('Valider', { exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Confirmer', exact: true }).click();
    await expect(section).toContainText('Statut mis à jour');
    await expect(section).toContainText('Validé');
    if (process.env.E2E_SCREENSHOT_DIR) { await mkdir(process.env.E2E_SCREENSHOT_DIR, { recursive: true }); await page.screenshot({ path: `${process.env.E2E_SCREENSHOT_DIR}/object-workflow.png`, fullPage: true }); }
    await expect(section.getByRole('button', { name: 'Changer le statut', exact: true })).toHaveCount(0);
    await page.getByLabel('Statut du workflow', { exact: true }).focus();
    await page.getByLabel('Statut du workflow', { exact: true }).press('Enter');
    const filtered = page.waitForResponse(response => response.url().includes('/api/data?') && response.url().includes('workflowStatus=approved'));
    await page.getByRole('option', { name: 'Validé (approved)', exact: true }).click();
    expect((await json(await filtered)).totalObjects).toBe(1);
    await page.reload();
    await expect(page.getByLabel('Statut du workflow', { exact: true })).toHaveValue('Validé (approved)');
    const events = await json(await page.request.get(`/api/history?modelKey=${encodeURIComponent(modelKey)}&action=TRANSITION`));
    expect(events.items).toHaveLength(2);
    expect(errors).toEqual([]);
  } finally {
    if (modelKey) expect((await page.request.delete(`/api/models/${encodeURIComponent(modelKey)}`, { headers })).ok()).toBe(true);
  }
});
