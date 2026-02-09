<template>
  <v-row>
    <v-col cols="12" md="6">
      <div class="kicker">Import données</div>
      <h1 class="headline">Importez et validez les données.</h1>
      <v-alert
        v-if="!state.canCreateCurrentModelData"
        class="mt-4"
        type="warning"
        variant="tonal"
        density="comfortable"
        border="start"
      >
        Votre profil n'a pas le droit CREATE sur ce modèle.
      </v-alert>
    </v-col>

    <v-col cols="12" md="6">
      <v-card class="card-animate" elevation="6" rounded="xl">
        <v-card-title class="section-title">Session de données</v-card-title>
        <v-card-text>
          <v-select
            v-model="state.selectedModelKey"
            :items="state.modelOptions"
            label="Modèle cible"
            prepend-icon="mdi-database-check"
            variant="outlined"
            density="comfortable"
            :loading="state.isLoadingModels"
            clearable
          />
          <div class="file-hint">
            Sélectionnez un modèle chargé en base pour importer les données.
          </div>

          <v-file-input
            class="mt-5"
            label="Fichier de données (data.xml)"
            accept=".xml"
            prepend-icon="mdi-file-tree"
            variant="outlined"
            density="comfortable"
            @update:model-value="state.handleDataFile"
          />
          <div class="file-hint">
            Les données seront importées pour le modèle sélectionné (sauf si validation uniquement).
          </div>

          <v-switch
            class="mt-4"
            v-model="state.validateOnly"
            label="Validation uniquement (sans import Neo4j)"
            color="primary"
            inset
          />

          <div class="d-flex align-center" style="gap: 12px;">
            <v-btn
              color="primary"
              size="large"
              :loading="state.isLoadingData"
              :disabled="!state.canUploadData"
              @click="state.uploadData"
            >
              {{ state.validateOnly ? 'Valider les données' : 'Importer les données' }}
            </v-btn>
            <v-btn
              variant="tonal"
              color="secondary"
              size="large"
              :disabled="!state.selectedModelKey || !state.canReadCurrentModelData"
              @click="state.refreshData(state.selectedModelKey)"
            >
              Rafraîchir
            </v-btn>
          </div>

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
        <v-card-title class="section-title">Résumé des données</v-card-title>
        <v-card-text>
          <div v-if="state.dataSummary">
            <div class="summary-badge text-primary">
              <v-icon icon="mdi-cube-outline" />
              {{ state.dataSummary.objectCount }} objets
            </div>
            <div class="summary-badge text-secondary" style="margin-left: 12px;">
              <v-icon icon="mdi-connection" />
              {{ state.dataSummary.linkCount }} liens
            </div>
            <v-divider class="my-4" />
            <div class="text-caption text-medium-emphasis">Types d'objets rencontrés</div>
            <v-chip-group column class="mt-2">
              <v-chip
                v-for="type in state.dataSummary.objectTypes"
                :key="type"
                color="accent"
                variant="tonal"
                class="ma-1"
              >
                <span class="material-symbols-outlined type-icon type-icon-chip" aria-hidden="true">
                  {{ state.getTypeIconName(type) }}
                </span>
                {{ type }}
              </v-chip>
            </v-chip-group>
            <div v-if="state.dataSummary.hasMore" class="text-caption text-medium-emphasis mt-2">
              + d'autres types non affichés
            </div>
          </div>
          <div v-else class="text-medium-emphasis">
            Aucune donnée importée pour le moment.
          </div>
        </v-card-text>
      </v-card>
    </v-col>

    <v-col cols="12" md="6">
      <v-card class="card-animate delay-2" elevation="4" rounded="xl">
        <v-card-title class="section-title">Rappels</v-card-title>
        <v-card-text>
          <div class="text-subtitle-2 font-weight-bold">Conseil</div>
          <div class="text-medium-emphasis">
            Utilisez la validation seule pour vérifier vos données avant import effectif.
          </div>
          <v-divider class="my-4" />
          <div class="text-subtitle-2 font-weight-bold">Synchronisation</div>
          <div class="text-medium-emphasis">
            Les objets sont visibles dans l'explorateur une fois importés et liés au modèle sélectionné.
          </div>
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
