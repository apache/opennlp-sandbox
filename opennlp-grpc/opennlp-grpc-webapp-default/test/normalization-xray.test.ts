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

import { describe, expect, it } from "vitest";

import {
  readNormalizationXray,
  renderNormalizationXray,
  type NormalizationXrayView,
} from "../src/normalization-xray";

function xrayResponse(normalization: unknown, rawText = "Hello   world") {
  return {
    document: {
      rawText,
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      normalization,
    },
  };
}

function sampleView(): NormalizationXrayView {
  const view = readNormalizationXray(xrayResponse({
    normalizedText: "Hello world",
    appliedNormalizers: ["NORMALIZER_STRIP_INVISIBLE", "NORMALIZER_WHITESPACE"],
    alignment: [
      { originalUnits: 5, normalizedUnits: 5, equal: true },
      { originalUnits: 3, normalizedUnits: 1 },
      { originalUnits: 5, normalizedUnits: 5, equal: true },
    ],
  }));
  if (!view) {
    throw new Error("The sample response must produce a view.");
  }
  return view;
}

describe("normalization x-ray reader", () => {
  it("reads an offset-aware normalization result into cumulative run boundaries", () => {
    const view = sampleView();

    expect(view.rawText).toBe("Hello   world");
    expect(view.normalizedText).toBe("Hello world");
    expect(view.appliedNormalizers).toEqual(["NORMALIZER_STRIP_INVISIBLE", "NORMALIZER_WHITESPACE"]);
    expect(view.runs).toEqual([
      { rawStart: 0, rawEnd: 5, normStart: 0, normEnd: 5, equal: true },
      { rawStart: 5, rawEnd: 8, normStart: 5, normEnd: 6, equal: false },
      { rawStart: 8, rawEnd: 13, normStart: 6, normEnd: 11, equal: true },
    ]);
  });

  it("returns undefined when the response carries no normalization result", () => {
    expect(readNormalizationXray(undefined)).toBeUndefined();
    expect(readNormalizationXray(null)).toBeUndefined();
    expect(readNormalizationXray({})).toBeUndefined();
    expect(readNormalizationXray({ document: { rawText: "Hi" } })).toBeUndefined();
    expect(readNormalizationXray(xrayResponse("not a record"))).toBeUndefined();
  });

  it("counts a surrogate-pair emoji as two units on the raw side", () => {
    const view = readNormalizationXray(xrayResponse({
      normalizedText: "a:b",
      appliedNormalizers: ["NORMALIZER_EMOJI_TO_EMOTICON"],
      alignment: [
        { originalUnits: 1, normalizedUnits: 1, equal: true },
        { originalUnits: 2, normalizedUnits: 1 },
        { originalUnits: 1, normalizedUnits: 1, equal: true },
      ],
    }, "a😀b"));

    expect(view?.runs).toEqual([
      { rawStart: 0, rawEnd: 1, normStart: 0, normEnd: 1, equal: true },
      { rawStart: 1, rawEnd: 3, normStart: 1, normEnd: 2, equal: false },
      { rawStart: 3, rawEnd: 4, normStart: 2, normEnd: 3, equal: true },
    ]);
    expect(view?.rawText.slice(1, 3)).toBe("😀");
  });

  it("treats unit counts omitted by proto3 JSON as zero", () => {
    const view = readNormalizationXray(xrayResponse({
      normalizedText: "ab",
      appliedNormalizers: [],
      alignment: [
        { normalizedUnits: 1 },
        { originalUnits: 1, normalizedUnits: 1, equal: true },
      ],
    }, "b"));

    expect(view?.runs).toEqual([
      { rawStart: 0, rawEnd: 0, normStart: 0, normEnd: 1, equal: false },
      { rawStart: 0, rawEnd: 1, normStart: 1, normEnd: 2, equal: true },
    ]);
  });

  it("drops malformed runs and tolerates trailing data past the text lengths", () => {
    const malformed = readNormalizationXray(xrayResponse({
      normalizedText: "Hello world",
      appliedNormalizers: ["NORMALIZER_WHITESPACE", 42, ""],
      alignment: [
        { originalUnits: 6, normalizedUnits: 6, equal: true },
        { originalUnits: "three", normalizedUnits: 1 },
        { originalUnits: 5, normalizedUnits: 5, equal: true },
      ],
    }));
    expect(malformed?.runs).toEqual([
      { rawStart: 0, rawEnd: 6, normStart: 0, normEnd: 6, equal: true },
    ]);
    expect(malformed?.appliedNormalizers).toEqual(["NORMALIZER_WHITESPACE"]);

    const overrunning = readNormalizationXray(xrayResponse({
      normalizedText: "Hello world",
      appliedNormalizers: [],
      alignment: [
        { originalUnits: 6, normalizedUnits: 6, equal: true },
        { originalUnits: 100, normalizedUnits: 1 },
      ],
    }));
    expect(overrunning?.runs).toEqual([
      { rawStart: 0, rawEnd: 6, normStart: 0, normEnd: 6, equal: true },
    ]);
  });
});

describe("normalization x-ray renderer", () => {
  it("renders one paired segment per alignment run in each pane", () => {
    const container = document.createElement("section");
    renderNormalizationXray(container, sampleView());

    const rawSegments = container.querySelectorAll<HTMLElement>('[data-side="raw"] .xray-segment');
    const normalizedSegments = container.querySelectorAll<HTMLElement>('[data-side="normalized"] .xray-segment');
    expect(rawSegments).toHaveLength(3);
    expect(normalizedSegments).toHaveLength(3);
    expect([...rawSegments].map((segment) => segment.textContent).join("")).toBe("Hello   world");
    expect([...normalizedSegments].map((segment) => segment.textContent).join("")).toBe("Hello world");
    for (let index = 0; index < 3; index++) {
      expect(rawSegments[index]?.dataset.runIndex).toBe(String(index));
      expect(normalizedSegments[index]?.dataset.runIndex).toBe(String(index));
      expect(rawSegments[index]?.tabIndex).toBe(0);
    }
  });

  it("marks equal runs as neutral and replace runs with the accent class", () => {
    const container = document.createElement("section");
    renderNormalizationXray(container, sampleView());

    const segments = container.querySelectorAll<HTMLElement>(".xray-segment");
    expect(container.querySelectorAll(".xray-segment.is-equal")).toHaveLength(4);
    expect(container.querySelectorAll(".xray-segment.is-replaced")).toHaveLength(2);
    expect(segments[1]?.classList.contains("is-replaced")).toBe(true);
    expect(segments[1]?.classList.contains("is-equal")).toBe(false);
  });

  it("shows applied normalizers as chips with a run summary caption", () => {
    const container = document.createElement("section");
    renderNormalizationXray(container, sampleView());

    const chips = [...container.querySelectorAll<HTMLElement>(".xray-chip")];
    expect(chips.map((chip) => chip.textContent)).toEqual(["strip invisible", "whitespace"]);
    expect(chips[0]?.title).toBe("NORMALIZER_STRIP_INVISIBLE");
    expect(container.querySelector(".xray-caption")?.textContent).toBe("3 alignment runs, 1 changed");
  });

  it("highlights the counterpart segment in the other pane on hover and focus", () => {
    const container = document.createElement("section");
    renderNormalizationXray(container, sampleView());

    const rawSegments = container.querySelectorAll<HTMLElement>('[data-side="raw"] .xray-segment');
    const normalizedSegments = container.querySelectorAll<HTMLElement>('[data-side="normalized"] .xray-segment');
    const hovered = rawSegments[1]!;

    hovered.dispatchEvent(new MouseEvent("mouseover"));
    expect(hovered.classList.contains("is-active")).toBe(true);
    expect(normalizedSegments[1]?.classList.contains("is-active")).toBe(true);
    expect(normalizedSegments[0]?.classList.contains("is-active")).toBe(false);

    hovered.dispatchEvent(new MouseEvent("mouseleave"));
    expect(hovered.classList.contains("is-active")).toBe(false);
    expect(normalizedSegments[1]?.classList.contains("is-active")).toBe(false);

    const focused = normalizedSegments[2]!;
    focused.dispatchEvent(new FocusEvent("focus"));
    expect(focused.classList.contains("is-active")).toBe(true);
    expect(rawSegments[2]?.classList.contains("is-active")).toBe(true);

    focused.dispatchEvent(new FocusEvent("blur"));
    expect(focused.classList.contains("is-active")).toBe(false);
    expect(rawSegments[2]?.classList.contains("is-active")).toBe(false);
  });

  it("marks segments emptied by a run with an empty-state class", () => {
    const view = readNormalizationXray(xrayResponse({
      normalizedText: "b",
      appliedNormalizers: ["NORMALIZER_STRIP_INVISIBLE"],
      alignment: [
        { originalUnits: 1 },
        { originalUnits: 1, normalizedUnits: 1, equal: true },
      ],
    }, "​b"));

    const container = document.createElement("section");
    renderNormalizationXray(container, view!);

    const removed = container.querySelector<HTMLElement>('[data-side="normalized"] .xray-segment');
    expect(removed?.classList.contains("is-empty")).toBe(true);
    expect(removed?.classList.contains("is-replaced")).toBe(true);
    expect(container.querySelector(".xray-caption")?.textContent).toBe("2 alignment runs, 1 changed");
  });
});
