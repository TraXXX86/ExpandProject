<template>
  <v-row class="mb-6">
    <v-col cols="12">
      <div class="kicker">Modèle</div>
      <h2 class="headline" style="font-size: clamp(1.6rem, 2.5vw, 2.4rem);">
        Visualisez les types, attributs et liens du modèle.
      </h2>
      <p class="subhead">
        Cette page synthétise la définition du modèle chargé pour vous permettre de comprendre
        rapidement sa structure.
      </p>
      <div class="d-flex align-center" style="gap: 8px; margin-top: 12px;">
        <v-chip v-if="state.selectedModel" color="primary" variant="tonal">
          {{ state.selectedModel.name }}<span v-if="state.selectedModel.version"> v{{ state.selectedModel.version }}</span>
        </v-chip>
        <v-chip v-else color="secondary" variant="tonal">Aucun modèle sélectionné</v-chip>
      </div>
    </v-col>
  </v-row>

  <v-row>
    <v-col cols="12" md="4">
      <v-card class="card-animate delay-1" elevation="4" rounded="xl">
        <v-card-title class="section-title">Types d'objets</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="state.modelObjectFilter"
            label="Filtrer un type d'objet"
            prepend-icon="mdi-filter-outline"
            variant="outlined"
            density="comfortable"
            clearable
          />
          <v-list v-if="state.filteredModelObjects.length" density="compact">
            <v-list-item
              v-for="type in state.filteredModelObjects"
              :key="type.key"
              :title="type.name"
              :subtitle="type.parent ? `Parent: ${type.parent}` : 'Sans parent'"
              :active="state.selectedModelObject && state.selectedModelObject.key === type.key"
              @click="state.selectedModelObject = type"
            >
              <template #prepend>
                <span class="material-symbols-outlined type-icon" aria-hidden="true">
                  {{ state.getTypeIconName(type.name) }}
                </span>
              </template>
            </v-list-item>
          </v-list>
          <div v-else class="text-medium-emphasis">
            Aucun type d'objet détecté.
          </div>
        </v-card-text>
      </v-card>
    </v-col>

    <v-col cols="12" md="8">
      <v-card class="card-animate delay-2" elevation="4" rounded="xl">
        <v-card-title class="section-title">Détails du type</v-card-title>
        <v-card-text>
          <div v-if="state.selectedModelObject">
            <div class="d-flex align-center" style="gap: 12px; flex-wrap: wrap;">
              <v-chip color="primary" variant="tonal">
                <span class="material-symbols-outlined type-icon type-icon-chip" aria-hidden="true">
                  {{ state.getTypeIconName(state.selectedModelObject.name) }}
                </span>
                {{ state.selectedModelObject.name }}
              </v-chip>
              <v-chip v-if="state.selectedModelObject.parent" color="secondary" variant="tonal">
                Parent: {{ state.selectedModelObject.parent }}
              </v-chip>
              <v-chip color="accent" variant="tonal">
                {{ state.selectedModelObject.attributes.length }} attributs
              </v-chip>
            </div>
            <div v-if="state.selectedModelObject.description" class="mt-3 text-medium-emphasis">
              {{ state.selectedModelObject.description }}
            </div>

            <v-divider class="my-4" />

            <div class="text-subtitle-2 font-weight-bold">Attributs</div>
            <v-table v-if="state.selectedModelObject.attributes.length" class="mt-2" density="compact">
              <thead>
                <tr>
                  <th>Attribut</th>
                  <th>Type</th>
                  <th>Obligatoire</th>
                  <th>Valeur par défaut</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="attribute in state.selectedModelObject.attributes" :key="attribute.name">
                  <td>
                    <div class="attr-label">{{ state.formatAttributeLabel(attribute.name, attribute) }}</div>
                    <div
                      v-if="state.formatAttributeLabel(attribute.name, attribute) !== attribute.name"
                      class="attr-key"
                    >
                      {{ attribute.name }}
                    </div>
                  </td>
                  <td>{{ attribute.type }}</td>
                  <td>{{ attribute.required ? 'Oui' : 'Non' }}</td>
                  <td>{{ attribute.defaultValue || '-' }}</td>
                </tr>
              </tbody>
            </v-table>
            <div v-else class="text-medium-emphasis mt-2">
              Aucun attribut défini pour ce type.
            </div>

            <v-divider class="my-4" />

            <div class="text-subtitle-2 font-weight-bold">Attributs représentatifs</div>
            <div v-if="representativeAttributes.length" class="mt-2">
              <v-chip-group column>
                <v-chip
                  v-for="attribute in representativeAttributes"
                  :key="attribute.name"
                  color="secondary"
                  variant="tonal"
                  class="ma-1"
                >
                  {{ attribute.label }}
                </v-chip>
              </v-chip-group>
            </div>
            <div v-else class="text-medium-emphasis mt-2">
              Aucun attribut représentatif défini pour ce type.
            </div>

            <v-divider class="my-4" />

            <div class="text-subtitle-2 font-weight-bold">Groupes d'attributs</div>
            <div v-if="state.modelGroupTabs.length" class="mt-2">
              <v-tabs
                v-model="state.selectedModelGroupTab"
                color="primary"
                class="model-tabs"
                show-arrows
              >
                <v-tab v-for="tab in state.modelGroupTabs" :key="tab.key" :value="tab.key">
                  {{ tab.label }}
                </v-tab>
              </v-tabs>
              <v-window v-model="state.selectedModelGroupTab" class="mt-2">
                <v-window-item v-for="tab in state.modelGroupTabs" :key="tab.key" :value="tab.key">
                  <v-table
                    v-if="tab.attributes && tab.attributes.length"
                    class="mt-2"
                    density="compact"
                  >
                    <thead>
                      <tr>
                        <th>Attribut</th>
                        <th>Ordre</th>
                        <th>Type</th>
                        <th>Obligatoire</th>
                        <th>Valeur par défaut</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="attribute in tab.attributes" :key="attribute.name">
                        <td>
                          <div class="attr-label">{{ attribute.label }}</div>
                          <div v-if="attribute.label !== attribute.name" class="attr-key">{{ attribute.name }}</div>
                        </td>
                        <td>{{ attribute.order !== '' ? attribute.order : '-' }}</td>
                        <td>{{ attribute.type || '-' }}</td>
                        <td>{{ attribute.required ? 'Oui' : 'Non' }}</td>
                        <td>{{ attribute.defaultValue || '-' }}</td>
                      </tr>
                    </tbody>
                  </v-table>
                  <div v-else class="text-medium-emphasis mt-2">
                    Aucun attribut déclaré dans ce groupe.
                  </div>
                </v-window-item>
              </v-window>
            </div>
            <div v-else class="text-medium-emphasis mt-2">
              Aucun groupe défini pour ce type.
            </div>
          </div>
          <div v-else class="text-medium-emphasis">
            Chargez un modèle puis sélectionnez un type d'objet.
          </div>
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>

  <v-row class="mt-8">
    <v-col cols="12" md="4">
      <v-card class="card-animate delay-1" elevation="4" rounded="xl">
        <v-card-title class="section-title">Types de liens</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="state.modelLinkFilter"
            label="Filtrer un type de lien"
            prepend-icon="mdi-filter-outline"
            variant="outlined"
            density="comfortable"
            clearable
          />
          <v-list v-if="state.filteredModelLinks.length" density="compact">
            <v-list-item
              v-for="link in state.filteredModelLinks"
              :key="link.key"
              :title="link.name"
              :subtitle="link.directed ? 'Orienté' : 'Non orienté'"
              :active="state.selectedModelLink && state.selectedModelLink.key === link.key"
              @click="state.selectedModelLink = link"
            >
              <template #prepend>
                <v-icon icon="mdi-link-variant" />
              </template>
            </v-list-item>
          </v-list>
          <div v-else class="text-medium-emphasis">
            Aucun type de lien détecté.
          </div>
        </v-card-text>
      </v-card>
    </v-col>

    <v-col cols="12" md="8">
      <v-card class="card-animate delay-2" elevation="4" rounded="xl">
        <v-card-title class="section-title">Détails du lien</v-card-title>
        <v-card-text>
          <div v-if="state.selectedModelLink">
            <div class="d-flex align-center" style="gap: 12px; flex-wrap: wrap;">
              <v-chip color="primary" variant="tonal">
                {{ state.selectedModelLink.name }}
              </v-chip>
              <v-chip color="secondary" variant="tonal">
                {{ state.selectedModelLink.directed ? 'Orienté' : 'Non orienté' }}
              </v-chip>
              <v-chip color="accent" variant="tonal">
                {{ state.selectedModelLink.attributes.length }} attributs
              </v-chip>
            </div>
            <div v-if="state.selectedModelLink.description" class="mt-3 text-medium-emphasis">
              {{ state.selectedModelLink.description }}
            </div>

            <v-divider class="my-4" />

            <div class="text-subtitle-2 font-weight-bold">Sources autorisées</div>
            <v-chip-group column class="mt-2">
              <v-chip
                v-for="source in state.selectedModelLink.sources"
                :key="source"
                color="primary"
                variant="tonal"
                class="ma-1"
              >
                {{ source }}
              </v-chip>
            </v-chip-group>

            <div class="text-subtitle-2 font-weight-bold mt-4">Cibles autorisées</div>
            <v-chip-group column class="mt-2">
              <v-chip
                v-for="target in state.selectedModelLink.targets"
                :key="target"
                color="secondary"
                variant="tonal"
                class="ma-1"
              >
                {{ target }}
              </v-chip>
            </v-chip-group>

            <v-divider class="my-4" />

            <div class="text-subtitle-2 font-weight-bold">Attributs du lien</div>
            <v-table v-if="state.selectedModelLink.attributes.length" class="mt-2" density="compact">
              <thead>
                <tr>
                  <th>Attribut</th>
                  <th>Type</th>
                  <th>Obligatoire</th>
                  <th>Valeur par défaut</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="attribute in state.selectedModelLink.attributes" :key="attribute.name">
                  <td>
                    <div class="attr-label">{{ state.formatAttributeLabel(attribute.name, attribute) }}</div>
                    <div
                      v-if="state.formatAttributeLabel(attribute.name, attribute) !== attribute.name"
                      class="attr-key"
                    >
                      {{ attribute.name }}
                    </div>
                  </td>
                  <td>{{ attribute.type }}</td>
                  <td>{{ attribute.required ? 'Oui' : 'Non' }}</td>
                  <td>{{ attribute.defaultValue || '-' }}</td>
                </tr>
              </tbody>
            </v-table>
            <div v-else class="text-medium-emphasis mt-2">
              Aucun attribut défini pour ce lien.
            </div>
          </div>
          <div v-else class="text-medium-emphasis">
            Chargez un modèle puis sélectionnez un type de lien.
          </div>
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>

  <v-row class="mt-8">
    <v-col cols="12">
      <v-card class="card-animate delay-3" elevation="3" rounded="xl">
        <v-card-title class="section-title">Graph des liens</v-card-title>
        <v-card-text>
          <div v-if="state.graphNodes.length" class="model-graph">
            <svg
              class="model-graph__svg"
              viewBox="0 0 640 400"
              role="img"
              aria-label="Graphique des liens entre types d'objets"
            >
              <defs>
                <marker
                  id="arrow"
                  markerWidth="10"
                  markerHeight="10"
                  refX="10"
                  refY="5"
                  orient="auto"
                >
                  <path d="M 0 0 L 10 5 L 0 10 z" fill="#1C4E80" />
                </marker>
              </defs>
              <g>
                <g v-for="edge in state.graphEdges" :key="edge.key" class="model-graph__edge">
                  <line
                    v-if="!edge.self"
                    :x1="edge.from.x"
                    :y1="edge.from.y"
                    :x2="edge.to.x"
                    :y2="edge.to.y"
                    stroke="#1C4E80"
                    stroke-width="2"
                    :marker-end="edge.directed ? 'url(#arrow)' : ''"
                  />
                  <path
                    v-else
                    :d="edge.path"
                    stroke="#1C4E80"
                    fill="none"
                    stroke-width="2"
                    :marker-end="edge.directed ? 'url(#arrow)' : ''"
                  />
                </g>
                <g>
                  <g
                    v-for="node in state.graphNodes"
                    :key="node.key"
                    class="model-graph__node"
                    @click="state.selectModelObjectByName(node.name)"
                  >
                    <circle
                      :cx="node.x"
                      :cy="node.y"
                      r="22"
                      fill="#F18F01"
                      stroke="#1C4E80"
                      stroke-width="2"
                    />
                    <text
                      :x="node.x"
                      :y="node.y"
                      text-anchor="middle"
                      dominant-baseline="middle"
                      fill="#12243A"
                      font-size="10"
                      font-weight="600"
                    >
                      {{ node.label }}
                    </text>
                  </g>
                </g>
              </g>
            </svg>
            <div class="text-caption text-medium-emphasis mt-2">
              Les flèches indiquent les liens orientés. Cliquez sur un type d'objet pour filtrer dans la liste.
            </div>
          </div>
          <div v-else class="text-medium-emphasis">
            Chargez un modèle pour afficher son graphe de liens.
          </div>
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>

  <v-row class="mt-8">
    <v-col cols="12">
      <v-card class="card-animate delay-1" elevation="4" rounded="xl">
        <v-card-title class="section-title">Edition XML</v-card-title>
        <v-card-text>
          <div class="text-medium-emphasis mb-4">
            Modifiez le XML du modele puis enregistrez-le en base ou exportez-le.
          </div>
          <div class="d-flex align-center" style="gap: 12px; flex-wrap: wrap;">
            <v-btn
              color="secondary"
              variant="tonal"
              :loading="state.isLoadingModelXml"
              :disabled="!state.selectedModelKey"
              @click="state.loadModelXml"
            >
              Charger le XML
            </v-btn>
            <v-btn
              color="primary"
              :loading="state.isSavingModelXml"
              :disabled="!state.selectedModelKey || !state.modelXml"
              @click="state.saveModelXml"
            >
              Sauvegarder
            </v-btn>
            <v-btn
              variant="tonal"
              color="primary"
              :disabled="!state.selectedModelKey"
              @click="exportXml"
            >
              Exporter XML
            </v-btn>
          </div>

          <v-textarea
            v-model="state.modelXml"
            class="mt-4"
            label="XML du modele"
            variant="outlined"
            auto-grow
            rows="12"
            :disabled="!state.selectedModelKey"
          />

          <v-alert
            v-if="state.modelXmlStatus"
            class="mt-4"
            :type="state.modelXmlStatus.type"
            variant="tonal"
            density="comfortable"
            border="start"
          >
            {{ state.modelXmlStatus.message }}
          </v-alert>
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
const representativeAttributes = computed(() => {
  const selected = state.selectedModelObject;
  if (!selected || !Array.isArray(selected.representativeAttributes)) {
    return [];
  }
  return [...selected.representativeAttributes]
    .sort((a, b) => {
      const orderA = a.order ?? a.index ?? 0;
      const orderB = b.order ?? b.index ?? 0;
      if (orderA === orderB) {
        return String(a.name || '').localeCompare(String(b.name || ''));
      }
      return orderA - orderB;
    })
    .map((attr) => ({
      ...attr,
      label: state.getAttributeLabel(selected.name, attr.name) || attr.name
    }));
});

async function exportXml() {
  if (!state.selectedModelKey) {
    return;
  }
  if (!state.modelXml) {
    await state.loadModelXml();
  }
  if (!state.modelXml) {
    return;
  }
  const name = state.selectedModel?.name || 'modele';
  const version = state.selectedModel?.version || '';
  const fileName = version ? `${name}-v${version}.xml` : `${name}.xml`;
  const blob = new Blob([state.modelXml], { type: 'application/xml' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}
</script>
