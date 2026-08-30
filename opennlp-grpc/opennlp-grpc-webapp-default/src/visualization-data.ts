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

import type { AnnotationView, DocumentShapeView } from "./document-shape";
import { asciiLowerCase, collapseWhitespace, ellipsizeCodePoints } from "./text-utils";

export interface HeatmapRow {
  start: number;
  end: number;
  label: string;
  score: number;
  category?: string;
  modelId?: string;
}

export interface HeatmapRows {
  semantic: HeatmapRow[];
  sentiment: HeatmapRow[];
}

export interface SimilarityHeatmapHit {
  sourceText: string;
  offsetEncoding: string;
  start: number;
  end: number;
  indexedChunkText: string;
  score: number;
  modelId: string;
}

export interface DocumentGraphNode {
  id: string;
  label: string;
  kind: "document" | "layer" | "annotation";
  layerId?: string;
  annotationIndex?: number;
  start?: number;
  end?: number;
}

export interface DocumentGraphLink {
  source: string;
  target: string;
}

export interface DocumentGraph {
  nodes: DocumentGraphNode[];
  links: DocumentGraphLink[];
  truncated: boolean;
}

export function buildHeatmapRows(shape: DocumentShapeView): HeatmapRows {
  const sentiment = shape.layers
    .filter((layer) => layer.valueType === "Category" && isSentimentLayer(layer.id, layer.standardIdentity))
    .flatMap((layer) => layer.annotations)
    .filter(hasAnnotationSpan)
    .flatMap((annotation) => annotation.score === undefined ? [] : [{
      start: annotation.start!,
      end: annotation.end!,
      label: shape.rawText.slice(annotation.start, annotation.end),
      score: signedSentimentScore(annotation.label, annotation.score),
      category: annotation.label,
    }]);

  return { semantic: [], sentiment };
}

/** Converts server-ranked chunks for one browser document into heatmap rows. */
export function buildSimilarityHeatmapRows(
  sourceText: string,
  hits: SimilarityHeatmapHit[],
): HeatmapRow[] {
  return hits.filter((hit) => hit.sourceText === sourceText
      && hit.offsetEncoding === "OFFSET_ENCODING_UTF16_CODE_UNIT")
    .map((hit) => ({
      start: hit.start,
      end: hit.end,
      label: hit.indexedChunkText,
      score: hit.score,
      modelId: hit.modelId,
    }));
}

export function buildDocumentGraph(shape: DocumentShapeView, maxAnnotations = 120): DocumentGraph {
  const nodes: DocumentGraphNode[] = [{
    id: "document",
    label: shape.rawText ? preview(shape.rawText, 36) : "Document",
    kind: "document",
  }];
  const links: DocumentGraphLink[] = [];
  let annotationCount = 0;
  let availableAnnotations = 0;

  for (const layer of shape.layers) {
    const layerId = `layer:${layer.id}`;
    nodes.push({ id: layerId, label: layer.title, kind: "layer", layerId: layer.id });
    links.push({ source: "document", target: layerId });
    availableAnnotations += layer.annotations.length;
  }

  const budget = Math.max(0, Math.floor(maxAnnotations));
  const nextIndexes = shape.layers.map(() => 0);
  while (annotationCount < budget) {
    let progressed = false;
    for (const [layerIndex, layer] of shape.layers.entries()) {
      if (annotationCount >= budget) {
        break;
      }
      const index = nextIndexes[layerIndex]!;
      const annotation = layer.annotations[index];
      if (!annotation) {
        continue;
      }
      nextIndexes[layerIndex] = index + 1;
      progressed = true;
      const layerId = `layer:${layer.id}`;
      const annotationId = `${layerId}:annotation:${index}`;
      nodes.push({
        id: annotationId,
        label: preview(annotation.label, 30),
        kind: "annotation",
        layerId: layer.id,
        annotationIndex: index,
        start: annotation.start,
        end: annotation.end,
      });
      links.push({ source: layerId, target: annotationId });
      annotationCount++;
    }
    if (!progressed) {
      break;
    }
  }

  return { nodes, links, truncated: availableAnnotations > annotationCount };
}

function hasAnnotationSpan(annotation: AnnotationView): boolean {
  return annotation.start !== undefined && annotation.end !== undefined && annotation.end > annotation.start;
}

function isSentimentLayer(id: string, standardIdentity: string | undefined): boolean {
  return asciiLowerCase(`${id} ${standardIdentity ?? ""}`).includes("sentiment");
}

/**
 * Maps a categorizer label and its confidence to a signed polarity in [-1, 1].
 *
 * Polar labels (containing "negative", "neutral" or "positive") keep the confidence as the
 * magnitude. Ordinal star labels such as "1_star" or "5 stars" place the rank on a five-point
 * scale, so one star is fully negative, three stars neutral and five stars fully positive,
 * scaled by the confidence. A label of unknown shape scores 0: the confidence alone says
 * nothing about direction, and rendering it as polarity painted the most negative sentences
 * green.
 */
function signedSentimentScore(label: string, score: number): number {
  const category = asciiLowerCase(label);
  const magnitude = Math.min(1, Math.abs(score));
  if (category.includes("negative")) {
    return -magnitude;
  }
  if (category.includes("neutral")) {
    return 0;
  }
  if (category.includes("positive")) {
    return magnitude;
  }
  const stars = starRank(category);
  if (stars !== undefined) {
    return ((stars - STAR_SCALE_MIDPOINT) / STAR_SCALE_MIDPOINT_DISTANCE) * magnitude;
  }
  return 0;
}

const STAR_SCALE_MIN = 1;
const STAR_SCALE_MAX = 5;
const STAR_SCALE_MIDPOINT = 3;
const STAR_SCALE_MIDPOINT_DISTANCE = STAR_SCALE_MAX - STAR_SCALE_MIDPOINT;

/**
 * Reads the rank out of a lower-cased ordinal label like "1_star", "5 stars" or "2-stars":
 * one to five ASCII digits, an optional separator, then the word "star". Returns undefined
 * for any other shape.
 */
function starRank(category: string): number | undefined {
  let index = 0;
  while (index < category.length && category.charCodeAt(index) === SPACE_CODE) {
    index++;
  }
  const digitStart = index;
  while (index < category.length && isAsciiDigit(category.charCodeAt(index))) {
    index++;
  }
  if (index === digitStart) {
    return undefined;
  }
  const rank = Number(category.slice(digitStart, index));
  if (index < category.length && isSeparator(category.charCodeAt(index))) {
    index++;
  }
  if (!category.startsWith("star", index)) {
    return undefined;
  }
  return rank >= STAR_SCALE_MIN && rank <= STAR_SCALE_MAX ? rank : undefined;
}

const SPACE_CODE = 0x20;

function isAsciiDigit(code: number): boolean {
  return code >= 0x30 && code <= 0x39;
}

function isSeparator(code: number): boolean {
  return code === SPACE_CODE || code === 0x5f || code === 0x2d;
}

function preview(value: string, limit: number): string {
  return ellipsizeCodePoints(collapseWhitespace(value), limit);
}
