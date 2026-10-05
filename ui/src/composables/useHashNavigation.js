import { nextTick, onBeforeUnmount, watch } from 'vue';

const pages = {
  user: ['navigate', 'table', 'search', 'create', 'import-data', 'paths', 'quality', 'history'],
  'model-admin': ['model', 'import-model', 'admin']
};

export function parseRoute(hash) {
  const [path, query = ''] = hash.replace(/^#/, '').split('?');
  const [portal, page] = path.split('/').filter(Boolean);
  const params = new URLSearchParams(query);
  const offset = Number(params.get('offset') || 0);
  return { portal: pages[portal] ? portal : '', page: pages[portal]?.includes(page) ? page : pages[portal]?.[0] || '', model: params.get('model') || '', object: params.get('object') || '', q: params.get('q') || '', type: params.get('type') || '', offset: Number.isSafeInteger(offset) && offset >= 0 ? offset : 0, searchMode: params.get('mode') === 'fulltext' ? 'fulltext' : 'contains' };
}

/** Hash routes also work behind static hosts without a history fallback. */
export function useHashNavigation(state) {
  const initial = parseRoute(window.location.hash);
  let pending = initial.portal ? initial : null;
  let lastHandledHash = window.location.hash;
  let applying = false;
  let sequence = 0;
  async function restore() {
    if (!pending || !state.isAuthenticated || state.isLoadingModels || !state.hasLoadedModels) return;
    const route = pending;
    pending = null;
    applying = true;
    const epoch = ++sequence;
    try {
      if (route.portal) state.setPortal(route.portal, route.page);
      else state.clearPortal();
      if (route.model && state.models.some(model => model.key === route.model)) state.selectedModelKey = route.model;
      state.dataQuery = route.q;
      state.searchMode = route.searchMode;
      state.dataType = route.type;
      if (route.page === 'table') { state.tableSearch = route.q; state.tableTypeFilter = route.type.split(',').filter(Boolean); }
      if (route.page === 'search') { state.fullTextQuery = route.q; state.fullTextTypeFilter = route.type.split(',').filter(Boolean); }
      await nextTick();
      if (route.portal && state.selectedModelKey) await state.refreshData(state.selectedModelKey, route.offset);
      if (route.object && state.selectedModelKey) {
        await state.loadNeighbors(route.object);
        if (epoch === sequence) state.setRootObjectByKey(route.object);
      }
    } finally { if (epoch === sequence) applying = false; }
  }
  const stopRestore = watch(() => [state.isAuthenticated, state.isLoadingModels, state.hasLoadedModels, state.models], restore, { immediate: true });
  const stopWrite = watch(() => [state.activePortal, state.currentPage, state.selectedModelKey, state.selectedRootObjectKey, state.dataOffset, state.searchMode, state.dataQuery, state.dataType, state.tableSearch, state.tableTypeFilter, state.fullTextQuery, state.fullTextTypeFilter], () => {
    if (applying || pending || !state.isAuthenticated) return;
    const params = new URLSearchParams();
    if (state.selectedModelKey) params.set('model', state.selectedModelKey);
    if (state.dataOffset) params.set('offset', String(state.dataOffset));
    if (state.searchMode === 'fulltext') params.set('mode', 'fulltext');
    if (state.currentPage === 'navigate' && state.selectedRootObjectKey) params.set('object', state.selectedRootObjectKey);
    const q = state.currentPage === 'table' ? state.tableSearch : state.currentPage === 'search' ? state.fullTextQuery : state.dataQuery;
    const type = state.currentPage === 'table' ? state.tableTypeFilter.join(',') : state.currentPage === 'search' ? state.fullTextTypeFilter.join(',') : state.dataType;
    if (q) params.set('q', q);
    if (type) params.set('type', type);
    const next = state.activePortal ? `#/${state.activePortal}/${state.currentPage}${params.size ? `?${params}` : ''}` : '#/';
    if (next !== window.location.hash) { window.history.pushState(null, '', next); lastHandledHash = next; }
  }, { deep: true });
  function change() { if (lastHandledHash === window.location.hash) return; lastHandledHash = window.location.hash; pending = parseRoute(window.location.hash); restore(); }
  window.addEventListener('hashchange', change);
  window.addEventListener('popstate', change);
  onBeforeUnmount(() => { sequence++; stopRestore(); stopWrite(); window.removeEventListener('hashchange', change); window.removeEventListener('popstate', change); });
}
