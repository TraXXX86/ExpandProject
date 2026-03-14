import { ref } from 'vue';

export function createEmptyModelDetails() {
  return {
    objectTypes: [],
    linkTypes: [],
    languages: [],
    defaultLanguage: '',
    userPortalLabels: {}
  };
}

export function createEmptyAuthMeta() {
  return {
    actorUsername: '',
    actorDisplayName: '',
    effectiveUsername: '',
    effectiveDisplayName: '',
    impersonating: false,
    actorPlatformAdmin: false,
    expiresAt: 0
  };
}

export function createEmptyAccessProfile() {
  return {
    username: '',
    displayName: '',
    portalUser: false,
    portalModelAdmin: false,
    platformAdmin: false
  };
}

export function createEmptyAdminAccessForm() {
  return {
    username: '',
    displayName: '',
    password: '',
    portalUser: true,
    portalModelAdmin: false,
    platformAdmin: false
  };
}

export function createDefaultHealthStatus() {
  return { api: 'unknown', neo4j: 'unknown', error: '' };
}

export function createAppStateBase() {
  const storedToken = typeof window !== 'undefined'
    ? window.localStorage.getItem('expand.authToken')
    : '';

  return {
    apiBase: import.meta.env?.VITE_API_BASE || 'http://localhost:8080',
    currentPage: ref('navigate'),
    activePortal: ref(''),
    userPortalPages: ['navigate', 'table', 'search', 'create', 'import-data'],
    modelAdminPortalPages: ['import-model', 'model', 'admin'],
    displayLanguage: ref(''),
    validateOnly: ref(false),
    modelSummary: ref(null),
    modelDetails: ref(createEmptyModelDetails()),
    selectedModelObject: ref(null),
    selectedModelLink: ref(null),
    modelObjectFilter: ref(''),
    modelLinkFilter: ref(''),
    dataSummary: ref(null),
    dataObjects: ref([]),
    dataLinks: ref([]),
    linkTypeInfo: ref({}),
    hasModel: ref(false),
    selectedObject: ref(null),
    objectFilter: ref(''),
    rootObjectQuery: ref(''),
    selectedRootObjectKey: ref(''),
    treeLinkSelections: ref({}),
    treeExpandedNodes: ref({}),
    showCycleDetection: ref(true),
    explorerMaxDepth: 12,
    status: ref(null),
    healthStatus: ref(createDefaultHealthStatus()),
    openGroups: ref([]),
    openLinkGroups: ref([]),
    selectedAttributeTab: ref(null),
    selectedLinkedRelationTab: ref(null),
    selectedModelGroupTab: ref(null),
    tableTypeFilter: ref([]),
    tableSearch: ref(''),
    tableAttributeKey: ref(''),
    tableAttributeKeyOperator: ref('contains'),
    tableAttributeValue: ref(''),
    tableAttributeValueOperator: ref('contains'),
    tableSelectedObject: ref(null),
    showTableDetailPanel: ref(true),
    tableSelectedAttributeTab: ref(null),
    createObjectType: ref(''),
    createObjectAttributes: ref({}),
    createObjectStatus: ref(null),
    isCreatingObject: ref(false),
    createLinkType: ref(''),
    createLinkSourceId: ref(null),
    createLinkTargetId: ref(null),
    createLinkStatus: ref(null),
    isCreatingLink: ref(false),
    modelXml: ref(''),
    modelXmlStatus: ref(null),
    isLoadingModelXml: ref(false),
    isSavingModelXml: ref(false),
    models: ref([]),
    selectedModelKey: ref(''),
    modelFile: ref(null),
    dataFile: ref(null),
    isLoadingModels: ref(false),
    isLoadingModel: ref(false),
    isLoadingData: ref(false),
    authToken: ref(storedToken || ''),
    isAuthenticated: ref(Boolean(storedToken)),
    isAuthenticating: ref(false),
    authStatus: ref(null),
    loginUsername: ref('admin'),
    loginPassword: ref('admin'),
    authMeta: ref(createEmptyAuthMeta()),
    users: ref([]),
    accessProfile: ref(createEmptyAccessProfile()),
    accessPermissions: ref([]),
    accessStatus: ref(null),
    isLoadingUsers: ref(false),
    adminAccessUserKey: ref(''),
    adminAccessStatus: ref(null),
    isSavingAccessUser: ref(false),
    isSavingAccessPermissions: ref(false),
    isDeletingAccessUser: ref(false),
    newAccessUsername: ref(''),
    newAccessDisplayName: ref(''),
    adminAccessForm: ref(createEmptyAdminAccessForm()),
    adminAccessPermissions: ref([]),
    adminModelKey: ref(''),
    adminCommandVisible: ref(false),
    adminStatus: ref(null),
    fullTextQuery: ref(''),
    fullTextTypeFilter: ref([])
  };
}
