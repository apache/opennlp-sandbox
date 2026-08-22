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

import type { AnnotationLayerView, AnnotationView, DocumentShapeView } from "./document-shape";
import { toBrowserSpan } from "./offsets";
import type { SearchHit } from "./search-adapter";
import { asciiLowerCase } from "./text-utils";

export interface ScoreColor {
  background: string;
  foreground: string;
}

export interface SourceHighlight {
  before: string;
  selected: string;
  after: string;
}

export interface ChunkComparison {
  exact: boolean;
  original: string;
  indexed: string;
}

export interface DocumentAnalytics {
  sentences: number;
  tokens: number;
  entities: number;
  chunks: number;
  terms: number;
}

export interface IntersectingAnnotation {
  layer: AnnotationLayerView;
  annotation: AnnotationView;
}

const NEGATIVE = [180, 35, 24] as const;
const NEUTRAL = [229, 231, 235] as const;
const POSITIVE = [22, 131, 90] as const;

export function scoreColor(value: number): ScoreColor {
  const score = Math.max(-1, Math.min(1, Number.isFinite(value) ? value : 0));
  if (score === -1) {
    return { background: "#b42318", foreground: "#ffffff" };
  }
  if (score === 0) {
    return { background: "#e5e7eb", foreground: "#111827" };
  }
  if (score === 1) {
    return { background: "#16835a", foreground: "#ffffff" };
  }
  const color = score < 0
    ? interpolate(NEGATIVE, NEUTRAL, score + 1)
    : interpolate(NEUTRAL, POSITIVE, score);
  return { background: toHex(color), foreground: relativeLuminance(color) < 0.179 ? "#ffffff" : "#111827" };
}

export function sourceHighlight(hit: SearchHit): SourceHighlight {
  const span = toBrowserSpan(hit.sourceText, hit.start, hit.end, hit.offsetEncoding);
  if (!span) {
    return { before: hit.sourceText, selected: "", after: "" };
  }
  return {
    before: hit.sourceText.slice(0, span.start),
    selected: hit.sourceText.slice(span.start, span.end),
    after: hit.sourceText.slice(span.end),
  };
}

export function compareChunkText(original: string, indexed: string): ChunkComparison {
  return { exact: original === indexed, original, indexed };
}

export function searchResultStatus(hitCount: number, truncated: boolean): string {
  const summary = hitCount === 0
    ? "No scored chunks were returned."
    : `${hitCount} scored ${hitCount === 1 ? "chunk" : "chunks"} returned.`;
  return truncated
    ? `${summary} The server response byte limit truncated additional matches.`
    : summary;
}

export function documentAnalytics(shape: DocumentShapeView): DocumentAnalytics {
  return {
    sentences: annotationCount(shape, (identity) => identity.includes("sentence")),
    tokens: annotationCount(shape, (identity) => identity.includes("token") && !identity.includes("subword")),
    entities: annotationCount(shape, (identity) => identity.includes("entit")),
    chunks: annotationCount(shape, (identity) => identity.includes("chunk")),
    terms: annotationCount(shape, (identity) => identity.includes("term")),
  };
}

export function annotationsIntersecting(
  shape: DocumentShapeView,
  start: number,
  end: number,
): IntersectingAnnotation[] {
  return shape.layers.flatMap((layer) => layer.annotations.flatMap((annotation) => {
    if (annotation.start === undefined || annotation.end === undefined
        || annotation.end <= start || annotation.start >= end) {
      return [];
    }
    return [{ layer, annotation }];
  }));
}

export function hitAnnotations(shape: DocumentShapeView, hit: SearchHit): IntersectingAnnotation[] {
  const span = toBrowserSpan(hit.sourceText, hit.start, hit.end, hit.offsetEncoding);
  return span ? annotationsIntersecting(shape, span.start, span.end) : [];
}

/** One rendered segment of indexed text: plain, or a keyword match. */
export interface MatchedSegment {
  text: string;
  matched: boolean;
  /** The analyzed query term behind a matched segment. */
  term?: string;
}

/**
 * Splits indexed chunk text into plain and matched segments for highlighting.
 * Spans are sorted by start; a span overlapping an earlier one is skipped so
 * segments never double-render text.
 */
export function matchedSegments(
  hit: Pick<SearchHit, "indexedChunkText" | "matchedSpans">,
): MatchedSegment[] {
  const indexed = hit.indexedChunkText;
  if (hit.matchedSpans.length === 0) {
    return indexed ? [{ text: indexed, matched: false }] : [];
  }
  const ordered = [...hit.matchedSpans].sort((left, right) =>
    left.start - right.start || left.end - right.end);
  const segments: MatchedSegment[] = [];
  let cursor = 0;
  for (const span of ordered) {
    if (span.start < cursor) {
      continue;
    }
    if (span.start > cursor) {
      segments.push({ text: indexed.slice(cursor, span.start), matched: false });
    }
    segments.push({ text: indexed.slice(span.start, span.end), matched: true, term: span.term });
    cursor = span.end;
  }
  if (cursor < indexed.length) {
    segments.push({ text: indexed.slice(cursor), matched: false });
  }
  return segments;
}

export class SearchSelection {
  selectedId?: string;

  select(hits: SearchHit[], id: string): SearchHit | undefined {
    const selected = hits.find((hit) => hit.id === id);
    if (selected) {
      this.selectedId = selected.id;
    }
    return selected;
  }
}

function annotationCount(shape: DocumentShapeView, match: (identity: string) => boolean): number {
  return shape.layers.reduce((count, layer) => {
    const identity = asciiLowerCase(`${layer.id} ${layer.standardIdentity ?? ""} ${layer.valueType}`);
    return count + (match(identity) ? layer.annotations.length : 0);
  }, 0);
}

function interpolate(start: readonly number[], end: readonly number[], amount: number): number[] {
  return start.map((channel, index) => Math.round(channel + ((end[index] ?? channel) - channel) * amount));
}

function toHex(color: number[]): string {
  return `#${color.map((channel) => channel.toString(16).padStart(2, "0")).join("")}`;
}

function relativeLuminance(color: number[]): number {
  const [red = 0, green = 0, blue = 0] = color.map((channel) => {
    const value = channel / 255;
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}
