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

import { describe, expect, it, vi } from "vitest";

import { ProgressiveRenderQueue } from "../src/progressive-render-queue";
import type { ProgressiveAnalysisState } from "../src/progressive-analysis";

describe("progressive render queue", () => {
  it("coalesces streamed updates into one render per animation frame", () => {
    let frame: FrameRequestCallback | undefined;
    const scheduleFrame = vi.fn((callback: FrameRequestCallback) => {
      frame = callback;
      return 7;
    });
    const cancelFrame = vi.fn();
    const render = vi.fn();
    const queue = new ProgressiveRenderQueue(render, scheduleFrame, cancelFrame);

    queue.schedule(state(2, ["opennlp:tokens"]));
    queue.schedule(state(3, ["opennlp:language"]));
    queue.schedule(state(4, ["opennlp:entities"]));

    expect(scheduleFrame).toHaveBeenCalledOnce();
    expect(render).not.toHaveBeenCalled();
    frame?.(16);
    expect(render).toHaveBeenCalledOnce();
    expect(render).toHaveBeenCalledWith(state(4, [
      "opennlp:tokens",
      "opennlp:language",
      "opennlp:entities",
    ]));
  });

  it("cancels a queued partial render before the final response replaces it", () => {
    const scheduleFrame = vi.fn((_callback: FrameRequestCallback) => 11);
    const cancelFrame = vi.fn();
    const render = vi.fn();
    const queue = new ProgressiveRenderQueue(render, scheduleFrame, cancelFrame);

    queue.schedule(state(2, ["opennlp:tokens"]));
    queue.cancel();

    expect(cancelFrame).toHaveBeenCalledWith(11);
    expect(render).not.toHaveBeenCalled();
  });
});

function state(sequence: number, updatedLayerIds: string[]): ProgressiveAnalysisState {
  return {
    sequence,
    response: { document: { rawText: "Alice ran." } },
    complete: false,
    updatedLayerIds,
    failures: [],
  };
}
