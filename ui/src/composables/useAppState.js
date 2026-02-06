import { computed, onMounted, ref, watch } from 'vue';

export function useAppState() {
  const apiBase = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

  const currentPage = ref('navigate');
  const displayLanguage = ref('');
  const validateOnly = ref(false);
  const modelSummary = ref(null);
  const modelDetails = ref({ objectTypes: [], linkTypes: [], languages: [], defaultLanguage: '' });
  const selectedModelObject = ref(null);
  const selectedModelLink = ref(null);
  const modelObjectFilter = ref('');
  const modelLinkFilter = ref('');
  const dataSummary = ref(null);
  const dataObjects = ref([]);
  const dataLinks = ref([]);
  const linkTypeInfo = ref({});
  const hasModel = ref(false);
  const selectedObject = ref(null);
  const objectFilter = ref('');
  const status = ref(null);
  const healthStatus = ref({ api: 'unknown', neo4j: 'unknown', error: '' });
  const openGroups = ref([]);
  const openLinkGroups = ref([]);
  const selectedAttributeTab = ref(null);
  const selectedModelGroupTab = ref(null);
  const tableTypeFilter = ref([]);
  const tableSearch = ref('');
  const tableAttributeKey = ref('');
  const tableAttributeKeyOperator = ref('contains');
  const tableAttributeValue = ref('');
  const tableAttributeValueOperator = ref('contains');
  const tableSelectedObject = ref(null);
  const showTableDetailPanel = ref(true);
  const tableSelectedAttributeTab = ref(null);

  const models = ref([]);
  const selectedModelKey = ref('');
  const modelFile = ref(null);
  const dataFile = ref(null);
  const isLoadingModels = ref(false);
  const isLoadingModel = ref(false);
  const isLoadingData = ref(false);

  const adminModelKey = ref('');
  const adminCommandVisible = ref(false);
  const adminStatus = ref(null);

  const modelLanguages = computed(() => {
    const languages = Array.isArray(modelDetails.value.languages)
      ? modelDetails.value.languages
      : [];
    return languages.map((language) => ({
      title: language.label || language.code || '',
      value: language.code || ''
    }));
  });

  const defaultLanguage = computed(() => modelDetails.value.defaultLanguage || '');

  const modelOptions = computed(() =>
    models.value.map((model) => ({
      title: model.version ? `${model.name} v${model.version}` : model.name,
      value: model.key
    }))
  );

  const selectedModel = computed(
    () => models.value.find((model) => model.key === selectedModelKey.value) || null
  );

  const adminModel = computed(
    () => models.value.find((model) => model.key === adminModelKey.value) || null
  );

  const canUploadModel = computed(() => Boolean(getFirstFile(modelFile.value)));
  const canUploadData = computed(
    () => Boolean(getFirstFile(dataFile.value) && selectedModelKey.value)
  );

  const neo4jChipLabel = computed(() => {
    if (healthStatus.value.neo4j === 'ok') {
      return 'OK';
    }
    if (healthStatus.value.neo4j === 'ko') {
      return 'KO';
    }
    return '—';
  });

  const neo4jChipColor = computed(() => {
    if (healthStatus.value.neo4j === 'ok') {
      return 'success';
    }
    if (healthStatus.value.neo4j === 'ko') {
      return 'error';
    }
    return 'secondary';
  });

  const filteredModelObjects = computed(() => {
    const filter = modelObjectFilter.value.trim().toLowerCase();
    if (!filter) {
      return modelDetails.value.objectTypes;
    }
    return modelDetails.value.objectTypes.filter((type) =>
      type.name.toLowerCase().includes(filter)
    );
  });

  const filteredModelLinks = computed(() => {
    const filter = modelLinkFilter.value.trim().toLowerCase();
    if (!filter) {
      return modelDetails.value.linkTypes;
    }
    return modelDetails.value.linkTypes.filter((link) =>
      link.name.toLowerCase().includes(filter)
    );
  });

  const modelGroupTabs = computed(() => {
    if (!selectedModelObject.value) {
      return [];
    }

    const attributes = Array.isArray(selectedModelObject.value.attributes)
      ? selectedModelObject.value.attributes
      : [];
    const attributeMap = new Map(attributes.map((attr) => [attr.name, attr]));
    const groups = Array.isArray(selectedModelObject.value.attributeGroups)
      ? selectedModelObject.value.attributeGroups
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

  const filteredObjects = computed(() => {
    const filter = objectFilter.value.trim().toLowerCase();
    if (!filter) {
      return dataObjects.value;
    }
    return dataObjects.value.filter((object) => {
      const id = object.id !== null && object.id !== undefined ? String(object.id) : '';
      return (
        object.type.toLowerCase().includes(filter)
        || id.toLowerCase().includes(filter)
      );
    });
  });

  const tableTypeOptions = computed(() => {
    const types = new Set();
    dataObjects.value.forEach((object) => {
      if (object.type) {
        types.add(object.type);
      }
    });
    return Array.from(types)
      .sort()
      .map((type) => ({ title: type, value: type }));
  });

  const filteredTableRows = computed(() => {
    const typeFilter = Array.isArray(tableTypeFilter.value)
      ? tableTypeFilter.value.map((type) => String(type).toLowerCase())
      : [];
    const search = tableSearch.value.trim().toLowerCase();
    const attributeKey = tableAttributeKey.value.trim().toLowerCase();
    const attributeKeyOperator = tableAttributeKeyOperator.value;
    const attributeValue = tableAttributeValue.value.trim().toLowerCase();
    const attributeValueOperator = tableAttributeValueOperator.value;

    return dataObjects.value
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
          label: getAttributeLabel(object.type, attr.key),
          value: attr.value
        }));
        return {
          key: object.key,
          id: object.id ?? 'N/A',
          idKey: object.idKey,
          type: object.type,
          primaryLabel: getObjectPrimaryLabel(object),
          attributeCount: attributes.length,
          attributePreview,
          attributes
        };
      });
  });

  const selectedObjectType = computed(() => {
    if (!selectedObject.value) {
      return null;
    }
    return modelDetails.value.objectTypes.find((type) => type.name === selectedObject.value.type) || null;
  });

  const tableSelectedObjectType = computed(() => {
    if (!tableSelectedObject.value) {
      return null;
    }
    return modelDetails.value.objectTypes.find((type) => type.name === tableSelectedObject.value.type) || null;
  });

  const attributeTabs = computed(() => {
    if (!selectedObject.value) {
      return [];
    }

    const typeDef = selectedObjectType.value;
    const objectAttributes = Array.isArray(selectedObject.value.attributes)
      ? selectedObject.value.attributes
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
          label: formatAttributeLabel(ref.name, definition),
          value: valueMap.get(ref.name) ?? '',
          type: definition?.type || '',
          required: Boolean(definition?.required),
          description: definition?.description || ''
        });
        used.add(ref.name);
      });

      if (rows.length) {
        tabs.push({
          key: `group-${groupIndex}-${group.name || 'groupe'}`,
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
        label: formatAttributeLabel(name, definition),
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
        key: 'group-autres',
        label: 'Autres',
        attributes: remaining
      });
    }

    if (!tabs.length && objectAttributes.length) {
      tabs.push({
        key: 'group-attributs',
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
  });

  const tableAttributeTabs = computed(() => {
    if (!tableSelectedObject.value) {
      return [];
    }

    const typeDef = tableSelectedObjectType.value;
    const objectAttributes = Array.isArray(tableSelectedObject.value.attributes)
      ? tableSelectedObject.value.attributes
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
          label: formatAttributeLabel(ref.name, definition),
          value: valueMap.get(ref.name) ?? '',
          type: definition?.type || '',
          required: Boolean(definition?.required),
          description: definition?.description || ''
        });
        used.add(ref.name);
      });

      if (rows.length) {
        tabs.push({
          key: `table-group-${groupIndex}-${group.name || 'groupe'}`,
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
        label: formatAttributeLabel(name, definition),
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
        key: 'table-group-autres',
        label: 'Autres',
        attributes: remaining
      });
    }

    if (!tabs.length && objectAttributes.length) {
      tabs.push({
        key: 'table-group-attributs',
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
  });

  const objectTypeGroups = computed(() => {
    const groups = new Map();
    filteredObjects.value.forEach((object) => {
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

  const adminCommand = computed(() => {
    if (!adminModel.value) {
      return 'java -jar importpackage.jar --delete-model <modelName> [modelVersion]';
    }
    const name = adminModel.value.name;
    const version = adminModel.value.version || '';
    if (version) {
      return `java -jar importpackage.jar --delete-model \"${name}\" \"${version}\"`;
    }
    return `java -jar importpackage.jar --delete-model \"${name}\"`;
  });

  const graphNodes = computed(() => buildGraphNodes());
  const graphEdges = computed(() => buildGraphEdges());

  const tableHeaders = [
    { title: 'Type', key: 'type' },
    { title: 'ID', key: 'id' },
    { title: 'Aperçu', key: 'preview' },
    { title: 'Attributs', key: 'attributes' },
    { title: 'Actions', key: 'actions', sortable: false }
  ];

  const fullTextHeaders = [
    { title: 'Type', key: 'type' },
    { title: 'ID', key: 'id' },
    { title: 'Aperçu', key: 'preview' },
    { title: 'Correspondances', key: 'matches' },
    { title: 'Actions', key: 'actions', sortable: false }
  ];

  const tableOperatorOptions = [
    { title: 'Contient', value: 'contains' },
    { title: 'Egal', value: 'equals' }
  ];

  const tableHasDetails = computed(() => Boolean(tableSelectedObject.value && showTableDetailPanel.value));

  const fullTextQuery = ref('');
  const fullTextTypeFilter = ref([]);

  const representativeAttributesByType = computed(() => {
    const map = new Map();
    modelDetails.value.objectTypes.forEach((type) => {
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

  const searchableAttributesByType = computed(() => {
    const map = new Map();
    modelDetails.value.objectTypes.forEach((type) => {
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

  const fullTextTypeOptions = computed(() => tableTypeOptions.value);

  const fullTextResults = computed(() => {
    const query = fullTextQuery.value.trim().toLowerCase();
    if (!query) {
      return [];
    }

    const typeFilter = Array.isArray(fullTextTypeFilter.value)
      ? fullTextTypeFilter.value.map((type) => String(type).toLowerCase())
      : [];

    return dataObjects.value
      .filter((object) => {
        if (typeFilter.length && !typeFilter.includes(object.type.toLowerCase())) {
          return false;
        }
        const searchable = searchableAttributesByType.value.get(object.type);
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
        const searchable = searchableAttributesByType.value.get(object.type) || new Set();
        const attributes = Array.isArray(object.attributes) ? object.attributes : [];
        const matches = attributes
          .filter((attr) => searchable.has(attr.key))
          .filter((attr) => String(attr.value || '').toLowerCase().includes(query))
          .map((attr) => ({
            key: attr.key,
            label: getAttributeLabel(object.type, attr.key),
            value: attr.value
          }));

        return {
          key: object.key,
          id: object.id ?? 'N/A',
          idKey: object.idKey,
          type: object.type,
          primaryLabel: getObjectPrimaryLabel(object),
          matches
        };
      });
  });

  const objectIndex = computed(() => {
    const map = new Map();
    dataObjects.value.forEach((object) => {
      map.set(object.idKey, object);
    });
    return map;
  });

  const linkedObjects = computed(() => {
    if (!selectedObject.value) {
      return [];
    }

    const relations = [];
    const currentKey = selectedObject.value.idKey;

    dataLinks.value.forEach((link, index) => {
      const directed = isDirectedLink(link.type);
      const outDirection = directed ? 'out' : 'both';
      const inDirection = directed ? 'in' : 'both';

      if (link.fromKey === currentKey) {
        relations.push(buildRelation(link, outDirection, link.toKey, index));
      }
      if (link.toKey === currentKey) {
        relations.push(buildRelation(link, inDirection, link.fromKey, index));
      }
    });

    return relations
      .map((relation) => {
        const target = objectIndex.value.get(relation.targetKey);
        if (!target) {
          return null;
        }
        return {
          ...relation,
          target
        };
      })
      .filter(Boolean);
  });

  const linkedGroups = computed(() => {
    if (!linkedObjects.value.length) {
      return [];
    }
    const map = new Map();
    linkedObjects.value.forEach((relation) => {
      const key = `${relation.type}-${relation.direction}`;
      if (!map.has(key)) {
        map.set(key, {
          key,
          title: `${relation.type} • ${relation.directionLabel}`,
          icon: relation.directionIcon,
          items: []
        });
      }
      map.get(key).items.push(relation);
    });

    return Array.from(map.values()).map((group) => ({
      ...group,
      items: group.items.sort((a, b) => {
        const typeA = a.target.type || '';
        const typeB = b.target.type || '';
        if (typeA !== typeB) {
          return typeA.localeCompare(typeB);
        }
        const idA = String(a.target.id ?? a.target.idKey);
        const idB = String(b.target.id ?? b.target.idKey);
        return idA.localeCompare(idB);
      })
    }));
  });

  const tableSelectedLinks = computed(() => {
    if (!tableSelectedObject.value) {
      return [];
    }

    const relations = [];
    const currentKey = tableSelectedObject.value.idKey;

    dataLinks.value.forEach((link, index) => {
      const directed = isDirectedLink(link.type);
      const outDirection = directed ? 'out' : 'both';
      const inDirection = directed ? 'in' : 'both';

      if (link.fromKey === currentKey) {
        relations.push(buildRelation(link, outDirection, link.toKey, index));
      }
      if (link.toKey === currentKey) {
        relations.push(buildRelation(link, inDirection, link.fromKey, index));
      }
    });

    return relations
      .map((relation) => {
        const target = objectIndex.value.get(relation.targetKey);
        if (!target) {
          return null;
        }
        return {
          ...relation,
          target
        };
      })
      .filter(Boolean);
  });

  onMounted(() => {
    refreshHealth();
    refreshModels();
  });

  watch(
    () => selectedModelKey.value,
    async (key) => {
      if (!key) {
        resetModelState();
        resetDataState();
        return;
      }
      await refreshModelDetails(key);
      await refreshData(key);
    }
  );

  watch(
    () => models.value,
    (list) => {
      if (!list.length) {
        adminModelKey.value = '';
        return;
      }
      const exists = list.some((model) => model.key === adminModelKey.value);
      if (!exists) {
        adminModelKey.value = list[0].key;
      }
    }
  );

  watch(
    () => selectedObject.value,
    (object) => {
      if (!object) {
        return;
      }
      const type = object.type || 'Objet';
      if (!openGroups.value.includes(type)) {
        openGroups.value = [...openGroups.value, type];
      }
    }
  );

  watch(
    () => linkedGroups.value,
    (groups) => {
      openLinkGroups.value = groups.map((group) => group.key);
    },
    { immediate: true }
  );

  watch(
    () => attributeTabs.value,
    (tabs) => {
      selectedAttributeTab.value = tabs[0]?.key || null;
    },
    { immediate: true }
  );

  watch(
    () => modelGroupTabs.value,
    (tabs) => {
      selectedModelGroupTab.value = tabs[0]?.key || null;
    },
    { immediate: true }
  );

  watch(
    () => tableAttributeTabs.value,
    (tabs) => {
      tableSelectedAttributeTab.value = tabs[0]?.key || null;
    },
    { immediate: true }
  );

  function handleModelFile(files) {
    modelFile.value = Array.isArray(files) ? files[0] : files;
  }

  function handleDataFile(files) {
    dataFile.value = Array.isArray(files) ? files[0] : files;
  }

  async function refreshModels(preferredKey) {
    isLoadingModels.value = true;
    status.value = null;
    refreshHealth();
    try {
      const response = await fetch(`${apiBase}/api/models`);
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors du chargement des modèles');
      }
      const list = Array.isArray(payload) ? payload : [];
      models.value = list;

      const currentKey = selectedModelKey.value;
      let nextKey = preferredKey || currentKey;
      if (!nextKey || !list.find((model) => model.key === nextKey)) {
        nextKey = list[0]?.key || '';
      }
      selectedModelKey.value = nextKey;

      if (nextKey && nextKey === currentKey) {
        await refreshModelDetails(nextKey);
        await refreshData(nextKey);
      }

      if (!nextKey) {
        resetModelState();
        resetDataState();
      }
    } catch (error) {
      status.value = {
        type: 'error',
        message: error.message
      };
    } finally {
      isLoadingModels.value = false;
    }
  }

  async function refreshModelDetails(modelKey) {
    isLoadingModel.value = true;
    try {
      const response = await fetch(`${apiBase}/api/models/${encodeURIComponent(modelKey)}`);
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors du chargement du modèle');
      }
      const objectTypes = payload?.objectTypes || [];
      const linkTypes = payload?.linkTypes || [];
      const languages = Array.isArray(payload?.languages) ? payload.languages : [];
      const defaultLanguageValue = payload?.defaultLanguage || '';
      modelDetails.value = { objectTypes, linkTypes, languages, defaultLanguage: defaultLanguageValue };
      const availableCodes = languages.map((language) => language.code).filter(Boolean);
      const fallbackLanguage = defaultLanguageValue || availableCodes[0] || '';
      if (!displayLanguage.value || (availableCodes.length && !availableCodes.includes(displayLanguage.value))) {
        displayLanguage.value = fallbackLanguage;
      }
      modelSummary.value = buildModelSummary(payload);
      selectedModelObject.value = objectTypes[0] ?? null;
      selectedModelLink.value = linkTypes[0] ?? null;
      linkTypeInfo.value = buildLinkTypeInfo(linkTypes);
      hasModel.value = objectTypes.length > 0 || linkTypes.length > 0;
      modelObjectFilter.value = '';
      modelLinkFilter.value = '';
    } catch (error) {
      resetModelState();
      hasModel.value = false;
      status.value = { type: 'error', message: error.message };
    } finally {
      isLoadingModel.value = false;
    }
  }

  async function refreshHealth() {
    try {
      const response = await fetch(`${apiBase}/api/health?deep=true`);
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'API indisponible');
      }
      healthStatus.value = {
        api: payload?.api || 'ok',
        neo4j: payload?.neo4j || 'unknown',
        error: payload?.neo4jError || ''
      };
    } catch (error) {
      healthStatus.value = { api: 'ko', neo4j: 'ko', error: error.message };
    }
  }

  async function refreshData(modelKey) {
    isLoadingData.value = true;
    try {
      const response = await fetch(
        `${apiBase}/api/data?modelKey=${encodeURIComponent(modelKey)}`
      );
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors du chargement des données');
      }

      const objects = normalizeObjects(payload?.objects || []);
      const links = normalizeLinks(payload?.links || []);
      dataObjects.value = objects;
      dataLinks.value = links;
      dataSummary.value = buildDataSummary(payload, objects, links);
      selectedObject.value = objects[0] ?? null;
      objectFilter.value = '';
    } catch (error) {
      resetDataState();
      status.value = { type: 'error', message: error.message };
    } finally {
      isLoadingData.value = false;
    }
  }

  async function uploadModel() {
    const file = getFirstFile(modelFile.value);
    if (!file) {
      status.value = { type: 'warning', message: 'Sélectionnez un fichier modèle.' };
      return;
    }
    status.value = null;
    isLoadingModel.value = true;
    try {
      const formData = new FormData();
      formData.append('modelFile', file);
      const response = await fetch(`${apiBase}/api/models`, {
        method: 'POST',
        body: formData
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors de l’import du modèle');
      }
      status.value = {
        type: 'success',
        message: `Modèle chargé en base (${payload?.name || 'OK'}).`
      };
      await refreshModels(payload?.key);
    } catch (error) {
      status.value = { type: 'error', message: error.message };
    } finally {
      isLoadingModel.value = false;
    }
  }

  async function uploadData() {
    const file = getFirstFile(dataFile.value);
    if (!file) {
      status.value = { type: 'warning', message: 'Sélectionnez un fichier de données.' };
      return;
    }
    if (!selectedModelKey.value) {
      status.value = { type: 'warning', message: 'Sélectionnez un modèle en base.' };
      return;
    }
    status.value = null;
    isLoadingData.value = true;
    try {
      const formData = new FormData();
      formData.append('dataFile', file);
      formData.append('modelKey', selectedModelKey.value);
      formData.append('validateOnly', String(validateOnly.value));
      const response = await fetch(`${apiBase}/api/data`, {
        method: 'POST',
        body: formData
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors de l’import des données');
      }

      if (payload?.valid) {
        const actionLabel = payload.validateOnly ? 'Validation effectuée' : 'Données importées';
        status.value = {
          type: 'success',
          message: `${actionLabel}. ${payload.objectCount || 0} objets, ${payload.linkCount || 0} liens.`
        };
      } else {
        status.value = {
          type: 'warning',
          message: `Validation échouée. ${payload?.errors?.length || 0} erreurs détectées.`
        };
      }

      if (payload?.valid && !payload?.validateOnly) {
        await refreshData(selectedModelKey.value);
      }
    } catch (error) {
      status.value = { type: 'error', message: error.message };
    } finally {
      isLoadingData.value = false;
    }
  }

  async function deleteModel() {
    if (!adminModelKey.value) {
      adminStatus.value = { type: 'warning', message: 'Sélectionnez un modèle.' };
      return;
    }
    adminStatus.value = null;
    try {
      const response = await fetch(
        `${apiBase}/api/models/${encodeURIComponent(adminModelKey.value)}`,
        { method: 'DELETE' }
      );
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors de la suppression du modèle');
      }
      adminStatus.value = {
        type: 'success',
        message: 'Modèle et données associés supprimés.'
      };
      await refreshModels();
    } catch (error) {
      adminStatus.value = { type: 'error', message: error.message };
    }
  }

  function resetModelState() {
    modelSummary.value = null;
    modelDetails.value = { objectTypes: [], linkTypes: [], languages: [], defaultLanguage: '' };
    selectedModelObject.value = null;
    selectedModelLink.value = null;
    linkTypeInfo.value = {};
    hasModel.value = false;
    displayLanguage.value = '';
  }

  function resetDataState() {
    dataSummary.value = null;
    dataObjects.value = [];
    dataLinks.value = [];
    selectedObject.value = null;
  }

  function selectObject(object) {
    selectedObject.value = object;
  }

  function setCurrentPage(page) {
    currentPage.value = page;
  }

  function selectObjectByKey(key) {
    const target = objectIndex.value.get(key);
    if (target) {
      selectedObject.value = target;
    }
  }

  function viewObjectFromTable(key) {
    const target = objectIndex.value.get(key);
    if (target) {
      tableSelectedObject.value = target;
      showTableDetailPanel.value = true;
    }
  }

  function openInExplorerFromTable() {
    if (!tableSelectedObject.value) {
      return;
    }
    selectObjectByKey(tableSelectedObject.value.idKey);
    currentPage.value = 'navigate';
  }

  function selectModelObjectByName(name) {
    if (!name) {
      return;
    }
    const match = modelDetails.value.objectTypes.find((type) => type.name === name);
    if (match) {
      selectedModelObject.value = match;
      modelObjectFilter.value = name;
    }
  }

  function selectModelLinkByName(name) {
    if (!name) {
      return;
    }
    const match = modelDetails.value.linkTypes.find((link) => link.name === name);
    if (match) {
      selectedModelLink.value = match;
      modelLinkFilter.value = name;
    }
  }

  async function copyAdminCommand() {
    if (!adminModel.value) {
      return;
    }
    try {
      await navigator.clipboard.writeText(adminCommand.value);
      adminStatus.value = { type: 'success', message: 'Commande copiée dans le presse-papiers.' };
    } catch (error) {
      adminStatus.value = { type: 'error', message: 'Impossible de copier la commande.' };
    }
  }

  function buildRelation(link, direction, targetKey, index) {
    return {
      key: `${link.type}-${index}-${direction}`,
      type: link.type,
      targetKey,
      direction,
      directionLabel: directionToLabel(direction),
      directionIcon: directionToIcon(direction)
    };
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

  function buildGraphNodes() {
    const nodes = modelDetails.value.objectTypes;
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
    if (!graphNodes.value.length) {
      return [];
    }
    const nodeMap = new Map(graphNodes.value.map((node) => [node.name, node]));
    const edges = [];

    modelDetails.value.linkTypes.forEach((link, linkIndex) => {
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
        parent: type.parent
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
    const known = linkTypeInfo.value[linkType];
    if (known !== undefined) {
      return known;
    }
    if (!hasModel.value) {
      return false;
    }
    return true;
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

  function getFirstFile(value) {
    if (!value) {
      return null;
    }
    if (Array.isArray(value)) {
      return value[0] || null;
    }
    return value;
  }

  function resolveAttributeLabel(definition, language, fallbackLanguage) {
    if (!definition || !definition.labels) {
      return '';
    }
    const labels = definition.labels;
    const requested = language || displayLanguage.value;
    const fallback = fallbackLanguage || defaultLanguage.value;
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

  function getAttributeDefinition(typeName, attributeName) {
    if (!typeName || !attributeName) {
      return null;
    }
    const typeDef = modelDetails.value.objectTypes.find((type) => type.name === typeName);
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

  function getRepresentativeAttributeKeys(typeName) {
    if (!typeName) {
      return [];
    }
    const refs = representativeAttributesByType.value.get(typeName);
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

  async function readJson(response) {
    try {
      return await response.json();
    } catch (error) {
      return null;
    }
  }

  return {
    apiBase,
    currentPage,
    displayLanguage,
    validateOnly,
    modelSummary,
    modelDetails,
    selectedModelObject,
    selectedModelLink,
    modelObjectFilter,
    modelLinkFilter,
    dataSummary,
    dataObjects,
    dataLinks,
    linkTypeInfo,
    hasModel,
    selectedObject,
    objectFilter,
    status,
    healthStatus,
    openGroups,
    openLinkGroups,
    selectedAttributeTab,
    selectedModelGroupTab,
    tableTypeFilter,
    tableSearch,
    tableAttributeKey,
    tableAttributeKeyOperator,
    tableAttributeValue,
    tableAttributeValueOperator,
    tableSelectedObject,
    showTableDetailPanel,
    tableSelectedAttributeTab,
    models,
    selectedModelKey,
    modelFile,
    dataFile,
    isLoadingModels,
    isLoadingModel,
    isLoadingData,
    adminModelKey,
    adminCommandVisible,
    adminStatus,
    modelLanguages,
    defaultLanguage,
    modelOptions,
    selectedModel,
    adminModel,
    canUploadModel,
    canUploadData,
    neo4jChipLabel,
    neo4jChipColor,
    filteredModelObjects,
    filteredModelLinks,
    modelGroupTabs,
    filteredObjects,
    tableTypeOptions,
    filteredTableRows,
    selectedObjectType,
    tableSelectedObjectType,
    attributeTabs,
    tableAttributeTabs,
    objectTypeGroups,
    adminCommand,
    graphNodes,
    graphEdges,
    tableHeaders,
    fullTextHeaders,
    tableOperatorOptions,
    tableHasDetails,
    fullTextQuery,
    fullTextTypeFilter,
    searchableAttributesByType,
    fullTextTypeOptions,
    fullTextResults,
    objectIndex,
    linkedObjects,
    linkedGroups,
    tableSelectedLinks,
    formatAttributeLabel,
    getAttributeLabel,
    getRepresentativeAttributeKeys,
    getObjectPrimaryAttributes,
    getObjectPrimaryLabel,
    handleModelFile,
    handleDataFile,
    refreshModels,
    refreshModelDetails,
    refreshHealth,
    refreshData,
    uploadModel,
    uploadData,
    deleteModel,
    selectObject,
    selectObjectByKey,
    viewObjectFromTable,
    openInExplorerFromTable,
    setCurrentPage,
    selectModelObjectByName,
    selectModelLinkByName,
    copyAdminCommand
  };
}
