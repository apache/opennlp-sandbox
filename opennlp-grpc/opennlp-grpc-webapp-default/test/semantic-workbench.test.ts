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
  persisted: false,
  corpusTitle: "Workbench index",
  provenance: "Server-owned in-memory workspace",
  build: {},
};

describe("workspace search", () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <button id="add-to-index-button"></button>
      <button id="clear-index-button"></button>
      <select id="workspace-index-select">
        <option value="" selected>New workspace (created on first add)</option>
      </select>
      <select id="workspace-provider-select">
        <option value="STANDARD_SEARCH_PROVIDER_FLAT_FLOAT" selected></option>
        <option value="STANDARD_SEARCH_PROVIDER_TURBO_QUANT"></option>
      </select>
      <form id="semantic-search-form"><textarea id="semantic-query" disabled></textarea>
        <button id="search-button" type="submit" disabled></button></form>
      <span id="index-count"></span><span id="index-storage"></span><p id="semantic-status"></p><div id="search-results"></div>
      <form id="heatmap-query-form"><input id="heatmap-query" />
        <button id="heatmap-query-button"></button></form>
      <select id="heatmap-projection-select"></select>
      <button id="heatmap-mode-query"></button><button id="heatmap-mode-sentiment"></button>
      <p id="heatmap-status"></p><div id="document-heatmap"></div><div id="heatmap-selection"></div>
      <div id="document-graph"></div><div id="graph-selection"></div>
      <button id="graph-completeness"></button>`;
  });

  it("browns out the tab when the operator disabled live indexing", () => {
    const workbench = new SemanticWorkbench({
      index: vi.fn(),
      search: vi.fn(),
      listIndexes: vi.fn(async () => []),
      deleteIndex: vi.fn(),
      openDocument: vi.fn(),
      selectAnnotation: vi.fn(),
      inspectChunk: vi.fn(),
      inspectSpan: vi.fn(),
    });
    workbench.setAvailability(false);

    expect((document.getElementById("search-button") as HTMLButtonElement).disabled).toBe(true);
    expect((document.getElementById("add-to-index-button") as HTMLButtonElement).disabled).toBe(true);
    expect(document.getElementById("semantic-status")!.textContent)
      .toContain("Live indexing is disabled by the server operator");
  });

  it("reports the add-to-index outcome to the tab that pressed the button", async () => {
    const outcomes: Array<[string, boolean]> = [];
    const index = vi.fn().mockResolvedValue({ id: "live-1", label: "Workbench index", size: 3,
      providerId: "flat_float", modelId: "demo-embedding", backendId: "static",
      vectorSpaceId: "demo", metric: "cosine", supportsAllHits: true, immutable: false,
      corpusTitle: "Workbench index", provenance: "test", build: {} });
    const workbench = new SemanticWorkbench({
      index,
      search: vi.fn(),
      listIndexes: vi.fn(async () => []),
      deleteIndex: vi.fn(),
      openDocument: vi.fn(),
      selectAnnotation: vi.fn(),
      inspectChunk: vi.fn(),
      inspectSpan: vi.fn(),
      onIndexed: (message, error) => outcomes.push([message, error]),
    });
    const button = document.getElementById("add-to-index-button") as HTMLButtonElement;
    expect(button.disabled).toBe(true);

    workbench.setDocument("Demo", {
      rawText: "Alice followed the White Rabbit.",
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      layers: [],
    }, { document: {
      rawText: "Alice followed the White Rabbit.",
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      chunkEmbeddingGroups: [{ groupId: "sentence-chunks", embeddingModelIds: ["demo-embedding"] }],
      layers: [],
      sentences: [{ text: "Alice followed the White Rabbit." }],
      embeddings: [{ modelId: "demo-embedding", vector: [0.1, 0.2] }],
    } });
    button.click();
    await vi.waitFor(() => expect(index).toHaveBeenCalled());
    await vi.waitFor(() => expect(outcomes.at(-1)?.[0]).toContain("Added to live index 'Workbench index'"));
    expect(outcomes.at(-1)?.[1]).toBe(false);
  });

  it("asks before deleting a live index and tells other tabs afterwards", async () => {
    const deleteIndex = vi.fn(async () => undefined);
    const onWorkspacesChanged = vi.fn();
    const answers: boolean[] = [false, true];
    const workbench = new SemanticWorkbench({
      index: vi.fn(),
      search: vi.fn(),
      listIndexes: vi.fn(async () => [{
        id: "live-1", label: "Notes", providerId: "flat_float", modelId: "m", backendId: "static",
        vectorSpaceId: "s", metric: "cosine", supportsAllHits: true, immutable: false,
        persisted: true,
        corpusTitle: "Notes", provenance: "test", build: {}, size: 2,
      }]),
      deleteIndex,
      openDocument: vi.fn(),
      selectAnnotation: vi.fn(),
      inspectChunk: vi.fn(),
      inspectSpan: vi.fn(),
      confirmDelete: () => answers.shift() ?? false,
      onWorkspacesChanged,
    });
    await workbench.initializeWorkspaces();
    const picker = document.getElementById("workspace-index-select") as HTMLSelectElement;
    // The picker says which state each index is in, from the descriptor's flags.
    expect(picker.options[1]?.textContent).toBe("Notes · 2 chunks · Saved to disk");
    picker.value = "live-1";
    picker.dispatchEvent(new Event("change"));
    await vi.waitFor(() => expect(document.getElementById("index-storage")!.textContent)
      .toBe("Saved to disk"));
    await vi.waitFor(() => expect(document.getElementById("semantic-status")!.textContent)
      .toContain("Searching 'Notes'"));
    const clear = document.getElementById("clear-index-button") as HTMLButtonElement;

    clear.click();
    expect(deleteIndex).not.toHaveBeenCalled();

    clear.click();
    await vi.waitFor(() => expect(deleteIndex).toHaveBeenCalledWith("live-1"));
    await vi.waitFor(() => expect(onWorkspacesChanged).toHaveBeenCalled());
  });

  it("shows where a first live index comes from when the server has none", async () => {
    const workbench = new SemanticWorkbench({
      index: vi.fn(),
      search: vi.fn(),
      listIndexes: vi.fn(async () => []),
      deleteIndex: vi.fn(),
      openDocument: vi.fn(),
      selectAnnotation: vi.fn(),
      inspectChunk: vi.fn(),
      inspectSpan: vi.fn(),
    });
    await workbench.initializeWorkspaces();

    const results = document.getElementById("search-results")!;
    expect(results.textContent).toContain("No live indexes yet");
    expect(Array.from(results.querySelectorAll<HTMLElement>("[data-workbench-jump]"))
      .map((jump) => jump.dataset.workbenchJump)).toEqual(["analysis", "workflows"]);
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
      listIndexes: vi.fn(async () => []),
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
      indexedChunkText: text.slice(start, end),
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
      listIndexes: vi.fn(async () => []),
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
      listIndexes: vi.fn(async () => []),
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

  it("attaches search to a picked existing workspace without adding a document", async () => {
    const search = vi.fn().mockResolvedValue({ hits: [], truncated: false });
    const index = vi.fn();
    const listIndexes = vi.fn(async () => [
      { ...DYNAMIC_INDEX, size: 12 },
      { ...DYNAMIC_INDEX, id: "heatmap-x", label: "Current document heatmap: Sentences" },
      { ...DYNAMIC_INDEX, id: "sealed-one", immutable: true },
    ]);
    const workbench = new SemanticWorkbench({
      index,
      search,
      listIndexes,
      deleteIndex: vi.fn(),
      openDocument: vi.fn(),
      selectAnnotation: vi.fn(),
      inspectChunk: vi.fn(),
      inspectSpan: vi.fn(),
    });
    await workbench.initializeWorkspaces();

    // Heatmap scratch indexes and immutable indexes stay out of the picker.
    const picker = document.getElementById("workspace-index-select") as HTMLSelectElement;
    expect([...picker.options].map((option) => option.value)).toEqual(["", "workspace-one"]);
    picker.value = "workspace-one";
    picker.dispatchEvent(new Event("change", { bubbles: true }));
    await vi.waitFor(() => expect(document.getElementById("semantic-status")?.textContent)
      .toContain("Searching 'Workbench index'"));

    const query = document.getElementById("semantic-query") as HTMLTextAreaElement;
    expect(query.disabled).toBe(false);
    query.value = "termination clauses";
    query.dispatchEvent(new Event("input", { bubbles: true }));
    document.getElementById("semantic-search-form")!
      .dispatchEvent(new SubmitEvent("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(search).toHaveBeenCalledWith(
      expect.objectContaining({ indexId: "workspace-one" })));
    expect(index).not.toHaveBeenCalled();
  });
});
