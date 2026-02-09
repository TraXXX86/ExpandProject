import { computed, onMounted, ref, watch } from 'vue';

export function useAppState() {
  const apiBase = import.meta.env.VITE_API_BASE || 'http://localhost:8080';
  const storedToken = typeof window !== 'undefined'
    ? window.localStorage.getItem('expand.authToken')
    : '';

  const currentPage = ref('navigate');
  const activePortal = ref('');
  const userPortalPages = ['navigate', 'table', 'search', 'create', 'import-data'];
  const modelAdminPortalPages = ['import-model', 'model', 'admin'];
  const displayLanguage = ref('');
  const validateOnly = ref(false);
  const modelSummary = ref(null);
  const modelDetails = ref({
    objectTypes: [],
    linkTypes: [],
    languages: [],
    defaultLanguage: '',
    userPortalLabels: {}
  });
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
  const rootObjectQuery = ref('');
  const selectedRootObjectKey = ref('');
  const treeLinkSelections = ref({});
  const treeExpandedNodes = ref({});
  const showCycleDetection = ref(true);
  const explorerMaxDepth = 12;
  const status = ref(null);
  const healthStatus = ref({ api: 'unknown', neo4j: 'unknown', error: '' });
  const openGroups = ref([]);
  const openLinkGroups = ref([]);
  const selectedAttributeTab = ref(null);
  const selectedLinkedRelationTab = ref(null);
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
  const createObjectType = ref('');
  const createObjectAttributes = ref({});
  const createObjectStatus = ref(null);
  const isCreatingObject = ref(false);
  const createLinkType = ref('');
  const createLinkSourceId = ref(null);
  const createLinkTargetId = ref(null);
  const createLinkStatus = ref(null);
  const isCreatingLink = ref(false);
  const modelXml = ref('');
  const modelXmlStatus = ref(null);
  const isLoadingModelXml = ref(false);
  const isSavingModelXml = ref(false);

  const models = ref([]);
  const selectedModelKey = ref('');
  const modelFile = ref(null);
  const dataFile = ref(null);
  const isLoadingModels = ref(false);
  const isLoadingModel = ref(false);
  const isLoadingData = ref(false);

  const authToken = ref(storedToken || '');
  const isAuthenticated = ref(Boolean(authToken.value));
  const isAuthenticating = ref(false);
  const authStatus = ref(null);
  const loginUsername = ref('admin');
  const loginPassword = ref('admin');
  const authMeta = ref({
    actorUsername: '',
    actorDisplayName: '',
    effectiveUsername: '',
    effectiveDisplayName: '',
    impersonating: false,
    actorPlatformAdmin: false,
    expiresAt: 0
  });

  const users = ref([]);
  const accessProfile = ref({
    username: '',
    displayName: '',
    portalUser: false,
    portalModelAdmin: false,
    platformAdmin: false
  });
  const accessPermissions = ref([]);
  const accessStatus = ref(null);
  const isLoadingUsers = ref(false);

  const adminAccessUserKey = ref('');
  const adminAccessStatus = ref(null);
  const isSavingAccessUser = ref(false);
  const isSavingAccessPermissions = ref(false);
  const isDeletingAccessUser = ref(false);
  const newAccessUsername = ref('');
  const newAccessDisplayName = ref('');
  const adminAccessForm = ref({
    username: '',
    displayName: '',
    password: '',
    portalUser: true,
    portalModelAdmin: false,
    platformAdmin: false
  });
  const adminAccessPermissions = ref([]);

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

  const userOptions = computed(() =>
    users.value.map((user) => ({
      title: user.displayName ? `${user.displayName} (${user.username})` : user.username,
      value: user.username
    }))
  );

  const isPlatformAdmin = computed(() => Boolean(accessProfile.value.platformAdmin));
  const canAccessUserPortal = computed(() => Boolean(accessProfile.value.portalUser || isPlatformAdmin.value));
  const canAccessModelAdminPortal = computed(
    () => Boolean(accessProfile.value.portalModelAdmin || isPlatformAdmin.value)
  );

  const selectedModelPermission = computed(() => getModelPermission(selectedModelKey.value));
  const canReadCurrentModelData = computed(() => canReadModelData(selectedModelKey.value));
  const canCreateCurrentModelData = computed(() => canCreateModelData(selectedModelKey.value));
  const canUpdateCurrentModelData = computed(() => canUpdateModelData(selectedModelKey.value));
  const canDeleteCurrentModelData = computed(() => canDeleteModelData(selectedModelKey.value));
  const canManageAccess = computed(() => Boolean(authMeta.value.actorPlatformAdmin));

  const createObjectTypeOptions = computed(() =>
    modelDetails.value.objectTypes
      .map((type) => ({ title: type.name, value: type.name }))
      .sort((a, b) => a.title.localeCompare(b.title))
  );

  const createLinkTypeOptions = computed(() =>
    modelDetails.value.linkTypes
      .map((link) => {
        const label = getLinkTypeLabel(link.name);
        return {
          title: label === link.name ? link.name : `${label} (${link.name})`,
          value: link.name
        };
      })
      .sort((a, b) => a.title.localeCompare(b.title))
  );

  const selectedModel = computed(
    () => models.value.find((model) => model.key === selectedModelKey.value) || null
  );

  const adminModel = computed(
    () => models.value.find((model) => model.key === adminModelKey.value) || null
  );

  const isPortalSelected = computed(() => Boolean(activePortal.value));
  const isUserPortal = computed(() => activePortal.value === 'user');
  const isModelAdminPortal = computed(() => activePortal.value === 'model-admin');

  const portalLabel = computed(() => {
    if (isUserPortal.value) {
      return 'Portail métier';
    }
    if (isModelAdminPortal.value) {
      return 'Portail administration du modèle';
    }
    return 'Connexion';
  });

  const canUploadModel = computed(
    () => Boolean(getFirstFile(modelFile.value) && canAccessModelAdminPortal.value)
  );
  const canUploadData = computed(
    () => Boolean(getFirstFile(dataFile.value) && selectedModelKey.value && canCreateCurrentModelData.value)
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
    return modelDetails.value.linkTypes.filter((link) => {
      const code = String(link.name || '').toLowerCase();
      const label = String(getLinkTypeLabel(link.name) || '').toLowerCase();
      return code.includes(filter) || label.includes(filter);
    });
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

  const rootObjectOptions = computed(() => {
    const query = rootObjectQuery.value.trim().toLowerCase();
    return dataObjects.value
      .filter((object) => {
        if (!query) {
          return true;
        }
        const id = object.id !== null && object.id !== undefined ? String(object.id) : '';
        const label = getObjectPrimaryLabel(object);
        const primaryValues = getObjectPrimaryAttributes(object)
          .map((attribute) => String(attribute.value || '').toLowerCase())
          .join(' ');
        const haystack = `${object.type} ${id} ${label} ${primaryValues}`.toLowerCase();
        return haystack.includes(query);
      })
      .slice(0, 120)
      .map((object) => ({
        title: formatObjectOptionLabel(object),
        value: object.idKey,
        subtitle: `${object.type} • ID ${object.id ?? 'N/A'}`
      }));
  });

  const selectedRootObject = computed(() => {
    if (!selectedRootObjectKey.value) {
      return null;
    }
    return objectIndex.value.get(selectedRootObjectKey.value) || null;
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

  const modelTypeIndex = computed(() => {
    const index = new Map();
    modelDetails.value.objectTypes.forEach((type) => {
      if (type.name) {
        index.set(type.name, type);
      }
    });
    return index;
  });

  const createObjectAttributeDefs = computed(() => {
    if (!createObjectType.value) {
      return [];
    }
    const chain = [];
    const visited = new Set();
    let current = modelTypeIndex.value.get(createObjectType.value);
    while (current && current.name && !visited.has(current.name)) {
      visited.add(current.name);
      chain.push(current);
      const parentName = current.parent;
      if (!parentName) {
        break;
      }
      current = modelTypeIndex.value.get(parentName);
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

  const createLinkDefinition = computed(
    () => modelDetails.value.linkTypes.find((link) => link.name === createLinkType.value) || null
  );

  const createLinkSourceOptions = computed(() => {
    if (!createLinkDefinition.value || !dataObjects.value.length) {
      return [];
    }
    const allowed = Array.isArray(createLinkDefinition.value.sources)
      ? createLinkDefinition.value.sources
      : [];
    if (!allowed.length) {
      return [];
    }
    return dataObjects.value
      .filter((object) => isTypeAllowed(object.type, allowed))
      .map((object) => ({
        title: formatObjectOptionLabel(object),
        value: object.id
      }))
      .sort((a, b) => String(a.title).localeCompare(String(b.title)));
  });

  const createLinkTargetOptions = computed(() => {
    if (!createLinkDefinition.value || !dataObjects.value.length) {
      return [];
    }
    const allowed = Array.isArray(createLinkDefinition.value.targets)
      ? createLinkDefinition.value.targets
      : [];
    if (!allowed.length) {
      return [];
    }
    return dataObjects.value
      .filter((object) => isTypeAllowed(object.type, allowed))
      .map((object) => ({
        title: formatObjectOptionLabel(object),
        value: object.id
      }))
      .sort((a, b) => String(a.title).localeCompare(String(b.title)));
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

  const relationsByObjectKey = computed(() => {
    const map = new Map();

    const appendRelation = (sourceKey, targetKey, link, direction) => {
      if (!sourceKey || !targetKey) {
        return;
      }
      const source = objectIndex.value.get(sourceKey);
      const target = objectIndex.value.get(targetKey);
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
        targetLabel: formatObjectOptionLabel(target)
      });
      map.set(sourceKey, list);
    };

    dataLinks.value.forEach((link) => {
      const directed = isDirectedLink(link.type);
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

  const explorerTree = computed(() => {
    const rootKey = selectedRootObjectKey.value;
    const root = rootKey ? objectIndex.value.get(rootKey) : null;
    if (!rootKey || !root) {
      return null;
    }
    return buildExplorerNode(rootKey, rootKey, 0, new Set([rootKey]));
  });

  const linkedObjects = computed(() => {
    if (!selectedObject.value) {
      return [];
    }
    return relationsByObjectKey.value.get(selectedObject.value.idKey) || [];
  });

  const linkedRelationTabs = computed(() => {
    if (!linkedObjects.value.length) {
      return [];
    }

    const map = new Map();
    linkedObjects.value.forEach((relation) => {
      const type = relation.type || 'Lien';
      if (!map.has(type)) {
        map.set(type, {
          key: `link-type-${type}`,
          type,
          label: getLinkTypeLabel(type),
          items: []
        });
      }
      map.get(type).items.push(relation);
    });

    return Array.from(map.values())
      .sort((a, b) => {
        const byLabel = String(a.label || '').localeCompare(String(b.label || ''));
        if (byLabel !== 0) {
          return byLabel;
        }
        return String(a.type || '').localeCompare(String(b.type || ''));
      })
      .map((tab) => ({
        ...tab,
        count: tab.items.length,
        items: tab.items.sort((a, b) => {
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
        })
      }));
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
    return relationsByObjectKey.value.get(tableSelectedObject.value.idKey) || [];
  });

  onMounted(async () => {
    const authenticated = await refreshSession();
    if (!authenticated) {
      return;
    }
    await refreshUsers();
    await refreshCurrentAccess();
    await refreshHealth();
    await refreshModels();
    ensurePortalAccess();
  });

  watch(
    () => activePortal.value,
    (portal) => {
      if (!portal) {
        return;
      }
      const pages = getPortalPages(portal);
      if (!pages.includes(currentPage.value)) {
        currentPage.value = getPortalDefaultPage(portal);
      }
    }
  );

  watch(
    () => selectedModelKey.value,
    async (key, previousKey) => {
      if (!key) {
        resetModelState();
        resetDataState();
        resetCreateState();
        resetModelEditorState();
        return;
      }
      await refreshModelDetails(key);
      await refreshData(key);
      if (key !== previousKey) {
        resetCreateState();
        resetModelEditorState();
      }
    }
  );

  watch(
    () => models.value,
    (list) => {
      if (!list.length) {
        adminModelKey.value = '';
        adminAccessPermissions.value = [];
        return;
      }
      const exists = list.some((model) => model.key === adminModelKey.value);
      if (!exists) {
        adminModelKey.value = list[0].key;
      }
      syncAdminPermissionRows();
    }
  );

  watch(
    () => users.value,
    (list) => {
      if (!list.length) {
        adminAccessUserKey.value = '';
        return;
      }
      if (!list.find((user) => user.username === adminAccessUserKey.value)) {
        adminAccessUserKey.value = list[0].username;
      }
    },
    { immediate: true }
  );

  watch(
    () => adminAccessUserKey.value,
    async (username) => {
      if (!username) {
        adminAccessForm.value = {
          username: '',
          displayName: '',
          password: '',
          portalUser: true,
          portalModelAdmin: false,
          platformAdmin: false
        };
        adminAccessPermissions.value = [];
        return;
      }
      await loadAdminAccessUser(username);
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
    () => linkedRelationTabs.value,
    (tabs) => {
      selectedLinkedRelationTab.value = tabs[0]?.key || null;
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

  watch(
    () => createObjectAttributeDefs.value,
    (defs) => {
      const next = {};
      defs.forEach((def) => {
        if (!def || !def.name) {
          return;
        }
        if (Object.prototype.hasOwnProperty.call(createObjectAttributes.value, def.name)) {
          next[def.name] = createObjectAttributes.value[def.name];
        } else if (def.defaultValue) {
          next[def.name] = def.defaultValue;
        } else {
          next[def.name] = '';
        }
      });
      createObjectAttributes.value = next;
    },
    { immediate: true }
  );

  watch(
    () => createObjectType.value,
    () => {
      createObjectStatus.value = null;
    }
  );

  watch(
    () => createLinkType.value,
    () => {
      createLinkSourceId.value = null;
      createLinkTargetId.value = null;
      createLinkStatus.value = null;
    }
  );

  function handleModelFile(files) {
    modelFile.value = Array.isArray(files) ? files[0] : files;
  }

  function handleDataFile(files) {
    dataFile.value = Array.isArray(files) ? files[0] : files;
  }

  function apiFetch(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (authToken.value) {
      headers.set('Authorization', `Bearer ${authToken.value}`);
    }
    return fetch(`${apiBase}${path}`, {
      ...options,
      headers
    });
  }

  function resetAuthState(message = '') {
    authToken.value = '';
    isAuthenticated.value = false;
    authMeta.value = {
      actorUsername: '',
      actorDisplayName: '',
      effectiveUsername: '',
      effectiveDisplayName: '',
      impersonating: false,
      actorPlatformAdmin: false,
      expiresAt: 0
    };
    accessProfile.value = {
      username: '',
      displayName: '',
      portalUser: false,
      portalModelAdmin: false,
      platformAdmin: false
    };
    accessPermissions.value = [];
    users.value = [];
    adminAccessUserKey.value = '';
    activePortal.value = '';
    resetModelState();
    resetDataState();
    resetCreateState();
    resetModelEditorState();
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem('expand.authToken');
    }
    if (message) {
      authStatus.value = { type: 'warning', message };
    }
  }

  async function login() {
    const username = String(loginUsername.value || '').trim();
    const password = String(loginPassword.value || '');
    if (!username || !password) {
      authStatus.value = { type: 'warning', message: 'Renseignez votre login et mot de passe.' };
      return false;
    }

    isAuthenticating.value = true;
    authStatus.value = null;
    try {
      const response = await fetch(`${apiBase}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Connexion refusée');
      }

      authToken.value = payload?.token || '';
      isAuthenticated.value = Boolean(authToken.value);
      if (typeof window !== 'undefined' && authToken.value) {
        window.localStorage.setItem('expand.authToken', authToken.value);
      }

      await applyAuthPayload(payload);
      await refreshUsers();
      await refreshHealth();
      await refreshModels();
      ensurePortalAccess();

      authStatus.value = { type: 'success', message: 'Connexion réussie.' };
      return true;
    } catch (error) {
      resetAuthState();
      authStatus.value = { type: 'error', message: error.message };
      return false;
    } finally {
      isAuthenticating.value = false;
    }
  }

  async function logout() {
    try {
      await apiFetch('/api/auth/logout', { method: 'POST' });
    } catch (error) {
      // ignore
    }
    resetAuthState();
    authStatus.value = { type: 'info', message: 'Déconnecté.' };
  }

  async function refreshSession() {
    if (!authToken.value) {
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
      isAuthenticated.value = true;
      await applyAuthPayload(payload);
      return true;
    } catch (error) {
      resetAuthState();
      return false;
    }
  }

  async function applyAuthPayload(payload) {
    authMeta.value = {
      actorUsername: payload?.auth?.actorUsername || '',
      actorDisplayName: payload?.auth?.actorDisplayName || '',
      effectiveUsername: payload?.auth?.effectiveUsername || '',
      effectiveDisplayName: payload?.auth?.effectiveDisplayName || '',
      impersonating: Boolean(payload?.auth?.impersonating),
      actorPlatformAdmin: Boolean(payload?.auth?.actorPlatformAdmin),
      expiresAt: Number(payload?.auth?.expiresAt || 0)
    };
    accessProfile.value = {
      username: payload?.user?.username || '',
      displayName: payload?.user?.displayName || '',
      portalUser: Boolean(payload?.user?.portalUser),
      portalModelAdmin: Boolean(payload?.user?.portalModelAdmin),
      platformAdmin: Boolean(payload?.user?.platformAdmin)
    };
    accessPermissions.value = Array.isArray(payload?.permissions) ? payload.permissions : [];
  }

  async function refreshUsers() {
    if (!isAuthenticated.value) {
      users.value = [];
      return;
    }
    isLoadingUsers.value = true;
    accessStatus.value = null;
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
      users.value = Array.isArray(payload) ? payload : [];
      if (!users.value.length) {
        accessStatus.value = { type: 'warning', message: "Aucun utilisateur n'est configuré." };
      }
    } catch (error) {
      users.value = [];
      accessStatus.value = { type: 'error', message: error.message };
    } finally {
      isLoadingUsers.value = false;
    }
  }

  async function refreshCurrentAccess() {
    if (!isAuthenticated.value) {
      accessProfile.value = {
        username: '',
        displayName: '',
        portalUser: false,
        portalModelAdmin: false,
        platformAdmin: false
      };
      accessPermissions.value = [];
      return;
    }
    accessStatus.value = null;
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
      authMeta.value = {
        actorUsername: payload?.auth?.actorUsername || authMeta.value.actorUsername,
        actorDisplayName: payload?.auth?.actorDisplayName || authMeta.value.actorDisplayName,
        effectiveUsername: payload?.auth?.effectiveUsername || payload?.user?.username || '',
        effectiveDisplayName: payload?.auth?.effectiveDisplayName || payload?.user?.displayName || '',
        impersonating: Boolean(payload?.auth?.impersonating),
        actorPlatformAdmin: Boolean(payload?.auth?.actorPlatformAdmin ?? authMeta.value.actorPlatformAdmin),
        expiresAt: Number(payload?.auth?.expiresAt || authMeta.value.expiresAt || 0)
      };
      accessProfile.value = {
        username: payload?.user?.username || '',
        displayName: payload?.user?.displayName || '',
        portalUser: Boolean(payload?.user?.portalUser),
        portalModelAdmin: Boolean(payload?.user?.portalModelAdmin),
        platformAdmin: Boolean(payload?.user?.platformAdmin)
      };
      accessPermissions.value = Array.isArray(payload?.permissions) ? payload.permissions : [];
    } catch (error) {
      accessProfile.value = {
        username: '',
        displayName: '',
        portalUser: false,
        portalModelAdmin: false,
        platformAdmin: false
      };
      accessPermissions.value = [];
      accessStatus.value = { type: 'error', message: error.message };
    }
  }

  async function impersonateUser(username) {
    if (!canManageAccess.value) {
      adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    if (!username) {
      adminAccessStatus.value = { type: 'warning', message: 'Sélectionnez un utilisateur.' };
      return;
    }

    adminAccessStatus.value = null;
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
      await refreshModels();
      ensurePortalAccess();
      adminAccessStatus.value = { type: 'success', message: `Impersonation de ${username} activée.` };
    } catch (error) {
      adminAccessStatus.value = { type: 'error', message: error.message };
    }
  }

  async function stopImpersonation() {
    if (!canManageAccess.value) {
      adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    adminAccessStatus.value = null;
    try {
      const response = await apiFetch('/api/auth/impersonate/stop', { method: 'POST' });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Impossible d'arrêter l'impersonation");
      }
      await applyAuthPayload(payload);
      await refreshModels();
      ensurePortalAccess();
      adminAccessStatus.value = { type: 'success', message: 'Impersonation arrêtée.' };
    } catch (error) {
      adminAccessStatus.value = { type: 'error', message: error.message };
    }
  }

  function syncAdminPermissionRows() {
    const byModelKey = new Map();
    adminAccessPermissions.value.forEach((permission) => {
      if (permission?.modelKey) {
        byModelKey.set(permission.modelKey, permission);
      }
    });

    adminAccessPermissions.value = models.value.map((model) => {
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

  async function loadAdminAccessUser(username) {
    if (!username) {
      return;
    }
    adminAccessStatus.value = null;
    try {
      const response = await apiFetch(`/api/access/users/${encodeURIComponent(username)}/access`);
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Impossible de charger les droits de l'utilisateur.");
      }

      adminAccessForm.value = {
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

      adminAccessPermissions.value = models.value.map((model) => {
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
      adminAccessStatus.value = { type: 'error', message: error.message };
    }
  }

  async function createAccessUser() {
    if (!canManageAccess.value) {
      adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    const username = String(newAccessUsername.value || '').trim();
    if (!username) {
      adminAccessStatus.value = { type: 'warning', message: 'Renseignez un identifiant utilisateur.' };
      return;
    }
    if (!/^[A-Za-z0-9._-]+$/.test(username)) {
      adminAccessStatus.value = {
        type: 'warning',
        message: "L'identifiant doit contenir uniquement lettres/chiffres et . _ -"
      };
      return;
    }

    isSavingAccessUser.value = true;
    adminAccessStatus.value = null;
    try {
      const response = await apiFetch('/api/access/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username,
          displayName: String(newAccessDisplayName.value || '').trim(),
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

      newAccessUsername.value = '';
      newAccessDisplayName.value = '';
      await refreshUsers();
      adminAccessUserKey.value = payload?.user?.username || username;
      adminAccessStatus.value = { type: 'success', message: 'Utilisateur créé.' };
    } catch (error) {
      adminAccessStatus.value = { type: 'error', message: error.message };
    } finally {
      isSavingAccessUser.value = false;
    }
  }

  async function saveAccessUser() {
    if (!canManageAccess.value) {
      adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    const username = adminAccessForm.value.username;
    if (!username) {
      adminAccessStatus.value = { type: 'warning', message: 'Sélectionnez un utilisateur.' };
      return;
    }
    isSavingAccessUser.value = true;
    adminAccessStatus.value = null;
    try {
      const response = await apiFetch(`/api/access/users/${encodeURIComponent(username)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          displayName: adminAccessForm.value.displayName,
          portalUser: Boolean(adminAccessForm.value.portalUser),
          portalModelAdmin: Boolean(adminAccessForm.value.portalModelAdmin),
          platformAdmin: Boolean(adminAccessForm.value.platformAdmin),
          password: String(adminAccessForm.value.password || '').trim() || undefined
        })
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Erreur lors de la mise à jour de l'utilisateur");
      }

      await refreshUsers();
      await refreshCurrentAccess();
      ensurePortalAccess();
      adminAccessForm.value.password = '';
      adminAccessStatus.value = { type: 'success', message: 'Profil utilisateur mis à jour.' };
    } catch (error) {
      adminAccessStatus.value = { type: 'error', message: error.message };
    } finally {
      isSavingAccessUser.value = false;
    }
  }

  async function saveAccessPermissions() {
    if (!canManageAccess.value) {
      adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    if (!adminAccessForm.value.username) {
      adminAccessStatus.value = { type: 'warning', message: 'Sélectionnez un utilisateur.' };
      return;
    }
    isSavingAccessPermissions.value = true;
    adminAccessStatus.value = null;
    try {
      const response = await apiFetch(
        `/api/access/users/${encodeURIComponent(adminAccessForm.value.username)}/permissions`,
        {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            permissions: adminAccessPermissions.value.map((permission) => ({
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
      await refreshUsers();
      await refreshCurrentAccess();
      await refreshModels();
      ensurePortalAccess();
      adminAccessStatus.value = { type: 'success', message: 'Permissions enregistrées.' };
    } catch (error) {
      adminAccessStatus.value = { type: 'error', message: error.message };
    } finally {
      isSavingAccessPermissions.value = false;
    }
  }

  async function deleteAccessUser() {
    if (!canManageAccess.value) {
      adminAccessStatus.value = { type: 'warning', message: 'Accès réservé à un administrateur plateforme.' };
      return;
    }
    const username = adminAccessForm.value.username;
    if (!username) {
      adminAccessStatus.value = { type: 'warning', message: 'Sélectionnez un utilisateur.' };
      return;
    }
    if (username === 'admin') {
      adminAccessStatus.value = { type: 'warning', message: "Le compte 'admin' ne peut pas être supprimé." };
      return;
    }

    isDeletingAccessUser.value = true;
    adminAccessStatus.value = null;
    try {
      const response = await apiFetch(`/api/access/users/${encodeURIComponent(username)}`, {
        method: 'DELETE'
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Erreur lors de la suppression de l'utilisateur");
      }
      await refreshUsers();
      await refreshCurrentAccess();
      await refreshModels();
      ensurePortalAccess();
      adminAccessStatus.value = { type: 'success', message: 'Utilisateur supprimé.' };
    } catch (error) {
      adminAccessStatus.value = { type: 'error', message: error.message };
    } finally {
      isDeletingAccessUser.value = false;
    }
  }

  async function refreshModels(preferredKey) {
    if (!isAuthenticated.value) {
      models.value = [];
      selectedModelKey.value = '';
      resetModelState();
      resetDataState();
      return;
    }
    isLoadingModels.value = true;
    status.value = null;
    refreshHealth();
    try {
      const response = await apiFetch('/api/models');
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
      syncAdminPermissionRows();

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
    if (modelKey && !canViewModel(modelKey)) {
      resetModelState();
      return;
    }
    isLoadingModel.value = true;
    try {
      const response = await apiFetch(`/api/models/${encodeURIComponent(modelKey)}`);
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
      modelDetails.value = {
        objectTypes,
        linkTypes,
        languages,
        defaultLanguage: defaultLanguageValue,
        userPortalLabels
      };
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
      const response = await apiFetch('/api/health?deep=true');
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
    if (modelKey && !canReadModelData(modelKey)) {
      resetDataState();
      return;
    }
    isLoadingData.value = true;
    try {
      const response = await apiFetch(`/api/data?modelKey=${encodeURIComponent(modelKey)}`);
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
      selectedRootObjectKey.value = objects[0]?.idKey || '';
      rootObjectQuery.value = '';
      treeLinkSelections.value = {};
      treeExpandedNodes.value = {};
      objectFilter.value = '';
    } catch (error) {
      resetDataState();
      status.value = { type: 'error', message: error.message };
    } finally {
      isLoadingData.value = false;
    }
  }

  async function uploadModel() {
    if (!canAccessModelAdminPortal.value) {
      status.value = { type: 'warning', message: 'Accès au portail administration requis.' };
      return;
    }
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
      const response = await apiFetch('/api/models', {
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
    if (!canCreateCurrentModelData.value) {
      status.value = { type: 'warning', message: 'Droit CREATE manquant pour ce modèle.' };
      return;
    }
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
      const response = await apiFetch('/api/data', {
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

  async function createObject() {
    if (!canCreateCurrentModelData.value) {
      createObjectStatus.value = { type: 'warning', message: 'Droit CREATE manquant pour ce modèle.' };
      return;
    }
    if (!selectedModelKey.value) {
      createObjectStatus.value = { type: 'warning', message: 'Sélectionnez un modèle cible.' };
      return;
    }
    if (!createObjectType.value) {
      createObjectStatus.value = { type: 'warning', message: "Sélectionnez un type d'objet." };
      return;
    }

    const missingRequired = createObjectAttributeDefs.value.filter((def) => {
      if (!def.required) {
        return false;
      }
      const value = createObjectAttributes.value[def.name];
      return value === undefined || value === null || String(value).trim() === '';
    });

    if (missingRequired.length) {
      createObjectStatus.value = {
        type: 'warning',
        message: 'Renseignez tous les attributs obligatoires.'
      };
      return;
    }

    createObjectStatus.value = null;
    isCreatingObject.value = true;
    try {
      const attributes = createObjectAttributeDefs.value
        .map((def) => ({
          key: def.name,
          value: createObjectAttributes.value[def.name]
        }))
        .filter((attr) => attr.key && String(attr.value ?? '').trim() !== '');

      const response = await apiFetch('/api/objects', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          modelKey: selectedModelKey.value,
          type: createObjectType.value,
          attributes
        })
      });

      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(extractErrorMessage(payload, 'Erreur lors de la création.'));
      }

      createObjectStatus.value = {
        type: 'success',
        message: `Objet créé (ID #${payload?.id ?? 'OK'}).`
      };
      await refreshData(selectedModelKey.value);
    } catch (error) {
      createObjectStatus.value = { type: 'error', message: error.message };
    } finally {
      isCreatingObject.value = false;
    }
  }

  async function createLink() {
    if (!canCreateCurrentModelData.value) {
      createLinkStatus.value = { type: 'warning', message: 'Droit CREATE manquant pour ce modèle.' };
      return;
    }
    if (!selectedModelKey.value) {
      createLinkStatus.value = { type: 'warning', message: 'Sélectionnez un modèle cible.' };
      return;
    }
    if (!createLinkType.value) {
      createLinkStatus.value = { type: 'warning', message: 'Sélectionnez un type de lien.' };
      return;
    }
    if (!createLinkSourceId.value || !createLinkTargetId.value) {
      createLinkStatus.value = { type: 'warning', message: 'Sélectionnez les deux objets.' };
      return;
    }

    createLinkStatus.value = null;
    isCreatingLink.value = true;
    try {
      const response = await apiFetch('/api/links', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          modelKey: selectedModelKey.value,
          type: createLinkType.value,
          fromId: createLinkSourceId.value,
          toId: createLinkTargetId.value
        })
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(extractErrorMessage(payload, 'Erreur lors de la création du lien.'));
      }

      createLinkStatus.value = {
        type: 'success',
        message: 'Lien créé.'
      };
      await refreshData(selectedModelKey.value);
    } catch (error) {
      createLinkStatus.value = { type: 'error', message: error.message };
    } finally {
      isCreatingLink.value = false;
    }
  }

  async function deleteObject(object) {
    if (!object || object.id === null || object.id === undefined) {
      return;
    }
    if (!selectedModelKey.value) {
      status.value = { type: 'warning', message: 'Sélectionnez un modèle cible.' };
      return;
    }
    if (!canDeleteCurrentModelData.value) {
      status.value = { type: 'warning', message: 'Droit DELETE manquant pour ce modèle.' };
      return;
    }

    status.value = null;
    isLoadingData.value = true;
    try {
      const response = await apiFetch(
        `/api/objects/${encodeURIComponent(object.id)}?modelKey=${encodeURIComponent(selectedModelKey.value)}`,
        { method: 'DELETE' }
      );
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || "Erreur lors de la suppression de l'objet");
      }
      status.value = { type: 'success', message: `Objet #${object.id} supprimé.` };
      await refreshData(selectedModelKey.value);
      if (selectedObject.value?.id === object.id) {
        selectedObject.value = null;
      }
      if (tableSelectedObject.value?.id === object.id) {
        tableSelectedObject.value = null;
      }
    } catch (error) {
      status.value = { type: 'error', message: error.message };
    } finally {
      isLoadingData.value = false;
    }
  }

  async function loadModelXml() {
    if (!canAccessModelAdminPortal.value) {
      modelXmlStatus.value = { type: 'warning', message: 'Accès au portail administration requis.' };
      return;
    }
    if (!selectedModelKey.value) {
      modelXmlStatus.value = { type: 'warning', message: 'Sélectionnez un modèle.' };
      return;
    }
    isLoadingModelXml.value = true;
    modelXmlStatus.value = null;
    try {
      const response = await apiFetch(`/api/models/${encodeURIComponent(selectedModelKey.value)}/xml`);
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
      modelXml.value = text;
      modelXmlStatus.value = { type: 'success', message: 'XML chargé.' };
    } catch (error) {
      modelXmlStatus.value = { type: 'error', message: error.message };
    } finally {
      isLoadingModelXml.value = false;
    }
  }

  async function saveModelXml() {
    if (!canAccessModelAdminPortal.value) {
      modelXmlStatus.value = { type: 'warning', message: 'Accès au portail administration requis.' };
      return;
    }
    if (!selectedModelKey.value) {
      modelXmlStatus.value = { type: 'warning', message: 'Sélectionnez un modèle.' };
      return;
    }
    if (!modelXml.value.trim()) {
      modelXmlStatus.value = { type: 'warning', message: 'XML vide.' };
      return;
    }
    isSavingModelXml.value = true;
    modelXmlStatus.value = null;
    try {
      const response = await apiFetch(`/api/models/${encodeURIComponent(selectedModelKey.value)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/xml' },
        body: modelXml.value
      });
      const payload = await readJson(response);
      if (!response.ok) {
        throw new Error(payload?.error || 'Erreur lors de la sauvegarde du modèle.');
      }
      const renamed = Boolean(payload?.renamed);
      modelXmlStatus.value = {
        type: 'success',
        message: renamed
          ? `Modèle mis à jour (nouvelle clé ${payload?.key}).`
          : 'Modèle mis à jour.'
      };
      await refreshModels(payload?.key || selectedModelKey.value);
    } catch (error) {
      modelXmlStatus.value = { type: 'error', message: error.message };
    } finally {
      isSavingModelXml.value = false;
    }
  }

  async function deleteModel() {
    if (!canAccessModelAdminPortal.value) {
      adminStatus.value = { type: 'warning', message: 'Accès au portail administration requis.' };
      return;
    }
    if (!adminModelKey.value) {
      adminStatus.value = { type: 'warning', message: 'Sélectionnez un modèle.' };
      return;
    }
    adminStatus.value = null;
    try {
      const response = await apiFetch(`/api/models/${encodeURIComponent(adminModelKey.value)}`, {
        method: 'DELETE'
      });
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
    modelDetails.value = {
      objectTypes: [],
      linkTypes: [],
      languages: [],
      defaultLanguage: '',
      userPortalLabels: {}
    };
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
    selectedRootObjectKey.value = '';
    rootObjectQuery.value = '';
    treeLinkSelections.value = {};
    treeExpandedNodes.value = {};
  }

  function resetCreateState() {
    createObjectType.value = '';
    createObjectAttributes.value = {};
    createObjectStatus.value = null;
    isCreatingObject.value = false;
    createLinkType.value = '';
    createLinkSourceId.value = null;
    createLinkTargetId.value = null;
    createLinkStatus.value = null;
    isCreatingLink.value = false;
  }

  function resetModelEditorState() {
    modelXml.value = '';
    modelXmlStatus.value = null;
    isLoadingModelXml.value = false;
    isSavingModelXml.value = false;
  }

  function selectObject(object) {
    selectedObject.value = object;
  }

  function setRootObjectByKey(key) {
    if (!key) {
      selectedRootObjectKey.value = '';
      treeLinkSelections.value = {};
      treeExpandedNodes.value = {};
      return;
    }
    const target = objectIndex.value.get(String(key));
    if (!target) {
      return;
    }
    const isSameRoot = selectedRootObjectKey.value === target.idKey;
    selectedRootObjectKey.value = target.idKey;
    selectedObject.value = target;
    if (!isSameRoot) {
      treeLinkSelections.value = {};
      treeExpandedNodes.value = {};
    }
  }

  function resetExplorerTraversal() {
    treeLinkSelections.value = {};
    treeExpandedNodes.value = {};
  }

  function clearNodeSelections(pathPrefix = '') {
    if (!pathPrefix) {
      treeLinkSelections.value = {};
      treeExpandedNodes.value = {};
      return;
    }
    const next = {};
    Object.entries(treeLinkSelections.value).forEach(([path, selected]) => {
      if (!path.startsWith(pathPrefix)) {
        next[path] = selected;
      }
    });
    treeLinkSelections.value = next;
    clearNodeExpansions(pathPrefix);
  }

  function clearNodeExpansions(pathPrefix = '') {
    if (!pathPrefix) {
      treeExpandedNodes.value = {};
      return;
    }
    const next = {};
    Object.entries(treeExpandedNodes.value).forEach(([path, expanded]) => {
      if (!path.startsWith(pathPrefix)) {
        next[path] = expanded;
      }
    });
    treeExpandedNodes.value = next;
  }

  function isNodeExpanded(nodePath) {
    return Boolean(treeExpandedNodes.value[nodePath]);
  }

  function toggleNodeExpanded(nodePath) {
    if (!nodePath) {
      return;
    }
    treeExpandedNodes.value = {
      ...treeExpandedNodes.value,
      [nodePath]: !treeExpandedNodes.value[nodePath]
    };
  }

  function getObjectKeyFromNodePath(nodePath) {
    if (!nodePath) {
      return '';
    }
    const segments = String(nodePath).split('>');
    const last = segments[segments.length - 1] || '';
    if (!last.includes(':')) {
      return last;
    }
    const parts = last.split(':');
    return parts[parts.length - 1] || '';
  }

  function getNodeRelationsByPath(nodePath, objectKey = '') {
    const resolvedObjectKey = objectKey ? String(objectKey) : getObjectKeyFromNodePath(nodePath);
    if (!resolvedObjectKey) {
      return [];
    }
    return relationsByObjectKey.value.get(resolvedObjectKey) || [];
  }

  function getNodeRelationTypes(nodePath, objectKey = '') {
    return Array.from(
      new Set(
        getNodeRelationsByPath(nodePath, objectKey)
          .map((relation) => relation.type)
          .filter(Boolean)
      )
    ).sort((a, b) => a.localeCompare(b));
  }

  function getSelectedNodeRelationTypes(nodePath, availableTypes) {
    const selected = treeLinkSelections.value[nodePath];
    if (Array.isArray(selected)) {
      return selected;
    }
    return availableTypes;
  }

  function isNodeRelationSelected(nodePath, relationType, objectKey = '') {
    if (!nodePath || !relationType) {
      return false;
    }
    const availableTypes = getNodeRelationTypes(nodePath, objectKey);
    if (!availableTypes.includes(relationType)) {
      return false;
    }
    const selectedTypes = getSelectedNodeRelationTypes(nodePath, availableTypes);
    return selectedTypes.includes(relationType);
  }

  function toggleNodeRelation(nodePath, relationType, checked, objectKey = '') {
    if (!nodePath || !relationType) {
      return;
    }

    const availableTypes = getNodeRelationTypes(nodePath, objectKey);
    if (!availableTypes.includes(relationType)) {
      return;
    }

    const current = new Set(getSelectedNodeRelationTypes(nodePath, availableTypes));
    if (checked) {
      current.add(relationType);
    } else {
      current.delete(relationType);
    }

    const nextSelected = Array.from(current).sort((a, b) => a.localeCompare(b));
    const next = { ...treeLinkSelections.value };
    const allSelected = nextSelected.length === availableTypes.length
      && availableTypes.every((type) => current.has(type));

    if (allSelected) {
      delete next[nodePath];
    } else {
      next[nodePath] = nextSelected;
    }

    treeLinkSelections.value = next;

    if (!checked) {
      const relatedBranches = getNodeRelationsByPath(nodePath, objectKey)
        .filter((relation) => relation.type === relationType)
        .map((relation) => `${nodePath}>${relation.key}`);
      relatedBranches.forEach((prefix) => clearNodeSelections(prefix));
    }
  }

  function setCurrentPage(page) {
    if (!page) {
      return;
    }
    currentPage.value = page;
  }

  function setPortal(portal, preferredPage = '') {
    const normalized = normalizePortal(portal);
    if (!normalized) {
      return;
    }
    if (normalized === 'user' && !canAccessUserPortal.value) {
      status.value = { type: 'warning', message: 'Accès au portail métier non autorisé pour ce profil.' };
      return;
    }
    if (normalized === 'model-admin' && !canAccessModelAdminPortal.value) {
      status.value = {
        type: 'warning',
        message: "Accès au portail administration du modèle non autorisé pour ce profil."
      };
      return;
    }
    activePortal.value = normalized;
    const pages = getPortalPages(normalized);
    if (preferredPage && pages.includes(preferredPage)) {
      currentPage.value = preferredPage;
      return;
    }
    if (!pages.includes(currentPage.value)) {
      currentPage.value = getPortalDefaultPage(normalized);
    }
  }

  function clearPortal() {
    activePortal.value = '';
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
      return userPortalPages;
    }
    if (portal === 'model-admin') {
      return modelAdminPortalPages;
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
    if (isPlatformAdmin.value) {
      return {
        modelKey,
        visible: true,
        canRead: true,
        canCreate: true,
        canUpdate: true,
        canDelete: true
      };
    }
    return accessPermissions.value.find((permission) => permission.modelKey === modelKey) || null;
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
    if (activePortal.value === 'user' && !canAccessUserPortal.value) {
      activePortal.value = '';
      status.value = { type: 'warning', message: 'Votre profil ne permet pas l’accès au portail métier.' };
    }
    if (activePortal.value === 'model-admin' && !canAccessModelAdminPortal.value) {
      activePortal.value = '';
      status.value = { type: 'warning', message: "Votre profil ne permet pas l’accès au portail administration." };
    }
  }

  function selectObjectByKey(key) {
    const target = objectIndex.value.get(String(key));
    if (target) {
      selectedObject.value = target;
    }
  }

  function viewObjectFromTable(key) {
    const target = objectIndex.value.get(String(key));
    if (target) {
      tableSelectedObject.value = target;
      showTableDetailPanel.value = true;
    }
  }

  function openInExplorerFromTable() {
    if (!tableSelectedObject.value) {
      return;
    }
    setRootObjectByKey(tableSelectedObject.value.idKey);
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

  function buildExplorerNode(objectKey, nodePath, depth, visitedKeys) {
    const object = objectIndex.value.get(objectKey);
    if (!object) {
      return null;
    }

    const relations = relationsByObjectKey.value.get(objectKey) || [];
    const availableTypes = Array.from(
      new Set(relations.map((relation) => relation.type).filter(Boolean))
    );
    const selected = Array.isArray(treeLinkSelections.value[nodePath])
      ? treeLinkSelections.value[nodePath]
      : availableTypes;
    const selectedSet = new Set(selected);
    const depthLimitReached = depth >= explorerMaxDepth;

    const children = relations
      .filter((relation) => selectedSet.has(relation.type))
      .map((relation) => {
        const childPath = `${nodePath}>${relation.key}`;
        if (depthLimitReached) {
          return {
            key: childPath,
            edge: relation,
            cycle: false,
            depthLimit: true,
            node: null
          };
        }
        if (visitedKeys.has(relation.targetKey)) {
          return {
            key: childPath,
            edge: relation,
            cycle: true,
            depthLimit: false,
            node: null
          };
        }
        const branchVisited = new Set(visitedKeys);
        branchVisited.add(relation.targetKey);
        return {
          key: childPath,
          edge: relation,
          cycle: false,
          depthLimit: false,
          node: buildExplorerNode(relation.targetKey, childPath, depth + 1, branchVisited)
        };
      });

    return {
      key: `${nodePath}:${object.idKey}`,
      nodePath,
      depth,
      object,
      relations,
      children,
      visitedKeys: Array.from(visitedKeys)
    };
  }

  function getExplorerSubtree(objectKey, nodePath, depth = 0, visitedKeys = []) {
    const key = String(objectKey || '');
    if (!key) {
      return null;
    }
    const path = nodePath || key;
    const visited = new Set(
      Array.isArray(visitedKeys) && visitedKeys.length
        ? visitedKeys.map((entry) => String(entry))
        : [key]
    );
    if (!visited.has(key)) {
      visited.add(key);
    }
    return buildExplorerNode(key, path, depth, visited);
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
    const typeDef = modelDetails.value.objectTypes.find((type) => type.name === typeName);
    const iconName = sanitizeMaterialIconName(typeDef?.icon);
    return iconName || 'category';
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

  function getLinkTypeDefinition(linkTypeName) {
    if (!linkTypeName) {
      return null;
    }
    return modelDetails.value.linkTypes.find((link) => link.name === linkTypeName) || null;
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
    const labels = modelDetails.value?.userPortalLabels || {};
    const requested = displayLanguage.value;
    const fallback = defaultLanguage.value;

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

    if (selectedModel.value?.name) {
      return selectedModel.value.name;
    }
    return 'Portail métier';
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
    let current = modelTypeIndex.value.get(candidate);
    while (current && current.parent) {
      const parent = current.parent;
      if (!parent || visited.has(parent)) {
        return false;
      }
      if (parent === allowed) {
        return true;
      }
      visited.add(parent);
      current = modelTypeIndex.value.get(parent);
    }
    return false;
  }

  function isTypeAllowed(candidate, allowedTypes) {
    if (!candidate || !Array.isArray(allowedTypes) || !allowedTypes.length) {
      return false;
    }
    return allowedTypes.some((allowed) => isTypeOrSubtype(candidate, allowed));
  }

  async function readJson(response) {
    try {
      return await response.json();
    } catch (error) {
      return null;
    }
  }

  function extractErrorMessage(payload, fallback) {
    if (payload?.error) {
      return payload.error;
    }
    const errors = Array.isArray(payload?.errors) ? payload.errors : [];
    if (errors.length) {
      return errors
        .map((err) => err?.message || err?.toString?.() || 'Erreur de validation')
        .join(' • ');
    }
    return fallback;
  }

  return {
    apiBase,
    authToken,
    isAuthenticated,
    isAuthenticating,
    authStatus,
    loginUsername,
    loginPassword,
    authMeta,
    currentPage,
    activePortal,
    users,
    userOptions,
    accessProfile,
    accessStatus,
    isLoadingUsers,
    canAccessUserPortal,
    canAccessModelAdminPortal,
    isPlatformAdmin,
    canManageAccess,
    selectedModelPermission,
    canReadCurrentModelData,
    canCreateCurrentModelData,
    canUpdateCurrentModelData,
    canDeleteCurrentModelData,
    adminAccessUserKey,
    adminAccessStatus,
    isSavingAccessUser,
    isSavingAccessPermissions,
    isDeletingAccessUser,
    newAccessUsername,
    newAccessDisplayName,
    adminAccessForm,
    adminAccessPermissions,
    isPortalSelected,
    isUserPortal,
    isModelAdminPortal,
    portalLabel,
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
    rootObjectQuery,
    selectedRootObjectKey,
    treeLinkSelections,
    treeExpandedNodes,
    showCycleDetection,
    status,
    healthStatus,
    openGroups,
    openLinkGroups,
    selectedAttributeTab,
    selectedLinkedRelationTab,
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
    createObjectType,
    createObjectAttributes,
    createObjectStatus,
    isCreatingObject,
    createLinkType,
    createLinkSourceId,
    createLinkTargetId,
    createLinkStatus,
    isCreatingLink,
    modelXml,
    modelXmlStatus,
    isLoadingModelXml,
    isSavingModelXml,
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
    createObjectTypeOptions,
    createLinkTypeOptions,
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
    rootObjectOptions,
    selectedRootObject,
    tableTypeOptions,
    filteredTableRows,
    selectedObjectType,
    tableSelectedObjectType,
    attributeTabs,
    tableAttributeTabs,
    objectTypeGroups,
    createObjectAttributeDefs,
    createLinkDefinition,
    createLinkSourceOptions,
    createLinkTargetOptions,
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
    relationsByObjectKey,
    explorerTree,
    linkedObjects,
    linkedRelationTabs,
    linkedGroups,
    tableSelectedLinks,
    formatAttributeLabel,
    getTypeIconName,
    getAttributeLabel,
    getLinkTypeLabel,
    getUserPortalTitle,
    getRepresentativeAttributeKeys,
    getObjectPrimaryAttributes,
    getObjectPrimaryLabel,
    formatObjectOptionLabel,
    handleModelFile,
    handleDataFile,
    refreshModels,
    refreshUsers,
    refreshCurrentAccess,
    refreshModelDetails,
    refreshHealth,
    refreshData,
    uploadModel,
    uploadData,
    createObject,
    createLink,
    deleteObject,
    createAccessUser,
    saveAccessUser,
    saveAccessPermissions,
    deleteAccessUser,
    impersonateUser,
    stopImpersonation,
    login,
    logout,
    refreshSession,
    loadModelXml,
    saveModelXml,
    deleteModel,
    selectObject,
    setRootObjectByKey,
    resetExplorerTraversal,
    clearNodeSelections,
    clearNodeExpansions,
    isNodeRelationSelected,
    toggleNodeRelation,
    isNodeExpanded,
    toggleNodeExpanded,
    getExplorerSubtree,
    selectObjectByKey,
    viewObjectFromTable,
    openInExplorerFromTable,
    setCurrentPage,
    setPortal,
    clearPortal,
    selectModelObjectByName,
    selectModelLinkByName,
    copyAdminCommand
  };
}
