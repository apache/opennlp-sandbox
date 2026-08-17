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

export interface DocumentShapeSummary {
  layerCount: number;
  annotationCount: number;
  offsetEncodingLabel: string;
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
  ["syntacticChunkValues", "Syntactic chunk"],
  ["stemValues", "Stem"],
  ["lexicalExpansionValues", "Lexical expansion"],
  ["normalizationValues", "Normalization"],
  ["analyticsValues", "Analytics"],
  ["chunkGroupValues", "Chunk group"],
  ["termVectorValues", "Term vector"],
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
  };
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
    title: layerTitle(id),
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
  const span = record(annotation.span);
  const start = numberValue(span?.start);
  const end = numberValue(span?.end);
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
  for (const key of ["value", "label", "entityType", "type", "stem", "term", "piece", "chunkTag"]) {
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
  return `Annotation ${index + 1}`;
}

function layerTitle(id: string): string {
  const localName = id.includes(":") ? id.slice(id.lastIndexOf(":") + 1) : id;
  return splitOnCharacters(localName, "-_")
    .map((part) => `${asciiUpperCase(part.charAt(0))}${part.slice(1)}`)
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
