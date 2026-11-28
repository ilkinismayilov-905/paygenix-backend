import { defineConfig, devices } from '@playwright/test'
import { env } from './support/env'

// End-to-end tests against a running stack (ADR 0001): the real API, Postgres, Redis and an SMTP catcher.
// Nothing is mocked; each test creates its own users, so tests can run in parallel and runs can repeat.
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  // one bcrypt-heavy API on a small machine: a few workers are enough
  workers: process.env.CI ? 2 : 3,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: env.baseURL,
    locale: 'en-US',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
