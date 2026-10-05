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

import { expect, test, type APIRequestContext } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.goto("/");
});

/** The first embedding model the server serves, or undefined when it has none. */
async function embeddingModelId(request: APIRequestContext): Promise<string | undefined> {
  const listing = await request.get("/api/v1/model-bundles").then((reply) => reply.json());
  for (const bundle of Array.isArray(listing.bundles) ? listing.bundles : []) {
    for (const model of Array.isArray(bundle.models) ? bundle.models : []) {
      if (model.componentType === "COMPONENT_TYPE_EMBEDDER" && typeof model.name === "string") {
        return model.name;
      }
    }
  }
  return undefined;
}

test("scopes the hero to the Analyze tab", async ({ page }) => {
  await expect(page.locator("#playground-heading")).toBeVisible();
  await page.click('[data-workbench-tab="corpus-search"]');
  await expect(page.locator("#playground-heading")).toBeHidden();
  await expect(page.locator("#server-search")).toBeVisible();
});

test("bridges configured search to workflows and workspace search", async ({ page }) => {
  await page.click('[data-workbench-tab="corpus-search"]');
  await expect(page.locator("#server-search-index-help"))
    .toContainText("Pick a configured index or");
  await page.click('#server-search-index-help [data-workbench-jump="workflows"]');
  await expect(page.locator("#workflows-workbench")).toBeVisible();
  await page.click('[data-workbench-tab="session-search"]');
  await page.click('[data-workbench-jump="corpus-search"]');
  await expect(page.locator("#server-search")).toBeVisible();
});

test("offers automatic workflow defaults with optional resource choices", async ({ page }) => {
  // A build needs a teacher and a writable artifact root; a server without them shows
  // the browned-out state, which is a skip here, not a failure.
  const teachers = await page.request.get("/api/v1/teachers").then((reply) => reply.json());
  test.skip(!Array.isArray(teachers.teachers) || teachers.teachers.length === 0
    || teachers.writesEnabled === false,
    "The server has no teacher or no writable artifact root, so nothing can be built.");
  await page.click('[data-workbench-tab="workflows"]');
  await expect(page.locator("#workflow-status")).toContainText("Ready");
  await expect(page.locator("#workflow-dictionary-select option").first())
    .toHaveText("Corpus terms only (default)");
  await expect(page.locator("#workflow-teacher-select option")).not.toHaveCount(0);
  await expect(page.locator("#workflow-provider-select option")).not.toHaveCount(0);
  await expect(page.locator("#workflow-run-button")).toBeDisabled();
  await page.fill("#workflow-corpus", "One small document is enough to start.");
  await expect(page.locator("#workflow-run-button")).toBeEnabled();
});

test("builds and searches a live corpus workflow", async ({ page }, testInfo) => {
  test.setTimeout(1_200_000);
  test.skip(process.env.OPENNLP_E2E_WORKFLOW_WRITE !== "1",
    "Set OPENNLP_E2E_WORKFLOW_WRITE=1 to create persistent vocabulary and model artifacts.");
  const teachers = await page.request.get("/api/v1/teachers").then((reply) => reply.json());
  test.skip((!Array.isArray(teachers.teachers) || teachers.teachers.length === 0)
    && !(await embeddingModelId(page.request)),
    "The server has neither a teacher nor an embedding model, so no index can be built.");

  await page.click('[data-workbench-tab="workflows"]');
  await expect(page.locator("#workflow-status")).toContainText("Ready");
  await page.fill("#workflow-name", "Live workflow smoke");
  await page.fill("#workflow-corpus",
    "People seek liberty, justice, and equal rights in a democratic society.\n\n"
    + "Courts protect civil rights while elected governments write public policy.");
  await page.fill("#workflow-query", "liberty and rights");
  await page.click("#workflow-run-button");

  await expect(page.locator("#workflow-status"))
    .toContainText("is built and searchable", { timeout: 1_100_000 });
  // A full build completes all six stages; without a teacher the vocabulary and distillation
  // stages are marked skipped and the installed embedding model does the embedding.
  const hasTeacher = Array.isArray(teachers.teachers) && teachers.teachers.length > 0;
  await expect(page.locator('#workflow-stages [data-state="complete"]')).toHaveCount(hasTeacher ? 6 : 4);
  await expect(page.locator('#workflow-stages [data-state="skipped"]')).toHaveCount(hasTeacher ? 0 : 2);
  await expect(page.locator('#workflow-stages [data-state="error"]')).toHaveCount(0);
  await expect(page.locator(".workflow-analysis-card")).toHaveCount(2);
  await expect(page.locator("#workflow-search-heatmap .heat-document")).toHaveCount(2);
  await page.screenshot({ path: testInfo.outputPath("workflow-live.png"), fullPage: true });
});

test("holds inspector placeholders until a document is selected", async ({ page }) => {
  await page.click('[data-workbench-tab="corpus-search"]');
  for (const counter of await page.locator("#search-analytics dd").all()) {
    await expect(counter).toHaveText("…");
  }
});

test("disables the TSV export with a reason until a vocabulary exists", async ({ page }) => {
  const vocabularies = await page.request.get("/api/v1/vocabularies").then((reply) => reply.json());
  await page.click('[data-workbench-tab="trainer"]');
  await expect(page.locator("#trainer-status")).not.toHaveText(/Loading/);
  const button = page.locator("#trainer-download-tsv-button");
  if (Array.isArray(vocabularies.vocabularies) && vocabularies.vocabularies.length > 0) {
    await expect(button).toBeEnabled();
  } else {
    await expect(button).toBeDisabled();
    await expect(button).toHaveAttribute("title", /vocabulary/);
  }
});
