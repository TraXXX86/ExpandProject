<template>
  <v-row class="mb-6">
    <v-col cols="12" md="6">
      <div class="kicker">Creation</div>
      <h1 class="headline">Creez des objets et des liens.</h1>
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
        <v-card-title class="section-title">Modele cible</v-card-title>
        <v-card-text>
          <v-select
            v-model="state.selectedModelKey"
            :items="state.modelOptions"
            label="Modele"
            prepend-icon="mdi-database-check"
            variant="outlined"
            density="comfortable"
            :loading="state.isLoadingModels"
            clearable
          />
          <div class="file-hint">
            Selectionnez un modele pour charger les types et vos objets existants.
          </div>
          <div class="d-flex align-center" style="gap: 12px; margin-top: 16px;">
            <v-btn
              variant="tonal"
              color="secondary"
              size="large"
              :disabled="!state.selectedModelKey || !state.canReadCurrentModelData"
              @click="state.refreshData(state.selectedModelKey)"
            >
              Rafraichir les donnees
            </v-btn>
            <div v-if="state.dataSummary" class="text-caption text-medium-emphasis">
              {{ state.dataSummary.objectCount }} objets, {{ state.dataSummary.linkCount }} liens
            </div>
          </div>
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>

  <v-row>
    <v-col cols="12" md="7">
      <v-card class="card-animate delay-1" elevation="4" rounded="xl">
        <v-card-title class="section-title">Creer un objet</v-card-title>
        <v-card-text>
          <v-select
            v-model="state.createObjectType"
            :items="state.createObjectTypeOptions"
            label="Type d'objet"
            prepend-icon="mdi-shape-outline"
            variant="outlined"
            density="comfortable"
            clearable
          />

          <div v-if="state.createObjectAttributeDefs.length" class="mt-4">
            <div class="text-subtitle-2 font-weight-bold">Attributs</div>
            <v-row class="mt-2">
              <v-col
                v-for="attribute in state.createObjectAttributeDefs"
                :key="attribute.name"
                cols="12"
                md="6"
              >
                <v-text-field
                  v-model="state.createObjectAttributes[attribute.name]"
                  :label="attribute.required ? `${attribute.label} *` : attribute.label"
                  :hint="`Type: ${attribute.type || 'STRING'}`"
                  persistent-hint
                  variant="outlined"
                  density="comfortable"
                />
              </v-col>
            </v-row>
          </div>
          <div v-else class="text-medium-emphasis mt-4">
            Selectionnez un type pour afficher les attributs disponibles.
          </div>

          <div class="d-flex align-center" style="gap: 12px; margin-top: 16px;">
            <v-btn
              color="primary"
              size="large"
              :loading="state.isCreatingObject"
              :disabled="!state.createObjectType || !state.selectedModelKey || !state.canCreateCurrentModelData"
              @click="state.createObject"
            >
              Creer l'objet
            </v-btn>
          </div>

          <v-alert
            v-if="state.createObjectStatus"
            class="mt-4"
            :type="state.createObjectStatus.type"
            variant="tonal"
            density="comfortable"
            border="start"
          >
            {{ state.createObjectStatus.message }}
          </v-alert>
        </v-card-text>
      </v-card>
    </v-col>

    <v-col cols="12" md="5">
      <v-card class="card-animate delay-2" elevation="4" rounded="xl">
        <v-card-title class="section-title">Conseils</v-card-title>
        <v-card-text>
          <div class="text-subtitle-2 font-weight-bold">Attributs requis</div>
          <div class="text-medium-emphasis">
            Les champs marques d'un asterisque sont obligatoires selon le modele.
          </div>
          <v-divider class="my-4" />
          <div class="text-subtitle-2 font-weight-bold">Valeurs par defaut</div>
          <div class="text-medium-emphasis">
            Les valeurs par defaut definies dans le modele sont pre-remplies dans les champs.
          </div>
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>

  <v-row class="mt-8">
    <v-col cols="12" md="7">
      <v-card class="card-animate delay-1" elevation="4" rounded="xl">
        <v-card-title class="section-title">Creer un lien</v-card-title>
        <v-card-text>
          <v-select
            v-model="state.createLinkType"
            :items="state.createLinkTypeOptions"
            label="Type de lien"
            prepend-icon="mdi-link-variant"
            variant="outlined"
            density="comfortable"
            clearable
          />

          <div v-if="state.createLinkDefinition" class="mt-3">
            <div class="text-caption text-medium-emphasis">Sources autorisees</div>
            <v-chip-group column class="mt-1">
              <v-chip
                v-for="source in (state.createLinkDefinition.sources || [])"
                :key="source"
                color="primary"
                variant="tonal"
                class="ma-1"
              >
                {{ source }}
              </v-chip>
            </v-chip-group>
            <div class="text-caption text-medium-emphasis mt-3">Cibles autorisees</div>
            <v-chip-group column class="mt-1">
              <v-chip
                v-for="target in (state.createLinkDefinition.targets || [])"
                :key="target"
                color="secondary"
                variant="tonal"
                class="ma-1"
              >
                {{ target }}
              </v-chip>
            </v-chip-group>
          </div>

          <v-row class="mt-4">
            <v-col cols="12" md="6">
              <v-autocomplete
                v-model="state.createLinkSourceId"
                v-model:search="state.createLinkSourceQuery"
                :items="state.createLinkSourceOptions"
                label="Objet source"
                prepend-icon="mdi-source-branch"
                variant="outlined"
                density="comfortable"
                clearable
                :loading="state.isLoadingCreateLinkSources"
                :disabled="!state.createLinkType || !state.canCreateCurrentModelData"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-autocomplete
                v-model="state.createLinkTargetId"
                v-model:search="state.createLinkTargetQuery"
                :items="state.createLinkTargetOptions"
                label="Objet cible"
                prepend-icon="mdi-target"
                variant="outlined"
                density="comfortable"
                clearable
                :loading="state.isLoadingCreateLinkTargets"
                :disabled="!state.createLinkType || !state.canCreateCurrentModelData"
              />
            </v-col>
          </v-row>

          <div class="d-flex align-center" style="gap: 12px; margin-top: 16px;">
            <v-btn
              color="primary"
              size="large"
              :loading="state.isCreatingLink"
              :disabled="!state.createLinkType || !state.createLinkSourceId || !state.createLinkTargetId || !state.canCreateCurrentModelData"
              @click="state.createLink"
            >
              Creer le lien
            </v-btn>
          </div>

          <v-alert
            v-if="state.createLinkStatus"
            class="mt-4"
            :type="state.createLinkStatus.type"
            variant="tonal"
            density="comfortable"
            border="start"
          >
            {{ state.createLinkStatus.message }}
          </v-alert>
        </v-card-text>
      </v-card>
    </v-col>

    <v-col cols="12" md="5">
      <v-card class="card-animate delay-2" elevation="4" rounded="xl">
        <v-card-title class="section-title">Disponibilite</v-card-title>
        <v-card-text>
          <div class="text-subtitle-2 font-weight-bold">Objets existants</div>
          <div class="text-medium-emphasis">
            Les listes affichent uniquement les objets compatibles avec le type de lien.
          </div>
          <v-divider class="my-4" />
          <div class="text-subtitle-2 font-weight-bold">Mise a jour</div>
          <div class="text-medium-emphasis">
            Pensez a rafraichir les donnees si vous venez d'importer un nouveau fichier.
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
