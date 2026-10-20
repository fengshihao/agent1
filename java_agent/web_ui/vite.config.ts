import { defineConfig } from "vite";

export default defineConfig({
  base: "./",
  build: {
    outDir: "../cli/src/main/resources/web-ui",
    emptyOutDir: true,
    assetsDir: "assets",
  },
  server: {
    proxy: {
      "/api": "http://127.0.0.1:17831",
    },
  },
});
