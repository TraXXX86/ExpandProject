import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  use: { actionTimeout: 10_000, baseURL: process.env.E2E_UI_URL || 'http://127.0.0.1:15173', headless: true, launchOptions: { ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}), args: ['--no-sandbox'] }, trace: 'retain-on-failure' },
  webServer: process.env.E2E_UI_URL ? undefined : {
    command: 'npm run dev -- --host 127.0.0.1 --port 15173 --strictPort',
    url: 'http://127.0.0.1:15173',
    reuseExistingServer: !process.env.CI,
    env: { API_PROXY_TARGET: process.env.E2E_API_URL || 'http://127.0.0.1:18080', VITE_API_BASE: '' }
  }
});
