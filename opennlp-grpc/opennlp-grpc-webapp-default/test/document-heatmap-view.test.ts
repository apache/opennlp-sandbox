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

/** @vitest-environment jsdom */

import { describe, expect, it, vi } from "vitest";

import {
  renderDocumentHeatmap,
  type DocumentHeatmapLane,
} from "../src/document-heatmap-view";

const TEXT = "Alice followed the White Rabbit into the garden.";

function lanes(): DocumentHeatmapLane[] {
  return [{
    id: "token-chunks",
    title: "Token windows",
    complete: true,
    scoreLabel: "cosine",
    chunks: [
      { id: "window-1", start: 0, end: 31, text: TEXT.slice(0, 31), score: 0.8 },
      { id: "window-2", start: 19, end: TEXT.length, text: TEXT.slice(19), score: 0.2 },
    ],
  }];
}

describe("inline document heatmap", () => {
  it("keeps overlapping chunks individually selectable without repeating source text", () => {
    const container = document.createElement("div");
    const selected = vi.fn();

    renderDocumentHeatmap(container, TEXT, lanes(), selected);

    expect(container.querySelector(".heat-source")?.textContent).toBe(TEXT);
    const chunks = container.querySelectorAll<HTMLButtonElement>(".heat-chunk-card");
    expect(chunks).toHaveLength(2);
    chunks[1]?.click();
    expect(selected).toHaveBeenCalledWith(
      expect.objectContaining({ id: "window-2", score: 0.2 }),
      chunks[1],
    );
  });

  it("renders unreturned chunks gray and reports partial coverage", () => {
    const container = document.createElement("div");
    const partial: DocumentHeatmapLane[] = [{
      id: "sentences",
      title: "Sentences",
      complete: false,
      scoreLabel: "cosine",
      chunks: [
        { id: "sentence-1", start: 0, end: 18, text: TEXT.slice(0, 18), score: 0.6 },
        { id: "sentence-2", start: 19, end: TEXT.length, text: TEXT.slice(19) },
      ],
    }];

    renderDocumentHeatmap(container, TEXT, partial, vi.fn());

    expect(container.textContent).toContain("1 of 2 chunks scored");
    expect(container.querySelectorAll(".heat-chunk-card.is-unscored")).toHaveLength(1);
    expect(container.querySelector(".heat-coverage-note")?.textContent).toContain("partial");
  });

  it("renders separate projection lanes with their own coverage", () => {
    const container = document.createElement("div");
    const sentence = { ...lanes()[0]!, id: "sentences", title: "Sentences" };

    renderDocumentHeatmap(container, TEXT, [sentence, ...lanes()], vi.fn());

    expect(Array.from(container.querySelectorAll(".document-heat-lane h4"))
      .map((heading) => heading.textContent)).toEqual(["Sentences", "Token windows"]);
  });

  it("labels each lane's scores with what they measure", () => {
    const container = document.createElement("div");
    const polarity: DocumentHeatmapLane[] = [{
      id: "sentiment",
      title: "Sentence sentiment",
      complete: true,
      scoreLabel: "polarity",
      chunks: [
        { id: "sentiment:0", start: 0, end: 18, text: TEXT.slice(0, 18), score: -0.884 },
        { id: "sentiment:1", start: 19, end: TEXT.length, text: TEXT.slice(19) },
      ],
    }];

    renderDocumentHeatmap(container, TEXT, polarity, () => undefined);

    const cards = Array.from(container.querySelectorAll(".heat-chunk-card"));
    expect(cards[0]?.textContent).toContain("Polarity -0.8840");
    expect(cards[1]?.textContent).toContain("Not scored");
    expect(container.textContent).not.toContain("Cosine");
    const segment = container.querySelector<HTMLButtonElement>(".heat-source-segment");
    expect(segment?.title).toBe("sentiment:0: polarity -0.8840");
  });
});
