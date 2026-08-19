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

import type { AnnotationLayerView, AnnotationView, LayerAccent } from "./document-shape";

/** One distinct term with its document frequency and share of all occurrences. */
export interface TermVectorSegment {
  term: string;
  frequency: number;
  share: number;
  annotation: AnnotationView;
}

/** All term vectors of one layer combined into one ranked, bounded stack. */
export interface TermVectorStack {
  /** The highest-frequency terms, at most the requested segment count. */
  segments: TermVectorSegment[];
  /** Distinct terms overall, including any folded into the remainder. */
  termCount: number;
  /** Occurrences overall, the sum of every term's frequency. */
  totalFrequency: number;
  /** Distinct terms folded into the remainder segment. */
  otherTermCount: number;
  /** Occurrences folded into the remainder segment. */
  otherFrequency: number;
}

/** The default number of stacked segments before the tail folds into one remainder. */
export const DEFAULT_STACK_SEGMENTS = 12;

export function isTermVectorLayer(layer: AnnotationLayerView): boolean {
  return layer.valueType === "Term vector";
}

/**
 * Ranks a term-vector layer's annotations by frequency, highest first; ties keep the
 * layer's first-occurrence order. A missing or invalid frequency counts as one
 * occurrence, the smallest value the server emits.
 */
export function rankedTermVectors(layer: AnnotationLayerView): TermVectorSegment[] {
  const totals = layer.annotations.map((annotation) => ({
    term: annotation.label,
    frequency: frequencyOf(annotation),
    share: 0,
    annotation,
  }));
  const totalFrequency = totals.reduce((sum, segment) => sum + segment.frequency, 0);
  return totals
    .map((segment) => ({
      ...segment,
      share: totalFrequency === 0 ? 0 : segment.frequency / totalFrequency,
    }))
    .sort((left, right) => right.frequency - left.frequency);
}

/** Combines a term-vector layer into one bounded stack, folding the tail into a remainder. */
export function buildTermVectorStack(
  layer: AnnotationLayerView,
  maxSegments = DEFAULT_STACK_SEGMENTS,
): TermVectorStack {
  const ranked = rankedTermVectors(layer);
  const segments = ranked.slice(0, Math.max(0, maxSegments));
  const folded = ranked.slice(segments.length);
  return {
    segments,
    termCount: ranked.length,
    totalFrequency: ranked.reduce((sum, segment) => sum + segment.frequency, 0),
    otherTermCount: folded.length,
    otherFrequency: folded.reduce((sum, segment) => sum + segment.frequency, 0),
  };
}

/**
 * Renders one term-vector layer as a single clickable stacked bar: one segment per
 * top term, sized by frequency, plus one remainder segment for the folded tail.
 * Clicking the bar invokes {@code onOpenList} with the bar itself, so the pop-out
 * can return focus to it when closed.
 */
export function renderTermVectorStack(
  layer: AnnotationLayerView,
  accent: LayerAccent,
  onOpenList: (trigger: HTMLElement) => void,
  maxSegments = DEFAULT_STACK_SEGMENTS,
): HTMLElement {
  const stack = buildTermVectorStack(layer, maxSegments);
  const section = document.createElement("section");
  section.className = "term-vector-stack";
  section.dataset.accent = accent;

  const heading = document.createElement("strong");
  heading.textContent = `${layer.title}: ${summaryText(stack)}`;

  const bar = document.createElement("button");
  bar.type = "button";
  bar.className = "term-vector-stack-bar";
  bar.setAttribute("aria-haspopup", "dialog");
  bar.setAttribute("aria-label",
    `${layer.title}, ${summaryText(stack)}. Open the ranked term list.`);
  for (const [index, segment] of stack.segments.entries()) {
    const piece = document.createElement("span");
    piece.className = "term-vector-stack-segment";
    piece.style.flexGrow = String(segment.frequency);
    piece.style.opacity = String(segmentOpacity(index));
    piece.title = `${segment.term}: ${segment.frequency}`;
    piece.textContent = segment.term;
    bar.append(piece);
  }
  if (stack.otherTermCount > 0) {
    const remainder = document.createElement("span");
    remainder.className = "term-vector-stack-segment term-vector-stack-other";
    remainder.style.flexGrow = String(stack.otherFrequency);
    remainder.title = `${stack.otherTermCount} more terms: ${stack.otherFrequency}`;
    remainder.textContent = `+${stack.otherTermCount}`;
    bar.append(remainder);
  }
  bar.addEventListener("click", () => onOpenList(bar));

  section.append(heading, bar);
  return section;
}

/** One phrase used by the heading, the bar label, and the pop-out summary. */
export function summaryText(stack: TermVectorStack): string {
  return `${stack.termCount} distinct ${stack.termCount === 1 ? "term" : "terms"}, `
    + `${stack.totalFrequency} ${stack.totalFrequency === 1 ? "occurrence" : "occurrences"}`;
}

function frequencyOf(annotation: AnnotationView): number {
  const frequency = annotation.source.frequency;
  return typeof frequency === "number" && Number.isFinite(frequency) && frequency > 0
    ? frequency
    : 1;
}

/** Grades the accent from full strength down the ranking, never below a legible floor. */
function segmentOpacity(index: number): number {
  return Math.max(0.35, 1 - index * 0.07);
}
