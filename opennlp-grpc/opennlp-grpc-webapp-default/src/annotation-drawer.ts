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

import type {
  ChunkProjectionEmbedding,
  ChunkProjectionGroup,
  ChunkProjectionItem,
} from "./chunk-projection";
import type {
  AnnotationEntry,
  AnnotationLayerView,
  AnnotationView,
  DocumentShapeView,
} from "./document-shape";
import type { SearchHit } from "./search-adapter";
import { annotationsIntersecting, hitAnnotations, sourceHighlight } from "./search-view-model";
import { buildTermVectorStack, rankedTermVectors, summaryText } from "./term-vector-stack";
import { asciiLowerCase } from "./text-utils";
import { emptyMessage, requiredElement } from "./ui-utils";

export class AnnotationDrawer {
  readonly #drawer = requiredElement<HTMLElement>("annotation-details");
  readonly #content = requiredElement<HTMLElement>("annotation-details-content");
  readonly #closeButton = requiredElement<HTMLButtonElement>("annotation-details-close");
  readonly #backdrop = requiredElement<HTMLElement>("annotation-drawer-backdrop");
  #returnFocus?: HTMLElement;

  constructor() {
    this.#closeButton.addEventListener("click", () => this.close());
    this.#backdrop.addEventListener("click", () => this.close());
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && !this.#drawer.hidden) {
        this.close();
      }
    });
  }

  reset(): void {
    this.close(false);
    this.#content.replaceChildren(emptyMessage("Select highlighted text to inspect its typed annotation."));
  }

  describeLayer(layer: AnnotationLayerView, note?: string): void {
    const container = document.createElement("div");
    const title = document.createElement("strong");
    title.textContent = layer.title;
    const description = document.createElement("p");
    const identity = layer.standardIdentity ?? layer.id;
    description.textContent = `${identity} contains ${layer.annotations.length} ${asciiLowerCase(layer.valueType)} `
      + `${layer.annotations.length === 1 ? "annotation" : "annotations"}.`;
    container.append(title, description);
    this.#content.replaceChildren(container);
    if (note) {
      this.#content.append(emptyMessage(note));
    }
  }

  showMessage(value: string): void {
    this.#content.replaceChildren(emptyMessage(value));
  }

  showAnnotation(layer: AnnotationLayerView, annotation: AnnotationView, trigger?: HTMLElement): void {
    const title = document.createElement("strong");
    title.textContent = annotation.label;
    const facts = document.createElement("dl");
    facts.className = "annotation-facts";
    addFact(facts, "Layer", layer.id);
    addFact(facts, "Value type", layer.valueType);
    if (annotation.start !== undefined && annotation.end !== undefined) {
      addFact(facts, "Browser span", `${annotation.start}..${annotation.end}`);
    }
    if (annotation.probability !== undefined) {
      addFact(facts, "Probability", annotation.probability.toFixed(4));
    }
    if (annotation.score !== undefined) {
      addFact(facts, "Score", annotation.score.toFixed(4));
    }
    if (layer.valueType === "Embedding") {
      const embedding = embeddingFromSource(annotation.source);
      const summary = embeddingSummary(embedding);
      this.#content.replaceChildren(title, facts, summary);
    } else {
      const source = document.createElement("pre");
      source.textContent = JSON.stringify(annotation.source, null, 2);
      this.#content.replaceChildren(title, facts, source);
    }
    this.open(trigger);
  }

  showAnnotations(
    text: string,
    start: number,
    end: number,
    entries: AnnotationEntry[],
    trigger?: HTMLElement,
  ): void {
    const visibleEntries = entries.filter((entry) => entry.layer.valueType !== "Embedding");
    const title = document.createElement("strong");
    title.textContent = text;
    const summary = document.createElement("p");
    summary.textContent = `${visibleEntries.length} ${visibleEntries.length === 1 ? "annotation" : "annotations"} cover `
      + `characters ${start}..${end}.`;
    this.#content.replaceChildren(title, summary,
      ...visibleEntries.map((entry) => annotationBlock(entry.layer, entry.annotation)));
    this.open(trigger);
  }

  /** Shows every positional annotation intersecting one browser-coordinate document span. */
  showDocumentSpan(
    shape: DocumentShapeView,
    start: number,
    end: number,
    text: string,
    trigger: HTMLElement,
  ): void {
    this.showAnnotations(text, start, end, annotationsIntersecting(shape, start, end), trigger);
  }

  /** Pops out one term-vector layer as its full ranked term list. */
  showTermVectorList(layer: AnnotationLayerView, trigger?: HTMLElement): void {
    const ranked = rankedTermVectors(layer);
    const title = document.createElement("strong");
    title.textContent = layer.title;
    const summary = document.createElement("p");
    summary.textContent = `${summaryText(buildTermVectorStack(layer, ranked.length))}, `
      + "ranked by frequency. Select a term for its typed annotation.";
    const list = document.createElement("ol");
    list.className = "term-vector-list";
    for (const segment of ranked) {
      const item = document.createElement("li");
      const row = document.createElement("button");
      row.type = "button";
      row.className = "term-vector-row";
      const term = document.createElement("span");
      term.className = "term-vector-row-term";
      term.textContent = segment.term;
      const count = document.createElement("span");
      count.className = "term-vector-row-count";
      count.textContent = `${segment.frequency} (${(segment.share * 100).toFixed(0)}%)`;
      row.append(term, count);
      row.addEventListener("click", () => this.showAnnotation(layer, segment.annotation, trigger));
      item.append(row);
      list.append(item);
    }
    this.#content.replaceChildren(title, summary, list);
    this.open(trigger);
  }

  showChunk(group: ChunkProjectionGroup, chunk: ChunkProjectionItem, trigger: HTMLElement): void {
    const title = document.createElement("strong");
    title.textContent = `${group.title}, chunk ${chunk.index}`;
    const facts = document.createElement("dl");
    facts.className = "annotation-facts";
    addFact(facts, "Strategy", group.strategy);
    addFact(facts, "Group", group.id);
    addFact(facts, "Document span", `${chunk.start}..${chunk.end}`);
    addFact(facts, "Embeddings", String(chunk.embeddingCount));
    if (group.embeddingModelIds.length > 0) {
      addFact(facts, "Models", joinLabels(group.embeddingModelIds));
    }
    const text = document.createElement("p");
    text.className = "drawer-chunk-text";
    text.textContent = chunk.text;
    this.#content.replaceChildren(title, facts, text,
      ...chunk.embeddings.map((embedding) => embeddingSummary(embedding)));
    this.open(trigger);
  }

  /** Shows one server-ranked chunk together with the document annotations it covers. */
  showSearchHit(hit: SearchHit, shape: DocumentShapeView, trigger: HTMLElement): void {
    const title = document.createElement("strong");
    title.textContent = hit.emittedChunkText;
    const facts = document.createElement("dl");
    facts.className = "annotation-facts";
    addFact(facts, "Cosine score", hit.score.toFixed(4));
    addFact(facts, "Projection", hit.chunkGroupId);
    addFact(facts, "Document span", `${hit.start}..${hit.end} (${hit.offsetEncoding})`);
    addFact(facts, "Search provider", hit.providerId);
    addFact(facts, "Index", hit.indexId);
    addFact(facts, "Model", hit.modelId);
    addFact(facts, "Serving backend", hit.backendId);
    addFact(facts, "Vector space", hit.vectorSpaceId);
    addOptionalFact(facts, "Model artifact", hit.artifactHash);
    addFact(facts, "Corpus", hit.corpusTitle);
    addFact(facts, "Provenance", hit.provenance);
    addOptionalFact(facts, "Source", hit.sourceUri);
    addOptionalFact(facts, "License", hit.licenseName);
    addOptionalFact(facts, "License URI", hit.licenseUri);
    addOptionalFact(facts, "Corpus artifact", hit.corpusArtifactHash);
    addOptionalFact(facts, "Bundle artifact", hit.build.bundleArtifactHash);
    addOptionalFact(facts, "Preparation", hit.build.preparationConfigHash);

    const source = document.createElement("p");
    source.className = "drawer-chunk-text";
    source.textContent = sourceHighlight(hit).selected;
    const entries = hitAnnotations(shape, hit)
      .filter((entry) => entry.layer.valueType !== "Embedding");
    const summary = document.createElement("p");
    summary.textContent = entries.length === 0
      ? "No positional annotations intersect this chunk."
      : `${entries.length} typed ${entries.length === 1 ? "annotation intersects" : "annotations intersect"} this chunk.`;
    this.#content.replaceChildren(title, facts, source, summary,
      ...entries.map((entry) => annotationBlock(entry.layer, entry.annotation)));
    this.open(trigger);
  }

  private open(trigger?: HTMLElement): void {
    this.#returnFocus = trigger ?? activeElement();
    this.#drawer.hidden = false;
    this.#backdrop.hidden = false;
    document.body.classList.add("drawer-open");
    this.#closeButton.focus();
  }

  private close(restoreFocus = true): void {
    if (this.#drawer.hidden) {
      return;
    }
    this.#drawer.hidden = true;
    this.#backdrop.hidden = true;
    document.body.classList.remove("drawer-open");
    if (restoreFocus) {
      this.#returnFocus?.focus();
    }
    this.#returnFocus = undefined;
  }
}

function addOptionalFact(container: HTMLDListElement, label: string, value: string | undefined): void {
  if (value) {
    addFact(container, label, value);
  }
}

function annotationBlock(layer: AnnotationLayerView, annotation: AnnotationView): HTMLElement {
  const block = document.createElement("section");
  block.className = "annotation-entry";
  const title = document.createElement("strong");
  title.textContent = `${layer.title}: ${annotation.label}`;
  const facts = document.createElement("dl");
  facts.className = "annotation-facts";
  addFact(facts, "Layer", layer.id);
  addFact(facts, "Value type", layer.valueType);
  if (annotation.probability !== undefined) {
    addFact(facts, "Probability", annotation.probability.toFixed(4));
  }
  if (annotation.score !== undefined) {
    addFact(facts, "Score", annotation.score.toFixed(4));
  }
  const source = document.createElement("pre");
  source.textContent = JSON.stringify(annotation.source, null, 2);
  block.append(title, facts, source);
  return block;
}

function embeddingFromSource(source: Record<string, unknown>): ChunkProjectionEmbedding {
  return {
    modelId: textValue(source.modelId) || "Unidentified model",
    granularity: textValue(source.granularity),
    vector: vectorValue(source.vector),
  };
}

function embeddingSummary(embedding: ChunkProjectionEmbedding): HTMLElement {
  const section = document.createElement("section");
  section.className = "embedding-preview";
  const title = document.createElement("strong");
  title.textContent = embedding.modelId;
  const facts = document.createElement("dl");
  facts.className = "annotation-facts";
  addFact(facts, "Granularity", granularityLabel(embedding.granularity));
  addFact(facts, "Dimensions", `${embedding.vector.length} dimensions`);
  addFact(facts, "First 3 values", firstVectorValues(embedding.vector));
  const copy = document.createElement("button");
  copy.type = "button";
  copy.className = "copy-button vector-copy";
  copy.textContent = "Copy vector";
  copy.disabled = embedding.vector.length === 0;
  copy.addEventListener("click", () => void copyVector(copy, embedding.vector));
  section.append(title, facts, copy);
  return section;
}

async function copyVector(button: HTMLButtonElement, vector: number[]): Promise<void> {
  try {
    await navigator.clipboard.writeText(JSON.stringify(vector));
    button.textContent = "Copied";
  } catch {
    button.textContent = "Copy failed";
  }
}

function firstVectorValues(vector: number[]): string {
  if (vector.length === 0) {
    return "Not returned";
  }
  return vector.slice(0, 3).map((value) => value.toFixed(6)).join(", ");
}

function granularityLabel(value: string): string {
  const labels: Readonly<Record<string, string>> = {
    EMBEDDING_GRANULARITY_DOCUMENT: "Document",
    EMBEDDING_GRANULARITY_SENTENCE: "Sentence",
    EMBEDDING_GRANULARITY_CHUNK_LEVEL: "Chunk",
    EMBEDDING_GRANULARITY_GROUP_CENTROID: "Group centroid",
  };
  return labels[value] ?? (value || "Unspecified");
}

function textValue(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function vectorValue(value: unknown): number[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const vector: number[] = [];
  for (const entry of value) {
    if (typeof entry !== "number" || !Number.isFinite(entry)) {
      return [];
    }
    vector.push(entry);
  }
  return vector;
}

function addFact(list: HTMLDListElement, term: string, value: string): void {
  const name = document.createElement("dt");
  name.textContent = term;
  const detail = document.createElement("dd");
  detail.textContent = value;
  list.append(name, detail);
}

function joinLabels(values: string[]): string {
  let result = "";
  for (const value of values) {
    result += result ? `, ${value}` : value;
  }
  return result;
}

function activeElement(): HTMLElement | undefined {
  return document.activeElement instanceof HTMLElement ? document.activeElement : undefined;
}
