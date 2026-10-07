import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
export default defineConfig({
  base: "/admin/",
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      "/api": process.env.API_PROXY || "http://localhost:8080",
      "/uploads": process.env.API_PROXY || "http://localhost:8080",
      "/ws": {
        target: process.env.API_PROXY || "http://localhost:8080",
        ws: true,
      },
    },
  },

});
