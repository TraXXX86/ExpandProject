import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises } from '@vue/test-utils';
import { mountUseAppState, jsonResponse } from './testUtils';

describe('useAppState critical data flows', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    window.localStorage.clear();
  });

  it('returns full-text results only for searchable attributes and selected types', async () => {
    vi.stubGlobal('fetch', vi.fn());

    const state = mountUseAppState();
    state.modelDetails.value = {
      objectTypes: [
        {
          name: 'PERSONNE',
          attributes: [
            { name: 'PRENOM', label: 'Prenom', searchable: true },
            { name: 'NOTE', label: 'Note interne', searchable: false }
          ],
          representativeAttributes: [{ name: 'PRENOM', order: 0 }]
        },
        {
          name: 'SOCIETE',
          attributes: [
            { name: 'NOM', label: 'Nom', searchable: true }
          ],
          representativeAttributes: [{ name: 'NOM', order: 0 }]
        }
      ],
      linkTypes: [],
      languages: [],
      defaultLanguage: '',
      userPortalLabels: {}
    };
    state.dataObjects.value = [
      {
        key: 'PERSONNE#1',
        id: 1,
        idKey: '1',
        type: 'PERSONNE',
        attributes: [
          { key: 'PRENOM', value: 'Alice' },
          { key: 'NOTE', value: 'internal-alice' }
        ]
      },
      {
        key: 'SOCIETE#2',
        id: 2,
        idKey: '2',
        type: 'SOCIETE',
        attributes: [
          { key: 'NOM', value: 'Alice Corp' }
        ]
      }
    ];

    state.fullTextQuery.value = 'alice';
    await flushPromises();

    expect(state.fullTextResults.value).toHaveLength(2);
    expect(state.fullTextResults.value[0].matches).toEqual([
      { key: 'PRENOM', label: 'PRENOM', value: 'Alice' }
    ]);

    state.fullTextTypeFilter.value = ['SOCIETE'];
    await flushPromises();

    expect(state.fullTextResults.value).toHaveLength(1);
    expect(state.fullTextResults.value[0].type).toBe('SOCIETE');

    state.fullTextTypeFilter.value = [];
    state.fullTextQuery.value = 'internal';
    await flushPromises();

    expect(state.fullTextResults.value).toEqual([]);
  });

  it('builds inherited create fields, applies defaults, and posts object creation payloads', async () => {
    const fetchMock = vi.fn((url, options) => {
      const value = String(url);

      if (value === 'http://localhost:8080/api/models/social') {
        return Promise.resolve(jsonResponse({
          objectTypes: [
            {
              name: 'PERSONNE',
              attributes: [
                { name: 'LANGUE', label: 'Langue', defaultValue: 'fr' },
                { name: 'PRENOM', label: 'Prenom', required: true }
              ]
            },
            {
              name: 'EMPLOYE',
              parent: 'PERSONNE',
              attributes: [
                { name: 'MATRICULE', label: 'Matricule', required: true }
              ]
            }
          ],
          linkTypes: [],
          languages: [],
          defaultLanguage: '',
          userPortalLabels: {}
        }));
      }
      if (value === 'http://localhost:8080/api/data?modelKey=social') {
        return Promise.resolve(jsonResponse({
          objects: [],
          links: [],
          objectCount: 0,
          linkCount: 0
        }));
      }
      if (value === 'http://localhost:8080/api/objects') {
        return Promise.resolve(jsonResponse({ id: 42 }));
      }

      throw new Error(`Unexpected fetch call: ${value} ${options?.method || 'GET'}`);
    });

    vi.stubGlobal('fetch', fetchMock);

    const state = mountUseAppState();
    state.accessProfile.value = {
      username: 'admin',
      displayName: 'Administrateur',
      portalUser: true,
      portalModelAdmin: true,
      platformAdmin: true
    };
    state.selectedModelKey.value = 'social';

    await flushPromises();
    await flushPromises();

    state.createObjectType.value = 'EMPLOYE';
    await flushPromises();

    expect(state.createObjectAttributeDefs.value.map((attr) => attr.name)).toEqual([
      'LANGUE',
      'MATRICULE',
      'PRENOM'
    ]);
    expect(state.createObjectAttributes.value.LANGUE).toBe('fr');

    state.createObjectAttributes.value.PRENOM = 'Alice';
    state.createObjectAttributes.value.MATRICULE = 'E-001';

    await state.createObject();
    await flushPromises();

    const createCall = fetchMock.mock.calls.find(([url]) =>
      String(url) === 'http://localhost:8080/api/objects'
    );

    expect(createCall).toBeTruthy();
    expect(JSON.parse(createCall[1].body)).toEqual({
      modelKey: 'social',
      type: 'EMPLOYE',
      attributes: [
        { key: 'LANGUE', value: 'fr' },
        { key: 'MATRICULE', value: 'E-001' },
        { key: 'PRENOM', value: 'Alice' }
      ]
    });
    expect(state.createObjectStatus.value).toEqual({
      type: 'success',
      message: 'Objet créé (ID #42).'
    });
  });
});
