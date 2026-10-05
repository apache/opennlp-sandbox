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

import { describe, expect, it } from "vitest";

import {
  applyProgressiveEvent,
  displayPipelineStep,
  emptyProgressiveAnalysis,
} from "../src/progressive-analysis";

describe("progressive analysis state", () => {
  it("upserts arriving layers and accepts the final canonical response", () => {
    let state = emptyProgressiveAnalysis();
    state = applyProgressiveEvent(state, {
      sequence: "1",
      started: { document: { docId: "alice", rawText: "Alice ran." } },
    });
    state = applyProgressiveEvent(state, {
      sequence: "2",
      layersReady: {
        step: "PIPELINE_STEP_TOKENIZE",
        layers: [{ id: "opennlp:sentences" }, { id: "opennlp:tokens" }],
      },
    });
    state = applyProgressiveEvent(state, {
      sequence: "3",
      layersReady: {
        step: "PIPELINE_STEP_NER",
        layers: [{ id: "opennlp:entities", entityValues: { annotations: [] } }],
      },
    });

    expect(state.response).toHaveProperty("document.layers.layers", [
      { id: "opennlp:sentences" },
      { id: "opennlp:tokens" },
      { id: "opennlp:entities", entityValues: { annotations: [] } },
    ]);
    expect(state.lastStep).toBe("PIPELINE_STEP_NER");
    expect(state.updatedLayerIds).toEqual(["opennlp:entities"]);
    expect(state.complete).toBe(false);

    state = applyProgressiveEvent(state, {
      sequence: "4",
      complete: {
        document: {
          docId: "alice",
          rawText: "Alice ran.",
          layers: { layers: [{ id: "opennlp:tokens", stringValues: { annotations: [] } }] },
        },
      },
    });

    expect(state.complete).toBe(true);
    expect(state.response).toHaveProperty(
      "document.layers.layers.0.stringValues.annotations", []);
  });

  it("rejects a missing or repeated event sequence", () => {
    const state = applyProgressiveEvent(emptyProgressiveAnalysis(), {
      sequence: "1",
      started: { document: { rawText: "Hello" } },
    });

    expect(() => applyProgressiveEvent(state, {
      sequence: "1",
      complete: { document: { rawText: "Hello" } },
    })).toThrow("expected progressive event 2");
  });

  it("formats a pipeline step for the progress status", () => {
    expect(displayPipelineStep("PIPELINE_STEP_POS_TAG")).toBe("POS TAG");
    expect(displayPipelineStep("CUSTOM_STEP")).toBe("CUSTOM STEP");
  });
});
