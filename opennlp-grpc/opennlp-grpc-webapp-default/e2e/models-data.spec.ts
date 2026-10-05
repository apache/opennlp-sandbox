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

/**
 * Models & data: the readiness grid, the catalog cards, and the install controls. The spec
 * never installs anything; it checks that every card is in exactly one of its three states
 * and that the consent checkbox is what arms an install button.
 */

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await page.click('[data-workbench-tab="models"]');
  await expect(page.locator("#model-data-workbench")).toBeVisible();
});

test("grades every pipeline step and counts the ready ones", async ({ page }) => {
  const summary = page.locator("#resource-summary");
  await expect(summary).toHaveText(/^\d+ of \d+ features ready$/);
  const counts = (await summary.textContent())!.match(/\d+/g)!.map(Number);
  const ready = counts[0] ?? 0;
  const total = counts[1] ?? 0;
  expect(total).toBeGreaterThan(0);
  const features = page.locator("#resource-feature-list .resource-feature");
  await expect(features).toHaveCount(total);
  await expect(page.locator("#resource-feature-list .resource-feature.is-ready")).toHaveCount(ready);
  for (const feature of await features.all()) {
    await expect(feature.locator("span")).toHaveText(/^(Ready|Needs model or data|Not in this build)$/);
  }
  // The classic English pipeline ships in every build, so a server that answers at all has
  // sentence detection ready.
  await expect(page.locator('[data-feature-step="PIPELINE_STEP_SENTENCE_DETECT"]'))
    .toHaveClass(/is-ready/);
});

test("lists the catalog with each card in one install state", async ({ page }) => {
  const catalog = await page.request.get("/api/v1/model-catalog").then((reply) => reply.json());
  const models = Array.isArray(catalog.models) ? catalog.models : [];
  test.skip(models.length === 0, "This build publishes no standard model catalog.");

  // Per-language model packs fold their members into one card, so there are fewer cards
  // than catalog entries, never more; every single-model card names a catalog id.
  const cards = page.locator("#resource-model-catalog .catalog-model-card");
  await expect(cards.first()).toBeVisible();
  const singles = page.locator("#resource-model-catalog .catalog-model-card[data-catalog-id]");
  const packs = page.locator("#resource-model-catalog .catalog-pack-card");
  expect(await singles.count() + await packs.count()).toBe(await cards.count());
  expect(await cards.count()).toBeLessThanOrEqual(models.length);
  const catalogIds = new Set(models.map((model: { catalogId: string }) => model.catalogId));
  for (const id of await singles.evaluateAll((elements) =>
    elements.map((element) => (element as HTMLElement).dataset.catalogId ?? ""))) {
    expect(catalogIds.has(id), `card for unknown catalog id ${id}`).toBe(true);
  }
  for (const card of await cards.all()) {
    const states = await Promise.all([
      card.locator(".catalog-installed-state").count(),
      card.locator(".catalog-installs-off").count(),
      card.locator("[data-catalog-install], [data-pack-install]").count(),
    ]);
    expect(states.filter((count) => count > 0)).toHaveLength(1);
  }
  // Single-model cards carry the unlock and format tags; pack cards list their members instead.
  for (const card of await singles.all()) {
    await expect(card.locator(".catalog-tags")).toBeVisible();
  }
});

test("arms an install button only after the license consent is ticked", async ({ page }) => {
  const catalog = await page.request.get("/api/v1/model-catalog").then((reply) => reply.json());
  test.skip(catalog.installsEnabled !== true,
    "Installs are off on this node (no model.catalog_root), so there is no install control to arm.");
  const card = page.locator("#resource-model-catalog .catalog-model-card[data-catalog-id]")
    .filter({ has: page.locator("[data-catalog-install]") }).first();
  test.skip(await card.count() === 0, "Every catalog model is already installed on this node.");

  const button = card.locator("[data-catalog-install]");
  const consent = card.locator("[data-catalog-consent]");
  await expect(button).toBeDisabled();
  await consent.check();
  await expect(button).toBeEnabled();
  await consent.uncheck();
  await expect(button).toBeDisabled();
});

test("says on every card that installs are off when the node has no catalog root", async ({ page }) => {
  const catalog = await page.request.get("/api/v1/model-catalog").then((reply) => reply.json());
  test.skip(catalog.installsEnabled === true, "Installs are on for this node.");
  const notices = page.locator("#resource-model-catalog .catalog-installs-off");
  await expect(notices.first()).toContainText("model.catalog_root");
  await expect(page.locator("#resource-model-catalog [data-catalog-install]")).toHaveCount(0);
});
