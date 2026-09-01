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
import { describe, expect, it, vi } from "vitest";

import type { CollectionView } from "../src/collection-adapter";
import { LifecycleWorkbench, type LifecycleApi } from "../src/lifecycle-workbench";
import type { SearchIndex } from "../src/search-adapter";

const html = readFileSync(join(import.meta.dirname, "..", "index.html"), "utf8");

function index(id: string, immutable: boolean): SearchIndex {
  return {
    id, label: `Index ${id}`, providerId: "flat_float", modelId: "m", backendId: "static",
    vectorSpaceId: "s", metric: "SEARCH_METRIC_COSINE", supportsAllHits: true, immutable,
    persisted: immutable,
    corpusTitle: id, provenance: "test", build: {}, size: 3,
  };
}

function collection(vocabularyArtifactId?: string): CollectionView {
  return {
    id: "legal", displayName: "Legal", memberIndexIds: ["a"], driftNewTermThreshold: 0,
    analysisChainId: "chain", termStatistics: [], omittedTermCount: 0,
    drift: { distinctTerms: 10, termOccurrences: 100, newTerms: 4, newTermOccurrences: 40,
      vocabularyCoverage: 0.6 },
    ...(vocabularyArtifactId ? { vocabularyArtifactId } : {}),
  };
}

function api(overrides: Partial<LifecycleApi> = {}): LifecycleApi {
  return {
    listIndexes: vi.fn(async () => []),
    listProviders: vi.fn(async () => ({
      providers: [], dynamicIndexingEnabled: true, persistenceConfigured: true })),
    listAliases: vi.fn(async () => []),
    persist: vi.fn(async () => undefined),
    seal: vi.fn(async () => index("a", true)),
    reindex: vi.fn(async () => undefined),
    setAlias: vi.fn(async () => undefined),
    deleteAlias: vi.fn(async () => undefined),
    listStaticModels: vi.fn(async () => []),
    listDictionaries: vi.fn(async () => []),
    listVocabularies: vi.fn(async () => []),
    listCollections: vi.fn(async () => []),
    getCollection: vi.fn(async () => undefined),
    setCollection: vi.fn(async () => undefined),
    deleteCollection: vi.fn(async () => true),
    // A watch that ends makes the workbench reconnect at once; a stub that never ends keeps
    // the tests from spinning on that loop.
    watchCollection: vi.fn(() => new Promise<void>(() => undefined)),
    ...overrides,
  };
}

function mount(): void {
  document.body.innerHTML = html.replace(/^[\s\S]*<body[^>]*>/, "").replace(/<\/body>[\s\S]*$/, "");
}

describe("lifecycle workbench", () => {
  it("shows where a first live index comes from when the server has none", async () => {
    mount();
    const workbench = new LifecycleWorkbench(api());
    await workbench.initialize();

    const status = document.getElementById("lifecycle-status")!;
    expect(status.textContent).toContain("No live indexes yet");
    expect(Array.from(status.querySelectorAll<HTMLElement>("[data-workbench-jump]"))
      .map((jump) => jump.dataset.workbenchJump)).toEqual(["workflows", "session-search"]);
    expect((document.getElementById("lifecycle-persist-button") as HTMLButtonElement).disabled).toBe(true);
  });

  it("keeps a read-only index listed and refuses to save it again", async () => {
    mount();
    const workbench = new LifecycleWorkbench(api({
      listIndexes: vi.fn(async () => [index("a", false), index("b", true)]),
    }));
    await workbench.initialize();

    const picker = document.getElementById("lifecycle-index-select") as HTMLSelectElement;
    expect(Array.from(picker.options).map((option) => option.text))
      .toEqual(["Index a (a) · In memory", "Index b (b) · Read-only"]);
    picker.value = "b";
    picker.dispatchEvent(new Event("change"));
    const seal = document.getElementById("lifecycle-seal-button") as HTMLButtonElement;
    expect(seal.disabled).toBe(true);
    expect(seal.title).toContain("already read-only");
    expect(document.getElementById("lifecycle-index-facts")!.textContent).toContain("Read-only");
  });

  it("confirms a read-only save with a jump to where the index is searched", async () => {
    mount();
    const service = api({ listIndexes: vi.fn(async () => [index("a", false)]) });
    const workbench = new LifecycleWorkbench(service);
    await workbench.initialize();
    const picker = document.getElementById("lifecycle-index-select") as HTMLSelectElement;
    picker.value = "a";
    picker.dispatchEvent(new Event("change"));

    document.getElementById("lifecycle-seal-button")!.click();
    await vi.waitFor(() => expect(service.seal).toHaveBeenCalledWith("a"));

    const status = document.getElementById("lifecycle-workspace-status")!;
    await vi.waitFor(() => expect(status.textContent).toContain("read-only and saved it to disk"));
    expect(status.querySelector<HTMLElement>("[data-workbench-jump]")?.dataset.workbenchJump)
      .toBe("session-search");
  });

  it("says coverage is not measured without a vocabulary and points at the Trainer", async () => {
    mount();
    const service = api({
      listIndexes: vi.fn(async () => [index("a", false)]),
      listCollections: vi.fn(async () => [collection()]),
      getCollection: vi.fn(async () => collection()),
    });
    const workbench = new LifecycleWorkbench(service);
    await workbench.initialize();
    const select = document.getElementById("collection-select") as HTMLSelectElement;
    select.value = "legal";
    select.dispatchEvent(new Event("change"));
    await vi.waitFor(() => expect(service.getCollection).toHaveBeenCalledWith("legal"));

    const label = document.getElementById("collection-coverage-label")!;
    await vi.waitFor(() => expect(label.textContent).toContain("Not measured"));
    expect(label.querySelector<HTMLElement>("[data-workbench-jump]")?.dataset.workbenchJump).toBe("trainer");
    expect(document.getElementById("collection-coverage-bar")!.parentElement!.classList.contains("is-unmeasured")).toBe(true);
  });

  it("points a save that names an unknown vocabulary at the Trainer", async () => {
    mount();
    const service = api({
      listIndexes: vi.fn(async () => [index("a", false)]),
      // Listed when the tab loaded, deleted on the server before the save.
      listVocabularies: vi.fn(async () => [
        { artifactId: "vocabulary-stale", displayName: "Stale vocabulary", termCount: 3 },
      ]),
      setCollection: vi.fn(async () => {
        throw new Error("Unknown vocabulary artifact 'vocabulary-stale'");
      }),
    });
    const workbench = new LifecycleWorkbench(service);
    await workbench.initialize();
    (document.getElementById("collection-id") as HTMLInputElement).value = "legal";
    (document.getElementById("collection-name") as HTMLInputElement).value = "Legal";
    (document.getElementById("collection-vocabulary-id") as HTMLSelectElement).value = "vocabulary-stale";

    document.getElementById("collection-save-button")!.click();
    await vi.waitFor(() => expect(service.setCollection).toHaveBeenCalled());

    const status = document.getElementById("collection-status")!;
    await vi.waitFor(() => expect(status.textContent).toContain("Unknown vocabulary artifact"));
    expect(status.querySelector<HTMLElement>("[data-workbench-jump]")?.dataset.workbenchJump).toBe("trainer");
  });
  it("fills the artifact pickers from the server and keeps a saved id it no longer lists", async () => {
    mount();
    const workbench = new LifecycleWorkbench(api({
      listDictionaries: vi.fn(async () => [
        { artifactId: "dictionary-legal", displayName: "Legal dictionary", entryCount: 80 },
      ]),
      listVocabularies: vi.fn(async () => [
        { artifactId: "vocabulary-legal", displayName: "Legal vocabulary", termCount: 4812 },
      ]),
      listCollections: vi.fn(async () => [collection("vocabulary-gone")]),
      getCollection: vi.fn(async () => collection("vocabulary-gone")),
    }));
    await workbench.initialize();

    const vocabularies = document.getElementById("collection-vocabulary-id") as HTMLSelectElement;
    expect(Array.from(vocabularies.options).map((option) => option.textContent))
      .toEqual(["No vocabulary (coverage not measured)", "Legal vocabulary (4812 terms) · legal"]);
    const dictionaries = document.getElementById("collection-dictionary-id") as HTMLSelectElement;
    expect(Array.from(dictionaries.options).map((option) => option.value))
      .toEqual(["", "dictionary-legal"]);

    const picker = document.getElementById("collection-select") as HTMLSelectElement;
    picker.value = "legal";
    picker.dispatchEvent(new Event("change"));
    await vi.waitFor(() => expect(vocabularies.value).toBe("vocabulary-gone"));
    expect(vocabularies.selectedOptions[0]?.textContent).toBe("vocabulary-gone (not on this server)");
  });
});
