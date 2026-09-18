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

import { expect, test, type Page } from "@playwright/test";

/**
 * Analysis must produce content at every text size. An analysis that completes with
 * empty layers, empty highlights, or blank projections is the regression these tests
 * exist for, so every assertion here is about counts, not about the page being visible.
 */

const SENTENCE = "George Washington visited Paris in spring and met Benjamin Franklin.";

const PARAGRAPH = [
  "George Washington visited Paris in spring and met Benjamin Franklin.",
  "The two men discussed the war, the treasury, and the new government in Philadelphia.",
  "Franklin introduced him to French officers who had served under Lafayette.",
  "They dined near the Seine and walked through the Tuileries after dark.",
  "Washington returned to Mount Vernon in June with letters for Congress.",
].join(" ");

/** About a page: the paragraph repeated with a different city and year each time. */
const PAGE = Array.from({ length: 12 }, (_, i) =>
  PARAGRAPH.replace("Paris", ["Paris", "London", "Boston", "Madrid"][i % 4] ?? "Paris")
    .replace("spring", `the spring of ${1780 + i}`)).join("\n\n");

/** Layers with a count that is expected to grow with the text; the others may stay flat. */
const SCALING_LAYERS = ["opennlp:sentences", "opennlp:tokens", "opennlp:pos", "opennlp:lemmas"];

interface LayerCount {
  id: string;
  count: number;
}

/** Runs Analyze on the text and returns the per-layer counts the layer list shows. */
async function analyze(page: Page, text: string, timeout = 240_000): Promise<LayerCount[]> {
  await page.fill("#analysis-text", text);
  await page.click("#analyze-button");
  // The click switches the status to "Starting progressive analysis"; completion rewrites it.
  await expect(page.locator("#form-status")).toContainText(/Analysis complete/, { timeout });
  const buttons = page.locator('.layer-button[data-layer-kind="layer"]');
  const counts: LayerCount[] = [];
  for (const button of await buttons.all()) {
    const id = await button.getAttribute("data-layer-id");
    const count = Number(await button.locator("small").textContent());
    counts.push({ id: id ?? "", count });
  }
  return counts;
}

function countOf(counts: LayerCount[], id: string): number {
  return counts.find((entry) => entry.id === id)?.count ?? 0;
}

/**
 * The chunk embedding groups the last analysis returned, read from the JSON projection; a
 * response too large for the JSON view is counted through the Chunks tab instead.
 */
async function chunkGroupCount(page: Page): Promise<number> {
  await page.click("#json-tab");
  const json = (await page.locator("#json-view").textContent()) ?? "";
  if (json.trimStart().startsWith("{")) {
    const body = JSON.parse(json);
    return Array.isArray(body?.document?.chunkEmbeddingGroups) ? body.document.chunkEmbeddingGroups.length : 0;
  }
  await page.click("#chunks-tab");
  return page.locator("#chunks-view .chunk-group-column").count();
}

async function expectContentInEveryProjection(page: Page): Promise<void> {
  // Highlights: something is boxed in the text.
  await expect(page.locator("#annotated-text .annotation-marker").first()).toBeVisible();
  expect(Number(await page.locator("#result-annotation-count").textContent())).toBeGreaterThan(0);
  expect(Number(await page.locator("#result-layer-count").textContent())).toBeGreaterThan(0);

  // The JSON view shows the response, or the download notice past the browser's limit.
  await page.click("#json-tab");
  await expect(page.locator("#json-view")).toBeVisible();
  await expect(page.locator("#json-view")).toContainText(/opennlp:tokens|download|too large/i);

  // Chunk cards exist exactly when the response includes chunk embedding groups.
  const groups = await chunkGroupCount(page);
  await page.click("#chunks-tab");
  await expect(page.locator("#chunks-view")).toBeVisible();
  if (groups > 0) {
    await expect(page.locator("#chunks-view .chunk-card").first()).toBeVisible();
  } else {
    await expect(page.locator("#chunks-view")).toContainText(/No chunk groups/);
  }

  await page.click("#graph-tab");
  await expect(page.locator("#graph-view")).toBeVisible();
  await expect(page.locator("#graph-view canvas, #graph-view svg").first()).toBeVisible({ timeout: 30_000 });

  // The heatmap scores chunks against a query; without an embedding model it reports that.
  await page.click("#heatmap-tab");
  await expect(page.locator("#heatmap-view")).toBeVisible();
  const embedding = await embeddingModelId(page);
  if (embedding && groups > 0) {
    await page.fill("#heatmap-query", "the city and its visitors");
    await page.click("#heatmap-query-button");
    await expect(page.locator("#heatmap-view .heat-chunk-card").first()).toBeVisible({ timeout: 60_000 });
  } else {
    await expect(page.locator("#heatmap-view")).toContainText(/query|embedding|sentiment/i);
  }
}

/** The first embedding model the server serves, or undefined when it has none. */
async function embeddingModelId(page: Page): Promise<string | undefined> {
  const listing = await page.request.get("/api/v1/model-bundles").then((reply) => reply.json());
  for (const bundle of Array.isArray(listing.bundles) ? listing.bundles : []) {
    for (const model of Array.isArray(bundle.models) ? bundle.models : []) {
      if (model.componentType === "COMPONENT_TYPE_EMBEDDER" && typeof model.name === "string") {
        return model.name;
      }
    }
  }
  return undefined;
}

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  // The button is enabled once the service is discovered and text is present.
  await expect(page.locator("#form-status")).toContainText(/Ready|Connected/, { timeout: 30_000 });
});

test("one sentence yields every layer with at least one annotation", async ({ page }) => {
  const counts = await analyze(page, SENTENCE);
  expect(counts.length).toBeGreaterThan(2);
  const empty = counts.filter((entry) => entry.count === 0).map((entry) => entry.id);
  expect(empty, `layers with zero annotations: ${empty.join(", ")}`).toEqual([]);
  expect(countOf(counts, "opennlp:sentences")).toBe(1);
  expect(countOf(counts, "opennlp:tokens")).toBeGreaterThanOrEqual(10);
  await expectContentInEveryProjection(page);
});

test("layer counts grow from a sentence to a paragraph to a page", async ({ page }) => {
  const sentence = await analyze(page, SENTENCE);
  const paragraph = await analyze(page, PARAGRAPH);
  const pageCounts = await analyze(page, PAGE);
  for (const id of SCALING_LAYERS) {
    expect(countOf(paragraph, id), id).toBeGreaterThan(countOf(sentence, id));
    expect(countOf(pageCounts, id), id).toBeGreaterThan(countOf(paragraph, id));
  }
  expect(countOf(pageCounts, "opennlp:sentences")).toBe(60);
  await expectContentInEveryProjection(page);
});

test("the bundled novel analyzes with content in every projection", async ({ page }) => {
  // The full pipeline and browser projection are bounded by the test timeout.
  test.setTimeout(1_200_000);
  await page.click("#alice-sample-button");
  await expect(page.locator("#analysis-text")).toHaveValue(/Alice/, { timeout: 30_000 });
  const text = await page.inputValue("#analysis-text");
  expect(text.length).toBeGreaterThan(100_000);
  const counts = await analyze(page, text, 900_000);
  expect(countOf(counts, "opennlp:sentences")).toBeGreaterThan(500);
  expect(countOf(counts, "opennlp:tokens")).toBeGreaterThan(20_000);
  const empty = counts.filter((entry) => entry.count === 0).map((entry) => entry.id);
  expect(empty, `layers with zero annotations: ${empty.join(", ")}`).toEqual([]);
  await expectContentInEveryProjection(page);
});

test("the API returns the same token count the page shows", async ({ page }) => {
  const counts = await analyze(page, PARAGRAPH);
  const reply = await page.request.post("/api/v1/analyze", {
    data: { document: { docId: "regression", rawText: PARAGRAPH } },
  });
  expect(reply.ok()).toBe(true);
  const body = await reply.json();
  const layers: Array<{ id: string; stringValues?: { annotations?: unknown[] } }> =
    body.document?.layers?.layers ?? [];
  const tokens = layers.find((layer) => layer.id === "opennlp:tokens");
  expect(tokens?.stringValues?.annotations?.length).toBe(countOf(counts, "opennlp:tokens"));
});

test("progressive and canonical analysis end with the same layers", async ({ page }) => {
  const canonical = await page.request.post("/api/v1/analyze", {
    data: { document: { docId: "regression", rawText: PARAGRAPH } },
  }).then((reply) => reply.json());
  const progressive = await page.request.post("/api/v1/analyze-progressive", {
    data: { document: { docId: "regression", rawText: PARAGRAPH } },
  });
  expect(progressive.ok()).toBe(true);
  const text = await progressive.text();
  // The progressive endpoint streams events; the last event has the full response.
  const events = text.split("\n").filter((line) => line.trim().length > 0)
    .map((line) => line.replace(/^data:\s*/, "")).filter((line) => line.startsWith("{"))
    .map((line) => JSON.parse(line));
  const terminal = events.map((event) => event.complete).filter((event) => event?.document?.layers?.layers).pop();
  expect(terminal, `no complete event with layers in ${events.length} events`).toBeTruthy();
  const ids = (layers: Array<{ id: string }>) => layers.map((layer) => layer.id).sort();
  expect(ids(terminal.document.layers.layers)).toEqual(ids(canonical.document.layers.layers));
});

test("whitespace-only text cannot be submitted", async ({ page }) => {
  await page.fill("#analysis-text", "   \n\t  ");
  const requests: string[] = [];
  page.on("request", (request) => {
    if (request.url().includes("/api/v1/analyze")) {
      requests.push(request.url());
    }
  });
  await expect(page.locator("#analyze-button")).toBeDisabled();
  await page.locator("#analyze-button").click({ force: true, trial: false }).catch(() => undefined);
  expect(requests).toEqual([]);
});
