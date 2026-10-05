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
 * The trainer must learn more as the corpus grows. Each run writes a vocabulary
 * artifact the server keeps, so the suite runs only with OPENNLP_E2E_WORKFLOW_WRITE=1.
 */

const RUN = `size-${Date.now().toString(36)}`;

const TWO_DOCUMENTS = [
  "Courts protect civil rights while elected governments write public policy.",
  "People seek liberty, justice, and equal rights in a democratic society.",
];

const TOPICS = ["rivers", "harbors", "mountains", "forests", "deserts", "islands", "glaciers",
  "prairies", "canyons", "wetlands", "volcanoes", "reefs", "valleys", "tundra", "savannas",
  "caves", "lagoons", "fjords", "dunes", "marshes"];

/** Twenty paragraphs, each about a different landscape, so the term count has room to grow. */
const TWENTY_DOCUMENTS = TOPICS.map((topic, i) =>
  `Travelers describe the ${topic} of the northern province in every season. `
  + `Farmers near the ${topic} trade grain, wool, and timber at the ${i % 2 === 0 ? "spring" : "autumn"} market. `
  + `Surveyors mapped the ${topic} in ${1800 + i} and named ${i + 2} settlements along the road.`);

async function openTrainer(page: Page): Promise<void> {
  await page.goto("/");
  await page.click('[data-workbench-tab="trainer"]');
  await expect(page.locator("#vocabulary-trainer")).toBeVisible();
  await expect(page.locator("#trainer-status")).not.toHaveText(/Loading/);
}

/** Learns a vocabulary from the documents and returns the term count the status reports. */
async function learn(page: Page, name: string, documents: string[]): Promise<number> {
  await page.fill("#trainer-vocabulary-name", name);
  await page.fill("#trainer-min-frequency", "1");
  await page.fill("#trainer-corpus", documents.join("\n\n"));
  await expect(page.locator("#trainer-corpus-stats"))
    .toContainText(`${documents.length} document${documents.length === 1 ? "" : "s"}`);
  await page.click("#trainer-learn-button");
  const status = page.locator("#trainer-status");
  await expect(status).toContainText(/Learned \d+ terms/, { timeout: 300_000 });
  const match = /Learned (\d[\d,]*) terms/.exec((await status.textContent()) ?? "");
  return Number((match?.[1] ?? "0").replace(/,/g, ""));
}

test.beforeEach(async ({ page }) => {
  test.skip(process.env.OPENNLP_E2E_WORKFLOW_WRITE !== "1",
    "Set OPENNLP_E2E_WORKFLOW_WRITE=1 to create persistent vocabulary artifacts.");
  const formats = await page.request.get("/api/v1/dictionary-formats").then((reply) => reply.json());
  test.skip(formats.writesEnabled !== true, "The server has no writable artifact root.");
  await openTrainer(page);
});

test("learns more terms from two documents, from twenty, and from a whole novel", async ({ page }) => {
  test.setTimeout(900_000);
  const small = await learn(page, `${RUN} two`, TWO_DOCUMENTS);
  expect(small).toBeGreaterThan(10);

  const medium = await learn(page, `${RUN} twenty`, TWENTY_DOCUMENTS);
  expect(medium).toBeGreaterThan(small);

  await page.click('[data-workbench-tab="analysis"]');
  await page.click("#alice-sample-button");
  await expect(page.locator("#analysis-text")).toHaveValue(/Alice/, { timeout: 30_000 });
  const novel = await page.inputValue("#analysis-text");
  const paragraphs = novel.split(/\n\s*\n/).map((p) => p.trim()).filter((p) => p.length > 0);
  expect(paragraphs.length).toBeGreaterThan(100);
  await page.click('[data-workbench-tab="trainer"]');
  const large = await learn(page, `${RUN} novel`, paragraphs);
  expect(large).toBeGreaterThan(medium * 5);

  // Every run is listed by the server, with its term count.
  const listing = await page.request.get("/api/v1/vocabularies").then((reply) => reply.json());
  const mine = (listing.vocabularies as Array<{ displayName?: string; termCount?: number | string }>)
    .filter((entry) => entry.displayName?.startsWith(RUN));
  expect(mine).toHaveLength(3);
  for (const entry of mine) {
    expect(Number(entry.termCount)).toBeGreaterThan(0);
  }
});

test("the TSV download has one line per learned term", async ({ page }) => {
  const count = await learn(page, `${RUN} tsv`, TWO_DOCUMENTS);
  const picker = page.locator("#trainer-vocabulary-select");
  await expect(picker.locator("option:checked")).toContainText(`${RUN} tsv`);
  const artifactId = await picker.inputValue();
  const reply = await page.request.post("/api/v1/download-vocabulary", { data: { artifactId } });
  expect(reply.ok()).toBe(true);
  const body = await reply.text();
  const lines = body.split("\n").filter((line) => line.trim().length > 0 && !line.startsWith("#"));
  // A header line may precede the terms.
  expect(lines.length).toBeGreaterThanOrEqual(count);
  expect(lines.length).toBeLessThanOrEqual(count + 1);
});

test("an empty corpus is rejected with a message and no artifact", async ({ page }) => {
  const before = await page.request.get("/api/v1/vocabularies").then((reply) => reply.json());
  await page.fill("#trainer-vocabulary-name", `${RUN} empty`);
  await page.fill("#trainer-corpus", "   \n\n   ");
  await page.click("#trainer-learn-button");
  await expect(page.locator("#trainer-status")).toContainText(/at least one/i);
  const after = await page.request.get("/api/v1/vocabularies").then((reply) => reply.json());
  expect((after.vocabularies ?? []).length).toBe((before.vocabularies ?? []).length);
});
