import { describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import { createRequestCoordinator } from './http';
import { useDataActions } from './useDataActions';

function context() {
  const ctx = { requests: createRequestCoordinator(), canReadModelData: () => true, readJson: response => response.json() };
  for (const key of ['selectedModelKey','currentPage','dataOffset','dataLimit','dataRequestQuery','dataObjects','pageObjects','dataLinks','neighborStatus','dataHasMore','dataSummary','selectedObject','selectedRootObjectKey','treeLinkSelections','treeExpandedNodes','isLoadingData','status','rootObjectQuery']) ctx[key] = ref(null);
  Object.assign(ctx, { selectedModelKey: ref('model'), currentPage: ref('table'), dataOffset: ref(0), dataLimit: ref(100), dataRequestQuery: ref({ q: 'query', type: 'Person', workflowId: 'approval', workflowStatus: 'draft' }), dataObjects: ref([]), dataLinks: ref([]), neighborStatus: ref({}) });
  return ctx;
}
const response = id => ({ ok: true, json: async () => ({ objects: [{ id, type: 'Person' }], links: [], totalObjects: 250, hasMore: true, offset: 0, objectTypes: ['Person'] }) });
describe('paged data state', () => {
  it('keeps the newest model response when an older transport does not honor abort', async () => {
    const ctx = context();
    const resolvers = [];
    ctx.apiFetch = vi.fn(() => new Promise(resolve => resolvers.push(resolve)));
    const actions = useDataActions(ctx);
    const old = actions.refreshData('model', 0);
    ctx.selectedModelKey.value = 'new-model';
    const current = actions.refreshData('new-model', 0);
    resolvers[1](response(2));
    await current;
    resolvers[0](response(1));
    await old;
    expect(ctx.dataObjects.value.map(object => object.id)).toEqual([2]);
    expect(ctx.dataSummary.value.totalObjects).toBe(250);
    expect(ctx.dataHasMore.value).toBe(true);
    expect(ctx.apiFetch.mock.calls[1][0]).toContain('q=query');
    expect(ctx.apiFetch.mock.calls[1][0]).toContain('workflowStatus=draft');
    expect(ctx.apiFetch.mock.calls[1][0]).toContain('workflowId=approval');
  });
  it('preserves protected workflow metadata and stable identity on normalized objects', () => {
    const actions = useDataActions(context());
    const workflow = { id: 'approval', version: '2', state: 'draft', revision: 3 };
    expect(actions.normalizeObjects([{ id: 0, uuid: 'stable-id', workflow, attributes: [] }])[0]).toMatchObject({ id: 0, uuid: 'stable-id', workflow, attributes: [] });
  });
  it('merges neighbors into the explorer cache without changing the table page', async () => {
    const ctx = context();
    ctx.pageObjects.value = [{ id: 1 }];
    ctx.apiFetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ objects: [{ id: 1 }, { id: 2 }], links: [{ id: 7, fromId: 1, toId: 2 }], hasMore: true }) });
    const actions = useDataActions(ctx);
    await actions.loadNeighbors('1');
    await actions.loadNeighbors('1');
    expect(ctx.apiFetch).toHaveBeenCalledTimes(1);
    expect(ctx.dataObjects.value).toHaveLength(2);
    expect(ctx.pageObjects.value).toEqual([{ id: 1 }]);
    expect(ctx.neighborStatus.value['1'].hasMore).toBe(true);
    expect(ctx.dataLinks.value[0].id).toBe(7);
  });
});
