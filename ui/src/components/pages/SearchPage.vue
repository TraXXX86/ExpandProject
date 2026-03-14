<template>
  <v-row class="mb-6">
    <v-col cols="12">
      <div class="kicker">Recherche</div>
      <h2 class="headline" style="font-size: clamp(1.6rem, 2.5vw, 2.4rem);">
        Recherche plein texte sur les attributs.
      </h2>
    </v-col>
  </v-row>

  <v-row>
    <v-col cols="12">
      <v-card class="card-animate delay-1" elevation="4" rounded="xl">
        <v-card-title class="section-title d-flex align-center justify-space-between">
          Résultats
          <v-chip v-if="state.fullTextQuery" color="primary" variant="tonal">
            {{ state.fullTextResults.length }} objet(s)
          </v-chip>
        </v-card-title>
        <v-card-text>
          <v-row>
            <v-col cols="12" md="6">
              <v-text-field
                v-model="state.fullTextQuery"
                label="Recherche plein texte"
                prepend-icon="mdi-magnify"
                variant="outlined"
                density="comfortable"
                clearable
                hint="La recherche interroge uniquement les attributs marqués SEARCHABLE côté modèle."
                persistent-hint
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-select
                v-model="state.fullTextTypeFilter"
                :items="state.fullTextTypeOptions"
                label="Types d'objet"
                prepend-icon="mdi-shape-outline"
                variant="outlined"
                density="comfortable"
                multiple
                chips
                clearable
              />
            </v-col>
          </v-row>

          <v-alert
            v-if="state.fullTextStatus"
            :type="state.fullTextStatus.type"
            variant="tonal"
            class="mt-2"
          >
            {{ state.fullTextStatus.message }}
          </v-alert>

          <v-data-table
            :headers="state.fullTextHeaders"
            :items="state.fullTextResults"
            item-key="key"
            density="comfortable"
            class="mt-4"
            :items-per-page="10"
            :loading="state.isSearchingFullText"
          >
            <template #item.type="{ item }">
              <v-chip color="primary" variant="tonal" size="small">
                <span class="material-symbols-outlined type-icon type-icon-chip" aria-hidden="true">
                  {{ state.getTypeIconName(item.type) }}
                </span>
                {{ item.type }}
              </v-chip>
            </template>
            <template #item.matches="{ item }">
              <div class="table-attributes">
                <span v-for="(attr, index) in item.matches" :key="attr.key">
                  {{ state.getAttributeLabel(item.type, attr.key) || attr.key }}: {{ attr.value }}<span v-if="index < item.matches.length - 1"> • </span>
                </span>
              </div>
            </template>
            <template #item.preview="{ item }">
              <div class="table-attributes">
                <span v-if="item.primaryLabel">{{ item.primaryLabel }}</span>
                <span v-else class="text-medium-emphasis">-</span>
              </div>
            </template>
            <template #item.actions="{ item }">
              <v-btn
                size="small"
                color="primary"
                variant="tonal"
                prepend-icon="mdi-open-in-new"
                @click="state.setRootObjectByKey(item.idKey); state.setCurrentPage('navigate')"
              >
                Ouvrir
              </v-btn>
            </template>
            <template #no-data>
              <div class="text-medium-emphasis py-6">
                <template v-if="state.isSearchingFullText">
                  Recherche en cours...
                </template>
                <template v-else-if="state.fullTextQuery">
                  Aucun objet ne correspond à cette recherche.
                </template>
                <template v-else>
                  Saisissez une recherche pour afficher les résultats.
                </template>
              </div>
            </template>
          </v-data-table>
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
