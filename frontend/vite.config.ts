/// <reference types="vitest/config" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import tsconfigPaths from 'vite-tsconfig-paths';
import { tanstackRouter } from '@tanstack/router-plugin/vite';

const proxyTarget = process.env['VITE_API_PROXY_TARGET'] || 'http://localhost:8080';

// Dev-only proxy so the Vite dev server can talk to the Spring Boot API
// without CORS setup. Point VITE_API_PROXY_TARGET at wherever the backend
// runs locally (default assumes the usual Spring Boot 8080).
export default defineConfig({
  plugins: [
    tanstackRouter({ target: 'react', autoCodeSplitting: true }),
    react(),
    tailwindcss(),
    tsconfigPaths()
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: proxyTarget,
        changeOrigin: true,
        // Without this, a backend that is not running produces a plain-text
        // `500 Internal Server Error` of the proxy's own making. The browser
        // cannot tell that apart from a real server fault, so every form in
        // the app reported "Internal Server Error" while Spring Boot was
        // simply not listening. Answer with the status that actually
        // describes it, and with the JSON envelope the client expects.
        configure(proxy) {
          proxy.on('error', (err: NodeJS.ErrnoException, _req, res) => {
            if (!('writeHead' in res) || res.headersSent) return;
            res.writeHead(503, { 'Content-Type': 'application/json' });
            res.end(
              JSON.stringify({
                status: 'error',
                code: 'BACKEND_UNREACHABLE',
                message: `Cannot reach the backend at ${proxyTarget} (${err.code || err.message}). Start the Spring Boot app and try again.`
              })
            );
          });
        }
      }
    }
  },
  build: {
    // Output straight into the Spring Boot static resources folder so the
    // backend serves the built SPA same-origin in production (see the
    // frontend-maven-plugin binding in pom.xml and SpaFallbackController).
    outDir: '../src/main/resources/static',
    emptyOutDir: true
  },
  test: {
    environment: 'happy-dom',
    globals: true
  }
});
