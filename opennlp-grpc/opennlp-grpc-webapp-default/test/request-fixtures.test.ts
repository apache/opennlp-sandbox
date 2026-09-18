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

import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it, vi } from "vitest";

import { buildAnalysisRequest, discoverAnalysisCapabilities } from "../src/analysis-config";
import {
  analyze,
  deleteCollection,
  deleteIndexAlias,
  deleteSearchIndex,
  deleteStaticModel,
  getCollection,
  indexDocuments,
  persistIndex,
  reindexIndex,
  sealIndex,
  searchIndex,
  setCollection,
  setIndexAlias,
} from "../src/api";
import { buildQueryNode } from "../src/query-builder";
import {
  createAllHitsSearchRequest,
  createCompoundSearchRequest,
  createIndexDocumentsRequest,
  createSearchRequest,
} from "../src/search-adapter";

/**
 * Records every JSON request body the workbench can send, produced by the real builders and
 * the real API client, as checked-in fixtures. The gateway's FrontEndRequestFixtureTest
 * parses each one with the same strict protobuf JSON parser production uses, so a field the
 * front end renames without the proto fails a Java test instead of a user's click.
 *
 * Regenerate after an intended change with OPENNLP_UPDATE_FIXTURES=1 npm test.
 */

const FIXTURE_DIR = join(__dirname, "fixtures", "requests");

interface Captured {
  route: string;
  body: string;
}

/** A fetcher that records the request and answers with an empty JSON object. */
function capturing(): { fetcher: typeof fetch; captured: Captured[] } {
  const captured: Captured[] = [];
  const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    captured.push({ route: String(input), body: String(init?.body ?? "") });
    return new Response("{}", { status: 200, headers: { "content-type": "application/json" } });
  }) as unknown as typeof fetch;
  return { fetcher, captured };
}

const serviceInfo = {
  availableProfileIds: ["en-basic", "en-sentiment"],
  supportedSteps: [
    "PIPELINE_STEP_LANGUAGE_DETECT",
    "PIPELINE_STEP_SENTENCE_DETECT",
    "PIPELINE_STEP_TOKENIZE",
    "PIPELINE_STEP_POS_TAG",
    "PIPELINE_STEP_LEMMATIZE",
    "PIPELINE_STEP_SENTIMENT",
    "PIPELINE_STEP_EMBED",
    "PIPELINE_STEP_CHUNK",
  ],
  maxTextBytes: 1048576,
};

const bundlesInfo = {
  bundles: [{
    bundleId: "en-basic",
    supportedLanguages: ["en"],
    supportedSteps: serviceInfo.supportedSteps,
    embeddingModels: [{ modelId: "minilm", backendId: "static" }],
  }],
};

const DOCUMENT = { docId: "fixture-1", rawText: "The cats sat on the mats." };

/** Every fixture, keyed by file name; the part before "--" is the gateway route. */
async function buildFixtures(): Promise<Map<string, string>> {
  const { fetcher, captured } = capturing();
  const capabilities = discoverAnalysisCapabilities(serviceInfo, bundlesInfo);
  await analyze(buildAnalysisRequest(DOCUMENT.rawText, {
    mode: "max",
    sentenceChunks: true,
    tokenChunks: true,
    tokenChunkSize: 96,
    tokenChunkOverlap: 12,
    embeddingModelId: "minilm",
    posTagFormat: "POS_TAG_FORMAT_UD",
  }, capabilities), fetcher);
  await searchIndex(createSearchRequest("legal-demo", "writ of habeas corpus", 5), fetcher);
  await searchIndex(createAllHitsSearchRequest("legal-demo", "writ"), fetcher);
  await searchIndex(createCompoundSearchRequest("legal-demo", buildQueryNode([
    { kind: "semantic", text: "unlawful detention" },
    { kind: "term", text: "habeas corpus", mode: "all" },
    { kind: "phrase", text: "writ of habeas corpus", slop: 1 },
  ], "rrf"), 5), fetcher);
  await indexDocuments(createIndexDocumentsRequest(undefined,
    "STANDARD_SEARCH_PROVIDER_FLAT_FLOAT", DOCUMENT, "minilm", ["sentence-chunks"]), fetcher);
  await indexDocuments(createIndexDocumentsRequest("live-1",
    "STANDARD_SEARCH_PROVIDER_FLAT_FLOAT", DOCUMENT, "minilm", ["sentence-chunks"]), fetcher);
  await deleteSearchIndex("live-1", fetcher);
  await persistIndex("live-1", fetcher);
  await sealIndex("live-1", fetcher);
  await reindexIndex({
    indexId: "live-1",
    embedding: { modelId: "minilm" },
    provider: { standard: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT" },
    alias: "current",
  }, fetcher);
  await setIndexAlias("current", "live-1", fetcher);
  await deleteIndexAlias("current", fetcher);
  await setCollection({
    collectionId: "legal",
    displayName: "Legal",
    memberIndexIds: ["live-1"],
    dictionaryArtifactId: "dictionary-1",
    vocabularyArtifactId: "vocabulary-1",
    modelArtifactId: "static-model-1",
    driftNewTermThreshold: 25,
  }, fetcher);
  await getCollection("legal", fetcher);
  await deleteCollection("legal", fetcher);
  await deleteStaticModel("static-model-1", fetcher);

  const fixtures = new Map<string, string>();
  const seen = new Map<string, number>();
  for (const { route, body } of captured) {
    const name = route.slice("/api/v1/".length);
    const count = (seen.get(name) ?? 0) + 1;
    seen.set(name, count);
    const file = count === 1 ? `${name}.json` : `${name}--${count}.json`;
    fixtures.set(file, `${JSON.stringify(JSON.parse(body), null, 2)}\n`);
  }
  return fixtures;
}

describe("request fixtures", () => {
  it("match the checked-in files the gateway test parses", async () => {
    const fixtures = await buildFixtures();
    expect(fixtures.size).toBeGreaterThanOrEqual(16);
    if (process.env.OPENNLP_UPDATE_FIXTURES) {
      mkdirSync(FIXTURE_DIR, { recursive: true });
      for (const [file, content] of fixtures) {
        writeFileSync(join(FIXTURE_DIR, file), content);
      }
    }
    const stale: string[] = [];
    for (const [file, content] of fixtures) {
      const path = join(FIXTURE_DIR, file);
      if (!existsSync(path) || readFileSync(path, "utf8") !== content) {
        stale.push(file);
      }
    }
    expect(stale, "fixtures out of date; run OPENNLP_UPDATE_FIXTURES=1 npm test").toEqual([]);
  });
});
