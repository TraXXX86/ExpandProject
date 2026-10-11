<template>
  <div class="kicker">Administration du modèle</div>
  <h2 class="headline mb-4">Workflows des objets</h2>
  <v-alert type="info" class="mb-4">Chargez les workflows indépendamment du modèle. L’activation concerne les nouveaux objets ; les objets existants évoluent uniquement après une initialisation ou une migration confirmée.</v-alert>
  <v-alert v-if="admin.error" type="error" class="mb-4" role="alert">{{ admin.error }}</v-alert>
  <v-alert v-if="admin.success" type="success" class="mb-4">{{ admin.success }}</v-alert>
  <v-card rounded="xl" class="mb-4"><v-card-text>
    <div class="d-flex align-center justify-space-between"><h3>Versions disponibles</h3><v-btn :loading="admin.busy === 'load'" :disabled="!admin.allowed || Boolean(admin.busy)" @click="admin.load">Actualiser</v-btn></div>
    <v-alert v-if="!admin.workflows.length" type="info" variant="tonal" class="my-3">Aucun workflow chargé pour ce modèle.</v-alert>
    <v-expansion-panels class="my-3"><v-expansion-panel v-for="item in admin.workflows" :key="admin.key(item)" :title="`${item.label || item.id} — ${item.version}`">
      <v-expansion-panel-text>
        <p>Types concernés : {{ item.objectTypes.map(type => `${type.name}${type.includeSubtypes ? ' et ses sous-types' : ''}`).join(', ') }}</p>
        <p class="my-2">Statut initial : {{ item.states.find(status => status.code === item.initialState)?.label || item.initialState }}</p>
        <v-chip v-for="status in item.states" :key="status.code" class="mr-2 mb-2">{{ status.label || status.code }}{{ status.terminal ? ' · terminal' : '' }}</v-chip>
        <ul class="ml-5 my-2"><li v-for="transition in item.transitions" :key="transition.id">{{ transition.label }} : {{ statusLabel(item, transition.from) }} → {{ statusLabel(item, transition.to) }}</li></ul>
        <v-btn v-if="admin.canManage" color="error" variant="text" :disabled="Boolean(admin.busy)" @click="deleteTarget = item">Supprimer cette version</v-btn>
      </v-expansion-panel-text>
    </v-expansion-panel></v-expansion-panels>
    <h4 v-if="admin.bindings.length" class="mb-2">Affectations pour les nouveaux objets</h4>
    <p v-for="binding in admin.bindings" :key="binding.objectType">{{ binding.objectType }} → {{ binding.workflowId }} — {{ binding.workflowVersion }} <v-btn v-if="admin.canManage" size="small" variant="text" :disabled="Boolean(admin.busy)" @click="deactivateType = binding.objectType">Retirer l’affectation</v-btn></p>
    <template v-if="admin.canManage">
      <v-file-input label="Fichier XML du workflow" accept=".xml,application/xml,text/xml" :model-value="admin.file" :disabled="Boolean(admin.busy)" class="mt-4" @update:model-value="admin.selectFile" />
      <v-btn color="primary" :loading="admin.busy === 'upload'" :disabled="!admin.file || Boolean(admin.busy)" @click="admin.upload">Charger le workflow</v-btn>
    </template>
  </v-card-text></v-card>
  <v-card v-if="admin.canManage && admin.workflows.length" rounded="xl"><v-card-text>
    <h3 class="mb-4">Préparer une affectation ou une migration</h3>
    <v-select v-model="admin.selected" label="Workflow cible" :items="admin.choices" :disabled="Boolean(admin.busy)" />
    <v-select v-model="admin.operation" label="Opération" :items="operations" :disabled="Boolean(admin.busy)" />
    <template v-if="admin.operation !== 'activate'">
      <v-select v-model="admin.objectType" label="Limiter à un type d’objet" :items="targetTypes" clearable :disabled="Boolean(admin.busy)" />
      <template v-if="admin.operation === 'migrate'">
        <v-select v-model="admin.source" label="Workflow source" :items="admin.choices" :disabled="Boolean(admin.busy)" />
        <v-select v-for="status in admin.origin?.states || []" :key="status.code" v-model="admin.mapping[status.code]" :label="`Remplacer le statut ${status.label || status.code} par`" :items="targetStates" :disabled="Boolean(admin.busy)" />
      </template>
      <v-btn :disabled="!canFind || Boolean(admin.busy)" :loading="admin.busy === 'candidates'" class="mb-3" @click="admin.loadCandidates">Rechercher les objets concernés</v-btn>
      <v-data-table v-model="admin.selectedIds" show-select item-value="id" :headers="candidateHeaders" :items="admin.candidates" :items-per-page="10" items-per-page-text="Objets par page" page-text="{0}–{1} sur {2}" :disabled="Boolean(admin.busy)">
        <template #item.fromState="{ item }">{{ fromStateLabel(item) }}</template>
        <template #item.toState="{ item }">{{ statusLabel(admin.target, item.toState) }}</template>
        <template #no-data>Recherchez les objets concernés, puis sélectionnez ceux à modifier.</template>
      </v-data-table>
      <p class="my-3">{{ admin.selectedIds.length }} objet(s) sélectionné(s). Seuls ces objets seront modifiés.</p>
    </template>
    <v-btn color="primary" :disabled="!admin.ready" :loading="admin.busy === 'preview'" @click="admin.generatePreview">Prévisualiser les changements</v-btn>
    <v-card v-if="admin.preview" variant="outlined" class="mt-4"><v-card-text>
      <h3 class="mb-3">Changements à confirmer</h3>
      <template v-if="admin.operation === 'activate'">
        <p>Types affectés : {{ admin.preview.objectTypes?.join(', ') }}</p>
        <p>{{ admin.preview.existingObjects || 0 }} objet(s) existant(s) restent inchangés.</p>
        <p v-for="binding in admin.preview.bindings || []" :key="binding.objectType">{{ binding.objectType }} : {{ binding.workflowId }} — {{ binding.workflowVersion }} → {{ admin.target?.id }} — {{ admin.target?.version }}</p>
        <p class="mt-2">Les nouveaux objets commenceront au statut initial du workflow cible.</p>
      </template>
      <template v-else>
        <p>{{ admin.preview.items?.length || 0 }} objet(s) seront modifiés.</p>
        <v-data-table :headers="candidateHeaders" :items="admin.preview.items || []" :items-per-page="10" items-per-page-text="Objets par page" page-text="{0}–{1} sur {2}"><template #item.fromState="{ item }">{{ fromStateLabel(item) }}</template><template #item.toState="{ item }">{{ statusLabel(admin.target, item.toState) }}</template></v-data-table>
      </template>
      <v-btn color="primary" class="mt-3" :disabled="!admin.canConfirm" :loading="admin.busy === 'commit'" @click="admin.confirm">Confirmer les changements</v-btn>
    </v-card-text></v-card>
  </v-card-text></v-card>
  <v-dialog v-model="deactivateOpen" max-width="520"><v-card title="Retirer cette affectation ?"><v-card-text>Les nouveaux objets de type {{ deactivateType }} seront créés sans workflow. Les objets existants conservent leur workflow et leur statut.</v-card-text><v-card-actions><v-btn @click="deactivateType = null">Annuler</v-btn><v-btn color="primary" @click="deactivate">Confirmer le retrait</v-btn></v-card-actions></v-card></v-dialog>
  <v-dialog v-model="deleteOpen" max-width="520"><v-card title="Supprimer cette version ?"><v-card-text>La version {{ deleteTarget?.version }} de {{ deleteTarget?.label || deleteTarget?.id }} sera supprimée. Une version active ou utilisée par des objets ne peut pas être supprimée.</v-card-text><v-card-actions><v-btn @click="deleteTarget = null">Annuler</v-btn><v-btn color="error" @click="remove">Confirmer la suppression</v-btn></v-card-actions></v-card></v-dialog>
</template>
<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { VDialog, VCardActions, VExpansionPanels, VExpansionPanel, VExpansionPanelText } from 'vuetify/components';
import { useWorkflowAdmin } from '../../composables/useWorkflowAdmin';
const { state } = defineProps({ state: { type: Object, required: true } });
const admin = reactive(useWorkflowAdmin(state));
const deleteTarget = ref(null), deactivateType = ref(null);
const deactivateOpen = computed({ get: () => Boolean(deactivateType.value), set: value => { if (!value) deactivateType.value = null; } });
const deleteOpen = computed({ get: () => Boolean(deleteTarget.value), set: value => { if (!value) deleteTarget.value = null; } });
const operations = [{ title: 'Activer pour les nouveaux objets', value: 'activate' }, { title: 'Initialiser des objets sans workflow', value: 'initialize' }, { title: 'Migrer des objets vers cette version', value: 'migrate' }];
const targetStates = computed(() => (admin.target?.states || []).map(item => ({ title: item.label || item.code, value: item.code })));
const targetTypes = computed(() => {
  const declarations = admin.target?.objectTypes || [];
  const definitions = state.modelDetails?.objectTypes || [];
  return definitions.filter(type => declarations.some(declaration => {
    if (declaration.name === type.name) return true;
    if (!declaration.includeSubtypes) return false;
    const seen = new Set(); let parent = type.parent;
    while (parent && !seen.has(parent)) {
      if (parent === declaration.name) return true;
      seen.add(parent); parent = definitions.find(item => item.name === parent)?.parent;
    }
    return false;
  })).map(item => item.name);
});
const canFind = computed(() => admin.target && (admin.operation !== 'migrate' || (admin.origin && admin.origin.states.every(item => admin.mapping[item.code]))));
const candidateHeaders = [{ title: 'Identifiant', key: 'id' }, { title: 'Type', key: 'objectType' }, { title: 'Statut actuel', key: 'fromState' }, { title: 'Nouveau statut', key: 'toState' }];
const statusLabel = (workflow, code) => workflow?.states?.find(item => item.code === code)?.label || code;
const fromStateLabel = item => item.fromState ? statusLabel(admin.workflows.find(value => value.id === item.fromWorkflowId && value.version === item.fromVersion) || admin.origin, item.fromState) : 'Sans workflow';
async function deactivate() { const type = deactivateType.value; deactivateType.value = null; await admin.deactivate([type]); }
async function remove() { const item = deleteTarget.value; deleteTarget.value = null; await admin.remove(item); }
watch(() => admin.scope, () => { deleteTarget.value = null; deactivateType.value = null; admin.load(); }, { immediate: true });
</script>
