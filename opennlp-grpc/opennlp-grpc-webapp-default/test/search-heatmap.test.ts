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

import { describe, expect, it } from "vitest";
import type { SearchHit } from "../src/search-adapter";
import { buildDocumentHeat } from "../src/search-heatmap";

const SOURCE = "The writ of habeas corpus protects liberty. Detention requires review.";

function hit(overrides: Partial<SearchHit>): SearchHit {
  return {
    id: "doc-1/chunk-1",
    documentId: "doc-1",
    chunkId: "chunk-1",
    score: 0.5,
    sourceDocument: {},
    sourceText: SOURCE,
    start: 0,
    end: 43,
    offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
    emittedChunkText: SOURCE.slice(0, 43),
    modelId: "model",
    backendId: "backend",
    vectorSpaceId: "space",
    providerId: "provider",
    indexId: "index",
    corpusTitle: "Corpus",
    provenance: "",
    build: {},
    matchedSpans: [],
    ...overrides,
  };
}

describe("buildDocumentHeat", () => {
  it("groups hits by document and orders documents by their best score", () => {
    const documents = buildDocumentHeat([
      hit({ id: "a/1", documentId: "a", chunkId: "1", score: 0.2 }),
      hit({ id: "b/1", documentId: "b", chunkId: "1", score: 0.9 }),
      hit({
        id: "a/2", documentId: "a", chunkId: "2", score: 0.7,
        start: 44, end: 70, emittedChunkText: SOURCE.slice(44, 70),
      }),
    ]);
    expect(documents.map((document) => document.documentId)).toEqual(["b", "a"]);
    expect(documents[1]?.maxScore).toBe(0.7);
    expect(documents[1]?.chunkCount).toBe(2);
  });

  it("emits scored chunk segments and unscored gaps in source order", () => {
    const documents = buildDocumentHeat([
      hit({
        id: "doc-1/late", chunkId: "late", score: 0.8,
        start: 44, end: 70, emittedChunkText: SOURCE.slice(44, 70),
      }),
      hit({ id: "doc-1/early", chunkId: "early", score: 0.3, start: 0, end: 43 }),
    ]);
    const segments = documents[0]?.segments ?? [];
    expect(segments.map((segment) => segment.text)).toEqual([
      SOURCE.slice(0, 43),
      SOURCE.slice(43, 44),
      SOURCE.slice(44, 70),
    ]);
    expect(segments[0]?.chunkId).toBe("early");
    expect(segments[0]?.score).toBe(0.3);
    expect(segments[0]?.hitId).toBe("doc-1/early");
    expect(segments[1]?.chunkId).toBeUndefined();
    expect(segments[1]?.score).toBeUndefined();
    expect(segments[2]?.chunkId).toBe("late");
    expect(segments[2]?.score).toBe(0.8);
  });

  it("keeps matched spans when the emitted text equals the source slice", () => {
    const documents = buildDocumentHeat([
      hit({ matchedSpans: [{ start: 12, end: 25, term: "habeas corpus" }] }),
    ]);
    expect(documents[0]?.segments[0]?.matchedSpans).toEqual([
      { start: 12, end: 25, term: "habeas corpus" },
    ]);
  });

  it("drops matched spans when the emitted text was transformed", () => {
    const documents = buildDocumentHeat([
      hit({
        emittedChunkText: "the writ of habeas corpus protects liberty.",
        matchedSpans: [{ start: 12, end: 25, term: "habeas corpus" }],
      }),
    ]);
    expect(documents[0]?.segments[0]?.matchedSpans).toEqual([]);
    expect(documents[0]?.segments[0]?.score).toBe(0.5);
  });

  it("skips chunks overlapping an earlier chunk so text never repeats", () => {
    const documents = buildDocumentHeat([
      hit({ id: "doc-1/one", chunkId: "one", score: 0.6, start: 0, end: 43 }),
      hit({
        id: "doc-1/two", chunkId: "two", score: 0.4,
        start: 20, end: 70, emittedChunkText: SOURCE.slice(20, 70),
      }),
    ]);
    const rendered = (documents[0]?.segments ?? []).map((segment) => segment.text).join("");
    expect(rendered).toBe(SOURCE);
    expect(documents[0]?.chunkCount).toBe(1);
  });

  it("maps UTF-8 byte offsets onto browser text", () => {
    const source = "Précis first. Détail second.";
    const documents = buildDocumentHeat([
      hit({
        sourceText: source,
        offsetEncoding: "OFFSET_ENCODING_UTF8_BYTE",
        start: 15,
        end: 30,
        emittedChunkText: "Détail second.",
      }),
    ]);
    const segments = documents[0]?.segments ?? [];
    expect(segments.map((segment) => segment.text)).toEqual(["Précis first. ", "Détail second."]);
    expect(segments[1]?.score).toBe(0.5);
  });

  it("returns no documents for no hits", () => {
    expect(buildDocumentHeat([])).toEqual([]);
  });
});
