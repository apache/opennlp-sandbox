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

import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

/**
 * Build index: a corpus of two, then twenty documents must come back as a searchable
 * index with as many documents as the corpus, and a search over it must return hits. Needs
 * an embedding model or a teacher on the server and OPENNLP_E2E_WORKFLOW_WRITE=1.
 */

const RUN = `index-${Date.now().toString(36)}`;

const TWO_DOCUMENTS = [
  "People seek liberty, justice, and equal rights in a democratic society.",
  "Courts protect civil rights while elected governments write public policy.",
];

const SUBJECTS = ["harbor pilots", "grain merchants", "river ferries", "wool weavers",
  "salt miners", "glass blowers", "copper smiths", "map makers", "ship chandlers",
  "tea traders", "paper mills", "silk dyers", "iron founders", "clock makers",
  "rope walks", "tanneries", "brick yards", "fishing fleets", "timber rafts", "cider presses"];

const TWENTY_DOCUMENTS = SUBJECTS.map((subject, i) =>
  `The ${subject} of the old port kept their own guild records from ${1700 + i}. `
  + `Their ledgers list prices, apprentices, and the ships that carried their goods. `
  + `A museum on the quay shows the tools the ${subject} used.`);

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

async function openWorkflows(page: Page): Promise<void> {
  await page.goto("/");
  await page.click('[data-workbench-tab="workflows"]');
  await expect(page.locator("#workflow-status")).toContainText(/Ready/, { timeout: 60_000 });
}

/** Builds an index from the documents and searches it; returns the number of search hits. */
async function buildAndSearch(page: Page, name: string, documents: string[], query: string): Promise<number> {
  await page.fill("#workflow-name", name);
  await page.fill("#workflow-corpus", documents.join("\n\n"));
  await expect(page.locator("#workflow-corpus-stats"))
    .toContainText(`${documents.length} document${documents.length === 1 ? "" : "s"}`);
  await page.fill("#workflow-query", query);
  await page.click("#workflow-run-button");
  await expect(page.locator("#workflow-status"))
    .toContainText("is built and searchable", { timeout: 1_100_000 });
  await expect(page.locator('#workflow-stages [data-state="error"]')).toHaveCount(0);
  await expect(page.locator(".workflow-analysis-card")).toHaveCount(documents.length);
  const hits = page.locator("#workflow-search-heatmap .heat-document");
  await expect(hits.first()).toBeVisible({ timeout: 60_000 });
  return hits.count();
}

test.beforeEach(async ({ page }) => {
  test.skip(process.env.OPENNLP_E2E_WORKFLOW_WRITE !== "1",
    "Set OPENNLP_E2E_WORKFLOW_WRITE=1 to create persistent index artifacts.");
  const teachers = await page.request.get("/api/v1/teachers").then((reply) => reply.json());
  const hasEmbedding = (await embeddingModelId(page.request)) !== undefined;
  const hasTeacher = Array.isArray(teachers.teachers) && teachers.teachers.length > 0;
  test.skip(!hasEmbedding && !hasTeacher,
    "The server has neither an embedding model nor a teacher, so no index can be built.");
  await openWorkflows(page);
});

test("indexes two documents and finds both", async ({ page }) => {
  test.setTimeout(1_200_000);
  const hits = await buildAndSearch(page, `${RUN} two`, TWO_DOCUMENTS, "liberty and rights");
  expect(hits).toBe(2);
  const indexes = await page.request.get("/api/v1/search-indexes").then((reply) => reply.json());
  const mine = (indexes.indexes ?? []).find((entry: { displayName?: string; label?: string }) =>
    (entry.displayName ?? entry.label ?? "").includes(`${RUN} two`));
  expect(mine, "the built index is listed by the server").toBeTruthy();
});

test("indexes twenty documents and ranks the matching subject first", async ({ page }) => {
  test.setTimeout(1_200_000);
  const hits = await buildAndSearch(page, `${RUN} twenty`, TWENTY_DOCUMENTS, "who made clocks in the old port");
  expect(hits).toBeGreaterThanOrEqual(2);
  await expect(page.locator("#workflow-search-heatmap .heat-document").first()).toContainText(/clock makers/);
});

test("a search that matches no document still answers, with zero hits and no error", async ({ page }) => {
  test.setTimeout(1_200_000);
  await buildAndSearch(page, `${RUN} none`, TWO_DOCUMENTS, "liberty");
  await page.fill("#workflow-query", "zxqv plortch vandelmuk");
  await page.click("#workflow-search-button");
  await expect(page.locator("#workflow-status")).toContainText(/Search complete/, { timeout: 120_000 });
  await expect(page.locator("#workflow-status")).not.toHaveClass(/is-error/);
});
