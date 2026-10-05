<template>
  <div class="kicker">Qualité des données</div>
  <h2 class="headline mb-4">Repérez les données à vérifier</h2>
  <v-card rounded="xl"><v-card-text>
    <v-btn color="primary" :loading="loading" :disabled="!allowed" @click="load">Actualiser le diagnostic</v-btn>
    <v-alert v-if="error" type="error" class="mt-4">{{ error }}</v-alert>
    <template v-if="result">
      <p class="mt-4">{{ result.objectCountExact ? '' : 'Au moins ' }}{{ result.objectCount }} objets et {{ result.linkCountExact ? '' : 'au moins ' }}{{ result.linkCount }} liens. Analyse de {{ result.scannedObjects }} objets et {{ result.scannedLinks }} liens.</p>
      <v-alert v-if="result.truncated" type="warning" class="my-4">Analyse partielle : une limite de volume a été atteinte. Les compteurs ci-dessous sont des minimums ; d’autres anomalies peuvent exister.</v-alert>
      <v-row class="my-2"><v-col v-for="category in categories" :key="category.value" cols="12" md="4"><v-card variant="outlined"><v-card-text><div class="text-h4">{{ result.issueCounts?.[category.value] ?? 0 }}</div><div>{{ category.title }}</div></v-card-text></v-card></v-col></v-row>
      <p class="text-caption mb-3">{{ result.duplicateHeuristic || 'Les doublons proposés sont des pistes à vérifier avant toute suppression.' }}</p>
      <v-alert v-if="result.samplesTruncated" type="info" class="mb-3">Seul un échantillon des anomalies est affiché. Les compteurs portent sur toute la portion analysée.</v-alert>
      <v-select v-model="category" :items="categories" label="Catégorie" clearable />
      <v-data-table :headers="headers" :items="issues" :items-per-page="10">
        <template #item.severity="{ item }"><v-chip size="small" :color="severityColor(item.severity)">{{ severityLabel(item.severity) }}</v-chip></template>
        <template #item.category="{ item }">{{ categories.find(value => value.value === item.category)?.title || item.category }}</template>
        <template #item.entity="{ item }">{{ item.entity === 'link' ? 'Lien' : 'Objet' }}</template>
        <template #item.reason="{ item }">{{ item.reason }}<span v-if="item.relatedIds?.length"> · Éléments associés : {{ item.relatedIds.join(', ') }}</span></template>
        <template #no-data>{{ category ? 'Aucune anomalie affichée dans cette catégorie.' : result.truncated ? 'Aucune anomalie dans la portion analysée.' : 'Aucune anomalie détectée par ces contrôles.' }}</template>
      </v-data-table>
    </template>
  </v-card-text></v-card>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
import { useScopedApi } from '../../composables/useScopedApi';
const { state } = defineProps({ state: { type: Object, required: true } });
const result = ref(null), error = ref(''), loading = ref(false), category = ref(null);
const { request, allowed, scope } = useScopedApi(state, () => { result.value = null; error.value = ''; loading.value = false; category.value = null; });
const categories = [{ title: 'Objets isolés', value: 'isolated' }, { title: 'Doublons potentiels', value: 'potential_duplicate' }, { title: 'Écarts au modèle', value: 'schema_drift' }];
const headers = [{ title: 'Niveau', key: 'severity' }, { title: 'Catégorie', key: 'category' }, { title: 'Élément', key: 'entity' }, { title: 'Type', key: 'type' }, { title: 'Identifiant', key: 'id' }, { title: 'Observation', key: 'reason', sortable: false }];
const issues = computed(() => (result.value?.issues || []).filter(issue => !category.value || issue.category === category.value));
const severityColor = value => ({ info: 'info', warning: 'warning', error: 'error' }[value] || 'info');
const severityLabel = value => ({ info: 'Information', warning: 'À vérifier', error: 'Erreur' }[value] || value);
async function load() {
  if (!allowed.value) return;
  const captured = scope.value; loading.value = true; error.value = ''; result.value = null;
  try { const payload = await request('quality', `/api/quality?modelKey=${encodeURIComponent(state.selectedModelKey)}`); if (payload) result.value = payload; }
  catch (failure) { error.value = failure.message; }
  finally { if (scope.value === captured) loading.value = false; }
}
watch(scope, load, { immediate: true });
</script>
