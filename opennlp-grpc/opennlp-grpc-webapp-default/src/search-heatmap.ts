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

import { toBrowserSpan } from "./offsets";
import type { MatchedSpan, SearchHit } from "./search-adapter";

/** One run of source text: a scored chunk span, or an unscored gap between chunks. */
export interface HeatSegment {
  text: string;
  /** Cosine score of the covering chunk; absent on gaps. */
  score?: number;
  /** Chunk identity of a scored segment; absent on gaps. */
  chunkId?: string;
  /** Hit id of a scored segment, for selection; absent on gaps. */
  hitId?: string;
  /** Keyword matches relative to this segment's text; empty when unmappable. */
  matchedSpans: MatchedSpan[];
}

/** One document's chunks laid over its full source text for heat rendering. */
export interface DocumentHeat {
  documentId: string;
  /** Best chunk score in this document. */
  maxScore: number;
  /** Chunks rendered as scored segments. */
  chunkCount: number;
  segments: HeatSegment[];
}

/** One source-mapped chunk with browser-unit offsets. */
interface PlacedChunk {
  hit: SearchHit;
  start: number;
  end: number;
}

/**
 * Groups scored hits by document and lays each document's chunks over its source
 * text as alternating gap and scored segments. Chunks are ordered by source
 * position; a chunk overlapping an earlier one is skipped so text never repeats.
 * Documents are ordered by their best chunk score, best first. Matched keyword
 * spans are kept only when the emitted chunk text equals the source slice, so a
 * span index is always valid inside its segment.
 */
export function buildDocumentHeat(hits: SearchHit[]): DocumentHeat[] {
  const byDocument = new Map<string, SearchHit[]>();
  for (const hit of hits) {
    const grouped = byDocument.get(hit.documentId);
    if (grouped) {
      grouped.push(hit);
    } else {
      byDocument.set(hit.documentId, [hit]);
    }
  }
  return [...byDocument.entries()]
    .flatMap(([documentId, grouped]) => {
      const heat = documentHeat(documentId, grouped);
      return heat ? [heat] : [];
    })
    .sort((left, right) => right.maxScore - left.maxScore
      || left.documentId.localeCompare(right.documentId));
}

/** Builds one document's heat, or nothing when no chunk maps onto its source. */
function documentHeat(documentId: string, hits: SearchHit[]): DocumentHeat | undefined {
  const sourceText = hits[0]?.sourceText ?? "";
  const placed = hits.flatMap((hit): PlacedChunk[] => {
    if (hit.sourceText !== sourceText) {
      return [];
    }
    const span = toBrowserSpan(sourceText, hit.start, hit.end, hit.offsetEncoding);
    return span ? [{ hit, start: span.start, end: span.end }] : [];
  }).sort((left, right) => left.start - right.start || left.end - right.end);
  const segments: HeatSegment[] = [];
  let maxScore = Number.NEGATIVE_INFINITY;
  let chunkCount = 0;
  let cursor = 0;
  for (const chunk of placed) {
    if (chunk.start < cursor) {
      continue;
    }
    if (chunk.start > cursor) {
      segments.push({ text: sourceText.slice(cursor, chunk.start), matchedSpans: [] });
    }
    const text = sourceText.slice(chunk.start, chunk.end);
    segments.push({
      text,
      score: chunk.hit.score,
      chunkId: chunk.hit.chunkId,
      hitId: chunk.hit.id,
      matchedSpans: chunk.hit.emittedChunkText === text ? chunk.hit.matchedSpans : [],
    });
    maxScore = Math.max(maxScore, chunk.hit.score);
    chunkCount++;
    cursor = chunk.end;
  }
  if (chunkCount === 0) {
    return undefined;
  }
  if (cursor < sourceText.length) {
    segments.push({ text: sourceText.slice(cursor), matchedSpans: [] });
  }
  return { documentId, maxScore, chunkCount, segments };
}
