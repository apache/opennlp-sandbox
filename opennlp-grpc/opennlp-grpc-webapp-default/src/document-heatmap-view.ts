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

import type { SearchHit } from "./search-adapter";
import { scoreColor } from "./search-view-model";

export interface DocumentHeatmapChunk {
  id: string;
  start: number;
  end: number;
  text: string;
  score?: number;
  hit?: SearchHit;
}

export interface DocumentHeatmapLane {
  id: string;
  title: string;
  complete: boolean;
  /** What a chunk's score measures, shown beside the number: "cosine" or "polarity". */
  scoreLabel: string;
  chunks: DocumentHeatmapChunk[];
}

export type DocumentHeatmapSelection = (
  chunk: DocumentHeatmapChunk,
  trigger: HTMLElement,
) => void;

/** Renders continuous source text and an exact selectable list for every chunk projection. */
export function renderDocumentHeatmap(
  container: HTMLElement,
  sourceText: string,
  lanes: DocumentHeatmapLane[],
  select: DocumentHeatmapSelection,
): void {
  container.replaceChildren(...lanes.map((lane) => renderLane(sourceText, lane, select)));
}

function renderLane(
  sourceText: string,
  lane: DocumentHeatmapLane,
  select: DocumentHeatmapSelection,
): HTMLElement {
  const section = document.createElement("section");
  section.className = "document-heat-lane";
  const heading = document.createElement("h4");
  heading.textContent = lane.title;
  const scored = lane.chunks.reduce((count, chunk) => count + (chunk.score === undefined ? 0 : 1), 0);
  const coverage = document.createElement("p");
  coverage.className = "heat-coverage-note";
  coverage.textContent = `${scored} of ${lane.chunks.length} chunks scored. `
    + (lane.complete ? "Complete server result." : "Coverage is partial; unreturned chunks remain gray.");
  const source = document.createElement("div");
  source.className = "heat-source";
  source.append(...sourceSegments(sourceText, lane, select));
  const cards = document.createElement("div");
  cards.className = "heat-chunk-list";
  cards.append(...lane.chunks.map((chunk, index) => chunkCard(chunk, index, lane.scoreLabel, select)));
  section.append(heading, coverage, source, cards);
  return section;
}

function sourceSegments(
  sourceText: string,
  lane: DocumentHeatmapLane,
  select: DocumentHeatmapSelection,
): HTMLElement[] {
  const chunks = lane.chunks;
  const boundaries = new Set<number>([0, sourceText.length]);
  for (const chunk of chunks) {
    if (validSpan(chunk, sourceText.length)) {
      boundaries.add(chunk.start);
      boundaries.add(chunk.end);
    }
  }
  const ordered = [...boundaries].sort((left, right) => left - right);
  const elements: HTMLElement[] = [];
  for (let index = 1; index < ordered.length; index++) {
    const start = ordered[index - 1] ?? 0;
    const end = ordered[index] ?? start;
    const selected = strongestCoveringChunk(chunks, start, end);
    const segment = selected?.score === undefined
      ? document.createElement("span")
      : document.createElement("button");
    segment.className = selected?.score === undefined ? "heat-source-segment is-unscored" : "heat-source-segment";
    segment.textContent = sourceText.slice(start, end);
    if (segment instanceof HTMLButtonElement && selected) {
      segment.type = "button";
      applyScore(segment, selected.score);
      segment.title = `${selected.id}: ${lane.scoreLabel} ${selected.score?.toFixed(4)}`;
      segment.addEventListener("click", () => select(selected, segment));
    }
    elements.push(segment);
  }
  return elements;
}

function chunkCard(
  chunk: DocumentHeatmapChunk,
  index: number,
  scoreLabel: string,
  select: DocumentHeatmapSelection,
): HTMLButtonElement {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "heat-chunk-card";
  if (chunk.score === undefined) {
    button.classList.add("is-unscored");
  } else {
    applyScore(button, chunk.score);
  }
  const label = document.createElement("strong");
  label.textContent = `Chunk ${index + 1}`;
  const score = document.createElement("span");
  score.textContent = chunk.score === undefined
    ? "Not scored"
    : `${capitalize(scoreLabel)} ${chunk.score.toFixed(4)}`;
  const text = document.createElement("span");
  text.className = "heat-chunk-text";
  text.textContent = chunk.text;
  button.append(label, score, text);
  button.addEventListener("click", () => select(chunk, button));
  return button;
}

function strongestCoveringChunk(
  chunks: DocumentHeatmapChunk[],
  start: number,
  end: number,
): DocumentHeatmapChunk | undefined {
  let selected: DocumentHeatmapChunk | undefined;
  for (const chunk of chunks) {
    if (chunk.score === undefined || chunk.start > start || chunk.end < end) {
      continue;
    }
    if (!selected || Math.abs(chunk.score) > Math.abs(selected.score ?? 0)) {
      selected = chunk;
    }
  }
  return selected;
}

function validSpan(chunk: DocumentHeatmapChunk, sourceLength: number): boolean {
  return Number.isSafeInteger(chunk.start) && Number.isSafeInteger(chunk.end)
    && chunk.start >= 0 && chunk.end > chunk.start && chunk.end <= sourceLength;
}

function applyScore(element: HTMLElement, value: number | undefined): void {
  if (value === undefined) {
    return;
  }
  const color = scoreColor(value);
  element.style.backgroundColor = color.background;
  element.style.color = color.foreground;
}

/** Upper-cases the first ASCII letter of a score label for the card text. */
function capitalize(label: string): string {
  return label.length === 0 ? label : label.charAt(0).toUpperCase() + label.slice(1);
}
