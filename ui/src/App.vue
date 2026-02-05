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
const dataSummary = ref(null);
const dataObjects = ref([]);
const dataLinks = ref([]);
const linkTypeInfo = ref({});
const selectedObject = ref(null);
const objectFilter = ref('');
const status = ref(null);

const canValidate = computed(() => Boolean(modelSummary.value && dataSummary.value));

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
    linkTypeInfo.value = {};
    return;
  }

  try {
    const text = await readFile(file);
    const doc = parseXml(text);
    modelSummary.value = extractModelSummary(doc);
    linkTypeInfo.value = extractLinkTypeDirections(doc);
  } catch (error) {
    modelSummary.value = null;
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
