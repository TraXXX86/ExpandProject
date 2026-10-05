import { mount, flushPromises } from '@vue/test-utils';
import { defineComponent, reactive } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import { useObjectWorkflow } from './useObjectWorkflow';
const payload = (state = 'draft', revision = 1) => ({ objectId: 1, objectUuid: 'uuid-1', workflow: { id: 'approval', version: '1', state, stateLabel: state, revision }, canTransition: true, transitions: state === 'draft' ? [{ id: 'submit', label: 'Soumettre', from: 'draft', to: 'approved' }] : [] });
function fixture() {
  const pending = [], changed = vi.fn();
  const state = reactive({ selectedModelKey: 'a', isAuthenticated: true, canReadCurrentModelData: true, canTransitionCurrentModelData: true, authEpoch: 1, apiFetch: vi.fn((path, options) => new Promise(resolve => pending.push({ path, options, resolve }))), readJson: async response => response.payload });
  const object = reactive({ id: 1, uuid: 'uuid-1' }); let flow;
  const wrapper = mount(defineComponent({ setup() { flow = useObjectWorkflow(state, () => object, changed); return () => null; } }));
  const respond = async (index, data = payload(), status = 200) => { pending[index].resolve({ ok: status < 400, status, payload: data }); await flushPromises(); };
  return { state, object, pending, changed, wrapper, flow, respond };
}
describe('object workflows', () => {
  it('requires confirmation and sends the server revision and stable identity, then reloads terminal status', async () => {
    const f = fixture(); await f.respond(0);
    f.flow.choose(f.flow.transitions.value[0]); expect(f.pending).toHaveLength(1);
    const done = f.flow.confirm(); expect(JSON.parse(f.pending[1].options.body)).toEqual({ modelKey: 'a', transitionId: 'submit', expectedRevision: 1, objectUuid: 'uuid-1' });
    await f.respond(1, {}); await f.respond(2, payload('approved', 2)); await done;
    expect(f.flow.transitions.value).toEqual([]); expect(f.changed).toHaveBeenCalledOnce(); expect(f.flow.selected.value).toBeNull(); f.wrapper.unmount();
  });
  it('reloads stale state on 409 without automatically retrying the transition', async () => {
    const f = fixture(); await f.respond(0); f.flow.choose(f.flow.transitions.value[0]); const done = f.flow.confirm();
    await f.respond(1, { error: 'stale' }, 409); await f.respond(2, payload('approved', 2)); await done;
    expect(f.flow.notice.value).toContain('a changé'); expect(f.flow.details.value.workflow.revision).toBe(2); expect(f.changed).not.toHaveBeenCalled(); expect(f.pending).toHaveLength(3); f.wrapper.unmount();
  });
  it('clears confirmation and drops late mutation results when selection or permission changes', async () => {
    const f = fixture(); await f.respond(0); f.flow.choose(f.flow.transitions.value[0]); const done = f.flow.confirm();
    f.object.id = 2; expect(f.flow.selected.value).toBeNull(); expect(f.flow.details.value).toBeNull();
    await f.respond(1, payload('approved', 2)); await done; expect(f.changed).not.toHaveBeenCalled();
    f.state.canReadCurrentModelData = false; expect(f.flow.transitions.value).toEqual([]); expect(f.pending.at(-1).options.signal.aborted).toBe(true); f.wrapper.unmount();
  });
  it('rejects a recycled numeric object identifier before offering actions', async () => {
    const f = fixture(); await f.respond(0, { ...payload(), objectUuid: 'different-object' });
    expect(f.flow.details.value).toBeNull(); expect(f.flow.transitions.value).toEqual([]); expect(f.flow.error.value).toContain('remplacé'); f.wrapper.unmount();
  });
  it('keeps the status read-only without the dedicated transition permission', async () => {
    const f = fixture(); await f.respond(0); f.state.canTransitionCurrentModelData = false; await f.respond(1);
    expect(f.flow.details.value.workflow.state).toBe('draft'); expect(f.flow.transitions.value).toEqual([]);
    f.flow.choose({ id: 'submit' }); await f.flow.confirm(); expect(f.pending).toHaveLength(2); f.wrapper.unmount();
  });
});
