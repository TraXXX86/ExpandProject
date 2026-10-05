/** Cancels superseded requests and guards against responses from clients ignoring abort. */
export function createRequestCoordinator() {
  const active = new Map();
  function cancel(key) { active.get(key)?.abort(); active.delete(key); }
  function start(key) {
    cancel(key);
    const controller = new AbortController();
    active.set(key, controller);
    return { signal: controller.signal, isCurrent: () => active.get(key) === controller && !controller.signal.aborted };
  }
  return {
    start, cancel,
    cancelPrefix(prefix) { [...active.keys()].filter(key => key.startsWith(prefix)).forEach(cancel); },
    cancelAll() { [...active.keys()].forEach(cancel); }
  };
}

/** Cookies are HttpOnly. Never persist or attach an API token in the browser. */
export function createApiClient({ apiBase = '', onUnauthorized = () => {} } = {}) {
  let sessionEpoch = 0;
  const client = async (path, options = {}) => {
    const epoch = sessionEpoch;
    const headers = new Headers(options.headers || {});
    if (!['GET', 'HEAD', 'OPTIONS'].includes((options.method || 'GET').toUpperCase())) {
      headers.set('X-Requested-With', 'ExpandProject');
    }
    const response = await fetch(`${apiBase}${path}`, { ...options, credentials: 'include', headers });
    if (response.status === 401 && epoch === sessionEpoch && !options.signal?.aborted && path !== '/api/auth/login') onUnauthorized();
    return response;
  };
  client.invalidate = () => { sessionEpoch++; };
  return client;
}
