import { mount } from '@vue/test-utils';
import { h, nextTick, reactive } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useImportWizard } from './useImportWizard';

const wrappers = [];
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()));
const response = (payload, status = 200) => ({ ok: status < 400, status, json: async () => payload });
const inspected = { format: 'csv', columns: ['id', 'name', 'city'], sampleRows: [['7', 'Ada', 'Paris']], rowCount: 1, sheets: [] };
const previewed = { valid: true, previewHash: 'snapshot-1', summary: { additions: 1 }, rows: [{ row: 2, action: 'CREATE', type: 'Person', dataId: 7, before: null, after: { name: 'Ada' }, errors: [] }] };
function harness() {
  const state = reactive({
    selectedModelKey: 'People:1', isAuthenticated: true,
    authMeta: { actorUsername: 'admin', effectiveUsername: 'admin' },
    canReadCurrentModelData: true, canCreateCurrentModelData: true, canUpdateCurrentModelData: true,
    modelDetails: { objectTypes: [
      { name: 'Entity', attributes: [{ name: 'city', type: 'STRING' }] },
      { name: 'Person', parent: 'Entity', attributes: [{ name: 'name', required: true, type: 'STRING' }] }
    ] },
    apiFetch: vi.fn(async path => response(path.endsWith('/inspect') ? inspected : path.endsWith('/preview') ? previewed : { imported: true })),
    refreshData: vi.fn(async () => {})
  });
  let wizard;
  wrappers.push(mount({ setup() { wizard = reactive(useImportWizard(state)); return () => h('div'); } }));
  return { wizard, state };
}
async function ready(wizard) {
  wizard.selectFile(new File(['id,name,city\n7,Ada,Paris'], 'people.csv', { type: 'text/csv' }));
  await wizard.inspect();
  wizard.objectType = 'Person';
  await nextTick();
  await wizard.generatePreview();
}

describe('import preview lifecycle', () => {
  it('maps inherited attributes and commits the identical file and options with the reviewed hash', async () => {
    const { wizard, state } = harness();
    await ready(wizard);
    expect(wizard.mapping).toEqual({ city: 'city', name: 'name' });
    expect(wizard.idColumn).toBe('id');
    expect(wizard.canCommit).toBe(true);
    const previewBody = state.apiFetch.mock.calls.find(([path]) => path.endsWith('/preview'))[1].body;
    expect(JSON.parse(previewBody.get('mapping'))).toEqual({ city: 'city', name: 'name' });
    expect(previewBody.get('idColumn')).toBe('id');
    await wizard.commit();
    const commitBody = state.apiFetch.mock.calls.find(([path]) => path.endsWith('/commit'))[1].body;
    for (const [name, value] of previewBody) expect(commitBody.get(name)).toEqual(value);
    expect(commitBody.get('previewHash')).toBe('snapshot-1');
    expect(wizard.preview).toBeNull();
    expect(wizard.success).toContain('Import terminé');
    expect(state.refreshData).toHaveBeenCalledWith('People:1');
  });

  it.each(['mapping', 'file', 'model', 'user', 'mode'])('invalidates a reviewed preview after a %s change', async change => {
    const { wizard, state } = harness();
    await ready(wizard);
    if (change === 'mapping') wizard.mapping.name = 'city';
    if (change === 'file') wizard.selectFile(new File(['id,name\n8,Grace'], 'next.csv'));
    if (change === 'model') state.selectedModelKey = 'Other:1';
    if (change === 'user') state.authMeta.effectiveUsername = 'reader';
    if (change === 'mode') wizard.mode = 'upsert';
    expect(wizard.preview).toBeNull();
    expect(wizard.canCommit).toBe(false);
    await wizard.commit();
    expect(state.apiFetch.mock.calls.some(([path]) => path.endsWith('/commit'))).toBe(false);
  });

  it('discards a late preview from a different file even if the client ignores abort', async () => {
    const { wizard, state } = harness();
    await ready(wizard);
    let resolve;
    state.apiFetch.mockImplementationOnce(() => new Promise(done => { resolve = done; }));
    const pending = wizard.generatePreview();
    wizard.selectFile(new File(['id,name\n8,Grace'], 'new.csv'));
    resolve(response(previewed));
    await pending;
    expect(wizard.preview).toBeNull();
    expect(wizard.busy).toBe('');
    expect(wizard.canCommit).toBe(false);
  });

  it('requires a fresh preview after a stale conflict and never retries the commit automatically', async () => {
    const { wizard, state } = harness();
    await ready(wizard);
    state.apiFetch.mockImplementationOnce(async () => response({ error: 'Stale import preview' }, 409));
    await wizard.commit();
    expect(wizard.error).toContain('Prévisualisez à nouveau');
    expect(wizard.preview).toBeNull();
    expect(wizard.canCommit).toBe(false);
    expect(state.refreshData).not.toHaveBeenCalled();
    await wizard.commit();
    expect(state.apiFetch.mock.calls.filter(([path]) => path.endsWith('/commit'))).toHaveLength(1);
  });

  it('blocks invalid previews and revoked update permission', async () => {
    const { wizard, state } = harness();
    await ready(wizard);
    wizard.preview = { ...previewed, valid: false };
    expect(wizard.canCommit).toBe(false);
    wizard.mode = 'upsert';
    await wizard.generatePreview();
    wizard.preview = { ...previewed, summary: { updates: 1 } };
    expect(wizard.canCommit).toBe(true);
    state.canUpdateCurrentModelData = false;
    expect(wizard.canCommit).toBe(false);
    expect(wizard.preview).toBeNull();
  });

  it('allows XML preview without table mappings, preserving the XML format for commit', async () => {
    const { wizard, state } = harness();
    wizard.selectFile(new File(['<DATA/>'], 'data.xml', { type: 'application/xml' }));
    expect(wizard.canPreview).toBe(true);
    await wizard.generatePreview();
    await wizard.commit();
    const body = state.apiFetch.mock.calls.find(([path]) => path.endsWith('/commit'))[1].body;
    expect(body.get('format')).toBe('xml');
    expect(body.has('mapping')).toBe(false);
    expect(body.has('objectType')).toBe(false);
  });

  it('retains workbook sheet choices while requiring a new inspection after choosing a sheet', async () => {
    const { wizard, state } = harness();
    state.apiFetch.mockImplementation(async path => response(path.endsWith('/inspect') ? { ...inspected, sheets: ['People', 'Cities'], sheet: 'People' } : previewed));
    wizard.selectFile(new File(['xlsx'], 'book.xlsx'));
    await wizard.inspect();
    wizard.sheet = 'Cities';
    expect(wizard.sheets).toEqual(['People', 'Cities']);
    expect(wizard.inspection).toBeNull();
    expect(wizard.canPreview).toBe(false);
    await wizard.inspect();
    expect(state.apiFetch.mock.lastCall[1].body.get('sheet')).toBe('Cities');
  });
});
