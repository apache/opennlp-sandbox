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
import {
  annotationConfidence,
  type AnnotationEntry,
  type AnnotationLayerView,
  type AnnotationView,
  type DocumentShapeView,
} from "./document-shape";
import type { SearchHit } from "./search-adapter";
import { annotationsIntersecting, hitAnnotations, sourceHighlight } from "./search-view-model";
import { buildTermVectorStack, rankedTermVectors, summaryText } from "./term-vector-stack";
import { asciiLowerCase } from "./text-utils";
import { emptyMessage, flashButtonLabel, requiredElement } from "./ui-utils";

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
    const engines = entityEngines(annotation.source);
    if (engines.length > 0) {
      addFact(facts, "Recognized by", engines.join(", "));
    }
    if (layer.valueType === "Embedding") {
      const embedding = embeddingFromSource(annotation.source);
      const summary = embeddingSummary(embedding);
      this.#content.replaceChildren(title, facts, summary);
    } else {
      const source = structuredValue(annotation.source);
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

  /** Pops out one document-scoped category layer as its full ranked distribution. */
  showCategoryDistribution(layer: AnnotationLayerView, trigger?: HTMLElement): void {
    const ranked = [...layer.annotations]
      .sort((left, right) => annotationConfidence(right) - annotationConfidence(left));
    const title = document.createElement("strong");
    title.textContent = layer.title;
    const summary = document.createElement("p");
    summary.textContent = `${ranked.length} category ${ranked.length === 1 ? "prediction" : "predictions"}, `
      + "ranked by confidence. Select one for its typed annotation.";
    const list = document.createElement("ol");
    list.className = "term-vector-list";
    for (const annotation of ranked) {
      const item = document.createElement("li");
      const row = document.createElement("button");
      row.type = "button";
      row.className = "term-vector-row";
      const label = document.createElement("span");
      label.className = "term-vector-row-term";
      label.textContent = annotation.label;
      const value = document.createElement("span");
      value.className = "term-vector-row-count";
      const confidence = annotationConfidence(annotation);
      value.textContent = confidence > 0 ? `${(confidence * 100).toFixed(1)}%` : "not reported";
      row.append(label, value);
      row.addEventListener("click", () => this.showAnnotation(layer, annotation, trigger));
      item.append(row);
      list.append(item);
    }
    this.#content.replaceChildren(title, summary, list);
    this.open(trigger);
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
    title.textContent = hit.indexedChunkText;
    const facts = document.createElement("dl");
    facts.className = "annotation-facts";
    addFact(facts, "Cosine score", hit.score.toFixed(4));
    addFact(facts, "Chunk group", hit.chunkGroupId);
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
    addOptionalFact(facts, "Index artifact", hit.build.bundleArtifactHash);
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
  const source = structuredValue(annotation.source);
  block.append(title, facts, source);
  return block;
}

function structuredValue(source: Record<string, unknown>): HTMLElement {
  const section = document.createElement("section");
  section.className = "structured-value";
  const header = document.createElement("div");
  header.className = "structured-value-header";
  const label = document.createElement("strong");
  label.textContent = "Value";
  const copy = document.createElement("button");
  copy.type = "button";
  copy.className = "copy-button structured-copy";
  copy.textContent = "Copy JSON";
  copy.addEventListener("click", () => void copyJson(copy, source));
  header.append(label, copy);
  section.append(header, structuredFields(source));
  return section;
}

function structuredFields(source: Record<string, unknown>): HTMLDListElement {
  const fields = document.createElement("dl");
  fields.className = "structured-fields";
  for (const [key, value] of Object.entries(source)) {
    const name = document.createElement("dt");
    name.textContent = key;
    const detail = document.createElement("dd");
    detail.append(structuredFieldValue(key, value));
    fields.append(name, detail);
  }
  return fields;
}

function structuredFieldValue(key: string, value: unknown): HTMLElement {
  if (Array.isArray(value)) {
    if (isVectorField(key, value)) {
      return structuredVector(value);
    }
    if (value.length <= 8 && value.every(isScalar)) {
      return codeValue(`[${value.map(scalarText).join(", ")}]`);
    }
    return structuredCollection(value);
  }
  const nested = recordValue(value);
  if (nested) {
    const entries = Object.entries(nested);
    if (entries.length <= 4 && entries.every(([, entry]) => isScalar(entry))) {
      const fields = entries.map(([name, entry]) => `${name}: ${scalarText(entry)}`);
      return codeValue(`{ ${fields.join(", ")} }`);
    }
    const details = document.createElement("details");
    const summary = document.createElement("summary");
    summary.textContent = countLabel(entries.length, "field");
    details.append(summary, structuredFields(nested));
    return details;
  }
  return codeValue(scalarText(value));
}

function structuredCollection(values: unknown[]): HTMLElement {
  const details = document.createElement("details");
  const summary = document.createElement("summary");
  summary.textContent = countLabel(values.length, "item");
  const list = document.createElement("ol");
  list.className = "structured-list";
  for (const value of values) {
    const item = document.createElement("li");
    const nested = recordValue(value);
    if (nested) {
      item.append(structuredFields(nested));
    } else if (Array.isArray(value)) {
      item.append(structuredCollection(value));
    } else {
      item.append(codeValue(scalarText(value)));
    }
    list.append(item);
  }
  details.append(summary, list);
  return details;
}

function structuredVector(values: unknown[]): HTMLElement {
  const vector = values.filter((value): value is number =>
    typeof value === "number" && Number.isFinite(value));
  const container = document.createElement("span");
  container.className = "structured-vector";
  const summary = document.createElement("span");
  const preview = vector.slice(0, 3).map(compactNumber).join(", ");
  summary.textContent = `${vector.length} values${preview ? ` · ${preview}${vector.length > 3 ? ", …" : ""}` : ""}`;
  const copy = document.createElement("button");
  copy.type = "button";
  copy.className = "copy-button vector-copy";
  copy.textContent = "Copy vector";
  copy.addEventListener("click", () => void copyVector(copy, vector));
  container.append(summary, copy);
  return container;
}

function isVectorField(key: string, value: unknown[]): boolean {
  return asciiLowerCase(key).includes("vector")
    && value.length > 0
    && value.every((entry) => typeof entry === "number" && Number.isFinite(entry));
}

function isScalar(value: unknown): boolean {
  return value === null
    || typeof value === "string"
    || typeof value === "number"
    || typeof value === "boolean";
}

function scalarText(value: unknown): string {
  if (value === null) {
    return "null";
  }
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number") {
    return compactNumber(value);
  }
  return String(value);
}

function compactNumber(value: number): string {
  return Number(value.toPrecision(6)).toString();
}

function codeValue(value: string): HTMLElement {
  const code = document.createElement("code");
  code.textContent = value;
  return code;
}

function recordValue(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}

function countLabel(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? "" : "s"}`;
}

async function copyJson(button: HTMLButtonElement, value: Record<string, unknown>): Promise<void> {
  try {
    await navigator.clipboard.writeText(JSON.stringify(value));
    flashButtonLabel(button, "Copied");
  } catch {
    flashButtonLabel(button, "Copy failed");
  }
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
    flashButtonLabel(button, "Copied");
  } catch {
    flashButtonLabel(button, "Copy failed");
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

/**
 * Lists the engines that recognized an entity, from its provenance sources:
 * one "recognizer (engine)" entry per contributing provider.
 */
function entityEngines(source: Record<string, unknown>): string[] {
  const sources = Array.isArray(source.sources) ? source.sources : [];
  const engines: string[] = [];
  for (const value of sources) {
    const record = typeof value === "object" && value !== null && !Array.isArray(value)
      ? value as Record<string, unknown>
      : undefined;
    const engine = typeof record?.engine === "string" ? record.engine : undefined;
    const recognizer =
        typeof record?.recognizerId === "string" ? record.recognizerId : undefined;
    if (engine || recognizer) {
      engines.push(recognizer && engine
        ? `${recognizer} (${engine})`
        : recognizer ?? engine ?? "");
    }
  }
  return engines;
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
