import { describe, expect, it } from 'vitest';
import { parseRoute } from './useHashNavigation';

describe('shareable routes', () => {
  it('decodes portal, model, object and filters', () => {
    expect(parseRoute('#/user/table?model=A%20B&object=42&q=hello&type=Person%2CCompany&offset=100')).toEqual({ portal: 'user', page: 'table', model: 'A B', object: '42', q: 'hello', type: 'Person,Company', offset: 100, searchMode: 'contains', workflowId: '', workflowStatus: '' });
  });
  it('restores workflow filters and the separate administration page', () => {
    const route = parseRoute('#/user/table?workflow=approval&status=review');
    expect(route.workflowId).toBe('approval'); expect(route.workflowStatus).toBe('review');
    expect(parseRoute('#/model-admin/workflows').page).toBe('workflows');
  });
  it('rejects unknown pages and portals', () => {
    expect(parseRoute('#/user/unknown').page).toBe('navigate');
    expect(parseRoute('#/external/admin').portal).toBe('');
  });
});
