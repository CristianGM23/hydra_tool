import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
    plugins: [react()],
    build: {
        manifest: true,
        chunkSizeWarningLimit: 1100,
        rollupOptions:{output:{manualChunks:(id:string)=>id.includes('node_modules/monaco-editor')?'monaco':undefined}}
    },
    server: {
        port: 5173,
        proxy: {
            '/api': 'http://localhost:8081'
        }
    }
});
