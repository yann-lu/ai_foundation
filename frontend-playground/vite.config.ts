import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import tsconfigPaths from 'vite-tsconfig-paths'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [react(), tailwindcss(), tsconfigPaths()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
    dedupe: ['react', 'react-dom', '@assistant-ui/react'],
  },
  server: {
    port: 5174,
    proxy: {
      '/admin': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/chat': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/actuator': { target: 'http://127.0.0.1:8080', changeOrigin: true },
    },
  },
})
