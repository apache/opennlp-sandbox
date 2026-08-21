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
  supportsAllHits: false,
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
      <select id="workspace-provider-select">
        <option value="STANDARD_SEARCH_PROVIDER_FLAT_FLOAT" selected></option>
        <option value="STANDARD_SEARCH_PROVIDER_TURBO_QUANT"></option>
      </select>
      <form id="semantic-search-form"><textarea id="semantic-query" disabled></textarea>
        <button id="search-button" type="submit" disabled></button></form>
      <span id="index-count"></span><p id="semantic-status"></p><div id="search-results"></div>
      <form id="heatmap-query-form"><input id="heatmap-query" />
        <button id="heatmap-query-button"></button></form>
      <select id="heatmap-projection-select"></select>
      <button id="heatmap-mode-query"></button><button id="heatmap-mode-sentiment"></button>
      <p id="heatmap-status"></p><div id="document-heatmap"></div><div id="heatmap-selection"></div>
      <div id="document-graph"></div><div id="graph-selection"></div>
      <button id="graph-completeness"></button>`;
  });

  it("indexes the current document on the server when the first workspace query is submitted", async () => {
    const index = vi.fn().mockResolvedValue({
      ...DYNAMIC_INDEX,
      providerId: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT",
      size: 10_000,
      maxTopK: 10_000,
      supportsAllHits: true,
    });
    const search = vi.fn().mockResolvedValue({ hits: [], truncated: false });
    const workbench = new SemanticWorkbench({
      index,
      search,
      deleteIndex: vi.fn(),
      openDocument: vi.fn(),
      selectAnnotation: vi.fn(),
      inspectChunk: vi.fn(),
      inspectSpan: vi.fn(),
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

    const provider = document.getElementById("workspace-provider-select") as HTMLSelectElement;
    provider.value = "STANDARD_SEARCH_PROVIDER_TURBO_QUANT";
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
    await vi.waitFor(() => expect(search).toHaveBeenCalledWith({
      indexId: "workspace-one",
      query: { rawText: "curious rabbit" },
      allHits: true,
    }));
  });

  it("searches every projection exhaustively with TurboQuant and renders selectable lanes", async () => {
    const text = "Alice followed the White Rabbit.";
    const indexes: Record<string, SearchIndex> = {
      "sentence-chunks": {
        ...DYNAMIC_INDEX,
        id: "sentence-index",
        label: "Sentence heatmap",
        providerId: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT",
        size: 1,
        maxTopK: 1,
        supportsAllHits: true,
      },
      "token-chunks": {
        ...DYNAMIC_INDEX,
        id: "token-index",
        label: "Token heatmap",
        providerId: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT",
        size: 1,
        maxTopK: 1,
        supportsAllHits: true,
      },
    };
    const sourceDocument = {
      docId: "alice",
      rawText: text,
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
    };
    const hit = (groupId: string, chunkId: string, start: number, end: number) => ({
      id: `alice/${chunkId}`,
      documentId: "alice",
      chunkId,
      chunkGroupId: groupId,
      score: groupId === "sentence-chunks" ? 0.91 : 0.63,
      sourceDocument,
      sourceText: text,
      start,
      end,
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      emittedChunkText: text.slice(start, end),
      modelId: "demo-embedding",
      backendId: "static",
      vectorSpaceId: "demo-space",
      providerId: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT",
      indexId: indexes[groupId]!.id,
      corpusTitle: "Current document",
      provenance: "Analyzed in this session",
      build: {},
      matchedSpans: [],
    });
    const index = vi.fn().mockImplementation((request: { chunkGroupIds?: string[] }) =>
      Promise.resolve(indexes[request.chunkGroupIds?.[0] ?? ""]));
    const search = vi.fn().mockImplementation((request: { indexId: string }) => Promise.resolve({
      hits: request.indexId === "sentence-index"
        ? [hit("sentence-chunks", "alice:0:0", 0, text.length)]
        : [hit("token-chunks", "alice:1:0", 6, 31)],
      truncated: false,
    }));
    const inspectChunk = vi.fn();
    const deleteIndex = vi.fn().mockResolvedValue(undefined);
    const workbench = new SemanticWorkbench({
      index,
      search,
      deleteIndex,
      openDocument: vi.fn(),
      selectAnnotation: vi.fn(),
      inspectChunk,
      inspectSpan: vi.fn(),
    });
    const response = {
      document: {
        ...sourceDocument,
        chunkEmbeddingGroups: [{
          groupId: "sentence-chunks",
          resultSetName: "Sentences",
          strategy: { standard: "STANDARD_CHUNKING_STRATEGY_SENTENCE" },
          embeddingModelIds: ["demo-embedding"],
          chunks: [{
            annotationSpan: { start: 0, end: text.length },
            textContent: text,
            embeddings: [{ modelId: "demo-embedding", vector: [0.1, 0.2] }],
          }],
        }, {
          groupId: "token-chunks",
          resultSetName: "Token windows",
          strategy: { standard: "STANDARD_CHUNKING_STRATEGY_TOKEN" },
          embeddingModelIds: ["demo-embedding"],
          chunks: [{
            annotationSpan: { start: 6, end: 31 },
            textContent: text.slice(6, 31),
            embeddings: [{ modelId: "demo-embedding", vector: [0.3, 0.4] }],
          }],
        }],
      },
    };
    workbench.setDocument("Demo", {
      rawText: text,
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      layers: [],
    }, response);

    const projection = document.getElementById("heatmap-projection-select") as HTMLSelectElement;
    expect(projection.value).toBe("ALL_PROJECTIONS");
    const query = document.getElementById("heatmap-query") as HTMLInputElement;
    query.value = "rabbit";
    query.dispatchEvent(new Event("input", { bubbles: true }));
    document.getElementById("heatmap-query-form")!
      .dispatchEvent(new SubmitEvent("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(search).toHaveBeenCalledTimes(2));
    expect(index).toHaveBeenCalledWith(expect.objectContaining({
      provider: { standard: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT" },
      chunkGroupIds: ["sentence-chunks"],
    }));
    expect(index).toHaveBeenCalledWith(expect.objectContaining({
      provider: { standard: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT" },
      chunkGroupIds: ["token-chunks"],
    }));
    expect(search).toHaveBeenCalledWith({
      indexId: "sentence-index",
      query: { rawText: "rabbit" },
      allHits: true,
    });
    expect(document.querySelectorAll(".document-heat-lane")).toHaveLength(2);
    expect(document.querySelector(".heat-source")?.textContent).toBe(text);
    const firstChunk = document.querySelector<HTMLButtonElement>(".heat-chunk-card");
    firstChunk?.click();
    expect(inspectChunk).toHaveBeenCalledWith(
      expect.objectContaining({ chunkGroupId: "sentence-chunks", score: 0.91 }),
      expect.objectContaining({ rawText: text }),
      firstChunk,
    );

    workbench.setDocument("Replacement", {
      rawText: "Another document.",
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      layers: [],
    });
    await vi.waitFor(() => expect(deleteIndex).toHaveBeenCalledTimes(2));
    expect(deleteIndex).toHaveBeenCalledWith("sentence-index");
    expect(deleteIndex).toHaveBeenCalledWith("token-index");
  });

  it("opens typed annotations when a sentiment segment is selected", async () => {
    const inspectSpan = vi.fn();
    const workbench = new SemanticWorkbench({
      index: vi.fn(),
      search: vi.fn(),
      deleteIndex: vi.fn(),
      openDocument: vi.fn(),
      selectAnnotation: vi.fn(),
      inspectChunk: vi.fn(),
      inspectSpan,
    });
    const shape = {
      rawText: "Lovely day.",
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      layers: [{
        id: "opennlp:sentiment",
        title: "Sentiment",
        scope: "POSITIONAL",
        valueType: "Category",
        standardIdentity: "opennlp:sentiment",
        annotations: [{ start: 0, end: 11, label: "positive", score: 0.8, source: {} }],
      }],
    };
    workbench.setDocument("Sentiment", shape);

    document.getElementById("heatmap-mode-sentiment")?.click();
    await vi.waitFor(() => expect(document.querySelector(".heat-chunk-card")).not.toBeNull());
    const chunk = document.querySelector<HTMLButtonElement>(".heat-chunk-card")!;
    chunk.click();

    expect(inspectSpan).toHaveBeenCalledWith(shape, 0, 11, "Lovely day.", chunk);
  });
});
