<template>
  <section class="my-4" aria-label="Workflow de l’objet">
    <div class="d-flex align-center flex-wrap" style="gap: 12px">
      <span class="text-subtitle-2">Statut</span>
      <span v-if="loading" role="status">Chargement…</span>
      <v-chip v-else-if="details" :color="details.workflow?.terminal ? 'success' : 'primary'" variant="tonal">{{ details.workflow?.stateLabel || details.workflow?.state || 'Sans workflow' }}</v-chip>
      <span v-if="details?.workflow" class="text-caption">{{ details.workflow.label || details.workflow.id }} · v{{ details.workflow.version }}</span>
      <v-btn v-if="transitions.length === 1" color="primary" :disabled="loading || busy" :loading="busy" @click="choose(transitions[0])">{{ transitions[0].label }}</v-btn>
      <v-menu v-else-if="transitions.length > 1">
        <template #activator="{ props: menuProps }"><v-btn v-bind="menuProps" color="primary" :disabled="loading || busy">Changer le statut</v-btn></template>
        <v-list><v-list-item v-for="transition in transitions" :key="transition.id" :title="transition.label" :subtitle="`Vers ${transition.toLabel || transition.to}`" @click="choose(transition)" /></v-list>
      </v-menu>
    </div>
    <v-alert v-if="notice" type="info" class="mt-3" aria-live="polite">{{ notice }}</v-alert>
    <v-alert v-if="error" type="error" class="mt-3">{{ error }} <v-btn variant="text" :disabled="busy || loading" @click="load">Actualiser</v-btn></v-alert>
    <v-dialog :model-value="Boolean(selected)" max-width="480" :persistent="busy" @update:model-value="value => { if (!value && !busy) selected = null; }">
      <v-card v-if="selected" title="Confirmer le changement de statut">
        <v-card-text>Passer de <strong>{{ details?.workflow?.stateLabel || details?.workflow?.state }}</strong> à <strong>{{ selected.toLabel || selected.to }}</strong> avec l’action « {{ selected.label }} » ?</v-card-text>
        <v-card-actions><v-btn :disabled="busy" @click="selected = null">Annuler</v-btn><v-btn color="primary" :loading="busy" :disabled="loading || busy" @click="confirm">Confirmer</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
  </section>
</template>
<script setup>
import { VDialog, VCardActions } from 'vuetify/components';
import { useObjectWorkflow } from '../composables/useObjectWorkflow';
const props = defineProps({ state: { type: Object, required: true }, object: { type: Object, required: true } });
function changed(payload) {
  const objects = [...(props.state.dataObjects || []), ...(props.state.pageObjects || []), props.state.selectedObject, props.state.tableSelectedObject];
  objects.filter(object => object && String(object.id) === String(payload.objectId)).forEach(object => { object.workflow = payload.workflow; });
  if (props.state.workflowStatus) props.state.refreshData?.();
}
const { details, loading, busy, error, notice, selected, transitions, load, choose, confirm } = useObjectWorkflow(props.state, () => props.object, changed);
</script>
