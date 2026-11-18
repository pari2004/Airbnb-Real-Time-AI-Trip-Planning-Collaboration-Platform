import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server on 5173; API/WS base URLs come from env (see .env.example).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true,
  },
});
