/** Cookie session lifecycle and model permissions. */
export function useAuthActions(ctx) {
  function resetAuthState(message = '') {
    ctx.apiFetch.invalidate?.();
    ctx.requests.cancelAll();
    ctx.isAuthenticating.value = false;
    ctx.isLoadingModels.value = false;
    ctx.hasLoadedModels.value = false;
    ctx.isLoadingModel.value = false;
    ctx.authToken.value = '';
    ctx.isAuthenticated.value = false;
    ctx.authMeta.value = {
      actorUsername: '',
      actorDisplayName: '',
      effectiveUsername: '',
      effectiveDisplayName: '',
      impersonating: false,
      actorPlatformAdmin: false,
      expiresAt: 0
    };
    ctx.accessProfile.value = {
      username: '',
      displayName: '',
      portalUser: false,
      portalModelAdmin: false,
      platformAdmin: false
    };
    ctx.accessPermissions.value = [];
    ctx.users.value = [];
    ctx.adminAccessUserKey.value = '';
    ctx.activePortal.value = '';
    ctx.resetModelState();
    ctx.resetDataState();
    ctx.resetCreateState();
    ctx.resetModelEditorState();
    if (typeof window !== 'undefined') {
      try { window.localStorage.removeItem('expand.authToken'); } catch { /* storage disabled */ }
    }
    if (message) {
      ctx.authStatus.value = { type: 'warning', message };
    }
  }

  async function login() {
    const username = String(ctx.loginUsername.value || '').trim();
    const password = String(ctx.loginPassword.value || '');
    if (!username || !password) {
      ctx.authStatus.value = { type: 'warning', message: 'Renseignez votre login et mot de passe.' };
      return false;
    }

    const request = ctx.requests.start('auth');
    ctx.apiFetch.invalidate?.();
    ctx.isAuthenticating.value = true;
    ctx.authStatus.value = null;
    try {
      const response = await ctx.apiFetch('/api/auth/login', {
        method: 'POST',
        signal: request.signal,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      const payload = await ctx.readJson(response);
      if (!request.isCurrent()) return false;
      if (!response.ok) {
        throw new Error(payload?.error || 'Connexion refusée');
      }

      ctx.isAuthenticated.value = true;
      ctx.loginPassword.value = '';

      await applyAuthPayload(payload);
      await ctx.refreshUsers();
      await ctx.refreshHealth();
      await ctx.refreshModels();
      ensurePortalAccess();

      ctx.authStatus.value = { type: 'success', message: 'Connexion réussie.' };
      return true;
    } catch (error) {
      if (!request.isCurrent() || error.name === 'AbortError') return false;
      resetAuthState();
      ctx.authStatus.value = { type: 'error', message: error.message };
      return false;
    } finally {
      if (request.isCurrent()) ctx.isAuthenticating.value = false;
    }
  }

  async function logout() {
    ctx.requests.cancelAll();
    try {
      await ctx.apiFetch('/api/auth/logout', { method: 'POST' });
    } catch (error) {
      // ignore
    }
    resetAuthState();
    ctx.authStatus.value = { type: 'info', message: 'Déconnecté.' };
  }

  async function refreshSession() {
    const request = ctx.requests.start('auth');
    try {
      const response = await ctx.apiFetch('/api/auth/me', { signal: request.signal });
      const payload = await ctx.readJson(response);
      if (!request.isCurrent()) return false;
      if (!response.ok) {
        resetAuthState();
        return false;
      }
      ctx.isAuthenticated.value = true;
      await applyAuthPayload(payload);
      return true;
    } catch (error) {
      if (!request.isCurrent() || error.name === 'AbortError') return false;
      resetAuthState();
      return false;
    }
  }

  async function applyAuthPayload(payload) {
    ctx.authMeta.value = {
      actorUsername: payload?.auth?.actorUsername || '',
      actorDisplayName: payload?.auth?.actorDisplayName || '',
      effectiveUsername: payload?.auth?.effectiveUsername || '',
      effectiveDisplayName: payload?.auth?.effectiveDisplayName || '',
      impersonating: Boolean(payload?.auth?.impersonating),
      actorPlatformAdmin: Boolean(payload?.auth?.actorPlatformAdmin),
      expiresAt: Number(payload?.auth?.expiresAt || 0)
    };
    ctx.accessProfile.value = {
      username: payload?.user?.username || '',
      displayName: payload?.user?.displayName || '',
      portalUser: Boolean(payload?.user?.portalUser),
      portalModelAdmin: Boolean(payload?.user?.portalModelAdmin),
      platformAdmin: Boolean(payload?.user?.platformAdmin)
    };
    ctx.accessPermissions.value = Array.isArray(payload?.permissions) ? payload.permissions : [];
  }

  async function refreshCurrentAccess() {
    if (!ctx.isAuthenticated.value) {
      ctx.accessProfile.value = {
        username: '',
        displayName: '',
        portalUser: false,
        portalModelAdmin: false,
        platformAdmin: false
      };
      ctx.accessPermissions.value = [];
      return;
    }
    ctx.accessStatus.value = null;
    try {
      const response = await ctx.apiFetch('/api/access/me');
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        if (response.status === 401) {
          resetAuthState('Session expirée, reconnectez-vous.');
          return;
        }
        throw new Error(payload?.error || 'Erreur lors du chargement des droits');
      }
      ctx.authMeta.value = {
        actorUsername: payload?.auth?.actorUsername || ctx.authMeta.value.actorUsername,
        actorDisplayName: payload?.auth?.actorDisplayName || ctx.authMeta.value.actorDisplayName,
        effectiveUsername: payload?.auth?.effectiveUsername || payload?.user?.username || '',
        effectiveDisplayName: payload?.auth?.effectiveDisplayName || payload?.user?.displayName || '',
        impersonating: Boolean(payload?.auth?.impersonating),
        actorPlatformAdmin: Boolean(payload?.auth?.actorPlatformAdmin ?? ctx.authMeta.value.actorPlatformAdmin),
        expiresAt: Number(payload?.auth?.expiresAt || ctx.authMeta.value.expiresAt || 0)
      };
      ctx.accessProfile.value = {
        username: payload?.user?.username || '',
        displayName: payload?.user?.displayName || '',
        portalUser: Boolean(payload?.user?.portalUser),
        portalModelAdmin: Boolean(payload?.user?.portalModelAdmin),
        platformAdmin: Boolean(payload?.user?.platformAdmin)
      };
      ctx.accessPermissions.value = Array.isArray(payload?.permissions) ? payload.permissions : [];
    } catch (error) {
      ctx.accessProfile.value = {
        username: '',
        displayName: '',
        portalUser: false,
        portalModelAdmin: false,
        platformAdmin: false
      };
      ctx.accessPermissions.value = [];
      ctx.accessStatus.value = { type: 'error', message: error.message };
    }
  }

  async function impersonateUser(username) {
    if (!ctx.canManageAccess.value) {
      ctx.adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    if (!username) {
      ctx.adminAccessStatus.value = { type: 'warning', message: 'Sélectionnez un utilisateur.' };
      return;
    }

    ctx.adminAccessStatus.value = null;
    try {
      const response = await ctx.apiFetch('/api/auth/impersonate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username })
      });
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Impersonation impossible');
      }
      await applyAuthPayload(payload);
      await ctx.refreshModels();
      ensurePortalAccess();
      ctx.adminAccessStatus.value = { type: 'success', message: `Impersonation de ${username} activée.` };
    } catch (error) {
      ctx.adminAccessStatus.value = { type: 'error', message: error.message };
    }
  }

  async function stopImpersonation() {
    if (!ctx.canManageAccess.value) {
      ctx.adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    ctx.adminAccessStatus.value = null;
    try {
      const response = await ctx.apiFetch('/api/auth/impersonate/stop', { method: 'POST' });
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Impossible d'arrêter l'impersonation");
      }
      await applyAuthPayload(payload);
      await ctx.refreshModels();
      ensurePortalAccess();
      ctx.adminAccessStatus.value = { type: 'success', message: 'Impersonation arrêtée.' };
    } catch (error) {
      ctx.adminAccessStatus.value = { type: 'error', message: error.message };
    }
  }

  function getModelPermission(modelKey) {
    if (!modelKey) {
      return null;
    }
    if (ctx.isPlatformAdmin.value) {
      return {
        modelKey,
        visible: true,
        canRead: true,
        canCreate: true,
        canUpdate: true,
        canDelete: true
      };
    }
    return ctx.accessPermissions.value.find((permission) => permission.modelKey === modelKey) || null;
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
    if (ctx.activePortal.value === 'user' && !ctx.canAccessUserPortal.value) {
      ctx.activePortal.value = '';
      ctx.status.value = { type: 'warning', message: 'Votre profil ne permet pas l’accès au portail métier.' };
    }
    if (ctx.activePortal.value === 'model-admin' && !ctx.canAccessModelAdminPortal.value) {
      ctx.activePortal.value = '';
      ctx.status.value = { type: 'warning', message: "Votre profil ne permet pas l’accès au portail administration." };
    }
  }

  return { resetAuthState, login, logout, refreshSession, applyAuthPayload, refreshCurrentAccess, impersonateUser, stopImpersonation, getModelPermission, canViewModel, canReadModelData, canCreateModelData, canUpdateModelData, canDeleteModelData, ensurePortalAccess };
}
