import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

test('cookie login, model import, object creation, search, shared route, and logout', async ({ page }) => {
  test.skip(!process.env.E2E_ADMIN_PASSWORD, 'Set E2E_ADMIN_PASSWORD for an isolated running API and Neo4j.');
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const modelName = `E2E_${Date.now()}`;
  const xml = (await readFile(new URL('../../importdata/src/main/resources/model/example_social_network_model.xml', import.meta.url), 'utf8')).replace('NAME="SocialNetworkModel"', `NAME="${modelName}"`);
  let modelKey;
  try {
    await page.goto('/');
    await expect(page.getByLabel('Login', { exact: true })).toHaveValue('');
    await page.getByLabel('Login', { exact: true }).fill(process.env.E2E_ADMIN_USERNAME || 'admin');
    await page.getByLabel('Mot de passe', { exact: true }).fill(process.env.E2E_ADMIN_PASSWORD);
    const loginResponse = page.waitForResponse(response => response.url().endsWith('/api/auth/login'));
    await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
    const login = await loginResponse;
    expect(login.ok()).toBe(true);
    expect(await login.json()).not.toHaveProperty('token');
    await page.getByRole('button', { name: 'Entrer dans le portail administration', exact: true }).click();
    await page.getByRole('tab', { name: 'Import modèle', exact: true }).click();
    await page.locator('input[type=file]').setInputFiles({ name: 'model.xml', mimeType: 'application/xml', buffer: Buffer.from(xml) });
    const importResponse = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/api/models'));
    await page.getByRole('button', { name: 'Charger le modèle', exact: true }).click();
    const imported = await importResponse;
    const payload = await imported.json();
    expect(imported.ok(), JSON.stringify(payload)).toBe(true);
    modelKey = payload.key;
    await expect(page.getByText(`Modèle chargé en base (${modelName}).`, { exact: true })).toBeVisible();

    await page.getByRole('button', { name: 'Changer de portail', exact: true }).click();
    await page.getByRole('button', { name: 'Entrer dans le portail métier', exact: true }).click();
    await page.getByRole('tab', { name: 'Création', exact: true }).click();
    await page.getByLabel("Type d'objet", { exact: true }).focus();
    await page.getByLabel("Type d'objet", { exact: true }).press('Enter');
    await page.getByRole('option', { name: 'PERSONNE', exact: true }).click();
    await page.getByLabel('Nom *', { exact: true }).fill("O'Connor BrowserSmoke");
    await page.getByLabel('Prénom *', { exact: true }).fill('Ada');
    const createResponse = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/api/objects'));
    await page.getByRole('button', { name: "Creer l'objet", exact: true }).click();
    const created = await createResponse;
    expect(created.ok(), JSON.stringify(await created.json())).toBe(true);
    await expect(page.getByText(/Objet créé \(ID/)).toBeVisible();

    await page.getByLabel('Nom *', { exact: true }).fill('Peer');
    await page.getByLabel('Prénom *', { exact: true }).fill('Grace');
    const peerResponse = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/api/objects'));
    await page.getByRole('button', { name: "Creer l'objet", exact: true }).click();
    expect((await peerResponse).ok()).toBe(true);
    await page.getByLabel('Type de lien', { exact: true }).focus();
    await page.getByLabel('Type de lien', { exact: true }).press('Enter');
    await page.getByRole('option', { name: /CONNAIT/ }).click();
    await page.getByLabel('Objet source', { exact: true }).fill('BrowserSmoke');
    await page.getByRole('option', { name: /BrowserSmoke/ }).click();
    await page.getByLabel('Objet cible', { exact: true }).fill('Peer');
    await page.getByRole('option', { name: /Peer/ }).click();
    const linkResponse = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/api/links'));
    await page.getByRole('button', { name: 'Creer le lien', exact: true }).click();
    expect((await linkResponse).ok()).toBe(true);
    await page.getByLabel('Lien', { exact: true }).focus();
    await page.getByLabel('Lien', { exact: true }).press('Enter');
    await page.getByRole('option', { name: /CONNAIT/ }).click();
    await page.getByLabel('TYPE_RELATION', { exact: true }).fill('AMI');
    const editResponse = page.waitForResponse(response => response.request().method() === 'PUT' && response.url().includes('/api/links/'));
    await page.getByRole('button', { name: 'Enregistrer', exact: true }).click();
    expect((await editResponse).ok()).toBe(true);
    await expect(page.getByLabel('TYPE_RELATION', { exact: true })).toHaveValue('AMI');
    page.once('dialog', dialog => dialog.accept());
    const deleteResponse = page.waitForResponse(response => response.request().method() === 'DELETE' && response.url().includes('/api/links/'));
    await page.getByRole('button', { name: 'Supprimer', exact: true }).click();
    expect((await deleteResponse).ok()).toBe(true);

    await page.getByRole('tab', { name: 'Recherche', exact: true }).click();
    await page.getByLabel('Recherche plein texte', { exact: true }).fill("O'Connor");
    await expect(page.getByRole('cell', { name: /NOM.*BrowserSmoke|Nom.*BrowserSmoke/ })).toBeVisible();
    await expect(page).toHaveURL(/q=O%27Connor/);
    await page.reload();
    await expect(page.getByLabel('Recherche plein texte', { exact: true })).toHaveValue("O'Connor");
    await expect(page.getByRole('cell', { name: /NOM.*BrowserSmoke|Nom.*BrowserSmoke/ })).toBeVisible();
    expect(await page.evaluate(() => localStorage.getItem('expand.authToken'))).toBeNull();
    expect(errors).toEqual([]);
  } finally {
    // Cleanup targets only the unique model created by this test.
    if (modelKey) await page.request.delete(`/api/models/${encodeURIComponent(modelKey)}`, { headers: { 'X-Requested-With': 'ExpandProject' } });
  }
  await page.getByRole('button', { name: 'Admin plateforme', exact: true }).click();
  await page.getByText('Se déconnecter', { exact: true }).click();
  await expect(page.getByLabel('Login', { exact: true })).toBeVisible();
});
