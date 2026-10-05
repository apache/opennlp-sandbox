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

export interface ProgressiveAnalysisState {
  sequence: number;
  response: Record<string, unknown>;
  complete: boolean;
  lastStep?: string;
  updatedLayerIds: string[];
  failures: string[];
}

/** Returns the state before the first progressive event arrives. */
export function emptyProgressiveAnalysis(): ProgressiveAnalysisState {
  return { sequence: 0, response: {}, complete: false, updatedLayerIds: [], failures: [] };
}

/** Formats a pipeline enum value for the progress status. */
export function displayPipelineStep(step: string): string {
  const prefix = "PIPELINE_STEP_";
  const value = step.startsWith(prefix) ? step.slice(prefix.length) : step;
  let label = "";
  for (const character of value) {
    label += character === "_" ? " " : character;
  }
  return label;
}

/** Applies one monotonic event, upserting complete layer values by stable layer id. */
export function applyProgressiveEvent(
  state: ProgressiveAnalysisState,
  event: Record<string, unknown>,
): ProgressiveAnalysisState {
  const sequence = numericSequence(event.sequence);
  if (sequence !== state.sequence + 1) {
    throw new Error(`expected progressive event ${state.sequence + 1}, received ${sequence}`);
  }
  if (isRecord(event.started)) {
    return {
      ...state,
      sequence,
      response: { document: recordOrEmpty(event.started.document) },
      updatedLayerIds: [],
    };
  }
  if (isRecord(event.layersReady)) {
    const update = event.layersReady;
    const response = mergeLayers(
      state.response,
      Array.isArray(update.layers) ? update.layers.filter(isRecord) : [],
      Array.isArray(update.diagnostics) ? update.diagnostics.filter(isRecord) : [],
    );
    return {
      ...state,
      sequence,
      response,
      lastStep: typeof update.step === "string" ? update.step : state.lastStep,
      updatedLayerIds: layerIds(update.layers),
    };
  }
  if (isRecord(event.stepFailed)) {
    const step = typeof event.stepFailed.step === "string"
      ? event.stepFailed.step : "Analysis branch";
    const message = typeof event.stepFailed.message === "string"
      ? event.stepFailed.message : "failed";
    return {
      ...state,
      sequence,
      lastStep: step,
      updatedLayerIds: [],
      failures: [...state.failures, `${step}: ${message}`],
    };
  }
  if (isRecord(event.complete)) {
    return {
      ...state,
      sequence,
      response: event.complete,
      complete: true,
      updatedLayerIds: [],
    };
  }
  throw new Error(`progressive event ${sequence} has no recognized update`);
}

function layerIds(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter(isRecord)
      .map((layer) => typeof layer.id === "string" ? layer.id : "")
      .filter(Boolean)
    : [];
}

/** Merges a layer batch without discarding values delivered by earlier branches. */
function mergeLayers(
  response: Record<string, unknown>,
  updates: Record<string, unknown>[],
  diagnostics: Record<string, unknown>[],
): Record<string, unknown> {
  const document = recordOrEmpty(response.document);
  const layerContainer = recordOrEmpty(document.layers);
  const existing = Array.isArray(layerContainer.layers)
    ? layerContainer.layers.filter(isRecord) : [];
  const positions = new Map(existing
    .map((layer, index) => [typeof layer.id === "string" ? layer.id : "", index] as const)
    .filter(([id]) => id));
  const layers = [...existing];
  for (const layer of updates) {
    const id = typeof layer.id === "string" ? layer.id : "";
    const position = positions.get(id);
    if (position === undefined) {
      if (id) {
        positions.set(id, layers.length);
      }
      layers.push(layer);
    } else {
      layers[position] = layer;
    }
  }
  const existingDiagnostics = Array.isArray(response.diagnostics)
    ? response.diagnostics.filter(isRecord) : [];
  return {
    ...response,
    document: {
      ...document,
      layers: { ...layerContainer, layers },
    },
    diagnostics: [...existingDiagnostics, ...diagnostics],
  };
}

/** Reads the uint64 protobuf JSON sequence as a safe browser number. */
function numericSequence(value: unknown): number {
  const sequence = typeof value === "number" ? value
    : typeof value === "string" ? Number(value) : Number.NaN;
  if (!Number.isSafeInteger(sequence) || sequence < 1) {
    throw new Error("progressive event sequence is missing or invalid");
  }
  return sequence;
}

function recordOrEmpty(value: unknown): Record<string, unknown> {
  return isRecord(value) ? value : {};
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
