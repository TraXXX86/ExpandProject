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
            <div class="text-caption text-medium-emphasis">IHM locale pour modèles XML</div>
          </div>
        </div>
        <div class="d-flex align-center" style="gap: 8px;">
          <v-chip color="secondary" variant="tonal">XML</v-chip>
          <v-chip color="primary" variant="tonal">Validation</v-chip>
          <v-chip color="accent" variant="tonal">Neo4j</v-chip>
        </div>
      </v-container>
    </v-app-bar>

    <v-main>
      <div class="hero-bg" />
      <v-container class="py-6 hero-content">
        <v-row class="mb-6">
          <v-col cols="12">
            <v-tabs v-model="currentPage" color="primary" align-tabs="start">
              <v-tab value="import">Import</v-tab>
              <v-tab value="model">Modèle</v-tab>
              <v-tab value="navigate">Navigation</v-tab>
            </v-tabs>
          </v-col>
        </v-row>

        <v-window v-model="currentPage">
          <v-window-item value="import">
            <v-row>
              <v-col cols="12" md="6">
                <div class="kicker">Studio d'import</div>
                <h1 class="headline">Donnez un héritage clair à vos objets.</h1>
                <p class="subhead">
                  Chargez vos modèles XML, explorez les attributs hérités et préparez l'import Neo4j.
                  L'interface fonctionne en local pour prévisualiser vos données avant validation.
                </p>
                <div class="d-flex flex-wrap" style="gap: 12px; margin-top: 24px;">
                  <v-btn color="primary" size="large" prepend-icon="mdi-upload">
                    Charger un modèle
                  </v-btn>
                  <v-btn variant="tonal" color="primary" size="large" prepend-icon="mdi-file-chart-outline">
                    Voir un exemple
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
                  Cette IHM lit et résume vos fichiers localement. L'envoi au backend n'est pas encore câblé.
                </v-alert>
              </v-col>

              <v-col cols="12" md="6">
                <v-card class="card-animate" elevation="6" rounded="xl">
                  <v-card-title class="section-title">Session d'import</v-card-title>
                  <v-card-text>
                    <v-file-input
                      label="Fichier de modèle (model.xml)"
                      accept=".xml"
                      prepend-icon="mdi-file-code"
                      variant="outlined"
                      density="comfortable"
                      @update:model-value="handleModelFile"
                    />
                    <div class="file-hint">Déclarez vos types, liens et héritages.</div>

                    <v-file-input
                      class="mt-5"
                      label="Fichier de données (data.xml)"
                      accept=".xml"
                      prepend-icon="mdi-file-tree"
                      variant="outlined"
                      density="comfortable"
                      @update:model-value="handleDataFile"
                    />
                    <div class="file-hint">Prévisualisez le nombre d'objets et de liens.</div>

                    <v-switch
                      class="mt-4"
                      v-model="validateOnly"
                      label="Validation uniquement (sans import Neo4j)"
                      color="primary"
                      inset
                    />

                    <div class="d-flex align-center" style="gap: 12px;">
                      <v-btn
                        color="primary"
                        size="large"
                        :disabled="!canValidate"
                        @click="runLocalCheck"
                      >
                        Vérifier la cohérence
                      </v-btn>
                      <v-btn
                        variant="tonal"
                        color="secondary"
                        size="large"
                        :disabled="!canValidate"
                      >
                        Préparer l'import
                      </v-btn>
                    </div>

                    <v-alert
                      v-if="status"
                      class="mt-5"
                      :type="status.type"
                      variant="tonal"
                      density="comfortable"
                      border="start"
                    >
                      {{ status.message }}
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
                    <div v-if="modelSummary">
                      <div class="summary-badge text-primary">
                        <v-icon icon="mdi-shape-outline" />
                        {{ modelSummary.objectCount }} types d'objets
                      </div>
                      <div class="summary-badge text-secondary" style="margin-left: 12px;">
                        <v-icon icon="mdi-link-variant" />
                        {{ modelSummary.linkCount }} types de liens
                      </div>
                      <v-divider class="my-4" />
                      <div class="text-caption text-medium-emphasis">Types détectés</div>
                      <v-chip-group column class="mt-2">
                        <v-chip
                          v-for="type in modelSummary.types"
                          :key="type.name"
                          color="primary"
                          variant="tonal"
                          class="ma-1"
                        >
                          {{ type.name }}
                          <span v-if="type.parent"> → {{ type.parent }}</span>
                        </v-chip>
                      </v-chip-group>
                      <div v-if="modelSummary.hasMore" class="text-caption text-medium-emphasis mt-2">
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
                  <v-card-title class="section-title">Résumé des données</v-card-title>
                  <v-card-text>
                    <div v-if="dataSummary">
                      <div class="summary-badge text-primary">
                        <v-icon icon="mdi-cube-outline" />
                        {{ dataSummary.objectCount }} objets
                      </div>
                      <div class="summary-badge text-secondary" style="margin-left: 12px;">
                        <v-icon icon="mdi-connection" />
                        {{ dataSummary.linkCount }} liens
                      </div>
                      <v-divider class="my-4" />
                      <div class="text-caption text-medium-emphasis">Types d'objets rencontrés</div>
                      <v-chip-group column class="mt-2">
                        <v-chip
                          v-for="type in dataSummary.objectTypes"
                          :key="type"
                          color="accent"
                          variant="tonal"
                          class="ma-1"
                        >
                          {{ type }}
                        </v-chip>
                      </v-chip-group>
                      <div v-if="dataSummary.hasMore" class="text-caption text-medium-emphasis mt-2">
                        + d'autres types non affichés
                      </div>
                    </div>
                    <div v-else class="text-medium-emphasis">
                      Aucun jeu de données chargé pour le moment.
                    </div>
                  </v-card-text>
                </v-card>
              </v-col>
            </v-row>

            <v-row class="mt-8">
              <v-col cols="12">
                <v-card class="card-animate delay-1" elevation="3" rounded="xl">
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
          </v-window-item>

          <v-window-item value="model">
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
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="12" md="4">
                <v-card class="card-animate delay-1" elevation="4" rounded="xl">
                  <v-card-title class="section-title">Types d'objets</v-card-title>
                  <v-card-text>
                    <v-text-field
                      v-model="modelObjectFilter"
                      label="Filtrer un type d'objet"
                      prepend-icon="mdi-filter-outline"
                      variant="outlined"
                      density="comfortable"
                      clearable
                    />
                    <v-list v-if="filteredModelObjects.length" density="compact">
                      <v-list-item
                        v-for="type in filteredModelObjects"
                        :key="type.key"
                        :title="type.name"
                        :subtitle="type.parent ? `Parent: ${type.parent}` : 'Sans parent'"
                        :active="selectedModelObject && selectedModelObject.key === type.key"
                        @click="selectedModelObject = type"
                      >
                        <template #prepend>
                          <v-icon icon="mdi-shape-outline" />
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
                    <div v-if="selectedModelObject">
                      <div class="d-flex align-center" style="gap: 12px; flex-wrap: wrap;">
                        <v-chip color="primary" variant="tonal">
                          {{ selectedModelObject.name }}
                        </v-chip>
                        <v-chip v-if="selectedModelObject.parent" color="secondary" variant="tonal">
                          Parent: {{ selectedModelObject.parent }}
                        </v-chip>
                        <v-chip color="accent" variant="tonal">
                          {{ selectedModelObject.attributes.length }} attributs
                        </v-chip>
                      </div>
                      <div v-if="selectedModelObject.description" class="mt-3 text-medium-emphasis">
                        {{ selectedModelObject.description }}
                      </div>

                      <v-divider class="my-4" />

                      <div class="text-subtitle-2 font-weight-bold">Attributs</div>
                      <v-table v-if="selectedModelObject.attributes.length" class="mt-2" density="compact">
                        <thead>
                          <tr>
                            <th>Nom</th>
                            <th>Type</th>
                            <th>Obligatoire</th>
                            <th>Valeur par défaut</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="attribute in selectedModelObject.attributes" :key="attribute.name">
                            <td>{{ attribute.name }}</td>
                            <td>{{ attribute.type }}</td>
                            <td>{{ attribute.required ? 'Oui' : 'Non' }}</td>
                            <td>{{ attribute.defaultValue || '-' }}</td>
                          </tr>
                        </tbody>
                      </v-table>
                      <div v-else class="text-medium-emphasis mt-2">
                        Aucun attribut défini pour ce type.
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
                      v-model="modelLinkFilter"
                      label="Filtrer un type de lien"
                      prepend-icon="mdi-filter-outline"
                      variant="outlined"
                      density="comfortable"
                      clearable
                    />
                    <v-list v-if="filteredModelLinks.length" density="compact">
                      <v-list-item
                        v-for="link in filteredModelLinks"
                        :key="link.key"
                        :title="link.name"
                        :subtitle="link.directed ? 'Orienté' : 'Non orienté'"
                        :active="selectedModelLink && selectedModelLink.key === link.key"
                        @click="selectedModelLink = link"
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
                    <div v-if="selectedModelLink">
                      <div class="d-flex align-center" style="gap: 12px; flex-wrap: wrap;">
                        <v-chip color="primary" variant="tonal">
                          {{ selectedModelLink.name }}
                        </v-chip>
                        <v-chip color="secondary" variant="tonal">
                          {{ selectedModelLink.directed ? 'Orienté' : 'Non orienté' }}
                        </v-chip>
                        <v-chip color="accent" variant="tonal">
                          {{ selectedModelLink.attributes.length }} attributs
                        </v-chip>
                      </div>
                      <div v-if="selectedModelLink.description" class="mt-3 text-medium-emphasis">
                        {{ selectedModelLink.description }}
                      </div>

                      <v-divider class="my-4" />

                      <div class="text-subtitle-2 font-weight-bold">Sources autorisées</div>
                      <v-chip-group column class="mt-2">
                        <v-chip
                          v-for="source in selectedModelLink.sources"
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
                          v-for="target in selectedModelLink.targets"
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
                      <v-table v-if="selectedModelLink.attributes.length" class="mt-2" density="compact">
                        <thead>
                          <tr>
                            <th>Nom</th>
                            <th>Type</th>
                            <th>Obligatoire</th>
                            <th>Valeur par défaut</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="attribute in selectedModelLink.attributes" :key="attribute.name">
                            <td>{{ attribute.name }}</td>
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
                    <div v-if="graphNodes.length" class="model-graph">
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
                        <g class="model-graph__edges">
                          <g
                            v-for="edge in graphEdges"
                            :key="edge.key"
                            class="model-graph__edge"
                            @click="selectModelLinkByName(edge.name)"
                          >
                            <title>{{ edge.label }}</title>
                            <line
                              v-if="!edge.self"
                              :x1="edge.from.x"
                              :y1="edge.from.y"
                              :x2="edge.to.x"
                              :y2="edge.to.y"
                              :marker-end="edge.directed ? 'url(#arrow)' : undefined"
                              stroke="#1C4E80"
                              stroke-width="1.5"
                              stroke-linecap="round"
                              opacity="0.7"
                            />
                            <path
                              v-else
                              :d="edge.path"
                              fill="none"
                              :marker-end="edge.directed ? 'url(#arrow)' : undefined"
                              stroke="#1C4E80"
                              stroke-width="1.5"
                              stroke-linecap="round"
                              opacity="0.7"
                            />
                          </g>
                        </g>
                        <g class="model-graph__nodes">
                          <g
                            v-for="node in graphNodes"
                            :key="node.key"
                            class="model-graph__node"
                            @click="selectModelObjectByName(node.name)"
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
          </v-window-item>

          <v-window-item value="navigate">
            <v-row class="mb-6">
              <v-col cols="12">
                <div class="kicker">Navigation</div>
                <h2 class="headline" style="font-size: clamp(1.6rem, 2.5vw, 2.4rem);">
                  Explorez les objets et leurs connexions.
                </h2>
                <p class="subhead">
                  Cette page s'adapte au modèle chargé et vous permet de suivre les liens entre les objets du
                  fichier de données.
                </p>
              </v-col>
            </v-row>

            <v-row>
              <v-col cols="12" md="4">
                <v-card class="card-animate delay-1" elevation="4" rounded="xl">
                  <v-card-title class="section-title">Objets chargés</v-card-title>
                  <v-card-text>
                    <v-text-field
                      v-model="objectFilter"
                      label="Filtrer par type ou ID"
                      prepend-icon="mdi-filter-outline"
                      variant="outlined"
                      density="comfortable"
                      clearable
                    />
                    <v-list v-if="filteredObjects.length" density="compact">
                      <v-list-item
                        v-for="object in filteredObjects"
                        :key="object.key"
                        :title="object.type"
                        :subtitle="`ID: ${object.id ?? 'N/A'} • ${object.attributes.length} attributs`"
                        :active="selectedObject && selectedObject.key === object.key"
                        @click="selectObject(object)"
                      >
                        <template #prepend>
                          <v-icon icon="mdi-cube-outline" />
                        </template>
                      </v-list-item>
                    </v-list>
                    <div v-else class="text-medium-emphasis">
                      Aucun objet détecté dans le fichier de données.
                    </div>
                  </v-card-text>
                </v-card>
              </v-col>

              <v-col cols="12" md="8">
                <v-card class="card-animate delay-2" elevation="4" rounded="xl">
                  <v-card-title class="section-title">Détails de l'objet</v-card-title>
                  <v-card-text>
                    <div v-if="selectedObject">
                      <div class="d-flex align-center" style="gap: 12px; flex-wrap: wrap;">
                        <v-chip color="primary" variant="tonal">
                          {{ selectedObject.type }}
                        </v-chip>
                        <v-chip color="secondary" variant="tonal">
                          ID: {{ selectedObject.id ?? 'N/A' }}
                        </v-chip>
                        <v-chip color="accent" variant="tonal">
                          {{ selectedObject.attributes.length }} attributs
                        </v-chip>
                      </div>

                      <v-divider class="my-4" />

                      <div class="text-subtitle-2 font-weight-bold">Attributs</div>
                      <v-table class="mt-2" density="compact">
                        <thead>
                          <tr>
                            <th>Clé</th>
                            <th>Valeur</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="attribute in selectedObject.attributes" :key="attribute.key">
                            <td>{{ attribute.key }}</td>
                            <td>{{ attribute.value }}</td>
                          </tr>
                        </tbody>
                      </v-table>

                      <v-divider class="my-4" />

                      <div class="text-subtitle-2 font-weight-bold">Liens</div>
                      <v-list v-if="linkedObjects.length" density="compact" class="mt-2">
                        <v-list-item
                          v-for="relation in linkedObjects"
                          :key="relation.key"
                          :title="relation.target.type"
                          :subtitle="`ID: ${relation.target.id ?? 'N/A'} • ${relation.type} • ${relation.directionLabel}`"
                          @click="selectObjectByKey(relation.target.idKey)"
                        >
                          <template #prepend>
                            <v-icon :icon="relation.directionIcon" />
                          </template>
                          <template #append>
                            <v-btn
                              icon="mdi-open-in-new"
                              variant="text"
                              @click.stop="selectObjectByKey(relation.target.idKey)"
                            />
                          </template>
                        </v-list-item>
                      </v-list>
                      <div v-else class="text-medium-emphasis mt-2">
                        Aucun lien à afficher pour cet objet.
                      </div>
                    </div>
                    <div v-else class="text-medium-emphasis">
                      Chargez un fichier de données puis sélectionnez un objet pour afficher ses détails.
                    </div>
                  </v-card-text>
                </v-card>
              </v-col>
            </v-row>
          </v-window-item>
        </v-window>
      </v-container>
    </v-main>
  </v-app>
</template>

<script setup>
import { computed, ref } from 'vue';

const currentPage = ref('import');
const validateOnly = ref(true);
const modelSummary = ref(null);
const modelDetails = ref({ objectTypes: [], linkTypes: [] });
const selectedModelObject = ref(null);
const selectedModelLink = ref(null);
const modelObjectFilter = ref('');
const modelLinkFilter = ref('');
const dataSummary = ref(null);
const dataObjects = ref([]);
const dataLinks = ref([]);
const linkTypeInfo = ref({});
const selectedObject = ref(null);
const objectFilter = ref('');
const status = ref(null);

const canValidate = computed(() => Boolean(modelSummary.value && dataSummary.value));

const filteredModelObjects = computed(() => {
  const filter = modelObjectFilter.value.trim().toLowerCase();
  if (!filter) {
    return modelDetails.value.objectTypes;
  }
  return modelDetails.value.objectTypes.filter((type) =>
    type.name.toLowerCase().includes(filter)
  );
});

const filteredModelLinks = computed(() => {
  const filter = modelLinkFilter.value.trim().toLowerCase();
  if (!filter) {
    return modelDetails.value.linkTypes;
  }
  return modelDetails.value.linkTypes.filter((link) =>
    link.name.toLowerCase().includes(filter)
  );
});

const filteredObjects = computed(() => {
  const filter = objectFilter.value.trim().toLowerCase();
  if (!filter) {
    return dataObjects.value;
  }
  return dataObjects.value.filter((object) => {
    const id = object.id !== null && object.id !== undefined ? String(object.id) : '';
    return (
      object.type.toLowerCase().includes(filter) ||
      id.toLowerCase().includes(filter)
    );
  });
});

const graphNodes = computed(() => buildGraphNodes());
const graphEdges = computed(() => buildGraphEdges());

const objectIndex = computed(() => {
  const map = new Map();
  dataObjects.value.forEach((object) => {
    map.set(object.idKey, object);
  });
  return map;
});

const linkedObjects = computed(() => {
  if (!selectedObject.value) {
    return [];
  }

  const relations = [];
  const currentKey = selectedObject.value.idKey;

  dataLinks.value.forEach((link, index) => {
    const directed = linkTypeInfo.value[link.type] === true;
    const outDirection = directed ? 'out' : 'both';
    const inDirection = directed ? 'in' : 'both';

    if (link.fromKey === currentKey) {
      relations.push(buildRelation(link, outDirection, link.toKey, index));
    }
    if (link.toKey === currentKey) {
      relations.push(buildRelation(link, inDirection, link.fromKey, index));
    }
  });

  return relations
    .map((relation) => {
      const target = objectIndex.value.get(relation.targetKey);
      if (!target) {
        return null;
      }
      return {
        ...relation,
        target
      };
    })
    .filter(Boolean);
});

async function handleModelFile(files) {
  const file = Array.isArray(files) ? files[0] : files;
  status.value = null;
  if (!file) {
    modelSummary.value = null;
    modelDetails.value = { objectTypes: [], linkTypes: [] };
    selectedModelObject.value = null;
    selectedModelLink.value = null;
    linkTypeInfo.value = {};
    return;
  }

  try {
    const text = await readFile(file);
    const doc = parseXml(text);
    modelSummary.value = extractModelSummary(doc);
    modelDetails.value = extractModelDetails(doc);
    selectedModelObject.value = modelDetails.value.objectTypes[0] ?? null;
    selectedModelLink.value = modelDetails.value.linkTypes[0] ?? null;
    linkTypeInfo.value = extractLinkTypeDirections(doc);
  } catch (error) {
    modelSummary.value = null;
    modelDetails.value = { objectTypes: [], linkTypes: [] };
    selectedModelObject.value = null;
    selectedModelLink.value = null;
    linkTypeInfo.value = {};
    status.value = {
      type: 'error',
      message: `Erreur de lecture du modèle: ${error.message}`
    };
  }
}

async function handleDataFile(files) {
  const file = Array.isArray(files) ? files[0] : files;
  status.value = null;
  if (!file) {
    dataSummary.value = null;
    dataObjects.value = [];
    dataLinks.value = [];
    selectedObject.value = null;
    return;
  }

  try {
    const text = await readFile(file);
    const doc = parseXml(text);
    dataSummary.value = extractDataSummary(doc);
    dataObjects.value = extractDataObjects(doc);
    dataLinks.value = extractDataLinks(doc);
    selectedObject.value = dataObjects.value[0] ?? null;
  } catch (error) {
    dataSummary.value = null;
    dataObjects.value = [];
    dataLinks.value = [];
    selectedObject.value = null;
    status.value = {
      type: 'error',
      message: `Erreur de lecture des données: ${error.message}`
    };
  }
}

function runLocalCheck() {
  if (!canValidate.value) {
    status.value = {
      type: 'warning',
      message: 'Chargez un modèle et un fichier de données pour continuer.'
    };
    return;
  }

  const mode = validateOnly.value ? 'Validation seule' : 'Préparation import';
  status.value = {
    type: 'success',
    message: `${mode} prête. Résumé local effectué, aucune erreur XML détectée.`
  };
}

function selectObject(object) {
  selectedObject.value = object;
}

function selectObjectByKey(key) {
  const target = objectIndex.value.get(key);
  if (target) {
    selectedObject.value = target;
  }
}

function selectModelObjectByName(name) {
  if (!name) {
    return;
  }
  const match = modelDetails.value.objectTypes.find((type) => type.name === name);
  if (match) {
    selectedModelObject.value = match;
    modelObjectFilter.value = name;
  }
}

function selectModelLinkByName(name) {
  if (!name) {
    return;
  }
  const match = modelDetails.value.linkTypes.find((link) => link.name === name);
  if (match) {
    selectedModelLink.value = match;
    modelLinkFilter.value = name;
  }
}

function buildRelation(link, direction, targetKey, index) {
  return {
    key: `${link.type}-${index}-${direction}`,
    type: link.type,
    targetKey,
    direction,
    directionLabel: directionToLabel(direction),
    directionIcon: directionToIcon(direction)
  };
}

function directionToLabel(direction) {
  if (direction === 'out') {
    return 'sortant';
  }
  if (direction === 'in') {
    return 'entrant';
  }
  return 'bidirectionnel';
}

function directionToIcon(direction) {
  if (direction === 'out') {
    return 'mdi-arrow-right';
  }
  if (direction === 'in') {
    return 'mdi-arrow-left';
  }
  return 'mdi-arrow-left-right';
}

function buildGraphNodes() {
  const nodes = modelDetails.value.objectTypes;
  if (!nodes.length) {
    return [];
  }
  const centerX = 320;
  const centerY = 200;
  const radius = Math.max(120, Math.min(160, nodes.length * 12));
  const step = (Math.PI * 2) / nodes.length;

  return nodes.map((node, index) => {
    const angle = index * step - Math.PI / 2;
    const x = centerX + Math.cos(angle) * radius;
    const y = centerY + Math.sin(angle) * radius;
    const label = node.name.length > 6 ? `${node.name.slice(0, 6)}...` : node.name;
    return {
      key: `node-${node.name}-${index}`,
      name: node.name,
      label,
      x,
      y
    };
  });
}

function buildGraphEdges() {
  if (!graphNodes.value.length) {
    return [];
  }
  const nodeMap = new Map(graphNodes.value.map((node) => [node.name, node]));
  const edges = [];

  modelDetails.value.linkTypes.forEach((link, linkIndex) => {
    link.sources.forEach((source) => {
      link.targets.forEach((target) => {
        const from = nodeMap.get(source);
        const to = nodeMap.get(target);
        if (!from || !to) {
          return;
        }
        const self = source === target;
        edges.push({
          key: `${link.name}-${source}-${target}-${linkIndex}`,
          name: link.name,
          from,
          to,
          directed: link.directed,
          self,
          path: self ? buildSelfLoopPath(from) : '',
          label: `${link.name} (${source}${link.directed ? ' → ' : ' ↔ '}${target})`
        });
      });
    });
  });

  return edges;
}

function buildSelfLoopPath(node) {
  const radius = 22;
  const loop = 36;
  const startX = node.x + radius;
  const startY = node.y - radius;
  const c1X = node.x + loop;
  const c1Y = node.y - loop * 1.4;
  const c2X = node.x + loop * 1.8;
  const c2Y = node.y + loop * 0.4;
  const endX = node.x;
  const endY = node.y + radius;
  return `M ${startX} ${startY} C ${c1X} ${c1Y}, ${c2X} ${c2Y}, ${endX} ${endY}`;
}

function readFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error('lecture impossible'));
    reader.readAsText(file);
  });
}

function parseXml(text) {
  const parser = new DOMParser();
  const doc = parser.parseFromString(text, 'application/xml');
  if (doc.querySelector('parsererror')) {
    throw new Error('XML invalide');
  }
  return doc;
}

function extractModelSummary(doc) {
  const objectTypes = Array.from(doc.querySelectorAll('OBJECT_TYPE')).map((node) => ({
    name: node.getAttribute('NAME') || 'Type',
    parent: node.getAttribute('PARENT') || ''
  }));
  const linkTypes = Array.from(doc.querySelectorAll('LINK_TYPE')).map(
    (node) => node.getAttribute('NAME') || 'Lien'
  );
  return {
    objectCount: objectTypes.length,
    linkCount: linkTypes.length,
    types: objectTypes.slice(0, 6),
    hasMore: objectTypes.length > 6 || linkTypes.length > 6
  };
}

function extractLinkTypeDirections(doc) {
  const linkTypes = Array.from(doc.querySelectorAll('LINK_TYPE'));
  const map = {};
  linkTypes.forEach((node) => {
    const name = node.getAttribute('NAME') || '';
    const directedAttr = node.getAttribute('DIRECTED');
    let directed = true;
    if (directedAttr !== null && directedAttr !== '') {
      directed = directedAttr.toLowerCase() !== 'false';
    }
    if (name) {
      map[name] = directed;
    }
  });
  return map;
}

function extractModelDetails(doc) {
  const objectTypes = Array.from(doc.querySelectorAll('OBJECT_TYPE')).map((node, index) => {
    const name = node.getAttribute('NAME') || `Type-${index + 1}`;
    const parent = node.getAttribute('PARENT') || '';
    const description = node.querySelector('DESCRIPTION')?.textContent?.trim() || '';
    const attributes = Array.from(node.querySelectorAll('ATTRIBUTE_DEFINITION')).map((attr) => ({
      name: attr.getAttribute('NAME') || '',
      type: attr.getAttribute('TYPE') || 'STRING',
      required: (attr.getAttribute('REQUIRED') || 'false').toLowerCase() === 'true',
      defaultValue: attr.getAttribute('DEFAULT_VALUE') || '',
      description: attr.querySelector('DESCRIPTION')?.textContent?.trim() || ''
    }));
    return {
      key: `${name}-${index}`,
      name,
      parent,
      description,
      attributes
    };
  });

  const linkTypes = Array.from(doc.querySelectorAll('LINK_TYPE')).map((node, index) => {
    const name = node.getAttribute('NAME') || `Lien-${index + 1}`;
    const directedAttr = node.getAttribute('DIRECTED');
    const directed = directedAttr ? directedAttr.toLowerCase() !== 'false' : true;
    const description = node.querySelector('DESCRIPTION')?.textContent?.trim() || '';
    const sources = Array.from(node.querySelectorAll('SOURCE_TYPES TYPE_REF')).map(
      (refNode) => refNode.getAttribute('NAME') || ''
    );
    const targets = Array.from(node.querySelectorAll('TARGET_TYPES TYPE_REF')).map(
      (refNode) => refNode.getAttribute('NAME') || ''
    );
    const attributes = Array.from(node.querySelectorAll('ATTRIBUTE_DEFINITION')).map((attr) => ({
      name: attr.getAttribute('NAME') || '',
      type: attr.getAttribute('TYPE') || 'STRING',
      required: (attr.getAttribute('REQUIRED') || 'false').toLowerCase() === 'true',
      defaultValue: attr.getAttribute('DEFAULT_VALUE') || '',
      description: attr.querySelector('DESCRIPTION')?.textContent?.trim() || ''
    }));
    return {
      key: `${name}-${index}`,
      name,
      directed,
      description,
      sources,
      targets,
      attributes
    };
  });

  return {
    objectTypes,
    linkTypes
  };
}

function extractDataSummary(doc) {
  const objects = Array.from(doc.querySelectorAll('OBJECT'));
  const links = Array.from(doc.querySelectorAll('LINK'));
  const objectTypes = [...new Set(objects.map((node) => node.getAttribute('TYPE') || 'Type'))];
  return {
    objectCount: objects.length,
    linkCount: links.length,
    objectTypes: objectTypes.slice(0, 6),
    hasMore: objectTypes.length > 6
  };
}

function extractDataObjects(doc) {
  const objectNodes = Array.from(doc.querySelectorAll('OBJECT'));
  return objectNodes.map((node, index) => {
    const attributes = Array.from(node.querySelectorAll('ATTRIBUTE')).map((attr) => ({
      key: attr.getAttribute('KEY') || '',
      value: attr.getAttribute('VALUE') || ''
    }));
    const idValue = node.getAttribute('ID');
    const id = idValue !== null && idValue !== '' ? idValue : null;
    const type = node.getAttribute('TYPE') || 'Type';
    const idKey = id !== null ? `${type}|${id}` : `${type}|index-${index}`;
    return {
      id,
      type,
      attributes,
      idKey,
      key: `${idKey}-${index}`
    };
  });
}

function extractDataLinks(doc) {
  const linkNodes = Array.from(doc.querySelectorAll('LINK'));
  return linkNodes.map((node, index) => {
    const type = node.getAttribute('TYPE') || 'Lien';
    const source = node.querySelector('OBJ_LINK_A');
    const target = node.querySelector('OBJ_LINK_B');
    const fromType = source?.getAttribute('TYPE') || 'Type';
    const fromId = source?.getAttribute('ID') || '';
    const toType = target?.getAttribute('TYPE') || 'Type';
    const toId = target?.getAttribute('ID') || '';
    return {
      key: `${type}-${index}`,
      type,
      fromKey: `${fromType}|${fromId}`,
      toKey: `${toType}|${toId}`
    };
  });
}
</script>
