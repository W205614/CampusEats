import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  timeout: 60000,
  workers: 1,
  fullyParallel: false,
  reporter: [
    ["list"],
    ["html", { outputFolder: "../../.local/playwright-report", open: "never" }],
  ],
  outputDir: "../../.local/playwright-results",
  use: {
    baseURL: process.env.BASE_URL || "http://localhost:18083",
    headless: true,
    actionTimeout: 10000,
    timezoneId: "Asia/Shanghai",
    viewport: { width: 1440, height: 1000 },
    trace: "off",
    screenshot: "only-on-failure",
  },
});
