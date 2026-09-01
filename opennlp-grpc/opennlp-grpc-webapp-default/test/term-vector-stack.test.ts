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

import { beforeEach, describe, expect, it, vi } from "vitest";

import { AnnotationDrawer } from "../src/annotation-drawer";
import type { AnnotationLayerView } from "../src/document-shape";
import { readDocumentShape } from "../src/document-shape";
import {
  buildTermVectorStack,
  isTermVectorLayer,
  rankedTermVectors,
  renderTermVectorStack,
} from "../src/term-vector-stack";

function termVectorLayer(terms: Array<[string, number | undefined]>): AnnotationLayerView {
  return {
    id: "opennlp:term-vectors",
    title: "Term Vectors",
    scope: "LAYER_SCOPE_DOCUMENT",
    valueType: "Term vector",
    annotations: terms.map(([term, frequency], index) => ({
      label: term || `Annotation ${index + 1}`,
      source: frequency === undefined ? { term } : { term, frequency },
    })),
  };
}

describe("term vector stack data", () => {
  it("recognizes term vector layers by value type", () => {
    expect(isTermVectorLayer(termVectorLayer([["dog", 1]]))).toBe(true);
    expect(isTermVectorLayer({ ...termVectorLayer([["dog", 1]]), valueType: "String" })).toBe(false);
  });

  it("ranks terms by frequency and keeps first-occurrence order for ties", () => {
    const ranked = rankedTermVectors(termVectorLayer([["alpha", 2], ["beta", 5], ["gamma", 2]]));
    expect(ranked.map((segment) => segment.term)).toEqual(["beta", "alpha", "gamma"]);
    expect(ranked.map((segment) => segment.frequency)).toEqual([5, 2, 2]);
    expect(ranked.map((segment) => segment.share)).toEqual([5 / 9, 2 / 9, 2 / 9]);
  });

  it("counts a missing frequency as one occurrence", () => {
    const ranked = rankedTermVectors(termVectorLayer([["solo", undefined], ["pair", 2]]));
    expect(ranked.map((segment) => [segment.term, segment.frequency])).toEqual([["pair", 2], ["solo", 1]]);
  });

  it("folds terms beyond the segment ceiling into one remainder", () => {
    const stack = buildTermVectorStack(
      termVectorLayer([["a", 9], ["b", 7], ["c", 5], ["d", 2], ["e", 1]]), 3);
    expect(stack.segments.map((segment) => segment.term)).toEqual(["a", "b", "c"]);
    expect(stack.termCount).toBe(5);
    expect(stack.otherTermCount).toBe(2);
    expect(stack.otherFrequency).toBe(3);
    expect(stack.totalFrequency).toBe(24);
  });

  it("keeps every term when the count is exactly at the segment ceiling", () => {
    const stack = buildTermVectorStack(termVectorLayer([["a", 3], ["b", 2], ["c", 1]]), 3);
    expect(stack.segments.map((segment) => segment.term)).toEqual(["a", "b", "c"]);
    expect(stack.otherTermCount).toBe(0);
    expect(stack.otherFrequency).toBe(0);
  });

  it("reports an empty layer as an empty stack", () => {
    const stack = buildTermVectorStack(termVectorLayer([]), 3);
    expect(stack.segments).toEqual([]);
    expect(stack.termCount).toBe(0);
    expect(stack.totalFrequency).toBe(0);
  });

  it("reads the gateway JSON shape of a term vector layer", () => {
    const shape = readDocumentShape({
      document: {
        rawText: "dog dog dog cat",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: { layers: [{
          id: "opennlp:term-vectors",
          scope: "LAYER_SCOPE_DOCUMENT",
          termVectorValues: { annotations: [
            { term: "dog", frequency: 3 },
            { term: "cat", frequency: 1 },
          ] },
        }] },
      },
    });
    const layer = shape.layers[0]!;
    expect(isTermVectorLayer(layer)).toBe(true);
    const ranked = rankedTermVectors(layer);
    expect(ranked.map((segment) => [segment.term, segment.frequency])).toEqual([["dog", 3], ["cat", 1]]);
  });
});

describe("term vector stack graphic", () => {
  it("renders one clickable stacked bar sized by term frequency", () => {
    const layer = termVectorLayer([["dog", 3], ["cat", 1]]);
    const onOpenList = vi.fn();

    const section = renderTermVectorStack(layer, "blue", onOpenList);
    document.body.replaceChildren(section);

    const bar = section.querySelector<HTMLButtonElement>("button.term-vector-stack-bar");
    expect(bar).not.toBeNull();
    expect(bar!.getAttribute("aria-haspopup")).toBe("dialog");
    expect(bar!.getAttribute("aria-label")).toContain("2 distinct terms");
    expect(bar!.getAttribute("aria-label")).toContain("4 occurrences");
    const segments = [...bar!.querySelectorAll<HTMLElement>(".term-vector-stack-segment")];
    expect(segments.map((segment) => segment.style.flexGrow)).toEqual(["3", "1"]);
    expect(segments.map((segment) => segment.title)).toEqual(["dog: 3", "cat: 1"]);

    bar!.click();
    expect(onOpenList).toHaveBeenCalledTimes(1);
  });

  it("renders the folded remainder as its own segment", () => {
    const layer = termVectorLayer([["a", 9], ["b", 7], ["c", 5], ["d", 2], ["e", 1]]);

    const section = renderTermVectorStack(layer, "blue", () => {}, 3);
    document.body.replaceChildren(section);

    const segments = [...section.querySelectorAll<HTMLElement>(".term-vector-stack-segment")];
    expect(segments).toHaveLength(4);
    const remainder = segments[3]!;
    expect(remainder.classList.contains("term-vector-stack-other")).toBe(true);
    expect(remainder.style.flexGrow).toBe("3");
    expect(remainder.title).toBe("2 more terms: 3");
  });
});

describe("term vector list pop-out", () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <div id="annotation-drawer-backdrop" hidden></div>
      <aside id="annotation-details" hidden>
        <button id="annotation-details-close" type="button">Close</button>
        <div id="annotation-details-content"></div>
      </aside>`;
  });

  it("pops out every term ranked by frequency", () => {
    const layer = termVectorLayer([["cat", 1], ["dog", 3]]);
    const drawer = new AnnotationDrawer();

    drawer.showTermVectorList(layer);

    const panel = document.getElementById("annotation-details")!;
    expect(panel.hidden).toBe(false);
    expect(panel.textContent).toContain("2 distinct terms");
    expect(panel.textContent).toContain("4 occurrences");
    const rows = [...panel.querySelectorAll<HTMLButtonElement>(".term-vector-row")];
    expect(rows.map((row) => row.textContent)).toEqual([
      expect.stringContaining("dog"),
      expect.stringContaining("cat"),
    ]);
    expect(rows[0]!.textContent).toContain("3");
    expect(rows[0]!.textContent).toContain("75%");
  });

  it("drills from a list row into the term's typed annotation", () => {
    const layer = termVectorLayer([["cat", 1], ["dog", 3]]);
    const drawer = new AnnotationDrawer();
    drawer.showTermVectorList(layer);

    const row = document.querySelector<HTMLButtonElement>(".term-vector-row")!;
    row.click();

    const panel = document.getElementById("annotation-details")!;
    expect(panel.hidden).toBe(false);
    expect(panel.textContent).toContain("opennlp:term-vectors");
    const frequency = [...panel.querySelectorAll(".structured-fields > dt")]
      .find((field) => field.textContent === "frequency");
    expect(frequency?.nextElementSibling?.textContent).toBe("3");
  });
});
