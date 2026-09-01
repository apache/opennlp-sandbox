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
  CorpusWorkflowWorkbench,
  type CorpusWorkflowApi,
} from "../src/corpus-workflow";
import type { SearchHit, SearchIndex } from "../src/search-adapter";
import type { TrainedModelSummary } from "../src/vocabulary-trainer";

const MODEL: TrainedModelSummary = {
  artifactId: "static-model-1",
  displayName: "Workflow model",
  dimension: 128,
  termCount: 12,
  teacherId: "mini",
  family: "wordpiece",
  vocabularySize: 30_522,
  explainedVarianceRatio: 0.94,
  artifactHash: "model-hash",
  byteSize: 12_000,
  createdAt: "2026-08-26T12:00:00Z",
  teacherReference: "minishlab/potion-base-8M",
  licenseName: "MIT",
  languages: ["en"],
};

const INDEX: SearchIndex = {
  id: "index-1",
  label: "Demo workflow",
  providerId: "flat-float",
  modelId: MODEL.artifactId,
  backendId: "trained-static",
  vectorSpaceId: "space-1",
  metric: "SEARCH_METRIC_COSINE",
  size: 2,
  supportsAllHits: true,
  immutable: false,
  persisted: false,
  corpusTitle: "Demo workflow",
  provenance: "Pasted workflow corpus",
  build: {},
};

function response(docId: string, rawText: string, embedded: boolean): Record<string, unknown> {
  return {
    document: {
      docId,
      rawText,
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      layers: {
        layers: [{
          layerId: { standard: "STANDARD_LAYER_SENTENCES" },
          scope: "ANNOTATION_SCOPE_SPAN",
          stringValues: [{ span: { start: 0, end: rawText.length }, value: rawText }],
        }],
      },
      ...(embedded ? {
        chunkEmbeddingGroups: [{
          groupId: "workflow-sentences",
          resultSetName: "Workflow sentence chunks",
          strategy: { standard: "STANDARD_CHUNKING_STRATEGY_SENTENCE" },
          embeddingModelIds: [MODEL.artifactId],
          chunks: [{
            annotationSpan: { start: 0, end: rawText.length },
            textContent: rawText,
            embeddings: [{ modelId: MODEL.artifactId, vector: [0.2, 0.4] }],
          }],
        }],
      } : {}),
    },
  };
}

function hit(documentId: string, sourceText: string, score: number): SearchHit {
  return {
    id: `hit-${documentId}`,
    documentId,
    chunkId: `chunk-${documentId}`,
    chunkGroupId: "workflow-sentences",
    score,
    sourceDocument: response(documentId, sourceText, true).document as Record<string, unknown>,
    sourceText,
    start: 0,
    end: sourceText.length,
    offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
    indexedChunkText: sourceText,
    modelId: MODEL.artifactId,
    backendId: "trained-static",
    vectorSpaceId: "space-1",
    providerId: "flat-float",
    indexId: INDEX.id,
    corpusTitle: INDEX.corpusTitle,
    provenance: INDEX.provenance,
    build: {},
    matchedSpans: [],
  };
}

describe("corpus workflow", () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <p id="workflow-status"></p>
      <textarea id="workflow-corpus"></textarea>
      <p id="workflow-corpus-stats"></p>
      <input id="workflow-name" value="Demo workflow" />
      <select id="workflow-dictionary-select"></select>
      <select id="workflow-teacher-select"></select>
      <select id="workflow-provider-select"></select>
      <input id="workflow-min-frequency" value="1" />
      <input id="workflow-max-terms" value="10000" />
      <input id="workflow-pca-dims" value="0" />
      <input id="workflow-query" value="rights and liberty" />
      <button id="workflow-run-button"></button>
      <form id="workflow-search-form"><button id="workflow-search-button"></button></form>
      <span id="workflow-mode-badge"></span>
      <span id="workflow-option-summary"></span>
      <ol id="workflow-stages">
        ${["analyze", "vocabulary", "train", "embed", "index", "search"].map((stage) =>
          `<li data-workflow-stage="${stage}"><span class="workflow-stage-status"></span></li>`).join("")}
      </ol>
      <div id="workflow-artifacts"></div>
      <div role="tablist">
        <button data-workflow-result-tab="analysis"></button>
        <button data-workflow-result-tab="search"></button>
      </div>
      <section id="workflow-analysis-panel"><div id="workflow-analysis-results"></div></section>
      <section id="workflow-search-panel" hidden>
        <div id="workflow-search-heatmap"></div><p id="workflow-search-selection"></p>
      </section>`;
  });

  function api(order: string[]): CorpusWorkflowApi {
    let analyzeCount = 0;
    return {
      listDictionaries: vi.fn(async () => [{
        artifactId: "dictionary-large", displayName: "Large English dictionary", entryCount: 80_000,
      }]),
      listTeachers: vi.fn(async () => ({
        teachers: [{ id: "mini", label: "Mini teacher", reference: "teacher.onnx" }],
        writesEnabled: true,
      })),
      listProviders: vi.fn(async () => [{
        instanceId: "flat-float", providerId: "flat-float", capabilities: ["vector", "live"],
        standard: "STANDARD_SEARCH_PROVIDER_FLAT_FLOAT",
      }]),
      analyze: vi.fn(async (request) => {
        order.push(analyzeCount < 2 ? "analyze" : "embed");
        analyzeCount++;
        return response(request.document.docId ?? "missing", request.document.rawText,
          Boolean(request.chunkEmbedConfigs?.length));
      }),
      learnVocabulary: vi.fn(async () => {
        order.push("vocabulary");
        return {
          artifactId: "vocabulary-1", displayName: "Demo vocabulary", termCount: 12,
          dictionaryTermCount: 8, corpusTermCount: 4,
        };
      }),
      trainStaticModel: vi.fn(async (_request, onProgress) => {
        order.push("train");
        onProgress("Distilling vocabulary terms");
        return MODEL;
      }),
      index: vi.fn(async () => {
        order.push("index");
        return INDEX;
      }),
      search: vi.fn(async () => {
        order.push("search");
        return {
          hits: [
            hit("workflow-doc-1", "Liberty protects people.", 0.82),
            hit("workflow-doc-2", "Courts preserve rights.", 0.61),
          ],
          truncated: false,
        };
      }),
    };
  }

  function callbacks(defaultModel?: { id: string; label: string }) {
    return {
      createAnalysisRequest: (document: { docId: string; rawText: string }, modelId?: string) => ({
        document,
        ...(modelId ? {
          chunkEmbedConfigs: [{
            configId: "workflow-sentences",
            resultSetName: "Workflow sentence chunks",
            chunking: {
              strategy: { standard: "STANDARD_CHUNKING_STRATEGY_SENTENCE" },
              cleanText: true,
              preserveUrls: true,
            },
            embeddingModelIds: [modelId],
          }],
        } : {}),
      }),
      onModelTrained: vi.fn(),
      onOpenAnalysis: vi.fn(),
      onIndexChanged: vi.fn(),
      defaultEmbeddingModel: () => defaultModel,
    };
  }

  it("indexes a large corpus in batches the server accepts, extending the index it created", async () => {
    const order: string[] = [];
    const workflowApi = api(order);
    workflowApi.analyze = vi.fn(async (request) => response(
      request.document.docId ?? "missing", request.document.rawText,
      Boolean(request.chunkEmbedConfigs?.length)));
    const workflow = new CorpusWorkflowWorkbench(workflowApi, callbacks());
    await workflow.initialize();
    const corpus = Array.from({ length: 20 }, (_, i) => `Document ${i + 1} about liberty.`).join("\n\n");
    (document.getElementById("workflow-corpus") as HTMLTextAreaElement).value = corpus;
    (document.getElementById("workflow-corpus") as HTMLTextAreaElement)
      .dispatchEvent(new Event("input"));
    (document.getElementById("workflow-run-button") as HTMLButtonElement).click();

    await vi.waitFor(() => expect(document.getElementById("workflow-status")?.textContent)
      .toContain("is built and searchable"));
    const calls = vi.mocked(workflowApi.index).mock.calls.map((call) => call[0]);
    expect(calls).toHaveLength(2);
    expect(calls[0]?.indexId).toBeUndefined();
    expect(calls[0]?.documents).toHaveLength(16);
    expect(calls[1]?.indexId).toBe("index-1");
    expect(calls[1]?.documents).toHaveLength(4);
    expect(calls[1]?.displayName).toBe(calls[0]?.displayName);
    expect(document.querySelectorAll(".workflow-analysis-card")).toHaveLength(20);
  });

  it("rejects a corpus larger than one index accepts before doing any work", async () => {
    const order: string[] = [];
    const workflowApi = api(order);
    const workflow = new CorpusWorkflowWorkbench(workflowApi, callbacks());
    await workflow.initialize();
    const corpus = Array.from({ length: 257 }, (_, i) => `Document ${i + 1}.`).join("\n\n");
    (document.getElementById("workflow-corpus") as HTMLTextAreaElement).value = corpus;
    (document.getElementById("workflow-corpus") as HTMLTextAreaElement)
      .dispatchEvent(new Event("input"));
    (document.getElementById("workflow-run-button") as HTMLButtonElement).click();

    await vi.waitFor(() => expect(document.getElementById("workflow-status")?.textContent)
      .toContain("256"));
    expect(document.getElementById("workflow-status")?.classList.contains("is-error")).toBe(true);
    expect(order).toEqual([]);
  });

  it("re-evaluates the build mode once an embedding model is discovered after initialization", async () => {
    const order: string[] = [];
    const workflowApi = api(order);
    workflowApi.listTeachers = vi.fn(async () => ({ teachers: [], writesEnabled: true }));
    let discovered: { id: string; label: string } | undefined;
    const flow = new CorpusWorkflowWorkbench(workflowApi, {
      ...callbacks(),
      defaultEmbeddingModel: () => discovered,
    });
    await flow.initialize();
    expect(flow.mode()).toBe("unavailable");

    discovered = { id: "potion", label: "potion (256d, static)" };
    flow.refreshMode();
    expect(flow.mode()).toBe("index-only");
    expect(document.getElementById("workflow-status")?.textContent)
      .toContain("Ready in analyze-and-index mode");
  });

  it("says which of the two prerequisites is missing and where to get a teacher", async () => {
    const order: string[] = [];
    const workflowApi = api(order);
    workflowApi.listTeachers = vi.fn(async () => ({ teachers: [], writesEnabled: false }));
    const workflow = new CorpusWorkflowWorkbench(workflowApi, callbacks());
    await workflow.initialize();

    expect(workflow.mode()).toBe("unavailable");
    const status = document.getElementById("workflow-status")!;
    expect(status.textContent).toContain("no teacher model is installed");
    expect(status.textContent).toContain("no embedding model is loaded");
    expect(status.classList.contains("is-error")).toBe(true);
    expect(status.querySelector<HTMLElement>("[data-workbench-jump]")?.dataset.workbenchJump).toBe("models");
    expect(document.getElementById("workflow-mode-badge")!.textContent).toBe("Unavailable on this server");
    const run = document.getElementById("workflow-run-button") as HTMLButtonElement;
    (document.getElementById("workflow-corpus") as HTMLTextAreaElement).value = "One document.";
    document.getElementById("workflow-corpus")!.dispatchEvent(new Event("input"));
    expect(run.disabled).toBe(true);
    expect(run.title).toContain("Nothing can be built");
  });

  it("analyzes, indexes and searches with an installed model when no teacher exists", async () => {
    const order: string[] = [];
    const workflowApi = api(order);
    workflowApi.listTeachers = vi.fn(async () => ({ teachers: [], writesEnabled: true }));
    const workflow = new CorpusWorkflowWorkbench(workflowApi,
      callbacks({ id: "minilm", label: "MiniLM (catalog)" }));
    await workflow.initialize();
    expect(workflow.mode()).toBe("index-only");
    expect(document.getElementById("workflow-status")!.textContent)
      .toContain("embedded with 'MiniLM (catalog)'");
    expect(document.getElementById("workflow-option-summary")!.textContent)
      .toContain("distillation is skipped");

    (document.getElementById("workflow-corpus") as HTMLTextAreaElement).value =
      "Liberty is precious.\n\nRights are protected.";
    document.getElementById("workflow-corpus")!.dispatchEvent(new Event("input"));
    document.getElementById("workflow-run-button")!.click();
    await vi.waitFor(() => expect(document.getElementById("workflow-status")!.textContent)
      .toContain("is built and searchable"));

    expect(workflowApi.learnVocabulary).not.toHaveBeenCalled();
    expect(workflowApi.trainStaticModel).not.toHaveBeenCalled();
    expect(workflowApi.index).toHaveBeenCalledWith(
      expect.objectContaining({ embedding: { modelId: "minilm" } }));
    const stage = (name: string) => document.querySelector<HTMLElement>(
      `[data-workflow-stage="${name}"]`)!.dataset.state;
    expect([stage("analyze"), stage("vocabulary"), stage("train"), stage("embed"), stage("index"), stage("search")])
      .toEqual(["complete", "skipped", "skipped", "complete", "complete", "complete"]);
  });

  it("keeps completed stages when a later stage fails", async () => {
    const order: string[] = [];
    const workflowApi = api(order);
    workflowApi.index = vi.fn(async () => {
      throw new Error("Search provider instance 'terms' cannot hold vectors");
    });
    const workflow = new CorpusWorkflowWorkbench(workflowApi, callbacks());
    await workflow.initialize();
    (document.getElementById("workflow-corpus") as HTMLTextAreaElement).value = "One document.";
    document.getElementById("workflow-corpus")!.dispatchEvent(new Event("input"));
    document.getElementById("workflow-run-button")!.click();
    await vi.waitFor(() => expect(document.getElementById("workflow-status")!.textContent)
      .toContain("cannot hold vectors"));

    const stage = (name: string) => document.querySelector<HTMLElement>(
      `[data-workflow-stage="${name}"]`)!.dataset.state;
    expect([stage("analyze"), stage("vocabulary"), stage("train"), stage("embed"), stage("index")])
      .toEqual(["complete", "complete", "complete", "complete", "error"]);
    expect(document.getElementById("workflow-status")!.classList.contains("is-error")).toBe(true);
  });

  it("runs the guided corpus-to-search pipeline and renders both result views", async () => {
    const order: string[] = [];
    const workflowApi = api(order);
    const onModelTrained = vi.fn();
    const onOpenAnalysis = vi.fn();
    const workflow = new CorpusWorkflowWorkbench(workflowApi, {
      createAnalysisRequest: (document, modelId) => ({
        document,
        ...(modelId ? {
          chunkEmbedConfigs: [{
            configId: "workflow-sentences",
            resultSetName: "Workflow sentence chunks",
            chunking: {
              strategy: { standard: "STANDARD_CHUNKING_STRATEGY_SENTENCE" },
              cleanText: true,
              preserveUrls: true,
            },
            embeddingModelIds: [modelId],
          }],
        } : {}),
      }),
      onModelTrained,
      onOpenAnalysis,
      onIndexChanged: vi.fn(),
      defaultEmbeddingModel: () => undefined,
    });
    await workflow.initialize();
    (document.getElementById("workflow-corpus") as HTMLTextAreaElement).value =
      "Liberty protects people.\n\nCourts preserve rights.";
    (document.getElementById("workflow-corpus") as HTMLTextAreaElement)
      .dispatchEvent(new Event("input"));
    const dictionary = document.getElementById("workflow-dictionary-select") as HTMLSelectElement;
    dictionary.value = "dictionary-large";

    (document.getElementById("workflow-run-button") as HTMLButtonElement).click();

    await vi.waitFor(() => expect(document.getElementById("workflow-status")?.textContent)
      .toContain("is built and searchable"));
    expect(order).toEqual([
      "analyze", "analyze", "vocabulary", "train", "embed", "embed", "index", "search",
    ]);
    const upload = vi.mocked(workflowApi.learnVocabulary).mock.calls[0]?.[0];
    expect(upload?.start.dictionaryArtifactId).toBe("dictionary-large");
    expect(upload?.documents).toHaveLength(2);
    expect(vi.mocked(workflowApi.index).mock.calls[0]?.[0].documents).toHaveLength(2);
    expect(onModelTrained).toHaveBeenCalledWith(MODEL);
    expect(document.querySelectorAll('[data-workflow-stage][data-state="complete"]')).toHaveLength(6);
    expect(document.querySelectorAll(".workflow-analysis-card")).toHaveLength(2);
    expect(document.querySelectorAll(".heat-document")).toHaveLength(2);
    expect(document.getElementById("workflow-artifacts")?.textContent).toContain("vocabulary-1");
    expect(document.getElementById("workflow-artifacts")?.textContent).toContain("index-1");

    (document.querySelector(".workflow-analysis-card button") as HTMLButtonElement).click();
    expect(onOpenAnalysis).toHaveBeenCalledOnce();
  });

  it("uses corpus-only vocabulary and can search the built index again without retraining", async () => {
    const order: string[] = [];
    const workflowApi = api(order);
    const workflow = new CorpusWorkflowWorkbench(workflowApi, {
      createAnalysisRequest: (document, modelId) => ({
        document,
        ...(modelId ? {
          chunkEmbedConfigs: [{
            configId: "workflow-sentences",
            resultSetName: "Workflow sentence chunks",
            chunking: {
              strategy: { standard: "STANDARD_CHUNKING_STRATEGY_SENTENCE" },
              cleanText: true,
              preserveUrls: true,
            },
            embeddingModelIds: [modelId],
          }],
        } : {}),
      }),
      onModelTrained: vi.fn(), onOpenAnalysis: vi.fn(), onIndexChanged: vi.fn(),
      defaultEmbeddingModel: () => undefined,
    });
    await workflow.initialize();
    const corpus = document.getElementById("workflow-corpus") as HTMLTextAreaElement;
    corpus.value = "One document.";
    corpus.dispatchEvent(new Event("input"));
    (document.getElementById("workflow-run-button") as HTMLButtonElement).click();
    await vi.waitFor(() => expect(workflowApi.index).toHaveBeenCalledOnce());

    const upload = vi.mocked(workflowApi.learnVocabulary).mock.calls[0]?.[0];
    expect(upload?.start.dictionaryArtifactId).toBeUndefined();
    (document.getElementById("workflow-query") as HTMLInputElement).value = "second query";
    document.getElementById("workflow-search-form")?.dispatchEvent(
      new SubmitEvent("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(workflowApi.search).toHaveBeenCalledTimes(2));
    expect(workflowApi.trainStaticModel).toHaveBeenCalledOnce();
    expect(workflowApi.index).toHaveBeenCalledOnce();
  });
});
