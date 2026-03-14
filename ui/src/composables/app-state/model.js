import { computed } from 'vue';

import { createEmptyModelDetails } from './base.js';
import { getFirstFile, readJson } from './shared.js';

export function useModelState(state) {
  state.modelLanguages = computed(() => {
    const languages = Array.isArray(state.modelDetails.value.languages)
      ? state.modelDetails.value.languages
      : [];
    return languages.map((language) => ({
      title: language.label || language.code || '',
      value: language.code || ''
    }));
  });

  state.defaultLanguage = computed(() => state.modelDetails.value.defaultLanguage || '');

  state.modelOptions = computed(() =>
    state.models.value.map((model) => ({
      title: model.version ? `${model.name} v${model.version}` : model.name,
      value: model.key
    }))
  );

  state.createObjectTypeOptions = computed(() =>
    state.modelDetails.value.objectTypes
      .map((type) => ({ title: type.name, value: type.name }))
      .sort((a, b) => a.title.localeCompare(b.title))
  );

  state.createLinkTypeOptions = computed(() =>
    state.modelDetails.value.linkTypes
      .map((link) => {
        const label = getLinkTypeLabel(link.name);
        return {
          title: label === link.name ? link.name : `${label} (${link.name})`,
          value: link.name
        };
      })
      .sort((a, b) => a.title.localeCompare(b.title))
  );

  state.selectedModel = computed(
    () => state.models.value.find((model) => model.key === state.selectedModelKey.value) || null
  );

  state.canUploadModel = computed(
    () => Boolean(getFirstFile(state.modelFile.value) && state.canAccessModelAdminPortal.value)
  );
  state.canUploadData = computed(
    () => Boolean(getFirstFile(state.dataFile.value) && state.selectedModelKey.value && state.canCreateCurrentModelData.value)
  );

  state.neo4jChipLabel = computed(() => {
    if (state.healthStatus.value.neo4j === 'ok') {
      return 'OK';
    }
    if (state.healthStatus.value.neo4j === 'ko') {
      return 'KO';
    }
    return '—';
  });

  state.neo4jChipColor = computed(() => {
    if (state.healthStatus.value.neo4j === 'ok') {
      return 'success';
    }
    if (state.healthStatus.value.neo4j === 'ko') {
      return 'error';
    }
    return 'secondary';
  });

  state.filteredModelObjects = computed(() => {
    const filter = state.modelObjectFilter.value.trim().toLowerCase();
    if (!filter) {
      return state.modelDetails.value.objectTypes;
    }
    return state.modelDetails.value.objectTypes.filter((type) =>
      type.name.toLowerCase().includes(filter)
    );
  });

  state.filteredModelLinks = computed(() => {
    const filter = state.modelLinkFilter.value.trim().toLowerCase();
    if (!filter) {
      return state.modelDetails.value.linkTypes;
    }
    return state.modelDetails.value.linkTypes.filter((link) => {
      const code = String(link.name || '').toLowerCase();
      const label = String(getLinkTypeLabel(link.name) || '').toLowerCase();
      return code.includes(filter) || label.includes(filter);
    });
  });

  state.modelGroupTabs = computed(() => {
    if (!state.selectedModelObject.value) {
      return [];
    }

    const attributes = Array.isArray(state.selectedModelObject.value.attributes)
      ? state.selectedModelObject.value.attributes
      : [];
    const attributeMap = new Map(attributes.map((attr) => [attr.name, attr]));
    const groups = Array.isArray(state.selectedModelObject.value.attributeGroups)
      ? state.selectedModelObject.value.attributeGroups
      : [];

    const sortedGroups = [...groups].sort((a, b) => {
      const orderA = a.order ?? 9999;
      const orderB = b.order ?? 9999;
      if (orderA !== orderB) {
        return orderA - orderB;
      }
      return String(a.name || '').localeCompare(String(b.name || ''));
    });

    const used = new Set();
    const tabs = sortedGroups.map((group, groupIndex) => {
      const groupAttributes = Array.isArray(group.attributes) ? group.attributes : [];
      const sortedAttributes = [...groupAttributes].sort((a, b) => {
        const orderA = a.order ?? a.index ?? 9999;
        const orderB = b.order ?? b.index ?? 9999;
        if (orderA !== orderB) {
          return orderA - orderB;
        }
        return String(a.name || '').localeCompare(String(b.name || ''));
      });

      const rows = sortedAttributes
        .filter((ref) => ref.name)
        .map((ref) => {
          const definition = attributeMap.get(ref.name);
          used.add(ref.name);
          return {
            name: ref.name,
            label: formatAttributeLabel(ref.name, definition),
            order: ref.order ?? ref.index ?? '',
            type: definition?.type || '',
            required: Boolean(definition?.required),
            defaultValue: definition?.defaultValue || ''
          };
        });

      return {
        key: `model-group-${groupIndex}-${group.name || 'groupe'}`,
        label: group.name || 'Groupe',
        attributes: rows
      };
    });

    const remaining = attributes.filter((attr) => !used.has(attr.name));
    if (remaining.length) {
      tabs.push({
        key: 'model-group-autres',
        label: 'Non groupés',
        attributes: remaining.map((attr) => ({
          name: attr.name,
          label: formatAttributeLabel(attr.name, attr),
          order: '',
          type: attr.type || '',
          required: Boolean(attr.required),
          defaultValue: attr.defaultValue || ''
        }))
      });
    }

    return tabs;
  });

  state.modelTypeIndex = computed(() => {
    const index = new Map();
    state.modelDetails.value.objectTypes.forEach((type) => {
      if (type.name) {
        index.set(type.name, type);
      }
    });
    return index;
  });

  state.createObjectAttributeDefs = computed(() => {
    if (!state.createObjectType.value) {
      return [];
    }
    const chain = [];
    const visited = new Set();
    let current = state.modelTypeIndex.value.get(state.createObjectType.value);
    while (current && current.name && !visited.has(current.name)) {
      visited.add(current.name);
      chain.push(current);
      const parentName = current.parent;
      if (!parentName) {
        break;
      }
      current = state.modelTypeIndex.value.get(parentName);
    }

    const merged = new Map();
    chain.reverse().forEach((type) => {
      const attributes = Array.isArray(type.attributes) ? type.attributes : [];
      attributes.forEach((attr) => {
        if (attr && attr.name) {
          merged.set(attr.name, attr);
        }
      });
    });

    return Array.from(merged.values())
      .map((attr) => ({
        ...attr,
        label: formatAttributeLabel(attr.name, attr) || attr.name
      }))
      .sort((a, b) => String(a.label).localeCompare(String(b.label)));
  });

  state.createLinkDefinition = computed(
    () => state.modelDetails.value.linkTypes.find((link) => link.name === state.createLinkType.value) || null
  );

  state.createLinkSourceOptions = computed(() => {
    if (!state.createLinkDefinition.value || !state.dataObjects.value.length) {
      return [];
    }
    const allowed = Array.isArray(state.createLinkDefinition.value.sources)
      ? state.createLinkDefinition.value.sources
      : [];
    if (!allowed.length) {
      return [];
    }
    return state.dataObjects.value
      .filter((object) => isTypeAllowed(object.type, allowed))
      .map((object) => ({
        title: formatObjectOptionLabel(object),
        value: object.id
      }))
      .sort((a, b) => String(a.title).localeCompare(String(b.title)));
  });

  state.createLinkTargetOptions = computed(() => {
    if (!state.createLinkDefinition.value || !state.dataObjects.value.length) {
      return [];
    }
    const allowed = Array.isArray(state.createLinkDefinition.value.targets)
      ? state.createLinkDefinition.value.targets
      : [];
    if (!allowed.length) {
      return [];
    }
    return state.dataObjects.value
      .filter((object) => isTypeAllowed(object.type, allowed))
      .map((object) => ({
        title: formatObjectOptionLabel(object),
        value: object.id
      }))
      .sort((a, b) => String(a.title).localeCompare(String(b.title)));
  });

  state.graphNodes = computed(() => buildGraphNodes());
  state.graphEdges = computed(() => buildGraphEdges());

  state.representativeAttributesByType = computed(() => {
    const map = new Map();
    state.modelDetails.value.objectTypes.forEach((type) => {
      const refs = Array.isArray(type.representativeAttributes)
        ? type.representativeAttributes
        : [];
      const sorted = [...refs].sort((a, b) => {
        const orderA = a.order ?? a.index ?? 0;
        const orderB = b.order ?? b.index ?? 0;
        if (orderA === orderB) {
          return String(a.name || '').localeCompare(String(b.name || ''));
        }
        return orderA - orderB;
      });
      map.set(
        type.name,
        sorted.map((ref) => ref.name).filter(Boolean)
      );
    });
    return map;
  });

  state.searchableAttributesByType = computed(() => {
    const map = new Map();
    state.modelDetails.value.objectTypes.forEach((type) => {
      const searchable = new Set();
      if (Array.isArray(type.attributes)) {
        type.attributes.forEach((attr) => {
          if (attr.searchable) {
            searchable.add(attr.name);
          }
        });
      }
      map.set(type.name, searchable);
    });
    return map;
  });

  function handleModelFile(files) {
    state.modelFile.value = Array.isArray(files) ? files[0] : files;
  }

  async function refreshModels(preferredKey) {
    if (!state.isAuthenticated.value) {
      state.models.value = [];
      state.selectedModelKey.value = '';
      resetModelState();
      state.resetDataState();
      return;
    }
    state.isLoadingModels.value = true;
    state.status.value = null;
    state.refreshHealth();
    try {
      const response = await state.apiFetch('/api/models');
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors du chargement des modèles');
      }
      const list = Array.isArray(payload) ? payload : [];
      state.models.value = list;

      const currentKey = state.selectedModelKey.value;
      let nextKey = preferredKey || currentKey;
      if (!nextKey || !list.find((model) => model.key === nextKey)) {
        nextKey = list[0]?.key || '';
      }
      state.selectedModelKey.value = nextKey;
      state.syncAdminPermissionRows?.();

      if (nextKey && nextKey === currentKey) {
        await refreshModelDetails(nextKey);
        await state.refreshData(nextKey);
      }

      if (!nextKey) {
        resetModelState();
        state.resetDataState();
      }
    } catch (error) {
      state.status.value = {
        type: 'error',
        message: error.message
      };
    } finally {
      state.isLoadingModels.value = false;
    }
  }

  async function refreshModelDetails(modelKey) {
    if (modelKey && !state.canViewModel(modelKey)) {
      resetModelState();
      return;
    }
    state.isLoadingModel.value = true;
    try {
      const response = await state.apiFetch(`/api/models/${encodeURIComponent(modelKey)}`);
      const payload = await readJson(response);
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
      state.modelDetails.value = {
        objectTypes,
        linkTypes,
        languages,
        defaultLanguage: defaultLanguageValue,
        userPortalLabels
      };
      const availableCodes = languages.map((language) => language.code).filter(Boolean);
      const fallbackLanguage = defaultLanguageValue || availableCodes[0] || '';
      if (!state.displayLanguage.value || (availableCodes.length && !availableCodes.includes(state.displayLanguage.value))) {
        state.displayLanguage.value = fallbackLanguage;
      }
      state.modelSummary.value = buildModelSummary(payload);
      state.selectedModelObject.value = objectTypes[0] ?? null;
      state.selectedModelLink.value = linkTypes[0] ?? null;
      state.linkTypeInfo.value = buildLinkTypeInfo(linkTypes);
      state.hasModel.value = objectTypes.length > 0 || linkTypes.length > 0;
      state.modelObjectFilter.value = '';
      state.modelLinkFilter.value = '';
    } catch (error) {
      resetModelState();
      state.hasModel.value = false;
      state.status.value = { type: 'error', message: error.message };
    } finally {
      state.isLoadingModel.value = false;
    }
  }

  async function refreshHealth() {
    try {
      const response = await state.apiFetch('/api/health?deep=true');
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'API indisponible');
      }
      state.healthStatus.value = {
        api: payload?.api || 'ok',
        neo4j: payload?.neo4j || 'unknown',
        error: payload?.neo4jError || ''
      };
    } catch (error) {
      state.healthStatus.value = { api: 'ko', neo4j: 'ko', error: error.message };
    }
  }

  async function uploadModel() {
    if (!state.canAccessModelAdminPortal.value) {
      state.status.value = { type: 'warning', message: 'Accès au portail administration requis.' };
      return;
    }
    const file = getFirstFile(state.modelFile.value);
    if (!file) {
      state.status.value = { type: 'warning', message: 'Sélectionnez un fichier modèle.' };
      return;
    }
    state.status.value = null;
    state.isLoadingModel.value = true;
    try {
      const formData = new FormData();
      formData.append('modelFile', file);
      const response = await state.apiFetch('/api/models', {
        method: 'POST',
        body: formData
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors de l’import du modèle');
      }
      state.status.value = {
        type: 'success',
        message: `Modèle chargé en base (${payload?.name || 'OK'}).`
      };
      await refreshModels(payload?.key);
    } catch (error) {
      state.status.value = { type: 'error', message: error.message };
    } finally {
      state.isLoadingModel.value = false;
    }
  }

  function resetModelState() {
    state.modelSummary.value = null;
    state.modelDetails.value = createEmptyModelDetails();
    state.selectedModelObject.value = null;
    state.selectedModelLink.value = null;
    state.linkTypeInfo.value = {};
    state.hasModel.value = false;
    state.displayLanguage.value = '';
  }

  function buildGraphNodes() {
    const nodes = state.modelDetails.value.objectTypes;
    if (!nodes.length) {
      return [];
    }
    const centerX = 320;
    const centerY = 200;
    const radius = Math.max(120, Math.min(160, nodes.length * 12));
    const step = (Math.PI * 2) / nodes.length;

    return nodes.map((node, index) => {
      const angle = index * step - Math.PI / 2;
      const x = centerX + Math.cos(angle) * radius;
      const y = centerY + Math.sin(angle) * radius;
      const label = node.name.length > 6 ? `${node.name.slice(0, 6)}...` : node.name;
      return {
        key: `node-${node.name}-${index}`,
        name: node.name,
        label,
        x,
        y
      };
    });
  }

  function buildGraphEdges() {
    if (!state.graphNodes.value.length) {
      return [];
    }
    const nodeMap = new Map(state.graphNodes.value.map((node) => [node.name, node]));
    const edges = [];

    state.modelDetails.value.linkTypes.forEach((link, linkIndex) => {
      link.sources.forEach((source) => {
        link.targets.forEach((target) => {
          const from = nodeMap.get(source);
          const to = nodeMap.get(target);
          if (!from || !to) {
            return;
          }
          const self = source === target;
          edges.push({
            key: `${link.name}-${source}-${target}-${linkIndex}`,
            name: link.name,
            from,
            to,
            directed: link.directed,
            self,
            path: self ? buildSelfLoopPath(from) : '',
            label: `${link.name} (${source}${link.directed ? ' → ' : ' ↔ '}${target})`
          });
        });
      });
    });

    return edges;
  }

  function buildSelfLoopPath(node) {
    const radius = 22;
    const loop = 36;
    const startX = node.x + radius;
    const startY = node.y - radius;
    const c1X = node.x + loop;
    const c1Y = node.y - loop * 1.4;
    const c2X = node.x + loop * 1.8;
    const c2Y = node.y + loop * 0.4;
    const endX = node.x;
    const endY = node.y + radius;
    return `M ${startX} ${startY} C ${c1X} ${c1Y}, ${c2X} ${c2Y}, ${endX} ${endY}`;
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
    const known = state.linkTypeInfo.value[linkType];
    if (known !== undefined) {
      return known;
    }
    if (!state.hasModel.value) {
      return false;
    }
    return true;
  }

  function resolveAttributeLabel(definition, language, fallbackLanguage) {
    if (!definition || !definition.labels) {
      return '';
    }
    const labels = definition.labels;
    const requested = language || state.displayLanguage.value;
    const fallback = fallbackLanguage || state.defaultLanguage.value;
    if (requested && labels[requested]) {
      return labels[requested];
    }
    if (fallback && labels[fallback]) {
      return labels[fallback];
    }
    return '';
  }

  function formatAttributeLabel(name, definition) {
    const label = resolveAttributeLabel(definition);
    return label || name || '';
  }

  function sanitizeMaterialIconName(iconName) {
    if (!iconName) {
      return '';
    }
    const normalized = String(iconName).trim().toLowerCase().replace(/[\s-]+/g, '_');
    if (!normalized || !/^[a-z0-9_]+$/.test(normalized)) {
      return '';
    }
    return normalized;
  }

  function getTypeIconName(typeName) {
    if (!typeName) {
      return 'category';
    }
    const typeDef = state.modelDetails.value.objectTypes.find((type) => type.name === typeName);
    const iconName = sanitizeMaterialIconName(typeDef?.icon);
    return iconName || 'category';
  }

  function getAttributeDefinition(typeName, attributeName) {
    if (!typeName || !attributeName) {
      return null;
    }
    const typeDef = state.modelDetails.value.objectTypes.find((type) => type.name === typeName);
    if (!typeDef || !Array.isArray(typeDef.attributes)) {
      return null;
    }
    return typeDef.attributes.find((attr) => attr.name === attributeName) || null;
  }

  function getAttributeLabel(typeName, attributeName) {
    const definition = getAttributeDefinition(typeName, attributeName);
    const label = resolveAttributeLabel(definition);
    return label || attributeName || '';
  }

  function getLinkTypeDefinition(linkTypeName) {
    if (!linkTypeName) {
      return null;
    }
    return state.modelDetails.value.linkTypes.find((link) => link.name === linkTypeName) || null;
  }

  function getLinkTypeLabel(linkTypeName, direction = 'both') {
    if (!linkTypeName) {
      return '';
    }
    const definition = getLinkTypeDefinition(linkTypeName);
    if (!definition) {
      return linkTypeName;
    }

    const sourceLabel = resolveAttributeLabel({
      labels: definition.sourceLabels || {}
    });
    const targetLabel = resolveAttributeLabel({
      labels: definition.targetLabels || {}
    });
    const commonLabel = resolveAttributeLabel({
      labels: definition.labels || {}
    });

    if (direction === 'out' && sourceLabel) {
      return sourceLabel;
    }
    if (direction === 'in' && targetLabel) {
      return targetLabel;
    }
    if (commonLabel) {
      return commonLabel;
    }
    if (direction === 'out' && sourceLabel) {
      return sourceLabel;
    }
    if (direction === 'in' && targetLabel) {
      return targetLabel;
    }
    return linkTypeName;
  }

  function getUserPortalTitle() {
    const labels = state.modelDetails.value?.userPortalLabels || {};
    const requested = state.displayLanguage.value;
    const fallback = state.defaultLanguage.value;

    if (requested && labels[requested]) {
      return labels[requested];
    }
    if (fallback && labels[fallback]) {
      return labels[fallback];
    }

    const firstLabel = Object.values(labels).find((value) => {
      if (value === null || value === undefined) {
        return false;
      }
      return String(value).trim().length > 0;
    });
    if (firstLabel) {
      return String(firstLabel);
    }

    if (state.selectedModel.value?.name) {
      return state.selectedModel.value.name;
    }
    return 'Portail métier';
  }

  function getRepresentativeAttributeKeys(typeName) {
    if (!typeName) {
      return [];
    }
    const refs = state.representativeAttributesByType.value.get(typeName);
    return Array.isArray(refs) ? refs : [];
  }

  function getObjectPrimaryAttributes(object) {
    if (!object) {
      return [];
    }
    const keys = getRepresentativeAttributeKeys(object.type);
    if (!keys.length) {
      return [];
    }
    const attributes = Array.isArray(object.attributes) ? object.attributes : [];
    const map = new Map(attributes.map((attr) => [attr.key, attr.value]));
    return keys
      .map((key) => ({
        key,
        label: getAttributeLabel(object.type, key),
        value: map.get(key)
      }))
      .filter((attr) => attr.value !== undefined && attr.value !== null && String(attr.value).trim() !== '');
  }

  function getObjectPrimaryLabel(object) {
    const primaryAttributes = getObjectPrimaryAttributes(object);
    if (!primaryAttributes.length) {
      return '';
    }
    const values = primaryAttributes
      .map((attr) => String(attr.value).trim())
      .filter(Boolean);
    return values.join(' ');
  }

  function formatObjectOptionLabel(object) {
    if (!object) {
      return '';
    }
    const label = getObjectPrimaryLabel(object);
    const type = object.type || 'Objet';
    const id = object.id ?? 'N/A';
    if (label) {
      return `${label} • ${type} #${id}`;
    }
    return `${type} #${id}`;
  }

  function isTypeOrSubtype(candidate, allowed) {
    if (!candidate || !allowed) {
      return false;
    }
    if (candidate === allowed) {
      return true;
    }
    const visited = new Set();
    let current = state.modelTypeIndex.value.get(candidate);
    while (current && current.parent) {
      const parent = current.parent;
      if (!parent || visited.has(parent)) {
        return false;
      }
      if (parent === allowed) {
        return true;
      }
      visited.add(parent);
      current = state.modelTypeIndex.value.get(parent);
    }
    return false;
  }

  function isTypeAllowed(candidate, allowedTypes) {
    if (!candidate || !Array.isArray(allowedTypes) || !allowedTypes.length) {
      return false;
    }
    return allowedTypes.some((allowed) => isTypeOrSubtype(candidate, allowed));
  }

  Object.assign(state, {
    handleModelFile,
    refreshModels,
    refreshModelDetails,
    refreshHealth,
    uploadModel,
    resetModelState,
    buildModelSummary,
    buildLinkTypeInfo,
    isDirectedLink,
    resolveAttributeLabel,
    formatAttributeLabel,
    getTypeIconName,
    getAttributeLabel,
    getLinkTypeLabel,
    getUserPortalTitle,
    getRepresentativeAttributeKeys,
    getObjectPrimaryAttributes,
    getObjectPrimaryLabel,
    formatObjectOptionLabel,
    isTypeAllowed
  });

  return state;
}
