import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true
  },
  test: {
    environment: 'jsdom',
    globals: true
  }
});
