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
import { timestampLabel } from "./text-utils";
import { errorMessage, requiredElement } from "./ui-utils";

export type ModelArtifactRole =
  "teacher" | "static" | "parser" | "chunker"
  | "sentence-detector" | "tokenizer" | "pos-tagger" | "lemmatizer" | "name-finder"
  | "subword-model" | "wordnet-lexicon" | "doc-categorizer";

/** One pinned file an install writes, as the catalog verifies it. */
export interface CatalogFileSummary {
  relativePath: string;
  byteSize: number;
  sha256Hex: string;
}

/** A catalog entry that would make a pipeline step ready. */
export interface CatalogFixer {
  catalogId: string;
  displayName: string;
}

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
  /** Artifact format label, e.g. "ONNX"; empty when the server did not say. */
  format: string;
  /** Pipeline steps the model unlocks once active, as wire enum names. */
  unlocks: string[];
  requiresRestart: boolean;
  files: CatalogFileSummary[];
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

/**
 * The four classic-pipeline models of one language, offered under a single
 * license review and installed with one action.
 */
export interface LanguagePack {
  /** Shared serving model id, e.g. "de-ud-gsd"; also the pack's stable key. */
  modelId: string;
  /** The language code every member reports. */
  language: string;
  /** Members in pipeline order: sentence detector, tokenizer, POS tagger, lemmatizer. */
  models: ModelCatalogSummary[];
  licenseName: string;
  licenseUri: string;
  byteSize: number;
}

/** The roles a language pack must cover, in pipeline order. */
const PACK_ROLES: readonly ModelArtifactRole[] =
  ["sentence-detector", "tokenizer", "pos-tagger", "lemmatizer"];

/**
 * Groups catalog entries into language packs: the pipeline-role models sharing
 * one serving model id, one language, and one license, with all four roles
 * present. Every other entry stays a single card.
 */
export function groupCatalogPacks(models: ModelCatalogSummary[]): {
  packs: LanguagePack[];
  singles: ModelCatalogSummary[];
} {
  const candidates = new Map<string, ModelCatalogSummary[]>();
  for (const model of models) {
    if (PACK_ROLES.includes(model.role)) {
      const members = candidates.get(model.modelId) ?? [];
      members.push(model);
      candidates.set(model.modelId, members);
    }
  }
  const packs: LanguagePack[] = [];
  const packedIds = new Set<string>();
  for (const [modelId, members] of candidates) {
    const ordered = PACK_ROLES.flatMap((role) =>
      members.filter((member) => member.role === role));
    const language = members[0]!.languages[0] ?? "";
    const licenseName = members[0]!.licenseName;
    const complete = ordered.length === PACK_ROLES.length
      && PACK_ROLES.every((role) => members.some((member) => member.role === role))
      && members.every((member) => member.licenseName === licenseName
        && (member.languages[0] ?? "") === language);
    if (!complete) {
      continue;
    }
    packs.push({
      modelId,
      language,
      models: ordered,
      licenseName,
      licenseUri: members[0]!.licenseUri,
      byteSize: ordered.reduce((total, member) => total + member.byteSize, 0),
    });
    for (const member of ordered) {
      packedIds.add(member.catalogId);
    }
  }
  return { packs, singles: models.filter((model) => !packedIds.has(model.catalogId)) };
}

/** Returns the language's English display name, or the code when unknown. */
export function languageDisplayName(code: string): string {
  if (!code) {
    return "Unknown language";
  }
  try {
    return new Intl.DisplayNames(["en"], { type: "language" }).of(code) ?? code;
  } catch {
    return code;
  }
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
  /** Runs after each catalog listing with the entries that unlock each pipeline step. */
  onCatalogLoaded?: (fixers: Map<string, CatalogFixer[]>) => void;
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
      state.textContent = installed.loaded
        ? "Installed and active"
        : restartRole(model?.role) ? "Installed, restart required" : "Installed, not loaded";
      const facts = document.createElement("span");
      const installedLabel = timestampLabel(installed.installedAt);
      facts.textContent = `${byteLabel(installed.byteSize)}`
        + (installedLabel ? ` · installed ${installedLabel}` : "");
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
      item.dataset.featureStep = step;
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
      : [listItem("No model packs are currently loaded.")]));
    this.#summary.textContent = `${ready.size} of ${PIPELINE_ORDER.length} features ready`;
  }

  /**
   * Scrolls the readiness card of a pipeline step into view and outlines every catalog
   * card whose install would make that step ready. Returns how many cards fix it.
   */
  focus(step: string): number {
    for (const outlined of this.#catalog.querySelectorAll(".is-focused")) {
      outlined.classList.remove("is-focused");
    }
    const feature = this.#features.querySelector<HTMLElement>(`[data-feature-step="${step}"]`);
    feature?.scrollIntoView?.({ block: "center" });
    let fixers = 0;
    for (const card of this.#catalog.querySelectorAll<HTMLElement>("[data-unlocks]")) {
      if ((card.dataset.unlocks ?? "").split(" ").includes(step)) {
        card.classList.add("is-focused");
        if (fixers === 0) {
          card.scrollIntoView?.({ block: "nearest" });
        }
        fixers++;
      }
    }
    return fixers;
  }

  /** Catalog entries that unlock each pipeline step, from the last listing. */
  fixers(): Map<string, CatalogFixer[]> {
    const fixers = new Map<string, CatalogFixer[]>();
    for (const model of this.#lastCatalog) {
      for (const step of model.unlocks) {
        fixers.set(step, [...(fixers.get(step) ?? []),
          { catalogId: model.catalogId, displayName: model.displayName }]);
      }
    }
    return fixers;
  }

  #lastCatalog: ModelCatalogSummary[] = [];

  private renderCatalog(
    models: ModelCatalogSummary[],
    installedModels: InstalledModelSummary[],
    installsEnabled: boolean,
  ): void {
    this.#catalog.replaceChildren();
    this.#lastCatalog = models;
    this.#callbacks.onCatalogLoaded?.(this.fixers());
    const installed = new Map(installedModels.map((model) => [model.catalogId, model]));
    const { packs, singles } = groupCatalogPacks(models);
    for (const pack of packs) {
      this.#catalog.append(this.packCard(pack, installed, installsEnabled));
    }
    for (const model of singles) {
      const active = installed.get(model.catalogId);
      const card = document.createElement("article");
      card.className = "catalog-model-card";
      card.dataset.catalogId = model.catalogId;
      card.dataset.unlocks = model.unlocks.join(" ");

      const header = document.createElement("header");
      const title = document.createElement("h5");
      title.textContent = model.displayName;
      const role = document.createElement("span");
      role.className = `catalog-role is-${model.role}`;
      role.textContent = roleLabel(model.role);
      header.append(title, role);

      const description = document.createElement("p");
      description.textContent = model.description;
      const facts = document.createElement("p");
      facts.className = "catalog-model-facts";
      facts.textContent = `${byteLabel(model.byteSize)} · ${model.licenseName}`
        + (model.dimension > 0 ? ` · ${model.dimension} dimensions` : "")
        + (model.languages.length > 0 ? ` · ${model.languages.join(", ")}` : "");
      const tags = catalogTags(model);
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
      card.append(header, description, facts, tags, references);

      if (active) {
        const state = document.createElement("strong");
        state.className = "catalog-installed-state";
        state.textContent = active.loaded
          ? "Installed and active"
          : restartRole(model.role) ? "Installed, restart required" : "Installed, not loaded";
        card.append(state);
      } else if (!installsEnabled) {
        card.append(installsOffNotice());
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
        button.textContent = installLabel(model.role);
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
      this.setStatus("Catalog browsing is available, but installs are off on this node: the "
        + "operator has not set model.catalog_root to a writable directory.");
    }
  }

  /** Builds one language-pack card: four pipeline models, one license review, one install. */
  private packCard(
    pack: LanguagePack,
    installed: Map<string, InstalledModelSummary>,
    installsEnabled: boolean,
  ): HTMLElement {
    const languageLabel = languageDisplayName(pack.language);
    const card = document.createElement("article");
    card.className = "catalog-model-card catalog-pack-card";
    card.dataset.packModelId = pack.modelId;

    const header = document.createElement("header");
    const title = document.createElement("h5");
    title.textContent = `${languageLabel} language pack`;
    const role = document.createElement("span");
    role.className = "catalog-role is-pack";
    role.textContent = "Classic pipeline";
    header.append(title, role);

    const description = document.createElement("p");
    description.textContent = `The four '${pack.modelId}' models the classic pipeline needs for `
      + `${languageLabel}: sentence detector, tokenizer, POS tagger, and lemmatizer. After a `
      + "server restart, analysis routes to them automatically when it detects the language.";
    const facts = document.createElement("p");
    facts.className = "catalog-model-facts";
    facts.textContent = `${byteLabel(pack.byteSize)} total · ${pack.licenseName} · ${pack.language}`;

    const members = document.createElement("ul");
    members.className = "catalog-pack-members";
    for (const model of pack.models) {
      const member = document.createElement("li");
      const name = document.createElement("span");
      name.textContent = `${roleLabel(model.role)} · ${byteLabel(model.byteSize)}`;
      const state = document.createElement("small");
      const active = installed.get(model.catalogId);
      state.textContent = !active
        ? "Not installed"
        : active.loaded ? "Installed and active" : "Installed, restart required";
      state.className = active ? "is-loaded" : "is-not-loaded";
      member.append(name, state);
      members.append(member);
    }

    const source = document.createElement("a");
    source.href = pack.models[0]!.sourceUri;
    source.target = "_blank";
    source.rel = "noopener noreferrer";
    source.textContent = "Model card";
    const license = document.createElement("a");
    license.href = pack.licenseUri;
    license.target = "_blank";
    license.rel = "noopener noreferrer";
    license.textContent = `${pack.licenseName} license`;
    const references = document.createElement("div");
    references.className = "catalog-model-references";
    references.append(source, license);
    card.append(header, description, facts, members, references);

    const remaining = pack.models.filter((model) => !installed.has(model.catalogId));
    if (remaining.length > 0 && !installsEnabled) {
      card.append(installsOffNotice());
      return card;
    }
    if (remaining.length === 0) {
      const state = document.createElement("strong");
      state.className = "catalog-installed-state";
      state.textContent = pack.models.every((model) => installed.get(model.catalogId)?.loaded)
        ? "Installed and active"
        : "Installed, restart required";
      card.append(state);
      return card;
    }
    const consent = document.createElement("label");
    consent.className = "catalog-consent";
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.dataset.packConsent = pack.modelId;
    const consentText = document.createElement("span");
    consentText.textContent = `I reviewed ${pack.licenseName} once and approve all `
      + `${remaining.length} node downloads.`;
    consent.append(checkbox, consentText);
    const button = document.createElement("button");
    button.type = "button";
    button.dataset.packInstall = pack.modelId;
    button.textContent = remaining.length === pack.models.length
      ? "Install all four models"
      : `Install the remaining ${remaining.length}`;
    button.disabled = true;
    checkbox.disabled = !installsEnabled;
    checkbox.addEventListener("change", () => {
      button.disabled = !installsEnabled || !checkbox.checked || this.#busy;
    });
    button.addEventListener("click", () => void this.installPack(pack, remaining, checkbox));
    card.append(consent, button);
    return card;
  }

  /** Installs a language pack's missing models one after another, then refreshes. */
  private async installPack(
    pack: LanguagePack,
    remaining: ModelCatalogSummary[],
    consent: HTMLInputElement,
  ): Promise<void> {
    if (!consent.checked || this.#busy) {
      return;
    }
    this.#busy = true;
    const label = `${languageDisplayName(pack.language)} language pack`;
    try {
      let position = 0;
      for (const model of remaining) {
        position++;
        const prefix = `${label}, ${position} of ${remaining.length}`;
        this.setStatus(`${prefix}: starting verified download for ${model.displayName}.`);
        await this.#api.install({
          catalogId: model.catalogId,
          revision: model.revision,
          licenseName: model.licenseName,
          licenseAcknowledged: true,
        }, (progress) => this.setStatus(`${prefix}: ${progressStatus(model, progress)}`));
      }
      this.setStatus(`The ${label} is installed; restart the server to activate `
        + `the '${pack.language}' pipeline.`);
      await this.initialize();
    } catch (error) {
      this.setStatus(errorMessage(error, `Could not install the ${label}.`), true);
    } finally {
      this.#busy = false;
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
      this.setStatus(installed.loaded
        ? `${model.displayName} is installed and active on this server node.`
        : `${model.displayName} is installed; restart required before it becomes active.`);
      if (model.role === "static" && installed.loaded) {
        this.#callbacks.onEmbeddingModelInstalled(model.modelId, model.displayName);
        this.#status.append(" ", jumpButton("analysis", "Use it on the Analyze tab"));
      } else if (model.role === "teacher" && installed.loaded) {
        this.#callbacks.onTeacherInstalled();
        this.#status.append(" ", jumpButton("trainer", "Distill with it on the Trainer tab"));
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
      : model.role === "MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING" ? "static"
      : model.role === "MODEL_ARTIFACT_ROLE_PARSER" ? "parser"
      : model.role === "MODEL_ARTIFACT_ROLE_CHUNKER" ? "chunker"
      : model.role === "MODEL_ARTIFACT_ROLE_SENTENCE_DETECTOR" ? "sentence-detector"
      : model.role === "MODEL_ARTIFACT_ROLE_TOKENIZER" ? "tokenizer"
      : model.role === "MODEL_ARTIFACT_ROLE_POS_TAGGER" ? "pos-tagger"
      : model.role === "MODEL_ARTIFACT_ROLE_LEMMATIZER" ? "lemmatizer"
      : model.role === "MODEL_ARTIFACT_ROLE_NAME_FINDER" ? "name-finder"
      : model.role === "MODEL_ARTIFACT_ROLE_SUBWORD_MODEL" ? "subword-model"
      : model.role === "MODEL_ARTIFACT_ROLE_WORDNET_LEXICON" ? "wordnet-lexicon"
      : model.role === "MODEL_ARTIFACT_ROLE_DOC_CATEGORIZER" ? "doc-categorizer" : undefined;
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
      format: formatLabel(optionalString(model.format)),
      unlocks: asArray(model.unlocks).filter((item): item is string => typeof item === "string"),
      requiresRestart: model.requiresRestart === true,
      files: asArray(model.files).flatMap((value) => {
        const file = asRecord(value);
        const relativePath = optionalString(file?.relativePath);
        return file && relativePath
          ? [{ relativePath, byteSize: count(file.byteSize), sha256Hex: optionalString(file.sha256Hex) }]
          : [];
      }),
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

function restartRole(role: ModelArtifactRole | undefined): boolean {
  return role === "parser" || role === "chunker"
    || role === "sentence-detector" || role === "tokenizer"
    || role === "pos-tagger" || role === "lemmatizer" || role === "name-finder";
}

/** Wire format enum to the label on a card. */
function formatLabel(format: string): string {
  const labels: Record<string, string> = {
    MODEL_ARTIFACT_FORMAT_OPENNLP_BIN: "OpenNLP .bin",
    MODEL_ARTIFACT_FORMAT_ONNX: "ONNX",
    MODEL_ARTIFACT_FORMAT_SENTENCEPIECE: "SentencePiece",
    MODEL_ARTIFACT_FORMAT_WN_LMF: "WN-LMF",
    MODEL_ARTIFACT_FORMAT_SAFETENSORS: "Safetensors",
  };
  return labels[format] ?? "";
}

function roleLabel(role: ModelArtifactRole): string {
  if (role === "static") {
    return "Ready-to-serve static table";
  }
  if (role === "teacher") {
    return "Training teacher";
  }
  const labels: Record<string, string> = {
    parser: "Constituency parser",
    chunker: "Phrase chunker",
    "sentence-detector": "Sentence detector",
    tokenizer: "Tokenizer",
    "pos-tagger": "POS tagger",
    lemmatizer: "Lemmatizer",
    "name-finder": "Name finder",
    "subword-model": "SentencePiece model",
    "wordnet-lexicon": "WordNet lexicon",
    "doc-categorizer": "Document categorizer",
  };
  return labels[role] ?? role;
}

function installLabel(role: ModelArtifactRole): string {
  const labels: Record<string, string> = {
    static: "Download and activate",
    teacher: "Download teacher",
    parser: "Download parser",
    chunker: "Download chunker",
    "sentence-detector": "Download sentence detector",
    tokenizer: "Download tokenizer",
    "pos-tagger": "Download POS tagger",
    lemmatizer: "Download lemmatizer",
  };
  return labels[role] ?? "Download model";
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

/** Names for unlocked steps that are not selectable features on the Analyze tab. */
const UNLOCK_NAMES: Readonly<Record<string, string>> = {
  PIPELINE_STEP_CHUNK: "Chunk embeddings",
};

/** The tag row of a catalog card: what installing unlocks, its format, and when it serves. */
function catalogTags(model: ModelCatalogSummary): HTMLUListElement {
  const tags = document.createElement("ul");
  tags.className = "catalog-tags";
  tags.setAttribute("aria-label", "What this model unlocks");
  const labels: string[] = [];
  if (model.role === "teacher") {
    labels.push("Unlocks: distilling on the Trainer tab");
  }
  for (const step of model.unlocks) {
    labels.push(`Unlocks: ${FEATURE_NAMES[step] ?? UNLOCK_NAMES[step] ?? step}`);
  }
  if (model.format) {
    labels.push(`Format: ${model.format}`);
  }
  labels.push(model.requiresRestart ? "Serves after a restart" : "Serves on install");
  for (const label of labels) {
    const tag = document.createElement("li");
    tag.textContent = label;
    tags.append(tag);
  }
  return tags;
}

/** The line a card shows in place of its install controls when the node cannot install. */
function installsOffNotice(): HTMLParagraphElement {
  const notice = document.createElement("p");
  notice.className = "catalog-installs-off";
  notice.textContent = "Installs are off on this node: the operator has not set model.catalog_root.";
  return notice;
}

/** A link-styled button that jumps to another workbench. */
function jumpButton(target: string, label: string): HTMLButtonElement {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "link-button";
  button.dataset.workbenchJump = target;
  button.textContent = label;
  return button;
}
