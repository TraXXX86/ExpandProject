<template>
  <v-row>
    <v-col cols="12" md="6">
      <div class="kicker">Import modèle</div>
      <h1 class="headline">Chargez et structurez vos modèles.</h1>
      <p class="subhead">
        Téléversez un fichier de modèle XML et synchronisez la base pour rendre la navigation disponible.
      </p>
      <div class="d-flex flex-wrap" style="gap: 12px; margin-top: 24px;">
        <v-btn
          color="primary"
          size="large"
          prepend-icon="mdi-database-refresh"
          :disabled="!state.canAccessModelAdminPortal"
          @click="state.refreshModels()"
        >
          Rafraîchir la base
        </v-btn>
        <v-btn
          variant="tonal"
          color="primary"
          size="large"
          prepend-icon="mdi-graph-outline"
          :disabled="!state.canAccessModelAdminPortal"
          @click="state.setCurrentPage('model')"
        >
          Visualiser le modèle
        </v-btn>
      </div>
      <v-alert
        class="mt-6"
        type="info"
        variant="tonal"
        density="comfortable"
        icon="mdi-information-outline"
        border="start"
      >
        Cette IHM échange avec l'API Java ({{ state.apiBase }}) pour charger les modèles.
      </v-alert>
    </v-col>

    <v-col cols="12" md="6">
      <v-card class="card-animate" elevation="6" rounded="xl">
        <v-card-title class="section-title">Session d'import</v-card-title>
        <v-card-text>
          <v-select
            v-model="state.selectedModelKey"
            :items="state.modelOptions"
            label="Modèle chargé en base"
            prepend-icon="mdi-database-check"
            variant="outlined"
            density="comfortable"
            :loading="state.isLoadingModels"
            clearable
          />
          <div class="file-hint">
            Sélectionnez un modèle déjà stocké pour naviguer dans les données.
          </div>

          <v-file-input
            class="mt-5"
            label="Fichier de modèle (model.xml)"
            accept=".xml"
            prepend-icon="mdi-file-code"
            variant="outlined"
            density="comfortable"
            @update:model-value="state.handleModelFile"
          />
          <div class="file-hint">Le modèle sera enregistré dans Neo4j.</div>
          <v-btn
            class="mt-3"
            color="primary"
            size="large"
            :loading="state.isLoadingModel"
            :disabled="!state.canUploadModel"
            @click="state.uploadModel"
          >
            Charger le modèle
          </v-btn>

          <v-alert
            v-if="state.status"
            class="mt-5"
            :type="state.status.type"
            variant="tonal"
            density="comfortable"
            border="start"
          >
            {{ state.status.message }}
          </v-alert>
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>

  <v-row class="mt-8">
    <v-col cols="12" md="6">
      <v-card class="card-animate delay-1" elevation="4" rounded="xl">
        <v-card-title class="section-title">Résumé du modèle</v-card-title>
        <v-card-text>
          <div v-if="state.modelSummary">
            <div class="summary-badge text-primary">
              <v-icon icon="mdi-shape-outline" />
              {{ state.modelSummary.objectCount }} types d'objets
            </div>
            <div class="summary-badge text-secondary" style="margin-left: 12px;">
              <v-icon icon="mdi-link-variant" />
              {{ state.modelSummary.linkCount }} types de liens
            </div>
            <v-divider class="my-4" />
            <div class="text-caption text-medium-emphasis">Types détectés</div>
            <v-chip-group column class="mt-2">
              <v-chip
                v-for="type in state.modelSummary.types"
                :key="type.name"
                color="primary"
                variant="tonal"
                class="ma-1"
              >
                <span class="material-symbols-outlined type-icon type-icon-chip" aria-hidden="true">
                  {{ state.getTypeIconName(type.name) }}
                </span>
                {{ type.name }}
                <span v-if="type.parent"> → {{ type.parent }}</span>
              </v-chip>
            </v-chip-group>
            <div v-if="state.modelSummary.hasMore" class="text-caption text-medium-emphasis mt-2">
              + d'autres types non affichés
            </div>
          </div>
          <div v-else class="text-medium-emphasis">
            Aucun modèle chargé pour le moment.
          </div>
        </v-card-text>
      </v-card>
    </v-col>

    <v-col cols="12" md="6">
      <v-card class="card-animate delay-2" elevation="4" rounded="xl">
        <v-card-title class="section-title">Checklist d'héritage</v-card-title>
        <v-card-text>
          <v-row>
            <v-col cols="12" md="4">
              <div class="text-subtitle-2 font-weight-bold">Définition</div>
              <div class="text-medium-emphasis">
                Chaque <code>OBJECT_TYPE</code> peut référencer un parent avec l'attribut <code>PARENT</code>.
              </div>
            </v-col>
            <v-col cols="12" md="4">
              <div class="text-subtitle-2 font-weight-bold">Validation</div>
              <div class="text-medium-emphasis">
                Les attributs hérités sont fusionnés pour les contrôles d'obligation et de type.
              </div>
            </v-col>
            <v-col cols="12" md="4">
              <div class="text-subtitle-2 font-weight-bold">Liens</div>
              <div class="text-medium-emphasis">
                Un lien qui autorise un parent accepte automatiquement ses sous-types.
              </div>
            </v-col>
          </v-row>
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>
</template>

<script setup>
const props = defineProps({
  state: {
    type: Object,
    required: true
  }
});

const state = props.state;
</script>
