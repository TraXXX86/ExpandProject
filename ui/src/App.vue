<template>
  <v-app :class="['app-shell', portalThemeClass]">
    <v-app-bar height="72" color="surface" class="app-header" elevate-on-scroll>
      <v-container class="d-flex align-center justify-space-between">
        <div class="d-flex align-center" style="gap: 12px;">
          <v-avatar color="primary" size="40">
            <v-icon icon="mdi-database-eye-outline" color="white" />
          </v-avatar>
          <div>
            <div class="text-subtitle-1 font-weight-bold">ExpandProject Studio</div>
            <div class="text-caption text-medium-emphasis">IHM connectée à l'API d'import</div>
          </div>
          <v-chip color="primary" variant="tonal" size="small">
            {{ state.portalLabel }}
          </v-chip>
        </div>
        <div class="d-flex align-center flex-wrap justify-end" style="gap: 8px;">
          <v-select
            v-if="state.modelOptions.length && state.isPortalSelected"
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
            v-if="state.modelLanguages.length && state.isPortalSelected"
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
          <v-menu location="bottom end">
            <template #activator="{ props }">
              <v-btn
                v-bind="props"
                color="secondary"
                variant="tonal"
                prepend-icon="mdi-shield-crown-outline"
              >
                Admin plateforme
              </v-btn>
            </template>
            <v-list density="comfortable" min-width="310">
              <v-list-item
                v-for="entry in platformAdminEntries"
                :key="entry.key"
                :prepend-icon="entry.icon"
                :title="entry.title"
                :subtitle="entry.subtitle"
                @click="openFromAdminMenu(entry)"
              />
              <v-divider />
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
            </v-list>
          </v-menu>
        </div>
      </v-container>
    </v-app-bar>

    <v-main>
      <div :class="['hero-bg', heroBgClass]" />
      <v-container class="py-6 hero-content">
        <template v-if="!state.isPortalSelected">
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
                  <div class="text-medium-emphasis">
                    Le menu <strong>Admin plateforme</strong> reste accessible en permanence depuis l'entête.
                  </div>
                  <v-divider class="my-4" />
                  <v-chip :color="state.neo4jChipColor" variant="tonal">
                    Neo4j {{ state.neo4jChipLabel }}
                  </v-chip>
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
                  <v-btn color="primary" size="large" @click="state.setPortal('user')">
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
                  <v-btn color="primary" size="large" @click="state.setPortal('model-admin')">
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
                  <h2 class="headline" style="font-size: clamp(1.6rem, 2.4vw, 2.3rem); margin-bottom: 0.4rem;">
                    {{ portalTitle }}
                  </h2>
                  <p class="subhead" style="max-width: 40rem;">
                    {{ portalDescription }}
                  </p>
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

              <v-tabs v-model="state.currentPage" color="primary" align-tabs="start" class="portal-tabs">
                <v-tab v-for="tab in activeTabs" :key="tab.value" :value="tab.value">
                  {{ tab.title }}
                </v-tab>
              </v-tabs>
            </v-col>
          </v-row>

          <v-window v-model="state.currentPage">
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
  </v-app>
</template>

<script setup>
import { computed, reactive } from 'vue';
import { useAppState } from './composables/useAppState';
import AdminPage from './components/pages/AdminPage.vue';
import ImportDataPage from './components/pages/ImportDataPage.vue';
import ImportModelPage from './components/pages/ImportModelPage.vue';
import ModelPage from './components/pages/ModelPage.vue';
import NavigatePage from './components/pages/NavigatePage.vue';
import SearchPage from './components/pages/SearchPage.vue';
import TablePage from './components/pages/TablePage.vue';
import CreatePage from './components/pages/CreatePage.vue';

const state = reactive(useAppState());

const userPortalTabs = [
  { title: 'Explorer', value: 'navigate' },
  { title: 'Recherche avancée', value: 'table' },
  { title: 'Recherche', value: 'search' },
  { title: 'Import données', value: 'import-data' },
  { title: 'Création', value: 'create' }
];

const modelAdminTabs = [
  { title: 'Modèle', value: 'model' },
  { title: 'Import modèle', value: 'import-model' },
  { title: 'Administration', value: 'admin' }
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

const activeTabs = computed(() => (state.isUserPortal ? userPortalTabs : modelAdminTabs));

const portalKicker = computed(() => (
  state.isUserPortal ? 'Portail métier' : 'Portail administration du modèle'
));

const portalTitle = computed(() => (
  state.isUserPortal
    ? 'Usage des données applicatives'
    : 'Administration du modèle de données'
));

const portalDescription = computed(() => (
  state.isUserPortal
    ? "Espace dédié aux équipes métiers pour consulter et manipuler les données de l'application."
    : 'Espace dédié aux administrateurs pour maintenir et faire évoluer la structure du modèle.'
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
</script>
