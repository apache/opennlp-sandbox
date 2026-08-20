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

import {
  buildAnalysisRequest,
  discoverAnalysisCapabilities,
  FEATURE_NAMES,
  PIPELINE_ORDER,
  type AnalysisCapabilities,
  type AnalysisSelection,
} from "./analysis-config";
import type { AnalyzeRequest } from "./api";
import type { DiscoveryOption } from "./discovery";
import { replaceCharacter, withoutPrefix } from "./text-utils";
import { requiredElement } from "./ui-utils";

export class AnalysisControls {
  readonly #profile = requiredElement<HTMLSelectElement>("profile-select");
  readonly #embeddingModel = requiredElement<HTMLSelectElement>("embedding-model-select");
  readonly #sentenceChunks = requiredElement<HTMLInputElement>("sentence-chunks");
  readonly #tokenChunks = requiredElement<HTMLInputElement>("token-chunks");
  readonly #tokenChunkSize = requiredElement<HTMLInputElement>("token-chunk-size");
  readonly #tokenChunkOverlap = requiredElement<HTMLInputElement>("token-chunk-overlap");
  readonly #enabledFeatures = requiredElement<HTMLUListElement>("enabled-feature-list");
  readonly #featureOptions = requiredElement<HTMLDivElement>("feature-options");
  readonly #modelList = requiredElement<HTMLUListElement>("model-list");
  readonly #onChange: () => void;
  #capabilities: AnalysisCapabilities = discoverAnalysisCapabilities(undefined, undefined);
  #customSteps = new Set<string>();

  constructor(onChange: () => void) {
    this.#onChange = onChange;
    this.#profile.addEventListener("change", () => {
      this.renderFeatureOptions();
      this.renderFeatures();
      this.#onChange();
    });
    this.#embeddingModel.addEventListener("change", () => this.renderFeatures());
    this.#sentenceChunks.addEventListener("change", () => this.changed());
    this.#tokenChunks.addEventListener("change", () => this.updateChunkControls());
    this.#tokenChunkSize.addEventListener("input", () => this.changed());
    this.#tokenChunkOverlap.addEventListener("input", () => this.changed());
    this.#tokenChunkSize.disabled = !this.#tokenChunks.checked;
    this.#tokenChunkOverlap.disabled = !this.#tokenChunks.checked;
    this.renderFeatures();
  }

  configure(serviceValue: unknown, bundlesValue: unknown): AnalysisCapabilities {
    this.#capabilities = discoverAnalysisCapabilities(serviceValue, bundlesValue);
    this.populateProfiles(this.#capabilities.profiles);
    this.populateEmbeddingModels(this.#capabilities.embeddingModels);
    this.populateModelList(this.#capabilities.bundles);
    this.#customSteps = new Set(this.#capabilities.maxSteps);
    this.renderFeatureOptions();
    this.renderFeatures();
    return this.#capabilities;
  }

  /**
   * Merges runtime-trained embedding models into the selector next to the
   * startup-configured ones, keeping the current selection when it survives.
   */
  setTrainedEmbeddingModels(models: DiscoveryOption[]): void {
    const selected = this.#embeddingModel.value;
    const configured = this.#capabilities.embeddingModels;
    const merged = [
      ...configured,
      ...models.filter((model) => !configured.some((option) => option.id === model.id)),
    ];
    this.populateEmbeddingModels(merged);
    if (merged.some((option) => option.id === selected)) {
      this.#embeddingModel.value = selected;
    }
    this.renderFeatures();
  }

  request(text: string, includeChunks = true): AnalyzeRequest {
    const value = this.#profile.value;
    const profileId = withoutPrefix(value, "profile:");
    let mode: AnalysisSelection["mode"] = value === "max" ? "max" : "automatic";
    if (value === "custom") {
      mode = "custom";
    }
    if (profileId !== value) {
      mode = "profile";
    }
    if (mode === "max" && this.#capabilities.maxSteps.length === 0) {
      mode = "automatic";
    }
    return buildAnalysisRequest(text, {
      mode,
      profileId: mode === "profile" ? profileId : undefined,
      selectedSteps: mode === "custom" ? [...this.#customSteps] : undefined,
      sentenceChunks: includeChunks && this.#sentenceChunks.checked,
      tokenChunks: includeChunks && this.#tokenChunks.checked,
      tokenChunkSize: this.#tokenChunkSize.valueAsNumber,
      tokenChunkOverlap: this.#tokenChunkOverlap.valueAsNumber,
      embeddingModelId: this.#embeddingModel.value || undefined,
    }, this.#capabilities);
  }

  get valid(): boolean {
    if (!this.#tokenChunks.checked) {
      return true;
    }
    const size = this.#tokenChunkSize.valueAsNumber;
    const overlap = this.#tokenChunkOverlap.valueAsNumber;
    return Number.isInteger(size) && size > 0 && Number.isInteger(overlap)
      && overlap >= 0 && overlap < size;
  }

  private changed(): void {
    this.#tokenChunkOverlap.setCustomValidity(
      this.valid ? "" : "Overlap must be smaller than the token window.",
    );
    this.renderFeatures();
    this.#onChange();
  }

  private updateChunkControls(): void {
    this.#tokenChunkSize.disabled = !this.#tokenChunks.checked;
    this.#tokenChunkOverlap.disabled = !this.#tokenChunks.checked;
    this.changed();
  }

  private populateProfiles(options: DiscoveryOption[]): void {
    this.#profile.replaceChildren(
      new Option("All available features", "max", true, true),
      new Option("Choose features", "custom"),
      new Option("Server automatic", "automatic"),
    );
    for (const option of options) {
      this.#profile.add(new Option(`Profile: ${option.label}`, `profile:${option.id}`));
    }
  }

  private populateEmbeddingModels(options: DiscoveryOption[]): void {
    this.#embeddingModel.replaceChildren();
    if (options.length === 0) {
      this.#embeddingModel.add(new Option("No embedding model configured", ""));
      this.#embeddingModel.disabled = true;
      return;
    }
    for (const option of options) {
      this.#embeddingModel.add(new Option(option.label, option.id));
    }
    this.#embeddingModel.disabled = false;
  }

  private populateModelList(options: DiscoveryOption[]): void {
    this.#modelList.replaceChildren();
    if (options.length === 0) {
      const item = document.createElement("li");
      item.textContent = "None reported";
      item.className = "is-empty";
      this.#modelList.append(item);
      return;
    }
    for (const option of options) {
      const item = document.createElement("li");
      item.textContent = option.label;
      item.title = option.id;
      this.#modelList.append(item);
    }
  }

  private renderFeatures(): void {
    const labels: string[] = [];
    if (this.#profile.value === "max") {
      for (const step of this.#capabilities.maxSteps) {
        labels.push(FEATURE_NAMES[step] ?? readableStep(step));
      }
    } else if (this.#profile.value === "custom") {
      for (const step of PIPELINE_ORDER) {
        if (this.#customSteps.has(step)) {
          labels.push(FEATURE_NAMES[step] ?? readableStep(step));
        }
      }
    } else if (this.#profile.value === "automatic") {
      labels.push("Server automatic profile");
    } else {
      labels.push(`Named ${withoutPrefix(this.#profile.value, "profile:")} profile`);
    }
    if (this.#sentenceChunks.checked) {
      labels.push("Sentence chunks");
    }
    if (this.#tokenChunks.checked) {
      labels.push("Token windows");
    }
    this.#enabledFeatures.replaceChildren(...labels.map(featureChip));
  }

  private renderFeatureOptions(): void {
    const mode = this.#profile.value;
    const selectable = new Set(this.#capabilities.maxSteps);
    const supported = new Set(this.#capabilities.supportedSteps);
    const selected = mode === "max" ? selectable : this.#customSteps;
    const options = PIPELINE_ORDER.map((step) => {
      const label = document.createElement("label");
      label.className = "feature-option";
      const input = document.createElement("input");
      input.type = "checkbox";
      input.value = step;
      input.checked = selected.has(step);
      input.disabled = mode !== "max" && mode !== "custom" || !selectable.has(step);
      input.addEventListener("change", () => {
        this.#customSteps = new Set(
          [...this.#featureOptions.querySelectorAll<HTMLInputElement>("input:checked")]
            .map((candidate) => candidate.value),
        );
        this.#profile.value = "custom";
        this.renderFeatureOptions();
        this.changed();
      });
      const text = document.createElement("span");
      const name = document.createElement("strong");
      name.textContent = FEATURE_NAMES[step] ?? readableStep(step);
      const status = document.createElement("small");
      status.textContent = selectable.has(step)
        ? "Ready"
        : supported.has(step) ? "Needs model or data" : "Not in this server build";
      text.append(name, status);
      label.append(input, text);
      return label;
    });
    this.#featureOptions.replaceChildren(...options);
  }
}

function readableStep(step: string): string {
  return replaceCharacter(withoutPrefix(step, "PIPELINE_STEP_"), "_", " ");
}

function featureChip(label: string): HTMLLIElement {
  const item = document.createElement("li");
  item.textContent = label;
  return item;
}
