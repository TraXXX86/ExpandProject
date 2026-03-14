import { describe, expect, it } from 'vitest';
import {
  buildRouteLocation,
  getPortalDefaultPage,
  normalizePageForPortal,
  normalizePortal,
  parseRouteState
} from './appRouteState';

describe('appRouteState', () => {
  it('normalizes portals and default pages', () => {
    expect(normalizePortal('data')).toBe('user');
    expect(normalizePortal('admin')).toBe('model-admin');
    expect(normalizePageForPortal('user', 'unknown')).toBe(getPortalDefaultPage('user'));
    expect(normalizePageForPortal('model-admin', 'admin')).toBe('admin');
  });

  it('parses route params and query context', () => {
    expect(parseRouteState({
      name: 'portal-page',
      params: {
        portal: 'user',
        page: 'table'
      },
      query: {
        model: 'crm',
        lang: 'fr',
        tableSearch: 'client',
        tableTypes: 'Account,Contact',
        tableAttributeKey: 'status',
        tableAttributeKeyOperator: 'equals',
        tableAttributeValue: 'active',
        tableAttributeValueOperator: 'contains'
      }
    })).toEqual({
      screen: 'portal-page',
      portal: 'user',
      page: 'table',
      modelKey: 'crm',
      language: 'fr',
      rootObjectKey: '',
      tableSearch: 'client',
      tableTypes: ['Account', 'Contact'],
      tableAttributeKey: 'status',
      tableAttributeKeyOperator: 'equals',
      tableAttributeValue: 'active',
      tableAttributeValueOperator: 'contains',
      fullTextQuery: '',
      fullTextTypes: []
    });
  });

  it('builds compact locations from current app state', () => {
    expect(buildRouteLocation({
      isAuthenticated: true,
      activePortal: 'user',
      currentPage: 'search',
      selectedModelKey: 'crm',
      displayLanguage: 'fr',
      selectedRootObjectKey: 'ignored',
      tableSearch: '',
      tableTypeFilter: [],
      tableAttributeKey: '',
      tableAttributeKeyOperator: 'contains',
      tableAttributeValue: '',
      tableAttributeValueOperator: 'contains',
      fullTextQuery: 'alice',
      fullTextTypeFilter: ['Customer', 'Lead']
    })).toEqual({
      name: 'portal-page',
      params: {
        portal: 'user',
        page: 'search'
      },
      query: {
        model: 'crm',
        lang: 'fr',
        fullTextQuery: 'alice',
        fullTextTypes: 'Customer,Lead'
      }
    });
  });

  it('routes anonymous users to login', () => {
    expect(buildRouteLocation({
      isAuthenticated: false,
      activePortal: 'user',
      currentPage: 'navigate'
    })).toEqual({ name: 'login' });
  });
});
