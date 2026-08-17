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
import type { SearchHit, SearchIndex, SearchRequest, SearchResponse } from "./search-adapter";
import { createSearchRequest } from "./search-adapter";
import {
  compareChunkText,
  documentAnalytics,
  hitAnnotations,
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
import { emptyMessage, errorMessage, requiredElement } from "./ui-utils";

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
  readonly #originalSpan = requiredElement<HTMLElement>("search-original-span");
  readonly #emittedChunk = requiredElement<HTMLElement>("search-emitted-chunk");
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

  #indexes: SearchIndex[] = [];
  #hits: SearchHit[] = [];
  #busy = false;
  #selectionGeneration = 0;

  constructor(options: ServerSearchWorkbenchOptions) {
    this.#options = options;
    this.#form.addEventListener("submit", (event) => void this.search(event));
    this.#query.addEventListener("input", () => this.updateControls());
    this.#indexSelect.addEventListener("change", () => {
      this.updateIndexDescription();
      this.updateControls();
    });
  }

  async initialize(): Promise<void> {
    try {
      this.#indexes = await this.#options.listIndexes();
      this.#indexSelect.replaceChildren();
      if (this.#indexes.length === 0) {
        this.#indexSelect.add(new Option("No server indexes configured", ""));
        this.setStatus("The service is available, but it did not report a configured search index.");
        this.#indexDescription.textContent = "An operator must configure an immutable index bundle at startup.";
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

  private async search(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const index = this.selectedIndex();
    const query = this.#query.value.trim();
    const maximum = index?.maxTopK ?? 50;
    const topK = Math.min(maximum, Math.max(1, Number.parseInt(this.#topK.value, 10) || 8));
    if (!index || !query || this.#busy) {
      return;
    }
    const queryBytes = new TextEncoder().encode(query).length;
    if (index.maxQueryBytes !== undefined && queryBytes > index.maxQueryBytes) {
      this.setStatus(`The query is ${formatInteger(queryBytes)} UTF-8 bytes. This index accepts at most `
        + `${formatInteger(index.maxQueryBytes)} bytes.`, true);
      return;
    }

    this.#busy = true;
    this.#selectionGeneration++;
    this.setStatus(`Searching ${index.label}.`);
    this.updateControls();
    try {
      const response = await this.#options.search(createSearchRequest(index.id, query, topK));
      this.#hits = response.hits;
      this.renderResults();
      this.setStatus(searchResultStatus(this.#hits.length, response.truncated));
      if (this.#hits[0]) {
        await this.selectHit(this.#hits[0].id);
      }
    } catch (error) {
      this.#hits = [];
      this.renderResults();
      this.setStatus(errorMessage(error, "Search failed."), true);
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
      identity.textContent = hit.chunkId;
      const provenance = document.createElement("small");
      provenance.textContent = `${hit.documentId} · ${hit.corpusTitle}`;
      const preview = document.createElement("span");
      preview.className = "server-hit-preview";
      preview.textContent = previewText(sourceHighlight(hit).selected || hit.emittedChunkText, 120);
      body.append(identity, provenance, preview);
      const score = scoreBadge(hit.score);
      button.append(rank, body, score);
      return button;
    }));
  }

  private async selectHit(id: string): Promise<void> {
    const hit = this.#selection.select(this.#hits, id);
    if (!hit) {
      return;
    }
    const generation = ++this.#selectionGeneration;
    for (const button of this.#results.querySelectorAll<HTMLButtonElement>(".server-search-hit")) {
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
        this.#annotations.replaceChildren(emptyMessage("Typed annotation analysis is unavailable for this source."));
      }
    }
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

    const comparison = compareChunkText(highlight.selected, hit.emittedChunkText);
    this.#originalSpan.textContent = comparison.original;
    this.#emittedChunk.textContent = comparison.emitted || "The server returned no emitted text.";
    this.#comparisonStatus.textContent = comparison.exact
      ? "The emitted chunk exactly matches the original source span."
      : "The emitted chunk differs from the original span, typically because of configured transformation.";
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
    if (hit.queryEmbeddingRoute.artifactHash) {
      addFact(this.#facts, "Query model artifact", hit.queryEmbeddingRoute.artifactHash);
    }
    if (hit.corpusArtifactHash) {
      addFact(this.#facts, "Corpus artifact", hit.corpusArtifactHash);
    }
    if (hit.build.bundleArtifactHash) {
      addFact(this.#facts, "Index bundle", hit.build.bundleArtifactHash);
    }
    if (hit.build.bundleFormatVersion !== undefined) {
      addFact(this.#facts, "Bundle format", String(hit.build.bundleFormatVersion));
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
    const limit = index.maxTopK ?? 50;
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
      + (index.provenance || "No provenance summary was reported.");
  }

  private selectedIndex(): SearchIndex | undefined {
    return this.#indexes.find((index) => index.id === this.#indexSelect.value);
  }

  private updateControls(): void {
    this.#searchButton.disabled = this.#busy || !this.selectedIndex() || !this.#query.value.trim();
    this.#indexSelect.disabled = this.#busy || this.#indexes.length === 0;
    this.#query.disabled = this.#busy || this.#indexes.length === 0;
    this.#topK.disabled = this.#busy || this.#indexes.length === 0;
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

function addFact(list: HTMLDListElement, term: string, value: string, href?: string): void {
  const container = document.createElement("div");
  const name = document.createElement("dt");
  name.textContent = term;
  const detail = document.createElement("dd");
  if (href) {
    const link = document.createElement("a");
    link.href = href;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    link.textContent = value;
    detail.append(link);
  } else {
    detail.textContent = value;
  }
  container.append(name, detail);
  list.append(container);
}

function configuredRouteLabel(hit: SearchHit): string {
  return `${hit.backendId} / ${hit.modelId} · ${hit.vectorSpaceId}`;
}

function queryRouteLabel(hit: SearchHit): string {
  const route = hit.queryEmbeddingRoute;
  return `${route.backendId} / ${route.modelId} · ${route.vectorSpaceId}`;
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
