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
import type { CatalogFixer } from "./model-data-workbench";
import { replaceCharacter, withoutPrefix } from "./text-utils";
import { requiredElement } from "./ui-utils";

export class AnalysisControls {
  readonly #profile = requiredElement<HTMLSelectElement>("profile-select");
  readonly #embeddingModel = requiredElement<HTMLSelectElement>("embedding-model-select");
  readonly #pipelineLanguage = requiredElement<HTMLSelectElement>("pipeline-language-select");
  readonly #posTagFormat = requiredElement<HTMLSelectElement>("pos-tag-format-select");
  readonly #sentenceChunks = requiredElement<HTMLInputElement>("sentence-chunks");
  readonly #tokenChunks = requiredElement<HTMLInputElement>("token-chunks");
  readonly #tokenChunkSize = requiredElement<HTMLInputElement>("token-chunk-size");
  readonly #tokenChunkOverlap = requiredElement<HTMLInputElement>("token-chunk-overlap");
  readonly #enabledFeatures = requiredElement<HTMLUListElement>("enabled-feature-list");
  readonly #featureOptions = requiredElement<HTMLDivElement>("feature-options");
  /** The checklist wrapper; absent in unit fixtures that mount the grid alone. */
  readonly #featurePicker = document.getElementById("feature-picker");
  /** Where a browned-out feature explains itself; absent in fixtures that mount the grid alone. */
  readonly #availability = document.getElementById("feature-availability");
  #fixers = new Map<string, CatalogFixer[]>();
  readonly #modelList = requiredElement<HTMLUListElement>("model-list");
  readonly #onChange: () => void;
  #capabilities: AnalysisCapabilities = discoverAnalysisCapabilities(undefined, undefined);
  #customSteps = new Set<string>();
  #trainedModels: DiscoveryOption[] = [];

  constructor(onChange: () => void) {
    this.#onChange = onChange;
    this.#profile.addEventListener("change", () => {
      this.renderFeatureOptions();
      this.renderFeatures();
      this.#onChange();
    });
    this.#embeddingModel.addEventListener("change", () => this.renderFeatures());
    this.#pipelineLanguage.addEventListener("change", () => this.#onChange());
    this.#posTagFormat.addEventListener("change", () => this.#onChange());
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
    this.populateEmbeddingModels(this.mergedEmbeddingModels());
    this.populateModelList(this.#capabilities.bundles);
    this.populatePipelineLanguages(this.#capabilities.pipelineLanguages);
    this.#customSteps = new Set(this.#capabilities.maxSteps);
    this.renderFeatureOptions();
    this.renderFeatures();
    return this.#capabilities;
  }

  /** Offers the configured language pipelines beside automatic routing. */
  private populatePipelineLanguages(pipelines: DiscoveryOption[]): void {
    const selected = this.#pipelineLanguage.value;
    this.#pipelineLanguage.replaceChildren();
    this.#pipelineLanguage.add(new Option("Automatic (route by detected language)", ""));
    for (const pipeline of pipelines) {
      this.#pipelineLanguage.add(new Option(pipeline.label, pipeline.id));
    }
    this.#pipelineLanguage.disabled = pipelines.length === 0;
    if (pipelines.some((pipeline) => pipeline.id === selected)) {
      this.#pipelineLanguage.value = selected;
    }
  }

  /**
   * Merges runtime-trained embedding models into the selector next to the
   * startup-configured ones, keeping the current selection when it survives.
   */
  setTrainedEmbeddingModels(models: DiscoveryOption[]): void {
    const selected = this.#embeddingModel.value;
    this.#trainedModels = models;
    const merged = this.mergedEmbeddingModels();
    this.populateEmbeddingModels(merged);
    if (merged.some((option) => option.id === selected)) {
      this.#embeddingModel.value = selected;
    }
    this.renderFeatures();
  }

  /**
   * Selects the given embedding model when the selector offers it.
   *
   * @return whether the model is offered and now selected
   */
  selectEmbeddingModel(modelId: string): boolean {
    if (!this.mergedEmbeddingModels().some((option) => option.id === modelId)) {
      return false;
    }
    this.#embeddingModel.value = modelId;
    this.renderFeatures();
    return true;
  }

  /**
   * Combines the startup-configured embedding models with the runtime-trained
   * ones, so a later {@link AnalysisControls#configure} call cannot drop models
   * that arrived first through {@link AnalysisControls#setTrainedEmbeddingModels}.
   */
  private mergedEmbeddingModels(): DiscoveryOption[] {
    const configured = this.#capabilities.embeddingModels;
    return [
      ...configured,
      ...this.#trainedModels.filter(
        (model) => !configured.some((option) => option.id === model.id)),
    ];
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
      pipelineLanguage: this.#pipelineLanguage.value || undefined,
      posTagFormat: this.#posTagFormat.value || undefined,
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
      new Option("Server default profile", "automatic"),
    );
    for (const option of options) {
      this.#profile.add(new Option(`Server profile: ${option.label}`, `profile:${option.id}`));
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
      labels.push("Server default profile");
    } else {
      labels.push(`Server profile '${withoutPrefix(this.#profile.value, "profile:")}'`);
    }
    if (this.#sentenceChunks.checked) {
      labels.push("Sentence chunks");
    }
    if (this.#tokenChunks.checked) {
      labels.push("Token windows");
    }
    const unavailable = PIPELINE_ORDER.filter((step) =>
      this.#capabilities.supportedSteps.includes(step) && !this.#capabilities.maxSteps.includes(step));
    this.#enabledFeatures.replaceChildren(
      ...labels.map(featureChip),
      ...unavailable.map((step) => this.unavailableChip(step)));
  }

  /**
   * Records which catalog entries would make each pipeline step ready, so a browned-out
   * feature can offer the install instead of naming a configuration key.
   */
  setFeatureFixers(fixers: Map<string, CatalogFixer[]>): void {
    this.#fixers = fixers;
    this.renderFeatures();
  }

  /** A muted, clickable chip for a step this server build supports but cannot run yet. */
  private unavailableChip(step: string): HTMLLIElement {
    const item = document.createElement("li");
    item.className = "feature-chip is-unavailable";
    const button = document.createElement("button");
    button.type = "button";
    button.dataset.unavailableStep = step;
    button.textContent = featureLabel(step);
    button.title = "Not available on this server. Select to see why.";
    button.addEventListener("click", () => this.explain(step));
    item.append(button);
    return item;
  }

  /**
   * Explains one unavailable step with a fixed shape: the reason, and one of three fixes.
   * A catalog entry that unlocks it offers a jump that lands on that card; a supported
   * step with no catalog entry names the operator setting; anything else is not in this
   * build.
   */
  explain(step: string): void {
    const panel = this.#availability;
    if (!panel) {
      return;
    }
    const label = featureLabel(step);
    const fixers = this.#fixers.get(step) ?? [];
    const supported = this.#capabilities.supportedSteps.includes(step);
    panel.replaceChildren();
    panel.hidden = false;
    const heading = document.createElement("p");
    heading.className = "feature-availability-title";
    heading.textContent = `${label} is not available on this server.`;
    const reason = document.createElement("p");
    reason.textContent = `Reason: ${!supported
      ? "this server build does not include it."
      : "no model or resource that serves it is loaded."}`;
    const fix = document.createElement("p");
    panel.append(heading, reason, fix);
    if (fixers.length > 0) {
      fix.textContent = `Fix: install ${fixers.map((fixer) => fixer.displayName).join(" or ")}`
        + " from the model catalog. ";
      const jump = document.createElement("button");
      jump.type = "button";
      jump.className = "link-button";
      jump.dataset.workbenchJump = "models";
      jump.dataset.workbenchFocus = step;
      jump.textContent = "Open Models & data";
      fix.append(jump);
    } else if (supported) {
      fix.textContent = `Fix: ask the operator to set ${CONFIGURATION_KEYS[step] ?? "its model path"}`
        + " in server.properties and restart.";
    } else {
      fix.textContent = "Fix: none from here; a different server build is needed.";
    }
  }

  private renderFeatureOptions(): void {
    const mode = this.#profile.value;
    if (this.#featurePicker) {
      this.#featurePicker.hidden = mode !== "custom";
    }
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

/**
 * The operator setting that serves each step when no catalog entry can, transcribed from
 * the service's own "not configured" errors.
 */
export const CONFIGURATION_KEYS: Readonly<Record<string, string>> = {
  PIPELINE_STEP_NER: "model.name_finder.<entity_type>.path",
  PIPELINE_STEP_GEOCODE: "a name finder, model.name_finder.<entity_type>.path",
  PIPELINE_STEP_PARSE: "model.parser.<model_id>.path",
  PIPELINE_STEP_SYNTACTIC_CHUNK: "model.chunker.<model_id>.path",
  PIPELINE_STEP_SUBWORD_TOKENIZE: "model.subword.<model_id>.path",
  PIPELINE_STEP_EXPAND: "model.wordnet.<model_id>.path",
  PIPELINE_STEP_DOC_CATEGORIZE: "model.doccat.<model_id>.path",
  PIPELINE_STEP_SENTIMENT: "model.sentiment.<model_id>.path or model.sentiment_dl.<model_id>.path",
  PIPELINE_STEP_EMBED: "model.embedder.<model_id>.<backend>",
  PIPELINE_STEP_CHUNK: "model.embedder.<model_id>.<backend>",
  PIPELINE_STEP_SENTENCE_DETECT: "model.pipeline.<lang>.sentence_detector.path",
  PIPELINE_STEP_TOKENIZE: "model.pipeline.<lang>.tokenizer.path",
  PIPELINE_STEP_POS_TAG: "model.pipeline.<lang>.pos_tagger.path",
  PIPELINE_STEP_LEMMATIZE: "model.pipeline.<lang>.lemmatizer.path",
  PIPELINE_STEP_STEM: "a language pack, model.pipeline.<lang>.tokenizer.path",
};

/** The feature name a person reads for a pipeline step. */
function featureLabel(step: string): string {
  return FEATURE_NAMES[step] ?? readableStep(step);
}

function readableStep(step: string): string {
  return replaceCharacter(withoutPrefix(step, "PIPELINE_STEP_"), "_", " ");
}

function featureChip(label: string): HTMLLIElement {
  const item = document.createElement("li");
  item.textContent = label;
  return item;
}
