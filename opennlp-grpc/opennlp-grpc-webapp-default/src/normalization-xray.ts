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

import { asciiLowerCase, replaceCharacter, withoutPrefix } from "./text-utils";

export interface NormalizationXrayView {
  rawText: string;
  normalizedText: string;
  appliedRungs: string[];
  runs: NormalizationRunView[];
}

export interface NormalizationRunView {
  rawStart: number;
  rawEnd: number;
  normStart: number;
  normEnd: number;
  equal: boolean;
}

/** Reads document.normalization from an analysis response, when present. */
export function readNormalizationXray(response: unknown): NormalizationXrayView | undefined {
  const envelope = record(response);
  const document = record(envelope?.document);
  const normalization = record(document?.normalization);
  if (!document || !normalization) {
    return undefined;
  }

  const rawText = stringValue(document.rawText);
  const normalizedText = stringValue(normalization.normalizedText);
  const appliedRungs = (Array.isArray(normalization.appliedRungs) ? normalization.appliedRungs : [])
    .flatMap((value) => {
      const rung = optionalString(value);
      return rung ? [rung] : [];
    });

  const runs: NormalizationRunView[] = [];
  let rawCursor = 0;
  let normCursor = 0;
  const alignment = Array.isArray(normalization.alignment) ? normalization.alignment : [];
  for (const entry of alignment) {
    const run = record(entry);
    const originalUnits = unitCount(run?.originalUnits);
    const normalizedUnits = unitCount(run?.normalizedUnits);
    if (originalUnits === undefined || normalizedUnits === undefined) {
      break;
    }
    if (rawCursor + originalUnits > rawText.length || normCursor + normalizedUnits > normalizedText.length) {
      break;
    }
    if (originalUnits > 0 || normalizedUnits > 0) {
      runs.push({
        rawStart: rawCursor,
        rawEnd: rawCursor + originalUnits,
        normStart: normCursor,
        normEnd: normCursor + normalizedUnits,
        equal: run?.equal === true,
      });
    }
    rawCursor += originalUnits;
    normCursor += normalizedUnits;
  }

  return { rawText, normalizedText, appliedRungs, runs };
}

/** Renders the two-pane normalization X-ray into the given container. */
export function renderNormalizationXray(container: HTMLElement, view: NormalizationXrayView): void {
  container.replaceChildren();

  const heading = document.createElement("div");
  heading.className = "xray-heading";
  const title = document.createElement("strong");
  title.textContent = "Normalization X-ray";
  const caption = document.createElement("small");
  caption.className = "xray-caption";
  const changed = view.runs.filter((run) => !run.equal).length;
  caption.textContent = `${view.runs.length} alignment ${view.runs.length === 1 ? "run" : "runs"}, ${changed} changed`;
  heading.append(title, caption);

  const rungs = document.createElement("div");
  rungs.className = "xray-rungs";
  rungs.setAttribute("aria-label", "Applied normalization rungs");
  if (view.appliedRungs.length === 0) {
    const chip = document.createElement("span");
    chip.className = "xray-chip is-empty";
    chip.textContent = "No rungs reported";
    rungs.append(chip);
  }
  for (const rung of view.appliedRungs) {
    const chip = document.createElement("span");
    chip.className = "xray-chip";
    chip.textContent = rungLabel(rung);
    chip.title = rung;
    rungs.append(chip);
  }

  const panes = document.createElement("div");
  panes.className = "xray-panes";
  panes.append(
    xrayPane(panes, "Raw text", view, "raw"),
    xrayPane(panes, "Normalized text", view, "normalized"),
  );

  container.append(heading, rungs, panes);
}

function xrayPane(
  panes: HTMLElement,
  label: string,
  view: NormalizationXrayView,
  side: "raw" | "normalized",
): HTMLElement {
  const pane = document.createElement("div");
  pane.className = "xray-pane";
  const paneLabel = document.createElement("span");
  paneLabel.className = "xray-pane-label";
  paneLabel.textContent = label;
  const body = document.createElement("div");
  body.className = "xray-pane-body";
  body.dataset.side = side;

  view.runs.forEach((run, index) => {
    const text = side === "raw"
      ? view.rawText.slice(run.rawStart, run.rawEnd)
      : view.normalizedText.slice(run.normStart, run.normEnd);
    const segment = document.createElement("button");
    segment.type = "button";
    segment.className = run.equal ? "xray-segment is-equal" : "xray-segment is-replaced";
    segment.dataset.runIndex = String(index);
    segment.textContent = text;
    if (!text) {
      segment.classList.add("is-empty");
      segment.textContent = "∅";
    }
    const state = run.equal ? "unchanged" : "replaced";
    segment.title = `Run ${index + 1}, ${state}`;
    segment.setAttribute("aria-label", `${label}, run ${index + 1}, ${state}: ${text || "no text"}`);
    segment.addEventListener("mouseover", () => activateRun(panes, index));
    segment.addEventListener("mouseleave", () => activateRun(panes, undefined));
    segment.addEventListener("focus", () => activateRun(panes, index));
    segment.addEventListener("blur", () => activateRun(panes, undefined));
    body.append(segment);
  });

  pane.append(paneLabel, body);
  return pane;
}

function activateRun(panes: HTMLElement, index: number | undefined): void {
  for (const segment of panes.querySelectorAll<HTMLElement>(".xray-segment")) {
    segment.classList.toggle("is-active", index !== undefined && segment.dataset.runIndex === String(index));
  }
}

function record(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function optionalString(value: unknown): string | undefined {
  const result = stringValue(value).trim();
  return result || undefined;
}

function unitCount(value: unknown): number | undefined {
  if (value === undefined) {
    return 0;
  }
  return typeof value === "number" && Number.isInteger(value) && value >= 0 ? value : undefined;
}

function rungLabel(rung: string): string {
  return asciiLowerCase(replaceCharacter(withoutPrefix(rung, "NORMALIZATION_RUNG_"), "_", " "));
}
