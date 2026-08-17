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
import { supportsCompleteGraph } from "./document-window";
import type { SearchHit, SearchIndex, SearchRequest, SearchResponse } from "./search-adapter";
import { collapseWhitespace, ellipsizeCodePoints } from "./text-utils";
import { emptyMessage, requiredElement } from "./ui-utils";
import {
  buildDocumentGraph,
  buildHeatmapRows,
  buildSimilarityHeatmapRows,
  type DocumentGraph,
  type DocumentGraphNode,
  type HeatmapRow,
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

type IndexableCurrentDocument = CurrentDocument & {
  wireDocument: Record<string, unknown>;
  modelId: string;
};

type HeatmapMode = "query" | "sentiment";

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
  readonly #heatmapQueryForm = requiredElement<HTMLFormElement>("heatmap-query-form");
  readonly #heatmapQuery = requiredElement<HTMLInputElement>("heatmap-query");
  readonly #heatmapQueryButton = requiredElement<HTMLButtonElement>("heatmap-query-button");
  readonly #queryModeButton = requiredElement<HTMLButtonElement>("heatmap-mode-query");
  readonly #sentimentModeButton = requiredElement<HTMLButtonElement>("heatmap-mode-sentiment");
  readonly #heatmapStatus = requiredElement<HTMLElement>("heatmap-status");
  readonly #heatmapCanvas = requiredElement<HTMLElement>("document-heatmap");
  readonly #heatmapSelection = requiredElement<HTMLElement>("heatmap-selection");
  readonly #graphCanvas = requiredElement<HTMLElement>("document-graph");
  readonly #graphSelection = requiredElement<HTMLElement>("graph-selection");
  readonly #graphCompleteness = requiredElement<HTMLButtonElement>("graph-completeness");

  #current?: CurrentDocument;
  #workspace?: SearchIndex;
  #heatmapWorkspace?: SearchIndex;
  #heatmaps: HeatmapRows = { semantic: [], sentiment: [] };
  #graph?: DocumentGraph;
  #heatmapChart?: ChartHandle;
  #graphChart?: ChartHandle;
  #busy = false;
  #nextDocumentId = 1;
  #documentRevision = 0;
  #workspaceDocumentRevision = -1;
  #heatmapRevision = -1;
  #heatmapMode: HeatmapMode = "query";
  #graphComplete = false;
  #activeView: ResultViewName = "document";

  constructor(options: SemanticWorkbenchOptions) {
    this.#options = options;
    this.#addButton.addEventListener("click", () => void this.addCurrentDocument());
    this.#clearButton.addEventListener("click", () => void this.clear());
    this.#searchForm.addEventListener("submit", (event) => void this.search(event));
    this.#query.addEventListener("input", () => this.updateControls());
    this.#heatmapQueryForm.addEventListener("submit", (event) => void this.searchHeatmap(event));
    this.#heatmapQuery.addEventListener("input", () => this.updateControls());
    this.#queryModeButton.addEventListener("click", () => this.selectHeatmapMode("query"));
    this.#sentimentModeButton.addEventListener("click", () => this.selectHeatmapMode("sentiment"));
    this.#graphCompleteness.addEventListener("click", () => this.toggleCompleteGraph());
    window.addEventListener("resize", () => this.resize());
    this.updateControls();
  }

  setDocument(title: string, shape: DocumentShapeView, response?: unknown): void {
    const indexed = indexableDocument(response);
    this.#current = {
      title: title.trim(),
      shape,
      wireDocument: indexed?.document,
      modelId: indexed?.modelId,
      groupIds: indexed?.groupIds ?? [],
    };
    this.#graphComplete = false;
    this.rebuildGraph();
    this.#heatmaps = buildHeatmapRows(shape);
    this.#documentRevision++;
    this.#heatmapSelection.textContent = "Select a colored segment to inspect its text and score.";
    this.updateHeatmapStatus();
    this.updateControls();
  }

  show(view: ResultViewName): void {
    this.#activeView = view;
    if (view === "heatmap") {
      void this.renderHeatmap();
    } else if (view === "graph") {
      void this.renderGraph();
    }
  }

  private async addCurrentDocument(): Promise<void> {
    const current = this.#current;
    if (!isIndexableCurrentDocument(current) || this.#busy) {
      this.setStatus("This result has no indexed chunk embeddings. Select an embedding model and chunk strategy.", true);
      return;
    }
    this.#busy = true;
    this.setStatus("Sending the analyzed document shape to the gRPC workspace index.");
    this.updateControls();
    try {
      const workspace = await this.indexCurrentDocument(current);
      this.setStatus(`Indexed by the gRPC server. ${workspace.size ?? 0} chunks available.`);
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
      this.#workspaceDocumentRevision = -1;
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
    const current = this.#current;
    if (!query || this.#busy || (!this.#workspace && !isIndexableCurrentDocument(current))) {
      return;
    }

    this.#busy = true;
    this.setStatus("Preparing the latest document for server-side search.");
    this.updateControls();
    try {
      if (isIndexableCurrentDocument(current)
          && this.#workspaceDocumentRevision !== this.#documentRevision) {
        await this.indexCurrentDocument(current);
      }
      const workspace = this.#workspace;
      if (!workspace) {
        throw new Error("No server workspace is available for this query.");
      }
      this.setStatus("The gRPC server is embedding and searching the workspace query.");
      const response = await this.#options.search({
        indexId: workspace.id,
        query: { rawText: query },
        topK: Math.min(50, workspace.maxTopK ?? 50),
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

  private async indexCurrentDocument(current: IndexableCurrentDocument): Promise<SearchIndex> {
    const document = indexDocumentProjection(current.wireDocument);
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
    this.#workspaceDocumentRevision = this.#documentRevision;
    return this.#workspace;
  }

  private async searchHeatmap(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const current = this.#current;
    const query = this.#heatmapQuery.value.trim();
    if (!isIndexableCurrentDocument(current) || !query || this.#busy) {
      return;
    }

    this.#busy = true;
    this.setHeatmapStatus("The gRPC server is indexing this document and scoring its chunks.");
    this.updateControls();
    try {
      const index = await this.ensureHeatmapIndex(current);
      const response = await this.#options.search({
        indexId: index.id,
        query: { rawText: query },
        topK: Math.min(index.size ?? 1, index.maxTopK ?? 50),
      });
      this.#heatmaps = {
        semantic: buildSimilarityHeatmapRows(current.shape.rawText, response.hits),
        sentiment: this.#heatmaps.sentiment,
      };
      this.setHeatmapStatus(response.hits.length === 0
        ? "The gRPC server returned no compatible chunks for this document."
        : `${response.hits.length} chunks scored by the gRPC server for “${query}”.`);
      await this.renderHeatmap();
    } catch (error) {
      this.setHeatmapStatus(error instanceof Error ? error.message : "Document heatmap search failed.", true);
    } finally {
      this.#busy = false;
      this.updateControls();
    }
  }

  private async ensureHeatmapIndex(current: IndexableCurrentDocument): Promise<SearchIndex> {
    if (this.#heatmapWorkspace && this.#heatmapWorkspace.modelId !== current.modelId) {
      await this.#options.deleteIndex(this.#heatmapWorkspace.id);
      this.#heatmapWorkspace = undefined;
      this.#heatmapRevision = -1;
    }
    if (this.#heatmapWorkspace && this.#heatmapRevision === this.#documentRevision) {
      return this.#heatmapWorkspace;
    }
    const document = {
      ...indexDocumentProjection(current.wireDocument),
      docId: "heatmap-current-document",
    };
    this.#heatmapWorkspace = await this.#options.index({
      ...(this.#heatmapWorkspace ? { indexId: this.#heatmapWorkspace.id } : {}),
      displayName: "Current document heatmap",
      documents: [document],
      embedding: { modelId: current.modelId },
      chunkGroupIds: current.groupIds,
    });
    this.#heatmapRevision = this.#documentRevision;
    return this.#heatmapWorkspace;
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
      semantic: sourceText ? buildSimilarityHeatmapRows(sourceText, hits) : [],
      sentiment: this.#heatmaps.sentiment,
    };
    if (this.#activeView === "heatmap") {
      void this.renderHeatmap();
    }
  }

  private async renderHeatmap(): Promise<void> {
    const { renderHeatmap } = await import("./charts");
    this.#heatmapChart?.dispose();
    const rows = this.#heatmapMode === "query" ? this.#heatmaps.semantic : this.#heatmaps.sentiment;
    this.#heatmapChart = renderHeatmap(
      this.#heatmapCanvas,
      rows,
      this.#heatmapMode === "query"
        ? "Enter a query above. The gRPC server will index and score this document's chunks."
        : "This document has no typed sentiment layer with positional scores.",
      (row) => this.selectHeatmapRow(row),
    );
  }

  private selectHeatmapMode(mode: HeatmapMode): void {
    this.#heatmapMode = mode;
    this.#queryModeButton.setAttribute("aria-pressed", String(mode === "query"));
    this.#sentimentModeButton.setAttribute("aria-pressed", String(mode === "sentiment"));
    this.#heatmapQueryForm.hidden = mode !== "query";
    this.updateHeatmapStatus();
    this.updateControls();
    void this.renderHeatmap();
  }

  private selectHeatmapRow(row: HeatmapRow): void {
    const kind = this.#heatmapMode === "query" ? "Similarity" : row.category ?? "Sentiment";
    this.#heatmapSelection.textContent = `${kind} ${row.score.toFixed(4)} · characters ${row.start}`
      + ` to ${row.end} · ${row.label}`;
  }

  private updateHeatmapStatus(): void {
    if (!this.#current) {
      this.setHeatmapStatus("Analyze text to build a document heatmap.");
    } else if (this.#heatmapMode === "sentiment") {
      this.setHeatmapStatus(this.#heatmaps.sentiment.length > 0
        ? `${this.#heatmaps.sentiment.length} typed sentiment spans are available in this analysis response.`
        : "No typed sentiment layer was returned. Enable Sentiment and install its model data first.");
    } else if (!this.#current.wireDocument || !this.#current.modelId) {
      this.setHeatmapStatus("Enable document embeddings and at least one chunk strategy, then analyze again.");
    } else {
      this.setHeatmapStatus("Enter a term or phrase. The gRPC server will score this document's chunks.");
    }
  }

  private async renderGraph(): Promise<void> {
    const { renderDocumentGraph } = await import("./charts");
    this.#graphChart?.dispose();
    this.#graphChart = this.#graph
      ? renderDocumentGraph(this.#graphCanvas, this.#graph, (node) => this.selectGraphNode(node))
      : undefined;
  }

  private toggleCompleteGraph(): void {
    if (!this.#current || !supportsCompleteGraph(this.annotationCount())) {
      return;
    }
    this.#graphComplete = !this.#graphComplete;
    this.rebuildGraph();
    if (this.#activeView === "graph") {
      void this.renderGraph();
    }
  }

  private rebuildGraph(): void {
    const shape = this.#current?.shape;
    if (!shape) {
      this.#graph = undefined;
      this.#graphCompleteness.hidden = true;
      return;
    }
    const total = shape.layers.reduce((count, layer) => count + layer.annotations.length, 0);
    const completeAllowed = supportsCompleteGraph(total);
    if (!completeAllowed) {
      this.#graphComplete = false;
    }
    this.#graph = buildDocumentGraph(shape, this.#graphComplete ? total : 120);
    this.#graphCompleteness.hidden = total <= 120;
    this.#graphCompleteness.disabled = !completeAllowed;
    this.#graphCompleteness.setAttribute("aria-pressed", String(this.#graphComplete));
    this.#graphCompleteness.textContent = !completeAllowed
      ? "Complete graph limited for large documents"
      : this.#graphComplete ? "Show balanced overview" : "Show complete graph";
    this.#graphSelection.textContent = this.#graph.truncated
      ? completeAllowed
        ? `Balanced overview of 120 of ${total} annotations across every layer. Select a node or show the complete graph.`
        : `Balanced overview of 120 of ${total} annotations. The complete graph is intentionally bounded.`
      : `Complete graph with ${total} annotations across ${shape.layers.length} layers. Select a node to inspect it.`;
  }

  private annotationCount(): number {
    return this.#current?.shape.layers.reduce(
      (count, layer) => count + layer.annotations.length, 0,
    ) ?? 0;
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
    this.#heatmapChart?.resize();
    this.#graphChart?.resize();
  }

  private updateControls(): void {
    const indexable = Boolean(this.#current?.wireDocument && this.#current.modelId);
    const searchable = Boolean(this.#workspace) || indexable;
    this.#addButton.disabled = !indexable || this.#busy;
    this.#clearButton.disabled = !this.#workspace || this.#busy;
    this.#searchButton.disabled = !searchable || !this.#query.value.trim() || this.#busy;
    this.#query.disabled = !searchable || this.#busy;
    const heatmapIndexable = Boolean(this.#current?.wireDocument && this.#current.modelId);
    this.#heatmapQuery.disabled = this.#heatmapMode !== "query" || !heatmapIndexable || this.#busy;
    this.#heatmapQueryButton.disabled = this.#heatmapMode !== "query" || !heatmapIndexable
      || !this.#heatmapQuery.value.trim() || this.#busy;
    this.#indexCount.textContent = String(this.#workspace?.size ?? 0);
  }

  private setStatus(value: string, error = false): void {
    this.#status.textContent = value;
    this.#status.classList.toggle("is-error", error);
  }

  private setHeatmapStatus(value: string, error = false): void {
    this.#heatmapStatus.textContent = value;
    this.#heatmapStatus.classList.toggle("is-error", error);
  }
}

function indexableDocument(response: unknown): {
  document: Record<string, unknown>;
  modelId: string;
  groupIds: string[];
} | undefined {
  try {
    const envelope = record(response);
    const document = record(envelope?.document);
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

function indexDocumentProjection(document: Record<string, unknown>): Record<string, unknown> {
  const projected: Record<string, unknown> = {};
  for (const field of [
    "docId",
    "rawText",
    "offsetEncoding",
    "metadata",
    "chunkEmbeddingGroups",
  ]) {
    if (document[field] !== undefined) {
      projected[field] = document[field];
    }
  }
  return projected;
}

function record(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown> : undefined;
}

function isIndexableCurrentDocument(
  current: CurrentDocument | undefined,
): current is IndexableCurrentDocument {
  return Boolean(current?.wireDocument && current.modelId);
}

function textPreview(text: string, limit: number): string {
  return ellipsizeCodePoints(collapseWhitespace(text), limit);
}
