import test from 'node:test';
import assert from 'node:assert/strict';

import { useAdminState } from './admin.js';
import { useAuthState } from './auth.js';
import { createAppStateBase } from './base.js';
import { useDataState } from './data.js';
import { useExplorerState } from './explorer.js';
import { useModelState } from './model.js';

function composeState() {
  const state = createAppStateBase();
  useAuthState(state);
  useModelState(state);
  useDataState(state);
  useExplorerState(state);
  useAdminState(state);
  return state;
}

test('composed state keeps the existing top-level action surface', () => {
  const state = composeState();

  assert.equal(typeof state.login, 'function');
  assert.equal(typeof state.refreshModels, 'function');
  assert.equal(typeof state.createObject, 'function');
  assert.equal(typeof state.getExplorerSubtree, 'function');
  assert.equal(typeof state.copyAdminCommand, 'function');
});

test('auth and portal state still normalize aliases and gate access', () => {
  const state = composeState();

  state.currentPage.value = 'admin';
  state.accessProfile.value = {
    username: 'alice',
    displayName: 'Alice',
    portalUser: true,
    portalModelAdmin: false,
    platformAdmin: false
  };

  state.setPortal('data');
  assert.equal(state.activePortal.value, 'user');
  assert.equal(state.currentPage.value, 'navigate');

  state.status.value = null;
  state.setPortal('model-admin');
  assert.equal(state.activePortal.value, 'user');
  assert.deepEqual(state.status.value, {
    type: 'warning',
    message: "Accès au portail administration du modèle non autorisé pour ce profil."
  });
});

test('model, data and explorer modules still cooperate on derived state', () => {
  const state = composeState();

  state.models.value = [{ key: 'model-a', name: 'Model A', version: '1.0' }];
  state.selectedModelKey.value = 'model-a';
  state.accessPermissions.value = [{
    modelKey: 'model-a',
    visible: true,
    canRead: true,
    canCreate: true,
    canUpdate: true,
    canDelete: true
  }];
  state.modelDetails.value = {
    objectTypes: [
      {
        name: 'Person',
        parent: '',
        icon: '',
        attributes: [{ name: 'name', searchable: true }],
        representativeAttributes: [{ name: 'name', order: 0 }],
        attributeGroups: []
      },
      {
        name: 'Company',
        parent: '',
        icon: '',
        attributes: [{ name: 'title', searchable: true }],
        representativeAttributes: [{ name: 'title', order: 0 }],
        attributeGroups: []
      }
    ],
    linkTypes: [{
      name: 'worksFor',
      directed: true,
      sources: ['Person'],
      targets: ['Company'],
      labels: {},
      sourceLabels: {},
      targetLabels: {}
    }],
    languages: [],
    defaultLanguage: '',
    userPortalLabels: {}
  };
  state.linkTypeInfo.value = { worksFor: true };
  state.hasModel.value = true;
  state.dataObjects.value = [
    {
      id: 1,
      idKey: '1',
      key: '1-0',
      type: 'Person',
      attributes: [{ key: 'name', value: 'Alice' }]
    },
    {
      id: 2,
      idKey: '2',
      key: '2-1',
      type: 'Company',
      attributes: [{ key: 'title', value: 'Acme' }]
    }
  ];
  state.dataLinks.value = [{
    key: 'link-0-1-2',
    type: 'worksFor',
    fromKey: '1',
    toKey: '2'
  }];
  state.createLinkType.value = 'worksFor';

  assert.deepEqual(state.createLinkSourceOptions.value, [
    { title: 'Alice • Person #1', value: 1 }
  ]);
  assert.deepEqual(state.createLinkTargetOptions.value, [
    { title: 'Acme • Company #2', value: 2 }
  ]);

  state.setRootObjectByKey('1');
  assert.equal(state.explorerTree.value?.object.id, 1);
  assert.equal(state.explorerTree.value?.children.length, 1);
  assert.equal(state.relationsByObjectKey.value.get('1')?.[0]?.directionLabel, 'sortant');

  state.fullTextQuery.value = 'ali';
  assert.deepEqual(state.fullTextResults.value.map((result) => result.id), [1]);
});
