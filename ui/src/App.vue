<template>
  <v-app :class="['app-shell', portalThemeClass]">
    <v-app-bar height="72" color="surface" class="app-header" elevate-on-scroll>
      <v-container class="d-flex align-center justify-space-between">
        <div class="d-flex align-center" style="gap: 12px;">
          <v-avatar color="primary" size="40">
            <v-icon icon="mdi-database-eye-outline" color="white" />
          </v-avatar>
          <div>
            <div class="text-subtitle-1 font-weight-bold">{{ headerTitle }}</div>
            <div class="text-caption text-medium-emphasis">{{ headerSubtitle }}</div>
          </div>
        </div>
        <div class="d-flex align-center flex-wrap justify-end" style="gap: 8px;">
          <v-chip v-if="state.isAuthenticated" color="primary" variant="tonal">
            {{ state.accessProfile.displayName || state.accessProfile.username || 'Utilisateur' }}
          </v-chip>
          <v-chip v-if="state.authMeta.impersonating" color="warning" variant="tonal">
            Impersonation: {{ state.authMeta.effectiveUsername }} (admin: {{ state.authMeta.actorUsername }})
          </v-chip>
          <v-select
            v-if="state.modelOptions.length && state.isPortalSelected && state.isAuthenticated"
            v-model="state.selectedModelKey"
            :items="state.modelOptions"
            label="Modèle"
            density="compact"
            variant="outlined"
            hide-details
            clearable
            :loading="state.isLoadingModels"
            class="model-select"
          />
          <v-select
            v-if="state.modelLanguages.length && state.isPortalSelected && state.isAuthenticated"
            v-model="state.displayLanguage"
            :items="state.modelLanguages"
            label="Langue"
            density="compact"
            variant="outlined"
            hide-details
            class="language-select"
          />
          <v-chip :color="state.neo4jChipColor" variant="tonal">
            Neo4j {{ state.neo4jChipLabel }}
          </v-chip>
          <v-menu v-if="state.isAuthenticated" location="bottom end">
            <template #activator="{ props }">
              <v-btn
                v-bind="props"
                color="secondary"
                variant="tonal"
                prepend-icon="mdi-shield-crown-outline"
              >
                {{ state.canManageAccess ? 'Admin plateforme' : 'Compte' }}
              </v-btn>
            </template>
            <v-list density="comfortable" min-width="310">
              <v-list-item
                v-if="state.canManageAccess"
                v-for="entry in platformAdminEntries"
                :key="entry.key"
                :prepend-icon="entry.icon"
                :title="entry.title"
                :subtitle="entry.subtitle"
                @click="openFromAdminMenu(entry)"
              />
              <v-divider v-if="state.canManageAccess" />
              <v-list-item
                v-if="state.canManageAccess && state.authMeta.impersonating"
                prepend-icon="mdi-account-cancel-outline"
                title="Arrêter l'impersonation"
                subtitle="Revenir à votre session administrateur"
                @click="state.stopImpersonation"
              />
              <v-list-item
                prepend-icon="mdi-access-point"
                title="Rafraîchir le statut plateforme"
                subtitle="Relancer les contrôles API/Neo4j"
                @click="state.refreshHealth"
              />
              <v-list-item
                prepend-icon="mdi-account-switch-outline"
                title="Changer de portail"
                subtitle="Retour à l'écran de connexion"
                @click="state.clearPortal"
              />
              <v-list-item
                prepend-icon="mdi-account-arrow-right-outline"
                title="Se déconnecter"
                subtitle="Fermer la session en cours"
                @click="state.logout"
              />
            </v-list>
          </v-menu>
          <v-btn
            v-else
            color="primary"
            variant="tonal"
            prepend-icon="mdi-login"
            @click="state.authStatus = null"
          >
            Connexion
          </v-btn>
        </div>
      </v-container>
    </v-app-bar>

    <v-main>
      <div :class="['hero-bg', heroBgClass]" />
      <v-container class="py-6 hero-content">
        <v-slide-y-transition>
          <v-alert
            v-if="busyState.active"
            class="mb-6"
            type="info"
            variant="tonal"
            density="comfortable"
            border="start"
            icon="mdi-progress-clock"
          >
            <div class="d-flex align-center justify-space-between flex-wrap" style="gap: 12px;">
              <span>{{ busyState.message }}</span>
              <v-progress-circular indeterminate size="18" width="2" color="info" />
            </div>
          </v-alert>
        </v-slide-y-transition>

        <template v-if="!state.isAuthenticated">
          <v-row class="justify-center">
            <v-col cols="12" md="7" lg="5">
              <v-card class="card-animate" elevation="6" rounded="xl">
                <v-card-title class="section-title">Connexion</v-card-title>
                <v-card-text>
                  <div class="text-medium-emphasis mb-4">
                    Connectez-vous avec votre compte plateforme.
                    Le compte initial administrateur est <strong>admin / admin</strong>.
                  </div>
                  <v-text-field
                    v-model="state.loginUsername"
                    label="Login"
                    prepend-icon="mdi-account"
                    variant="outlined"
                    density="comfortable"
                  />
                  <v-text-field
                    v-model="state.loginPassword"
                    label="Mot de passe"
                    type="password"
                    prepend-icon="mdi-lock-outline"
                    variant="outlined"
                    density="comfortable"
                    @keyup.enter="state.login"
                  />
                  <v-btn
                    color="primary"
                    size="large"
                    :loading="state.isAuthenticating"
                    @click="state.login"
                  >
                    Se connecter
                  </v-btn>
                  <v-alert
                    v-if="state.authStatus"
                    class="mt-4"
                    :type="state.authStatus.type"
                    variant="tonal"
                    density="comfortable"
                    border="start"
                  >
                    {{ state.authStatus.message }}
                  </v-alert>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>
        </template>

        <template v-else-if="!state.isPortalSelected">
          <v-row class="mb-8">
            <v-col cols="12" md="7">
              <div class="kicker">Connexion</div>
              <h1 class="headline">Choisissez votre portail de travail.</h1>
              <p class="subhead">
                Un portail est dédié aux utilisateurs de données (CRUD métier), l'autre aux administrateurs
                du modèle de données (CRUD du modèle et maintenance).
              </p>
            </v-col>
            <v-col cols="12" md="5">
              <v-card class="card-animate" elevation="4" rounded="xl">
                <v-card-title class="section-title">Plateforme</v-card-title>
                <v-card-text>
                  <div v-if="state.accessProfile.username" class="text-medium-emphasis mb-2">
                    Utilisateur actif: <strong>{{ state.accessProfile.displayName || state.accessProfile.username }}</strong>
                  </div>
                  <div class="text-medium-emphasis">
                    Le menu <strong>Admin plateforme</strong> reste accessible en permanence depuis l'entête.
                  </div>
                  <v-divider class="my-4" />
                  <v-chip :color="state.neo4jChipColor" variant="tonal">
                    Neo4j {{ state.neo4jChipLabel }}
                  </v-chip>
                  <v-alert
                    v-if="state.accessStatus"
                    class="mt-4"
                    :type="state.accessStatus.type"
                    variant="tonal"
                    density="comfortable"
                    border="start"
                  >
                    {{ state.accessStatus.message }}
                  </v-alert>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>

          <v-row>
            <v-col cols="12" md="6">
              <v-card class="portal-entry portal-entry-user card-animate delay-1" elevation="5" rounded="xl">
                <v-card-title class="section-title d-flex align-center" style="gap: 10px;">
                  <v-icon icon="mdi-account-group-outline" />
                  Portail métier
                </v-card-title>
                <v-card-text>
                  <div class="text-medium-emphasis mb-4">
                    Consultation et manipulation des objets métier: navigation, recherche, création et import de données.
                  </div>
                  <v-chip-group column class="mb-5">
                    <v-chip color="primary" variant="tonal" class="ma-1">CRUD des données</v-chip>
                    <v-chip color="secondary" variant="tonal" class="ma-1">Recherche avancée</v-chip>
                    <v-chip color="accent" variant="tonal" class="ma-1">Exploration métier</v-chip>
                  </v-chip-group>
                  <v-btn
                    color="primary"
                    size="large"
                    :disabled="!state.canAccessUserPortal"
                    @click="state.setPortal('user')"
                  >
                    Entrer dans le portail métier
                  </v-btn>
                </v-card-text>
              </v-card>
            </v-col>
            <v-col cols="12" md="6">
              <v-card class="portal-entry portal-entry-admin card-animate delay-2" elevation="5" rounded="xl">
                <v-card-title class="section-title d-flex align-center" style="gap: 10px;">
                  <v-icon icon="mdi-cog-outline" />
                  Portail administration modèle
                </v-card-title>
                <v-card-text>
                  <div class="text-medium-emphasis mb-4">
                    Gouvernance et évolution du modèle: import, visualisation, édition XML et suppression contrôlée.
                  </div>
                  <v-chip-group column class="mb-5">
                    <v-chip color="primary" variant="tonal" class="ma-1">CRUD du modèle</v-chip>
                    <v-chip color="secondary" variant="tonal" class="ma-1">Maintenance</v-chip>
                    <v-chip color="accent" variant="tonal" class="ma-1">Administration</v-chip>
                  </v-chip-group>
                  <v-btn
                    color="primary"
                    size="large"
                    :disabled="!state.canAccessModelAdminPortal"
                    @click="state.setPortal('model-admin')"
                  >
                    Entrer dans le portail administration
                  </v-btn>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>
        </template>

        <template v-else>
          <v-row class="mb-6">
            <v-col cols="12">
              <div class="d-flex align-start justify-space-between flex-wrap" style="gap: 14px;">
                <div>
                  <div class="kicker">{{ portalKicker }}</div>
                </div>
                <v-btn
                  variant="tonal"
                  color="secondary"
                  prepend-icon="mdi-account-switch-outline"
                  @click="state.clearPortal"
                >
                  Changer de portail
                </v-btn>
              </div>

              <v-tabs
                v-if="activeTabs.length"
                v-model="state.currentPage"
                color="primary"
                align-tabs="start"
                class="portal-tabs"
              >
                <v-tab v-for="tab in activeTabs" :key="tab.value" :value="tab.value">
                  {{ tab.title }}
                  <v-tooltip v-if="tab.tooltip" activator="parent" location="bottom">
                    {{ tab.tooltip }}
                  </v-tooltip>
                </v-tab>
              </v-tabs>
              <v-alert
                v-else
                class="mt-4"
                type="warning"
                variant="tonal"
                density="comfortable"
                border="start"
              >
                Aucun onglet n'est disponible pour ce modèle avec vos droits actuels.
              </v-alert>
            </v-col>
          </v-row>

          <v-row class="mb-6">
            <v-col cols="12">
              <v-card class="view-overview-card" elevation="3" rounded="xl">
                <v-card-text class="d-flex align-center justify-space-between flex-wrap" style="gap: 16px;">
                  <div>
                    <div class="kicker">Vue actuelle</div>
                    <div class="text-subtitle-1 font-weight-bold">{{ activePageTitle }}</div>
                    <div class="text-body-2 text-medium-emphasis">
                      {{ activePageDescription }}
                    </div>
                    <div class="d-flex align-center flex-wrap mt-3" style="gap: 8px;">
                      <v-chip
                        v-for="item in overviewChips"
                        :key="item.key"
                        :color="item.color"
                        variant="tonal"
                        size="small"
                      >
                        <v-icon :icon="item.icon" start />
                        {{ item.text }}
                      </v-chip>
                    </div>
                    <div class="text-caption text-medium-emphasis mt-3">
                      URL partageable: {{ shareablePath }}
                    </div>
                  </div>
                  <v-btn
                    color="primary"
                    variant="tonal"
                    prepend-icon="mdi-link-variant"
                    @click="copyShareableLink"
                  >
                    Copier le lien
                  </v-btn>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>

          <v-window v-if="activeTabs.length" v-model="state.currentPage">
            <v-window-item value="navigate">
              <NavigatePage :state="state" />
            </v-window-item>

            <v-window-item value="table">
              <TablePage :state="state" />
            </v-window-item>

            <v-window-item value="search">
              <SearchPage :state="state" />
            </v-window-item>

            <v-window-item value="import-model">
              <ImportModelPage :state="state" />
            </v-window-item>

            <v-window-item value="import-data">
              <ImportDataPage :state="state" />
            </v-window-item>

            <v-window-item value="create">
              <CreatePage :state="state" />
            </v-window-item>

            <v-window-item value="model">
              <ModelPage :state="state" />
            </v-window-item>

            <v-window-item value="admin">
              <AdminPage :state="state" />
            </v-window-item>
          </v-window>
        </template>
      </v-container>
    </v-main>

    <v-snackbar v-model="shareLinkSnackbar" color="primary" timeout="2400">
      {{ shareLinkMessage }}
    </v-snackbar>
  </v-app>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAppState } from './composables/useAppState';
import AdminPage from './components/pages/AdminPage.vue';
import ImportDataPage from './components/pages/ImportDataPage.vue';
import ImportModelPage from './components/pages/ImportModelPage.vue';
import ModelPage from './components/pages/ModelPage.vue';
import NavigatePage from './components/pages/NavigatePage.vue';
import SearchPage from './components/pages/SearchPage.vue';
import TablePage from './components/pages/TablePage.vue';
import CreatePage from './components/pages/CreatePage.vue';
import { buildRouteLocation, parseRouteState } from './router/appRouteState';

const state = reactive(useAppState());
const route = useRoute();
const router = useRouter();
const isHydratingRoute = ref(false);
const pendingRootObjectKey = ref('');
const shareLinkSnackbar = ref(false);
const shareLinkMessage = ref('');

const userPortalTabs = [
  {
    title: 'Explorer',
    value: 'navigate',
    tooltip: "Explorer les objets de manière récursive en choisissant les liens à parcourir."
  },
  {
    title: 'Recherche avancée',
    value: 'table',
    tooltip: "Lister les objets et filtrer par type, identifiant ou attributs."
  },
  {
    title: 'Recherche',
    value: 'search',
    tooltip: 'Lancer une recherche plein texte sur les attributs marqués SEARCHABLE.'
  },
  {
    title: 'Import données',
    value: 'import-data',
    tooltip: 'Importer ou valider un fichier XML de données pour le modèle sélectionné.'
  },
  {
    title: 'Création',
    value: 'create',
    tooltip: 'Créer des objets et des liens en respectant les contraintes du modèle.'
  }
];

const modelAdminTabs = [
  {
    title: 'Modèle',
    value: 'model',
    tooltip: 'Consulter les types, attributs et liens définis dans le modèle.'
  },
  {
    title: 'Import modèle',
    value: 'import-model',
    tooltip: 'Charger ou mettre à jour le modèle XML en base.'
  },
  {
    title: 'Administration',
    value: 'admin',
    tooltip: "Gérer la suppression du modèle, les utilisateurs et leurs droits d'accès."
  }
];

const platformAdminEntries = [
  {
    key: 'data-portal',
    title: 'Ouvrir portail métier',
    subtitle: 'Fonctionnalités de gestion des données applicatives',
    portal: 'user',
    page: 'navigate',
    icon: 'mdi-account-group-outline'
  },
  {
    key: 'model-portal',
    title: 'Ouvrir portail administration modèle',
    subtitle: 'Fonctionnalités de gouvernance du modèle',
    portal: 'model-admin',
    page: 'model',
    icon: 'mdi-cog-outline'
  },
  {
    key: 'import-model',
    title: 'Accès direct à Import modèle',
    subtitle: "Démarrer un chargement ou une mise à jour du modèle",
    portal: 'model-admin',
    page: 'import-model',
    icon: 'mdi-upload-outline'
  },
  {
    key: 'delete-model',
    title: 'Accès direct à Suppression modèle',
    subtitle: 'Action d’administration globale avec confirmation',
    portal: 'model-admin',
    page: 'admin',
    icon: 'mdi-delete-outline'
  }
];

const activeTabs = computed(() => {
  if (state.isUserPortal) {
    return userPortalTabs.filter((tab) => {
      if (['navigate', 'table', 'search'].includes(tab.value)) {
        return state.canReadCurrentModelData;
      }
      if (['import-data', 'create'].includes(tab.value)) {
        return state.canCreateCurrentModelData;
      }
      return true;
    });
  }
  return modelAdminTabs;
});

const routeState = computed(() => parseRouteState(route));
const activePageMeta = computed(() => (
  [...userPortalTabs, ...modelAdminTabs].find((tab) => tab.value === state.currentPage) || null
));

const activePageTitle = computed(() => activePageMeta.value?.title || 'Vue');
const activePageDescription = computed(() => (
  activePageMeta.value?.tooltip || 'Retrouvez ce contexte directement depuis l’URL.'
));

const overviewChips = computed(() => {
  const chips = [];

  if (state.selectedModel) {
    chips.push({
      key: 'model',
      color: 'primary',
      icon: 'mdi-database-outline',
      text: state.selectedModel.version
        ? `${state.selectedModel.name} v${state.selectedModel.version}`
        : state.selectedModel.name
    });
  }

  if (state.displayLanguage) {
    const selectedLanguage = state.modelLanguages.find((language) => language.value === state.displayLanguage);
    chips.push({
      key: 'language',
      color: 'secondary',
      icon: 'mdi-translate',
      text: selectedLanguage?.title || state.displayLanguage
    });
  }

  if (state.dataSummary) {
    chips.push({
      key: 'objects',
      color: 'accent',
      icon: 'mdi-cube-outline',
      text: `${state.dataSummary.objectCount} objets`
    });
    chips.push({
      key: 'links',
      color: 'secondary',
      icon: 'mdi-connection',
      text: `${state.dataSummary.linkCount} liens`
    });
  }

  if (state.currentPage === 'navigate' && state.selectedRootObject) {
    chips.push({
      key: 'root',
      color: 'primary',
      icon: 'mdi-source-branch',
      text: `${state.selectedRootObject.type} #${state.selectedRootObject.id ?? 'N/A'}`
    });
  }

  if (state.currentPage === 'search' && state.fullTextQuery) {
    chips.push({
      key: 'search',
      color: 'secondary',
      icon: 'mdi-magnify',
      text: `Recherche: ${state.fullTextQuery}`
    });
  }

  if (state.currentPage === 'table' && (
    state.tableSearch
    || state.tableTypeFilter.length
    || state.tableAttributeKey
    || state.tableAttributeValue
  )) {
    chips.push({
      key: 'filters',
      color: 'secondary',
      icon: 'mdi-filter-variant',
      text: 'Filtres actifs'
    });
  }

  return chips;
});

const shareableLocation = computed(() => buildRouteLocation({
  isAuthenticated: state.isAuthenticated,
  activePortal: state.activePortal,
  currentPage: state.currentPage,
  selectedModelKey: state.selectedModelKey,
  displayLanguage: state.displayLanguage,
  selectedRootObjectKey: state.selectedRootObjectKey,
  tableSearch: state.tableSearch,
  tableTypeFilter: state.tableTypeFilter,
  tableAttributeKey: state.tableAttributeKey,
  tableAttributeKeyOperator: state.tableAttributeKeyOperator,
  tableAttributeValue: state.tableAttributeValue,
  tableAttributeValueOperator: state.tableAttributeValueOperator,
  fullTextQuery: state.fullTextQuery,
  fullTextTypeFilter: state.fullTextTypeFilter
}));

const shareablePath = computed(() => router.resolve(shareableLocation.value).fullPath);

const shareableUrl = computed(() => {
  const href = router.resolve(shareableLocation.value).href;
  if (typeof window === 'undefined') {
    return href;
  }
  return new URL(href, window.location.origin).toString();
});

const busyState = computed(() => {
  if (state.isAuthenticating) {
    return { active: true, message: 'Connexion en cours...' };
  }
  if (state.isLoadingModels) {
    return { active: true, message: 'Chargement des modèles disponibles...' };
  }
  if (state.isLoadingModel && state.currentPage === 'import-model') {
    return { active: true, message: 'Import ou mise à jour du modèle en cours...' };
  }
  if (state.isLoadingModel) {
    return { active: true, message: 'Chargement du modèle en cours...' };
  }
  if (state.isLoadingData && state.currentPage === 'import-data') {
    return {
      active: true,
      message: state.validateOnly
        ? 'Validation du fichier XML en cours...'
        : 'Import des données en cours...'
    };
  }
  if (state.isLoadingData) {
    return { active: true, message: 'Actualisation des données en cours...' };
  }
  if (state.isCreatingObject) {
    return { active: true, message: "Création de l'objet en cours..." };
  }
  if (state.isCreatingLink) {
    return { active: true, message: 'Création du lien en cours...' };
  }
  if (state.isLoadingUsers) {
    return { active: true, message: 'Chargement des utilisateurs en cours...' };
  }
  if (state.isLoadingModelXml) {
    return { active: true, message: 'Chargement du XML du modèle...' };
  }
  if (state.isSavingModelXml) {
    return { active: true, message: 'Sauvegarde du modèle en cours...' };
  }
  if (state.isSavingAccessUser || state.isSavingAccessPermissions || state.isDeletingAccessUser) {
    return { active: true, message: 'Mise à jour des accès en cours...' };
  }
  return { active: false, message: '' };
});

watch(
  () => activeTabs.value,
  (tabs) => {
    if (!tabs.length) {
      return;
    }
    if (!tabs.find((tab) => tab.value === state.currentPage)) {
      state.setCurrentPage(tabs[0].value);
    }
  },
  { immediate: true }
);

watch(
  () => [route.fullPath, state.isAuthenticated, state.modelOptions.length, state.dataObjects.length, state.modelLanguages.length],
  () => {
    applyRouteState();
  },
  { immediate: true }
);

watch(
  () => [
    state.isAuthenticated,
    state.activePortal,
    state.currentPage,
    state.selectedModelKey,
    state.displayLanguage,
    state.selectedRootObjectKey,
    state.tableSearch,
    JSON.stringify(state.tableTypeFilter),
    state.tableAttributeKey,
    state.tableAttributeKeyOperator,
    state.tableAttributeValue,
    state.tableAttributeValueOperator,
    state.fullTextQuery,
    JSON.stringify(state.fullTextTypeFilter)
  ],
  async () => {
    if (isHydratingRoute.value) {
      return;
    }

    const nextLocation = shareableLocation.value;
    const resolved = router.resolve(nextLocation);
    if (resolved.fullPath === route.fullPath) {
      return;
    }

    const sameRouteIdentity = route.name === resolved.name
      && JSON.stringify(route.params || {}) === JSON.stringify(resolved.params || {});

    if (sameRouteIdentity) {
      await router.replace(nextLocation);
      return;
    }

    await router.push(nextLocation);
  },
  { immediate: true }
);

const portalKicker = computed(() => (
  state.isUserPortal ? 'Portail métier' : 'Portail administration du modèle'
));

const portalThemeClass = computed(() => {
  if (state.isUserPortal) {
    return 'portal-user-theme';
  }
  if (state.isModelAdminPortal) {
    return 'portal-admin-theme';
  }
  return 'portal-neutral-theme';
});

const headerTitle = computed(() => {
  if (state.isUserPortal) {
    return state.getUserPortalTitle();
  }
  return 'ExpandProject Studio';
});

const headerSubtitle = computed(() => {
  if (state.isUserPortal) {
    if (!state.selectedModel) {
      return 'Portail métier';
    }
    const version = state.selectedModel.version ? ` v${state.selectedModel.version}` : '';
    return `${state.selectedModel.name}${version}`;
  }
  if (state.isModelAdminPortal) {
    return 'Portail administration du modèle';
  }
  return "IHM connectée à l'API d'import";
});

const heroBgClass = computed(() => {
  if (state.isUserPortal) {
    return 'hero-bg-user';
  }
  if (state.isModelAdminPortal) {
    return 'hero-bg-admin';
  }
  return 'hero-bg-neutral';
});

function openFromAdminMenu(entry) {
  state.setPortal(entry.portal, entry.page);
}

function applyRouteState() {
  const nextRouteState = routeState.value;
  pendingRootObjectKey.value = nextRouteState.page === 'navigate' ? nextRouteState.rootObjectKey : '';

  if (!state.isAuthenticated) {
    return;
  }

  isHydratingRoute.value = true;
  try {
    if (nextRouteState.screen === 'portal-page' && nextRouteState.portal) {
      state.setPortal(nextRouteState.portal, nextRouteState.page);
    } else if (nextRouteState.screen === 'portal-selector' || nextRouteState.screen === 'root') {
      state.clearPortal();
    }

    if (nextRouteState.modelKey && state.modelOptions.some((option) => option.value === nextRouteState.modelKey)) {
      state.selectedModelKey = nextRouteState.modelKey;
    }

    if (nextRouteState.language && state.modelLanguages.some((option) => option.value === nextRouteState.language)) {
      state.displayLanguage = nextRouteState.language;
    }

    if (nextRouteState.page === 'table') {
      state.tableSearch = nextRouteState.tableSearch;
      state.tableTypeFilter = nextRouteState.tableTypes;
      state.tableAttributeKey = nextRouteState.tableAttributeKey;
      state.tableAttributeKeyOperator = nextRouteState.tableAttributeKeyOperator || 'contains';
      state.tableAttributeValue = nextRouteState.tableAttributeValue;
      state.tableAttributeValueOperator = nextRouteState.tableAttributeValueOperator || 'contains';
    }

    if (nextRouteState.page === 'search') {
      state.fullTextQuery = nextRouteState.fullTextQuery;
      state.fullTextTypeFilter = nextRouteState.fullTextTypes;
    }
  } finally {
    isHydratingRoute.value = false;
  }

  if (pendingRootObjectKey.value && state.dataObjects.length) {
    state.setRootObjectByKey(pendingRootObjectKey.value);
    if (state.selectedRootObjectKey === pendingRootObjectKey.value) {
      pendingRootObjectKey.value = '';
    }
  }
}

async function copyShareableLink() {
  try {
    await navigator.clipboard.writeText(shareableUrl.value);
    shareLinkMessage.value = 'Lien de partage copié.';
  } catch (error) {
    shareLinkMessage.value = 'Impossible de copier le lien.';
  }
  shareLinkSnackbar.value = true;
}
</script>
