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

/** @vitest-environment jsdom */

import { beforeEach, describe, expect, it, vi } from "vitest";

import { SemanticWorkbench } from "../src/semantic-workbench";
import type { SearchIndex } from "../src/search-adapter";

const DYNAMIC_INDEX: SearchIndex = {
  id: "workspace-one",
  label: "Workbench index",
  providerId: "STANDARD_SEARCH_PROVIDER_FLAT_FLOAT",
  modelId: "demo-embedding",
  backendId: "static",
  vectorSpaceId: "demo-space",
  dimension: 2,
  metric: "SEARCH_METRIC_COSINE",
  size: 1,
  maxTopK: 50,
  maxQueryBytes: 65_536,
  maxResponseBytes: 4_194_304,
  immutable: false,
  corpusTitle: "Workbench index",
  provenance: "Server-owned in-memory workspace",
  build: {},
};

describe("workspace search", () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <button id="add-to-index-button"></button>
      <button id="clear-index-button"></button>
      <form id="semantic-search-form"><textarea id="semantic-query" disabled></textarea>
        <button id="search-button" type="submit" disabled></button></form>
      <span id="index-count"></span><p id="semantic-status"></p><div id="search-results"></div>
      <form id="heatmap-query-form"><input id="heatmap-query" />
        <button id="heatmap-query-button"></button></form>
      <button id="heatmap-mode-query"></button><button id="heatmap-mode-sentiment"></button>
      <p id="heatmap-status"></p><div id="document-heatmap"></div><div id="heatmap-selection"></div>
      <div id="document-graph"></div><div id="graph-selection"></div>
      <button id="graph-completeness"></button>`;
  });

  it("indexes the current document on the server when the first workspace query is submitted", async () => {
    const index = vi.fn().mockResolvedValue(DYNAMIC_INDEX);
    const search = vi.fn().mockResolvedValue({ hits: [], truncated: false });
    const workbench = new SemanticWorkbench({
      index,
      search,
      deleteIndex: vi.fn(),
      openDocument: vi.fn(),
      selectAnnotation: vi.fn(),
    });
    const response = {
      document: {
        docId: "alice",
        rawText: "Alice followed the White Rabbit.",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        metadata: { source: "demo" },
        chunkEmbeddingGroups: [{
          groupId: "sentence-chunks",
          embeddingModelIds: ["demo-embedding"],
        }],
        layers: [{ layerId: "opennlp:tokens", annotations: [{ start: 0, end: 5 }] }],
        sentences: [{ text: "Alice followed the White Rabbit." }],
        embeddings: [{ modelId: "demo-embedding", vector: [0.1, 0.2] }],
      },
    };
    workbench.setDocument("Demo", {
      rawText: "Alice followed the White Rabbit.",
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      layers: [],
    }, response);

    const query = document.getElementById("semantic-query") as HTMLTextAreaElement;
    expect(query.disabled).toBe(false);
    query.value = "curious rabbit";
    query.dispatchEvent(new Event("input", { bubbles: true }));
    document.getElementById("semantic-search-form")!
      .dispatchEvent(new SubmitEvent("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(index).toHaveBeenCalledTimes(1));
    expect(index).toHaveBeenCalledWith(expect.objectContaining({
      documents: [{
        docId: "alice",
        rawText: "Alice followed the White Rabbit.",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        metadata: { source: "demo" },
        chunkEmbeddingGroups: [{
          groupId: "sentence-chunks",
          embeddingModelIds: ["demo-embedding"],
        }],
      }],
    }));
    await vi.waitFor(() => expect(search).toHaveBeenCalledWith(expect.objectContaining({
      indexId: "workspace-one",
      query: { rawText: "curious rabbit" },
    })));
  });
});
