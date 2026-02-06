<template>
  <v-app class="app-shell">
    <v-app-bar flat height="72" color="transparent">
      <v-container class="d-flex align-center justify-space-between">
        <div class="d-flex align-center" style="gap: 12px;">
          <v-avatar color="primary" size="40">
            <v-icon icon="mdi-database-eye-outline" color="white" />
          </v-avatar>
          <div>
            <div class="text-subtitle-1 font-weight-bold">ExpandProject Studio</div>
            <div class="text-caption text-medium-emphasis">IHM connectée à l'API d'import</div>
          </div>
        </div>
        <div class="d-flex align-center" style="gap: 8px;">
          <v-select
            v-if="state.modelLanguages.length"
            v-model="state.displayLanguage"
            :items="state.modelLanguages"
            label="Langue"
            density="compact"
            variant="outlined"
            hide-details
            class="language-select"
          />
          <v-chip color="secondary" variant="tonal">XML</v-chip>
          <v-chip color="primary" variant="tonal">Validation</v-chip>
          <v-chip :color="state.neo4jChipColor" variant="tonal">
            Neo4j {{ state.neo4jChipLabel }}
          </v-chip>
        </div>
      </v-container>
    </v-app-bar>

    <v-main>
      <div class="hero-bg" />
      <v-container class="py-6 hero-content">
        <v-row class="mb-6">
          <v-col cols="12">
            <v-tabs v-model="state.currentPage" color="primary" align-tabs="start">
              <v-tab value="navigate">Explorer</v-tab>
              <v-tab value="table">Recherche avancée</v-tab>
              <v-tab value="search">Recherche</v-tab>
              <v-tab value="import-model">Import modèle</v-tab>
              <v-tab value="import-data">Import données</v-tab>
              <v-tab value="model">Modèle</v-tab>
              <v-tab value="admin">Administration</v-tab>
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

          <v-window-item value="model">
            <ModelPage :state="state" />
          </v-window-item>

          <v-window-item value="admin">
            <AdminPage :state="state" />
          </v-window-item>
        </v-window>
      </v-container>
    </v-main>
  </v-app>
</template>

<script setup>
import { reactive } from 'vue';
import { useAppState } from './composables/useAppState';
import AdminPage from './components/pages/AdminPage.vue';
import ImportDataPage from './components/pages/ImportDataPage.vue';
import ImportModelPage from './components/pages/ImportModelPage.vue';
import ModelPage from './components/pages/ModelPage.vue';
import NavigatePage from './components/pages/NavigatePage.vue';
import SearchPage from './components/pages/SearchPage.vue';
import TablePage from './components/pages/TablePage.vue';

const state = reactive(useAppState());
</script>
