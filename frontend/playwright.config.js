import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: 'http://127.0.0.1:5173', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], ...(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}) } }],
  webServer: [
    { command: 'java -jar ../backend/target/flow-policy.jar --server.port=8081', url: 'http://127.0.0.1:8081/actuator/health', timeout: 90000, reuseExistingServer: false },
    { command: 'npm run dev -- --port 5173 --strictPort', url: 'http://127.0.0.1:5173', timeout: 60000, reuseExistingServer: false, env: { API_PROXY_TARGET: 'http://127.0.0.1:8081' } },
  ],
});
