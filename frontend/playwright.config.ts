/* Licensed under the Apache License, Version 2.0. See LICENSE. */
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  workers: 1,
  retries: 0,
  timeout: 60_000,
  reporter: 'list',
  use: {
    actionTimeout: 10_000,
    baseURL: process.env.SCM_BASE_URL ?? 'http://127.0.0.1:8585',
    viewport: { width: 1600, height: 1060 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    launchOptions: {
      ...(process.env.SCM_CHROMIUM_PATH
        ? { executablePath: process.env.SCM_CHROMIUM_PATH }
        : {}),
      args: ['--disable-dev-shm-usage'],
    },
  },
});
