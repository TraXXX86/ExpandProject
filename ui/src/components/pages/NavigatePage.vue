<template>
  <v-row class="mb-6">
    <v-col cols="12">
      <div class="kicker">Explorateur</div>
      <h2 class="headline" style="font-size: clamp(1.6rem, 2.5vw, 2.4rem);">
        Explorez les objets et leurs connexions de maniere recursive.
      </h2>
      <div class="d-flex align-center flex-wrap" style="gap: 8px; margin-top: 12px;">
        <v-chip v-if="state.selectedModel" color="primary" variant="tonal">
          {{ state.selectedModel.name }}<span v-if="state.selectedModel.version"> v{{ state.selectedModel.version }}</span>
        </v-chip>
        <v-chip v-else color="secondary" variant="tonal">Aucun modele selectionne</v-chip>
        <v-chip v-if="state.dataSummary" color="accent" variant="tonal">
          {{ state.dataSummary.objectCount }} objets
        </v-chip>
        <v-chip v-if="state.dataSummary" color="secondary" variant="tonal">
          {{ state.dataSummary.linkCount }} liens
        </v-chip>
        <v-chip v-if="state.selectedRootObject" color="secondary" variant="tonal">
          Racine: {{ state.selectedRootObject.type }} (ID {{ state.selectedRootObject.id ?? 'N/A' }})
        </v-chip>
        <v-chip v-if="state.selectedObject" color="primary" variant="tonal">
          <span class="material-symbols-outlined type-icon type-icon-chip" aria-hidden="true">
            {{ state.getTypeIconName(state.selectedObject.type) }}
          </span>
          Selection: {{ state.selectedObject.type }} (ID {{ state.selectedObject.id ?? 'N/A' }})
        </v-chip>
      </div>
    </v-col>
  </v-row>

  <v-row class="navigate-layout">
    <v-col cols="12" lg="5">
      <v-card class="card-animate delay-1 nav-panel" elevation="6" rounded="xl">
        <v-card-title class="section-title d-flex align-center justify-space-between">
          Arborescence recursive
          <v-chip v-if="state.dataSummary" color="primary" variant="tonal" size="small">
            {{ state.dataSummary.objectCount }} objets
          </v-chip>
        </v-card-title>
        <v-card-text>
          <v-autocomplete
            v-model="state.selectedRootObjectKey"
            v-model:search="state.rootObjectQuery"
            :items="state.rootObjectOptions"
            item-title="title"
            item-value="value"
            label="Rechercher la racine"
            prepend-icon="mdi-magnify"
            variant="outlined"
            density="comfortable"
            clearable
            no-filter
            :disabled="!state.dataObjects.length"
            @update:model-value="state.setRootObjectByKey"
          >
            <template #item="{ props, item }">
              <v-list-item v-bind="props" :subtitle="item.raw.subtitle" />
            </template>
          </v-autocomplete>

          <div class="explorer-root-hint">
            Clic droit sur un noeud pour choisir les liens a parcourir, puis utilisez la fleche
            pour deplier les objets enfants.
          </div>

          <div class="d-flex align-center justify-space-between mt-2" style="gap: 8px; flex-wrap: wrap;">
            <v-switch
              v-model="state.showCycleDetection"
              color="secondary"
              hide-details
              density="compact"
              label="Afficher detection des cycles"
            />
            <v-btn
              size="small"
              variant="tonal"
              color="secondary"
              prepend-icon="mdi-restore"
              :disabled="!state.explorerTree"
              @click="state.resetExplorerTraversal"
            >
              Reinitialiser le parcours
            </v-btn>
          </div>

          <v-divider class="my-4" />

          <div class="tree-section">
            <div class="tree-section-title">Parcours</div>
            <div v-if="state.explorerTree" class="explorer-tree-wrap">
              <ExplorerRecursiveNode :node="state.explorerTree" :state="state" />
            </div>
            <div v-else class="text-medium-emphasis">
              Selectionnez un objet racine pour afficher l'arborescence.
            </div>
          </div>
        </v-card-text>
      </v-card>
    </v-col>

    <v-col cols="12" lg="7">
      <v-card class="card-animate delay-2 identity-panel" elevation="6" rounded="xl">
        <v-card-title class="section-title">Fiche d'identite</v-card-title>
        <v-card-text>
          <div v-if="state.selectedObject">
            <div class="identity-header">
              <div>
                <div class="identity-title">
                  {{ state.getObjectPrimaryLabel(state.selectedObject) || state.selectedObject.type }}
                </div>
                <div class="identity-subtitle">
                  {{ state.getObjectPrimaryLabel(state.selectedObject)
                    ? `Type: ${state.selectedObject.type} • ID: ${state.selectedObject.id ?? 'N/A'}`
                    : `ID: ${state.selectedObject.id ?? 'N/A'}` }}
                </div>
              </div>
              <v-chip color="secondary" variant="tonal">
                {{ state.linkedObjects.length }} liens
              </v-chip>
            </div>

            <div v-if="state.getObjectPrimaryAttributes(state.selectedObject).length" class="mt-4">
              <div class="text-subtitle-2 font-weight-bold">Attributs principaux</div>
              <div class="d-flex flex-wrap" style="gap: 8px; margin-top: 6px;">
                <v-chip
                  v-for="attribute in state.getObjectPrimaryAttributes(state.selectedObject)"
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
            <div v-if="state.attributeTabs.length" class="mt-2">
              <v-tabs
                v-model="state.selectedAttributeTab"
                color="primary"
                class="identity-tabs"
                show-arrows
              >
                <v-tab v-for="tab in state.attributeTabs" :key="tab.key" :value="tab.key">
                  {{ tab.label }}
                </v-tab>
              </v-tabs>
              <v-window v-model="state.selectedAttributeTab" class="mt-2">
                <v-window-item v-for="tab in state.attributeTabs" :key="tab.key" :value="tab.key">
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
              Aucun attribut detaille pour cet objet.
            </div>

            <v-divider class="my-4" />

            <div class="text-subtitle-2 font-weight-bold">Relations directes</div>
            <div v-if="state.linkedRelationTabs.length" class="mt-2">
              <v-tabs
                v-model="state.selectedLinkedRelationTab"
                color="primary"
                class="identity-tabs"
                show-arrows
              >
                <v-tab v-for="tab in state.linkedRelationTabs" :key="tab.key" :value="tab.key">
                  {{ tab.label }} ({{ tab.count }})
                </v-tab>
              </v-tabs>
              <v-window v-model="state.selectedLinkedRelationTab" class="mt-2">
                <v-window-item
                  v-for="tab in state.linkedRelationTabs"
                  :key="tab.key"
                  :value="tab.key"
                >
                  <div v-if="tab.items.length" class="identity-linked-list">
                    <button
                      v-for="relation in tab.items"
                      :key="relation.key"
                      type="button"
                      class="identity-linked-item"
                      :title="state.formatObjectOptionLabel(relation.target)"
                      @click="state.selectObjectByKey(relation.target.idKey)"
                    >
                      <div class="explorer-node-row">
                        <span class="material-symbols-outlined type-icon explorer-node-icon" aria-hidden="true">
                          {{ state.getTypeIconName(relation.target.type) }}
                        </span>
                        <div class="explorer-node-primary">
                          <template v-if="state.getObjectPrimaryAttributes(relation.target).length">
                            <span
                              v-for="attribute in state.getObjectPrimaryAttributes(relation.target)"
                              :key="attribute.key"
                              class="explorer-primary-value"
                            >
                              {{ attribute.value }}
                            </span>
                          </template>
                          <span v-else class="explorer-primary-empty">
                            Aucun attribut principal
                          </span>
                        </div>
                      </div>
                    </button>
                  </div>
                  <div v-else class="text-medium-emphasis mt-2">
                    Aucun objet lie pour ce type de lien.
                  </div>
                </v-window-item>
              </v-window>
            </div>
            <div v-else class="text-medium-emphasis mt-2">
              Aucun lien a afficher pour cet objet.
            </div>
          </div>
          <div v-else class="text-medium-emphasis">
            Importez des donnees pour ce modele puis selectionnez un objet dans l'arborescence.
          </div>
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>
</template>

<script setup>
import ExplorerRecursiveNode from './ExplorerRecursiveNode.vue';

const props = defineProps({
  state: {
    type: Object,
    required: true
  }
});

const state = props.state;
</script>
