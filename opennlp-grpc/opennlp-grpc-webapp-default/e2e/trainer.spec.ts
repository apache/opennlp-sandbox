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
 * Trainer: the gating states, the pickers filled from the server, and learning a vocabulary.
 * Learning writes an artifact the server keeps, so that test runs only with
 * OPENNLP_E2E_WORKFLOW_WRITE=1, like the workflow build.
 */

const RUN = `e2e-${Date.now().toString(36)}`;

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await page.click('[data-workbench-tab="trainer"]');
  await expect(page.locator("#vocabulary-trainer")).toBeVisible();
});

test("reports the gating state the server is in and disables what it blocks", async ({ page }) => {
  const formats = await page.request.get("/api/v1/dictionary-formats").then((reply) => reply.json());
  const teachers = await page.request.get("/api/v1/teachers").then((reply) => reply.json());
  const status = page.locator("#trainer-status");
  await expect(status).not.toHaveText(/Loading/);
  if (formats.writesEnabled !== true || teachers.writesEnabled !== true) {
    await expect(status).toContainText("off on this server");
    await expect(status).toContainText("vocabulary.artifact_root");
    await expect(page.locator("#trainer-learn-button")).toBeDisabled();
    await expect(page.locator("#trainer-train-button")).toBeDisabled();
  } else if (!Array.isArray(teachers.teachers) || teachers.teachers.length === 0) {
    await expect(status).toContainText("No teacher model is installed");
    await expect(status.locator('[data-workbench-jump="models"]')).toBeVisible();
    await expect(page.locator("#trainer-learn-button")).toBeEnabled();
    await expect(page.locator("#trainer-train-button")).toBeDisabled();
    await expect(page.locator("#trainer-train-button")).toHaveAttribute("title", /teacher/);
  } else {
    await expect(status).toContainText("Paste corpus text to learn a vocabulary");
    await expect(page.locator("#trainer-learn-button")).toBeEnabled();
    await expect(page.locator("#trainer-train-button")).toBeEnabled();
  }
});

test("fills the pickers with the dictionaries and vocabularies the server already has", async ({ page }) => {
  const dictionaries = await page.request.get("/api/v1/dictionaries").then((reply) => reply.json());
  const vocabularies = await page.request.get("/api/v1/vocabularies").then((reply) => reply.json());
  const dictionaryCount = Array.isArray(dictionaries.dictionaries) ? dictionaries.dictionaries.length : 0;
  const vocabularyCount = Array.isArray(vocabularies.vocabularies) ? vocabularies.vocabularies.length : 0;

  await expect(page.locator("#trainer-status")).not.toHaveText(/Loading/);
  const dictionaryOptions = page.locator("#trainer-dictionary-select option");
  await expect(dictionaryOptions).toHaveCount(dictionaryCount + 1);
  await expect(dictionaryOptions.first()).toHaveText("Corpus terms only");
  const vocabularyOptions = page.locator("#trainer-vocabulary-select option");
  if (vocabularyCount === 0) {
    await expect(vocabularyOptions).toHaveText(["No vocabularies learned yet"]);
    await expect(page.locator("#trainer-download-tsv-button")).toBeDisabled();
  } else {
    await expect(vocabularyOptions).toHaveCount(vocabularyCount);
    await expect(page.locator("#trainer-download-tsv-button")).toBeEnabled();
  }
});

test("learns a vocabulary from pasted documents and offers its TSV", async ({ page }) => {
  test.skip(process.env.OPENNLP_E2E_WORKFLOW_WRITE !== "1",
    "Set OPENNLP_E2E_WORKFLOW_WRITE=1 to create a persistent vocabulary artifact.");
  const formats = await page.request.get("/api/v1/dictionary-formats").then((reply) => reply.json());
  test.skip(formats.writesEnabled !== true, "The server has no writable artifact root.");

  await expect(page.locator("#trainer-learn-button")).toBeEnabled();
  await page.fill("#trainer-vocabulary-name", `Trainer ${RUN}`);
  await page.fill("#trainer-min-frequency", "1");
  await page.fill("#trainer-corpus",
    "Courts protect civil rights while elected governments write public policy.\n\n"
    + "People seek liberty, justice, and equal rights in a democratic society.");
  await expect(page.locator("#trainer-corpus-stats")).toContainText("2 documents");
  await page.click("#trainer-learn-button");

  const status = page.locator("#trainer-status");
  await expect(status).toContainText(/Learned \d+ terms/, { timeout: 120_000 });
  const picker = page.locator("#trainer-vocabulary-select");
  await expect(picker.locator("option:checked")).toContainText(`Trainer ${RUN}`);
  await expect(page.locator("#trainer-download-tsv-button")).toBeEnabled();

  // The new artifact is listed by the server, not only by this page.
  const vocabularies = await page.request.get("/api/v1/vocabularies").then((reply) => reply.json());
  expect(vocabularies.vocabularies.some((entry: { displayName?: string }) =>
    entry.displayName === `Trainer ${RUN}`)).toBe(true);
});
