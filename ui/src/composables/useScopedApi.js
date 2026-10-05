import { computed, onBeforeUnmount, watch } from 'vue';
import { createRequestCoordinator } from './http';

/** Feature requests are isolated by model, authenticated session and effective permissions. */
export function useScopedApi(state, reset) {
  const requests = createRequestCoordinator();
  const scope = computed(() => JSON.stringify([
    state.selectedModelKey, state.authEpoch, state.isAuthenticated,
    state.authMeta?.actorUsername, state.authMeta?.effectiveUsername, state.authMeta?.expiresAt,
    state.canReadCurrentModelData
  ]));
  const allowed = computed(() => Boolean(state.selectedModelKey && state.isAuthenticated && state.canReadCurrentModelData));
  watch(scope, () => { requests.cancelAll(); reset(); }, { flush: 'sync' });
  onBeforeUnmount(() => requests.cancelAll());
  async function request(key, path, options = {}) {
    if (!allowed.value) return null;
    const startScope = scope.value;
    const job = requests.start(key);
    try {
      const response = await state.apiFetch(path, { ...options, signal: job.signal });
      const payload = await state.readJson(response);
      if (!job.isCurrent() || scope.value !== startScope) return null;
      if (!response.ok) throw new Error(payload?.error || `La requête a échoué (${response.status}).`);
      return payload;
    } catch (error) {
      if (!job.isCurrent() || scope.value !== startScope || error.name === 'AbortError') return null;
      throw error;
    }
  }
  return { request, allowed, scope, cancel: requests.cancel };
}
