import { afterEach, describe, expect, it, vi } from 'vitest';
import { createApiClient, createRequestCoordinator } from './http';

afterEach(() => vi.unstubAllGlobals());
describe('request coordination', () => {
  it('cancels the previous request even when its response ignores abort', () => {
    const requests = createRequestCoordinator();
    const first = requests.start('data');
    const second = requests.start('data');
    expect(first.signal.aborted).toBe(true);
    expect(first.isCurrent()).toBe(false);
    expect(second.isCurrent()).toBe(true);
    requests.cancelAll();
    expect(second.isCurrent()).toBe(false);
  });
  it('invalidates neighbor caches without cancelling unrelated work', () => {
    const requests = createRequestCoordinator();
    const neighbor = requests.start('neighbors:12');
    const model = requests.start('model');
    requests.cancelPrefix('neighbors:');
    expect(neighbor.isCurrent()).toBe(false);
    expect(model.isCurrent()).toBe(true);
  });
});
describe('cookie API client', () => {
  it('includes cookies and a CSRF header on mutations without exposing a bearer token', async () => {
    const fetch = vi.fn().mockResolvedValue({ status: 200 });
    vi.stubGlobal('fetch', fetch);
    await createApiClient()('/api/objects', { method: 'POST' });
    const [, options] = fetch.mock.calls[0];
    expect(options.credentials).toBe('include');
    expect(options.headers.get('X-Requested-With')).toBe('ExpandProject');
    expect(options.headers.has('Authorization')).toBe(false);
  });
  it('expires auth for a rejected session but ignores an aborted stale rejection', async () => {
    const expired = vi.fn();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 401 }));
    const api = createApiClient({ onUnauthorized: expired });
    await api('/api/auth/me');
    const controller = new AbortController();
    controller.abort();
    await api('/api/data', { signal: controller.signal });
    await api('/api/auth/login', { method: 'POST' });
    expect(expired).toHaveBeenCalledTimes(1);
  });
  it('ignores a stale unauthorized response after the active session changes', async () => {
    let resolve;
    vi.stubGlobal('fetch', vi.fn(() => new Promise(done => { resolve = done; })));
    const expired = vi.fn();
    const api = createApiClient({ onUnauthorized: expired });
    const oldSession = api('/api/access/me');
    api.invalidate();
    resolve({ status: 401 });
    await oldSession;
    expect(expired).not.toHaveBeenCalled();
  });
});
