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

test("loads the bundled Alice sample from the gateway's static assets", async ({ page }) => {
  await page.goto("/");
  await page.click("#alice-sample-button");
  // The sample is fetched and decompressed from data/, so this also proves
  // the gateway serves its bundled static assets.
  await expect(page.locator("#analysis-text")).toHaveValue(/Alice/, { timeout: 30_000 });
});

test("loads the bundled Pride and Prejudice sample from the gateway's static assets", async ({ page }) => {
  await page.goto("/");
  await page.click("#pride-sample-button");
  await expect(page.locator("#analysis-text")).toHaveValue(/universally acknowledged/, { timeout: 30_000 });
});

test("analyzes text and opens on the calm Highlights overlay", async ({ page }) => {
  await page.goto("/");
  await page.fill("#analysis-text",
    "George Washington visited Paris in spring. The city welcomed him warmly.");
  await page.click("#analyze-button");
  await expect(page.locator(".layer-button").first()).toBeVisible({ timeout: 180_000 });

  // Entities and sentences render first; every token boxed is one click away.
  const highlights = page.locator('.layer-button[data-layer-kind="highlights"]');
  await expect(highlights).toHaveAttribute("aria-pressed", "true");
  const markers = page.locator("#annotated-text .annotation-marker");
  await expect(markers.first()).toBeVisible();
  const highlightCount = await markers.count();

  await page.click('.layer-button[data-layer-kind="all"]');
  await expect(page.locator('.layer-button[data-layer-kind="all"]'))
    .toHaveAttribute("aria-pressed", "true");
  await expect.poll(() => markers.count()).toBeGreaterThan(highlightCount);

  await markers.first().click();
  await expect(page.locator("#annotation-details")).toBeVisible();
  await expect(page.locator("#annotation-details .structured-value").first()).toBeVisible();
  await expect(page.locator("#annotation-details pre")).toHaveCount(0);
  await page.click("#annotation-details-close");

  // Document-scoped category layers collapse to one ranked chip each.
  await expect(page.locator(".document-annotation-chip").filter({ hasText: "top of" }).first())
    .toBeVisible();
});
