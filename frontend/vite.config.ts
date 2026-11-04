/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const backend = 'http://localhost:8000'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    strictPort: true,
    // Same origin as the API (ADR 0001): the browser never makes a cross-origin call.
    // /oauth2/callback is a frontend route, so only the authorization start is proxied.
    proxy: { '/api': backend, '/oauth2/authorization': backend, '/login/oauth2': backend },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
