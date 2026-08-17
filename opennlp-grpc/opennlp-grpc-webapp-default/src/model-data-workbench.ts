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
 * KIND, either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

import {
  FEATURE_NAMES,
  PIPELINE_ORDER,
  type AnalysisCapabilities,
} from "./analysis-config";
import { errorMessage, requiredElement } from "./ui-utils";

/** Renders model and data readiness separately from analysis request controls. */
export class ModelDataWorkbench {
  readonly #summary = requiredElement<HTMLElement>("resource-summary");
  readonly #features = requiredElement<HTMLElement>("resource-feature-list");
  readonly #bundles = requiredElement<HTMLUListElement>("resource-bundle-list");
  readonly #command = requiredElement<HTMLElement>("resource-install-command");
  readonly #copy = requiredElement<HTMLButtonElement>("copy-resource-command");
  readonly #status = requiredElement<HTMLElement>("resource-install-status");

  constructor() {
    this.#copy.addEventListener("click", () => void this.copyCommand());
  }

  configure(capabilities: AnalysisCapabilities): void {
    const ready = new Set(capabilities.maxSteps);
    const supported = new Set(capabilities.supportedSteps);
    this.#features.replaceChildren(...PIPELINE_ORDER.map((step) => {
      const item = document.createElement("article");
      const state = ready.has(step) ? "ready" : supported.has(step) ? "missing" : "unsupported";
      item.className = `resource-feature is-${state}`;
      const title = document.createElement("strong");
      title.textContent = FEATURE_NAMES[step] ?? step;
      const label = document.createElement("span");
      label.textContent = state === "ready"
        ? "Ready"
        : state === "missing" ? "Needs model or data" : "Not in this build";
      item.append(title, label);
      return item;
    }));

    this.#bundles.replaceChildren(...(capabilities.bundles.length > 0
      ? capabilities.bundles.map((bundle) => {
          const item = document.createElement("li");
          item.textContent = bundle.label;
          item.title = bundle.id;
          return item;
        })
      : [listItem("No model bundles are currently loaded.")]));
    this.#summary.textContent = `${ready.size} of ${PIPELINE_ORDER.length} features ready`;
  }

  private async copyCommand(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.#command.textContent ?? "");
      this.#status.textContent = "Installer command copied. Replace the source, checksum, and target values.";
      this.#status.classList.remove("is-error");
    } catch (error) {
      this.#status.textContent = errorMessage(error, "Could not copy the installer command.");
      this.#status.classList.add("is-error");
    }
  }
}

function listItem(text: string): HTMLLIElement {
  const item = document.createElement("li");
  item.textContent = text;
  return item;
}
