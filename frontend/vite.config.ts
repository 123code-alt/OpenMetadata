/* Licensed under the Apache License, Version 2.0. See LICENSE. */
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';
import svgr from 'vite-plugin-svgr';

export default defineConfig({
  base: '/',
  plugins: [
    react(),
    tailwindcss(),
    svgr({
      include: '**/*.svg',
      svgrOptions: { exportType: 'named', namedExport: 'ReactComponent' },
    }),
  ],
  resolve: {
    dedupe: [
      'react',
      'react-dom',
      'react-aria',
      'react-aria-components',
      'react-stately',
      '@untitledui/icons',
      'react-hook-form',
      'tailwind-merge',
      'input-otp',
      '@react-aria/utils',
      '@react-stately/utils',
      '@react-types/shared',
      '@internationalized/date',
    ],
  },
  server: {
    proxy: { '/api': process.env.SCM_DEV_API_URL ?? 'http://127.0.0.1:8585' },
  },
  build: { chunkSizeWarningLimit: 1200 },
});
