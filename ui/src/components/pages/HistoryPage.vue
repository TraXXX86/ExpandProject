<template>
  <div class="kicker">Traçabilité</div>
  <h2 class="headline mb-4">Historique des modifications</h2>
  <v-card rounded="xl"><v-card-text>
    <v-alert v-if="error" type="error" class="mb-4">{{ error }}</v-alert>
    <v-row>
      <v-col cols="12" md="3"><v-select v-model="entityType" label="Élément" :items="entityOptions" clearable /></v-col>
      <v-col cols="12" md="3"><v-select v-model="action" label="Action" :items="actionOptions" clearable /></v-col>
      <v-col cols="12" md="4"><v-text-field v-model="entityId" label="Identifiant stable de l’élément" clearable @keyup.enter="refresh" /></v-col>
      <v-col cols="12" md="2"><v-btn color="primary" :loading="loading" :disabled="!allowed" @click="refresh">Rechercher</v-btn></v-col>
    </v-row>
    <p class="text-caption mb-4">Les opérations enregistrées depuis l’activation de l’historique apparaissent ici. L’historique permet de comparer les changements ; il ne les annule pas.</p>
    <p v-if="!loading && !items.length && !error">Aucune modification pour ces filtres.</p>
    <article v-for="item in items" :key="item.id" class="history-entry">
      <div class="d-flex flex-wrap align-center" style="gap: 10px;">
        <v-chip size="small">{{ label(actionOptions, item.action) }}</v-chip>
        <strong>{{ label(entityOptions, item.entityType) }}</strong>
        <time :datetime="item.timestamp">{{ formatDate(item.timestamp) }}</time>
      </div>
      <p class="my-2">Par {{ item.actor }}<span v-if="item.effectiveUser && item.effectiveUser !== item.actor">, agissant en tant que {{ item.effectiveUser }}</span></p>
      <p class="text-caption">Élément : {{ item.entityId }} · Opération : {{ item.operationId }}</p>
      <details class="mt-2">
        <summary>Comparer avant / après</summary>
        <v-alert v-if="item.before?.snapshotOmitted || item.after?.snapshotOmitted" type="warning" class="mt-3">Une version avant ou après dépasse la limite de 1 Mio et n’a pas été conservée en détail. La comparaison de cette modification est incomplète.</v-alert>
        <v-row class="mt-1"><v-col cols="12" md="6"><h3 class="text-subtitle-2">Avant</h3><pre>{{ snapshot(item.before) }}</pre></v-col><v-col cols="12" md="6"><h3 class="text-subtitle-2">Après</h3><pre>{{ snapshot(item.after) }}</pre></v-col></v-row>
      </details>
    </article>
    <div class="d-flex align-center justify-space-between mt-4">
      <v-btn :disabled="offset === 0 || loading" @click="load(Math.max(0, offset - limit))">Précédent</v-btn>
      <span>{{ total ? `${offset + 1}–${offset + items.length} sur ${total}` : '0 modification' }}</span>
      <v-btn :disabled="!hasMore || loading" @click="load(offset + limit)">Suivant</v-btn>
    </div>
  </v-card-text></v-card>
</template>
<script setup>
import { ref, watch } from 'vue';
import { useScopedApi } from '../../composables/useScopedApi';
const { state } = defineProps({ state: { type: Object, required: true } });
const entityOptions = [{ title: 'Objet', value: 'OBJECT' }, { title: 'Lien', value: 'LINK' }, { title: 'Modèle', value: 'MODEL' }, { title: 'Import', value: 'IMPORT' }, { title: 'Workflow', value: 'WORKFLOW' }];
const actionOptions = [{ title: 'Création', value: 'CREATE' }, { title: 'Modification', value: 'UPDATE' }, { title: 'Suppression', value: 'DELETE' }, { title: 'Import', value: 'IMPORT' }, { title: 'Changement de statut', value: 'TRANSITION' }, { title: 'Initialisation du workflow', value: 'INITIALIZE' }, { title: 'Migration du workflow', value: 'MIGRATE' }, { title: 'Activation du workflow', value: 'ACTIVATE' }, { title: 'Désactivation du workflow', value: 'DEACTIVATE' }];
const items = ref([]), error = ref(''), loading = ref(false), total = ref(0), offset = ref(0), hasMore = ref(false);
const entityType = ref(null), action = ref(null), entityId = ref(''), limit = 25;
let activeFilters = {};
const { request, allowed, scope } = useScopedApi(state, () => { items.value = []; error.value = ''; loading.value = false; total.value = 0; offset.value = 0; hasMore.value = false; entityType.value = null; action.value = null; entityId.value = ''; activeFilters = {}; });
const label = (options, value) => options.find(option => option.value === value)?.title || value;
const snapshot = value => value == null ? 'Absent' : value.snapshotOmitted ? 'Détail non conservé : cette version dépasse la limite de taille.' : JSON.stringify(value, null, 2);
const formatDate = value => Number.isNaN(Date.parse(value)) ? value : new Date(value).toLocaleString('fr-FR');
async function load(nextOffset = 0) {
  if (!allowed.value) return;
  const captured = scope.value; loading.value = true; error.value = '';
  const query = new URLSearchParams({ modelKey: state.selectedModelKey, offset: String(nextOffset), limit: String(limit) });
  if (nextOffset === 0) activeFilters = { entityType: entityType.value, action: action.value, entityId: entityId.value?.trim() };
  Object.entries(activeFilters).forEach(([key, value]) => { if (value) query.set(key, value); });
  try { const result = await request('history', `/api/history?${query}`); if (result) { items.value = result.items || []; offset.value = result.offset ?? nextOffset; total.value = result.total || 0; hasMore.value = Boolean(result.hasMore); } }
  catch (failure) { items.value = []; error.value = failure.message; }
  finally { if (scope.value === captured) loading.value = false; }
}
const refresh = () => load(0);
watch(scope, refresh, { immediate: true });
</script>
<style scoped>
.history-entry { padding: 18px 0; border-bottom: 1px solid #ddd; }
summary { cursor: pointer; font-weight: 600; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 420px; overflow: auto; background: #f6f4f0; padding: 12px; border-radius: 8px; font-size: 0.8rem; }
</style>
