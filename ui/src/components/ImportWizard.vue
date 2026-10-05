<template>
  <v-card rounded="xl" elevation="4">
    <v-card-text>
      <v-alert v-if="!state.canReadCurrentModelData || !state.canCreateCurrentModelData" type="warning" variant="tonal" class="mb-5">
        Sélectionnez un modèle sur lequel vous pouvez consulter et ajouter des données. La mise à jour nécessite aussi le droit de modification.
      </v-alert>
      <h2 class="text-h6 mb-4">1. Choisir le fichier et sa destination</h2>
      <v-select v-model="state.selectedModelKey" :items="state.modelOptions" label="Modèle cible" variant="outlined" :loading="state.isLoadingModels" :disabled="Boolean(wizard.busy)" />
      <v-file-input :model-value="wizard.file" label="Fichier CSV, Excel ou XML" accept=".csv,.xlsx,.xml" variant="outlined" :disabled="Boolean(wizard.busy)" @update:model-value="wizard.selectFile" />
      <p class="text-body-2 text-medium-emphasis mb-4">Jusqu’à 5 000 lignes et 10 Mo. Les fichiers CSV utilisent une première ligne d’en-têtes. Excel : format .xlsx, sans formules.</p>
      <v-select v-if="wizard.file" v-model="wizard.format" :items="formats" label="Format du fichier" :disabled="Boolean(wizard.busy)" variant="outlined" />
      <v-select v-if="wizard.format === 'xlsx' && wizard.sheets.length" v-model="wizard.sheet" :items="sheetOptions" label="Feuille à importer" :disabled="Boolean(wizard.busy)" variant="outlined" hint="Après un changement de feuille, analysez à nouveau le fichier." persistent-hint class="mb-4" />
      <v-btn v-if="wizard.format !== 'xml'" :disabled="!wizard.canInspect" :loading="wizard.busy === 'inspect'" variant="tonal" @click="wizard.inspect">Analyser le fichier</v-btn>
      <template v-if="wizard.inspection && wizard.format !== 'xml'">
        <p class="text-body-2 mt-4">{{ wizard.inspection.rowCount }} lignes détectées · {{ wizard.columns.length }} colonnes</p>
        <div v-if="wizard.inspection.sampleRows?.length" class="import-table mt-3" tabindex="0" aria-label="Extrait du fichier">
          <table>
            <caption>Extrait du fichier</caption>
            <thead><tr><th v-for="column in wizard.columns" :key="column" scope="col">{{ column }}</th></tr></thead>
            <tbody><tr v-for="(row, index) in wizard.inspection.sampleRows.slice(0, 5)" :key="index"><td v-for="(cell, column) in row" :key="column">{{ cell }}</td></tr></tbody>
          </table>
        </div>
        <v-divider class="my-6" />
        <h2 class="text-h6 mb-4">2. Associer les colonnes aux données</h2>
        <v-select v-model="wizard.objectType" :items="typeOptions" label="Type d’objet à importer" :disabled="Boolean(wizard.busy)" variant="outlined" />
        <v-select v-model="wizard.idColumn" :items="wizard.columns" label="Colonne d’identifiant stable" :disabled="Boolean(wizard.busy)" variant="outlined" hint="Un entier positif ou nul, conservé entre vos imports. Il identifie un objet dans ce modèle et ce type." persistent-hint class="mb-5" />
        <v-row>
          <v-col v-for="attribute in wizard.attributes" :key="attribute.name" cols="12" md="6">
            <v-select v-model="wizard.mapping[attribute.name]" :items="wizard.columns" :label="`${attributeLabel(attribute)}${attribute.required ? ' *' : ''}`" :hint="attributeHint(attribute)" persistent-hint clearable :disabled="Boolean(wizard.busy)" variant="outlined" />
          </v-col>
        </v-row>
        <p class="text-body-2 text-medium-emphasis mt-3">Les colonnes non associées sont ignorées. Les valeurs seront vérifiées lors de la prévisualisation.</p>
      </template>
      <template v-if="wizard.file && (wizard.inspection || wizard.format === 'xml')">
        <v-divider class="my-6" />
        <h2 class="text-h6 mb-4">{{ wizard.format === 'xml' ? '2' : '3' }}. Vérifier les changements</h2>
        <v-select v-model="wizard.mode" :items="modes" label="Comportement de l’import" :disabled="Boolean(wizard.busy)" variant="outlined" />
        <p class="text-body-2 text-medium-emphasis mb-4">« Ajouter uniquement » signale les identifiants déjà présents. « Ajouter et mettre à jour » retrouve les objets grâce à leur type et leur identifiant stable. Aucun objet n’est supprimé.</p>
        <v-alert v-if="wizard.mode === 'upsert' && !state.canUpdateCurrentModelData" type="warning" variant="tonal" class="mb-4">Vous ne disposez pas du droit de modification sur ce modèle.</v-alert>
        <v-btn color="primary" :disabled="!wizard.canPreview" :loading="wizard.busy === 'preview'" @click="wizard.generatePreview">Prévisualiser les changements</v-btn>
      </template>
      <v-btn v-if="wizard.busy && wizard.busy !== 'commit'" variant="text" class="ml-3" @click="wizard.cancel">Annuler l’analyse</v-btn>
      <v-alert v-if="wizard.error" type="error" variant="tonal" class="mt-5" role="alert">{{ wizard.error }}</v-alert>
      <v-alert v-if="wizard.success" type="success" variant="tonal" class="mt-5" role="status">{{ wizard.success }}</v-alert>
      <section v-if="wizard.preview" class="mt-6" aria-label="Prévisualisation des changements">
        <h2 class="text-h6 mb-4">Résultat de la prévisualisation</h2>
        <div class="d-flex flex-wrap ga-2 mb-4">
          <v-chip color="success">{{ wizard.preview.summary?.additions || 0 }} ajouts</v-chip>
          <v-chip color="info">{{ wizard.preview.summary?.updates || 0 }} modifications</v-chip>
          <v-chip>{{ wizard.preview.summary?.unchanged || 0 }} inchangés</v-chip>
          <v-chip color="error">{{ wizard.preview.summary?.conflicts || 0 }} conflits</v-chip>
        </div>
        <v-alert v-if="!wizard.preview.valid" type="warning" variant="tonal" class="mb-4">Corrigez les conflits dans le fichier ou la correspondance des colonnes, puis prévisualisez à nouveau. Aucune donnée n’a été importée.</v-alert>
        <div class="import-table" tabindex="0" aria-label="Détail des changements">
          <table>
            <caption>Détail des changements, 25 lignes par page</caption>
            <thead><tr><th scope="col">{{ wizard.format === 'xml' ? 'Élément du fichier' : 'Ligne' }}</th><th scope="col">Élément</th><th scope="col">Changement</th><th scope="col">Avant</th><th scope="col">Après</th><th scope="col">À corriger</th></tr></thead>
            <tbody>
              <tr v-for="(row, index) in wizard.visibleRows" :key="`${wizard.previewPage}-${index}`">
                <td>{{ row.row }}</td><td>{{ row.entityType === 'LINK' ? 'Lien' : 'Objet' }} · {{ row.type }}<span v-if="row.dataId != null"> #{{ row.dataId }}</span><div v-if="row.from && row.to" class="text-caption mt-1">{{ row.from.type }} #{{ row.from.dataId }} → {{ row.to.type }} #{{ row.to.dataId }}</div></td>
                <td>{{ actionLabel(row.action) }}</td><td><pre>{{ valuesLabel(row.before) }}</pre><div v-if="row.workflowBefore" class="text-caption mt-2">Workflow : {{ row.workflowBefore.id }} v{{ row.workflowBefore.version }} · {{ row.workflowBefore.state }}</div></td><td><pre>{{ valuesLabel(row.after) }}</pre><div v-if="row.workflowAfter" class="text-caption mt-2">Workflow : {{ row.workflowAfter.id }} v{{ row.workflowAfter.version }} · {{ row.workflowAfter.state }}</div></td>
                <td><ul v-if="row.errors?.length"><li v-for="(message, n) in row.errors" :key="n">{{ message }}</li></ul><span v-else>—</span></td>
              </tr>
            </tbody>
          </table>
        </div>
        <v-pagination v-if="wizard.preview.rows?.length > 25" v-model="wizard.previewPage" :length="Math.ceil(wizard.preview.rows.length / 25)" :total-visible="5" aria-label="Pages de la prévisualisation" />
        <p class="text-body-2 mt-4 mb-3">Confirmez pour appliquer ces changements ensemble. Si les données ont changé entre-temps, une nouvelle prévisualisation sera demandée.</p>
        <v-btn color="primary" :disabled="!wizard.canCommit" :loading="wizard.busy === 'commit'" @click="wizard.commit">Confirmer l’import</v-btn>
      </section>
      <v-alert v-if="wizard.busy === 'commit'" type="info" variant="tonal" class="mt-4" role="status">Enregistrement en cours…</v-alert>
    </v-card-text>
  </v-card>
</template>

<script setup>
import { computed, reactive } from 'vue';
import { useImportWizard } from '../composables/useImportWizard';
const props = defineProps({ state: { type: Object, required: true } });
const state = props.state;
const wizard = reactive(useImportWizard(state));
const formats = [{ title: 'CSV', value: 'csv' }, { title: 'Excel (.xlsx)', value: 'xlsx' }, { title: 'XML', value: 'xml' }];
const modes = [{ title: 'Ajouter uniquement', value: 'create' }, { title: 'Ajouter et mettre à jour', value: 'upsert' }];
const typeOptions = computed(() => wizard.types.map(type => ({ title: type.name, value: type.name })));
const sheetOptions = computed(() => [{ title: 'Première feuille', value: '' }, ...wizard.sheets.map(name => ({ title: name, value: name }))]);
function attributeLabel(attribute) { return state.formatAttributeLabel?.(attribute.name, attribute) || attribute.name; }
function attributeHint(attribute) {
  const types = { STRING: 'Texte', INTEGER: 'Nombre entier', DOUBLE: 'Nombre décimal', BOOLEAN: 'Vrai ou faux', DATE: 'Date', DATETIME: 'Date et heure' };
  return [types[attribute.type] || attribute.type, attribute.description, attribute.defaultValue ? `Valeur par défaut : ${attribute.defaultValue}` : ''].filter(Boolean).join(' · ');
}
function actionLabel(action) { return { CREATE: 'Ajout', UPDATE: 'Modification', UNCHANGED: 'Inchangé', CONFLICT: 'Conflit' }[action] || action; }
function valuesLabel(value) {
  if (value == null) return '—';
  if (typeof value !== 'object') return String(value);
  return Object.entries(value).map(([key, item]) => `${key} : ${typeof item === 'object' && item !== null ? JSON.stringify(item) : item ?? '—'}`).join('\n') || '—';
}
</script>
<style scoped>
.import-table { overflow-x: auto; max-height: 32rem; }
table { width: 100%; border-collapse: collapse; text-align: left; font-size: .875rem; }
caption { text-align: left; color: rgb(var(--v-theme-on-surface)); padding-bottom: .5rem; }
th, td { padding: .75rem; vertical-align: top; border-bottom: 1px solid rgba(var(--v-border-color), var(--v-border-opacity)); }
th { white-space: nowrap; }
pre { font-family: inherit; white-space: pre-wrap; overflow-wrap: anywhere; min-width: 10rem; }
ul { padding-left: 1rem; min-width: 12rem; }
</style>
