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

import { readDocumentShape, type DocumentShapeView } from "./document-shape";
import {
  buildQueryNode,
  clauseLabel,
  type JoinMode,
  type QueryClause,
} from "./query-builder";
import type { SearchHit, SearchIndex, SearchRequest, SearchResponse } from "./search-adapter";
import {
  supportsKeywordClauses,
  createAllHitsSearchRequest,
  createCompoundSearchRequest,
  createSearchRequest,
} from "./search-adapter";
import { buildDocumentHeat, type HeatSegment } from "./search-heatmap";
import {
  compareChunkText,
  documentAnalytics,
  hitAnnotations,
  matchedSegments,
  scoreColor,
  searchResultStatus,
  SearchSelection,
  sourceHighlight,
} from "./search-view-model";
import {
  asciiLowerCase,
  collapseWhitespace,
  ellipsizeCodePoints,
  formatInteger,
  replaceCharacter,
  withoutPrefix,
} from "./text-utils";
import { addFact, emptyMessage, errorMessage, requiredElement } from "./ui-utils";

const SEARCH_TOP_K_LIMIT = 50_000;

export interface ServerSearchWorkbenchOptions {
  listIndexes(): Promise<SearchIndex[]>;
  search(request: SearchRequest): Promise<SearchResponse>;
  analyzeSource(hit: SearchHit): Promise<DocumentShapeView>;
}

/** Coordinates server-backed search controls and results. */
export class ServerSearchWorkbench {
  readonly #options: ServerSearchWorkbenchOptions;
  readonly #form = requiredElement<HTMLFormElement>("server-search-form");
  readonly #indexSelect = requiredElement<HTMLSelectElement>("server-search-index");
  readonly #query = requiredElement<HTMLInputElement>("server-search-query");
  readonly #topK = requiredElement<HTMLInputElement>("server-search-top-k");
  readonly #searchButton = requiredElement<HTMLButtonElement>("server-search-button");
  readonly #status = requiredElement<HTMLElement>("server-search-status");
  readonly #indexDescription = requiredElement<HTMLElement>("server-index-description");
  readonly #results = requiredElement<HTMLElement>("server-search-results");
  readonly #resultCount = requiredElement<HTMLElement>("server-result-count");
  readonly #sourceText = requiredElement<HTMLElement>("search-source-text");
  readonly #originalPanel = requiredElement<HTMLElement>("search-original-panel");
  readonly #originalSpan = requiredElement<HTMLElement>("search-original-span");
  readonly #indexedChunk = requiredElement<HTMLElement>("search-indexed-chunk");
  readonly #comparisonStatus = requiredElement<HTMLElement>("chunk-comparison-status");
  readonly #score = requiredElement<HTMLOutputElement>("selected-search-score");
  readonly #facts = requiredElement<HTMLDListElement>("search-hit-facts");
  readonly #annotations = requiredElement<HTMLElement>("search-hit-annotations");
  readonly #analytics = {
    sentences: requiredElement<HTMLElement>("search-sentence-count"),
    tokens: requiredElement<HTMLElement>("search-token-count"),
    entities: requiredElement<HTMLElement>("search-entity-count"),
    chunks: requiredElement<HTMLElement>("search-chunk-count"),
    terms: requiredElement<HTMLElement>("search-term-count"),
  };
  readonly #selection = new SearchSelection();
  readonly #builderKind = requiredElement<HTMLSelectElement>("builder-kind");
  readonly #builderText = requiredElement<HTMLInputElement>("builder-text");
  readonly #builderMode = requiredElement<HTMLSelectElement>("builder-mode");
  readonly #builderSlop = requiredElement<HTMLInputElement>("builder-slop");
  readonly #builderAdd = requiredElement<HTMLButtonElement>("builder-add-button");
  readonly #builderClauses = requiredElement<HTMLElement>("builder-clauses");
  readonly #builderJoin = requiredElement<HTMLSelectElement>("builder-join");
  readonly #builderClear = requiredElement<HTMLButtonElement>("builder-clear-button");
  readonly #viewListButton = requiredElement<HTMLButtonElement>("server-view-list-button");
  readonly #viewHeatmapButton = requiredElement<HTMLButtonElement>("server-view-heatmap-button");
  readonly #heatmap = requiredElement<HTMLElement>("server-search-heatmap");

  #indexes: SearchIndex[] = [];
  #hits: SearchHit[] = [];
  #clauses: QueryClause[] = [];
  #busy = false;
  #selectionGeneration = 0;
  #heatmapView = false;
  #fullCoverage = true;

  constructor(options: ServerSearchWorkbenchOptions) {
    this.#options = options;
    this.#form.addEventListener("submit", (event) => void this.search(event));
    this.#query.addEventListener("input", () => this.updateControls());
    this.#indexSelect.addEventListener("change", () => {
      this.updateIndexDescription();
      this.updateControls();
    });
    this.#builderKind.addEventListener("change", () => this.updateBuilderControls());
    this.#builderAdd.addEventListener("click", () => this.addClause());
    // The clause input sits outside the search form, so Enter would otherwise
    // be dropped; it adds the drafted clause exactly as the button does.
    this.#builderText.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        this.addClause();
      }
    });
    this.#builderClear.addEventListener("click", () => {
      this.#clauses = [];
      this.renderClauses();
      this.updateControls();
    });
    this.#viewListButton.addEventListener("click", () => this.setHeatmapView(false));
    this.#viewHeatmapButton.addEventListener("click", () => this.setHeatmapView(true));
    this.updateBuilderControls();
    this.renderClauses();
  }

  async initialize(): Promise<void> {
    try {
      this.#indexes = await this.#options.listIndexes();
      this.#indexSelect.replaceChildren();
      if (this.#indexes.length === 0) {
        this.#indexSelect.add(new Option("No server indexes configured", ""));
        this.setStatus("The service is available, but it did not report a configured search index.");
        this.#indexDescription.textContent = "No index exists yet. Build one from your own documents, or ask the operator to configure a read-only index. ";
        this.#indexDescription.append(jumpButton("workflows", "Open Build index"));
        return;
      }
      for (const index of this.#indexes) {
        this.#indexSelect.add(new Option(index.label, index.id));
      }
      this.#indexSelect.disabled = false;
      this.#query.disabled = false;
      this.updateIndexDescription();
      this.setStatus(`${this.#indexes.length} server ${this.#indexes.length === 1 ? "index" : "indexes"} available.`);
      this.updateControls();
    } catch (error) {
      this.setStatus(errorMessage(error, "Search index discovery is unavailable."), true);
      this.#indexDescription.textContent = "The analysis workbench remains available.";
    }
  }

  private addClause(): void {
    const text = this.#builderText.value.trim();
    if (!text) {
      this.setStatus("Enter clause text before adding it.", true);
      return;
    }
    const kind = this.#builderKind.value;
    if (kind === "term") {
      this.#clauses.push({
        kind: "term",
        text,
        mode: this.#builderMode.value === "all" ? "all" : "any",
      });
    } else if (kind === "phrase") {
      const slop = Number.parseInt(this.#builderSlop.value, 10);
      this.#clauses.push({
        kind: "phrase",
        text,
        slop: Number.isFinite(slop) && slop >= 0 ? slop : 0,
      });
    } else {
      this.#clauses.push({ kind: "semantic", text });
    }
    this.#builderText.value = "";
    this.renderClauses();
    this.updateControls();
  }

  private renderClauses(): void {
    this.#builderClauses.replaceChildren();
    if (this.#clauses.length === 0) {
      this.#builderClauses.append(
        emptyMessage("No clauses yet; the text query above runs as a single semantic clause."));
      return;
    }
    this.#clauses.forEach((clause, position) => {
      const chip = document.createElement("span");
      chip.className = "builder-clause";
      const label = document.createElement("span");
      label.textContent = clauseLabel(clause);
      const remove = document.createElement("button");
      remove.type = "button";
      remove.setAttribute("aria-label", `Remove clause ${position + 1}`);
      remove.textContent = "×";
      remove.addEventListener("click", () => {
        this.#clauses.splice(position, 1);
        this.renderClauses();
        this.updateControls();
      });
      chip.append(label, remove);
      this.#builderClauses.append(chip);
    });
  }

  private updateBuilderControls(): void {
    const kind = this.#builderKind.value;
    this.#builderMode.hidden = kind !== "term";
    this.#builderSlop.hidden = kind !== "phrase";
  }

  /**
   * Offers only the clause kinds the selected index can execute: an index with no keyword
   * component runs semantic clauses alone, and the builder says so instead of letting the
   * search fail.
   */
  private updateClauseKinds(): void {
    const index = this.selectedIndex();
    const keyword = index === undefined || supportsKeywordClauses(index);
    for (const option of Array.from(this.#builderKind.options)) {
      if (option.value === "term" || option.value === "phrase") {
        option.disabled = !keyword;
      }
    }
    if (!keyword && this.#builderKind.value !== "semantic") {
      this.#builderKind.value = "semantic";
      this.updateBuilderControls();
    }
    this.#builderKind.title = keyword ? "" : "This index has no keyword component, so only semantic clauses run.";
  }

  /**
   * Writes a search failure to the status line with the jump that resolves it: a missing
   * index points at where indexes are built and saved, a missing embedding model at the
   * catalog.
   */
  private explainFailure(message: string): void {
    this.setStatus(message, true);
    const lower = asciiLowerCase(message);
    if (lower.includes("unknown search index") || lower.includes("unknown index")) {
      this.#status.append(" ", jumpButton("workflows", "Build an index"), " ",
        jumpButton("lifecycle", "Save one to disk on Lifecycle"));
    } else if (lower.includes("embedding model") || lower.includes("vector space")
        || lower.includes("not loaded")) {
      this.#status.append(" ", jumpButton("models", "Install a model on Models & data"));
    }
  }

  private async search(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const index = this.selectedIndex();
    const query = this.#query.value.trim();
    const compound = this.#clauses.length > 0;
    const maximum = Math.min(index?.maxTopK ?? SEARCH_TOP_K_LIMIT, SEARCH_TOP_K_LIMIT);
    // Exhaustive-capable providers use a typed request; other providers stay bounded by top_k.
    const topK = this.#heatmapView
      ? maximum
      : Math.min(maximum, Math.max(1, Number.parseInt(this.#topK.value, 10) || 8));
    if (!index || (!query && !compound) || this.#busy) {
      return;
    }
    let request: SearchRequest;
    if (compound) {
      try {
        request = createCompoundSearchRequest(index.id,
          buildQueryNode(this.#clauses, joinMode(this.#builderJoin.value)), topK);
      } catch (error) {
        this.setStatus(errorMessage(error, "The advanced search clauses are invalid."), true);
        return;
      }
    } else {
      const queryBytes = new TextEncoder().encode(query).length;
      if (index.maxQueryBytes !== undefined && queryBytes > index.maxQueryBytes) {
        this.setStatus(`The query is ${formatInteger(queryBytes)} UTF-8 bytes. This index accepts at most `
          + `${formatInteger(index.maxQueryBytes)} bytes.`, true);
        return;
      }
      request = this.#heatmapView && index.supportsAllHits
        ? createAllHitsSearchRequest(index.id, query)
        : createSearchRequest(index.id, query, topK);
    }

    this.#busy = true;
    this.#selectionGeneration++;
    this.setStatus(compound
      ? `Running the advanced search against ${index.label}.`
      : `Searching ${index.label}.`);
    this.updateControls();
    try {
      const response = await this.#options.search(request);
      this.#hits = response.hits;
      this.#fullCoverage = !response.truncated && (request.allHits === true
        ? index.size !== undefined && response.hits.length === index.size
        : response.hits.length < topK || index.size !== undefined && topK >= index.size);
      this.renderResults();
      this.renderHeatmap();
      this.setStatus(searchResultStatus(this.#hits.length, response.truncated));
      if (this.#hits[0]) {
        await this.selectHit(this.#hits[0].id);
      }
    } catch (error) {
      this.#hits = [];
      this.renderResults();
      this.renderHeatmap();
      this.explainFailure(errorMessage(error, "Search failed."));
    } finally {
      this.#busy = false;
      this.updateControls();
    }
  }

  private renderResults(): void {
    this.#resultCount.textContent = `${this.#hits.length} ${this.#hits.length === 1 ? "hit" : "hits"}`;
    if (this.#hits.length === 0) {
      this.#results.replaceChildren(emptyMessage("No scored chunks were returned."));
      return;
    }
    this.#results.replaceChildren(...this.#hits.map((hit, index) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "server-search-hit";
      button.dataset.hitId = hit.id;
      button.setAttribute("aria-pressed", "false");
      button.addEventListener("click", () => void this.selectHit(hit.id));

      const rank = document.createElement("span");
      rank.className = "server-hit-rank";
      rank.textContent = String(index + 1).padStart(2, "0");
      const body = document.createElement("span");
      body.className = "server-hit-body";
      const identity = document.createElement("strong");
      identity.textContent = hit.documentId;
      const provenance = document.createElement("small");
      provenance.textContent = `chunk ${ellipsizeCodePoints(hit.chunkId, 24)} · ${hit.corpusTitle}`;
      const preview = document.createElement("span");
      preview.className = "server-hit-preview";
      preview.textContent = previewText(sourceHighlight(hit).selected || hit.indexedChunkText, 120);
      body.append(identity, provenance, preview);
      const score = scoreBadge(hit.score);
      button.append(rank, body, score);
      return button;
    }));
  }

  /** Switches between the ranked list and the document heatmap. */
  private setHeatmapView(active: boolean): void {
    if (this.#heatmapView === active) {
      return;
    }
    this.#heatmapView = active;
    this.#viewListButton.setAttribute("aria-pressed", String(!active));
    this.#viewHeatmapButton.setAttribute("aria-pressed", String(active));
    this.#results.hidden = active;
    this.#heatmap.hidden = !active;
    this.#topK.disabled = active || this.#busy || this.#indexes.length === 0;
  }

  /** Renders every returned chunk over its document's source text, shaded by score. */
  private renderHeatmap(): void {
    const documents = buildDocumentHeat(this.#hits);
    if (documents.length === 0) {
      this.#heatmap.replaceChildren(
        emptyMessage("Run a query in heatmap view to shade every chunk of every document."));
      return;
    }
    const rendered = documents.map((heat) => {
      const article = document.createElement("article");
      article.className = "heat-document";
      const heading = document.createElement("div");
      heading.className = "heat-document-heading";
      const identity = document.createElement("h4");
      identity.textContent = heat.documentId;
      const chunks = document.createElement("small");
      chunks.textContent = `${heat.chunkCount} scored ${heat.chunkCount === 1 ? "chunk" : "chunks"}`;
      heading.append(identity, chunks, scoreBadge(heat.maxScore));
      const text = document.createElement("p");
      text.className = "heat-text";
      text.append(...heat.segments.map((segment) => this.heatSegmentNode(segment)));
      article.append(heading, text);
      return article;
    });
    if (this.#fullCoverage) {
      this.#heatmap.replaceChildren(...rendered);
      return;
    }
    const note = document.createElement("p");
    note.className = "heat-coverage-note";
    note.textContent = "Only the requested top results are shaded. Search again in heatmap view "
      + "to score every chunk.";
    this.#heatmap.replaceChildren(note, ...rendered);
  }

  /** Builds one heat segment: plain gap text, or a selectable score-shaded chunk. */
  private heatSegmentNode(segment: HeatSegment): Node {
    if (segment.score === undefined || !segment.hitId) {
      return document.createTextNode(segment.text);
    }
    const color = scoreColor(segment.score);
    const chunk = document.createElement("button");
    chunk.type = "button";
    chunk.className = "heat-chunk";
    chunk.dataset.hitId = segment.hitId;
    chunk.style.backgroundColor = color.background;
    chunk.style.color = color.foreground;
    chunk.title = `${segment.chunkId} · score ${segment.score.toFixed(4)}`;
    chunk.setAttribute("aria-pressed", "false");
    chunk.setAttribute("aria-label",
      `Chunk ${segment.chunkId}, cosine score ${segment.score.toFixed(4)}`);
    chunk.addEventListener("click", () => void this.selectHit(segment.hitId ?? ""));
    chunk.append(...matchedSegments({
      indexedChunkText: segment.text,
      matchedSpans: segment.matchedSpans,
    }).map((part) => {
      if (!part.matched) {
        return document.createTextNode(part.text);
      }
      const mark = document.createElement("mark");
      mark.className = "matched-span";
      mark.textContent = part.text;
      if (part.term) {
        mark.title = `Matched query term: ${part.term}`;
      }
      return mark;
    }));
    return chunk;
  }

  private async selectHit(id: string): Promise<void> {
    const hit = this.#selection.select(this.#hits, id);
    if (!hit) {
      return;
    }
    const generation = ++this.#selectionGeneration;
    const selectable = [
      ...this.#results.querySelectorAll<HTMLButtonElement>(".server-search-hit"),
      ...this.#heatmap.querySelectorAll<HTMLButtonElement>(".heat-chunk"),
    ];
    for (const button of selectable) {
      button.setAttribute("aria-pressed", String(button.dataset.hitId === id));
    }
    this.renderHit(hit);
    const embeddedShape = sourceDocumentShape(hit);
    this.renderAnalysis(embeddedShape, hit, embeddedShape.layers.length === 0);
    if (embeddedShape.layers.length > 0) {
      return;
    }
    try {
      const analyzedShape = await this.#options.analyzeSource(hit);
      if (generation === this.#selectionGeneration && this.#selection.selectedId === hit.id) {
        this.renderAnalysis(analyzedShape, hit, false);
      }
    } catch {
      if (generation === this.#selectionGeneration && this.#selection.selectedId === hit.id) {
        this.renderAnalysisUnavailable();
      }
    }
  }

  /** Marks the analytics counters unavailable so a stalled ellipsis never suggests loading. */
  private renderAnalysisUnavailable(): void {
    for (const element of Object.values(this.#analytics)) {
      element.textContent = "n/a";
    }
    this.#annotations.replaceChildren(
      emptyMessage("Typed annotation analysis is unavailable for this source."));
  }

  private renderHit(hit: SearchHit): void {
    const color = scoreColor(hit.score);
    this.#score.textContent = hit.score.toFixed(4);
    this.#score.style.backgroundColor = color.background;
    this.#score.style.color = color.foreground;
    this.#score.setAttribute("aria-label", `Cosine score ${hit.score.toFixed(4)}`);

    const highlight = sourceHighlight(hit);
    const marker = document.createElement("mark");
    marker.textContent = highlight.selected;
    marker.style.backgroundColor = color.background;
    marker.style.color = color.foreground;
    marker.setAttribute("aria-label", `Selected source span, score ${hit.score.toFixed(4)}`);
    this.#sourceText.replaceChildren(document.createTextNode(highlight.before), marker,
      document.createTextNode(highlight.after));

    const comparison = compareChunkText(highlight.selected, hit.indexedChunkText);
    // A byte-identical chunk collapses to one panel; the note explains the missing copy.
    this.#originalPanel.hidden = comparison.exact;
    this.#originalPanel.parentElement?.classList.toggle("is-single", comparison.exact);
    this.#originalSpan.textContent = comparison.original;
    this.renderIndexedChunk(hit);
    this.#comparisonStatus.textContent = comparison.exact
      ? "The indexed chunk exactly matches the original source span, so it is shown once."
      : "The indexed chunk differs from the original span, usually because normalization was "
        + "applied before indexing.";
    this.#comparisonStatus.classList.toggle("is-different", !comparison.exact);

    this.#facts.replaceChildren();
    addFact(this.#facts, "Document ID", hit.documentId);
    addFact(this.#facts, "Chunk ID", hit.chunkId);
    addFact(this.#facts, "Source offsets", `${hit.start}..${hit.end} (${offsetLabel(hit.offsetEncoding)})`);
    addFact(this.#facts, "Cosine score", hit.score.toFixed(6));
    addFact(this.#facts, "Index", hit.indexId || "Not reported");
    addFact(this.#facts, "Search provider", providerLabel(hit.providerId));
    addFact(this.#facts, "Configured embedding route", configuredRouteLabel(hit));
    addFact(this.#facts, "Query embedding route", queryRouteLabel(hit));
    addFact(this.#facts, "Corpus", hit.corpusTitle);
    addFact(this.#facts, "Provenance", hit.provenance || "Not reported");
    if (hit.artifactHash) {
      addFact(this.#facts, "Model artifact", hit.artifactHash);
    }
    if (hit.queryEmbeddingRoute?.artifactHash) {
      addFact(this.#facts, "Query model artifact", hit.queryEmbeddingRoute.artifactHash);
    }
    if (hit.corpusArtifactHash) {
      addFact(this.#facts, "Corpus artifact", hit.corpusArtifactHash);
    }
    if (hit.build.bundleArtifactHash) {
      addFact(this.#facts, "Index artifact", hit.build.bundleArtifactHash);
    }
    if (hit.build.bundleFormatVersion !== undefined) {
      addFact(this.#facts, "Index format", String(hit.build.bundleFormatVersion));
    }
    if (hit.build.builderId) {
      const builder = [hit.build.builderId, hit.build.builderVersion].filter(Boolean).join(" ");
      addFact(this.#facts, "Index builder", builder);
    }
    if (hit.build.preparationConfigHash) {
      addFact(this.#facts, "Preparation config", hit.build.preparationConfigHash);
    }
    if (hit.licenseName) {
      addFact(this.#facts, "Corpus license", hit.licenseName, hit.licenseUri);
    } else if (hit.licenseUri) {
      addFact(this.#facts, "Corpus license", "License terms", hit.licenseUri);
    }
    if (hit.sourceUri) {
      addFact(this.#facts, "Source", hit.sourceUri, hit.sourceUri);
    }
  }

  /** Renders the indexed chunk with keyword matches marked for highlighting. */
  private renderIndexedChunk(hit: SearchHit): void {
    const segments = matchedSegments(hit);
    if (segments.length === 0) {
      this.#indexedChunk.textContent = "The server returned no indexed text.";
      return;
    }
    this.#indexedChunk.replaceChildren(...segments.map((segment) => {
      if (!segment.matched) {
        return document.createTextNode(segment.text);
      }
      const mark = document.createElement("mark");
      mark.className = "matched-span";
      mark.textContent = segment.text;
      if (segment.term) {
        mark.title = `Matched query term: ${segment.term}`;
      }
      return mark;
    }));
  }

  private renderAnalysis(shape: DocumentShapeView, hit: SearchHit, loading: boolean): void {
    const analytics = documentAnalytics(shape);
    for (const [key, element] of Object.entries(this.#analytics)) {
      element.textContent = loading ? "…" : String(analytics[key as keyof typeof analytics]);
    }
    if (loading) {
      this.#annotations.replaceChildren(emptyMessage("Loading typed annotations for the selected document."));
      return;
    }
    const annotations = hitAnnotations(shape, hit);
    if (annotations.length === 0) {
      this.#annotations.replaceChildren(emptyMessage("No typed annotations intersect this source span."));
      return;
    }
    this.#annotations.replaceChildren(...annotations.map(({ layer, annotation }) => {
      const item = document.createElement("article");
      const name = document.createElement("strong");
      name.textContent = annotation.label;
      const detail = document.createElement("span");
      detail.textContent = `${layer.title} · ${layer.valueType}`;
      item.append(name, detail);
      return item;
    }));
  }

  private updateIndexDescription(): void {
    const index = this.selectedIndex();
    if (!index) {
      return;
    }
    const limit = Math.min(index.maxTopK ?? SEARCH_TOP_K_LIMIT, SEARCH_TOP_K_LIMIT);
    this.#topK.max = String(limit);
    if (Number(this.#topK.value) > limit) {
      this.#topK.value = String(limit);
    }
    const size = index.size === undefined ? "unknown size" : `${formatInteger(index.size)} vectors`;
    const dimension = index.dimension === undefined ? "unknown dimensions" : `${index.dimension} dimensions`;
    const license = index.licenseName ? ` · ${index.licenseName}` : "";
    const queryLimit = index.maxQueryBytes === undefined ? ""
      : ` · ${formatInteger(index.maxQueryBytes)} query bytes max`;
    const responseLimit = index.maxResponseBytes === undefined ? ""
      : ` · ${formatInteger(index.maxResponseBytes)} response bytes max`;
    this.#indexDescription.textContent = `${index.corpusTitle} · ${size} · ${dimension} · ${metricLabel(index.metric)}`
      + `${license}${queryLimit}${responseLimit}. `
      + (index.provenance || "No provenance summary was reported.")
      + (supportsKeywordClauses(index) ? ""
        : " This index has no keyword component, so Advanced search runs semantic clauses only.");
    this.updateClauseKinds();
  }

  private selectedIndex(): SearchIndex | undefined {
    return this.#indexes.find((index) => index.id === this.#indexSelect.value);
  }

  private updateControls(): void {
    this.#searchButton.disabled = this.#busy || !this.selectedIndex()
      || (!this.#query.value.trim() && this.#clauses.length === 0);
    this.#indexSelect.disabled = this.#busy || this.#indexes.length === 0;
    this.#query.disabled = this.#busy || this.#indexes.length === 0;
    // Compound clauses replace the free-text query, so the native required
    // constraint must not block the form submit while clauses exist.
    this.#query.required = this.#clauses.length === 0;
    this.#topK.disabled = this.#heatmapView || this.#busy || this.#indexes.length === 0;
  }

  private setStatus(message: string, error = false): void {
    this.#status.textContent = message;
    this.#status.classList.toggle("is-error", error);
  }
}

function sourceDocumentShape(hit: SearchHit): DocumentShapeView {
  return readDocumentShape({ document: hit.sourceDocument });
}

function scoreBadge(score: number): HTMLSpanElement {
  const color = scoreColor(score);
  const badge = document.createElement("span");
  badge.className = "server-hit-score";
  badge.textContent = score.toFixed(3);
  badge.style.backgroundColor = color.background;
  badge.style.color = color.foreground;
  badge.setAttribute("aria-label", `Cosine score ${score.toFixed(3)}`);
  return badge;
}

function configuredRouteLabel(hit: SearchHit): string {
  return `${hit.backendId} / ${hit.modelId} · ${hit.vectorSpaceId}`;
}

function queryRouteLabel(hit: SearchHit): string {
  const route = hit.queryEmbeddingRoute;
  return route
    ? `${route.backendId} / ${route.modelId} · ${route.vectorSpaceId}`
    : "None (keyword-only query)";
}

function offsetLabel(value: string): string {
  if (value === "OFFSET_ENCODING_UTF16_CODE_UNIT") {
    return "UTF-16";
  }
  if (value === "OFFSET_ENCODING_UNICODE_CODE_POINT") {
    return "code points";
  }
  return "UTF-8 bytes";
}

function metricLabel(value: string): string {
  return value === "SEARCH_METRIC_COSINE" ? "cosine" : replaceCharacter(asciiLowerCase(value), "_", " ");
}

function providerLabel(value: string): string {
  const provider = withoutPrefix(value, "STANDARD_SEARCH_PROVIDER_");
  return replaceCharacter(asciiLowerCase(provider), "_", " ") || "Not reported";
}

function previewText(value: string, limit: number): string {
  return ellipsizeCodePoints(collapseWhitespace(value), limit);
}

function joinMode(value: string): JoinMode {
  return value === "or" || value === "rrf" ? value : "and";
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
