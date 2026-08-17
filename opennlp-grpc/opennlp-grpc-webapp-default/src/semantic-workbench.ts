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

import type { ChartHandle } from "./charts";
import type { IndexDocumentsRequest } from "./api";
import type { DocumentShapeView } from "./document-shape";
import type { SearchHit, SearchIndex, SearchRequest, SearchResponse } from "./search-adapter";
import { collapseWhitespace, ellipsizeCodePoints } from "./text-utils";
import { emptyMessage, requiredElement } from "./ui-utils";
import {
  buildDocumentGraph,
  buildHeatmapRows,
  type DocumentGraph,
  type DocumentGraphNode,
  type HeatmapRows,
} from "./visualization-data";

export type ResultViewName = "document" | "chunks" | "heatmap" | "graph" | "json";

export interface SemanticWorkbenchOptions {
  index(request: IndexDocumentsRequest): Promise<SearchIndex>;
  search(request: SearchRequest): Promise<SearchResponse>;
  deleteIndex(indexId: string): Promise<void>;
  openDocument(hit: SearchHit): void;
  selectAnnotation(layerId: string, annotationIndex: number): void;
}

interface CurrentDocument {
  title: string;
  shape: DocumentShapeView;
  wireDocument?: Record<string, unknown>;
  modelId?: string;
  groupIds: string[];
}

/** Coordinates server-owned, in-memory workspace indexing and query rendering. */
export class SemanticWorkbench {
  readonly #options: SemanticWorkbenchOptions;
  readonly #addButton = requiredElement<HTMLButtonElement>("add-to-index-button");
  readonly #clearButton = requiredElement<HTMLButtonElement>("clear-index-button");
  readonly #searchForm = requiredElement<HTMLFormElement>("semantic-search-form");
  readonly #query = requiredElement<HTMLTextAreaElement>("semantic-query");
  readonly #searchButton = requiredElement<HTMLButtonElement>("search-button");
  readonly #indexCount = requiredElement<HTMLElement>("index-count");
  readonly #status = requiredElement<HTMLElement>("semantic-status");
  readonly #results = requiredElement<HTMLElement>("search-results");
  readonly #semanticHeatmap = requiredElement<HTMLElement>("semantic-heatmap");
  readonly #sentimentHeatmap = requiredElement<HTMLElement>("sentiment-heatmap");
  readonly #graphCanvas = requiredElement<HTMLElement>("document-graph");
  readonly #graphSelection = requiredElement<HTMLElement>("graph-selection");

  #current?: CurrentDocument;
  #workspace?: SearchIndex;
  #heatmaps: HeatmapRows = { semantic: [], sentiment: [] };
  #graph?: DocumentGraph;
  #semanticChart?: ChartHandle;
  #sentimentChart?: ChartHandle;
  #graphChart?: ChartHandle;
  #busy = false;
  #nextDocumentId = 1;
  #activeView: ResultViewName = "document";

  constructor(options: SemanticWorkbenchOptions) {
    this.#options = options;
    this.#addButton.addEventListener("click", () => void this.addCurrentDocument());
    this.#clearButton.addEventListener("click", () => void this.clear());
    this.#searchForm.addEventListener("submit", (event) => void this.search(event));
    this.#query.addEventListener("input", () => this.updateControls());
    window.addEventListener("resize", () => this.resize());
    this.updateControls();
  }

  setDocument(title: string, shape: DocumentShapeView, json = ""): void {
    const indexed = indexableDocument(json);
    this.#current = {
      title: title.trim(),
      shape,
      wireDocument: indexed?.document,
      modelId: indexed?.modelId,
      groupIds: indexed?.groupIds ?? [],
    };
    this.#graph = buildDocumentGraph(shape);
    this.#heatmaps = buildHeatmapRows(shape);
    this.#graphSelection.textContent = this.#graph.truncated
      ? "The graph shows the first 120 annotations. Select a node to inspect it."
      : "Select a layer or annotation node to inspect it in the Document view.";
    this.updateControls();
  }

  show(view: ResultViewName): void {
    this.#activeView = view;
    if (view === "heatmap") {
      void this.renderHeatmaps();
    } else if (view === "graph") {
      void this.renderGraph();
    }
  }

  private async addCurrentDocument(): Promise<void> {
    const current = this.#current;
    if (!current?.wireDocument || !current.modelId || this.#busy) {
      this.setStatus("This result has no indexed chunk embeddings. Select an embedding model and chunk strategy.", true);
      return;
    }
    this.#busy = true;
    this.setStatus("Sending the analyzed document shape to the gRPC workspace index.");
    this.updateControls();
    try {
      const document = { ...current.wireDocument };
      if (typeof document.docId !== "string" || !document.docId.trim()) {
        document.docId = `workbench-document-${this.#nextDocumentId++}`;
      }
      this.#workspace = await this.#options.index({
        ...(this.#workspace ? { indexId: this.#workspace.id } : {}),
        displayName: "Workbench index",
        documents: [document],
        embedding: { modelId: current.modelId },
        chunkGroupIds: current.groupIds,
      });
      this.setStatus(`Indexed by the gRPC server. ${this.#workspace.size ?? 0} chunks available.`);
    } catch (error) {
      this.setStatus(error instanceof Error ? error.message : "Server-side indexing failed.", true);
    } finally {
      this.#busy = false;
      this.updateControls();
    }
  }

  private async clear(): Promise<void> {
    if (!this.#workspace || this.#busy) {
      return;
    }
    this.#busy = true;
    this.updateControls();
    try {
      await this.#options.deleteIndex(this.#workspace.id);
      this.#workspace = undefined;
      this.#results.replaceChildren(emptyMessage("No workspace search results yet."));
      this.setStatus("The gRPC server deleted the workspace index.");
    } catch (error) {
      this.setStatus(error instanceof Error ? error.message : "Could not delete the workspace index.", true);
    } finally {
      this.#busy = false;
      this.updateControls();
    }
  }

  private async search(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const query = this.#query.value.trim();
    if (!query || this.#busy || !this.#workspace) {
      return;
    }

    this.#busy = true;
    this.setStatus("The gRPC server is embedding and searching the workspace query.");
    this.updateControls();
    try {
      const response = await this.#options.search({
        indexId: this.#workspace.id,
        query: { rawText: query },
        topK: Math.min(50, this.#workspace.maxTopK ?? 50),
      });
      this.renderSearchResults(response.hits);
      this.updateServerHeatmap(response.hits);
      this.setStatus(response.hits.length === 0
        ? "The server returned no compatible chunks."
        : `${response.hits.length} server-ranked chunks returned. Heatmap updated.`);
    } catch (error) {
      this.setStatus(error instanceof Error ? error.message : "Workspace query failed.", true);
    } finally {
      this.#busy = false;
      this.updateControls();
    }
  }

  private renderSearchResults(hits: SearchHit[]): void {
    if (hits.length === 0) {
      this.#results.replaceChildren(emptyMessage("No compatible vectors were found in the server workspace."));
      return;
    }
    this.#results.replaceChildren(...hits.map((hit, index) => {
      const item = document.createElement("article");
      item.className = "search-hit";
      const rank = document.createElement("span");
      rank.className = "search-rank";
      rank.textContent = String(index + 1).padStart(2, "0");
      const body = document.createElement("div");
      const title = document.createElement("h4");
      title.textContent = hit.documentId;
      const detail = document.createElement("p");
      detail.textContent = `${hit.modelId} · cosine ${hit.score.toFixed(4)}`;
      const preview = document.createElement("p");
      preview.className = "search-preview";
      preview.textContent = textPreview(hit.emittedChunkText, 150);
      body.append(title, detail, preview);
      const open = document.createElement("button");
      open.type = "button";
      open.className = "secondary-button";
      open.textContent = "Open";
      open.addEventListener("click", () => this.#options.openDocument(hit));
      item.append(rank, body, open);
      return item;
    }));
  }

  private updateServerHeatmap(hits: SearchHit[]): void {
    const sourceText = this.#current?.shape.rawText;
    this.#heatmaps = {
      semantic: hits.filter((hit) => hit.sourceText === sourceText
          && hit.offsetEncoding === "OFFSET_ENCODING_UTF16_CODE_UNIT")
        .map((hit) => ({
          start: hit.start,
          end: hit.end,
          label: hit.emittedChunkText,
          score: hit.score,
          modelId: hit.modelId,
        })),
      sentiment: this.#heatmaps.sentiment,
    };
    if (this.#activeView === "heatmap") {
      void this.renderHeatmaps();
    }
  }

  private async renderHeatmaps(): Promise<void> {
    const { renderHeatmap } = await import("./charts");
    this.#semanticChart?.dispose();
    this.#sentimentChart?.dispose();
    this.#semanticChart = renderHeatmap(
      this.#semanticHeatmap,
      this.#heatmaps.semantic,
      "Run a workspace query to display scores returned by the gRPC search service.",
    );
    this.#sentimentChart = renderHeatmap(
      this.#sentimentHeatmap,
      this.#heatmaps.sentiment,
      "This document has no typed sentiment layer with positional scores.",
    );
  }

  private async renderGraph(): Promise<void> {
    const { renderDocumentGraph } = await import("./charts");
    this.#graphChart?.dispose();
    this.#graphChart = this.#graph
      ? renderDocumentGraph(this.#graphCanvas, this.#graph, (node) => this.selectGraphNode(node))
      : undefined;
  }

  private selectGraphNode(node: DocumentGraphNode): void {
    this.#graphSelection.textContent = node.kind === "document"
      ? "Document root"
      : `${node.kind === "layer" ? "Layer" : "Annotation"}: ${node.label}`;
    if (node.layerId !== undefined && node.annotationIndex !== undefined) {
      this.#options.selectAnnotation(node.layerId, node.annotationIndex);
    }
  }

  private resize(): void {
    this.#semanticChart?.resize();
    this.#sentimentChart?.resize();
    this.#graphChart?.resize();
  }

  private updateControls(): void {
    const indexable = Boolean(this.#current?.wireDocument && this.#current.modelId);
    this.#addButton.disabled = !indexable || this.#busy;
    this.#clearButton.disabled = !this.#workspace || this.#busy;
    this.#searchButton.disabled = !this.#workspace || !this.#query.value.trim() || this.#busy;
    this.#query.disabled = !this.#workspace || this.#busy;
    this.#indexCount.textContent = String(this.#workspace?.size ?? 0);
  }

  private setStatus(value: string, error = false): void {
    this.#status.textContent = value;
    this.#status.classList.toggle("is-error", error);
  }
}

function indexableDocument(json: string): {
  document: Record<string, unknown>;
  modelId: string;
  groupIds: string[];
} | undefined {
  try {
    const envelope = JSON.parse(json) as Record<string, unknown>;
    const document = record(envelope.document);
    const groups: Array<Record<string, unknown>> = [];
    if (Array.isArray(document?.chunkEmbeddingGroups)) {
      for (const value of document.chunkEmbeddingGroups) {
        const group = record(value);
        if (group) {
          groups.push(group);
        }
      }
    }
    const groupIds = groups.flatMap((group) => typeof group.groupId === "string" ? [group.groupId] : []);
    const modelId = groups.flatMap((group) => Array.isArray(group.embeddingModelIds)
      ? group.embeddingModelIds.filter((value): value is string => typeof value === "string") : [])[0];
    return document && modelId ? { document, modelId, groupIds } : undefined;
  } catch {
    return undefined;
  }
}

function record(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown> : undefined;
}

function textPreview(text: string, limit: number): string {
  return ellipsizeCodePoints(collapseWhitespace(text), limit);
}
