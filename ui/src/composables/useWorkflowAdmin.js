import { computed, onBeforeUnmount, ref, watch } from 'vue';

/** Every mutation is bound to the exact model, session, permissions and reviewed input. */
export function useWorkflowAdmin(state) {
  const workflows = ref([]), bindings = ref([]), file = ref(null), selected = ref('');
  const operation = ref('activate'), objectType = ref(''), source = ref(''), mapping = ref({});
  const candidates = ref([]), selectedIds = ref([]), preview = ref(null);
  const error = ref(''), success = ref(''), busy = ref('');
  let generation = 0, controller;
  const scope = computed(() => JSON.stringify([state.selectedModelKey, state.authEpoch, state.isAuthenticated,
    state.authMeta?.actorUsername, state.authMeta?.effectiveUsername, state.authMeta?.expiresAt,
    state.canReadCurrentModelData, state.canManageCurrentWorkflows]));
  const allowed = computed(() => Boolean(state.selectedModelKey && state.isAuthenticated && state.canReadCurrentModelData));
  const canManage = computed(() => Boolean(allowed.value && state.canManageCurrentWorkflows));
  const key = item => `${item.id}@${item.version}`;
  const target = computed(() => workflows.value.find(item => key(item) === selected.value));
  const origin = computed(() => workflows.value.find(item => key(item) === source.value));
  const choices = computed(() => workflows.value.map(item => ({ title: `${item.label || item.id} — ${item.version}`, value: key(item) })));
  const types = computed(() => (state.modelDetails?.objectTypes || []).map(item => item.name));
  const ready = computed(() => Boolean(canManage.value && target.value && !busy.value &&
    (operation.value === 'activate' || (selectedIds.value.length && (operation.value !== 'migrate' ||
      (origin.value && (origin.value.states || []).every(item => mapping.value[item.code])))))));
  const canConfirm = computed(() => Boolean(canManage.value && preview.value?.previewHash && preview.value?.valid !== false && !busy.value));
  function invalidate() {
    generation++; controller?.abort(); controller = null; busy.value = ''; preview.value = null; error.value = ''; success.value = '';
  }
  function reset() {
    invalidate(); workflows.value = []; bindings.value = []; file.value = null; selected.value = ''; source.value = '';
    objectType.value = ''; mapping.value = {}; candidates.value = []; selectedIds.value = [];
  }
  watch(scope, reset, { flush: 'sync' });
  watch([operation, objectType, source, selected], () => { invalidate(); candidates.value = []; selectedIds.value = []; mapping.value = {}; }, { flush: 'sync' });
  watch([file, mapping, selectedIds], invalidate, { flush: 'sync', deep: true });
  onBeforeUnmount(invalidate);
  async function run(label, path, options, receive) {
    if (!allowed.value || (options?.method && !canManage.value) || busy.value) return;
    const epoch = generation, captured = scope.value, abort = new AbortController();
    controller = abort; busy.value = label; error.value = ''; success.value = '';
    try {
      const response = await state.apiFetch(path, { ...options, signal: abort.signal });
      const payload = await state.readJson(response);
      if (epoch !== generation || captured !== scope.value || abort.signal.aborted) return;
      if (!response.ok) {
        if (response.status === 409 && label === 'commit') { preview.value = null; throw new Error('Les données ont changé depuis la prévisualisation. Prévisualisez à nouveau avant de confirmer.'); }
        throw new Error(payload?.error || `La requête a échoué (${response.status}).`);
      }
      receive(payload);
      return true;
    } catch (cause) {
      if (epoch === generation && captured === scope.value && !abort.signal.aborted) error.value = cause.message;
    } finally { if (epoch === generation && captured === scope.value) { busy.value = ''; controller = null; } }
  }
  const query = () => `modelKey=${encodeURIComponent(state.selectedModelKey)}`;
  const json = body => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  async function load() {
    return run('load', `/api/workflows?${query()}`, {}, payload => { workflows.value = payload.items || []; bindings.value = payload.bindings || []; });
  }
  function selectFile(value) { file.value = Array.isArray(value) ? value[0] || null : value || null; }
  async function upload() {
    if (!file.value || !canManage.value) return;
    const body = new FormData(); body.append('workflowFile', file.value); body.append('modelKey', state.selectedModelKey);
    const captured = scope.value;
    const applied = await run('upload', '/api/workflows', { method: 'POST', body }, () => { success.value = 'Workflow chargé. Vous pouvez maintenant l’affecter à un type d’objet.'; });
    if (applied && captured === scope.value) { const message = success.value; await load(); if (captured === scope.value) success.value = message; }
  }
  function payload(includeIds = true) {
    return { modelKey: state.selectedModelKey, id: target.value?.id, version: target.value?.version,
      ...(operation.value !== 'activate' ? { mode: operation.value, objectTypes: objectType.value ? [objectType.value] : [], ...(includeIds ? { objectIds: [...selectedIds.value] } : {}) } : {}),
      ...(operation.value === 'migrate' ? { sourceId: origin.value?.id, sourceVersion: origin.value?.version, stateMapping: { ...mapping.value } } : {}) };
  }
  const actionPath = () => `/api/workflows/${operation.value === 'activate' ? 'activation' : 'migration'}`;
  async function loadCandidates() {
    if (!canManage.value || !target.value || operation.value === 'activate' || (operation.value === 'migrate' && (!origin.value || !origin.value.states.every(item => mapping.value[item.code])))) return;
    preview.value = null; selectedIds.value = [];
    return run('candidates', `${actionPath()}/preview`, json(payload(false)), result => { candidates.value = result.items || []; if (result.hasMore) error.value = 'La liste est limitée à 1 000 objets. Traitez cette sélection puis recherchez les suivants.'; });
  }
  function generatePreview() {
    if (!ready.value) return;
    preview.value = null;
    const reviewed = payload();
    return run('preview', `${actionPath()}/preview`, json(reviewed), result => { preview.value = { ...result, request: reviewed, path: actionPath() }; });
  }
  async function confirm() {
    if (!canConfirm.value) return;
    const body = { ...preview.value.request, previewHash: preview.value.previewHash };
    const captured = scope.value;
    const applied = await run('commit', `${preview.value.path}/commit`, json(body), () => { preview.value = null; success.value = 'Changements de workflow enregistrés.'; });
    if (applied && captured === scope.value) { const message = success.value; await load(); if (captured === scope.value) { success.value = message; await state.refreshData?.(state.selectedModelKey); } }
  }
  async function deactivate(objectTypes) {
    if (!canManage.value || !objectTypes?.length) return;
    const captured = scope.value;
    const applied = await run('deactivate', '/api/workflows/deactivate', json({ modelKey: state.selectedModelKey, objectTypes }), () => { success.value = 'Affectation retirée. Les objets existants conservent leur workflow.'; });
    if (applied && captured === scope.value) { const message = success.value; await load(); if (captured === scope.value) success.value = message; }
  }
  async function remove(item) {
    if (!canManage.value || !item) return;
    const captured = scope.value;
    const applied = await run('delete', '/api/workflows', { ...json({ modelKey: state.selectedModelKey, id: item.id, version: item.version }), method: 'DELETE' }, () => { success.value = 'Version supprimée.'; });
    if (applied && captured === scope.value) { const message = success.value; await load(); if (captured === scope.value) success.value = message; }
  }
  return { workflows, bindings, file, selected, operation, objectType, source, mapping, candidates, selectedIds, preview,
    error, success, busy, scope, allowed, canManage, target, origin, choices, types, ready, canConfirm, key,
    load, selectFile, upload, loadCandidates, generatePreview, confirm, remove, deactivate, invalidate };
}
