import { defineConfig } from 'vite';
import uni from '@dcloudio/vite-plugin-uni';
export default defineConfig({
  plugins: [uni()],
  build: { license: { fileName: 'THIRD_PARTY_LICENSES.md' } },
  server: {
    port: 5174,
    proxy: {
      '/api': process.env.API_PROXY || 'http://localhost:8080',
      '/uploads': process.env.API_PROXY || 'http://localhost:8080',
    },
  },
});
