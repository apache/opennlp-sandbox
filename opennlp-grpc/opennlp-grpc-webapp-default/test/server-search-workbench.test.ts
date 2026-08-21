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

import type { SearchIndex } from "../src/search-adapter";
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
});
