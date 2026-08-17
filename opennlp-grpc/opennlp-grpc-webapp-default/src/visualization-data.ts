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
  emittedChunkText: string;
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
      label: hit.emittedChunkText,
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

    for (const [index, annotation] of layer.annotations.entries()) {
      if (annotationCount >= maxAnnotations) {
        continue;
      }
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
  }

  return { nodes, links, truncated: availableAnnotations > annotationCount };
}

function hasAnnotationSpan(annotation: AnnotationView): boolean {
  return annotation.start !== undefined && annotation.end !== undefined && annotation.end > annotation.start;
}

function isSentimentLayer(id: string, standardIdentity: string | undefined): boolean {
  return asciiLowerCase(`${id} ${standardIdentity ?? ""}`).includes("sentiment");
}

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
  return Math.max(-1, Math.min(1, score));
}

function preview(value: string, limit: number): string {
  return ellipsizeCodePoints(collapseWhitespace(value), limit);
}
