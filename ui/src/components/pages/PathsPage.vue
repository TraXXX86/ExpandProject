<template>
  <div class="kicker">Explorer les relations</div>
  <h2 class="headline mb-4">Quel chemin relie ces deux objets ?</h2>
  <v-card rounded="xl"><v-card-text>
    <v-alert v-if="error" type="error" class="mb-4">{{ error }}</v-alert>
    <v-row>
      <v-col v-for="side in ['from', 'to']" :key="side" cols="12" md="6">
        <v-autocomplete v-model="selection[side]" :items="options[side]" item-title="title" item-value="value" :label="side === 'from' ? 'Objet de départ' : 'Objet d’arrivée'" :loading="searching[side]" no-filter clearable @update:search="scheduleSearch(side, $event)" />
        <p v-if="more[side]" class="text-caption">Les 50 premiers résultats sont affichés. Précisez votre recherche.</p>
      </v-col>
      <v-col cols="12" md="3"><v-select v-model="maxDepth" :items="[1, 2, 3, 4, 5, 6]" label="Nombre maximal de liens" /></v-col>
      <v-col cols="12" md="6"><v-select v-model="types" :items="state.createLinkTypeOptions || []" label="Types de liens autorisés (tous par défaut)" multiple chips clearable /></v-col>
      <v-col cols="12" md="3"><v-checkbox v-model="respectDirection" label="Respecter le sens des liens" /></v-col>
    </v-row>
    <p class="text-caption mb-3">La recherche d’objets interroge toutes les données du modèle. Les résultats présentent les chemins les plus courts dans la limite choisie.</p>
    <v-btn color="primary" :disabled="selection.from == null || selection.to == null || !allowed" :loading="loading" @click="find">Chercher les chemins</v-btn>
    <template v-if="result">
      <v-alert v-if="result.truncated" type="warning" class="mt-4">Recherche interrompue à une limite de volume : d’autres chemins peuvent exister.</v-alert>
      <p class="my-4">{{ result.paths?.length || 0 }} chemin(s) affiché(s) · {{ result.visitedObjects }} objets visités · {{ result.scannedEdges }} liens examinés.</p>
      <p v-if="!result.paths?.length">{{ result.truncated ? 'Aucun chemin trouvé dans la portion explorée.' : 'Aucun chemin trouvé avec ces critères et cette profondeur.' }}</p>
      <v-card v-for="(path, index) in result.paths" :key="index" variant="outlined" class="mb-3"><v-card-text>
        <h3 class="text-subtitle-1 mb-3">Chemin {{ index + 1 }} · {{ path.links.length }} lien(s)</h3>
        <ol class="path-steps">
          <li v-for="(object, step) in path.objects" :key="`${object.id}-${step}`">
            <div><strong>{{ objectLabel(object) }}</strong><span class="text-caption"> · {{ object.type }} · ID {{ object.id }}</span></div>
            <div v-if="path.links[step]" class="path-link">{{ linkDirection(path.links[step], object) }} {{ state.getLinkTypeLabel?.(path.links[step].type) || path.links[step].type }}</div>
          </li>
        </ol>
      </v-card-text></v-card>
    </template>
  </v-card-text></v-card>
</template>
<script setup>
import { onBeforeUnmount, reactive, ref, watch } from 'vue';
import { useScopedApi } from '../../composables/useScopedApi';
const { state } = defineProps({ state: { type: Object, required: true } });
const selection = reactive({ from: null, to: null }), options = reactive({ from: [], to: [] }), searching = reactive({ from: false, to: false }), more = reactive({ from: false, to: false });
const timers = {}, maxDepth = ref(4), types = ref([]), respectDirection = ref(true), result = ref(null), loading = ref(false), error = ref('');
function clearTimers() { Object.values(timers).forEach(clearTimeout); }
function reset() {
  clearTimers(); selection.from = null; selection.to = null; options.from = []; options.to = []; searching.from = false; searching.to = false; more.from = false; more.to = false; result.value = null; loading.value = false; error.value = ''; types.value = [];
}
const { request, allowed, scope, cancel } = useScopedApi(state, reset);
function objectLabel(object) {
  return state.getObjectPrimaryLabel?.(object) || (object.attributes || []).slice(0, 2).map(attribute => attribute.value).filter(Boolean).join(' · ') || `${object.type} ${object.id}`;
}
function linkDirection(link, object) {
  if (link.directed === false || state.isDirectedLink?.(link.type) === false) return '↔';
  return String(link.fromId) === String(object.id) ? '→' : '←';
}
async function search(side, query = '') {
  if (!allowed.value) return;
  const captured = scope.value; searching[side] = true;
  try {
    const params = new URLSearchParams({ modelKey: state.selectedModelKey, offset: '0', limit: '50', q: query || '', searchMode: 'contains' });
    const payload = await request(`objects:${side}`, `/api/data?${params}`);
    if (!payload) return;
    const selected = options[side].find(item => item.value === selection[side]);
    const next = (payload.objects || []).map(object => ({ value: object.id, title: `${objectLabel(object)} · ${object.type} · ID ${object.id}` }));
    if (selected && !next.some(item => item.value === selected.value)) next.unshift(selected);
    options[side] = next; more[side] = Boolean(payload.hasMore);
  } catch (failure) { error.value = failure.message; }
  finally { if (scope.value === captured) searching[side] = false; }
}
function scheduleSearch(side, query) {
  clearTimeout(timers[side]); cancel(`objects:${side}`);
  timers[side] = setTimeout(() => search(side, query), 300);
}
async function find() {
  if (!allowed.value || selection.from == null || selection.to == null) return;
  const captured = scope.value; loading.value = true; error.value = ''; result.value = null;
  const query = new URLSearchParams({ modelKey: state.selectedModelKey, fromId: String(selection.from), toId: String(selection.to), maxDepth: String(maxDepth.value), respectDirection: String(respectDirection.value) });
  if (types.value?.length) query.set('types', types.value.join(','));
  try { const payload = await request('paths', `/api/graph/paths?${query}`); if (payload) result.value = payload; }
  catch (failure) { error.value = failure.message; }
  finally { if (scope.value === captured) loading.value = false; }
}
watch([() => selection.from, () => selection.to, maxDepth, types, respectDirection], () => { cancel('paths'); result.value = null; loading.value = false; }, { deep: true });
watch(scope, () => { search('from'); search('to'); }, { immediate: true });
onBeforeUnmount(clearTimers);
</script>
<style scoped>
.path-steps { padding-left: 28px; }
.path-steps li { padding: 6px 0; }
.path-link { padding: 8px 0 0 12px; color: #1c4e80; }
</style>
