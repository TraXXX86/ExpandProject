<template>
  <v-row class="mb-6">
    <v-col cols="12">
      <div class="kicker">Recherche avancée</div>
      <h2 class="headline" style="font-size: clamp(1.6rem, 2.5vw, 2.4rem);">
        Listez les objets et filtrez rapidement.
      </h2>
    </v-col>
  </v-row>

  <v-row>
    <v-col cols="12" :lg="state.tableHasDetails ? 8 : 12">
      <v-card class="card-animate delay-1" elevation="4" rounded="xl">
        <v-card-title class="section-title d-flex align-center justify-space-between">
          Objets importés
          <div class="d-flex align-center" style="gap: 8px;">
            <v-chip v-if="state.dataSummary" color="primary" variant="tonal">
              {{ state.tableTotalCount }} / {{ state.dataSummary.objectCount }} objets
            </v-chip>
            <v-btn
              v-if="state.tableSelectedObject && !state.tableHasDetails"
              size="small"
              variant="tonal"
              color="secondary"
              prepend-icon="mdi-eye-outline"
              @click="state.showTableDetailPanel = true"
            >
              Afficher details
            </v-btn>
          </div>
        </v-card-title>
        <v-card-text>
          <v-row>
            <v-col cols="12" md="4">
              <v-select
                v-model="state.tableTypeFilter"
                :items="state.tableTypeOptions"
                label="Types d'objet"
                prepend-icon="mdi-shape-outline"
                variant="outlined"
                density="comfortable"
                multiple
                chips
                clearable
              />
            </v-col>
            <v-col cols="12" md="4">
              <v-text-field
                v-model="state.tableSearch"
                label="Recherche (type, ID ou attribut)"
                prepend-icon="mdi-magnify"
                variant="outlined"
                density="comfortable"
                clearable
              />
            </v-col>
            <v-col cols="12" md="4">
              <v-select
                v-model="state.tableAttributeKeyOperator"
                :items="state.tableOperatorOptions"
                label="Operateur (clé)"
                prepend-icon="mdi-tune-variant"
                variant="outlined"
                density="comfortable"
              />
            </v-col>
          </v-row>

          <v-row>
            <v-col cols="12" md="4">
              <v-text-field
                v-model="state.tableAttributeKey"
                label="Attribut (clé)"
                prepend-icon="mdi-key-outline"
                variant="outlined"
                density="comfortable"
                clearable
              />
            </v-col>
            <v-col cols="12" md="4">
              <v-select
                v-model="state.tableAttributeValueOperator"
                :items="state.tableOperatorOptions"
                label="Operateur (valeur)"
                prepend-icon="mdi-tune-variant"
                variant="outlined"
                density="comfortable"
              />
            </v-col>
            <v-col cols="12" md="4">
              <v-text-field
                v-model="state.tableAttributeValue"
                label="Attribut (valeur)"
                prepend-icon="mdi-text-box-search-outline"
                variant="outlined"
                density="comfortable"
                clearable
              />
            </v-col>
          </v-row>

          <v-data-table
            :headers="state.tableHeaders"
            :items="state.filteredTableRows"
            item-key="key"
            density="comfortable"
            class="mt-4"
            :loading="state.isLoadingTable"
            hide-default-footer
          >
            <template #item.type="{ item }">
              <v-chip color="primary" variant="tonal" size="small">
                <span class="material-symbols-outlined type-icon type-icon-chip" aria-hidden="true">
                  {{ state.getTypeIconName(item.type) }}
                </span>
                {{ item.type }}
              </v-chip>
            </template>
            <template #item.attributes="{ item }">
              <div class="table-attributes">
                <span v-for="(attr, index) in item.attributePreview" :key="attr.key">
                  {{ attr.label || attr.key }}: {{ attr.value }}<span v-if="index < item.attributePreview.length - 1"> • </span>
                </span>
                <span v-if="item.attributeCount > item.attributePreview.length">
                  • +{{ item.attributeCount - item.attributePreview.length }}
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
              <div class="d-flex align-center" style="gap: 6px;">
                <v-btn
                  size="small"
                  color="primary"
                  variant="tonal"
                  prepend-icon="mdi-eye-outline"
                  @click="state.viewObjectFromTable(item.idKey)"
                >
                  Voir
                </v-btn>
                <v-btn
                  v-if="state.canDeleteCurrentModelData"
                  size="small"
                  color="error"
                  variant="tonal"
                  prepend-icon="mdi-delete-outline"
                  @click="state.deleteObject(item)"
                >
                  Supprimer
                </v-btn>
              </div>
            </template>
            <template #no-data>
              <div class="text-medium-emphasis py-6">
                Aucun objet ne correspond aux filtres.
              </div>
            </template>
          </v-data-table>

          <div class="d-flex align-center justify-space-between flex-wrap mt-4" style="gap: 12px;">
            <div class="text-caption text-medium-emphasis">
              {{ state.tableTotalCount }} resultat(s) • page {{ state.tablePage }} / {{ state.tablePageCount }}
            </div>
            <div class="d-flex align-center flex-wrap justify-end" style="gap: 12px;">
              <v-select
                v-model="state.tableItemsPerPage"
                :items="[10, 25, 50, 100].map((value) => ({ title: `${value} / page`, value }))"
                label="Pagination"
                density="compact"
                variant="outlined"
                hide-details
                style="max-width: 140px;"
              />
              <v-pagination
                v-model="state.tablePage"
                :length="state.tablePageCount"
                :total-visible="7"
                density="comfortable"
              />
            </div>
          </div>
        </v-card-text>
      </v-card>
    </v-col>

    <v-col v-if="state.tableHasDetails" cols="12" lg="4">
      <v-card class="card-animate delay-2 table-detail-panel" elevation="4" rounded="xl">
        <v-card-title class="section-title d-flex align-center justify-space-between">
          Détails
          <div class="d-flex align-center" style="gap: 6px;">
            <v-btn
              size="small"
              variant="tonal"
              color="primary"
              prepend-icon="mdi-open-in-new"
              @click="state.openInExplorerFromTable"
            >
              Ouvrir dans l'explorateur
            </v-btn>
            <v-btn
              icon="mdi-close"
              variant="text"
              @click="state.showTableDetailPanel = false"
              aria-label="Masquer le panneau"
            />
          </div>
        </v-card-title>
        <v-card-text>
            <div v-if="state.tableSelectedObject">
              <div class="d-flex align-center" style="gap: 12px; flex-wrap: wrap;">
                <v-chip color="primary" variant="tonal">
                  <span class="material-symbols-outlined type-icon type-icon-chip" aria-hidden="true">
                    {{ state.getTypeIconName(state.tableSelectedObject.type) }}
                  </span>
                  {{ state.tableSelectedObject.type }}
                </v-chip>
              <v-chip color="secondary" variant="tonal">
                ID: {{ state.tableSelectedObject.id ?? 'N/A' }}
              </v-chip>
                <v-chip color="accent" variant="tonal">
                  {{ state.tableSelectedObject.attributes.length }} attributs
                </v-chip>
              </div>

              <div v-if="primaryAttributes.length" class="mt-3">
                <div class="text-subtitle-2 font-weight-bold">Attributs principaux</div>
                <div class="d-flex flex-wrap" style="gap: 8px; margin-top: 6px;">
                  <v-chip
                    v-for="attribute in primaryAttributes"
                    :key="attribute.key"
                    color="secondary"
                    variant="tonal"
                    size="small"
                  >
                    {{ attribute.label }} : {{ attribute.value }}
                  </v-chip>
                </div>
              </div>

              <v-divider class="my-4" />

            <div class="text-subtitle-2 font-weight-bold">Attributs</div>
            <div v-if="state.tableAttributeTabs.length" class="mt-2">
              <v-tabs
                v-model="state.tableSelectedAttributeTab"
                color="primary"
                class="identity-tabs"
                show-arrows
              >
                <v-tab v-for="tab in state.tableAttributeTabs" :key="tab.key" :value="tab.key">
                  {{ tab.label }}
                </v-tab>
              </v-tabs>
              <v-window v-model="state.tableSelectedAttributeTab" class="mt-2">
                <v-window-item v-for="tab in state.tableAttributeTabs" :key="tab.key" :value="tab.key">
                  <v-table
                    v-if="tab.attributes && tab.attributes.length"
                    class="mt-2"
                    density="compact"
                  >
                    <thead>
                      <tr>
                        <th>Attribut</th>
                        <th>Valeur</th>
                        <th>Type</th>
                        <th>Requis</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="attribute in tab.attributes" :key="attribute.key">
                        <td>
                          <div class="attr-label">{{ attribute.label }}</div>
                          <div v-if="attribute.label !== attribute.key" class="attr-key">{{ attribute.key }}</div>
                        </td>
                        <td>{{ attribute.value || '-' }}</td>
                        <td>{{ attribute.type || '-' }}</td>
                        <td>{{ attribute.required ? 'Oui' : 'Non' }}</td>
                      </tr>
                    </tbody>
                  </v-table>
                  <div v-else class="text-medium-emphasis mt-2">
                    Aucun attribut pour ce groupe.
                  </div>
                </v-window-item>
              </v-window>
            </div>
            <div v-else class="text-medium-emphasis mt-2">
              Aucun attribut pour cet objet.
            </div>

            <v-divider class="my-4" />

            <div class="text-subtitle-2 font-weight-bold">Liens</div>
            <v-list v-if="state.tableSelectedLinks.length" density="compact" class="mt-2">
              <v-list-item
                v-for="relation in state.tableSelectedLinks"
                :key="relation.key"
                :title="state.getObjectPrimaryLabel(relation.target) || relation.target.type"
                :subtitle="`Type: ${relation.target.type} • ID: ${relation.target.id ?? 'N/A'} • ${relation.type} • ${relation.directionLabel}`"
                @click="state.viewObjectFromTable(relation.target.idKey)"
              >
                <template #prepend>
                  <v-icon :icon="relation.directionIcon" />
                </template>
              </v-list-item>
            </v-list>
            <div v-else class="text-medium-emphasis mt-2">
              Aucun lien à afficher pour cet objet.
            </div>
          </div>
          <div v-else class="text-medium-emphasis">
            Cliquez sur “Voir” pour afficher les détails d'un objet.
          </div>
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  state: {
    type: Object,
    required: true
  }
});

const state = props.state;
const primaryAttributes = computed(() => state.getObjectPrimaryAttributes(state.tableSelectedObject));
</script>
