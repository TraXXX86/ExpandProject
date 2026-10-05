/** Platform user and permission management. */
export function useAccessAdminActions(ctx) {
  async function refreshUsers() {
    if (!ctx.isAuthenticated.value) {
      ctx.users.value = [];
      return;
    }
    ctx.isLoadingUsers.value = true;
    ctx.accessStatus.value = null;
    try {
      const response = await ctx.apiFetch('/api/access/users');
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        if (response.status === 401) {
          ctx.resetAuthState('Session expirée, reconnectez-vous.');
          return;
        }
        throw new Error(payload?.error || 'Erreur lors du chargement des utilisateurs');
      }
      ctx.users.value = Array.isArray(payload) ? payload : [];
      if (!ctx.users.value.length) {
        ctx.accessStatus.value = { type: 'warning', message: "Aucun utilisateur n'est configuré." };
      }
    } catch (error) {
      ctx.users.value = [];
      ctx.accessStatus.value = { type: 'error', message: error.message };
    } finally {
      ctx.isLoadingUsers.value = false;
    }
  }

  function syncAdminPermissionRows() {
    const byModelKey = new Map();
    ctx.adminAccessPermissions.value.forEach((permission) => {
      if (permission?.modelKey) {
        byModelKey.set(permission.modelKey, permission);
      }
    });

    ctx.adminAccessPermissions.value = ctx.models.value.map((model) => {
      const existing = byModelKey.get(model.key);
      return {
        modelKey: model.key,
        modelName: model.name,
        modelVersion: model.version || '',
        visible: Boolean(existing?.visible),
        canRead: Boolean(existing?.canRead),
        canCreate: Boolean(existing?.canCreate),
        canUpdate: Boolean(existing?.canUpdate),
        canDelete: Boolean(existing?.canDelete),
        canTransition: Boolean(existing?.canTransition)
      };
    });
  }

  async function loadAdminAccessUser(username) {
    if (!username) {
      return;
    }
    ctx.adminAccessStatus.value = null;
    try {
      const response = await ctx.apiFetch(`/api/access/users/${encodeURIComponent(username)}/access`);
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Impossible de charger les droits de l'utilisateur.");
      }

      ctx.adminAccessForm.value = {
        username: payload?.user?.username || username,
        displayName: payload?.user?.displayName || '',
        password: '',
        portalUser: Boolean(payload?.user?.portalUser),
        portalModelAdmin: Boolean(payload?.user?.portalModelAdmin),
        platformAdmin: Boolean(payload?.user?.platformAdmin)
      };

      const permissionByModel = new Map();
      const permissions = Array.isArray(payload?.permissions) ? payload.permissions : [];
      permissions.forEach((permission) => {
        if (permission?.modelKey) {
          permissionByModel.set(permission.modelKey, permission);
        }
      });

      ctx.adminAccessPermissions.value = ctx.models.value.map((model) => {
        const entry = permissionByModel.get(model.key);
        return {
          modelKey: model.key,
          modelName: model.name,
          modelVersion: model.version || '',
          visible: Boolean(entry?.visible),
          canRead: Boolean(entry?.canRead),
          canCreate: Boolean(entry?.canCreate),
          canUpdate: Boolean(entry?.canUpdate),
          canDelete: Boolean(entry?.canDelete),
          canTransition: Boolean(entry?.canTransition)
        };
      });
    } catch (error) {
      ctx.adminAccessStatus.value = { type: 'error', message: error.message };
    }
  }

  async function createAccessUser() {
    if (!ctx.canManageAccess.value) {
      ctx.adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    const username = String(ctx.newAccessUsername.value || '').trim();
    if (!username) {
      ctx.adminAccessStatus.value = { type: 'warning', message: 'Renseignez un identifiant utilisateur.' };
      return;
    }
    if (!/^[A-Za-z0-9._-]+$/.test(username)) {
      ctx.adminAccessStatus.value = {
        type: 'warning',
        message: "L'identifiant doit contenir uniquement lettres/chiffres et . _ -"
      };
      return;
    }

    if (!ctx.newAccessPassword.value) {
      ctx.adminAccessStatus.value = { type: 'warning', message: 'Choisissez un mot de passe pour ce nouveau compte.' };
      return;
    }
    ctx.isSavingAccessUser.value = true;
    ctx.adminAccessStatus.value = null;
    try {
      const response = await ctx.apiFetch('/api/access/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username,
          displayName: String(ctx.newAccessDisplayName.value || '').trim(),
          password: ctx.newAccessPassword.value,
          portalUser: true,
          portalModelAdmin: false,
          platformAdmin: false
        })
      });
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Erreur lors de la création de l'utilisateur");
      }

      ctx.newAccessUsername.value = '';
      ctx.newAccessPassword.value = '';
      ctx.newAccessDisplayName.value = '';
      await refreshUsers();
      ctx.adminAccessUserKey.value = payload?.user?.username || username;
      ctx.adminAccessStatus.value = { type: 'success', message: 'Utilisateur créé.' };
    } catch (error) {
      ctx.adminAccessStatus.value = { type: 'error', message: error.message };
    } finally {
      ctx.isSavingAccessUser.value = false;
    }
  }

  async function saveAccessUser() {
    if (!ctx.canManageAccess.value) {
      ctx.adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    const username = ctx.adminAccessForm.value.username;
    if (!username) {
      ctx.adminAccessStatus.value = { type: 'warning', message: 'Sélectionnez un utilisateur.' };
      return;
    }
    ctx.isSavingAccessUser.value = true;
    ctx.adminAccessStatus.value = null;
    try {
      const response = await ctx.apiFetch(`/api/access/users/${encodeURIComponent(username)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          displayName: ctx.adminAccessForm.value.displayName,
          portalUser: Boolean(ctx.adminAccessForm.value.portalUser),
          portalModelAdmin: Boolean(ctx.adminAccessForm.value.portalModelAdmin),
          platformAdmin: Boolean(ctx.adminAccessForm.value.platformAdmin),
          password: String(ctx.adminAccessForm.value.password || '').trim() || undefined
        })
      });
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Erreur lors de la mise à jour de l'utilisateur");
      }

      await refreshUsers();
      await ctx.refreshCurrentAccess();
      ctx.ensurePortalAccess();
      ctx.adminAccessForm.value.password = '';
      ctx.adminAccessStatus.value = { type: 'success', message: 'Profil utilisateur mis à jour.' };
    } catch (error) {
      ctx.adminAccessStatus.value = { type: 'error', message: error.message };
    } finally {
      ctx.isSavingAccessUser.value = false;
    }
  }

  async function saveAccessPermissions() {
    if (!ctx.canManageAccess.value) {
      ctx.adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    if (!ctx.adminAccessForm.value.username) {
      ctx.adminAccessStatus.value = { type: 'warning', message: 'Sélectionnez un utilisateur.' };
      return;
    }
    ctx.isSavingAccessPermissions.value = true;
    ctx.adminAccessStatus.value = null;
    try {
      const response = await ctx.apiFetch(
        `/api/access/users/${encodeURIComponent(ctx.adminAccessForm.value.username)}/permissions`,
        {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            permissions: ctx.adminAccessPermissions.value.map((permission) => ({
              modelKey: permission.modelKey,
              visible: Boolean(permission.visible),
              canRead: Boolean(permission.canRead),
              canCreate: Boolean(permission.canCreate),
              canUpdate: Boolean(permission.canUpdate),
              canDelete: Boolean(permission.canDelete),
              canTransition: Boolean(permission.canTransition)
            }))
          })
        }
      );
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors de la sauvegarde des permissions');
      }
      await refreshUsers();
      await ctx.refreshCurrentAccess();
      await ctx.refreshModels();
      ctx.ensurePortalAccess();
      ctx.adminAccessStatus.value = { type: 'success', message: 'Permissions enregistrées.' };
    } catch (error) {
      ctx.adminAccessStatus.value = { type: 'error', message: error.message };
    } finally {
      ctx.isSavingAccessPermissions.value = false;
    }
  }

  async function deleteAccessUser() {
    if (!ctx.canManageAccess.value) {
      ctx.adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    const username = ctx.adminAccessForm.value.username;
    if (!username) {
      ctx.adminAccessStatus.value = { type: 'warning', message: 'Sélectionnez un utilisateur.' };
      return;
    }
    if (username === 'admin') {
      ctx.adminAccessStatus.value = { type: 'warning', message: "Le compte 'admin' ne peut pas être supprimé." };
      return;
    }

    if (!window.confirm(`Supprimer l’utilisateur ${username} ?`)) return;
    ctx.isDeletingAccessUser.value = true;
    ctx.adminAccessStatus.value = null;
    try {
      const response = await ctx.apiFetch(`/api/access/users/${encodeURIComponent(username)}`, {
        method: 'DELETE'
      });
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Erreur lors de la suppression de l'utilisateur");
      }
      await refreshUsers();
      await ctx.refreshCurrentAccess();
      await ctx.refreshModels();
      ctx.ensurePortalAccess();
      ctx.adminAccessStatus.value = { type: 'success', message: 'Utilisateur supprimé.' };
    } catch (error) {
      ctx.adminAccessStatus.value = { type: 'error', message: error.message };
    } finally {
      ctx.isDeletingAccessUser.value = false;
    }
  }

  async function copyAdminCommand() {
    if (!ctx.adminModel.value) {
      return;
    }
    try {
      await navigator.clipboard.writeText(ctx.adminCommand.value);
      ctx.adminStatus.value = { type: 'success', message: 'Commande copiée dans le presse-papiers.' };
    } catch (error) {
      ctx.adminStatus.value = { type: 'error', message: 'Impossible de copier la commande.' };
    }
  }

  return { refreshUsers, syncAdminPermissionRows, loadAdminAccessUser, createAccessUser, saveAccessUser, saveAccessPermissions, deleteAccessUser, copyAdminCommand };
}
