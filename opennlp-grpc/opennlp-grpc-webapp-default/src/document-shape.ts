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

import { toBrowserOffset } from "./offsets";
import { asciiLowerCase, asciiUpperCase, splitOnCharacters } from "./text-utils";

export interface DocumentShapeView {
  rawText: string;
  offsetEncoding: string;
  layers: AnnotationLayerView[];
}

export interface AnnotationLayerView {
  id: string;
  title: string;
  scope: string;
  valueType: string;
  standardIdentity?: string;
  qualifier?: string;
  annotations: AnnotationView[];
}

export interface AnnotationView {
  start?: number;
  end?: number;
  label: string;
  probability?: number;
  score?: number;
  source: Record<string, unknown>;
}

export interface AnnotationEntry {
  layer: AnnotationLayerView;
  annotation: AnnotationView;
}

export interface CombinedAnnotationSegment {
  start: number;
  end: number;
  entries: AnnotationEntry[];
}

export interface DocumentShapeSummary {
  layerCount: number;
  annotationCount: number;
  offsetEncodingLabel: string;
  /** Ids of layers the server returned with no annotations, in response order. */
  emptyLayerIds: string[];
}

interface RawDocumentShapeCounts {
  layerCount: number;
  annotationCount: number;
}

export type LayerAccent = "blue" | "cyan" | "green" | "amber" | "violet" | "rose";

const VALUE_TYPES = new Map<string, string>([
  ["stringValues", "String"],
  ["categoryValues", "Category"],
  ["embeddingValues", "Embedding"],
  ["treeValues", "Parse tree"],
  ["subwordValues", "Subword"],
  ["geoValues", "Geographic result"],
  ["wordTypeValues", "Word type"],
  ["entityValues", "Named entity"],
  ["syntacticChunkValues", "Phrase chunk"],
  ["stemValues", "Stem"],
  ["lexicalExpansionValues", "Lexical expansion"],
  ["normalizationValues", "Normalization"],
  ["analyticsValues", "Analytics"],
  ["chunkGroupValues", "Chunk group"],
  ["termVectorValues", "Term vector"],
  ["dependencyValues", "Dependency"],
  ["relationValues", "Relation"],
]);

export function readDocumentShape(response: unknown): DocumentShapeView {
  const envelope = record(response);
  const document = record(envelope?.document);
  const rawText = stringValue(document?.rawText);
  const offsetEncoding = stringValue(document?.offsetEncoding);
  const documentLayers = record(document?.layers);
  const layers = Array.isArray(documentLayers?.layers) ? documentLayers.layers : [];

  return {
    rawText,
    offsetEncoding,
    layers: layers.flatMap((value) => {
      const layer = record(value);
      if (!layer) {
        return [];
      }
      return [readLayer(layer, rawText, offsetEncoding)];
    }),
  };
}

export function summarizeDocumentShape(shape: DocumentShapeView): DocumentShapeSummary {
  return {
    layerCount: shape.layers.length,
    annotationCount: shape.layers.reduce((total, layer) => total + layer.annotations.length, 0),
    offsetEncodingLabel: offsetEncodingLabel(shape.offsetEncoding),
    emptyLayerIds: shape.layers.filter((layer) => layer.annotations.length === 0).map((layer) => layer.id),
  };
}

/** Counts protobuf JSON layers without materializing their annotation views. */
export function countRawDocumentShape(response: unknown): RawDocumentShapeCounts {
  const envelope = record(response);
  const document = record(envelope?.document);
  const documentLayers = record(document?.layers);
  const values = Array.isArray(documentLayers?.layers) ? documentLayers.layers : [];
  let layerCount = 0;
  let annotationCount = 0;
  for (const value of values) {
    const layer = record(value);
    if (!layer) {
      continue;
    }
    layerCount++;
    for (const key of VALUE_TYPES.keys()) {
      const container = record(layer[key]);
      if (container) {
        annotationCount += Array.isArray(container.annotations)
          ? container.annotations.length : 0;
        break;
      }
    }
  }
  return { layerCount, annotationCount };
}

/**
 * Words the completion status with the layers that came back empty, so an analysis that
 * ran every step but produced no annotations for some of them is visible at a glance.
 */
export function analysisCompletionMessage(summary: DocumentShapeSummary): string {
  if (summary.layerCount === 0) {
    return "Analysis complete, but the response has no annotation layers.";
  }
  const empty = summary.emptyLayerIds;
  if (empty.length === 0) {
    return "Analysis complete.";
  }
  const noun = empty.length === 1 ? "layer" : "layers";
  return `Analysis complete; ${empty.length} ${noun} returned no annotations: ${empty.join(", ")}.`;
}

/** Builds non-overlapping text segments carrying every positional annotation that covers them. */
export function combinedAnnotationSegments(shape: DocumentShapeView): CombinedAnnotationSegment[] {
  interface OrderedEntry extends AnnotationEntry {
    order: number;
  }
  interface BoundaryEvents {
    starts: OrderedEntry[];
    ends: OrderedEntry[];
  }
  const events = new Map<number, BoundaryEvents>();
  let order = 0;
  for (const layer of shape.layers) {
    for (const annotation of layer.annotations) {
      if (hasUsableSpan(annotation, shape.rawText.length)) {
        const entry = { layer, annotation, order: order++ };
        boundaryEvents(events, annotation.start).starts.push(entry);
        boundaryEvents(events, annotation.end).ends.push(entry);
      }
    }
  }
  const boundaries = [...events.keys()].sort((left, right) => left - right);
  const active = new Map<number, OrderedEntry>();
  const segments: CombinedAnnotationSegment[] = [];
  for (let index = 0; index + 1 < boundaries.length; index++) {
    const start = boundaries[index]!;
    const end = boundaries[index + 1]!;
    const boundary = events.get(start)!;
    for (const entry of boundary.ends) {
      active.delete(entry.order);
    }
    for (const entry of boundary.starts) {
      active.set(entry.order, entry);
    }
    if (end <= start) {
      continue;
    }
    if (active.size > 0) {
      const entries = [...active.values()]
        .sort((left, right) => left.order - right.order)
        .map(({ layer, annotation }) => ({ layer, annotation }));
      segments.push({ start, end, entries });
    }
  }
  return segments;
}

function boundaryEvents(
  events: Map<number, { starts: Array<AnnotationEntry & { order: number }>;
    ends: Array<AnnotationEntry & { order: number }> }>,
  offset: number,
): { starts: Array<AnnotationEntry & { order: number }>;
  ends: Array<AnnotationEntry & { order: number }> } {
  let result = events.get(offset);
  if (!result) {
    result = { starts: [], ends: [] };
    events.set(offset, result);
  }
  return result;
}

/** Returns document-scoped annotations that cannot be projected onto a text span. */
export function documentScopedAnnotations(shape: DocumentShapeView): AnnotationEntry[] {
  return shape.layers.flatMap((layer) => layer.annotations.flatMap((annotation) =>
    layer.scope === "LAYER_SCOPE_POSITIONAL"
      || hasUsableSpan(annotation, shape.rawText.length) ? [] : [{ layer, annotation }]));
}

export interface DocumentAnnotationChip extends AnnotationEntry {
  /** Document-scoped annotations this chip stands for; above 1 only for collapsed category layers. */
  totalCount: number;
}

/**
 * Builds the document-wide chip list: category layers with several
 * document-scoped predictions (sentiment, document categories) collapse into
 * one chip carrying the most confident label, while every other layer keeps
 * one chip per annotation.
 */
export function documentAnnotationChips(shape: DocumentShapeView): DocumentAnnotationChip[] {
  const chips: DocumentAnnotationChip[] = [];
  const collapsed = new Map<string, DocumentAnnotationChip>();
  for (const { layer, annotation } of documentScopedAnnotations(shape)) {
    if (layer.valueType !== "Category") {
      chips.push({ layer, annotation, totalCount: 1 });
      continue;
    }
    const existing = collapsed.get(layer.id);
    if (!existing) {
      const chip = { layer, annotation, totalCount: 1 };
      collapsed.set(layer.id, chip);
      chips.push(chip);
      continue;
    }
    existing.totalCount++;
    if (annotationConfidence(annotation) > annotationConfidence(existing.annotation)) {
      existing.annotation = annotation;
    }
  }
  return chips;
}

/** Returns an annotation's confidence: its probability, else its score, else 0. */
export function annotationConfidence(annotation: AnnotationView): number {
  return annotation.probability ?? annotation.score ?? 0;
}

/**
 * Decides whether a layer belongs to the calm first-run overlay: entities and
 * sentences only, so a fresh analysis does not open with every token boxed.
 */
export function isDefaultOverlayLayer(layer: AnnotationLayerView): boolean {
  const identity = asciiLowerCase(`${layer.id} ${layer.standardIdentity ?? ""}`);
  return identity.includes("entit") || identity.includes("sentence");
}

export function layerAccent(layer: AnnotationLayerView): LayerAccent {
  const identity = asciiLowerCase(`${layer.id} ${layer.standardIdentity ?? ""}`);
  if (identity.includes("entit") || identity.includes("geo")) {
    return "violet";
  }
  if (identity.includes("embed") || identity.includes("chunk-group")) {
    return "rose";
  }
  if (identity.includes("sentiment") || identity.includes("language") || identity.includes("categor")) {
    return "green";
  }
  if (identity.includes("pos") || identity.includes("parse") || identity.includes("syntactic")) {
    return "amber";
  }
  if (identity.includes("token") || identity.includes("sentence") || identity.includes("subword")) {
    return "cyan";
  }
  return "blue";
}

function readLayer(
  layer: Record<string, unknown>,
  rawText: string,
  offsetEncoding: string,
): AnnotationLayerView {
  const id = stringValue(layer.id) || "unnamed-layer";
  const valueEntry = [...VALUE_TYPES].find(([key]) => record(layer[key]) !== undefined);
  const valueContainer = valueEntry ? record(layer[valueEntry[0]]) : undefined;
  const values = Array.isArray(valueContainer?.annotations) ? valueContainer.annotations : [];
  const identity = record(layer.identity);

  return {
    id,
    title: layerTitle(id, optionalString(identity?.standard), optionalString(identity?.qualifier)),
    scope: stringValue(layer.scope),
    valueType: valueEntry?.[1] ?? "Unknown",
    standardIdentity: optionalString(identity?.standard),
    qualifier: optionalString(identity?.qualifier),
    annotations: values.flatMap((value, index) => {
      const annotation = record(value);
      return annotation ? [readAnnotation(annotation, index, rawText, offsetEncoding)] : [];
    }),
  };
}

function readAnnotation(
  annotation: Record<string, unknown>,
  index: number,
  rawText: string,
  offsetEncoding: string,
): AnnotationView {
  const span = record(annotation.span) ?? record(annotation.annotationSpan);
  const start = span ? numberValue(span.start) ?? 0 : undefined;
  const end = span ? numberValue(span.end) ?? 0 : undefined;
  const convertedStart = start === undefined ? undefined : toBrowserOffset(rawText, start, offsetEncoding);
  const convertedEnd = end === undefined ? undefined : toBrowserOffset(rawText, end, offsetEncoding);

  return {
    start: convertedStart,
    end: convertedEnd,
    label: annotationLabel(annotation, index),
    probability: numberValue(annotation.probability) ?? numberValue(span?.probability),
    score: numberValue(annotation.score),
    source: annotation,
  };
}

function annotationLabel(annotation: Record<string, unknown>, index: number): string {
  for (const key of [
    "value", "label", "text", "stem", "term", "piece", "chunkTag", "entityType", "type",
    "resultSetName", "groupId", "lexiconId", "algorithm", "relation",
  ]) {
    const value = optionalString(annotation[key]);
    if (value) {
      return value;
    }
  }
  const modelId = optionalString(annotation.modelId);
  const vector = Array.isArray(annotation.vector) ? annotation.vector : [];
  if (modelId || vector.length > 0) {
    return `${modelId || "Embedding"} (${vector.length} dimensions)`;
  }
  const resolutionName = optionalString(record(annotation.resolution)?.name);
  if (resolutionName) {
    return resolutionName;
  }
  return `Annotation ${index + 1}`;
}

const STANDARD_LAYER_PREFIX = "STANDARD_LAYER_";

/** Titles for standard layers whose words do not read well when merely lower-cased. */
const STANDARD_LAYER_TITLES: Record<string, string> = {
  POS_TAGS: "POS tags",
  SYNTACTIC_CHUNKS: "Phrase chunks",
  GEO: "Geocoding",
};

/**
 * Titles a layer from its declared standard identity, so "opennlp:pos" reads "POS tags" and
 * the four term profiles read "Terms (stem)" rather than colliding with the stemmer's
 * "Stems"; a layer without a standard identity is titled from its id.
 */
function layerTitle(id: string, standard?: string, qualifier?: string): string {
  const standardName = standard?.startsWith(STANDARD_LAYER_PREFIX)
    ? standard.slice(STANDARD_LAYER_PREFIX.length)
    : undefined;
  if (standardName && standardName !== "UNSPECIFIED") {
    const title = STANDARD_LAYER_TITLES[standardName] ?? sentenceCase(standardName);
    return qualifier ? `${title} (${asciiLowerCase(qualifier)})` : title;
  }
  return titleFromId(id);
}

/** "TERM_VECTORS" reads "Term vectors". */
function sentenceCase(enumWords: string): string {
  const words = splitOnCharacters(enumWords, "_").map(asciiLowerCase).join(" ");
  return `${asciiUpperCase(words.charAt(0))}${words.slice(1)}`;
}

function titleFromId(id: string): string {
  const localName = id.includes(":") ? id.slice(id.lastIndexOf(":") + 1) : id;
  return splitOnCharacters(localName, "-_")
    .map((part) => part.length <= 3 && part === asciiUpperCase(part)
      // Short all-caps parts are acronyms (NFC, UD) and keep their casing.
      ? part
      : `${asciiUpperCase(part.charAt(0))}${asciiLowerCase(part.slice(1))}`)
    .join(" ") || id;
}

function offsetEncodingLabel(encoding: string): string {
  switch (encoding) {
    case "OFFSET_ENCODING_UTF16_CODE_UNIT":
      return "UTF-16";
    case "OFFSET_ENCODING_UNICODE_CODE_POINT":
      return "Unicode code points";
    case "OFFSET_ENCODING_UTF8_BYTE":
    case "OFFSET_ENCODING_UNSPECIFIED":
      return "UTF-8 bytes";
    default:
      return encoding || "Not reported";
  }
}

function record(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function optionalString(value: unknown): string | undefined {
  const result = stringValue(value).trim();
  return result || undefined;
}

function numberValue(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function hasUsableSpan(annotation: AnnotationView, textLength: number): annotation is AnnotationView & {
  start: number;
  end: number;
} {
  return annotation.start !== undefined && annotation.end !== undefined
    && annotation.start >= 0 && annotation.end > annotation.start && annotation.end <= textLength;
}
