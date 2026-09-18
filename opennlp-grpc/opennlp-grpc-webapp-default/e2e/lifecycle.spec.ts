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

/**
 * Lifecycle: save a live index to disk, alias it, put it in a collection, and make it
 * read-only. The index is created through the gateway with the server's first embedding
 * model and removed again at the end, so the suite leaves the server as it found it.
 */

const RUN = `e2e-${Date.now().toString(36)}`;

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

test("shows where a first live index comes from when the server has none", async ({ page }) => {
  const listing = await page.request.get("/api/v1/search-indexes").then((reply) => reply.json());
  test.skip(Array.isArray(listing.indexes) && listing.indexes.length > 0,
    "The server already has indexes, so the empty state does not apply.");
  await page.goto("/");
  await page.click('[data-workbench-tab="lifecycle"]');
  const status = page.locator("#lifecycle-status");
  await expect(status).toContainText("No live indexes yet");
  await expect(status.locator('[data-workbench-jump="workflows"]')).toBeVisible();
  await expect(status.locator('[data-workbench-jump="session-search"]')).toBeVisible();
  await expect(page.locator("#lifecycle-index-select")).toBeDisabled();
  await expect(page.locator("#lifecycle-persist-button")).toBeDisabled();
});

test("saves, aliases, collects and makes a live index read-only", async ({ page }) => {
  test.setTimeout(300_000);
  const providers = await page.request.get("/api/v1/search-providers").then((reply) => reply.json());
  test.skip(providers.dynamicIndexingEnabled === false, "Live indexing is off on this server.");
  const modelId = await embeddingModelId(page.request);
  test.skip(!modelId, "The server serves no embedding model, so no live index can be built.");

  // A live index takes analyzed documents that already carry chunk embeddings, exactly as
  // the Analyze tab hands them over, so the document is analyzed first.
  const analyzed = await page.request.post("/api/v1/analyze", {
    data: {
      document: {
        docId: `${RUN}-1`,
        rawText: "Courts protect civil rights. Elected governments write public policy.",
      },
      options: { offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT", embeddingModelId: modelId },
      profile: {
        steps: ["PIPELINE_STEP_SENTENCE_DETECT", "PIPELINE_STEP_TOKENIZE", "PIPELINE_STEP_EMBED"],
      },
      chunkEmbedConfigs: [{
        configId: "sentence-chunks",
        resultSetName: "Sentence chunks",
        chunking: { strategy: { standard: "STANDARD_CHUNKING_STRATEGY_SENTENCE" }, cleanText: true },
        embeddingModelIds: [modelId],
      }],
    },
  });
  expect(analyzed.ok(), await analyzed.text()).toBeTruthy();
  const created = await page.request.post("/api/v1/index-documents", {
    data: {
      displayName: `Lifecycle ${RUN}`,
      provider: { standard: "STANDARD_SEARCH_PROVIDER_FLAT_FLOAT" },
      documents: [(await analyzed.json()).document],
      embedding: { modelId },
      chunkGroupIds: ["sentence-chunks"],
    },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  const indexId: string = (await created.json()).index.indexId;
  const alias = `${RUN}-current`;
  const collectionId = `${RUN}-collection`;

  try {
    await page.goto("/");
    await page.click('[data-workbench-tab="lifecycle"]');
    await expect(page.locator("#lifecycle-status")).toContainText("available");
    await page.selectOption("#lifecycle-index-select", indexId);
    await expect(page.locator("#lifecycle-index-select option:checked"))
      .toHaveText(`Lifecycle ${RUN} (${indexId}) · In memory`);
    const facts = page.locator("#lifecycle-index-facts");
    await expect(facts).toContainText("In memory");

    // Save to disk keeps the index writable; the state label follows.
    const persist = page.locator("#lifecycle-persist-button");
    if (providers.persistenceConfigured !== true) {
      await expect(persist).toBeDisabled();
    } else {
      await persist.click();
      await expect(page.locator("#lifecycle-workspace-status"))
        .toContainText(`Saved 'Lifecycle ${RUN}' to disk`, { timeout: 60_000 });
      await expect(facts).toContainText("Saved to disk");
    }

    // An alias is a stable name clients keep while the index behind it changes.
    await page.fill("#lifecycle-alias-input", alias);
    await page.click("#lifecycle-set-alias-button");
    await expect(page.locator("#lifecycle-alias-status"))
      .toContainText(`Alias '${alias}' now resolves to '${indexId}'`);
    await expect(page.locator("#lifecycle-alias-list")).toContainText(`${alias} → ${indexId}`);

    // A collection without a vocabulary reports its coverage as not measured, not as 0%.
    await page.fill("#collection-id", collectionId);
    await page.fill("#collection-name", `Collection ${RUN}`);
    await page.selectOption("#collection-members", [indexId]);
    await page.click("#collection-save-button");
    await expect(page.locator("#collection-status"))
      .toContainText(`Saved collection '${collectionId}'`, { timeout: 60_000 });
    await expect(page.locator("#collection-coverage-label")).toContainText("Not measured");

    // Read-only is one way; the index stays listed and searchable.
    if (providers.persistenceConfigured === true) {
      await page.selectOption("#lifecycle-index-select", indexId);
      await page.click("#lifecycle-seal-button");
      await expect(page.locator("#lifecycle-workspace-status"))
        .toContainText("read-only and saved it to disk", { timeout: 60_000 });
      await expect(page.locator("#lifecycle-index-select option:checked")).toContainText("Read-only");
      await expect(page.locator("#lifecycle-seal-button")).toBeDisabled();
      await expect(page.locator("#lifecycle-persist-button")).toBeDisabled();
    }
  } finally {
    await page.request.post("/api/v1/delete-collection", { data: { collectionId } });
    await page.request.post("/api/v1/delete-index-alias", { data: { alias } });
    await page.request.post("/api/v1/delete-search-index", { data: { indexId } });
  }
});
