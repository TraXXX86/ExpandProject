import { mount } from '@vue/test-utils';
import { defineComponent, reactive } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import { useScopedApi } from './useScopedApi';

function fixture() {
  const pending = [];
  const state = reactive({ selectedModelKey: 'model-a', isAuthenticated: true, canReadCurrentModelData: true, authMeta: { actorUsername: 'admin', effectiveUsername: 'alice', expiresAt: 123 }, apiFetch: vi.fn((path, options) => new Promise(resolve => pending.push({ path, options, resolve }))), readJson: async response => response.payload });
  const reset = vi.fn(); let api;
  const wrapper = mount(defineComponent({ setup() { api = useScopedApi(state, reset); return () => null; } }));
  return { state, pending, reset, wrapper, api };
}
describe('scoped feature requests', () => {
  it('discards late private responses after impersonation changes even if fetch ignores abort', async () => {
    const { state, pending, reset, wrapper, api } = fixture();
    const result = api.request('views', '/api/views');
    state.authMeta.effectiveUsername = 'bob';
    expect(reset).toHaveBeenCalled();
    expect(pending[0].options.signal.aborted).toBe(true);
    pending[0].resolve({ ok: true, payload: { items: [{ name: 'Private to Alice' }] } });
    expect(await result).toBeNull();
    wrapper.unmount();
  });
  it('cancels superseded requests and prevents requests after permission revocation', async () => {
    const { state, pending, wrapper, api } = fixture();
    const first = api.request('history', '/api/history?offset=0');
    const second = api.request('history', '/api/history?offset=25');
    pending[1].resolve({ ok: true, payload: { items: ['current'] } });
    pending[0].resolve({ ok: true, payload: { items: ['old'] } });
    expect(await second).toEqual({ items: ['current'] });
    expect(await first).toBeNull();
    state.canReadCurrentModelData = false;
    expect(await api.request('history', '/api/history')).toBeNull();
    expect(state.apiFetch).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });
  it('aborts outstanding requests when the component unmounts', async () => {
    const { pending, wrapper, api } = fixture();
    const result = api.request('quality', '/api/quality');
    wrapper.unmount();
    pending[0].resolve({ ok: false, status: 500, payload: { error: 'late error' } });
    expect(await result).toBeNull();
  });
});
