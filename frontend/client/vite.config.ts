import { defineConfig } from 'vite';
import uni from '@dcloudio/vite-plugin-uni';
import { demoImage } from '../../scripts/vite-demo-image.mts';
export default defineConfig({
  plugins: [uni(), demoImage()],
  build: { license: { fileName: 'THIRD_PARTY_LICENSES.md' } },
  server: {
    port: 5174,
    proxy: {
      '/api': process.env.API_PROXY || 'http://localhost:8080',
      '/uploads': process.env.API_PROXY || 'http://localhost:8080',
    },
  },
});
