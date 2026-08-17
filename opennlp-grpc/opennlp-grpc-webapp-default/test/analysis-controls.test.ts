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
    <input id="sentence-chunks" type="checkbox" />
    <input id="token-chunks" type="checkbox" />
    <input id="token-chunk-size" type="number" value="96" />
    <input id="token-chunk-overlap" type="number" value="12" />
    <ul id="enabled-feature-list"></ul>
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
});
