<template>
  <v-card class="mb-4" variant="outlined" rounded="lg">
    <v-card-text>
      <div class="text-subtitle-1 font-weight-bold mb-2">Vues enregistrées</div>
      <v-alert v-if="error" type="error" class="mb-3">{{ error }}</v-alert>
      <v-alert v-if="truncated" type="warning" class="mb-3">Seules les 500 premières vues accessibles sont affichées. Certaines vues supplémentaires ne figurent pas dans cette liste.</v-alert>
      <v-alert v-if="notice" type="success" class="mb-3">{{ notice }}</v-alert>
      <div class="d-flex flex-wrap align-center" style="gap: 12px;">
        <v-select v-model="selectedId" :items="items.map(item => ({ title: `${item.name}${item.shared ? ' · partagée' : ' · privée'}`, value: item.id }))" label="Choisir une vue" clearable hide-details style="min-width: 220px;" :loading="loading" />
        <v-btn :disabled="!selected || busy" @click="open">Appliquer</v-btn>
        <v-btn variant="text" :disabled="loading" @click="load">Actualiser</v-btn>
      </div>
      <div class="d-flex flex-wrap align-center mt-3" style="gap: 12px;">
        <v-text-field v-model="name" label="Nom de la vue" maxlength="120" hide-details style="min-width: 220px;" />
        <v-checkbox v-model="shared" label="Partager avec les lecteurs du modèle" hide-details />
        <v-btn color="primary" :disabled="!name.trim() || busy || !allowed" :loading="busy" @click="save(false)">Enregistrer une copie</v-btn>
        <v-btn v-if="selected?.canEdit" :disabled="!name.trim() || busy" @click="save(true)">Mettre à jour</v-btn>
        <v-btn v-if="selected?.canEdit" color="error" variant="text" :disabled="busy" @click="remove">Supprimer</v-btn>
      </div>
      <div class="text-caption mt-2">Une vue mémorise vos filtres, colonnes et tris. Le tri et les filtres d’attributs portent sur la page chargée.</div>
    </v-card-text>
  </v-card>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
import { useScopedApi } from '../composables/useScopedApi';
import { applyView, captureView } from '../composables/savedViews';
const { state } = defineProps({ state: { type: Object, required: true } });
const truncated = ref(false);
const items = ref([]), selectedId = ref(null), name = ref(''), shared = ref(false), loading = ref(false), busy = ref(false), error = ref(''), notice = ref('');
function reset() { truncated.value = false; items.value = []; selectedId.value = null; name.value = ''; shared.value = false; error.value = ''; notice.value = ''; loading.value = false; busy.value = false; }
const { request, allowed, scope } = useScopedApi(state, reset);
const selected = computed(() => items.value.find(item => item.id === selectedId.value));
watch(selected, item => { name.value = item?.name || ''; shared.value = Boolean(item?.shared); });
async function load() {
  if (!allowed.value) return;
  const captured = scope.value;
  loading.value = true; error.value = '';
  try { const result = await request('views', `/api/views?modelKey=${encodeURIComponent(state.selectedModelKey)}`); if (result) { items.value = result.items || []; truncated.value = Boolean(result.truncated); } }
  catch (failure) { error.value = failure.message; }
  finally { if (scope.value === captured) loading.value = false; }
}
function open() { if (!selected.value) return; try { applyView(state, selected.value.state); notice.value = 'Vue appliquée.'; } catch (failure) { error.value = failure.message; } }
async function save(update) {
  if (!allowed.value || busy.value || !name.value.trim() || (update && !selected.value?.canEdit)) return;
  const captured = scope.value;
  busy.value = true; error.value = ''; notice.value = '';
  try {
    const result = await request('mutation', update ? `/api/views/${encodeURIComponent(selectedId.value)}` : '/api/views', {
      method: update ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name.value.trim(), modelKey: state.selectedModelKey, shared: shared.value, state: captureView(state) })
    });
    if (!result) return;
    await load();
    if (scope.value === captured) { selectedId.value = result.id || result.item?.id || selectedId.value; notice.value = 'Vue enregistrée.'; }
  } catch (failure) { error.value = failure.message; }
  finally { if (scope.value === captured) busy.value = false; }
}
async function remove() {
  if (!selected.value?.canEdit || !window.confirm(`Supprimer la vue « ${selected.value.name} » ?`)) return;
  const captured = scope.value; busy.value = true; error.value = '';
  try {
    const result = await request('mutation', `/api/views/${encodeURIComponent(selectedId.value)}?modelKey=${encodeURIComponent(state.selectedModelKey)}`, { method: 'DELETE' });
    if (!result) return;
    selectedId.value = null; await load();
    if (scope.value === captured) notice.value = 'Vue supprimée.';
  } catch (failure) { error.value = failure.message; }
  finally { if (scope.value === captured) busy.value = false; }
}
watch(scope, load, { immediate: true });
</script>
