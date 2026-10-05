/** Model loading, XML import and administration. */
export function useModelActions(ctx) {
  function handleModelFile(files) {
    ctx.modelFile.value = Array.isArray(files) ? files[0] : files;
  }

  function handleDataFile(files) {
    ctx.dataFile.value = Array.isArray(files) ? files[0] : files;
  }

  async function refreshModels(preferredKey) {
    if (!ctx.isAuthenticated.value) {
      ctx.models.value = [];
      ctx.selectedModelKey.value = '';
      resetModelState();
      ctx.resetDataState();
      return;
    }
    const request = ctx.requests.start('models');
    ctx.isLoadingModels.value = true;
    ctx.status.value = null;
    refreshHealth();
    try {
      const response = await ctx.apiFetch('/api/models', { signal: request.signal });
      const payload = await ctx.readJson(response);
      if (!request.isCurrent()) return;
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors du chargement des modèles');
      }
      const list = Array.isArray(payload) ? payload : [];
      ctx.models.value = list;

      const currentKey = ctx.selectedModelKey.value;
      let nextKey = preferredKey || currentKey;
      if (!nextKey || !list.find((model) => model.key === nextKey)) {
        nextKey = list[0]?.key || '';
      }
      ctx.selectedModelKey.value = nextKey;
      ctx.syncAdminPermissionRows();

      if (nextKey && nextKey === currentKey) {
        await refreshModelDetails(nextKey);
        await ctx.refreshData(nextKey);
      }

      if (!nextKey) {
        resetModelState();
        ctx.resetDataState();
      }
    } catch (error) {
      if (!request.isCurrent() || error.name === 'AbortError') return;
      ctx.status.value = {
        type: 'error',
        message: error.message
      };
    } finally {
      if (request.isCurrent()) { ctx.isLoadingModels.value = false; ctx.hasLoadedModels.value = true; }
    }
  }

  async function refreshModelDetails(modelKey) {
    if (modelKey && !ctx.canViewModel(modelKey)) {
      resetModelState();
      return;
    }
    const request = ctx.requests.start('model');
    ctx.isLoadingModel.value = true;
    try {
      const response = await ctx.apiFetch(`/api/models/${encodeURIComponent(modelKey)}`, { signal: request.signal });
      const payload = await ctx.readJson(response);
      if (!request.isCurrent() || modelKey !== ctx.selectedModelKey.value) return;
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors du chargement du modèle');
      }
      const objectTypes = payload?.objectTypes || [];
      const linkTypes = payload?.linkTypes || [];
      const languages = Array.isArray(payload?.languages) ? payload.languages : [];
      const defaultLanguageValue = payload?.defaultLanguage || '';
      const userPortalLabels = payload?.userPortalLabels && typeof payload.userPortalLabels === 'object'
        ? payload.userPortalLabels
        : {};
      ctx.modelDetails.value = {
        objectTypes,
        linkTypes,
        languages,
        defaultLanguage: defaultLanguageValue,
        userPortalLabels
      };
      const availableCodes = languages.map((language) => language.code).filter(Boolean);
      const fallbackLanguage = defaultLanguageValue || availableCodes[0] || '';
      if (!ctx.displayLanguage.value || (availableCodes.length && !availableCodes.includes(ctx.displayLanguage.value))) {
        ctx.displayLanguage.value = fallbackLanguage;
      }
      ctx.modelSummary.value = buildModelSummary(payload);
      ctx.selectedModelObject.value = objectTypes[0] ?? null;
      ctx.selectedModelLink.value = linkTypes[0] ?? null;
      ctx.linkTypeInfo.value = buildLinkTypeInfo(linkTypes);
      ctx.hasModel.value = objectTypes.length > 0 || linkTypes.length > 0;
      ctx.modelObjectFilter.value = '';
      ctx.modelLinkFilter.value = '';
    } catch (error) {
      if (!request.isCurrent() || error.name === 'AbortError') return;
      resetModelState();
      ctx.hasModel.value = false;
      ctx.status.value = { type: 'error', message: error.message };
    } finally {
      if (request.isCurrent()) ctx.isLoadingModel.value = false;
    }
  }

  async function refreshHealth() {
    try {
      const response = await ctx.apiFetch('/api/health?deep=true');
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'API indisponible');
      }
      ctx.healthStatus.value = {
        api: payload?.api || 'ok',
        neo4j: payload?.neo4j || 'unknown',
        error: payload?.neo4jError || ''
      };
    } catch (error) {
      ctx.healthStatus.value = { api: 'ko', neo4j: 'ko', error: error.message };
    }
  }

  async function uploadModel() {
    if (!ctx.canAccessModelAdminPortal.value) {
      ctx.status.value = { type: 'warning', message: 'Accès au portail administration requis.' };
      return;
    }
    const file = ctx.getFirstFile(ctx.modelFile.value);
    if (!file) {
      ctx.status.value = { type: 'warning', message: 'Sélectionnez un fichier modèle.' };
      return;
    }
    ctx.status.value = null;
    ctx.isLoadingModel.value = true;
    try {
      const formData = new FormData();
      formData.append('modelFile', file);
      const response = await ctx.apiFetch('/api/models', {
        method: 'POST',
        body: formData
      });
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors de l’import du modèle');
      }
      await ctx.refreshCurrentAccess();
      await refreshModels(payload?.key);
      ctx.status.value = {
        type: 'success',
        message: `Modèle chargé en base (${payload?.name || 'OK'}).`
      };
    } catch (error) {
      ctx.status.value = { type: 'error', message: error.message };
    } finally {
      ctx.isLoadingModel.value = false;
    }
  }

  async function loadModelXml() {
    if (!ctx.canAccessModelAdminPortal.value) {
      ctx.modelXmlStatus.value = { type: 'warning', message: 'Accès au portail administration requis.' };
      return;
    }
    if (!ctx.selectedModelKey.value) {
      ctx.modelXmlStatus.value = { type: 'warning', message: 'Sélectionnez un modèle.' };
      return;
    }
    ctx.isLoadingModelXml.value = true;
    ctx.modelXmlStatus.value = null;
    try {
      const response = await ctx.apiFetch(`/api/models/${encodeURIComponent(ctx.selectedModelKey.value)}/xml`);
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
      ctx.modelXml.value = text;
      ctx.modelXmlStatus.value = { type: 'success', message: 'XML chargé.' };
    } catch (error) {
      ctx.modelXmlStatus.value = { type: 'error', message: error.message };
    } finally {
      ctx.isLoadingModelXml.value = false;
    }
  }

  async function saveModelXml() {
    if (!ctx.canUpdateModelData(ctx.selectedModelKey.value)) { ctx.modelXmlStatus.value = { type: 'warning', message: 'Droit UPDATE manquant pour ce modèle.' }; return; }
    if (!ctx.canAccessModelAdminPortal.value) {
      ctx.modelXmlStatus.value = { type: 'warning', message: 'Accès au portail administration requis.' };
      return;
    }
    if (!ctx.selectedModelKey.value) {
      ctx.modelXmlStatus.value = { type: 'warning', message: 'Sélectionnez un modèle.' };
      return;
    }
    if (!ctx.modelXml.value.trim()) {
      ctx.modelXmlStatus.value = { type: 'warning', message: 'XML vide.' };
      return;
    }
    ctx.isSavingModelXml.value = true;
    ctx.modelXmlStatus.value = null;
    try {
      const response = await ctx.apiFetch(`/api/models/${encodeURIComponent(ctx.selectedModelKey.value)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/xml' },
        body: ctx.modelXml.value
      });
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors de la sauvegarde du modèle.');
      }
      ctx.modelXmlStatus.value = {
        type: 'success',
        message: 'Modèle mis à jour.'
      };
      await refreshModels(payload?.key || ctx.selectedModelKey.value);
    } catch (error) {
      ctx.modelXmlStatus.value = { type: 'error', message: error.message };
    } finally {
      ctx.isSavingModelXml.value = false;
    }
  }

  async function deleteModel() {
    if (!ctx.canAccessModelAdminPortal.value) {
      ctx.adminStatus.value = { type: 'warning', message: 'Accès au portail administration requis.' };
      return;
    }
    if (!ctx.adminModelKey.value) {
      ctx.adminStatus.value = { type: 'warning', message: 'Sélectionnez un modèle.' };
      return;
    }
    if (!window.confirm('Supprimer ce modèle et toutes ses données ?')) return;
    if (!ctx.canDeleteModelData(ctx.adminModelKey.value)) { ctx.adminStatus.value = { type: 'warning', message: 'Droit DELETE manquant pour ce modèle.' }; return; }
    ctx.isDeletingModel.value = true;
    ctx.adminStatus.value = null;
    try {
      const response = await ctx.apiFetch(`/api/models/${encodeURIComponent(ctx.adminModelKey.value)}`, {
        method: 'DELETE'
      });
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors de la suppression du modèle');
      }
      ctx.adminStatus.value = {
        type: 'success',
        message: 'Modèle et données associés supprimés.'
      };
      await refreshModels();
    } catch (error) {
      ctx.adminStatus.value = { type: 'error', message: error.message };
    } finally { ctx.isDeletingModel.value = false; }
  }

  function resetModelState() {
    ctx.modelSummary.value = null;
    ctx.modelDetails.value = {
      objectTypes: [],
      linkTypes: [],
      languages: [],
      defaultLanguage: '',
      userPortalLabels: {}
    };
    ctx.selectedModelObject.value = null;
    ctx.selectedModelLink.value = null;
    ctx.linkTypeInfo.value = {};
    ctx.hasModel.value = false;
    ctx.displayLanguage.value = '';
  }

  function resetModelEditorState() {
    ctx.modelXml.value = '';
    ctx.modelXmlStatus.value = null;
    ctx.isLoadingModelXml.value = false;
    ctx.isSavingModelXml.value = false;
  }

  function selectModelObjectByName(name) {
    if (!name) {
      return;
    }
    const match = ctx.modelDetails.value.objectTypes.find((type) => type.name === name);
    if (match) {
      ctx.selectedModelObject.value = match;
      ctx.modelObjectFilter.value = name;
    }
  }

  function selectModelLinkByName(name) {
    if (!name) {
      return;
    }
    const match = ctx.modelDetails.value.linkTypes.find((link) => link.name === name);
    if (match) {
      ctx.selectedModelLink.value = match;
      ctx.modelLinkFilter.value = name;
    }
  }

  function buildModelSummary(payload) {
    const objectTypes = payload?.objectTypes || [];
    const linkTypes = payload?.linkTypes || [];
    return {
      objectCount: payload?.objectTypeCount ?? objectTypes.length,
      linkCount: payload?.linkTypeCount ?? linkTypes.length,
      types: objectTypes.slice(0, 6).map((type) => ({
        name: type.name,
        parent: type.parent,
        icon: type.icon || ''
      })),
      hasMore: objectTypes.length > 6 || linkTypes.length > 6
    };
  }

  function buildLinkTypeInfo(linkTypes) {
    const map = {};
    linkTypes.forEach((link) => {
      if (link.name) {
        map[link.name] = Boolean(link.directed);
      }
    });
    return map;
  }

  function isDirectedLink(linkType) {
    if (!linkType) {
      return false;
    }
    const known = ctx.linkTypeInfo.value[linkType];
    if (known !== undefined) {
      return known;
    }
    if (!ctx.hasModel.value) {
      return false;
    }
    return true;
  }

  return { refreshModels, refreshModelDetails, refreshHealth, uploadModel, loadModelXml, saveModelXml, deleteModel, resetModelState, resetModelEditorState, handleModelFile, handleDataFile, selectModelObjectByName, selectModelLinkByName, buildModelSummary, buildLinkTypeInfo, isDirectedLink };
}
