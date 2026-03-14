import { computed } from 'vue';

import {
  createEmptyAccessProfile,
  createEmptyAuthMeta
} from './base.js';
import { readJson } from './shared.js';

export function useAuthState(state) {
  state.userOptions = computed(() =>
    state.users.value.map((user) => ({
      title: user.displayName ? `${user.displayName} (${user.username})` : user.username,
      value: user.username
    }))
  );

  state.isPlatformAdmin = computed(() => Boolean(state.accessProfile.value.platformAdmin));
  state.canAccessUserPortal = computed(
    () => Boolean(state.accessProfile.value.portalUser || state.isPlatformAdmin.value)
  );
  state.canAccessModelAdminPortal = computed(
    () => Boolean(state.accessProfile.value.portalModelAdmin || state.isPlatformAdmin.value)
  );
  state.selectedModelPermission = computed(() => getModelPermission(state.selectedModelKey.value));
  state.canReadCurrentModelData = computed(() => canReadModelData(state.selectedModelKey.value));
  state.canCreateCurrentModelData = computed(() => canCreateModelData(state.selectedModelKey.value));
  state.canUpdateCurrentModelData = computed(() => canUpdateModelData(state.selectedModelKey.value));
  state.canDeleteCurrentModelData = computed(() => canDeleteModelData(state.selectedModelKey.value));
  state.canManageAccess = computed(() => Boolean(state.authMeta.value.actorPlatformAdmin));
  state.isPortalSelected = computed(() => Boolean(state.activePortal.value));
  state.isUserPortal = computed(() => state.activePortal.value === 'user');
  state.isModelAdminPortal = computed(() => state.activePortal.value === 'model-admin');

  state.portalLabel = computed(() => {
    if (state.isUserPortal.value) {
      return 'Portail métier';
    }
    if (state.isModelAdminPortal.value) {
      return 'Portail administration du modèle';
    }
    return 'Connexion';
  });

  function apiFetch(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (state.authToken.value) {
      headers.set('Authorization', `Bearer ${state.authToken.value}`);
    }
    return fetch(`${state.apiBase}${path}`, {
      ...options,
      headers
    });
  }

  function resetAuthState(message = '') {
    state.authToken.value = '';
    state.isAuthenticated.value = false;
    state.authMeta.value = createEmptyAuthMeta();
    state.accessProfile.value = createEmptyAccessProfile();
    state.accessPermissions.value = [];
    state.users.value = [];
    state.adminAccessUserKey.value = '';
    state.activePortal.value = '';
    state.resetModelState();
    state.resetDataState();
    state.resetCreateState();
    state.resetModelEditorState();
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem('expand.authToken');
    }
    if (message) {
      state.authStatus.value = { type: 'warning', message };
    }
  }

  async function applyAuthPayload(payload) {
    state.authMeta.value = {
      actorUsername: payload?.auth?.actorUsername || '',
      actorDisplayName: payload?.auth?.actorDisplayName || '',
      effectiveUsername: payload?.auth?.effectiveUsername || '',
      effectiveDisplayName: payload?.auth?.effectiveDisplayName || '',
      impersonating: Boolean(payload?.auth?.impersonating),
      actorPlatformAdmin: Boolean(payload?.auth?.actorPlatformAdmin),
      expiresAt: Number(payload?.auth?.expiresAt || 0)
    };
    state.accessProfile.value = {
      username: payload?.user?.username || '',
      displayName: payload?.user?.displayName || '',
      portalUser: Boolean(payload?.user?.portalUser),
      portalModelAdmin: Boolean(payload?.user?.portalModelAdmin),
      platformAdmin: Boolean(payload?.user?.platformAdmin)
    };
    state.accessPermissions.value = Array.isArray(payload?.permissions) ? payload.permissions : [];
  }

  async function login() {
    const username = String(state.loginUsername.value || '').trim();
    const password = String(state.loginPassword.value || '');
    if (!username || !password) {
      state.authStatus.value = { type: 'warning', message: 'Renseignez votre login et mot de passe.' };
      return false;
    }

    state.isAuthenticating.value = true;
    state.authStatus.value = null;
    try {
      const response = await fetch(`${state.apiBase}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Connexion refusée');
      }

      state.authToken.value = payload?.token || '';
      state.isAuthenticated.value = Boolean(state.authToken.value);
      if (typeof window !== 'undefined' && state.authToken.value) {
        window.localStorage.setItem('expand.authToken', state.authToken.value);
      }

      await applyAuthPayload(payload);
      await refreshUsers();
      await state.refreshHealth();
      await state.refreshModels();
      ensurePortalAccess();

      state.authStatus.value = { type: 'success', message: 'Connexion réussie.' };
      return true;
    } catch (error) {
      resetAuthState();
      state.authStatus.value = { type: 'error', message: error.message };
      return false;
    } finally {
      state.isAuthenticating.value = false;
    }
  }

  async function logout() {
    try {
      await apiFetch('/api/auth/logout', { method: 'POST' });
    } catch (error) {
      // ignore
    }
    resetAuthState();
    state.authStatus.value = { type: 'info', message: 'Déconnecté.' };
  }

  async function refreshSession() {
    if (!state.authToken.value) {
      resetAuthState();
      return false;
    }
    try {
      const response = await apiFetch('/api/auth/me');
      const payload = await readJson(response);
      if (!response.ok) {
        resetAuthState();
        return false;
      }
      state.isAuthenticated.value = true;
      await applyAuthPayload(payload);
      return true;
    } catch (error) {
      resetAuthState();
      return false;
    }
  }

  async function refreshUsers() {
    if (!state.isAuthenticated.value) {
      state.users.value = [];
      return;
    }
    state.isLoadingUsers.value = true;
    state.accessStatus.value = null;
    try {
      const response = await apiFetch('/api/access/users');
      const payload = await readJson(response);
      if (!response.ok) {
        if (response.status === 401) {
          resetAuthState('Session expirée, reconnectez-vous.');
          return;
        }
        throw new Error(payload?.error || 'Erreur lors du chargement des utilisateurs');
      }
      state.users.value = Array.isArray(payload) ? payload : [];
      if (!state.users.value.length) {
        state.accessStatus.value = { type: 'warning', message: "Aucun utilisateur n'est configuré." };
      }
    } catch (error) {
      state.users.value = [];
      state.accessStatus.value = { type: 'error', message: error.message };
    } finally {
      state.isLoadingUsers.value = false;
    }
  }

  async function refreshCurrentAccess() {
    if (!state.isAuthenticated.value) {
      state.accessProfile.value = createEmptyAccessProfile();
      state.accessPermissions.value = [];
      return;
    }
    state.accessStatus.value = null;
    try {
      const response = await apiFetch('/api/access/me');
      const payload = await readJson(response);
      if (!response.ok) {
        if (response.status === 401) {
          resetAuthState('Session expirée, reconnectez-vous.');
          return;
        }
        throw new Error(payload?.error || 'Erreur lors du chargement des droits');
      }
      state.authMeta.value = {
        actorUsername: payload?.auth?.actorUsername || state.authMeta.value.actorUsername,
        actorDisplayName: payload?.auth?.actorDisplayName || state.authMeta.value.actorDisplayName,
        effectiveUsername: payload?.auth?.effectiveUsername || payload?.user?.username || '',
        effectiveDisplayName: payload?.auth?.effectiveDisplayName || payload?.user?.displayName || '',
        impersonating: Boolean(payload?.auth?.impersonating),
        actorPlatformAdmin: Boolean(payload?.auth?.actorPlatformAdmin ?? state.authMeta.value.actorPlatformAdmin),
        expiresAt: Number(payload?.auth?.expiresAt || state.authMeta.value.expiresAt || 0)
      };
      state.accessProfile.value = {
        username: payload?.user?.username || '',
        displayName: payload?.user?.displayName || '',
        portalUser: Boolean(payload?.user?.portalUser),
        portalModelAdmin: Boolean(payload?.user?.portalModelAdmin),
        platformAdmin: Boolean(payload?.user?.platformAdmin)
      };
      state.accessPermissions.value = Array.isArray(payload?.permissions) ? payload.permissions : [];
    } catch (error) {
      state.accessProfile.value = createEmptyAccessProfile();
      state.accessPermissions.value = [];
      state.accessStatus.value = { type: 'error', message: error.message };
    }
  }

  async function impersonateUser(username) {
    if (!state.canManageAccess.value) {
      state.adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    if (!username) {
      state.adminAccessStatus.value = { type: 'warning', message: 'Sélectionnez un utilisateur.' };
      return;
    }

    state.adminAccessStatus.value = null;
    try {
      const response = await apiFetch('/api/auth/impersonate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username })
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Impersonation impossible');
      }
      await applyAuthPayload(payload);
      await state.refreshModels();
      ensurePortalAccess();
      state.adminAccessStatus.value = { type: 'success', message: `Impersonation de ${username} activée.` };
    } catch (error) {
      state.adminAccessStatus.value = { type: 'error', message: error.message };
    }
  }

  async function stopImpersonation() {
    if (!state.canManageAccess.value) {
      state.adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    state.adminAccessStatus.value = null;
    try {
      const response = await apiFetch('/api/auth/impersonate/stop', { method: 'POST' });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Impossible d'arrêter l'impersonation");
      }
      await applyAuthPayload(payload);
      await state.refreshModels();
      ensurePortalAccess();
      state.adminAccessStatus.value = { type: 'success', message: 'Impersonation arrêtée.' };
    } catch (error) {
      state.adminAccessStatus.value = { type: 'error', message: error.message };
    }
  }

  function setCurrentPage(page) {
    if (!page) {
      return;
    }
    state.currentPage.value = page;
  }

  function setPortal(portal, preferredPage = '') {
    const normalized = normalizePortal(portal);
    if (!normalized) {
      return;
    }
    if (normalized === 'user' && !state.canAccessUserPortal.value) {
      state.status.value = { type: 'warning', message: 'Accès au portail métier non autorisé pour ce profil.' };
      return;
    }
    if (normalized === 'model-admin' && !state.canAccessModelAdminPortal.value) {
      state.status.value = {
        type: 'warning',
        message: "Accès au portail administration du modèle non autorisé pour ce profil."
      };
      return;
    }
    state.activePortal.value = normalized;
    const pages = getPortalPages(normalized);
    if (preferredPage && pages.includes(preferredPage)) {
      state.currentPage.value = preferredPage;
      return;
    }
    if (!pages.includes(state.currentPage.value)) {
      state.currentPage.value = getPortalDefaultPage(normalized);
    }
  }

  function clearPortal() {
    state.activePortal.value = '';
  }

  function normalizePortal(portal) {
    if (portal === 'user' || portal === 'data') {
      return 'user';
    }
    if (portal === 'model-admin' || portal === 'admin') {
      return 'model-admin';
    }
    return '';
  }

  function getPortalPages(portal) {
    if (portal === 'user') {
      return state.userPortalPages;
    }
    if (portal === 'model-admin') {
      return state.modelAdminPortalPages;
    }
    return [];
  }

  function getPortalDefaultPage(portal) {
    if (portal === 'user') {
      return 'navigate';
    }
    if (portal === 'model-admin') {
      return 'model';
    }
    return 'navigate';
  }

  function getModelPermission(modelKey) {
    if (!modelKey) {
      return null;
    }
    if (state.isPlatformAdmin.value) {
      return {
        modelKey,
        visible: true,
        canRead: true,
        canCreate: true,
        canUpdate: true,
        canDelete: true
      };
    }
    return state.accessPermissions.value.find((permission) => permission.modelKey === modelKey) || null;
  }

  function canViewModel(modelKey) {
    const permission = getModelPermission(modelKey);
    return Boolean(permission?.visible);
  }

  function canReadModelData(modelKey) {
    const permission = getModelPermission(modelKey);
    return Boolean(permission?.visible && permission?.canRead);
  }

  function canCreateModelData(modelKey) {
    const permission = getModelPermission(modelKey);
    return Boolean(permission?.visible && permission?.canCreate);
  }

  function canUpdateModelData(modelKey) {
    const permission = getModelPermission(modelKey);
    return Boolean(permission?.visible && permission?.canUpdate);
  }

  function canDeleteModelData(modelKey) {
    const permission = getModelPermission(modelKey);
    return Boolean(permission?.visible && permission?.canDelete);
  }

  function ensurePortalAccess() {
    if (state.activePortal.value === 'user' && !state.canAccessUserPortal.value) {
      state.activePortal.value = '';
      state.status.value = { type: 'warning', message: 'Votre profil ne permet pas l’accès au portail métier.' };
    }
    if (state.activePortal.value === 'model-admin' && !state.canAccessModelAdminPortal.value) {
      state.activePortal.value = '';
      state.status.value = { type: 'warning', message: "Votre profil ne permet pas l’accès au portail administration." };
    }
  }

  Object.assign(state, {
    apiFetch,
    resetAuthState,
    applyAuthPayload,
    login,
    logout,
    refreshSession,
    refreshUsers,
    refreshCurrentAccess,
    impersonateUser,
    stopImpersonation,
    setCurrentPage,
    setPortal,
    clearPortal,
    normalizePortal,
    getPortalPages,
    getPortalDefaultPage,
    getModelPermission,
    canViewModel,
    canReadModelData,
    canCreateModelData,
    canUpdateModelData,
    canDeleteModelData,
    ensurePortalAccess
  });

  return state;
}
