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

import type { ProgressiveAnalysisState } from "./progressive-analysis";

type ProgressiveRenderer = (state: ProgressiveAnalysisState) => void;
type FrameScheduler = (callback: FrameRequestCallback) => number;
type FrameCanceller = (handle: number) => void;

/** Coalesces streamed layer updates so the browser rebuilds the workbench once per frame. */
export class ProgressiveRenderQueue {
  readonly #render: ProgressiveRenderer;
  readonly #scheduleFrame: FrameScheduler;
  readonly #cancelFrame: FrameCanceller;
  #frameHandle: number | undefined;
  #pending: ProgressiveAnalysisState | undefined;

  constructor(
    render: ProgressiveRenderer,
    scheduleFrame: FrameScheduler = (callback) => window.requestAnimationFrame(callback),
    cancelFrame: FrameCanceller = (handle) => window.cancelAnimationFrame(handle),
  ) {
    this.#render = render;
    this.#scheduleFrame = scheduleFrame;
    this.#cancelFrame = cancelFrame;
  }

  /** Retains the newest state and every layer id changed before the next paint. */
  schedule(state: ProgressiveAnalysisState): void {
    const changed = new Set(this.#pending?.updatedLayerIds ?? []);
    for (const id of state.updatedLayerIds) {
      changed.add(id);
    }
    this.#pending = { ...state, updatedLayerIds: [...changed] };
    if (this.#frameHandle !== undefined) {
      return;
    }
    this.#frameHandle = this.#scheduleFrame(() => {
      this.#frameHandle = undefined;
      const pending = this.#pending;
      this.#pending = undefined;
      if (pending) {
        this.#render(pending);
      }
    });
  }

  /** Drops a pending partial paint when a final response or error supersedes it. */
  cancel(): void {
    if (this.#frameHandle !== undefined) {
      this.#cancelFrame(this.#frameHandle);
    }
    this.#frameHandle = undefined;
    this.#pending = undefined;
  }
}
