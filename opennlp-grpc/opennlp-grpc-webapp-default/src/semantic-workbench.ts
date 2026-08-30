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
import { readChunkProjection, type ChunkProjectionGroup } from "./chunk-projection";
import type { IndexDocumentsRequest } from "./api";
import type { DocumentShapeView } from "./document-shape";
import {
  renderDocumentHeatmap,
  type DocumentHeatmapChunk,
  type DocumentHeatmapLane,
} from "./document-heatmap-view";
import { supportsCompleteGraph } from "./document-window";
import { toBrowserSpan } from "./offsets";
import {
  createAllHitsSearchRequest,
  createIndexDocumentsRequest,
  createSearchRequest,
  indexStateLabel,
  SCRATCH_INDEX_PREFIX,
  type SearchHit,
  type SearchIndex,
  type SearchRequest,
  type SearchResponse,
} from "./search-adapter";
import { collapseWhitespace, ellipsizeCodePoints } from "./text-utils";
import { emptyMessage, requiredElement } from "./ui-utils";
import {
  buildDocumentGraph,
  buildHeatmapRows,
  type DocumentGraph,
  type DocumentGraphNode,
  type HeatmapRow,
  type HeatmapRows,
} from "./visualization-data";

export type ResultViewName = "document" | "chunks" | "heatmap" | "graph" | "json";

export interface SemanticWorkbenchOptions {
  index(request: IndexDocumentsRequest): Promise<SearchIndex>;
  search(request: SearchRequest): Promise<SearchResponse>;
  listIndexes(): Promise<SearchIndex[]>;
  deleteIndex(indexId: string): Promise<void>;
  openDocument(hit: SearchHit): void;
  inspectChunk(hit: SearchHit, shape: DocumentShapeView, trigger: HTMLElement): void;
  inspectSpan(
    shape: DocumentShapeView,
    start: number,
    end: number,
    text: string,
    trigger: HTMLElement,
  ): void;
  selectAnnotation(layerId: string, annotationIndex: number): void;
  /**
   * Receives the outcome of "Add to live index", which is pressed on the Analyze tab; when
   * absent the outcome is written to this tab's own status line.
   */
  onIndexed?(message: string, error: boolean): void;
  /** Asks before a live index is deleted on the server; absent means no confirmation. */
  confirmDelete?(label: string): boolean;
  /** Runs after this tab created or deleted a live index, so other tabs can refresh. */
  onWorkspacesChanged?(): void;
}

interface CurrentDocument {
  title: string;
  shape: DocumentShapeView;
  wireDocument?: Record<string, unknown>;
  modelId?: string;
  groupIds: string[];
  projections: ChunkProjectionGroup[];
}

type IndexableCurrentDocument = CurrentDocument & {
  wireDocument: Record<string, unknown>;
  modelId: string;
};

type HeatmapMode = "query" | "sentiment";

const ALL_PROJECTIONS = "ALL_PROJECTIONS";
const TURBO_QUANT_PROVIDER = "STANDARD_SEARCH_PROVIDER_TURBO_QUANT";
/** Display-name prefix marking the short-lived heatmap indexes this tab creates. */
const HEATMAP_INDEX_PREFIX = SCRATCH_INDEX_PREFIX;

/** Coordinates server-owned, in-memory workspace indexing and query rendering. */
export class SemanticWorkbench {
  readonly #options: SemanticWorkbenchOptions;
  readonly #addButton = requiredElement<HTMLButtonElement>("add-to-index-button");
  readonly #clearButton = requiredElement<HTMLButtonElement>("clear-index-button");
  readonly #workspaceSelect = requiredElement<HTMLSelectElement>("workspace-index-select");
  readonly #providerSelect = requiredElement<HTMLSelectElement>("workspace-provider-select");
  /** The name a new live index gets; absent in fixtures that mount the search form alone. */
  readonly #nameInput = document.getElementById("workspace-name-input") as HTMLInputElement | null;
  readonly #searchForm = requiredElement<HTMLFormElement>("semantic-search-form");
  readonly #query = requiredElement<HTMLTextAreaElement>("semantic-query");
  readonly #searchButton = requiredElement<HTMLButtonElement>("search-button");
  readonly #indexCount = requiredElement<HTMLElement>("index-count");
  readonly #indexStorage = requiredElement<HTMLElement>("index-storage");
  readonly #status = requiredElement<HTMLElement>("semantic-status");
  readonly #results = requiredElement<HTMLElement>("search-results");
  readonly #heatmapQueryForm = requiredElement<HTMLFormElement>("heatmap-query-form");
  readonly #heatmapQuery = requiredElement<HTMLInputElement>("heatmap-query");
  readonly #heatmapQueryButton = requiredElement<HTMLButtonElement>("heatmap-query-button");
  readonly #heatmapProjection = requiredElement<HTMLSelectElement>("heatmap-projection-select");
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
  readonly #heatmapWorkspaces = new Map<string, { index: SearchIndex; revision: number }>();
  #heatmapLanes: DocumentHeatmapLane[] = [];
  #heatmaps: HeatmapRows = { semantic: [], sentiment: [] };
  #graph?: DocumentGraph;
  #graphChart?: ChartHandle;
  #busy = false;
  #nextDocumentId = 1;
  #documentRevision = 0;
  #workspaceDocumentRevision = -1;
  #heatmapMode: HeatmapMode = "query";
  #graphComplete = false;
  #activeView: ResultViewName = "document";

  constructor(options: SemanticWorkbenchOptions) {
    this.#options = options;
    this.#addButton.addEventListener("click", () => void this.addCurrentDocument());
    this.#clearButton.addEventListener("click", () => void this.clear());
    this.#workspaceSelect.addEventListener("change", () => void this.attachSelectedWorkspace());
    this.#searchForm.addEventListener("submit", (event) => void this.search(event));
    this.#query.addEventListener("input", () => this.updateControls());
    this.#heatmapQueryForm.addEventListener("submit", (event) => void this.searchHeatmap(event));
    this.#heatmapQuery.addEventListener("input", () => this.updateControls());
    this.#heatmapProjection.addEventListener("change", () => this.projectionChanged());
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
      projections: readChunkProjection(response),
    };
    void this.deleteHeatmapIndexes().catch((error: unknown) => {
      this.setHeatmapStatus(error instanceof Error
        ? error.message : "Could not delete the previous document heatmap indexes.", true);
    });
    this.#graphComplete = false;
    this.rebuildGraph();
    this.#heatmaps = buildHeatmapRows(shape);
    this.#heatmapLanes = [];
    this.populateProjectionOptions();
    this.#documentRevision++;
    this.#heatmapSelection.textContent = "Select a colored segment to inspect its text and score.";
    this.updateHeatmapStatus();
    this.updateControls();
  }

  /** Loads the server's existing dynamic workspaces into the picker; call once at startup. */
  async initializeWorkspaces(): Promise<void> {
    await this.refreshWorkspaces(true);
  }

  /**
   * Repopulates the picker with every writable live index on the server, keeping the
   * attached one selected when it still exists. An explicit refresh (another tab changed
   * the indexes) also detaches an index that vanished; the quiet refresh after an add does
   * not, since the add itself is the source of truth for that moment.
   */
  private async refreshWorkspaces(detachMissing = false): Promise<void> {
    const indexes = await this.#options.listIndexes();
    const workspaces = indexes.filter((index) => !index.immutable
      && !index.label.startsWith(HEATMAP_INDEX_PREFIX));
    const selected = this.#workspace?.id ?? this.#workspaceSelect.value;
    this.#workspaceSelect.replaceChildren(
      new Option("New live index (created on first add)", ""));
    for (const workspace of workspaces) {
      const size = workspace.size ?? 0;
      this.#workspaceSelect.add(new Option(
        `${workspace.label} · ${size} ${size === 1 ? "chunk" : "chunks"} · ${indexStateLabel(workspace)}`,
        workspace.id));
    }
    const stillExists = workspaces.some((workspace) => workspace.id === selected);
    this.#workspaceSelect.value = stillExists ? selected : "";
    if (detachMissing && this.#workspace && !stillExists) {
      // The attached index was deleted or made read-only elsewhere; searching it would fail.
      this.#workspace = undefined;
      this.#workspaceDocumentRevision = -1;
      this.updateControls();
    }
    if (workspaces.length === 0 && !this.#current && this.#results.childElementCount <= 1) {
      this.renderFirstRun();
    }
  }

  /** The first-run state: what a live index is, and the two places one comes from. */
  private renderFirstRun(): void {
    const message = emptyMessage("No live indexes yet. Analyze a document with an embedding "
      + "model and press Add to live index, or build one from your own documents. ");
    message.append(jumpButton("analysis", "Open Analyze"), " ", jumpButton("workflows", "Open Build index"));
    this.#results.replaceChildren(message);
  }

  /** Refreshes the picker after an index change without surfacing discovery errors. */
  private refreshWorkspacesQuietly(): void {
    void this.refreshWorkspaces().catch(() => {
      // The picker keeps its current options when discovery is unavailable.
    });
  }

  /** Attaches search to the picked existing workspace, or detaches back to a new one. */
  private async attachSelectedWorkspace(): Promise<void> {
    const id = this.#workspaceSelect.value;
    if (!id) {
      this.#workspace = undefined;
      this.#workspaceDocumentRevision = -1;
      this.setStatus("Nothing selected. The next add creates a new live index.");
      this.updateControls();
      return;
    }
    try {
      const indexes = await this.#options.listIndexes();
      const workspace = indexes.find((index) => index.id === id);
      if (!workspace) {
        throw new Error("The selected live index no longer exists on the server.");
      }
      this.#workspace = workspace;
      // The attached workspace is searched as it stands; the current document
      // only joins it through an explicit "Add to live index".
      this.#workspaceDocumentRevision = this.#documentRevision;
      const size = workspace.size ?? 0;
      this.setStatus(`Searching '${workspace.label}': `
        + `${size} ${size === 1 ? "chunk is" : "chunks are"} searchable.`);
    } catch (error) {
      this.setStatus(error instanceof Error ? error.message : "Could not select the live index.", true);
    }
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
      this.reportIndexing("This result has no chunk embeddings to index. Select an embedding "
        + "model and a chunk strategy, then analyze again.", true);
      return;
    }
    this.#busy = true;
    this.reportIndexing("Sending the analyzed document to the live index.", false);
    this.updateControls();
    try {
      const workspace = await this.indexCurrentDocument(current);
      this.reportIndexing(`Added to live index '${workspace.label}': ${workspace.size ?? 0} chunks `
        + "are searchable.", false);
    } catch (error) {
      this.reportIndexing(error instanceof Error ? error.message : "Server-side indexing failed.", true);
    } finally {
      this.#busy = false;
      this.updateControls();
    }
  }

  /** Routes an indexing outcome to the tab whose button started it. */
  private reportIndexing(message: string, error: boolean): void {
    if (this.#options.onIndexed) {
      this.#options.onIndexed(message, error);
    }
    this.setStatus(message, error);
  }

  private async clear(): Promise<void> {
    if ((!this.#workspace && this.#heatmapWorkspaces.size === 0) || this.#busy) {
      return;
    }
    if (this.#workspace && this.#options.confirmDelete
        && !this.#options.confirmDelete(this.#workspace.label)) {
      return;
    }
    this.#busy = true;
    this.updateControls();
    try {
      if (this.#workspace) {
        await this.#options.deleteIndex(this.#workspace.id);
      }
      this.#workspace = undefined;
      this.#workspaceDocumentRevision = -1;
      await this.deleteHeatmapIndexes();
      this.#results.replaceChildren(emptyMessage("No live index search results yet."));
      this.setStatus("The server deleted the live index.");
      this.refreshWorkspacesQuietly();
      this.#options.onWorkspacesChanged?.();
    } catch (error) {
      this.setStatus(error instanceof Error ? error.message : "Could not delete the live index.", true);
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
        throw new Error("No live index is available for this query.");
      }
      this.setStatus("The server is embedding the query and searching the live index.");
      const response = await this.#options.search(workspace.supportsAllHits
        ? createAllHitsSearchRequest(workspace.id, query)
        : createSearchRequest(workspace.id, query, Math.min(50, workspace.maxTopK ?? 50)));
      this.renderSearchResults(response.hits);
      this.updateServerHeatmap(response.hits);
      this.setStatus(response.hits.length === 0
        ? "The server returned no compatible chunks."
        : `${response.hits.length} server-ranked chunks returned. Heatmap updated.`);
    } catch (error) {
      this.setStatus(error instanceof Error ? error.message : "Live index search failed.", true);
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
    this.#workspace = await this.#options.index(createIndexDocumentsRequest(
      this.#workspace?.id, this.#providerSelect.value, document, current.modelId,
      current.groupIds, this.#nameInput?.value ?? ""));
    this.#workspaceDocumentRevision = this.#documentRevision;
    this.refreshWorkspacesQuietly();
    this.#options.onWorkspacesChanged?.();
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
      const groups = this.selectedProjections(current);
      const lanes: DocumentHeatmapLane[] = [];
      let totalHits = 0;
      for (const group of groups) {
        const index = await this.ensureHeatmapIndex(current, group);
        const response = await this.#options.search(index.supportsAllHits
          ? createAllHitsSearchRequest(index.id, query)
          : createSearchRequest(index.id, query, Math.min(index.size ?? 1, index.maxTopK ?? 50)));
        totalHits += response.hits.length;
        lanes.push(projectionLane(current, group, index, response));
      }
      this.#heatmapLanes = lanes;
      this.setHeatmapStatus(totalHits === 0
        ? "The gRPC server returned no compatible chunks for this document."
        : `${totalHits} chunks scored by the gRPC server for “${query}”.`);
      await this.renderHeatmap();
    } catch (error) {
      this.setHeatmapStatus(error instanceof Error ? error.message : "Document heatmap search failed.", true);
    } finally {
      this.#busy = false;
      this.updateControls();
    }
  }

  private async ensureHeatmapIndex(
    current: IndexableCurrentDocument,
    group: ChunkProjectionGroup,
  ): Promise<SearchIndex> {
    const existing = this.#heatmapWorkspaces.get(group.id);
    if (existing?.revision === this.#documentRevision && existing.index.modelId === current.modelId) {
      return existing.index;
    }
    if (existing) {
      await this.#options.deleteIndex(existing.index.id);
      this.#heatmapWorkspaces.delete(group.id);
    }
    const document = {
      ...indexDocumentProjection(current.wireDocument),
      docId: "heatmap-current-document",
    };
    const index = await this.#options.index({
      displayName: `Current document heatmap: ${group.title}`,
      provider: { standard: TURBO_QUANT_PROVIDER },
      documents: [document],
      embedding: { modelId: current.modelId },
      chunkGroupIds: [group.id],
    });
    this.#heatmapWorkspaces.set(group.id, { index, revision: this.#documentRevision });
    return index;
  }

  private renderSearchResults(hits: SearchHit[]): void {
    if (hits.length === 0) {
      this.#results.replaceChildren(emptyMessage("No compatible vectors were found in the live index."));
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
      preview.textContent = textPreview(hit.indexedChunkText, 150);
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
    const current = this.#current;
    this.#heatmapLanes = current ? current.projections.map((group) => projectionLane(
      current,
      group,
      undefined,
      { hits: hits.filter((hit) => hit.chunkGroupId === group.id), truncated: false },
    )) : [];
    if (this.#activeView === "heatmap") {
      void this.renderHeatmap();
    }
  }

  private async renderHeatmap(): Promise<void> {
    const current = this.#current;
    if (!current) {
      this.#heatmapCanvas.replaceChildren(emptyMessage("Analyze a document to build its heatmap."));
      return;
    }
    const lanes = this.#heatmapMode === "query"
      ? this.#heatmapLanes
      : [sentimentLane(this.#heatmaps.sentiment)];
    if (lanes.length === 0 || lanes.every((lane) => lane.chunks.length === 0)) {
      const message = this.#heatmapMode === "query"
        ? "Enter a query above. The gRPC server will index and score this document's chunks."
        : "This document has no typed sentiment layer with positional scores.";
      this.#heatmapCanvas.replaceChildren(emptyMessage(message));
      return;
    }
    renderDocumentHeatmap(this.#heatmapCanvas, current.shape.rawText, lanes,
      (chunk, trigger) => this.selectHeatmapChunk(chunk, trigger));
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

  private selectHeatmapChunk(chunk: DocumentHeatmapChunk, trigger: HTMLElement): void {
    const kind = this.#heatmapMode === "query" ? "Similarity" : "Sentiment";
    const score = chunk.score === undefined ? "not returned" : chunk.score.toFixed(4);
    this.#heatmapSelection.textContent = `${kind} ${score} · characters ${chunk.start}`
      + ` to ${chunk.end} · ${chunk.text}`;
    if (chunk.hit && this.#current) {
      this.#options.inspectChunk(chunk.hit, this.#current.shape, trigger);
    } else if (this.#current) {
      this.#options.inspectSpan(
        this.#current.shape, chunk.start, chunk.end, chunk.text, trigger,
      );
    }
  }

  private projectionChanged(): void {
    this.#heatmapLanes = [];
    this.updateHeatmapStatus();
    if (this.#activeView === "heatmap") {
      void this.renderHeatmap();
    }
  }

  private populateProjectionOptions(): void {
    const all = document.createElement("option");
    all.value = ALL_PROJECTIONS;
    all.textContent = "All chunk groups, separate lanes";
    const options = this.#current?.projections.map((group) => {
      const option = document.createElement("option");
      option.value = group.id;
      option.textContent = `${group.title} (${group.strategy})`;
      return option;
    }) ?? [];
    this.#heatmapProjection.replaceChildren(all, ...options);
    this.#heatmapProjection.value = ALL_PROJECTIONS;
  }

  private selectedProjections(current: CurrentDocument): ChunkProjectionGroup[] {
    return this.#heatmapProjection.value === ALL_PROJECTIONS
      ? current.projections
      : current.projections.filter((group) => group.id === this.#heatmapProjection.value);
  }

  private async deleteHeatmapIndexes(): Promise<void> {
    const indexes = [...this.#heatmapWorkspaces.values()].map((workspace) => workspace.index.id);
    this.#heatmapWorkspaces.clear();
    for (const indexId of indexes) {
      await this.#options.deleteIndex(indexId);
    }
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
    this.#graphChart?.resize();
  }

  /**
   * Browns out the tab when the operator disabled live indexing, saying so once instead
   * of letting every add and search fail with the same server error.
   */
  setAvailability(dynamicIndexingEnabled: boolean): void {
    this.#available = dynamicIndexingEnabled;
    if (!dynamicIndexingEnabled) {
      this.setStatus("Live indexing is disabled by the server operator, so documents cannot be "
        + "added or searched here. Read-only indexes are still searched on the Corpus search tab.",
        true);
    }
    this.updateControls();
  }

  #available = true;

  private updateControls(): void {
    this.#indexStorage.textContent = this.#workspace
      ? indexStateLabel(this.#workspace) : "In memory once created";
    this.#workspaceSelect.disabled = this.#busy || !this.#available;
    this.#providerSelect.disabled = Boolean(this.#workspace) || this.#busy || !this.#available;
    const indexable = this.#available
      && Boolean(this.#current?.wireDocument && this.#current.modelId);
    const searchable = this.#available && (Boolean(this.#workspace) || indexable);
    this.#addButton.disabled = !indexable || this.#busy;
    this.#clearButton.disabled = (!this.#workspace && this.#heatmapWorkspaces.size === 0) || this.#busy;
    this.#searchButton.disabled = !searchable || !this.#query.value.trim() || this.#busy;
    this.#query.disabled = !searchable || this.#busy;
    const heatmapIndexable = Boolean(this.#current?.wireDocument && this.#current.modelId);
    this.#heatmapQuery.disabled = this.#heatmapMode !== "query" || !heatmapIndexable || this.#busy;
    this.#heatmapQueryButton.disabled = this.#heatmapMode !== "query" || !heatmapIndexable
      || this.#current?.projections.length === 0 || !this.#heatmapQuery.value.trim() || this.#busy;
    this.#heatmapProjection.disabled = this.#heatmapMode !== "query" || !heatmapIndexable || this.#busy;
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

function projectionLane(
  current: CurrentDocument,
  group: ChunkProjectionGroup,
  index: SearchIndex | undefined,
  response: SearchResponse,
): DocumentHeatmapLane {
  const hits = new Map<string, SearchHit[]>();
  for (const hit of response.hits) {
    if (hit.chunkGroupId !== group.id) {
      continue;
    }
    const span = toBrowserSpan(hit.sourceText, hit.start, hit.end, hit.offsetEncoding);
    if (!span) {
      continue;
    }
    const key = spanKey(span.start, span.end);
    const existing = hits.get(key) ?? [];
    existing.push(hit);
    hits.set(key, existing);
  }
  const chunks = group.chunks.flatMap((chunk) => {
    const span = toBrowserSpan(
      current.shape.rawText,
      chunk.start,
      chunk.end,
      current.shape.offsetEncoding,
    );
    if (!span) {
      return [];
    }
    const matching = hits.get(spanKey(span.start, span.end));
    const hit = matching?.shift();
    return [{
      id: hit?.chunkId ?? `${group.id}:${chunk.index}`,
      start: span.start,
      end: span.end,
      text: chunk.text,
      ...(hit ? { score: hit.score, hit } : {}),
    }];
  });
  const scored = chunks.reduce((count, chunk) => count + (chunk.hit ? 1 : 0), 0);
  const expected = index?.size ?? chunks.length;
  return {
    id: group.id,
    title: `${group.title} (${group.strategy})`,
    complete: !response.truncated && scored === chunks.length && scored === expected,
    scoreLabel: "cosine",
    chunks,
  };
}

function sentimentLane(rows: HeatmapRow[]): DocumentHeatmapLane {
  return {
    id: "sentiment",
    title: "Sentence sentiment",
    complete: true,
    scoreLabel: "polarity",
    chunks: rows.map((row, index) => ({
      id: `sentiment:${index}`,
      start: row.start,
      end: row.end,
      text: row.label,
      score: row.score,
    })),
  };
}

function spanKey(start: number, end: number): string {
  return `${start}:${end}`;
}

/** A link-styled button that jumps to another workbench. */
function jumpButton(target: string, label: string): HTMLButtonElement {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "link-button";
  button.dataset.workbenchJump = target;
  button.textContent = label;
  return button;
}
