import { computed } from 'vue';

import { createEmptyAdminAccessForm } from './base.js';
import { readJson } from './shared.js';

export function useAdminState(state) {
  state.adminModel = computed(
    () => state.models.value.find((model) => model.key === state.adminModelKey.value) || null
  );

  state.adminCommand = computed(() => {
    if (!state.adminModel.value) {
      return 'java -jar importpackage.jar --delete-model <modelName> [modelVersion]';
    }
    const name = state.adminModel.value.name;
    const version = state.adminModel.value.version || '';
    if (version) {
      return `java -jar importpackage.jar --delete-model "${name}" "${version}"`;
    }
    return `java -jar importpackage.jar --delete-model "${name}"`;
  });

  function syncAdminPermissionRows() {
    const byModelKey = new Map();
    state.adminAccessPermissions.value.forEach((permission) => {
      if (permission?.modelKey) {
        byModelKey.set(permission.modelKey, permission);
      }
    });

    state.adminAccessPermissions.value = state.models.value.map((model) => {
      const existing = byModelKey.get(model.key);
      return {
        modelKey: model.key,
        modelName: model.name,
        modelVersion: model.version || '',
        visible: Boolean(existing?.visible),
        canRead: Boolean(existing?.canRead),
        canCreate: Boolean(existing?.canCreate),
        canUpdate: Boolean(existing?.canUpdate),
        canDelete: Boolean(existing?.canDelete)
      };
    });
  }

  function resetAdminAccessEditor() {
    state.adminAccessForm.value = createEmptyAdminAccessForm();
    state.adminAccessPermissions.value = [];
  }

  async function loadAdminAccessUser(username) {
    if (!username) {
      return;
    }
    state.adminAccessStatus.value = null;
    try {
      const response = await state.apiFetch(`/api/access/users/${encodeURIComponent(username)}/access`);
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Impossible de charger les droits de l'utilisateur.");
      }

      state.adminAccessForm.value = {
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

      state.adminAccessPermissions.value = state.models.value.map((model) => {
        const entry = permissionByModel.get(model.key);
        return {
          modelKey: model.key,
          modelName: model.name,
          modelVersion: model.version || '',
          visible: Boolean(entry?.visible),
          canRead: Boolean(entry?.canRead),
          canCreate: Boolean(entry?.canCreate),
          canUpdate: Boolean(entry?.canUpdate),
          canDelete: Boolean(entry?.canDelete)
        };
      });
    } catch (error) {
      state.adminAccessStatus.value = { type: 'error', message: error.message };
    }
  }

  async function createAccessUser() {
    if (!state.canManageAccess.value) {
      state.adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    const username = String(state.newAccessUsername.value || '').trim();
    if (!username) {
      state.adminAccessStatus.value = { type: 'warning', message: 'Renseignez un identifiant utilisateur.' };
      return;
    }
    if (!/^[A-Za-z0-9._-]+$/.test(username)) {
      state.adminAccessStatus.value = {
        type: 'warning',
        message: "L'identifiant doit contenir uniquement lettres/chiffres et . _ -"
      };
      return;
    }

    state.isSavingAccessUser.value = true;
    state.adminAccessStatus.value = null;
    try {
      const response = await state.apiFetch('/api/access/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username,
          displayName: String(state.newAccessDisplayName.value || '').trim(),
          password: username,
          portalUser: true,
          portalModelAdmin: false,
          platformAdmin: false
        })
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Erreur lors de la création de l'utilisateur");
      }

      state.newAccessUsername.value = '';
      state.newAccessDisplayName.value = '';
      await state.refreshUsers();
      state.adminAccessUserKey.value = payload?.user?.username || username;
      state.adminAccessStatus.value = { type: 'success', message: 'Utilisateur créé.' };
    } catch (error) {
      state.adminAccessStatus.value = { type: 'error', message: error.message };
    } finally {
      state.isSavingAccessUser.value = false;
    }
  }

  async function saveAccessUser() {
    if (!state.canManageAccess.value) {
      state.adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    const username = state.adminAccessForm.value.username;
    if (!username) {
      state.adminAccessStatus.value = { type: 'warning', message: 'Sélectionnez un utilisateur.' };
      return;
    }
    state.isSavingAccessUser.value = true;
    state.adminAccessStatus.value = null;
    try {
      const response = await state.apiFetch(`/api/access/users/${encodeURIComponent(username)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          displayName: state.adminAccessForm.value.displayName,
          portalUser: Boolean(state.adminAccessForm.value.portalUser),
          portalModelAdmin: Boolean(state.adminAccessForm.value.portalModelAdmin),
          platformAdmin: Boolean(state.adminAccessForm.value.platformAdmin),
          password: String(state.adminAccessForm.value.password || '').trim() || undefined
        })
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Erreur lors de la mise à jour de l'utilisateur");
      }

      await state.refreshUsers();
      await state.refreshCurrentAccess();
      state.ensurePortalAccess();
      state.adminAccessForm.value.password = '';
      state.adminAccessStatus.value = { type: 'success', message: 'Profil utilisateur mis à jour.' };
    } catch (error) {
      state.adminAccessStatus.value = { type: 'error', message: error.message };
    } finally {
      state.isSavingAccessUser.value = false;
    }
  }

  async function saveAccessPermissions() {
    if (!state.canManageAccess.value) {
      state.adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    if (!state.adminAccessForm.value.username) {
      state.adminAccessStatus.value = { type: 'warning', message: 'Sélectionnez un utilisateur.' };
      return;
    }
    state.isSavingAccessPermissions.value = true;
    state.adminAccessStatus.value = null;
    try {
      const response = await state.apiFetch(
        `/api/access/users/${encodeURIComponent(state.adminAccessForm.value.username)}/permissions`,
        {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            permissions: state.adminAccessPermissions.value.map((permission) => ({
              modelKey: permission.modelKey,
              visible: Boolean(permission.visible),
              canRead: Boolean(permission.canRead),
              canCreate: Boolean(permission.canCreate),
              canUpdate: Boolean(permission.canUpdate),
              canDelete: Boolean(permission.canDelete)
            }))
          })
        }
      );
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors de la sauvegarde des permissions');
      }
      await state.refreshUsers();
      await state.refreshCurrentAccess();
      await state.refreshModels();
      state.ensurePortalAccess();
      state.adminAccessStatus.value = { type: 'success', message: 'Permissions enregistrées.' };
    } catch (error) {
      state.adminAccessStatus.value = { type: 'error', message: error.message };
    } finally {
      state.isSavingAccessPermissions.value = false;
    }
  }

  async function deleteAccessUser() {
    if (!state.canManageAccess.value) {
      state.adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    const username = state.adminAccessForm.value.username;
    if (!username) {
      state.adminAccessStatus.value = { type: 'warning', message: 'Sélectionnez un utilisateur.' };
      return;
    }
    if (username === 'admin') {
      state.adminAccessStatus.value = { type: 'warning', message: "Le compte 'admin' ne peut pas être supprimé." };
      return;
    }

    state.isDeletingAccessUser.value = true;
    state.adminAccessStatus.value = null;
    try {
      const response = await state.apiFetch(`/api/access/users/${encodeURIComponent(username)}`, {
        method: 'DELETE'
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Erreur lors de la suppression de l'utilisateur");
      }
      await state.refreshUsers();
      await state.refreshCurrentAccess();
      await state.refreshModels();
      state.ensurePortalAccess();
      state.adminAccessStatus.value = { type: 'success', message: 'Utilisateur supprimé.' };
    } catch (error) {
      state.adminAccessStatus.value = { type: 'error', message: error.message };
    } finally {
      state.isDeletingAccessUser.value = false;
    }
  }

  async function loadModelXml() {
    if (!state.canAccessModelAdminPortal.value) {
      state.modelXmlStatus.value = { type: 'warning', message: 'Accès au portail administration requis.' };
      return;
    }
    if (!state.selectedModelKey.value) {
      state.modelXmlStatus.value = { type: 'warning', message: 'Sélectionnez un modèle.' };
      return;
    }
    state.isLoadingModelXml.value = true;
    state.modelXmlStatus.value = null;
    try {
      const response = await state.apiFetch(`/api/models/${encodeURIComponent(state.selectedModelKey.value)}/xml`);
      const text = await response.text();
      if (!response.ok) {
        let message = text;
        try {
          const parsed = JSON.parse(text);
          message = parsed?.error || message;
        } catch (err) {
          // ignore JSON parse failure
        }
        throw new Error(message || 'Erreur lors du chargement du XML.');
      }
      state.modelXml.value = text;
      state.modelXmlStatus.value = { type: 'success', message: 'XML chargé.' };
    } catch (error) {
      state.modelXmlStatus.value = { type: 'error', message: error.message };
    } finally {
      state.isLoadingModelXml.value = false;
    }
  }

  async function saveModelXml() {
    if (!state.canAccessModelAdminPortal.value) {
      state.modelXmlStatus.value = { type: 'warning', message: 'Accès au portail administration requis.' };
      return;
    }
    if (!state.selectedModelKey.value) {
      state.modelXmlStatus.value = { type: 'warning', message: 'Sélectionnez un modèle.' };
      return;
    }
    if (!state.modelXml.value.trim()) {
      state.modelXmlStatus.value = { type: 'warning', message: 'XML vide.' };
      return;
    }
    state.isSavingModelXml.value = true;
    state.modelXmlStatus.value = null;
    try {
      const response = await state.apiFetch(`/api/models/${encodeURIComponent(state.selectedModelKey.value)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/xml' },
        body: state.modelXml.value
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors de la sauvegarde du modèle.');
      }
      const renamed = Boolean(payload?.renamed);
      state.modelXmlStatus.value = {
        type: 'success',
        message: renamed
          ? `Modèle mis à jour (nouvelle clé ${payload?.key}).`
          : 'Modèle mis à jour.'
      };
      await state.refreshModels(payload?.key || state.selectedModelKey.value);
    } catch (error) {
      state.modelXmlStatus.value = { type: 'error', message: error.message };
    } finally {
      state.isSavingModelXml.value = false;
    }
  }

  async function deleteModel() {
    if (!state.canAccessModelAdminPortal.value) {
      state.adminStatus.value = { type: 'warning', message: 'Accès au portail administration requis.' };
      return;
    }
    if (!state.adminModelKey.value) {
      state.adminStatus.value = { type: 'warning', message: 'Sélectionnez un modèle.' };
      return;
    }
    state.adminStatus.value = null;
    try {
      const response = await state.apiFetch(`/api/models/${encodeURIComponent(state.adminModelKey.value)}`, {
        method: 'DELETE'
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors de la suppression du modèle');
      }
      state.adminStatus.value = {
        type: 'success',
        message: 'Modèle et données associés supprimés.'
      };
      await state.refreshModels();
    } catch (error) {
      state.adminStatus.value = { type: 'error', message: error.message };
    }
  }

  async function copyAdminCommand() {
    if (!state.adminModel.value) {
      return;
    }
    try {
      await navigator.clipboard.writeText(state.adminCommand.value);
      state.adminStatus.value = { type: 'success', message: 'Commande copiée dans le presse-papiers.' };
    } catch (error) {
      state.adminStatus.value = { type: 'error', message: 'Impossible de copier la commande.' };
    }
  }

  function resetModelEditorState() {
    state.modelXml.value = '';
    state.modelXmlStatus.value = null;
    state.isLoadingModelXml.value = false;
    state.isSavingModelXml.value = false;
  }

  Object.assign(state, {
    syncAdminPermissionRows,
    resetAdminAccessEditor,
    loadAdminAccessUser,
    createAccessUser,
    saveAccessUser,
    saveAccessPermissions,
    deleteAccessUser,
    loadModelXml,
    saveModelXml,
    deleteModel,
    copyAdminCommand,
    resetModelEditorState
  });

  return state;
}
