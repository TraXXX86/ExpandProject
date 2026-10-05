import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { createRequestCoordinator } from './http';

/** An object selection and its effective authorization form one request scope. */
export function useObjectWorkflow(state, object, onChanged = () => {}) {
  const details = ref(null), loading = ref(false), busy = ref(false), error = ref(''), notice = ref(''), selected = ref(null);
  const requests = createRequestCoordinator();
  const scope = computed(() => JSON.stringify([state.selectedModelKey, object()?.id, object()?.uuid, state.authEpoch, state.isAuthenticated, state.authMeta, state.canReadCurrentModelData, state.canTransitionCurrentModelData]));
  const allowed = computed(() => Boolean(state.selectedModelKey && object()?.id != null && state.isAuthenticated && state.canReadCurrentModelData));
  const transitions = computed(() => state.canTransitionCurrentModelData && details.value?.canTransition ? details.value.transitions || [] : []);
  function reset() { requests.cancelAll(); details.value = null; loading.value = false; busy.value = false; error.value = ''; notice.value = ''; selected.value = null; }
  async function load() {
    if (!allowed.value) return;
    const captured = scope.value, job = requests.start('load'); loading.value = true; error.value = ''; details.value = null; selected.value = null;
    try {
      const response = await state.apiFetch(`/api/objects/${encodeURIComponent(object().id)}/workflow?modelKey=${encodeURIComponent(state.selectedModelKey)}`, { signal: job.signal });
      const payload = await state.readJson(response);
      if (!job.isCurrent() || scope.value !== captured) return;
      if (!response.ok) throw new Error(payload?.error || 'Chargement du workflow impossible.');
      if (object()?.uuid && payload.objectUuid !== object().uuid) throw new Error('Cet objet a été remplacé. Actualisez la liste avant de continuer.');
      details.value = payload;
    } catch (failure) { if (job.isCurrent() && scope.value === captured && failure.name !== 'AbortError') error.value = failure.message; }
    finally { if (job.isCurrent() && scope.value === captured) loading.value = false; }
  }
  function choose(transition) { if (!loading.value && !busy.value && transitions.value.some(item => item.id === transition.id)) selected.value = transition; }
  async function confirm() {
    const transition = selected.value, workflow = details.value?.workflow;
    if (busy.value || !allowed.value || !workflow || !transitions.value.some(item => item.id === transition?.id)) return;
    const captured = scope.value, job = requests.start('transition'); busy.value = true; error.value = ''; notice.value = '';
    try {
      const response = await state.apiFetch(`/api/objects/${encodeURIComponent(object().id)}/workflow/transitions`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, signal: job.signal,
        body: JSON.stringify({ modelKey: state.selectedModelKey, transitionId: transition.id, expectedRevision: workflow.revision, objectUuid: details.value.objectUuid })
      });
      const payload = await state.readJson(response);
      if (!job.isCurrent() || scope.value !== captured) return;
      selected.value = null;
      if (!response.ok) {
        details.value = null;
        if (response.status === 409) { notice.value = 'Cet objet a changé. Son statut a été actualisé ; choisissez à nouveau une action.'; await load(); return; }
        if (response.status === 403) { await load(); throw new Error('Vous ne disposez plus du droit de faire évoluer cet objet.'); }
        throw new Error(payload?.error || 'Changement de statut impossible.');
      }
      await load();
      if (scope.value === captured && details.value) { notice.value = 'Statut mis à jour.'; onChanged(details.value); }
    } catch (failure) { if (job.isCurrent() && scope.value === captured && failure.name !== 'AbortError') error.value = failure.message; }
    finally { if (job.isCurrent() && scope.value === captured) busy.value = false; }
  }
  watch(scope, () => { reset(); load(); }, { immediate: true, flush: 'sync' });
  onBeforeUnmount(() => requests.cancelAll());
  return { details, loading, busy, error, notice, selected, transitions, load, choose, confirm };
}
