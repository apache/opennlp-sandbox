/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests against a running gRPC server and gateway; they are not
 * part of the Maven build. Point OPENNLP_E2E_BASE_URL at the gateway, for
 * example http://127.0.0.1:7072 for the Docker demonstration image, then run
 * `npx playwright install chromium` once and `npm run e2e`.
 */
const baseURL = process.env.OPENNLP_E2E_BASE_URL;
if (!baseURL) {
  throw new Error("Set OPENNLP_E2E_BASE_URL to the gateway address, for example http://127.0.0.1:7072.");
}

export default defineConfig({
  testDir: "./e2e",
  // The suite drives one shared server, so tests must not interleave writes.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 240_000,
  expect: { timeout: 15_000 },
  reporter: [["list"]],
  use: {
    baseURL,
    trace: "retain-on-failure",
    ...devices["Desktop Chrome"],
  },
});
