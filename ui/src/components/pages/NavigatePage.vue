<template>
  <v-row class="mb-6">
    <v-col cols="12">
      <div class="kicker">Explorateur</div>
      <h2 class="headline" style="font-size: clamp(1.6rem, 2.5vw, 2.4rem);">
        Explorez les objets et leurs connexions en toute simplicité.
      </h2>
      <p class="subhead">
        Parcourez l'arborescence, suivez les liens et consultez la fiche d'identité des objets
        sans perdre le contexte du modèle.
      </p>
      <div class="d-flex align-center flex-wrap" style="gap: 8px; margin-top: 12px;">
        <v-chip v-if="state.selectedModel" color="primary" variant="tonal">
          {{ state.selectedModel.name }}<span v-if="state.selectedModel.version"> v{{ state.selectedModel.version }}</span>
        </v-chip>
        <v-chip v-else color="secondary" variant="tonal">Aucun modèle sélectionné</v-chip>
        <v-chip v-if="state.dataSummary" color="accent" variant="tonal">
          {{ state.dataSummary.objectCount }} objets
        </v-chip>
        <v-chip v-if="state.dataSummary" color="secondary" variant="tonal">
          {{ state.dataSummary.linkCount }} liens
        </v-chip>
        <v-chip v-if="state.selectedObject" color="primary" variant="tonal">
          Sélection: {{ state.selectedObject.type }} (ID {{ state.selectedObject.id ?? 'N/A' }})
        </v-chip>
      </div>
    </v-col>
  </v-row>

  <v-row class="navigate-layout">
    <v-col cols="12" lg="4">
      <v-card class="card-animate delay-1 nav-panel" elevation="6" rounded="xl">
        <v-card-title class="section-title d-flex align-center justify-space-between">
          Arborescence
          <v-chip v-if="state.dataSummary" color="primary" variant="tonal" size="small">
            {{ state.dataSummary.objectCount }} objets
          </v-chip>
        </v-card-title>
        <v-card-text>
          <v-text-field
            v-model="state.objectFilter"
            label="Filtrer par type ou ID"
            prepend-icon="mdi-filter-outline"
            variant="outlined"
            density="comfortable"
            clearable
          />

          <div class="tree-section">
            <div class="tree-section-title">Objets</div>
            <v-list
              v-if="state.objectTypeGroups.length"
              density="compact"
              class="tree-list"
              v-model:opened="state.openGroups"
            >
              <v-list-group
                v-for="group in state.objectTypeGroups"
                :key="group.type"
                :value="group.type"
              >
                <template #activator="{ props }">
                  <v-list-item
                    v-bind="props"
                    :title="group.type"
                    :subtitle="`${group.count} objets`"
                  >
                    <template #prepend>
                      <v-icon icon="mdi-shape-outline" />
                    </template>
                  </v-list-item>
                </template>
                <v-list-item
                  v-for="object in group.objects"
                  :key="object.key"
                  :title="state.getObjectPrimaryLabel(object) || object.type"
                  :subtitle="state.getObjectPrimaryLabel(object)
                    ? `Type: ${object.type} • ID: ${object.id ?? 'N/A'}`
                    : `ID: ${object.id ?? 'N/A'}`"
                  :active="state.selectedObject && state.selectedObject.key === object.key"
                  @click="state.selectObject(object)"
                >
                  <template #prepend>
                    <v-icon icon="mdi-cube-outline" />
                  </template>
                  <template #append>
                    <v-chip size="x-small" color="primary" variant="tonal">
                      {{ object.attributes.length }}
                    </v-chip>
                  </template>
                </v-list-item>
              </v-list-group>
            </v-list>
            <div v-else class="text-medium-emphasis">
              Aucun objet importé pour ce modèle.
            </div>
          </div>

          <v-divider class="my-4" />

          <div class="tree-section">
            <div class="tree-section-title">Liens autour de l'objet</div>
            <div v-if="state.selectedObject">
              <div class="tree-subtitle">
                Racine: {{ state.selectedObject.type }} • ID {{ state.selectedObject.id ?? 'N/A' }}
              </div>
              <v-list
                v-if="state.linkedGroups.length"
                density="compact"
                class="tree-list"
                v-model:opened="state.openLinkGroups"
              >
                <v-list-group
                  v-for="group in state.linkedGroups"
                  :key="group.key"
                  :value="group.key"
                >
                  <template #activator="{ props }">
                    <v-list-item
                      v-bind="props"
                      :title="group.title"
                      :subtitle="`${group.items.length} objets`"
                    >
                      <template #prepend>
                        <v-icon :icon="group.icon" />
                      </template>
                    </v-list-item>
                  </template>
                  <v-list-item
                    v-for="relation in group.items"
                    :key="relation.key"
                    :title="state.getObjectPrimaryLabel(relation.target) || relation.target.type"
                    :subtitle="state.getObjectPrimaryLabel(relation.target)
                      ? `Type: ${relation.target.type} • ID: ${relation.target.id ?? 'N/A'}`
                      : `ID: ${relation.target.id ?? 'N/A'}`"
                    @click="state.selectObjectByKey(relation.target.idKey)"
                  >
                    <template #prepend>
                      <v-icon icon="mdi-circle-small" />
                    </template>
                    <template #append>
                      <v-btn
                        icon="mdi-open-in-new"
                        variant="text"
                        size="small"
                        @click.stop="state.selectObjectByKey(relation.target.idKey)"
                      />
                    </template>
                  </v-list-item>
                </v-list-group>
              </v-list>
              <div v-else class="text-medium-emphasis mt-2">
                Aucun lien à afficher pour cet objet.
              </div>
            </div>
            <div v-else class="text-medium-emphasis">
              Sélectionnez un objet pour afficher ses liens directs.
            </div>
          </div>
        </v-card-text>
      </v-card>
    </v-col>

    <v-col cols="12" lg="8">
      <v-card class="card-animate delay-2 identity-panel" elevation="6" rounded="xl">
        <v-card-title class="section-title">Fiche d'identité</v-card-title>
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

            <div class="identity-metrics">
              <div class="metric-card">
                <div class="metric-label">Attributs</div>
                <div class="metric-value">{{ state.selectedObject.attributes.length }}</div>
              </div>
              <div class="metric-card">
                <div class="metric-label">Relations</div>
                <div class="metric-value">{{ state.linkedObjects.length }}</div>
              </div>
              <div class="metric-card">
                <div class="metric-label">Type</div>
                <div class="metric-value">{{ state.selectedObject.type }}</div>
              </div>
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
              Aucun attribut détaillé pour cet objet.
            </div>

            <v-divider class="my-4" />

            <div class="text-subtitle-2 font-weight-bold">Relations directes</div>
            <v-list v-if="state.linkedObjects.length" density="compact" class="mt-2">
              <v-list-item
                v-for="relation in state.linkedObjects"
                :key="relation.key"
                :title="relation.target.type"
                :subtitle="`ID: ${relation.target.id ?? 'N/A'} • ${relation.type}`"
                @click="state.selectObjectByKey(relation.target.idKey)"
              >
                <template #prepend>
                  <v-icon :icon="relation.directionIcon" />
                </template>
                <template #append>
                  <v-chip size="x-small" color="primary" variant="tonal">
                    {{ relation.directionLabel }}
                  </v-chip>
                </template>
              </v-list-item>
            </v-list>
            <div v-else class="text-medium-emphasis mt-2">
              Aucun lien à afficher pour cet objet.
            </div>
          </div>
          <div v-else class="text-medium-emphasis">
            Importez des données pour ce modèle puis sélectionnez un objet dans l'arborescence.
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
