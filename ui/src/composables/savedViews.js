const stringFields = ['tableSearch', 'tableAttributeKey', 'tableAttributeValue', 'tableAttributeKeyOperator', 'tableAttributeValueOperator', 'fullTextQuery', 'searchMode', 'workflowStatus', 'workflowId'];
const arrayFields = ['tableTypeFilter', 'fullTextTypeFilter'];
export function captureView(state) {
  const filters = {};
  stringFields.forEach(key => { filters[key] = typeof state[key] === 'string' ? state[key] : ''; });
  arrayFields.forEach(key => { filters[key] = [...(state[key] || [])]; });
  return {
    page: state.currentPage === 'search' ? 'search' : 'table', ...filters,
    columns: [...(state.tableVisibleColumns || [])],
    sortBy: (state.tableSortBy || []).map(({ key, order }) => ({ key, order }))
  };
}
export function applyView(state, saved) {
  if (!saved || !['table', 'search'].includes(saved.page)) throw new Error('Cette vue est incompatible.');
  const filters = saved;
  stringFields.forEach(key => { state[key] = typeof filters[key] === 'string' ? filters[key] : (key.endsWith('Operator') || key === 'searchMode' ? 'contains' : ''); });
  arrayFields.forEach(key => { state[key] = Array.isArray(filters[key]) ? filters[key].filter(value => typeof value === 'string') : []; });
  const allowed = new Set((state.tableHeaders || []).map(header => header.key));
  state.tableVisibleColumns = (saved.columns || []).filter(key => allowed.has(key));
  state.tableSortBy = (saved.sortBy || []).filter(item => allowed.has(item.key) && ['asc', 'desc'].includes(item.order)).map(({ key, order }) => ({ key, order }));
  state.dataOffset = 0;
  state.setCurrentPage(saved.page);
}
