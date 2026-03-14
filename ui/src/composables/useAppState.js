import { onMounted, watch } from 'vue';

import { useAdminState } from './app-state/admin.js';
import { useAuthState } from './app-state/auth.js';
import { createAppStateBase } from './app-state/base.js';
import { useDataState } from './app-state/data.js';
import { useExplorerState } from './app-state/explorer.js';
import { useModelState } from './app-state/model.js';

export function useAppState() {
  const state = createAppStateBase();

  useAuthState(state);
  useModelState(state);
  useDataState(state);
  useExplorerState(state);
  useAdminState(state);

  onMounted(async () => {
    const authenticated = await state.refreshSession();
    if (!authenticated) {
      return;
    }
    await state.refreshUsers();
    await state.refreshCurrentAccess();
    await state.refreshHealth();
    await state.refreshModels();
    state.ensurePortalAccess();
  });

  watch(
    () => state.activePortal.value,
    (portal) => {
      if (!portal) {
        return;
      }
      const pages = state.getPortalPages(portal);
      if (!pages.includes(state.currentPage.value)) {
        state.currentPage.value = state.getPortalDefaultPage(portal);
      }
    }
  );

  watch(
    () => state.selectedModelKey.value,
    async (key, previousKey) => {
      if (!key) {
        state.resetModelState();
        state.resetDataState();
        state.resetCreateState();
        state.resetModelEditorState();
        return;
      }
      await state.refreshModelDetails(key);
      await state.refreshData(key);
      if (key !== previousKey) {
        state.resetCreateState();
        state.resetModelEditorState();
      }
    }
  );

  watch(
    () => state.models.value,
    (list) => {
      if (!list.length) {
        state.adminModelKey.value = '';
        state.adminAccessPermissions.value = [];
        return;
      }
      const exists = list.some((model) => model.key === state.adminModelKey.value);
      if (!exists) {
        state.adminModelKey.value = list[0].key;
      }
      state.syncAdminPermissionRows();
    }
  );

  watch(
    () => state.users.value,
    (list) => {
      if (!list.length) {
        state.adminAccessUserKey.value = '';
        return;
      }
      if (!list.find((user) => user.username === state.adminAccessUserKey.value)) {
        state.adminAccessUserKey.value = list[0].username;
      }
    },
    { immediate: true }
  );

  watch(
    () => state.adminAccessUserKey.value,
    async (username) => {
      if (!username) {
        state.resetAdminAccessEditor();
        return;
      }
      await state.loadAdminAccessUser(username);
    }
  );

  watch(
    () => state.selectedObject.value,
    (object) => {
      if (!object) {
        return;
      }
      const type = object.type || 'Objet';
      if (!state.openGroups.value.includes(type)) {
        state.openGroups.value = [...state.openGroups.value, type];
      }
    }
  );

  watch(
    () => state.linkedGroups.value,
    (groups) => {
      state.openLinkGroups.value = groups.map((group) => group.key);
    },
    { immediate: true }
  );

  watch(
    () => state.attributeTabs.value,
    (tabs) => {
      state.selectedAttributeTab.value = tabs[0]?.key || null;
    },
    { immediate: true }
  );

  watch(
    () => state.linkedRelationTabs.value,
    (tabs) => {
      state.selectedLinkedRelationTab.value = tabs[0]?.key || null;
    },
    { immediate: true }
  );

  watch(
    () => state.modelGroupTabs.value,
    (tabs) => {
      state.selectedModelGroupTab.value = tabs[0]?.key || null;
    },
    { immediate: true }
  );

  watch(
    () => state.tableAttributeTabs.value,
    (tabs) => {
      state.tableSelectedAttributeTab.value = tabs[0]?.key || null;
    },
    { immediate: true }
  );

  watch(
    () => state.createObjectAttributeDefs.value,
    (defs) => {
      const next = {};
      defs.forEach((def) => {
        if (!def || !def.name) {
          return;
        }
        if (Object.prototype.hasOwnProperty.call(state.createObjectAttributes.value, def.name)) {
          next[def.name] = state.createObjectAttributes.value[def.name];
        } else if (def.defaultValue) {
          next[def.name] = def.defaultValue;
        } else {
          next[def.name] = '';
        }
      });
      state.createObjectAttributes.value = next;
    },
    { immediate: true }
  );

  watch(
    () => state.createObjectType.value,
    () => {
      state.createObjectStatus.value = null;
    }
  );

  watch(
    () => state.createLinkType.value,
    () => {
      state.createLinkSourceId.value = null;
      state.createLinkTargetId.value = null;
      state.createLinkStatus.value = null;
    }
  );

  return state;
}
