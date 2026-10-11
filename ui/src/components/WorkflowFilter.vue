<template>
  <v-row>
    <v-col cols="12" md="6"><v-select v-model="state.workflowId" :items="workflows" label="Workflow" clearable :disabled="!allowed" @update:model-value="state.workflowStatus = ''" /></v-col>
    <v-col cols="12" md="6"><v-select v-model="state.workflowStatus" :items="statuses" label="Statut du workflow" clearable :disabled="!allowed" /></v-col>
  </v-row>
  <v-alert v-if="error" type="warning" class="mb-3">{{ error }} <v-btn variant="text" @click="load">Réessayer</v-btn></v-alert>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
import { useScopedApi } from '../composables/useScopedApi';
const { state } = defineProps({ state: { type: Object, required: true } });
const catalog = ref([]), error = ref('');
const { request, allowed, scope } = useScopedApi(state, () => { catalog.value = []; error.value = ''; });
const workflows = computed(() => [...new Map(catalog.value.map(item => [item.id, { title: item.label || item.id, value: item.id }])).values()]);
const statuses = computed(() => {
  const values = new Map();
  catalog.value.filter(item => !state.workflowId || item.id === state.workflowId).forEach(item => (item.states || []).forEach(status => {
    const title = status.label || status.code;
    values.set(status.code, { title: title === status.code ? title : `${title} (${status.code})`, value: status.code });
  }));
  if (!state.workflowId) values.set('__unassigned__', { title: 'Sans workflow', value: '__unassigned__' });
  if (state.workflowStatus && !values.has(state.workflowStatus)) values.set(state.workflowStatus, { title: state.workflowStatus, value: state.workflowStatus });
  return [...values.values()];
});
async function load() {
  if (!allowed.value) return;
  error.value = '';
  try { const payload = await request('workflow-filter', `/api/workflows?modelKey=${encodeURIComponent(state.selectedModelKey)}`); if (payload) catalog.value = payload.items || []; }
  catch (failure) { error.value = failure.message; }
}
watch(scope, load, { immediate: true });
</script>
