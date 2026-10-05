import { mount, flushPromises } from '@vue/test-utils';
import { h, reactive } from 'vue';
import { afterEach, expect, it, vi } from 'vitest';
import { useAppState } from './useAppState';

afterEach(() => vi.unstubAllGlobals());
it('restores a cookie session and initializes all derived page views after the module split', async () => {
  localStorage.setItem('expand.authToken', 'legacy-secret');
  const payloads = {
    '/api/auth/me': { user: { username: 'tester', platformAdmin: true, portalUser: true, portalModelAdmin: true }, auth: { actorPlatformAdmin: true } },
    '/api/access/me': { user: { username: 'tester', platformAdmin: true, portalUser: true, portalModelAdmin: true }, auth: { actorPlatformAdmin: true } },
    '/api/access/users': [],
    '/api/health': { api: 'ok', neo4j: 'ok' },
    '/api/models': [{ key: 'People:1', name: 'People', version: '1' }],
    '/api/models/People%3A1': { defaultLanguage: 'fr', objectTypes: [{ name: 'Person', attributes: [{ name: 'name', type: 'STRING', searchable: true, representative: true }] }], linkTypes: [{ name: 'Knows', sources: ['Person'], targets: ['Person'] }] },
    '/api/data': { objects: [{ id: 1, type: 'Person', attributes: [{ key: 'name', value: 'Ada' }] }], links: [], offset: 0, totalObjects: 1, objectTypes: ['Person'] }
  };
  vi.stubGlobal('fetch', vi.fn(async path => ({ ok: true, status: 200, json: async () => payloads[path.split('?')[0]] || {} })));
  let state;
  const wrapper = mount({ setup() { state = reactive(useAppState()); return () => h('div'); } });
  await flushPromises();
  expect(localStorage.getItem('expand.authToken')).toBeNull();
  expect(state.isAuthenticated).toBe(true);
  expect(state.loginUsername).toBe('');
  expect(state.loginPassword).toBe('');
  expect(state.selectedModelKey).toBe('People:1');
  expect(state.isLoadingModels).toBe(false);
  expect(state.dataObjects).toHaveLength(1);
  expect(state.defaultLanguage).toBe('fr');
  // Evaluating every exported derived value catches missing cross-module dependencies.
  for (const key of Object.keys(state)) expect(() => state[key]).not.toThrow();
  state.setPortal('user', 'table');
  expect(state.filteredTableRows[0].id).toBe(1);
  await state.refreshData();
  expect(state.isLoadingData).toBe(false);
  wrapper.unmount();
});
