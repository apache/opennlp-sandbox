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

import { readDocumentShape } from "../src/document-shape";
import {
  annotationsIntersecting,
  compareChunkText,
  documentAnalytics,
  scoreColor,
  searchResultStatus,
  SearchSelection,
  sourceHighlight,
} from "../src/search-view-model";
import type { SearchHit } from "../src/search-adapter";

const hit: SearchHit = {
  id: "doc-1/chunk-1",
  documentId: "doc-1",
  chunkId: "chunk-1",
  score: 0.8,
  sourceDocument: { rawText: "A😀 café in Paris.", offsetEncoding: "OFFSET_ENCODING_UTF8_BYTE" },
  sourceText: "A😀 café in Paris.",
  start: 1,
  end: 11,
  offsetEncoding: "OFFSET_ENCODING_UTF8_BYTE",
  emittedChunkText: "😀 cafe",
  modelId: "mini",
  backendId: "static",
  vectorSpaceId: "mini-v1",
  providerId: "STANDARD_SEARCH_PROVIDER_FLAT_FLOAT",
  indexId: "demo",
  corpusTitle: "Demo corpus",
  provenance: "Fixture",
  build: {},
  queryEmbeddingRoute: { modelId: "mini", backendId: "fallback", vectorSpaceId: "mini-v1" },
};

describe("server search view model", () => {
  it("uses a fixed accessible red-neutral-green score scale", () => {
    expect(scoreColor(-1)).toEqual({ background: "#b42318", foreground: "#ffffff" });
    expect(scoreColor(0)).toEqual({ background: "#e5e7eb", foreground: "#111827" });
    expect(scoreColor(1)).toEqual({ background: "#16835a", foreground: "#ffffff" });
    expect(scoreColor(-5)).toEqual(scoreColor(-1));
    expect(scoreColor(5)).toEqual(scoreColor(1));
  });

  it("converts source offsets safely and preserves the original span", () => {
    expect(sourceHighlight(hit)).toEqual({ before: "A", selected: "😀 café", after: " in Paris." });
  });

  it("reports whether normalization changed the emitted chunk", () => {
    expect(compareChunkText("café", "cafe")).toEqual({ exact: false, original: "café", emitted: "cafe" });
    expect(compareChunkText("same", "same")).toEqual({ exact: true, original: "same", emitted: "same" });
  });

  it("counts document analytics and finds annotations intersecting the selected source span", () => {
    const shape = readDocumentShape({
      document: {
        rawText: "One person visits Paris.",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: { layers: [
          { id: "opennlp:sentences", stringValues: { annotations: [{ span: { start: 0, end: 24 }, value: "s" }] } },
          { id: "opennlp:tokens", stringValues: { annotations: [
            { span: { start: 0, end: 3 }, value: "One" },
            { span: { start: 4, end: 10 }, value: "person" },
            { span: { start: 18, end: 23 }, value: "Paris" },
          ] } },
          { id: "opennlp:entities", entityValues: { annotations: [
            { span: { start: 18, end: 23 }, type: "location" },
          ] } },
          { id: "opennlp:chunk-groups", chunkGroupValues: { annotations: [{ span: { start: 4, end: 23 } }] } },
          { id: "OPENNLP:TERM-VECTORS", termVectorValues: { annotations: [{ term: "paris" }, { term: "visit" }] } },
        ] },
      },
    });

    expect(documentAnalytics(shape)).toEqual({ sentences: 1, tokens: 3, entities: 1, chunks: 1, terms: 2 });
    expect(annotationsIntersecting(shape, 18, 23).map((entry) => `${entry.layer.id}:${entry.annotation.label}`))
      .toEqual([
        "opennlp:sentences:s",
        "opennlp:tokens:Paris",
        "opennlp:entities:location",
        "opennlp:chunk-groups:Annotation 1",
      ]);
  });

  it("changes click selection without mutating the hit list", () => {
    const second = { ...hit, id: "doc-2/chunk-2", documentId: "doc-2", chunkId: "chunk-2" };
    const hits = [hit, second];
    const selection = new SearchSelection();

    expect(selection.select(hits, second.id)).toBe(second);
    expect(selection.selectedId).toBe(second.id);
    expect(hits).toEqual([hit, second]);
    expect(selection.select(hits, "missing")).toBeUndefined();
  });

  it("reports server truncation as a bounded successful result", () => {
    expect(searchResultStatus(5, true)).toBe(
      "5 scored chunks returned. The server response byte limit truncated additional matches.",
    );
    expect(searchResultStatus(0, true)).toBe(
      "No scored chunks were returned. The server response byte limit truncated additional matches.",
    );
  });
});
