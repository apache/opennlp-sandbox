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

import { beforeEach, describe, expect, it } from "vitest";

import { AnalysisControls } from "../src/analysis-controls";

function mountControlsDom(): void {
  document.body.innerHTML = `
    <select id="profile-select"></select>
    <select id="embedding-model-select"></select>
    <select id="pipeline-language-select"></select>
    <select id="pos-tag-format-select"><option value=""></option></select>
    <input id="sentence-chunks" type="checkbox" />
    <input id="token-chunks" type="checkbox" />
    <input id="token-chunk-size" type="number" value="96" />
    <input id="token-chunk-overlap" type="number" value="12" />
    <ul id="enabled-feature-list"></ul>
    <div id="feature-availability" hidden></div>
    <div id="feature-options"></div>
    <ul id="model-list"></ul>`;
}

function serviceInfo(profileIds: string[]): unknown {
  return { availableProfileIds: profileIds };
}

describe("analysis controls profile selection", () => {
  beforeEach(() => {
    mountControlsDom();
  });

  it("starts with every ready feature selectable when named profiles are advertised", () => {
    const controls = new AnalysisControls(() => undefined);

    controls.configure({
      availableProfileIds: ["en-basic", "en-ner", "en-embed"],
      supportedSteps: [
        "PIPELINE_STEP_TOKENIZE",
        "PIPELINE_STEP_NER",
        "PIPELINE_STEP_GEOCODE",
        "PIPELINE_STEP_EMBED",
      ],
    }, {
      bundles: [
        {
          bundleId: "en-ner",
          supportedLanguages: ["en"],
          supportedSteps: ["PIPELINE_STEP_TOKENIZE", "PIPELINE_STEP_NER"],
        },
        {
          bundleId: "en-embed",
          supportedLanguages: ["en"],
          supportedSteps: ["PIPELINE_STEP_EMBED"],
          models: [{
            name: "legal-minilm-full",
            componentType: "COMPONENT_TYPE_EMBEDDER",
            embeddingDimension: 256,
            backendId: "static",
          }],
        },
      ],
    });

    const select = document.getElementById("profile-select") as HTMLSelectElement;
    expect(select.value).toBe("max");
    const featureInputs = [...document.querySelectorAll<HTMLInputElement>("#feature-options input")];
    const readyInputs = featureInputs.filter((input) => [
      "PIPELINE_STEP_TOKENIZE",
      "PIPELINE_STEP_NER",
      "PIPELINE_STEP_GEOCODE",
      "PIPELINE_STEP_EMBED",
    ].includes(input.value));
    expect(readyInputs).toHaveLength(4);
    expect(readyInputs.every((input) => input.checked && !input.disabled)).toBe(true);
    expect(controls.request("Some text.").profile?.steps).toEqual([
      "PIPELINE_STEP_TOKENIZE",
      "PIPELINE_STEP_NER",
      "PIPELINE_STEP_GEOCODE",
      "PIPELINE_STEP_EMBED",
    ]);
  });

  it("starts with all available features when only a basic profile is advertised", () => {
    const controls = new AnalysisControls(() => undefined);

    controls.configure(serviceInfo(["en-basic"]), undefined);

    const select = document.getElementById("profile-select") as HTMLSelectElement;
    expect(select.value).toBe("max");
  });

  it("keeps runtime-trained embedding models when discovery configures afterwards", () => {
    const controls = new AnalysisControls(() => undefined);
    controls.setTrainedEmbeddingModels([
      { id: "static-model-1234", label: "Alice model2vec (trained)" },
    ]);

    controls.configure(serviceInfo(["en-embed"]), {
      bundles: [
        {
          bundleId: "en-embed",
          supportedLanguages: ["en"],
          supportedSteps: ["PIPELINE_STEP_EMBED"],
          models: [{
            name: "minilm",
            componentType: "COMPONENT_TYPE_EMBEDDER",
            embeddingDimension: 384,
            backendId: "tei",
          }],
        },
      ],
    });

    const select = document.getElementById("embedding-model-select") as HTMLSelectElement;
    const values = [...select.options].map((option) => option.value);
    expect(values).toContain("minilm");
    expect(values).toContain("static-model-1234");
  });

  it("selects an offered embedding model programmatically and rejects unknown ids", () => {
    const controls = new AnalysisControls(() => undefined);
    controls.setTrainedEmbeddingModels([
      { id: "static-model-1234", label: "Alice model2vec (trained)" },
    ]);
    const select = document.getElementById("embedding-model-select") as HTMLSelectElement;

    expect(controls.selectEmbeddingModel("static-model-1234")).toBe(true);
    expect(select.value).toBe("static-model-1234");
    expect(controls.selectEmbeddingModel("static-model-missing")).toBe(false);
    expect(select.value).toBe("static-model-1234");
  });

  it("browns out supported features without a model and explains each with its fix", () => {
    const controls = new AnalysisControls(() => undefined);
    controls.configure({
      availableProfileIds: ["en-basic"],
      supportedSteps: ["PIPELINE_STEP_TOKENIZE", "PIPELINE_STEP_NER", "PIPELINE_STEP_PARSE"],
    }, {
      bundles: [{
        bundleId: "en-basic",
        supportedLanguages: ["en"],
        supportedSteps: ["PIPELINE_STEP_TOKENIZE"],
      }],
    });
    controls.setFeatureFixers(new Map([
      ["PIPELINE_STEP_NER", [{ catalogId: "en-ner-15-person", displayName: "English person names" }]],
    ]));

    const chips = Array.from(document.querySelectorAll<HTMLButtonElement>(
      "#enabled-feature-list .is-unavailable button"));
    expect(chips.map((chip) => chip.dataset.unavailableStep))
      .toEqual(["PIPELINE_STEP_NER", "PIPELINE_STEP_PARSE"]);
    expect(chips.map((chip) => chip.textContent)).toEqual(["Named entities", "Constituency parses"]);
    const panel = document.getElementById("feature-availability")!;
    expect(panel.hidden).toBe(true);

    chips[0]!.click();
    expect(panel.hidden).toBe(false);
    expect(panel.textContent).toContain("Named entities is not available on this server.");
    expect(panel.textContent).toContain("no model or resource that serves it is loaded");
    expect(panel.textContent).toContain("install English person names from the model catalog");
    const jump = panel.querySelector<HTMLElement>("[data-workbench-jump]")!;
    expect(jump.dataset.workbenchJump).toBe("models");
    expect(jump.dataset.workbenchFocus).toBe("PIPELINE_STEP_NER");

    chips[1]!.click();
    expect(panel.textContent).toContain("ask the operator to set model.parser.<model_id>.path");
    expect(panel.querySelector("[data-workbench-jump]")).toBeNull();

    controls.explain("PIPELINE_STEP_GEOCODE");
    expect(panel.textContent).toContain("this server build does not include it");
    expect(panel.textContent).toContain("a different server build is needed");
  });
});
