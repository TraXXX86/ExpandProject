/** Paged data loading, neighbor cache and data mutations. */
export function useDataActions(ctx) {
  async function refreshData(modelKey = ctx.selectedModelKey.value, offset = ctx.dataOffset.value) {
    ctx.clearDataFilterTimer?.();
    if (!modelKey || !ctx.canReadModelData(modelKey)) { resetDataState(); return; }
    ctx.requests.cancelPrefix('neighbors:');
    const request = ctx.requests.start('data');
    ctx.isLoadingData.value = true;
    const query = new URLSearchParams({ modelKey, offset: String(offset), limit: String(ctx.dataLimit.value), ...ctx.dataRequestQuery.value });
    if (ctx.currentPage.value === 'search') query.set('searchable', 'true');
    try {
      const response = await ctx.apiFetch(`/api/data?${query}`, { signal: request.signal });
      const payload = await ctx.readJson(response);
      if (!request.isCurrent() || modelKey !== ctx.selectedModelKey.value) return;
      if (!response.ok) throw new Error(payload?.error || 'Erreur lors du chargement des données');
      const objects = normalizeObjects(payload?.objects || []);
      const links = normalizeLinks(payload?.links || []);
      ctx.dataObjects.value = objects;
      ctx.pageObjects.value = objects;
      ctx.dataLinks.value = links;
      ctx.neighborStatus.value = {};
      ctx.dataOffset.value = Number(payload.offset ?? offset);
      ctx.dataHasMore.value = Boolean(payload.hasMore);
      ctx.dataSummary.value = { ...buildDataSummary(payload, objects, links), totalObjects: payload.totalObjects ?? objects.length, totalLinks: payload.totalLinks ?? links.length, hasMoreLinks: Boolean(payload.hasMoreLinks) };
      const root = objects.find(object => object.idKey === ctx.selectedRootObjectKey.value) || objects[0];
      ctx.selectedObject.value = root ?? null;
      ctx.selectedRootObjectKey.value = root?.idKey || '';
      ctx.treeLinkSelections.value = {};
      ctx.treeExpandedNodes.value = {};
    } catch (error) {
      if (!request.isCurrent() || error.name === 'AbortError') return;
      resetDataState();
      ctx.status.value = { type: 'error', message: error.message };
    } finally {
      if (request.isCurrent()) ctx.isLoadingData.value = false;
    }
  }

  async function loadNeighbors(objectKey) {
    const modelKey = ctx.selectedModelKey.value;
    if (!modelKey || !objectKey || ctx.neighborStatus.value[objectKey]?.loading || ctx.neighborStatus.value[objectKey]?.loaded) return;
    const request = ctx.requests.start(`neighbors:${objectKey}`);
    ctx.neighborStatus.value = { ...ctx.neighborStatus.value, [objectKey]: { loading: true } };
    try {
      const query = new URLSearchParams({ modelKey, limit: '100' });
      const response = await ctx.apiFetch(`/api/objects/${encodeURIComponent(objectKey)}/neighbors?${query}`, { signal: request.signal });
      const payload = await ctx.readJson(response);
      if (!request.isCurrent() || modelKey !== ctx.selectedModelKey.value) return;
      if (!response.ok) throw new Error(payload?.error || 'Chargement des voisins impossible');
      const objects = new Map(ctx.dataObjects.value.map(object => [object.idKey, object]));
      normalizeObjects(payload.objects || []).forEach(object => objects.set(object.idKey, object));
      const links = new Map(ctx.dataLinks.value.map(link => [link.key, link]));
      normalizeLinks(payload.links || []).forEach(link => links.set(link.key, link));
      ctx.dataObjects.value = [...objects.values()];
      ctx.dataLinks.value = [...links.values()];
      ctx.neighborStatus.value = { ...ctx.neighborStatus.value, [objectKey]: { loaded: true, loading: false, hasMore: Boolean(payload.hasMore) } };
    } catch (error) {
      if (!request.isCurrent() || error.name === 'AbortError') return;
      ctx.neighborStatus.value = { ...ctx.neighborStatus.value, [objectKey]: { loading: false, error: error.message } };
    }
  }

  async function mutateLink(link, attributes = null) {
    const deleting = attributes === null;
    if (!link || link.id == null || !(deleting ? ctx.canDeleteCurrentModelData.value : ctx.canUpdateCurrentModelData.value)) return;
    if (deleting && !window.confirm(`Supprimer le lien #${link.id} ?`)) return;
    const modelKey = ctx.selectedModelKey.value;
    ctx.isMutatingLink.value = true;
    try {
      const response = await ctx.apiFetch(`/api/links/${encodeURIComponent(link.uuid || link.id)}?modelKey=${encodeURIComponent(modelKey)}`, {
        method: deleting ? 'DELETE' : 'PUT',
        headers: { 'Content-Type': 'application/json' },
        ...(deleting ? {} : { body: JSON.stringify({ modelKey, attributes }) })
      });
      const payload = await ctx.readJson(response);
      if (modelKey !== ctx.selectedModelKey.value) return;
      if (!response.ok) throw new Error(payload?.error || 'Modification du lien impossible');
      await refreshData(modelKey);
      ctx.status.value = { type: 'success', message: deleting ? 'Lien supprimé.' : 'Lien enregistré.' };
    } catch (error) { ctx.status.value = { type: 'error', message: error.message }; }
    finally { ctx.isMutatingLink.value = false; }
  }

  async function uploadData() {
    if (!ctx.canCreateCurrentModelData.value) {
      ctx.status.value = { type: 'warning', message: 'Droit CREATE manquant pour ce modèle.' };
      return;
    }
    const file = ctx.getFirstFile(ctx.dataFile.value);
    if (!file) {
      ctx.status.value = { type: 'warning', message: 'Sélectionnez un fichier de données.' };
      return;
    }
    if (!ctx.selectedModelKey.value) {
      ctx.status.value = { type: 'warning', message: 'Sélectionnez un modèle en base.' };
      return;
    }
    ctx.status.value = null;
    ctx.isLoadingData.value = true;
    try {
      const formData = new FormData();
      formData.append('dataFile', file);
      formData.append('modelKey', ctx.selectedModelKey.value);
      formData.append('validateOnly', String(ctx.validateOnly.value));
      const response = await ctx.apiFetch('/api/data', {
        method: 'POST',
        body: formData
      });
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors de l’import des données');
      }

      if (payload?.valid) {
        const actionLabel = payload.validateOnly ? 'Validation effectuée' : 'Données importées';
        ctx.status.value = {
          type: 'success',
          message: `${actionLabel}. ${payload.objectCount || 0} objets, ${payload.linkCount || 0} liens.`
        };
      } else {
        ctx.status.value = {
          type: 'warning',
          message: `Validation échouée. ${payload?.errors?.length || 0} erreurs détectées.`
        };
      }

      if (payload?.valid && !payload?.validateOnly) {
        await refreshData(ctx.selectedModelKey.value);
      }
    } catch (error) {
      ctx.status.value = { type: 'error', message: error.message };
    } finally {
      ctx.isLoadingData.value = false;
    }
  }

  async function createObject() {
    if (!ctx.canCreateCurrentModelData.value) {
      ctx.createObjectStatus.value = { type: 'warning', message: 'Droit CREATE manquant pour ce modèle.' };
      return;
    }
    if (!ctx.selectedModelKey.value) {
      ctx.createObjectStatus.value = { type: 'warning', message: 'Sélectionnez un modèle cible.' };
      return;
    }
    if (!ctx.createObjectType.value) {
      ctx.createObjectStatus.value = { type: 'warning', message: "Sélectionnez un type d'objet." };
      return;
    }

    const missingRequired = ctx.createObjectAttributeDefs.value.filter((def) => {
      if (!def.required) {
        return false;
      }
      const value = ctx.createObjectAttributes.value[def.name];
      return value === undefined || value === null || String(value).trim() === '';
    });

    if (missingRequired.length) {
      ctx.createObjectStatus.value = {
        type: 'warning',
        message: 'Renseignez tous les attributs obligatoires.'
      };
      return;
    }

    ctx.createObjectStatus.value = null;
    ctx.isCreatingObject.value = true;
    try {
      const attributes = ctx.createObjectAttributeDefs.value
        .map((def) => ({
          key: def.name,
          value: ctx.createObjectAttributes.value[def.name]
        }))
        .filter((attr) => attr.key && String(attr.value ?? '').trim() !== '');

      const response = await ctx.apiFetch('/api/objects', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          modelKey: ctx.selectedModelKey.value,
          type: ctx.createObjectType.value,
          attributes
        })
      });

      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(ctx.extractErrorMessage(payload, 'Erreur lors de la création.'));
      }

      ctx.createObjectStatus.value = {
        type: 'success',
        message: `Objet créé (ID #${payload?.id ?? 'OK'}).`
      };
      await refreshData(ctx.selectedModelKey.value);
    } catch (error) {
      ctx.createObjectStatus.value = { type: 'error', message: error.message };
    } finally {
      ctx.isCreatingObject.value = false;
    }
  }

  async function createLink() {
    if (!ctx.canCreateCurrentModelData.value) {
      ctx.createLinkStatus.value = { type: 'warning', message: 'Droit CREATE manquant pour ce modèle.' };
      return;
    }
    if (!ctx.selectedModelKey.value) {
      ctx.createLinkStatus.value = { type: 'warning', message: 'Sélectionnez un modèle cible.' };
      return;
    }
    if (!ctx.createLinkType.value) {
      ctx.createLinkStatus.value = { type: 'warning', message: 'Sélectionnez un type de lien.' };
      return;
    }
    if (!ctx.createLinkSourceId.value || !ctx.createLinkTargetId.value) {
      ctx.createLinkStatus.value = { type: 'warning', message: 'Sélectionnez les deux objets.' };
      return;
    }

    ctx.createLinkStatus.value = null;
    ctx.isCreatingLink.value = true;
    try {
      const response = await ctx.apiFetch('/api/links', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          modelKey: ctx.selectedModelKey.value,
          type: ctx.createLinkType.value,
          fromId: ctx.createLinkSourceId.value,
          toId: ctx.createLinkTargetId.value
        })
      });
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(ctx.extractErrorMessage(payload, 'Erreur lors de la création du lien.'));
      }

      ctx.createLinkStatus.value = {
        type: 'success',
        message: 'Lien créé.'
      };
      await refreshData(ctx.selectedModelKey.value);
    } catch (error) {
      ctx.createLinkStatus.value = { type: 'error', message: error.message };
    } finally {
      ctx.isCreatingLink.value = false;
    }
  }

  async function deleteObject(object) {
    if (!window.confirm(`Supprimer l’objet #${object?.id} et ses liens ?`)) return;
    if (!object || object.id === null || object.id === undefined) {
      return;
    }
    if (!ctx.selectedModelKey.value) {
      ctx.status.value = { type: 'warning', message: 'Sélectionnez un modèle cible.' };
      return;
    }
    if (!ctx.canDeleteCurrentModelData.value) {
      ctx.status.value = { type: 'warning', message: 'Droit DELETE manquant pour ce modèle.' };
      return;
    }

    ctx.status.value = null;
    ctx.isLoadingData.value = true;
    try {
      const response = await ctx.apiFetch(
        `/api/objects/${encodeURIComponent(object.id)}?modelKey=${encodeURIComponent(ctx.selectedModelKey.value)}`,
        { method: 'DELETE' }
      );
      const payload = await ctx.readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Erreur lors de la suppression de l'objet");
      }
      ctx.status.value = { type: 'success', message: `Objet #${object.id} supprimé.` };
      await refreshData(ctx.selectedModelKey.value);
      if (ctx.selectedObject.value?.id === object.id) {
        ctx.selectedObject.value = null;
      }
      if (ctx.tableSelectedObject.value?.id === object.id) {
        ctx.tableSelectedObject.value = null;
      }
    } catch (error) {
      ctx.status.value = { type: 'error', message: error.message };
    } finally {
      ctx.isLoadingData.value = false;
    }
  }

  function resetDataState() {
    ctx.dataSummary.value = null;
    ctx.requests.cancel('data');
    ctx.requests.cancelPrefix('neighbors:');
    ctx.isLoadingData.value = false;
    ctx.pageObjects.value = [];
    ctx.neighborStatus.value = {};
    ctx.dataOffset.value = 0;
    ctx.dataHasMore.value = false;
    ctx.dataObjects.value = [];
    ctx.dataLinks.value = [];
    ctx.selectedObject.value = null;
    ctx.selectedRootObjectKey.value = '';
    ctx.rootObjectQuery.value = '';
    ctx.treeLinkSelections.value = {};
    ctx.treeExpandedNodes.value = {};
  }

  function resetCreateState() {
    ctx.createObjectType.value = '';
    ctx.createObjectAttributes.value = {};
    ctx.createObjectStatus.value = null;
    ctx.isCreatingObject.value = false;
    ctx.createLinkType.value = '';
    ctx.createLinkSourceId.value = null;
    ctx.createLinkTargetId.value = null;
    ctx.createLinkStatus.value = null;
    ctx.isCreatingLink.value = false;
  }

  function buildDataSummary(payload, objects, links) {
    const objectTypes = Array.isArray(payload?.objectTypes)
      ? payload.objectTypes
      : Array.from(new Set(objects.map((object) => object.type)));
    return {
      objectCount: payload?.objectCount ?? objects.length,
      linkCount: payload?.linkCount ?? links.length,
      objectTypes,
      hasMore: objectTypes.length > 6
    };
  }

  function normalizeObjects(objects) {
    return objects.map((object, index) => {
      const id = object.id ?? null;
      const type = object.type || 'Objet';
      const idKey = id !== null && id !== undefined ? String(id) : `index-${index}`;
      return {
        id,
        type,
        attributes: Array.isArray(object.attributes) ? object.attributes : [],
        idKey,
        key: `${idKey}-${index}`
      };
    });
  }

  function normalizeLinks(links) {
    return links.map((link, index) => {
      const fromKey = link.fromId !== undefined ? String(link.fromId) : '';
      const toKey = link.toId !== undefined ? String(link.toId) : '';
      return {
        id: link.id ?? null,
        uuid: link.uuid || '',
        attributes: Array.isArray(link.attributes) ? link.attributes : [],
        key: link.uuid ? `link-${link.uuid}` : link.id != null ? `link-${link.id}` : `link-${index}-${fromKey}-${toKey}`,
        type: link.type || link.linkType || link.relationshipType || 'Lien',
        fromKey,
        toKey
      };
    });
  }

  return { refreshData, loadNeighbors, mutateLink, uploadData, createObject, createLink, deleteObject, resetDataState, resetCreateState, buildDataSummary, normalizeObjects, normalizeLinks };
}
