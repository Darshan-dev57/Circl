import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// In dev the API is proxied, so the browser sees one origin and the refresh cookie just works.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
