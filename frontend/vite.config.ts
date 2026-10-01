import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// The dev server proxies /api to Spring Boot, so the browser sees one origin and no CORS setup is needed.
// In production the dashboard is served by Spring Boot itself from the same origin.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const apiTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080';
  const apiProxy = {
    '/api': { target: apiTarget, changeOrigin: true },
  };

  return {
    plugins: [react(), tailwindcss()],
    server: { port: 5173, proxy: apiProxy },
    preview: { proxy: apiProxy },
  };
});
