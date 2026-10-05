import { mount } from '@vue/test-utils';
import { h, reactive } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useWorkflowAdmin } from './useWorkflowAdmin';
const wrappers = [];
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()));
const workflow = { id: 'review', version: '1', initialState: 'draft', objectTypes: [{ name: 'Person' }], states: [{ code: 'draft' }, { code: 'done' }], transitions: [] };
const response = (body, status = 200) => ({ ok: status < 400, status, json: async () => body });
function harness() {
  const state = reactive({ selectedModelKey: 'People:1', authEpoch: 1, isAuthenticated: true,
    authMeta: { actorUsername: 'admin', effectiveUsername: 'admin' }, canReadCurrentModelData: true, canManageCurrentWorkflows: true,
    modelDetails: { objectTypes: [{ name: 'Person' }] }, readJson: response => response.json(), refreshData: vi.fn(),
    apiFetch: vi.fn(async path => response(path.includes('?') ? { items: [workflow], bindings: [] } : { previewHash: 'reviewed', items: [{ id: 12, objectType: 'Person', fromState: null, toState: 'draft' }] })) });
  let admin;
  wrappers.push(mount({ setup() { admin = reactive(useWorkflowAdmin(state)); return () => h('div'); } }));
  return { state, admin };
}
async function ready(admin) { await admin.load(); admin.selected = 'review@1'; await admin.generatePreview(); }
describe('workflow administration', () => {
  it('uploads independent XML and activates only the reviewed version', async () => {
    const { state, admin } = harness(); admin.selectFile(new File(['<WORKFLOW/>'], 'review.xml')); await admin.upload();
    const upload = state.apiFetch.mock.calls.find(([path]) => path === '/api/workflows');
    expect(upload[1].body.get('modelKey')).toBe('People:1'); expect(upload[1].body.get('workflowFile').name).toBe('review.xml');
    admin.selected = 'review@1'; await admin.generatePreview(); await admin.confirm();
    const commit = state.apiFetch.mock.calls.find(([path]) => path.endsWith('/activation/commit'));
    expect(JSON.parse(commit[1].body)).toEqual({ modelKey: 'People:1', id: 'review', version: '1', previewHash: 'reviewed' });
    expect(admin.preview).toBeNull();
  });
  it.each(['model', 'session', 'permission', 'target', 'operation'])('invalidates confirmation on %s change', async change => {
    const { state, admin } = harness(); await ready(admin);
    if (change === 'model') state.selectedModelKey = 'Other:1';
    if (change === 'session') state.authEpoch++;
    if (change === 'permission') state.canManageCurrentWorkflows = false;
    if (change === 'target') admin.selected = 'review@2';
    if (change === 'operation') admin.operation = 'initialize';
    expect(admin.preview).toBeNull(); await admin.confirm(); expect(state.apiFetch.mock.calls.some(([path]) => path.endsWith('/commit'))).toBe(false);
  });
  it('requires explicit object selection and commits exactly those IDs', async () => {
    const { state, admin } = harness(); await ready(admin); admin.operation = 'initialize'; await admin.loadCandidates();
    expect(admin.candidates).toHaveLength(1); expect(admin.ready).toBe(false);
    admin.selectedIds = [12]; await admin.generatePreview(); await admin.confirm();
    const commit = state.apiFetch.mock.calls.find(([path]) => path.endsWith('/migration/commit'));
    expect(JSON.parse(commit[1].body)).toMatchObject({ mode: 'initialize', objectIds: [12], objectTypes: [], previewHash: 'reviewed' });
  });
  it('invalidates selection previews when mapping changes', async () => {
    const { admin } = harness(); await ready(admin); admin.operation = 'migrate'; admin.source = 'review@1';
    admin.mapping = { draft: 'draft', done: 'done' }; admin.selectedIds = [12]; await admin.generatePreview();
    expect(admin.canConfirm).toBe(true); admin.mapping.done = 'draft'; expect(admin.canConfirm).toBe(false);
  });
  it('discards stale responses even if abort is ignored', async () => {
    const { state, admin } = harness(); await admin.load(); admin.selected = 'review@1';
    let resolve; state.apiFetch.mockImplementationOnce(() => new Promise(done => { resolve = done; }));
    const pending = admin.generatePreview(); state.authMeta.effectiveUsername = 'other'; resolve(response({ previewHash: 'stale' })); await pending;
    expect(admin.preview).toBeNull(); expect(admin.busy).toBe('');
  });
  it('never retries a stale commit and explains re-preview', async () => {
    const { state, admin } = harness(); await ready(admin); state.apiFetch.mockImplementationOnce(async () => response({ error: 'stale' }, 409));
    await admin.confirm(); expect(admin.error).toContain('Prévisualisez à nouveau'); await admin.confirm();
    expect(state.apiFetch.mock.calls.filter(([path]) => path.endsWith('/commit'))).toHaveLength(1);
  });
  it('keeps read-only catalog access while denying administration', async () => {
    const { state, admin } = harness(); state.canManageCurrentWorkflows = false;
    await admin.load(); expect(admin.workflows).toHaveLength(1);
    admin.selected = 'review@1'; await admin.generatePreview(); await admin.remove(workflow); await admin.deactivate(['Person']);
    expect(state.apiFetch.mock.calls.every(([, options]) => !options.method)).toBe(true);
  });
  it('does not leak a completed old-session mutation into the new session', async () => {
    const { state, admin } = harness(); await ready(admin);
    let resolve; state.apiFetch.mockImplementationOnce(() => new Promise(done => { resolve = done; }));
    const pending = admin.confirm(); state.authEpoch++; resolve(response({ applied: true })); await pending;
    expect(admin.success).toBe(''); expect(state.refreshData).not.toHaveBeenCalled(); expect(admin.workflows).toEqual([]);
  });

});
