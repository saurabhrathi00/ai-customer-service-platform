import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // Dev-only convenience. Each Spring service serves under its own
      // context path; we route /api/<svc>/... → http://localhost:<port>/<svc>/...
      '/ws/demo':       { target: 'ws://localhost:8086', changeOrigin: true, ws: true, rewrite: (p) => p.replace(/^\/ws\/demo/, '/call-orchestration-service/ws/demo') },
      '/api/auth':      { target: 'http://localhost:8081', changeOrigin: true, rewrite: (p) => p.replace(/^\/api\/auth/, '/auth-service') },
      '/api/business':  { target: 'http://localhost:8082', changeOrigin: true, rewrite: (p) => p.replace(/^\/api\/business/, '/user-business-service') },
      '/api/knowledge': { target: 'http://localhost:8083', changeOrigin: true, rewrite: (p) => p.replace(/^\/api\/knowledge/, '/knowledge-service') },
      '/api/calls':     { target: 'http://localhost:8086', changeOrigin: true, rewrite: (p) => p.replace(/^\/api\/calls/, '/call-orchestration-service') },
      '/api/summary':   { target: 'http://localhost:8089', changeOrigin: true, rewrite: (p) => p.replace(/^\/api\/summary/, '/conversation-summary-service') },
      '/api/notify':        { target: 'http://localhost:8090', changeOrigin: true, rewrite: (p) => p.replace(/^\/api\/notify/, '/notification-service') },
      '/api/subscription': { target: 'http://localhost:8091', changeOrigin: true, rewrite: (p) => p.replace(/^\/api\/subscription/, '/subscription-service') },
    },
  },
});
