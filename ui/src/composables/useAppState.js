import { useModelViews } from './useModelViews';
import { useAccessViews } from './useAccessViews';
import { useDataViews } from './useDataViews';
import { useAuthActions } from './useAuthActions';
import { useAccessAdminActions } from './useAccessAdminActions';
import { useModelActions } from './useModelActions';
import { useDataActions } from './useDataActions';
import { useExplorerActions } from './useExplorerActions';
import { usePresentationHelpers } from './usePresentationHelpers';
import { createApiClient, createRequestCoordinator } from './http';
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

export function useAppState() {
  const apiBase = import.meta.env.VITE_API_BASE || '';
  // Remove tokens persisted by earlier versions; sessions now use HttpOnly cookies.
  try { window.localStorage.removeItem('expand.authToken'); } catch { /* storage may be disabled */ }
  const requests = createRequestCoordinator();

  const currentPage = ref('navigate');
  const activePortal = ref('');
  const userPortalPages = ['navigate', 'table', 'search', 'create', 'import-data', 'paths', 'quality', 'history'];
  const modelAdminPortalPages = ['import-model', 'model', 'admin', 'workflows'];
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
  const pageObjects = ref([]);
  const dataOffset = ref(0);
  const dataLimit = ref(100);
  const dataHasMore = ref(false);
  const dataQuery = ref('');
  const dataType = ref('');
  const searchMode = ref('contains');
  const workflowId = ref('');
  const workflowStatus = ref('');
  const neighborStatus = ref({});
  const isMutatingLink = ref(false);
  let filterTimer;

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
  const tableVisibleColumns = ref([]);
  const tableSortBy = ref([]);
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
  const hasLoadedModels = ref(false);
  const isLoadingModel = ref(false);
  const isLoadingData = ref(false);

  const authToken = ref('');
  const isAuthenticated = ref(Boolean(authToken.value));
  const isAuthenticating = ref(false);
  const authStatus = ref(null);
  const loginUsername = ref('');
  const loginPassword = ref('');
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
  const newAccessPassword = ref('');
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
  const isDeletingModel = ref(false);

  const tableHeaders = [
    { title: 'Type', key: 'type' },
    { title: 'ID', key: 'id' },
    { title: 'Aperçu', key: 'preview' },
    { title: 'Statut', key: 'workflowStatus' },
    { title: 'Attributs', key: 'attributes' },
    { title: 'Actions', key: 'actions', sortable: false }
  ];

  const fullTextHeaders = [
    { title: 'Type', key: 'type' },
    { title: 'ID', key: 'id' },
    { title: 'Aperçu', key: 'preview' },
    { title: 'Statut', key: 'workflowStatus' },
    { title: 'Correspondances', key: 'matches' },
    { title: 'Actions', key: 'actions', sortable: false }
  ];

  const tableOperatorOptions = [
    { title: 'Contient', value: 'contains' },
    { title: 'Egal', value: 'equals' }
  ];

  const fullTextQuery = ref('');
  const fullTextTypeFilter = ref([]);

    // One shared context keeps refs stable across focused action modules.
  function clearDataFilterTimer() { clearTimeout(filterTimer); }

  const ctx = {
    clearDataFilterTimer,
    get hasLoadedModels() { return hasLoadedModels; },
    get newAccessPassword() { return newAccessPassword; },
    get isDeletingModel() { return isDeletingModel; },
    get searchMode() { return searchMode; },
    get workflowId() { return workflowId; },
    get workflowStatus() { return workflowStatus; },
    get getLinkTypeLabel() { return getLinkTypeLabel; },
    get formatAttributeLabel() { return formatAttributeLabel; },
    get getModelPermission() { return getModelPermission; },
    get canCreateModelData() { return canCreateModelData; },
    get canUpdateModelData() { return canUpdateModelData; },
    get canDeleteModelData() { return canDeleteModelData; },
    get getObjectPrimaryLabel() { return getObjectPrimaryLabel; },
    get getObjectPrimaryAttributes() { return getObjectPrimaryAttributes; },
    get formatObjectOptionLabel() { return formatObjectOptionLabel; },
    get getAttributeLabel() { return getAttributeLabel; },
    get isTypeAllowed() { return isTypeAllowed; },
    get buildGraphNodes() { return buildGraphNodes; },
    get buildGraphEdges() { return buildGraphEdges; },
    get directionToLabel() { return directionToLabel; },
    get directionToIcon() { return directionToIcon; },
    get isDirectedLink() { return isDirectedLink; },
    get buildExplorerNode() { return buildExplorerNode; },

    get dataQuery() { return dataQuery; },
    get dataType() { return dataType; },
    get fullTextQuery() { return fullTextQuery; },
    get fullTextTypeFilter() { return fullTextTypeFilter; },
    get objectFilter() { return objectFilter; },
    get tableAttributeKey() { return tableAttributeKey; },
    get tableAttributeKeyOperator() { return tableAttributeKeyOperator; },
    get tableAttributeValue() { return tableAttributeValue; },
    get tableAttributeValueOperator() { return tableAttributeValueOperator; },
    get tableSearch() { return tableSearch; },
    get tableTypeFilter() { return tableTypeFilter; },

    get accessPermissions() { return accessPermissions; },
    get accessProfile() { return accessProfile; },
    get accessStatus() { return accessStatus; },
    get activePortal() { return activePortal; },
    get adminAccessForm() { return adminAccessForm; },
    get adminAccessPermissions() { return adminAccessPermissions; },
    get adminAccessStatus() { return adminAccessStatus; },
    get adminAccessUserKey() { return adminAccessUserKey; },
    get adminCommand() { return adminCommand; },
    get adminModel() { return adminModel; },
    get adminModelKey() { return adminModelKey; },
    get adminStatus() { return adminStatus; },
    get apiFetch() { return apiFetch; },
    get authMeta() { return authMeta; },
    get authStatus() { return authStatus; },
    get authToken() { return authToken; },
    get canAccessModelAdminPortal() { return canAccessModelAdminPortal; },
    get canAccessUserPortal() { return canAccessUserPortal; },
    get canCreateCurrentModelData() { return canCreateCurrentModelData; },
    get canDeleteCurrentModelData() { return canDeleteCurrentModelData; },
    get canManageAccess() { return canManageAccess; },
    get canReadModelData() { return canReadModelData; },
    get canUpdateCurrentModelData() { return canUpdateCurrentModelData; },
    get canViewModel() { return canViewModel; },
    get createLinkSourceId() { return createLinkSourceId; },
    get createLinkStatus() { return createLinkStatus; },
    get createLinkTargetId() { return createLinkTargetId; },
    get createLinkType() { return createLinkType; },
    get createObjectAttributeDefs() { return createObjectAttributeDefs; },
    get createObjectAttributes() { return createObjectAttributes; },
    get createObjectStatus() { return createObjectStatus; },
    get createObjectType() { return createObjectType; },
    get currentPage() { return currentPage; },
    get dataFile() { return dataFile; },
    get dataHasMore() { return dataHasMore; },
    get dataLimit() { return dataLimit; },
    get dataLinks() { return dataLinks; },
    get dataObjects() { return dataObjects; },
    get dataOffset() { return dataOffset; },
    get dataRequestQuery() { return dataRequestQuery; },
    get dataSummary() { return dataSummary; },
    get defaultLanguage() { return defaultLanguage; },
    get displayLanguage() { return displayLanguage; },
    get ensurePortalAccess() { return ensurePortalAccess; },
    get explorerMaxDepth() { return explorerMaxDepth; },
    get extractErrorMessage() { return extractErrorMessage; },
    get getFirstFile() { return getFirstFile; },
    get graphNodes() { return graphNodes; },
    get hasModel() { return hasModel; },
    get healthStatus() { return healthStatus; },
    get isAuthenticated() { return isAuthenticated; },
    get isAuthenticating() { return isAuthenticating; },
    get isCreatingLink() { return isCreatingLink; },
    get isCreatingObject() { return isCreatingObject; },
    get isDeletingAccessUser() { return isDeletingAccessUser; },
    get isLoadingData() { return isLoadingData; },
    get isLoadingModel() { return isLoadingModel; },
    get isLoadingModelXml() { return isLoadingModelXml; },
    get isLoadingModels() { return isLoadingModels; },
    get isLoadingUsers() { return isLoadingUsers; },
    get isMutatingLink() { return isMutatingLink; },
    get isPlatformAdmin() { return isPlatformAdmin; },
    get isSavingAccessPermissions() { return isSavingAccessPermissions; },
    get isSavingAccessUser() { return isSavingAccessUser; },
    get isSavingModelXml() { return isSavingModelXml; },
    get linkTypeInfo() { return linkTypeInfo; },
    get loginPassword() { return loginPassword; },
    get loginUsername() { return loginUsername; },
    get modelDetails() { return modelDetails; },
    get modelFile() { return modelFile; },
    get modelLinkFilter() { return modelLinkFilter; },
    get modelObjectFilter() { return modelObjectFilter; },
    get modelSummary() { return modelSummary; },
    get modelTypeIndex() { return modelTypeIndex; },
    get modelXml() { return modelXml; },
    get modelXmlStatus() { return modelXmlStatus; },
    get models() { return models; },
    get neighborStatus() { return neighborStatus; },
    get newAccessDisplayName() { return newAccessDisplayName; },
    get newAccessUsername() { return newAccessUsername; },
    get objectIndex() { return objectIndex; },
    get pageObjects() { return pageObjects; },
    get readJson() { return readJson; },
    get refreshCurrentAccess() { return refreshCurrentAccess; },
    get refreshData() { return refreshData; },
    get refreshHealth() { return refreshHealth; },
    get refreshModels() { return refreshModels; },
    get refreshUsers() { return refreshUsers; },
    get relationsByObjectKey() { return relationsByObjectKey; },
    get representativeAttributesByType() { return representativeAttributesByType; },
    get requests() { return requests; },
    get resetAuthState() { return resetAuthState; },
    get resetCreateState() { return resetCreateState; },
    get resetDataState() { return resetDataState; },
    get resetModelEditorState() { return resetModelEditorState; },
    get resetModelState() { return resetModelState; },
    get rootObjectQuery() { return rootObjectQuery; },
    get selectedModel() { return selectedModel; },
    get selectedModelKey() { return selectedModelKey; },
    get selectedModelLink() { return selectedModelLink; },
    get selectedModelObject() { return selectedModelObject; },
    get selectedObject() { return selectedObject; },
    get selectedRootObjectKey() { return selectedRootObjectKey; },
    get showTableDetailPanel() { return showTableDetailPanel; },
    get status() { return status; },
    get syncAdminPermissionRows() { return syncAdminPermissionRows; },
    get tableSelectedObject() { return tableSelectedObject; },
    get treeExpandedNodes() { return treeExpandedNodes; },
    get treeLinkSelections() { return treeLinkSelections; },
    get users() { return users; },
    get validateOnly() { return validateOnly; }
  };
  const { resetAuthState, login, logout, refreshSession, applyAuthPayload, refreshCurrentAccess, impersonateUser, stopImpersonation, getModelPermission, canViewModel, canReadModelData, canCreateModelData, canUpdateModelData, canDeleteModelData, ensurePortalAccess } = useAuthActions(ctx);
  const { refreshUsers, syncAdminPermissionRows, loadAdminAccessUser, createAccessUser, saveAccessUser, saveAccessPermissions, deleteAccessUser, copyAdminCommand } = useAccessAdminActions(ctx);
  const { refreshModels, refreshModelDetails, refreshHealth, uploadModel, loadModelXml, saveModelXml, deleteModel, resetModelState, resetModelEditorState, handleModelFile, handleDataFile, selectModelObjectByName, selectModelLinkByName, buildModelSummary, buildLinkTypeInfo, isDirectedLink } = useModelActions(ctx);
  const { refreshData, loadNeighbors, mutateLink, uploadData, createObject, createLink, deleteObject, resetDataState, resetCreateState, buildDataSummary, normalizeObjects, normalizeLinks } = useDataActions(ctx);
  const { selectObject, setRootObjectByKey, resetExplorerTraversal, clearNodeSelections, clearNodeExpansions, isNodeExpanded, toggleNodeExpanded, getObjectKeyFromNodePath, getNodeRelationsByPath, getNodeRelationTypes, getSelectedNodeRelationTypes, isNodeRelationSelected, toggleNodeRelation, selectObjectByKey, viewObjectFromTable, openInExplorerFromTable, buildExplorerNode, getExplorerSubtree, buildRelation, directionToLabel, directionToIcon, buildGraphNodes, buildGraphEdges, buildSelfLoopPath } = useExplorerActions(ctx);
  const { getFirstFile, resolveAttributeLabel, formatAttributeLabel, sanitizeMaterialIconName, getTypeIconName, getAttributeDefinition, getAttributeLabel, getLinkTypeDefinition, getLinkTypeLabel, getUserPortalTitle, getRepresentativeAttributeKeys, getObjectPrimaryAttributes, getObjectPrimaryLabel, formatObjectOptionLabel, isTypeOrSubtype, isTypeAllowed, readJson, extractErrorMessage } = usePresentationHelpers(ctx);

  const { modelLanguages, defaultLanguage, modelOptions, selectedModel, adminModel, filteredModelObjects, filteredModelLinks, modelGroupTabs, adminCommand } = useModelViews(ctx);
  const { canTransitionCurrentModelData, canManageCurrentWorkflows, userOptions, isPlatformAdmin, canAccessUserPortal, canAccessModelAdminPortal, selectedModelPermission, canReadCurrentModelData, canCreateCurrentModelData, canUpdateCurrentModelData, canDeleteCurrentModelData, canManageAccess, isPortalSelected, isUserPortal, isModelAdminPortal, portalLabel, canUploadModel, canUploadData, neo4jChipLabel, neo4jChipColor } = useAccessViews(ctx);
  const { dataRequestQuery, createObjectTypeOptions, createLinkTypeOptions, filteredObjects, rootObjectOptions, selectedRootObject, tableTypeOptions, filteredTableRows, selectedObjectType, tableSelectedObjectType, attributeTabs, tableAttributeTabs, objectTypeGroups, modelTypeIndex, createObjectAttributeDefs, createLinkDefinition, createLinkSourceOptions, createLinkTargetOptions, graphNodes, graphEdges, tableHasDetails, representativeAttributesByType, searchableAttributesByType, fullTextTypeOptions, fullTextResults, objectIndex, relationsByObjectKey, explorerTree, linkedObjects, linkedRelationTabs, linkedGroups, tableSelectedLinks } = useDataViews(ctx);

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

  watch(dataRequestQuery, (query, previous) => {
    if (JSON.stringify(query) === JSON.stringify(previous)) return;
    clearTimeout(filterTimer);
    requests.cancel('data');
    filterTimer = setTimeout(() => refreshData(selectedModelKey.value, 0), 300);
  });
  onBeforeUnmount(() => { clearTimeout(filterTimer); requests.cancelAll(); });

  watch(
    () => selectedModelKey.value,
    async (key, previousKey) => {
      workflowId.value = ''; workflowStatus.value = '';
      requests.cancel('model');
      isLoadingModel.value = false;
      resetDataState();
      if (!key) {
        resetModelState();
        resetDataState();
        resetCreateState();
        resetModelEditorState();
        return;
      }
      await Promise.all([refreshModelDetails(key), refreshData(key, 0)]);
      if (key !== selectedModelKey.value) return;
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

  const apiFetch = createApiClient({ apiBase, onUnauthorized: () => resetAuthState('Session expirée, reconnectez-vous.') });

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

  return {
    apiBase, apiFetch, readJson, tableVisibleColumns, tableSortBy, workflowId, workflowStatus, canTransitionCurrentModelData, canManageCurrentWorkflows,
    pageObjects, dataOffset, dataLimit, dataHasMore, dataQuery, dataType, searchMode, neighborStatus, isMutatingLink, loadNeighbors, mutateLink,
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
    newAccessPassword,
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
    hasLoadedModels,
    isLoadingModel,
    isLoadingData,
    adminModelKey,
    isDeletingModel,
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
