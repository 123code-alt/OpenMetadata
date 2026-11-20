/* Licensed under the Apache License, Version 2.0. See LICENSE. */
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import dts from 'vite-plugin-dts';
import packageJson from './package.json';

export default defineConfig({
  plugins: [react(), dts({ include: ['src'], outDir: 'dist/types' })],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  build: {
    lib: {
      entry: fileURLToPath(new URL('./src/index.ts', import.meta.url)),
      name: 'OpenMetadataUICore',
      formats: ['es', 'cjs'],
      fileName: format => `index.${format}.js`,
    },
    rollupOptions: {
      external: id => Object.keys(packageJson.dependencies).some(name => id === name || id.startsWith(`${name}/`)),
    },
    target: 'es2020',
  },
});
