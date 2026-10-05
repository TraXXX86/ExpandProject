import { computed, onBeforeUnmount, ref, watch } from 'vue';

/** The preview belongs to one file, mapping, model and effective user. */
export function useImportWizard(state) {
  const file = ref(null);
  const format = ref('csv');
  const sheet = ref('');
  const sheets = ref([]);
  const objectType = ref('');
  const idColumn = ref('');
  const mapping = ref({});
  const mode = ref('create');
  const inspection = ref(null);
  const preview = ref(null);
  const error = ref('');
  const success = ref('');
  const busy = ref('');
  const previewPage = ref(1);
  let request;
  let generation = 0;

  const types = computed(() => state.modelDetails?.objectTypes || []);
  const attributes = computed(() => {
    const definitions = new Map();
    const seen = new Set();
    function collect(name) {
      if (!name || seen.has(name)) return;
      seen.add(name);
      const type = types.value.find(item => item.name === name);
      collect(type?.parent);
      for (const attribute of type?.attributes || []) definitions.set(attribute.name, attribute);
    }
    collect(objectType.value);
    return [...definitions.values()];
  });
  const columns = computed(() => inspection.value?.columns || []);
  const canInspect = computed(() => Boolean(file.value && state.selectedModelKey && state.canReadCurrentModelData && state.canCreateCurrentModelData && !busy.value));
  const canPreview = computed(() => Boolean(canInspect.value && (format.value === 'xml' || (inspection.value && objectType.value && idColumn.value))));
  const canCommit = computed(() => Boolean(preview.value?.valid && preview.value?.previewHash && state.canCreateCurrentModelData && (!preview.value?.summary?.updates || state.canUpdateCurrentModelData) && !busy.value));
  const visibleRows = computed(() => (preview.value?.rows || []).slice((previewPage.value - 1) * 25, previewPage.value * 25));

  function invalidate(clearInspection = false) {
    generation++;
    request?.abort();
    request = null;
    busy.value = '';
    preview.value = null;
    previewPage.value = 1;
    error.value = '';
    success.value = '';
    if (clearInspection) inspection.value = null;
  }
  watch([file, format, sheet], () => invalidate(true), { flush: 'sync' });
  watch([objectType, idColumn, mapping, mode], () => invalidate(), { deep: true, flush: 'sync' });
  watch(() => [state.selectedModelKey, state.authMeta?.effectiveUsername, state.authMeta?.actorUsername, state.isAuthenticated], () => {
    invalidate(true);
    file.value = null;
    objectType.value = '';
    idColumn.value = '';
    mapping.value = {};
  }, { flush: 'sync' });
  watch(() => [state.canReadCurrentModelData, state.canCreateCurrentModelData, state.canUpdateCurrentModelData, state.modelDetails], () => invalidate(), { flush: 'sync' });
  watch(objectType, autoMap);
  onBeforeUnmount(() => { generation++; request?.abort(); });

  function selectFile(value) {
    file.value = Array.isArray(value) ? value[0] || null : value || null;
    const extension = file.value?.name.split('.').pop()?.toLowerCase();
    format.value = extension === 'xml' ? 'xml' : ['xlsx', 'xls'].includes(extension) ? 'xlsx' : 'csv';
    sheet.value = '';
    sheets.value = [];
    idColumn.value = '';
    mapping.value = {};
  }
  function autoMap() {
    const match = name => columns.value.find(column => column.toLowerCase() === name.toLowerCase()) || '';
    mapping.value = Object.fromEntries(attributes.value.map(attribute => [attribute.name, match(attribute.name)]));
    if (!idColumn.value) idColumn.value = match('id');
  }
  function formData(hash) {
    const body = new FormData();
    body.append('file', file.value);
    body.append('modelKey', state.selectedModelKey);
    body.append('format', format.value);
    body.append('mode', mode.value);
    if (sheet.value) body.append('sheet', sheet.value);
    if (format.value !== 'xml') {
      body.append('objectType', objectType.value);
      body.append('idColumn', idColumn.value);
      body.append('mapping', JSON.stringify(Object.fromEntries(Object.entries(mapping.value).filter(([, column]) => column))));
    }
    if (hash) body.append('previewHash', hash);
    return body;
  }
  async function send(action) {
    const hash = preview.value?.previewHash;
    invalidate(action === 'inspect');
    busy.value = action;
    const epoch = generation;
    const controller = new AbortController();
    request = controller;
    try {
      const response = await state.apiFetch(`/api/imports/${action}`, { method: 'POST', body: formData(hash), signal: controller.signal });
      const result = await response.json();
      if (epoch !== generation || controller.signal.aborted) return;
      if (!response.ok) {
        if (response.status === 409 && action === 'commit') throw new Error('Les données ont changé depuis la prévisualisation. Prévisualisez à nouveau avant de confirmer.');
        throw new Error(result.error || result.message || 'Impossible de traiter le fichier. Vérifiez son contenu puis réessayez.');
      }
      if (action === 'inspect') {
        inspection.value = result;
        sheets.value = result.sheets || [];
        autoMap();
      } else if (action === 'preview') {
        preview.value = result;
      } else {
        success.value = 'Import terminé. Les données ont été enregistrées.';
        await state.refreshData?.(state.selectedModelKey);
      }
    } catch (cause) {
      if (epoch === generation && !controller.signal.aborted) error.value = cause.message || 'Le service est inaccessible. Réessayez.';
    } finally {
      if (epoch === generation) { busy.value = ''; request = null; }
    }
  }
  function inspect() { if (canInspect.value) return send('inspect'); }
  function generatePreview() { if (canPreview.value) return send('preview'); }
  function commit() { if (canCommit.value) return send('commit'); }
  function cancel() { if (busy.value !== 'commit') invalidate(); }

  return { file, format, sheet, sheets, objectType, idColumn, mapping, mode, inspection, preview, error, success, busy, previewPage, types, attributes, columns, visibleRows, canInspect, canPreview, canCommit, selectFile, inspect, generatePreview, commit, cancel };
}
