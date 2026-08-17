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
import {
  representativeVectors,
  SessionVectorIndex,
  type SessionDocument,
} from "./embedding-workbench";
import type { DocumentShapeView } from "./document-shape";
import { collapseWhitespace, ellipsizeCodePoints } from "./text-utils";
import { emptyMessage, requiredElement } from "./ui-utils";
import {
  buildDocumentGraph,
  buildHeatmapRows,
  type DocumentGraph,
  type DocumentGraphNode,
  type HeatmapRows,
} from "./visualization-data";

export type ResultViewName = "document" | "heatmap" | "graph" | "json";

export interface SemanticWorkbenchOptions {
  analyzeQuery(text: string): Promise<DocumentShapeView>;
  openDocument(document: SessionDocument): void;
  selectAnnotation(layerId: string, annotationIndex: number): void;
}

export class SemanticWorkbench {
  readonly #options: SemanticWorkbenchOptions;
  readonly #index = new SessionVectorIndex();
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

  #current?: { id: string; title: string; shape: DocumentShapeView; json: string };
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
    this.#addButton.addEventListener("click", () => this.addCurrentDocument());
    this.#clearButton.addEventListener("click", () => this.clear());
    this.#searchForm.addEventListener("submit", (event) => void this.search(event));
    this.#query.addEventListener("input", () => this.updateControls());
    window.addEventListener("resize", () => this.resize());
    this.updateControls();
  }

  setDocument(title: string, shape: DocumentShapeView, json = ""): void {
    this.#current = { id: `document-${this.#nextDocumentId++}`, title: title.trim(), shape, json };
    this.#graph = buildDocumentGraph(shape);
    // Score each positional embedding against the document's own representative vector, so
    // the heatmap renders immediately after analysis when the profile returns embeddings.
    this.#heatmaps = buildHeatmapRows(shape, representativeVectors(shape));
    this.#graphSelection.textContent = this.#graph.truncated
      ? "The graph shows the first 120 annotations. Select a node to inspect it."
      : "Select a layer or annotation node to inspect it in the Document view.";
    this.updateControls();
  }

  /** Returns the heatmap rows computed for the current document. */
  heatmapRows(): HeatmapRows {
    return this.#heatmaps;
  }

  show(view: ResultViewName): void {
    this.#activeView = view;
    if (view === "heatmap") {
      void this.renderHeatmaps();
    } else if (view === "graph") {
      void this.renderGraph();
    }
  }

  private addCurrentDocument(): void {
    if (!this.#current || !this.#index.add(
      this.#current.id,
      documentTitle(this.#current.title),
      this.#current.shape,
      this.#current.json,
    )) {
      this.setStatus("This result has no usable document embedding. Choose an embedding-enabled profile.", true);
      return;
    }
    this.setStatus(`Added document to this browser session. ${this.#index.size} indexed.`);
    this.updateControls();
  }

  private clear(): void {
    this.#index.clear();
    this.#results.replaceChildren(emptyMessage("No session search results yet."));
    this.setStatus("Session index cleared.");
    this.updateControls();
  }

  private async search(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const query = this.#query.value.trim();
    if (!query || this.#busy || this.#index.size === 0) {
      return;
    }

    this.#busy = true;
    this.setStatus("Analyzing the query with the selected profile.");
    this.updateControls();
    try {
      const queryShape = await this.#options.analyzeQuery(query);
      const queryVectors = representativeVectors(queryShape);
      if (queryVectors.length === 0) {
        this.setStatus("The selected profile returned no document embedding for this query.", true);
        return;
      }
      const hits = this.#index.search(queryShape);
      this.renderSearchResults(hits);
      if (this.#current) {
        this.#heatmaps = buildHeatmapRows(this.#current.shape, queryVectors);
        if (this.#activeView === "heatmap") {
          await this.renderHeatmaps();
        }
      }
      this.setStatus(hits.length === 0
        ? "No indexed document used a compatible embedding model and vector size."
        : `${hits.length} matching ${hits.length === 1 ? "document" : "documents"}. Heatmap updated.`);
    } catch (error) {
      this.setStatus(error instanceof Error ? error.message : "Query analysis failed.", true);
    } finally {
      this.#busy = false;
      this.updateControls();
    }
  }

  private renderSearchResults(hits: ReturnType<SessionVectorIndex["search"]>): void {
    if (hits.length === 0) {
      this.#results.replaceChildren(emptyMessage("No compatible vectors were found in this session."));
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
      title.textContent = hit.document.title;
      const detail = document.createElement("p");
      detail.textContent = `${hit.modelId} · cosine ${hit.score.toFixed(4)}`;
      const preview = document.createElement("p");
      preview.className = "search-preview";
      preview.textContent = textPreview(hit.document.shape.rawText, 150);
      body.append(title, detail, preview);
      const open = document.createElement("button");
      open.type = "button";
      open.className = "secondary-button";
      open.textContent = "Open";
      open.addEventListener("click", () => this.#options.openDocument(hit.document));
      item.append(rank, body, open);
      return item;
    }));
  }

  private async renderHeatmaps(): Promise<void> {
    const { renderHeatmap } = await import("./charts");
    this.#semanticChart?.dispose();
    this.#sentimentChart?.dispose();
    this.#semanticChart = renderHeatmap(
      this.#semanticHeatmap,
      this.#heatmaps.semantic,
      "This document has no positional embedding layers. Choose an embedding-enabled profile.",
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
    const hasEmbedding = this.#current ? representativeVectors(this.#current.shape).length > 0 : false;
    this.#addButton.disabled = !hasEmbedding || this.#busy;
    this.#clearButton.disabled = this.#index.size === 0 || this.#busy;
    this.#searchButton.disabled = this.#index.size === 0 || !this.#query.value.trim() || this.#busy;
    this.#query.disabled = this.#index.size === 0 || this.#busy;
    this.#indexCount.textContent = `${this.#index.size} ${this.#index.size === 1 ? "document" : "documents"}`;
  }

  private setStatus(value: string, error = false): void {
    this.#status.textContent = value;
    this.#status.classList.toggle("is-error", error);
  }
}

function documentTitle(text: string): string {
  return textPreview(text, 52) || "Untitled document";
}

function textPreview(text: string, limit: number): string {
  return ellipsizeCodePoints(collapseWhitespace(text), limit);
}
