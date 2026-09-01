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

import { expect, test } from "@playwright/test";

test("searches a corpus index and inspects the top hit", async ({ page }) => {
  await page.goto("/");
  await page.click('[data-workbench-tab="corpus-search"]');
  const indexSelect = page.locator("#server-search-index");
  // Index discovery is asynchronous; an empty catalog is a skip, not a failure.
  await expect(indexSelect).toBeEnabled({ timeout: 30_000 }).catch(() => undefined);
  const hasIndex = await indexSelect.evaluate((select) =>
    !(select as HTMLSelectElement).disabled
    && (select as HTMLSelectElement).options[0]?.value !== undefined
    && (select as HTMLSelectElement).options[0]?.value !== "");
  test.skip(!hasIndex, "The server reports no configured search index.");

  await page.fill("#server-search-query", "the");
  await page.click("#server-search-button");
  const status = page.locator("#chunk-comparison-status");
  await expect(status).not.toHaveText("No chunk comparison yet.", { timeout: 60_000 });

  // The single-panel collapse and the note must agree with each other.
  const single = await page.locator(".chunk-comparison")
    .evaluate((panel) => panel.classList.contains("is-single"));
  if (single) {
    await expect(page.locator("#search-original-panel")).toBeHidden();
    await expect(status).toContainText("shown once");
  } else {
    await expect(page.locator("#search-original-panel")).toBeVisible();
    await expect(status).toContainText("differs");
  }

  // Lazy analysis resolves every counter to a number or an explicit n/a.
  for (const counter of await page.locator("#search-analytics dd").all()) {
    await expect(counter).not.toHaveText("…", { timeout: 180_000 });
  }
});
