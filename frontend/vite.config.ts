import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// The dev server proxies /api to Spring Boot, so the browser sees one origin and no CORS setup is needed.
// The proxy also adds the dashboard's Basic credentials server-side; they never reach browser code.
// In production the dashboard is served by Spring Boot itself and the browser prompts for them.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const apiTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080';
  const username = env.DASHBOARD_USERNAME || 'admin';
  const password = env.DASHBOARD_PASSWORD || 'local-dev-password';
  const apiProxy = {
    '/api': {
      target: apiTarget,
      changeOrigin: true,
      headers: { Authorization: `Basic ${Buffer.from(`${username}:${password}`).toString('base64')}` },
    },
  };

  return {
    plugins: [react(), tailwindcss()],
    server: { port: 5173, proxy: apiProxy },
    preview: { proxy: apiProxy },
  };
});
