import { computed } from 'vue';

import { extractErrorMessage, getFirstFile, readJson } from './shared.js';

export function useDataState(state) {
  state.filteredObjects = computed(() => {
    const filter = state.objectFilter.value.trim().toLowerCase();
    if (!filter) {
      return state.dataObjects.value;
    }
    return state.dataObjects.value.filter((object) => {
      const id = object.id !== null && object.id !== undefined ? String(object.id) : '';
      return (
        object.type.toLowerCase().includes(filter)
        || id.toLowerCase().includes(filter)
      );
    });
  });

  state.rootObjectOptions = computed(() => {
    const query = state.rootObjectQuery.value.trim().toLowerCase();
    return state.dataObjects.value
      .filter((object) => {
        if (!query) {
          return true;
        }
        const id = object.id !== null && object.id !== undefined ? String(object.id) : '';
        const label = state.getObjectPrimaryLabel(object);
        const primaryValues = state.getObjectPrimaryAttributes(object)
          .map((attribute) => String(attribute.value || '').toLowerCase())
          .join(' ');
        const haystack = `${object.type} ${id} ${label} ${primaryValues}`.toLowerCase();
        return haystack.includes(query);
      })
      .slice(0, 120)
      .map((object) => ({
        title: state.formatObjectOptionLabel(object),
        value: object.idKey,
        subtitle: `${object.type} • ID ${object.id ?? 'N/A'}`
      }));
  });

  state.selectedRootObject = computed(() => {
    if (!state.selectedRootObjectKey.value) {
      return null;
    }
    return state.objectIndex.value.get(state.selectedRootObjectKey.value) || null;
  });

  state.tableTypeOptions = computed(() => {
    const types = new Set();
    state.dataObjects.value.forEach((object) => {
      if (object.type) {
        types.add(object.type);
      }
    });
    return Array.from(types)
      .sort()
      .map((type) => ({ title: type, value: type }));
  });

  state.filteredTableRows = computed(() => {
    const typeFilter = Array.isArray(state.tableTypeFilter.value)
      ? state.tableTypeFilter.value.map((type) => String(type).toLowerCase())
      : [];
    const search = state.tableSearch.value.trim().toLowerCase();
    const attributeKey = state.tableAttributeKey.value.trim().toLowerCase();
    const attributeKeyOperator = state.tableAttributeKeyOperator.value;
    const attributeValue = state.tableAttributeValue.value.trim().toLowerCase();
    const attributeValueOperator = state.tableAttributeValueOperator.value;

    return state.dataObjects.value
      .filter((object) => {
        if (typeFilter.length && !typeFilter.includes(object.type.toLowerCase())) {
          return false;
        }

        if (search) {
          const id = object.id !== null && object.id !== undefined ? String(object.id).toLowerCase() : '';
          if (!object.type.toLowerCase().includes(search) && !id.includes(search)) {
            return false;
          }
        }

        if (!attributeKey && !attributeValue) {
          return true;
        }

        const attributes = Array.isArray(object.attributes) ? object.attributes : [];
        return attributes.some((attr) => {
          const keyValue = String(attr.key || '').toLowerCase();
          const valueValue = String(attr.value || '').toLowerCase();
          const keyMatch = !attributeKey
            || (attributeKeyOperator === 'equals' ? keyValue === attributeKey : keyValue.includes(attributeKey));
          const valueMatch = !attributeValue
            || (attributeValueOperator === 'equals' ? valueValue === attributeValue : valueValue.includes(attributeValue));
          return keyMatch && valueMatch;
        });
      })
      .map((object) => {
        const attributes = Array.isArray(object.attributes) ? object.attributes : [];
        const attributePreview = attributes.slice(0, 3).map((attr) => ({
          key: attr.key,
          label: state.getAttributeLabel(object.type, attr.key),
          value: attr.value
        }));
        return {
          key: object.key,
          id: object.id ?? 'N/A',
          idKey: object.idKey,
          type: object.type,
          primaryLabel: state.getObjectPrimaryLabel(object),
          attributeCount: attributes.length,
          attributePreview,
          attributes
        };
      });
  });

  state.selectedObjectType = computed(() => {
    if (!state.selectedObject.value) {
      return null;
    }
    return state.modelDetails.value.objectTypes.find((type) => type.name === state.selectedObject.value.type) || null;
  });

  state.tableSelectedObjectType = computed(() => {
    if (!state.tableSelectedObject.value) {
      return null;
    }
    return state.modelDetails.value.objectTypes.find((type) => type.name === state.tableSelectedObject.value.type) || null;
  });

  state.attributeTabs = computed(() => buildAttributeTabs(
    state.selectedObject.value,
    state.selectedObjectType.value,
    'group'
  ));

  state.tableAttributeTabs = computed(() => buildAttributeTabs(
    state.tableSelectedObject.value,
    state.tableSelectedObjectType.value,
    'table-group'
  ));

  state.objectTypeGroups = computed(() => {
    const groups = new Map();
    state.filteredObjects.value.forEach((object) => {
      const type = object.type || 'Objet';
      if (!groups.has(type)) {
        groups.set(type, []);
      }
      groups.get(type).push(object);
    });
    return Array.from(groups.entries())
      .sort(([typeA], [typeB]) => typeA.localeCompare(typeB))
      .map(([type, objects]) => {
        const sorted = [...objects].sort((a, b) => {
          const keyA = String(a.id ?? a.idKey);
          const keyB = String(b.id ?? b.idKey);
          return keyA.localeCompare(keyB);
        });
        return {
          type,
          count: sorted.length,
          objects: sorted
        };
      });
  });

  state.tableHeaders = [
    { title: 'Type', key: 'type' },
    { title: 'ID', key: 'id' },
    { title: 'Aperçu', key: 'preview' },
    { title: 'Attributs', key: 'attributes' },
    { title: 'Actions', key: 'actions', sortable: false }
  ];

  state.fullTextHeaders = [
    { title: 'Type', key: 'type' },
    { title: 'ID', key: 'id' },
    { title: 'Aperçu', key: 'preview' },
    { title: 'Correspondances', key: 'matches' },
    { title: 'Actions', key: 'actions', sortable: false }
  ];

  state.tableOperatorOptions = [
    { title: 'Contient', value: 'contains' },
    { title: 'Egal', value: 'equals' }
  ];

  state.tableHasDetails = computed(
    () => Boolean(state.tableSelectedObject.value && state.showTableDetailPanel.value)
  );

  state.fullTextTypeOptions = computed(() => state.tableTypeOptions.value);

  state.fullTextResults = computed(() => {
    const query = state.fullTextQuery.value.trim().toLowerCase();
    if (!query) {
      return [];
    }

    const typeFilter = Array.isArray(state.fullTextTypeFilter.value)
      ? state.fullTextTypeFilter.value.map((type) => String(type).toLowerCase())
      : [];

    return state.dataObjects.value
      .filter((object) => {
        if (typeFilter.length && !typeFilter.includes(object.type.toLowerCase())) {
          return false;
        }
        const searchable = state.searchableAttributesByType.value.get(object.type);
        if (!searchable || searchable.size === 0) {
          return false;
        }
        const attributes = Array.isArray(object.attributes) ? object.attributes : [];
        return attributes.some((attr) => {
          if (!searchable.has(attr.key)) {
            return false;
          }
          const value = String(attr.value || '').toLowerCase();
          return value.includes(query);
        });
      })
      .map((object) => {
        const searchable = state.searchableAttributesByType.value.get(object.type) || new Set();
        const attributes = Array.isArray(object.attributes) ? object.attributes : [];
        const matches = attributes
          .filter((attr) => searchable.has(attr.key))
          .filter((attr) => String(attr.value || '').toLowerCase().includes(query))
          .map((attr) => ({
            key: attr.key,
            label: state.getAttributeLabel(object.type, attr.key),
            value: attr.value
          }));

        return {
          key: object.key,
          id: object.id ?? 'N/A',
          idKey: object.idKey,
          type: object.type,
          primaryLabel: state.getObjectPrimaryLabel(object),
          matches
        };
      });
  });

  state.objectIndex = computed(() => {
    const map = new Map();
    state.dataObjects.value.forEach((object) => {
      map.set(object.idKey, object);
    });
    return map;
  });

  state.relationsByObjectKey = computed(() => {
    const map = new Map();

    const appendRelation = (sourceKey, targetKey, link, direction) => {
      if (!sourceKey || !targetKey) {
        return;
      }
      const source = state.objectIndex.value.get(sourceKey);
      const target = state.objectIndex.value.get(targetKey);
      if (!source || !target) {
        return;
      }

      const key = `${link.key}:${direction}:${targetKey}`;
      const list = map.get(sourceKey) || [];
      list.push({
        key,
        type: link.type,
        targetKey,
        target,
        direction,
        directionLabel: directionToLabel(direction),
        directionIcon: directionToIcon(direction),
        targetLabel: state.formatObjectOptionLabel(target)
      });
      map.set(sourceKey, list);
    };

    state.dataLinks.value.forEach((link) => {
      const directed = state.isDirectedLink(link.type);
      const outDirection = directed ? 'out' : 'both';
      const inDirection = directed ? 'in' : 'both';

      if (link.fromKey && link.toKey && link.fromKey === link.toKey) {
        appendRelation(link.fromKey, link.toKey, link, outDirection);
        return;
      }

      appendRelation(link.fromKey, link.toKey, link, outDirection);
      appendRelation(link.toKey, link.fromKey, link, inDirection);
    });

    map.forEach((relations, sourceKey) => {
      relations.sort((a, b) => {
        if (a.type !== b.type) {
          return a.type.localeCompare(b.type);
        }
        if (a.direction !== b.direction) {
          return a.direction.localeCompare(b.direction);
        }
        const typeA = a.target.type || '';
        const typeB = b.target.type || '';
        if (typeA !== typeB) {
          return typeA.localeCompare(typeB);
        }
        const idA = String(a.target.id ?? a.target.idKey);
        const idB = String(b.target.id ?? b.target.idKey);
        return idA.localeCompare(idB);
      });
      map.set(sourceKey, relations);
    });

    return map;
  });

  state.tableSelectedLinks = computed(() => {
    if (!state.tableSelectedObject.value) {
      return [];
    }
    return state.relationsByObjectKey.value.get(state.tableSelectedObject.value.idKey) || [];
  });

  function handleDataFile(files) {
    state.dataFile.value = Array.isArray(files) ? files[0] : files;
  }

  async function refreshData(modelKey) {
    if (modelKey && !state.canReadModelData(modelKey)) {
      resetDataState();
      return;
    }
    state.isLoadingData.value = true;
    try {
      const response = await state.apiFetch(`/api/data?modelKey=${encodeURIComponent(modelKey)}`);
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors du chargement des données');
      }

      const objects = normalizeObjects(payload?.objects || []);
      const links = normalizeLinks(payload?.links || []);
      state.dataObjects.value = objects;
      state.dataLinks.value = links;
      state.dataSummary.value = buildDataSummary(payload, objects, links);
      state.selectedObject.value = objects[0] ?? null;
      state.selectedRootObjectKey.value = objects[0]?.idKey || '';
      state.rootObjectQuery.value = '';
      state.treeLinkSelections.value = {};
      state.treeExpandedNodes.value = {};
      state.objectFilter.value = '';
    } catch (error) {
      resetDataState();
      state.status.value = { type: 'error', message: error.message };
    } finally {
      state.isLoadingData.value = false;
    }
  }

  async function uploadData() {
    if (!state.canCreateCurrentModelData.value) {
      state.status.value = { type: 'warning', message: 'Droit CREATE manquant pour ce modèle.' };
      return;
    }
    const file = getFirstFile(state.dataFile.value);
    if (!file) {
      state.status.value = { type: 'warning', message: 'Sélectionnez un fichier de données.' };
      return;
    }
    if (!state.selectedModelKey.value) {
      state.status.value = { type: 'warning', message: 'Sélectionnez un modèle en base.' };
      return;
    }
    state.status.value = null;
    state.isLoadingData.value = true;
    try {
      const formData = new FormData();
      formData.append('dataFile', file);
      formData.append('modelKey', state.selectedModelKey.value);
      formData.append('validateOnly', String(state.validateOnly.value));
      const response = await state.apiFetch('/api/data', {
        method: 'POST',
        body: formData
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors de l’import des données');
      }

      if (payload?.valid) {
        const actionLabel = payload.validateOnly ? 'Validation effectuée' : 'Données importées';
        state.status.value = {
          type: 'success',
          message: `${actionLabel}. ${payload.objectCount || 0} objets, ${payload.linkCount || 0} liens.`
        };
      } else {
        state.status.value = {
          type: 'warning',
          message: `Validation échouée. ${payload?.errors?.length || 0} erreurs détectées.`
        };
      }

      if (payload?.valid && !payload?.validateOnly) {
        await refreshData(state.selectedModelKey.value);
      }
    } catch (error) {
      state.status.value = { type: 'error', message: error.message };
    } finally {
      state.isLoadingData.value = false;
    }
  }

  async function createObject() {
    if (!state.canCreateCurrentModelData.value) {
      state.createObjectStatus.value = { type: 'warning', message: 'Droit CREATE manquant pour ce modèle.' };
      return;
    }
    if (!state.selectedModelKey.value) {
      state.createObjectStatus.value = { type: 'warning', message: 'Sélectionnez un modèle cible.' };
      return;
    }
    if (!state.createObjectType.value) {
      state.createObjectStatus.value = { type: 'warning', message: "Sélectionnez un type d'objet." };
      return;
    }

    const missingRequired = state.createObjectAttributeDefs.value.filter((def) => {
      if (!def.required) {
        return false;
      }
      const value = state.createObjectAttributes.value[def.name];
      return value === undefined || value === null || String(value).trim() === '';
    });

    if (missingRequired.length) {
      state.createObjectStatus.value = {
        type: 'warning',
        message: 'Renseignez tous les attributs obligatoires.'
      };
      return;
    }

    state.createObjectStatus.value = null;
    state.isCreatingObject.value = true;
    try {
      const attributes = state.createObjectAttributeDefs.value
        .map((def) => ({
          key: def.name,
          value: state.createObjectAttributes.value[def.name]
        }))
        .filter((attr) => attr.key && String(attr.value ?? '').trim() !== '');

      const response = await state.apiFetch('/api/objects', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          modelKey: state.selectedModelKey.value,
          type: state.createObjectType.value,
          attributes
        })
      });

      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(extractErrorMessage(payload, 'Erreur lors de la création.'));
      }

      state.createObjectStatus.value = {
        type: 'success',
        message: `Objet créé (ID #${payload?.id ?? 'OK'}).`
      };
      await refreshData(state.selectedModelKey.value);
    } catch (error) {
      state.createObjectStatus.value = { type: 'error', message: error.message };
    } finally {
      state.isCreatingObject.value = false;
    }
  }

  async function createLink() {
    if (!state.canCreateCurrentModelData.value) {
      state.createLinkStatus.value = { type: 'warning', message: 'Droit CREATE manquant pour ce modèle.' };
      return;
    }
    if (!state.selectedModelKey.value) {
      state.createLinkStatus.value = { type: 'warning', message: 'Sélectionnez un modèle cible.' };
      return;
    }
    if (!state.createLinkType.value) {
      state.createLinkStatus.value = { type: 'warning', message: 'Sélectionnez un type de lien.' };
      return;
    }
    if (!state.createLinkSourceId.value || !state.createLinkTargetId.value) {
      state.createLinkStatus.value = { type: 'warning', message: 'Sélectionnez les deux objets.' };
      return;
    }

    state.createLinkStatus.value = null;
    state.isCreatingLink.value = true;
    try {
      const response = await state.apiFetch('/api/links', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          modelKey: state.selectedModelKey.value,
          type: state.createLinkType.value,
          fromId: state.createLinkSourceId.value,
          toId: state.createLinkTargetId.value
        })
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(extractErrorMessage(payload, 'Erreur lors de la création du lien.'));
      }

      state.createLinkStatus.value = {
        type: 'success',
        message: 'Lien créé.'
      };
      await refreshData(state.selectedModelKey.value);
    } catch (error) {
      state.createLinkStatus.value = { type: 'error', message: error.message };
    } finally {
      state.isCreatingLink.value = false;
    }
  }

  async function deleteObject(object) {
    if (!object || object.id === null || object.id === undefined) {
      return;
    }
    if (!state.selectedModelKey.value) {
      state.status.value = { type: 'warning', message: 'Sélectionnez un modèle cible.' };
      return;
    }
    if (!state.canDeleteCurrentModelData.value) {
      state.status.value = { type: 'warning', message: 'Droit DELETE manquant pour ce modèle.' };
      return;
    }

    state.status.value = null;
    state.isLoadingData.value = true;
    try {
      const response = await state.apiFetch(
        `/api/objects/${encodeURIComponent(object.id)}?modelKey=${encodeURIComponent(state.selectedModelKey.value)}`,
        { method: 'DELETE' }
      );
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Erreur lors de la suppression de l'objet");
      }
      state.status.value = { type: 'success', message: `Objet #${object.id} supprimé.` };
      await refreshData(state.selectedModelKey.value);
      if (state.selectedObject.value?.id === object.id) {
        state.selectedObject.value = null;
      }
      if (state.tableSelectedObject.value?.id === object.id) {
        state.tableSelectedObject.value = null;
      }
    } catch (error) {
      state.status.value = { type: 'error', message: error.message };
    } finally {
      state.isLoadingData.value = false;
    }
  }

  function resetDataState() {
    state.dataSummary.value = null;
    state.dataObjects.value = [];
    state.dataLinks.value = [];
    state.selectedObject.value = null;
    state.selectedRootObjectKey.value = '';
    state.rootObjectQuery.value = '';
    state.treeLinkSelections.value = {};
    state.treeExpandedNodes.value = {};
  }

  function resetCreateState() {
    state.createObjectType.value = '';
    state.createObjectAttributes.value = {};
    state.createObjectStatus.value = null;
    state.isCreatingObject.value = false;
    state.createLinkType.value = '';
    state.createLinkSourceId.value = null;
    state.createLinkTargetId.value = null;
    state.createLinkStatus.value = null;
    state.isCreatingLink.value = false;
  }

  function directionToLabel(direction) {
    if (direction === 'out') {
      return 'sortant';
    }
    if (direction === 'in') {
      return 'entrant';
    }
    return 'bidirectionnel';
  }

  function directionToIcon(direction) {
    if (direction === 'out') {
      return 'mdi-arrow-right';
    }
    if (direction === 'in') {
      return 'mdi-arrow-left';
    }
    return 'mdi-arrow-left-right';
  }

  function buildDataSummary(payload, objects, links) {
    const objectTypes = Array.isArray(payload?.objectTypes)
      ? payload.objectTypes
      : Array.from(new Set(objects.map((object) => object.type)));
    return {
      objectCount: payload?.objectCount ?? objects.length,
      linkCount: payload?.linkCount ?? links.length,
      objectTypes: objectTypes.slice(0, 6),
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
        key: `link-${index}-${fromKey}-${toKey}`,
        type: link.type || link.linkType || link.relationshipType || 'Lien',
        fromKey,
        toKey
      };
    });
  }

  function buildAttributeTabs(selectedObject, typeDef, keyPrefix) {
    if (!selectedObject) {
      return [];
    }

    const objectAttributes = Array.isArray(selectedObject.attributes)
      ? selectedObject.attributes
      : [];
    const valueMap = new Map(objectAttributes.map((attr) => [attr.key, attr.value]));
    const definitionMap = new Map();

    if (typeDef && Array.isArray(typeDef.attributes)) {
      typeDef.attributes.forEach((attr) => {
        definitionMap.set(attr.name, attr);
      });
    }

    const used = new Set();
    const tabs = [];
    const groupDefs = typeDef && Array.isArray(typeDef.attributeGroups) ? typeDef.attributeGroups : [];

    const sortedGroups = [...groupDefs].sort((a, b) => {
      const orderA = a.order ?? 9999;
      const orderB = b.order ?? 9999;
      if (orderA !== orderB) {
        return orderA - orderB;
      }
      return String(a.name || '').localeCompare(String(b.name || ''));
    });

    sortedGroups.forEach((group, groupIndex) => {
      const groupAttributes = Array.isArray(group.attributes) ? group.attributes : [];
      const sortedAttributes = [...groupAttributes].sort((a, b) => {
        const orderA = a.order ?? a.index ?? 9999;
        const orderB = b.order ?? b.index ?? 9999;
        if (orderA !== orderB) {
          return orderA - orderB;
        }
        return String(a.name || '').localeCompare(String(b.name || ''));
      });

      const rows = [];
      sortedAttributes.forEach((ref) => {
        if (!ref.name) {
          return;
        }
        const definition = definitionMap.get(ref.name);
        rows.push({
          key: ref.name,
          label: state.formatAttributeLabel(ref.name, definition),
          value: valueMap.get(ref.name) ?? '',
          type: definition?.type || '',
          required: Boolean(definition?.required),
          description: definition?.description || ''
        });
        used.add(ref.name);
      });

      if (rows.length) {
        tabs.push({
          key: `${keyPrefix}-${groupIndex}-${group.name || 'groupe'}`,
          label: group.name || 'Groupe',
          attributes: rows
        });
      }
    });

    const remaining = [];
    definitionMap.forEach((definition, name) => {
      if (used.has(name)) {
        return;
      }
      remaining.push({
        key: name,
        label: state.formatAttributeLabel(name, definition),
        value: valueMap.get(name) ?? '',
        type: definition?.type || '',
        required: Boolean(definition?.required),
        description: definition?.description || ''
      });
      used.add(name);
    });

    objectAttributes.forEach((attr) => {
      if (used.has(attr.key)) {
        return;
      }
      remaining.push({
        key: attr.key,
        label: attr.key,
        value: attr.value ?? '',
        type: '',
        required: false,
        description: 'Attribut non défini dans le modèle'
      });
      used.add(attr.key);
    });

    if (remaining.length) {
      remaining.sort((a, b) => String(a.key).localeCompare(String(b.key)));
      tabs.push({
        key: `${keyPrefix}-autres`,
        label: 'Autres',
        attributes: remaining
      });
    }

    if (!tabs.length && objectAttributes.length) {
      tabs.push({
        key: `${keyPrefix}-attributs`,
        label: 'Attributs',
        attributes: objectAttributes.map((attr) => ({
          key: attr.key,
          label: attr.key,
          value: attr.value ?? '',
          type: '',
          required: false,
          description: ''
        }))
      });
    }

    return tabs;
  }

  Object.assign(state, {
    handleDataFile,
    refreshData,
    uploadData,
    createObject,
    createLink,
    deleteObject,
    resetDataState,
    resetCreateState,
    directionToLabel,
    directionToIcon
  });

  return state;
}
