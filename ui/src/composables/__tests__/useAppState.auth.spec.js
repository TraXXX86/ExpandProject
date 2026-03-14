import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises } from '@vue/test-utils';
import { mountUseAppState, jsonResponse } from './testUtils';

describe('useAppState authentication', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    window.localStorage.clear();
  });

  it('warns when credentials are missing', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const state = mountUseAppState();
    state.loginUsername.value = '';
    state.loginPassword.value = '';

    const authenticated = await state.login();

    expect(authenticated).toBe(false);
    expect(fetchMock).not.toHaveBeenCalled();
    expect(state.authStatus.value).toEqual({
      type: 'warning',
      message: 'Renseignez votre login et mot de passe.'
    });
  });

  it('logs in, persists the token, and loads initial platform data', async () => {
    const loginPayload = {
      token: 'token-123',
      auth: {
        actorUsername: 'admin',
        actorDisplayName: 'Administrateur',
        effectiveUsername: 'admin',
        effectiveDisplayName: 'Administrateur',
        impersonating: false,
        actorPlatformAdmin: true,
        expiresAt: 123456
      },
      user: {
        username: 'admin',
        displayName: 'Administrateur',
        portalUser: true,
        portalModelAdmin: true,
        platformAdmin: true
      },
      permissions: []
    };

    const fetchMock = vi.fn((url) => {
      const value = String(url);

      if (value === 'http://localhost:8080/api/auth/login') {
        return Promise.resolve(jsonResponse(loginPayload));
      }
      if (value === 'http://localhost:8080/api/access/users') {
        return Promise.resolve(jsonResponse([]));
      }
      if (value === 'http://localhost:8080/api/health?deep=true') {
        return Promise.resolve(jsonResponse({ api: 'ok', neo4j: 'ok', neo4jError: '' }));
      }
      if (value === 'http://localhost:8080/api/models') {
        return Promise.resolve(jsonResponse([
          { key: 'social', name: 'Social Network', version: '1.0' }
        ]));
      }
      if (value === 'http://localhost:8080/api/models/social') {
        return Promise.resolve(jsonResponse({
          objectTypes: [],
          linkTypes: [],
          languages: [],
          defaultLanguage: '',
          userPortalLabels: {}
        }));
      }
      if (value === 'http://localhost:8080/api/data?modelKey=social') {
        return Promise.resolve(jsonResponse({
          objects: [],
          links: [],
          objectCount: 0,
          linkCount: 0
        }));
      }

      throw new Error(`Unexpected fetch call: ${value}`);
    });

    vi.stubGlobal('fetch', fetchMock);

    const state = mountUseAppState();
    state.loginUsername.value = 'admin';
    state.loginPassword.value = 'admin';

    const authenticated = await state.login();
    await flushPromises();
    await flushPromises();

    expect(authenticated).toBe(true);
    expect(state.isAuthenticated.value).toBe(true);
    expect(state.authToken.value).toBe('token-123');
    expect(window.localStorage.getItem('expand.authToken')).toBe('token-123');
    expect(state.selectedModelKey.value).toBe('social');
    expect(state.healthStatus.value.neo4j).toBe('ok');
    expect(state.authStatus.value).toEqual({
      type: 'success',
      message: 'Connexion réussie.'
    });

    const usersCall = fetchMock.mock.calls.find(([url]) =>
      String(url) === 'http://localhost:8080/api/access/users'
    );
    expect(usersCall).toBeTruthy();
    expect(usersCall[1].headers.get('Authorization')).toBe('Bearer token-123');
  });
});
