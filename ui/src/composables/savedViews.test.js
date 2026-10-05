import { describe, expect, it, vi } from 'vitest';
import { applyView, captureView } from './savedViews';

describe('saved views', () => {
  it('round-trips filters, columns and sort without retaining shared references', () => {
    const state = { currentPage: 'table', tableSearch: 'Alice', tableTypeFilter: ['Person'], tableAttributeKey: 'age', tableAttributeValue: '30', tableAttributeKeyOperator: 'equals', tableAttributeValueOperator: 'contains', fullTextQuery: '', fullTextTypeFilter: [], searchMode: 'contains', tableVisibleColumns: ['id', 'type'], tableSortBy: [{ key: 'type', order: 'desc' }], tableHeaders: [{ key: 'id' }, { key: 'type' }], setCurrentPage: vi.fn() };
    const saved = captureView(state);
    state.tableSearch = 'Bob'; state.tableTypeFilter.push('Other'); state.tableVisibleColumns.push('preview');
    expect(saved.tableTypeFilter).toEqual(['Person']);
    expect(saved.columns).toEqual(['id', 'type']);
    applyView(state, saved);
    expect(state.tableSearch).toBe('Alice');
    expect(state.tableTypeFilter).toEqual(['Person']);
    expect(state.tableSortBy).toEqual([{ key: 'type', order: 'desc' }]);
    expect(state.dataOffset).toBe(0);
    expect(state.setCurrentPage).toHaveBeenCalledWith('table');
  });
  it('does not apply unknown state properties or obsolete columns', () => {
    const state = { tableHeaders: [{ key: 'type' }], setCurrentPage: vi.fn(), canReadCurrentModelData: false };
    applyView(state, { page: 'search', canReadCurrentModelData: true, columns: ['secret'], sortBy: [{ key: 'unknown', order: 'asc' }], fullTextQuery: 'Ada' });
    expect(state.canReadCurrentModelData).toBe(false);
    expect(state.tableVisibleColumns).toEqual([]);
    expect(state.tableSortBy).toEqual([]);
    expect(state.fullTextQuery).toBe('Ada');
    expect(state.searchMode).toBe('contains');
    expect(() => applyView(state, { page: 'admin' })).toThrow('incompatible');
  });
});
