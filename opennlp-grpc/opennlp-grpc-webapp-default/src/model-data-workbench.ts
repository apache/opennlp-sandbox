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

import type { InstallModelRequest } from "./api";
import {
  FEATURE_NAMES,
  PIPELINE_ORDER,
  type AnalysisCapabilities,
} from "./analysis-config";
import { errorMessage, requiredElement } from "./ui-utils";

export type ModelArtifactRole = "teacher" | "static";

export interface ModelCatalogSummary {
  catalogId: string;
  displayName: string;
  role: ModelArtifactRole;
  modelId: string;
  sourceUri: string;
  revision: string;
  licenseName: string;
  licenseUri: string;
  byteSize: number;
  dimension: number;
  languages: string[];
  description: string;
}

export interface InstalledModelSummary {
  catalogId: string;
  artifactHash: string;
  byteSize: number;
  installedAt: string;
  loaded: boolean;
}

export interface ModelInstallProgress {
  stage: string;
  message: string;
  completedBytes: number;
  totalBytes: number;
}

export interface ModelCatalogApi {
  listCatalog(): Promise<{ models: ModelCatalogSummary[]; installsEnabled: boolean }>;
  listInstalled(): Promise<InstalledModelSummary[]>;
  install(
    request: InstallModelRequest,
    onProgress: (progress: ModelInstallProgress) => void,
  ): Promise<InstalledModelSummary>;
}

export interface ModelCatalogCallbacks {
  onEmbeddingModelInstalled(modelId: string, displayName: string): void;
  onTeacherInstalled(): void;
}

/** Renders model, data, and node-local catalog readiness. */
export class ModelDataWorkbench {
  readonly #api: ModelCatalogApi;
  readonly #callbacks: ModelCatalogCallbacks;
  readonly #summary = requiredElement<HTMLElement>("resource-summary");
  readonly #features = requiredElement<HTMLElement>("resource-feature-list");
  readonly #bundles = requiredElement<HTMLUListElement>("resource-bundle-list");
  readonly #command = requiredElement<HTMLElement>("resource-install-command");
  readonly #copy = requiredElement<HTMLButtonElement>("copy-resource-command");
  readonly #status = requiredElement<HTMLElement>("resource-install-status");
  readonly #catalog = requiredElement<HTMLElement>("resource-model-catalog");
  readonly #installedModels = requiredElement<HTMLElement>("resource-installed-models");
  #busy = false;

  constructor(api: ModelCatalogApi, callbacks: ModelCatalogCallbacks) {
    this.#api = api;
    this.#callbacks = callbacks;
    this.#copy.addEventListener("click", () => void this.copyCommand());
  }

  /** Loads the immutable model catalog and this node's installed-model inventory. */
  async initialize(): Promise<void> {
    try {
      const [catalog, installed] = await Promise.all([
        this.#api.listCatalog(),
        this.#api.listInstalled(),
      ]);
      this.publishInstalledStaticModels(catalog.models, installed);
      this.renderCatalog(catalog.models, installed, catalog.installsEnabled);
      this.renderInstalledModels(catalog.models, installed);
    } catch (error) {
      this.#catalog.textContent = errorMessage(error, "Could not load the model catalog.");
      this.#catalog.classList.add("is-error");
    }
  }

  private renderInstalledModels(
    catalogModels: ModelCatalogSummary[],
    installedModels: InstalledModelSummary[],
  ): void {
    this.#installedModels.replaceChildren();
    if (installedModels.length === 0) {
      const empty = document.createElement("p");
      empty.className = "empty-message";
      empty.textContent = "No catalog models have been downloaded to this node.";
      this.#installedModels.append(empty);
      return;
    }
    const catalog = new Map(catalogModels.map((model) => [model.catalogId, model]));
    for (const installed of installedModels) {
      const model = catalog.get(installed.catalogId);
      const row = document.createElement("article");
      row.className = "downloaded-model-row";

      const heading = document.createElement("strong");
      heading.textContent = model?.displayName ?? installed.catalogId;
      const state = document.createElement("span");
      state.className = installed.loaded ? "is-loaded" : "is-not-loaded";
      state.textContent = installed.loaded ? "Installed and active" : "Installed, not loaded";
      const facts = document.createElement("span");
      facts.textContent = `${byteLabel(installed.byteSize)}`
        + (installed.installedAt ? ` · installed ${installed.installedAt}` : "");
      const hash = document.createElement("code");
      hash.textContent = installed.artifactHash || "Artifact hash unavailable";
      row.append(heading, state, facts, hash);
      this.#installedModels.append(row);
    }
  }

  /** Publishes static routes restored before the UI connected to this node. */
  private publishInstalledStaticModels(
    models: ModelCatalogSummary[],
    installedModels: InstalledModelSummary[],
  ): void {
    const installed = new Set(installedModels
      .filter((model) => model.loaded)
      .map((model) => model.catalogId));
    for (const model of models) {
      if (model.role === "static" && installed.has(model.catalogId)) {
        this.#callbacks.onEmbeddingModelInstalled(model.modelId, model.displayName);
      }
    }
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

  private renderCatalog(
    models: ModelCatalogSummary[],
    installedModels: InstalledModelSummary[],
    installsEnabled: boolean,
  ): void {
    this.#catalog.replaceChildren();
    const installed = new Map(installedModels.map((model) => [model.catalogId, model]));
    for (const model of models) {
      const active = installed.get(model.catalogId);
      const card = document.createElement("article");
      card.className = "catalog-model-card";
      card.dataset.catalogId = model.catalogId;

      const header = document.createElement("header");
      const title = document.createElement("h5");
      title.textContent = model.displayName;
      const role = document.createElement("span");
      role.className = `catalog-role is-${model.role}`;
      role.textContent = model.role === "static" ? "Ready-to-serve static table" : "Training teacher";
      header.append(title, role);

      const description = document.createElement("p");
      description.textContent = model.description;
      const facts = document.createElement("p");
      facts.className = "catalog-model-facts";
      facts.textContent = `${byteLabel(model.byteSize)} · ${model.licenseName}`
        + (model.dimension > 0 ? ` · ${model.dimension} dimensions` : "")
        + (model.languages.length > 0 ? ` · ${model.languages.join(", ")}` : "");
      const source = document.createElement("a");
      source.href = model.sourceUri;
      source.target = "_blank";
      source.rel = "noopener noreferrer";
      source.textContent = "Model card";
      const license = document.createElement("a");
      license.href = model.licenseUri;
      license.target = "_blank";
      license.rel = "noopener noreferrer";
      license.textContent = `${model.licenseName} license`;
      const references = document.createElement("div");
      references.className = "catalog-model-references";
      references.append(source, license);
      card.append(header, description, facts, references);

      if (active) {
        const state = document.createElement("strong");
        state.className = "catalog-installed-state";
        state.textContent = active.loaded ? "Installed and active" : "Installed, not loaded";
        card.append(state);
      } else {
        const consent = document.createElement("label");
        consent.className = "catalog-consent";
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.dataset.catalogConsent = model.catalogId;
        const consentText = document.createElement("span");
        consentText.textContent = `I reviewed ${model.licenseName} and approve this node download.`;
        consent.append(checkbox, consentText);
        const button = document.createElement("button");
        button.type = "button";
        button.dataset.catalogInstall = model.catalogId;
        button.textContent = model.role === "static" ? "Download and activate" : "Download teacher";
        button.disabled = true;
        checkbox.disabled = !installsEnabled;
        checkbox.addEventListener("change", () => {
          button.disabled = !installsEnabled || !checkbox.checked || this.#busy;
        });
        button.addEventListener("click", () => void this.install(model, checkbox));
        card.append(consent, button);
      }
      this.#catalog.append(card);
    }
    if (models.length === 0) {
      this.#catalog.textContent = "This build does not publish a standard model catalog.";
    } else if (!installsEnabled) {
      this.setStatus("Catalog browsing is available. Configure model.catalog_root to enable node downloads.");
    }
  }

  private async install(model: ModelCatalogSummary, consent: HTMLInputElement): Promise<void> {
    if (!consent.checked || this.#busy) {
      return;
    }
    this.#busy = true;
    this.setStatus(`Starting verified download for ${model.displayName}.`);
    try {
      const installed = await this.#api.install({
        catalogId: model.catalogId,
        revision: model.revision,
        licenseName: model.licenseName,
        licenseAcknowledged: true,
      }, (progress) => this.setStatus(progressStatus(model, progress)));
      this.setStatus(`${model.displayName} is installed and active on this server node.`);
      if (model.role === "static" && installed.loaded) {
        this.#callbacks.onEmbeddingModelInstalled(model.modelId, model.displayName);
      } else if (model.role === "teacher" && installed.loaded) {
        this.#callbacks.onTeacherInstalled();
      }
      await this.initialize();
    } catch (error) {
      this.setStatus(errorMessage(error, `Could not install ${model.displayName}.`), true);
    } finally {
      this.#busy = false;
    }
  }

  private setStatus(text: string, error = false): void {
    this.#status.textContent = text;
    this.#status.classList.toggle("is-error", error);
  }

  private async copyCommand(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.#command.textContent ?? "");
      this.setStatus("Installer command copied. Replace the source, checksum, and target values.");
    } catch (error) {
      this.setStatus(errorMessage(error, "Could not copy the installer command."), true);
    }
  }
}

/** Reads the catalog response and rejects malformed identities. */
export function readModelCatalog(
  value: unknown,
): { models: ModelCatalogSummary[]; installsEnabled: boolean } {
  const body = asRecord(value);
  const models = asArray(body.models).map((entry): ModelCatalogSummary => {
    const model = asRecord(entry);
    const catalogId = requiredString(model.catalogId, "catalog model id");
    const modelId = requiredString(model.modelId, "catalog serving model id");
    const role: ModelArtifactRole | undefined =
      model.role === "MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER" ? "teacher"
      : model.role === "MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING" ? "static" : undefined;
    if (!role) {
      throw new Error(`Catalog model '${catalogId}' has an unsupported role.`);
    }
    const sourceUri = requiredHttpsUri(
      model.sourceUri,
      `Catalog model '${catalogId}' source URI`,
    );
    const revision = requiredString(model.revision, `Catalog model '${catalogId}' revision`);
    const licenseName = requiredString(
      model.licenseName,
      `Catalog model '${catalogId}' license name`,
    );
    const licenseUri = requiredHttpsUri(
      model.licenseUri,
      `Catalog model '${catalogId}' license URI`,
    );
    return {
      catalogId,
      displayName: optionalString(model.displayName) || catalogId,
      role,
      modelId,
      sourceUri,
      revision,
      licenseName,
      licenseUri,
      byteSize: count(model.byteSize),
      dimension: count(model.dimension),
      languages: asArray(model.languages).filter((item): item is string => typeof item === "string"),
      description: optionalString(model.description),
    };
  });
  return { models, installsEnabled: body.installsEnabled === true };
}

function requiredHttpsUri(value: unknown, description: string): string {
  const text = requiredString(value, description);
  let uri: URL;
  try {
    uri = new URL(text);
  } catch {
    throw new Error(`${description} must be an absolute HTTPS URI.`);
  }
  if (uri.protocol !== "https:" || uri.username || uri.password || uri.hash) {
    throw new Error(
      `${description} must be an absolute HTTPS URI without credentials or a fragment.`,
    );
  }
  return uri.href;
}

/** Reads this server node's installed-model inventory. */
export function readInstalledModels(value: unknown): InstalledModelSummary[] {
  return asArray(asRecord(value).models).map((entry) => {
    const installed = asRecord(entry);
    return {
      catalogId: requiredString(asRecord(installed.catalog).catalogId, "installed catalog id"),
      artifactHash: optionalString(installed.artifactHash),
      byteSize: count(installed.byteSize),
      installedAt: timestampText(installed.installedAt),
      loaded: installed.loaded === true,
    };
  });
}

/** Reads one streamed install progress object. */
export function readModelInstallProgress(value: unknown): ModelInstallProgress {
  const progress = asRecord(value);
  return {
    stage: optionalString(progress.stage),
    message: optionalString(progress.message),
    completedBytes: count(progress.completedBytes),
    totalBytes: count(progress.totalBytes),
  };
}

/** Reads one installed descriptor returned as the terminal stream frame. */
export function readInstalledModel(value: unknown): InstalledModelSummary {
  return readInstalledModels({ models: [value] })[0]!;
}

function progressStatus(model: ModelCatalogSummary, progress: ModelInstallProgress): string {
  const detail = progress.message || progress.stage || "Installing";
  return progress.totalBytes > 0
    ? `${detail}: ${byteLabel(progress.completedBytes)} of ${byteLabel(progress.totalBytes)}`
    : `${detail} for ${model.displayName}`;
}

function byteLabel(bytes: number): string {
  if (bytes >= 1_048_576) {
    return `${(bytes / 1_048_576).toFixed(1)} MiB`;
  }
  if (bytes >= 1_024) {
    return `${(bytes / 1_024).toFixed(1)} KiB`;
  }
  return `${bytes} bytes`;
}

function timestampText(value: unknown): string {
  if (typeof value === "string") {
    return value;
  }
  const timestamp = asRecord(value);
  return optionalString(timestamp.seconds);
}

function requiredString(value: unknown, label: string): string {
  const text = optionalString(value);
  if (!text) {
    throw new Error(`The server returned an invalid ${label}.`);
  }
  return text;
}

function optionalString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function count(value: unknown): number {
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;
}

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null ? value as Record<string, unknown> : {};
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function listItem(text: string): HTMLLIElement {
  const item = document.createElement("li");
  item.textContent = text;
  return item;
}
