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

import {
  ModelDataWorkbench,
  readInstalledModels,
  readModelCatalog,
  type ModelCatalogApi,
} from "../src/model-data-workbench";

const STATIC_MODEL = {
  catalogId: "potion-base-8m",
  displayName: "Potion Base 8M",
  role: "static" as const,
  modelId: "potion-base-8m",
  sourceUri: "https://huggingface.co/minishlab/potion-base-8M",
  revision: "revision-1",
  licenseName: "MIT",
  licenseUri: "https://opensource.org/license/mit",
  byteSize: 30_236_760,
  dimension: 256,
  languages: ["en"],
  description: "Ready-to-serve static embeddings.",
};

describe("model catalog readers", () => {
  it("reads every first-class catalog artifact role", () => {
    const result = readModelCatalog({ installsEnabled: true, models: [
      {
        catalogId: "teacher", displayName: "MiniLM", modelId: "mini",
        role: "MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER", byteSize: "10",
        sourceUri: "https://example.test/teacher", revision: "teacher-revision",
        licenseName: "Apache-2.0", licenseUri: "https://www.apache.org/licenses/LICENSE-2.0",
      },
      {
        catalogId: "static", displayName: "Potion", modelId: "potion",
        role: "MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING", byteSize: "20", dimension: 256,
        sourceUri: "https://example.test/static", revision: "static-revision",
        licenseName: "MIT", licenseUri: "https://opensource.org/license/mit",
      },
      {
        catalogId: "parser", displayName: "GUM parser", modelId: "gum",
        role: "MODEL_ARTIFACT_ROLE_PARSER", byteSize: "30",
        sourceUri: "https://example.test/parser", revision: "parser-revision",
        licenseName: "CC-BY-4.0", licenseUri: "https://example.test/parser-license",
      },
      {
        catalogId: "chunker", displayName: "GUM chunker", modelId: "gum",
        role: "MODEL_ARTIFACT_ROLE_CHUNKER", byteSize: "40",
        sourceUri: "https://example.test/chunker", revision: "chunker-revision",
        licenseName: "CC-BY-4.0", licenseUri: "https://example.test/chunker-license",
      },
    ] });

    expect(result.installsEnabled).toBe(true);
    expect(result.models.map((model) => model.role))
      .toEqual(["teacher", "static", "parser", "chunker"]);
    expect(result.models[1]?.dimension).toBe(256);
    expect(readInstalledModels({ models: [{
      catalog: { catalogId: "static" }, artifactHash: "abc", byteSize: "20", loaded: true,
      installedAt: "2026-08-21T20:00:00Z",
    }] })).toEqual([{ catalogId: "static", artifactHash: "abc", byteSize: 20,
      installedAt: "2026-08-21T20:00:00Z", loaded: true }]);
  });

  it("rejects catalog cards that cannot safely support informed consent", () => {
    expect(() => readModelCatalog({ models: [{
      catalogId: "unsafe", modelId: "unsafe",
      role: "MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING",
      sourceUri: "javascript:alert(1)", revision: "r", licenseName: "MIT",
      licenseUri: "https://opensource.org/license/mit",
    }] })).toThrow(/HTTPS/);
    expect(() => readModelCatalog({ models: [{
      catalogId: "moving", modelId: "moving",
      role: "MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING",
      sourceUri: "https://example.test/model", licenseName: "MIT",
      licenseUri: "https://opensource.org/license/mit",
    }] })).toThrow(/revision/);
  });
});

describe("model catalog workbench", () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <strong id="resource-summary"></strong>
      <div id="resource-feature-list"></div>
      <ul id="resource-bundle-list"></ul>
      <pre id="resource-install-command"></pre>
      <button id="copy-resource-command"></button>
      <p id="resource-install-status"></p>
      <div id="resource-model-catalog"></div>
      <div id="resource-installed-models"></div>`;
  });

  function api(): ModelCatalogApi {
    return {
      listCatalog: vi.fn(async () => ({ models: [STATIC_MODEL], installsEnabled: true })),
      listInstalled: vi.fn(async () => []),
      install: vi.fn(async (_request, onProgress) => {
        onProgress({ stage: "downloading", message: "Downloading config.json",
          completedBytes: 10, totalBytes: STATIC_MODEL.byteSize });
        return { catalogId: STATIC_MODEL.catalogId, artifactHash: "abc",
          byteSize: STATIC_MODEL.byteSize, installedAt: "now", loaded: true };
      }),
    };
  }

  it("requires license acknowledgement before installing and activates static models", async () => {
    const service = api();
    const installed = vi.fn();
    const workbench = new ModelDataWorkbench(service, {
      onEmbeddingModelInstalled: installed,
      onTeacherInstalled: vi.fn(),
    });
    await workbench.initialize();

    const button = document.querySelector<HTMLButtonElement>("[data-catalog-install]")!;
    const consent = document.querySelector<HTMLInputElement>("[data-catalog-consent]")!;
    expect(button.disabled).toBe(true);
    expect(document.getElementById("resource-model-catalog")?.textContent)
      .toContain("Ready-to-serve static embeddings");
    expect(Array.from(document.querySelectorAll<HTMLAnchorElement>(".catalog-model-card a"))
      .map((link) => link.href)).toContain(STATIC_MODEL.licenseUri);

    consent.click();
    expect(button.disabled).toBe(false);
    button.click();
    await vi.waitFor(() => expect(service.install).toHaveBeenCalled());

    expect(service.install).toHaveBeenCalledWith({
      catalogId: STATIC_MODEL.catalogId,
      revision: STATIC_MODEL.revision,
      licenseName: STATIC_MODEL.licenseName,
      licenseAcknowledged: true,
    }, expect.any(Function));
    expect(installed).toHaveBeenCalledWith(STATIC_MODEL.modelId, STATIC_MODEL.displayName);
    expect(document.getElementById("resource-install-status")?.textContent)
      .toContain("installed and active");
  });

  it("publishes static models restored from the node inventory", async () => {
    const service = api();
    service.listInstalled = vi.fn(async () => [{
      catalogId: STATIC_MODEL.catalogId,
      artifactHash: "abc",
      byteSize: STATIC_MODEL.byteSize,
      installedAt: "now",
      loaded: true,
    }]);
    const installed = vi.fn();
    const workbench = new ModelDataWorkbench(service, {
      onEmbeddingModelInstalled: installed,
      onTeacherInstalled: vi.fn(),
    });

    await workbench.initialize();

    expect(installed).toHaveBeenCalledWith(STATIC_MODEL.modelId, STATIC_MODEL.displayName);
  });

  it("explains that a newly installed parser needs a server restart", async () => {
    const parser = {
      ...STATIC_MODEL,
      catalogId: "gum-parser",
      displayName: "GUM parser",
      role: "parser" as const,
      modelId: "gum",
      dimension: 0,
      licenseName: "CC-BY-4.0",
    };
    const service = api();
    service.listCatalog = vi.fn(async () => ({ models: [parser], installsEnabled: true }));
    service.install = vi.fn(async () => ({
      catalogId: parser.catalogId,
      artifactHash: "abc",
      byteSize: parser.byteSize,
      installedAt: "now",
      loaded: false,
    }));
    const workbench = new ModelDataWorkbench(service, {
      onEmbeddingModelInstalled: vi.fn(),
      onTeacherInstalled: vi.fn(),
    });
    await workbench.initialize();

    document.querySelector<HTMLInputElement>("[data-catalog-consent]")!.click();
    document.querySelector<HTMLButtonElement>("[data-catalog-install]")!.click();
    await vi.waitFor(() => expect(service.install).toHaveBeenCalled());

    expect(document.getElementById("resource-install-status")?.textContent)
      .toContain("restart required");
  });

  it("renders the verified downloaded-model inventory", async () => {
    const service = api();
    service.listInstalled = vi.fn(async () => [{
      catalogId: STATIC_MODEL.catalogId,
      artifactHash: "a4b3ea50e20ed3fac7c841c2953b8596b00321125ec085e81bbe1a6e737642a7",
      byteSize: STATIC_MODEL.byteSize,
      installedAt: "2026-08-21T20:00:00Z",
      loaded: true,
    }]);
    const workbench = new ModelDataWorkbench(service, {
      onEmbeddingModelInstalled: vi.fn(),
      onTeacherInstalled: vi.fn(),
    });

    await workbench.initialize();

    const inventory = document.getElementById("resource-installed-models")!;
    expect(inventory.textContent).toContain(STATIC_MODEL.displayName);
    expect(inventory.textContent).toContain("Installed and active");
    expect(inventory.textContent).toContain("28.8 MiB");
    expect(inventory.textContent).toContain("2026-08-21T20:00:00Z");
    expect(inventory.textContent).toContain("a4b3ea50e20ed3f");
  });
});
