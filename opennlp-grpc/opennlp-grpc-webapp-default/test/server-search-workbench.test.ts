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

import { readFileSync } from "node:fs";
import { join } from "node:path";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { SearchHit, SearchIndex } from "../src/search-adapter";
import { ServerSearchWorkbench } from "../src/server-search-workbench";

const html = readFileSync(join(import.meta.dirname, "..", "index.html"), "utf8");

function testIndex(): SearchIndex {
  return {
    id: "workspace-test",
    label: "Test workspace",
    providerId: "turbo-quant",
    modelId: "static-model-test",
    backendId: "static",
    vectorSpaceId: "space-test",
    metric: "METRIC_COSINE",
    supportsAllHits: false,
    immutable: false,
    persisted: false,
    corpusTitle: "Test corpus",
    provenance: "Built by the workbench test",
    build: {},
  };
}

async function mountWorkbench(): Promise<ServerSearchWorkbench> {
  document.body.innerHTML = html.replace(/^[\s\S]*<body[^>]*>/, "").replace(/<\/body>[\s\S]*$/, "");
  const workbench = new ServerSearchWorkbench({
    listIndexes: () => Promise.resolve([testIndex()]),
    search: () => Promise.resolve({ hits: [], truncated: false }),
    analyzeSource: () => Promise.reject(new Error("not exercised")),
  });
  await workbench.initialize();
  return workbench;
}

function testHit(overrides: Partial<SearchHit> = {}): SearchHit {
  return {
    id: "hit-1",
    documentId: "alice-1",
    chunkId: "chunk-1",
    chunkGroupId: "group-1",
    score: 0.9,
    sourceDocument: {},
    sourceText: "Alice went home.",
    start: 0,
    end: 5,
    offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
    indexedChunkText: "Alice",
    modelId: "static-model-test",
    backendId: "static",
    vectorSpaceId: "space-test",
    providerId: "turbo-quant",
    indexId: "workspace-test",
    corpusTitle: "Test corpus",
    provenance: "Built by the workbench test",
    build: {},
    matchedSpans: [],
    ...overrides,
  };
}

/** Mounts the workbench, runs one query returning the hit, and waits for its selection. */
async function searchOneHit(
  hit: SearchHit,
  analyzeSource: () => Promise<never>,
): Promise<void> {
  document.body.innerHTML = html.replace(/^[\s\S]*<body[^>]*>/, "").replace(/<\/body>[\s\S]*$/, "");
  const workbench = new ServerSearchWorkbench({
    listIndexes: () => Promise.resolve([testIndex()]),
    search: () => Promise.resolve({ hits: [hit], truncated: false }),
    analyzeSource,
  });
  await workbench.initialize();
  const query = document.getElementById("server-search-query") as HTMLInputElement;
  query.value = "alice";
  query.dispatchEvent(new Event("input", { bubbles: true }));
  document.getElementById("server-search-form")
    ?.dispatchEvent(new SubmitEvent("submit", { bubbles: true, cancelable: true }));
  await vi.waitFor(() => {
    expect(document.getElementById("search-original-span")?.textContent).not.toBe("No selection");
  });
}

describe("server search hit inspection", () => {
  beforeEach(() => {
    document.body.innerHTML = "";
  });

  it("points an empty index list at the Build index tab", async () => {
    document.body.innerHTML = html.replace(/^[\s\S]*<body[^>]*>/, "").replace(/<\/body>[\s\S]*$/, "");
    const workbench = new ServerSearchWorkbench({
      listIndexes: () => Promise.resolve([]),
      search: () => Promise.resolve({ hits: [], truncated: false }),
      analyzeSource: () => Promise.reject(new Error("not exercised")),
    });
    await workbench.initialize();

    const description = document.getElementById("server-index-description")!;
    expect(description.textContent).toContain("No index exists yet");
    expect(description.querySelector<HTMLElement>("[data-workbench-jump]")?.dataset.workbenchJump)
      .toBe("workflows");
  });

  it("explains a missing index with jumps to where indexes are built and saved", async () => {
    document.body.innerHTML = html.replace(/^[\s\S]*<body[^>]*>/, "").replace(/<\/body>[\s\S]*$/, "");
    const workbench = new ServerSearchWorkbench({
      listIndexes: () => Promise.resolve([testIndex()]),
      search: () => Promise.reject(new Error("Unknown search index 'workspace-test': no read-only bundle or live index has that id or alias")),
      analyzeSource: () => Promise.reject(new Error("not exercised")),
    });
    await workbench.initialize();
    (document.getElementById("server-search-query") as HTMLInputElement).value = "writ";
    document.getElementById("server-search-query")!.dispatchEvent(new Event("input", { bubbles: true }));
    document.getElementById("server-search-form")!
      .dispatchEvent(new SubmitEvent("submit", { bubbles: true, cancelable: true }));
    await vi.waitFor(() => expect(document.getElementById("server-search-status")!.textContent)
      .toContain("Unknown search index"));

    const jumps = Array.from(document.querySelectorAll<HTMLElement>("#server-search-status [data-workbench-jump]"))
      .map((jump) => jump.dataset.workbenchJump);
    expect(jumps).toEqual(["workflows", "lifecycle"]);
  });

  it("offers only semantic clauses on an index with no keyword component", async () => {
    document.body.innerHTML = html.replace(/^[\s\S]*<body[^>]*>/, "").replace(/<\/body>[\s\S]*$/, "");
    const vectorOnly = { ...testIndex(), components: [
      { kind: "vector" as const, providerInstanceId: "turbo_quant" }] };
    const workbench = new ServerSearchWorkbench({
      listIndexes: () => Promise.resolve([vectorOnly]),
      search: () => Promise.resolve({ hits: [], truncated: false }),
      analyzeSource: () => Promise.reject(new Error("not exercised")),
    });
    await workbench.initialize();

    const kind = document.getElementById("builder-kind") as HTMLSelectElement;
    const disabled = Array.from(kind.options).filter((option) => option.disabled).map((option) => option.value);
    expect(disabled).toEqual(["term", "phrase"]);
    expect(kind.value).toBe("semantic");
    expect(document.getElementById("server-index-description")!.textContent)
      .toContain("no keyword component");
  });

  it("shows the chunk once when it exactly matches the original span", async () => {
    await searchOneHit(testHit(), () => new Promise<never>(() => undefined));

    const panel = document.getElementById("search-original-panel") as HTMLElement;
    expect(panel.hidden).toBe(true);
    expect(document.querySelector(".chunk-comparison")?.classList.contains("is-single")).toBe(true);
    expect(document.getElementById("chunk-comparison-status")?.textContent)
      .toContain("shown once");
    // Analysis has not returned yet, so the counters stay pending.
    expect(document.getElementById("search-entity-count")?.textContent).toBe("…");
  });

  it("shows both copies when the indexed chunk differs from the source span", async () => {
    await searchOneHit(testHit({ indexedChunkText: "alice" }),
      () => new Promise<never>(() => undefined));

    const panel = document.getElementById("search-original-panel") as HTMLElement;
    expect(panel.hidden).toBe(false);
    expect(document.querySelector(".chunk-comparison")?.classList.contains("is-single")).toBe(false);
    expect(document.getElementById("chunk-comparison-status")?.textContent)
      .toContain("differs");
  });

  it("marks the analytics counters unavailable when lazy analysis fails", async () => {
    await searchOneHit(testHit(), () => Promise.reject(new Error("analysis backend down")));

    await vi.waitFor(() => {
      expect(document.getElementById("search-entity-count")?.textContent).toBe("n/a");
    });
    expect(document.getElementById("search-sentence-count")?.textContent).toBe("n/a");
    expect(document.getElementById("search-hit-annotations")?.textContent)
      .toContain("unavailable");
  });
});

describe("server search workbench compound queries", () => {
  beforeEach(() => {
    document.body.innerHTML = "";
  });

  it("drops the query field's required constraint while clauses exist", async () => {
    await mountWorkbench();
    const query = document.getElementById("server-search-query") as HTMLInputElement;
    const kind = document.getElementById("builder-kind") as HTMLSelectElement;
    const text = document.getElementById("builder-text") as HTMLInputElement;
    const add = document.getElementById("builder-add-button") as HTMLButtonElement;

    expect(query.required).toBe(true);

    kind.value = "phrase";
    kind.dispatchEvent(new Event("change"));
    text.value = "White Rabbit";
    add.click();

    expect(query.required).toBe(false);
    const search = document.getElementById("server-search-button") as HTMLButtonElement;
    expect(search.disabled).toBe(false);

    const remove = document.querySelector<HTMLButtonElement>(
      '#builder-clauses button[aria-label="Remove clause 1"]');
    remove?.click();
    expect(query.required).toBe(true);
  });

  it("adds the drafted clause when Enter is pressed in the clause input", async () => {
    await mountWorkbench();
    const text = document.getElementById("builder-text") as HTMLInputElement;
    text.value = "habeas corpus";

    text.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true, cancelable: true }));

    expect(document.querySelector("#builder-clauses .builder-clause")?.textContent)
      .toContain("habeas corpus");
    expect(text.value).toBe("");
    const query = document.getElementById("server-search-query") as HTMLInputElement;
    expect(query.required).toBe(false);
  });

  it("uses the explicit exhaustive contract for a TurboQuant heatmap", async () => {
    document.body.innerHTML = html.replace(/^[\s\S]*<body[^>]*>/, "").replace(/<\/body>[\s\S]*$/, "");
    const index = {
      ...testIndex(),
      size: 3,
      maxTopK: 3,
      supportsAllHits: true,
      providerId: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT",
    };
    const search = vi.fn().mockResolvedValue({ hits: [], truncated: false });
    const workbench = new ServerSearchWorkbench({
      listIndexes: () => Promise.resolve([index]),
      search,
      analyzeSource: () => Promise.reject(new Error("not exercised")),
    });
    await workbench.initialize();
    document.getElementById("server-view-heatmap-button")?.click();
    const query = document.getElementById("server-search-query") as HTMLInputElement;
    query.value = "rabbit";
    query.dispatchEvent(new Event("input", { bubbles: true }));
    document.getElementById("server-search-form")
      ?.dispatchEvent(new SubmitEvent("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(search).toHaveBeenCalledWith({
      indexId: "workspace-test",
      query: { rawText: "rabbit" },
      allHits: true,
    }));
  });

  it("sends a requested fifty thousand result limit when the index permits it", async () => {
    document.body.innerHTML = html.replace(/^[\s\S]*<body[^>]*>/, "").replace(/<\/body>[\s\S]*$/, "");
    const search = vi.fn().mockResolvedValue({ hits: [], truncated: false });
    const workbench = new ServerSearchWorkbench({
      listIndexes: () => Promise.resolve([{ ...testIndex(), maxTopK: 50_000 }]),
      search,
      analyzeSource: () => Promise.reject(new Error("not exercised")),
    });
    await workbench.initialize();
    const topK = document.getElementById("server-search-top-k") as HTMLInputElement;
    expect(topK.max).toBe("50000");
    topK.value = "50000";
    const query = document.getElementById("server-search-query") as HTMLInputElement;
    query.value = "rabbit";
    query.dispatchEvent(new Event("input", { bubbles: true }));
    document.getElementById("server-search-form")
      ?.dispatchEvent(new SubmitEvent("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(search).toHaveBeenCalledWith({
      indexId: "workspace-test",
      query: { rawText: "rabbit" },
      topK: 50_000,
    }));
  });
});
