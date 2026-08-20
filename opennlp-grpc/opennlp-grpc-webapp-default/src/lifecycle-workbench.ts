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

import type { ReindexIndexRequest, SetCollectionRequest } from "./api";
import type { CollectionEventView, CollectionView } from "./collection-adapter";
import type { IndexAliasView, SearchIndex, SearchProviderInstance } from "./search-adapter";
import { formatInteger } from "./text-utils";
import { emptyMessage, errorMessage, requiredElement } from "./ui-utils";
import type { TrainedModelSummary } from "./vocabulary-trainer";

export interface LifecycleApi {
  listIndexes(): Promise<SearchIndex[]>;
  listProviders(): Promise<SearchProviderInstance[]>;
  listAliases(): Promise<IndexAliasView[]>;
  persist(indexId: string): Promise<SearchIndex | undefined>;
  seal(indexId: string): Promise<SearchIndex | undefined>;
  reindex(request: ReindexIndexRequest): Promise<SearchIndex | undefined>;
  setAlias(alias: string, indexId: string): Promise<void>;
  deleteAlias(alias: string): Promise<void>;
  listStaticModels(): Promise<TrainedModelSummary[]>;
  listCollections(): Promise<CollectionView[]>;
  getCollection(collectionId: string): Promise<CollectionView | undefined>;
  setCollection(request: SetCollectionRequest): Promise<CollectionView | undefined>;
  deleteCollection(collectionId: string): Promise<boolean>;
  /** Resolves when the bounded gateway watch ends; the workbench reconnects. */
  watchCollection(
    collectionId: string,
    onEvent: (event: CollectionEventView | undefined) => void,
  ): Promise<void>;
}

const WATCH_RETRY_MILLIS = 3000;

/**
 * The lifecycle workbench: persist, seal, alias, and blue/green reindex
 * dynamic workspace indexes, and manage collections whose drift meter follows
 * the server-streaming watch through the gateway's reconnecting NDJSON feed.
 */
export class LifecycleWorkbench {
  readonly #api: LifecycleApi;

  readonly #status = requiredElement<HTMLElement>("lifecycle-status");
  readonly #indexSelect = requiredElement<HTMLSelectElement>("lifecycle-index-select");
  readonly #refreshButton = requiredElement<HTMLButtonElement>("lifecycle-refresh-button");
  readonly #indexFacts = requiredElement<HTMLDListElement>("lifecycle-index-facts");
  readonly #persistButton = requiredElement<HTMLButtonElement>("lifecycle-persist-button");
  readonly #sealButton = requiredElement<HTMLButtonElement>("lifecycle-seal-button");
  readonly #aliasInput = requiredElement<HTMLInputElement>("lifecycle-alias-input");
  readonly #setAliasButton = requiredElement<HTMLButtonElement>("lifecycle-set-alias-button");
  readonly #aliasList = requiredElement<HTMLElement>("lifecycle-alias-list");
  readonly #providerList = requiredElement<HTMLElement>("lifecycle-provider-list");
  readonly #reindexModel = requiredElement<HTMLSelectElement>("lifecycle-reindex-model");
  readonly #reindexProvider = requiredElement<HTMLSelectElement>("lifecycle-reindex-provider");
  readonly #reindexAlias = requiredElement<HTMLInputElement>("lifecycle-reindex-alias");
  readonly #reindexButton = requiredElement<HTMLButtonElement>("lifecycle-reindex-button");

  readonly #collectionSelect = requiredElement<HTMLSelectElement>("collection-select");
  readonly #collectionId = requiredElement<HTMLInputElement>("collection-id");
  readonly #collectionName = requiredElement<HTMLInputElement>("collection-name");
  readonly #collectionMembers = requiredElement<HTMLSelectElement>("collection-members");
  readonly #collectionVocabulary = requiredElement<HTMLInputElement>("collection-vocabulary-id");
  readonly #collectionDictionary = requiredElement<HTMLInputElement>("collection-dictionary-id");
  readonly #collectionModel = requiredElement<HTMLSelectElement>("collection-model-id");
  readonly #collectionThreshold = requiredElement<HTMLInputElement>("collection-threshold");
  readonly #collectionSaveButton = requiredElement<HTMLButtonElement>("collection-save-button");
  readonly #collectionDeleteButton =
    requiredElement<HTMLButtonElement>("collection-delete-button");
  readonly #driftStats = requiredElement<HTMLDListElement>("collection-drift-stats");
  readonly #coverageBar = requiredElement<HTMLElement>("collection-coverage-bar");
  readonly #coverageLabel = requiredElement<HTMLElement>("collection-coverage-label");
  readonly #ledger = requiredElement<HTMLElement>("collection-ledger");
  readonly #watchStatus = requiredElement<HTMLElement>("collection-watch-status");
  readonly #eventLog = requiredElement<HTMLElement>("collection-event-log");

  #indexes: SearchIndex[] = [];
  #busy = false;
  #watchGeneration = 0;

  constructor(api: LifecycleApi) {
    this.#api = api;
    this.#refreshButton.addEventListener("click", () => void this.refresh());
    this.#indexSelect.addEventListener("change", () => {
      this.renderIndexFacts();
      this.updateControls();
    });
    this.#persistButton.addEventListener("click", () => void this.persistSelected(false));
    this.#sealButton.addEventListener("click", () => void this.persistSelected(true));
    this.#setAliasButton.addEventListener("click", () => void this.setAlias());
    this.#reindexButton.addEventListener("click", () => void this.reindexSelected());
    this.#collectionSelect.addEventListener("change", () => void this.openSelectedCollection());
    this.#collectionSaveButton.addEventListener("click", () => void this.saveCollection());
    this.#collectionDeleteButton.addEventListener("click", () => void this.deleteCollection());
  }

  /** Loads workspaces, providers, aliases, models, and collections once at startup. */
  async initialize(): Promise<void> {
    await this.refresh();
  }

  private async refresh(): Promise<void> {
    try {
      const [indexes, providers, aliases, models, collections] = await Promise.all([
        this.#api.listIndexes(),
        this.#api.listProviders(),
        this.#api.listAliases(),
        this.#api.listStaticModels(),
        this.#api.listCollections(),
      ]);
      this.#indexes = indexes.filter((index) => !index.immutable);
      this.renderIndexOptions();
      this.renderProviders(providers);
      this.renderAliases(aliases);
      this.renderModels(models);
      this.renderCollectionOptions(collections);
      this.setStatus(this.#indexes.length === 0
        ? "Index documents in Workspace search to create a dynamic workspace first."
        : `${this.#indexes.length} dynamic ${this.#indexes.length === 1 ? "workspace" : "workspaces"} available.`);
    } catch (error) {
      this.setStatus(errorMessage(error, "Could not load the lifecycle catalog."), true);
    }
    this.updateControls();
  }

  private renderIndexOptions(): void {
    const selected = this.#indexSelect.value;
    this.#indexSelect.replaceChildren();
    if (this.#indexes.length === 0) {
      this.#indexSelect.add(new Option("No dynamic workspaces", ""));
      this.#indexSelect.disabled = true;
    } else {
      for (const index of this.#indexes) {
        this.#indexSelect.add(new Option(`${index.label} (${index.id})`, index.id));
      }
      this.#indexSelect.disabled = false;
      if (this.#indexes.some((index) => index.id === selected)) {
        this.#indexSelect.value = selected;
      }
    }
    const members = new Set(Array.from(this.#collectionMembers.selectedOptions)
      .map((option) => option.value));
    this.#collectionMembers.replaceChildren();
    for (const index of this.#indexes) {
      const option = new Option(`${index.label} (${index.id})`, index.id);
      option.selected = members.has(index.id);
      this.#collectionMembers.add(option);
    }
    this.renderIndexFacts();
  }

  private renderIndexFacts(): void {
    const index = this.selectedIndex();
    this.#indexFacts.replaceChildren();
    if (!index) {
      return;
    }
    addFact(this.#indexFacts, "Provider", index.providerId);
    addFact(this.#indexFacts, "Embedding model", index.modelId);
    addFact(this.#indexFacts, "Vector space", index.vectorSpaceId);
    addFact(this.#indexFacts, "Chunks", formatInteger(index.size ?? 0));
    addFact(this.#indexFacts, "Sealed", index.immutable ? "yes" : "no");
  }

  private renderProviders(providers: SearchProviderInstance[]): void {
    this.#providerList.replaceChildren();
    if (providers.length === 0) {
      this.#providerList.append(emptyMessage("No provider instances reported."));
    }
    const keepSource = this.#reindexProvider.value;
    this.#reindexProvider.replaceChildren(new Option("Keep the source provider", ""));
    for (const provider of providers) {
      const row = document.createElement("div");
      row.className = "lifecycle-provider-row";
      const name = document.createElement("strong");
      name.textContent = provider.instanceId;
      const capabilities = document.createElement("span");
      capabilities.textContent = provider.capabilities.join(" · ");
      row.append(name, capabilities);
      this.#providerList.append(row);
      if (provider.capabilities.includes("vector") && provider.capabilities.includes("live")) {
        this.#reindexProvider.add(new Option(provider.instanceId, provider.instanceId));
      }
    }
    this.#reindexProvider.value = keepSource;
    if (this.#reindexProvider.value !== keepSource) {
      this.#reindexProvider.value = "";
    }
  }

  private renderAliases(aliases: IndexAliasView[]): void {
    this.#aliasList.replaceChildren();
    if (aliases.length === 0) {
      this.#aliasList.append(emptyMessage("No aliases yet."));
      return;
    }
    for (const alias of aliases) {
      const row = document.createElement("div");
      row.className = "lifecycle-alias-row";
      const label = document.createElement("span");
      label.textContent = `${alias.alias} → ${alias.indexId}`;
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "secondary-button";
      remove.textContent = "Delete";
      remove.addEventListener("click", () => void this.run(async () => {
        await this.#api.deleteAlias(alias.alias);
        this.setStatus(`Deleted alias '${alias.alias}'.`);
        await this.refresh();
      }));
      row.append(label, remove);
      this.#aliasList.append(row);
    }
  }

  private renderModels(models: TrainedModelSummary[]): void {
    const selectedReindex = this.#reindexModel.value;
    this.#reindexModel.replaceChildren();
    const selectedCollection = this.#collectionModel.value;
    this.#collectionModel.replaceChildren(new Option("No model artifact", ""));
    for (const model of models) {
      this.#reindexModel.add(new Option(`${model.displayName} (${model.artifactId})`,
        model.artifactId));
      this.#collectionModel.add(new Option(`${model.displayName} (${model.artifactId})`,
        model.artifactId));
    }
    if (models.length === 0) {
      this.#reindexModel.add(new Option("Train a model first", ""));
    }
    this.#reindexModel.value = selectedReindex;
    this.#collectionModel.value = selectedCollection;
    if (this.#collectionModel.selectedIndex < 0) {
      this.#collectionModel.value = "";
    }
  }

  private async persistSelected(seal: boolean): Promise<void> {
    const index = this.selectedIndex();
    if (!index) {
      this.setStatus("Select a dynamic workspace first.", true);
      return;
    }
    await this.run(async () => {
      const updated = seal
        ? await this.#api.seal(index.id)
        : await this.#api.persist(index.id);
      this.setStatus(seal
        ? `Sealed '${index.label}'; it is now immutable and durable.`
        : `Persisted '${index.label}' as a checkpoint (${formatInteger(updated?.size ?? 0)} chunks).`);
      await this.refresh();
    });
  }

  private async setAlias(): Promise<void> {
    const index = this.selectedIndex();
    const alias = this.#aliasInput.value.trim();
    if (!index || !alias) {
      this.setStatus("Select a workspace and enter an alias name.", true);
      return;
    }
    await this.run(async () => {
      await this.#api.setAlias(alias, index.id);
      this.#aliasInput.value = "";
      this.setStatus(`Alias '${alias}' now resolves to '${index.id}'.`);
      await this.refresh();
    });
  }

  private async reindexSelected(): Promise<void> {
    const index = this.selectedIndex();
    const modelId = this.#reindexModel.value;
    if (!index || !modelId) {
      this.setStatus("Select a workspace and a trained model to reindex into.", true);
      return;
    }
    const provider = this.#reindexProvider.value;
    const alias = this.#reindexAlias.value.trim();
    await this.run(async () => {
      this.setStatus(`The server is replaying '${index.label}' through '${modelId}'.`);
      const built = await this.#api.reindex({
        indexId: index.id,
        embedding: { modelId },
        ...(provider ? { provider: { custom: provider } } : {}),
        ...(alias ? { alias } : {}),
      });
      this.setStatus(built
        ? `Built '${built.id}' beside the source${alias ? ` and swapped alias '${alias}'` : ""}.`
        : "The reindex build completed.");
      await this.refresh();
    });
  }

  private renderCollectionOptions(collections: CollectionView[]): void {
    const selected = this.#collectionSelect.value;
    this.#collectionSelect.replaceChildren(new Option("New collection", ""));
    for (const collection of collections) {
      this.#collectionSelect.add(new Option(
        `${collection.displayName} (${collection.id})`, collection.id));
    }
    this.#collectionSelect.value = selected;
    if (this.#collectionSelect.selectedIndex < 0) {
      this.#collectionSelect.value = "";
    }
  }

  private async openSelectedCollection(): Promise<void> {
    const collectionId = this.#collectionSelect.value;
    if (!collectionId) {
      this.stopWatch();
      this.#collectionId.value = "";
      this.#collectionName.value = "";
      this.#collectionVocabulary.value = "";
      this.#collectionDictionary.value = "";
      this.#collectionModel.value = "";
      this.#collectionThreshold.value = "0";
      for (const option of Array.from(this.#collectionMembers.options)) {
        option.selected = false;
      }
      this.renderCollection(undefined);
      return;
    }
    try {
      const collection = await this.#api.getCollection(collectionId);
      if (!collection) {
        this.setStatus(`Collection '${collectionId}' was not found.`, true);
        return;
      }
      this.fillEditor(collection);
      this.renderCollection(collection);
      this.startWatch(collection.id);
    } catch (error) {
      this.setStatus(errorMessage(error, "Could not load the collection."), true);
    }
  }

  private fillEditor(collection: CollectionView): void {
    this.#collectionId.value = collection.id;
    this.#collectionName.value = collection.displayName;
    this.#collectionVocabulary.value = collection.vocabularyArtifactId ?? "";
    this.#collectionDictionary.value = collection.dictionaryArtifactId ?? "";
    this.#collectionModel.value = collection.modelArtifactId ?? "";
    if (this.#collectionModel.selectedIndex < 0) {
      this.#collectionModel.value = "";
    }
    this.#collectionThreshold.value = String(collection.driftNewTermThreshold);
    const members = new Set(collection.memberIndexIds);
    for (const option of Array.from(this.#collectionMembers.options)) {
      option.selected = members.has(option.value);
    }
  }

  private async saveCollection(): Promise<void> {
    const collectionId = this.#collectionId.value.trim();
    const displayName = this.#collectionName.value.trim();
    if (!collectionId || !displayName) {
      this.setStatus("A collection needs an id and a display name.", true);
      return;
    }
    const vocabulary = this.#collectionVocabulary.value.trim();
    const dictionary = this.#collectionDictionary.value.trim();
    const model = this.#collectionModel.value;
    const threshold = Number.parseInt(this.#collectionThreshold.value, 10);
    await this.run(async () => {
      const collection = await this.#api.setCollection({
        collectionId,
        displayName,
        memberIndexIds: Array.from(this.#collectionMembers.selectedOptions)
          .map((option) => option.value),
        ...(dictionary ? { dictionaryArtifactId: dictionary } : {}),
        ...(vocabulary ? { vocabularyArtifactId: vocabulary } : {}),
        ...(model ? { modelArtifactId: model } : {}),
        driftNewTermThreshold: Number.isFinite(threshold) && threshold > 0 ? threshold : 0,
      });
      this.setStatus(`Saved collection '${collectionId}'.`);
      await this.refresh();
      this.#collectionSelect.value = collectionId;
      if (collection) {
        this.renderCollection(collection);
        this.startWatch(collection.id);
      }
    });
  }

  private async deleteCollection(): Promise<void> {
    const collectionId = this.#collectionSelect.value || this.#collectionId.value.trim();
    if (!collectionId) {
      this.setStatus("Select a collection to delete.", true);
      return;
    }
    await this.run(async () => {
      this.stopWatch();
      const deleted = await this.#api.deleteCollection(collectionId);
      this.setStatus(deleted
        ? `Deleted collection '${collectionId}'.`
        : `Collection '${collectionId}' did not exist.`);
      this.#collectionSelect.value = "";
      await this.refresh();
      await this.openSelectedCollection();
    });
  }

  /** Starts the reconnecting watch loop; a newer watch or delete supersedes it. */
  private startWatch(collectionId: string): void {
    const generation = ++this.#watchGeneration;
    this.#eventLog.replaceChildren();
    this.#watchStatus.textContent = `Watching '${collectionId}'.`;
    void (async () => {
      while (generation === this.#watchGeneration) {
        try {
          await this.#api.watchCollection(collectionId, (event) => {
            if (generation !== this.#watchGeneration || !event) {
              return;
            }
            this.renderCollection(event.collection);
            this.logEvent(event);
          });
          // The bounded gateway watch ended; resubscribe for a fresh snapshot.
        } catch (error) {
          if (generation !== this.#watchGeneration) {
            return;
          }
          this.#watchStatus.textContent =
            errorMessage(error, "The watch stream failed.") + " Reconnecting.";
          await delay(WATCH_RETRY_MILLIS);
        }
      }
    })();
  }

  private stopWatch(): void {
    this.#watchGeneration++;
    this.#watchStatus.textContent = "Not watching a collection.";
  }

  private logEvent(event: CollectionEventView): void {
    if (event.kind === "snapshot") {
      this.#watchStatus.textContent = `Watching '${event.collection.id}'. Snapshot received.`;
      return;
    }
    const entry = document.createElement("div");
    const description = event.kind === "drift"
      ? `Drift threshold crossed: ${formatInteger(event.collection.drift.newTerms)} new terms.`
      : event.kind === "index-persisted"
        ? `Index persisted: ${event.indexId ?? "unknown"}.`
        : `Model published: ${event.modelArtifactId ?? "unknown"}.`;
    entry.textContent = description;
    this.#eventLog.prepend(entry);
  }

  private renderCollection(collection: CollectionView | undefined): void {
    this.#driftStats.replaceChildren();
    this.#ledger.replaceChildren();
    if (!collection) {
      this.#coverageBar.style.width = "0%";
      this.#coverageLabel.textContent = "No collection selected.";
      this.#ledger.append(emptyMessage("Select or save a collection to see its term ledger."));
      return;
    }
    const drift = collection.drift;
    addFact(this.#driftStats, "Distinct terms", formatInteger(drift.distinctTerms));
    addFact(this.#driftStats, "Occurrences", formatInteger(drift.termOccurrences));
    addFact(this.#driftStats, "New terms", formatInteger(drift.newTerms));
    addFact(this.#driftStats, "New occurrences", formatInteger(drift.newTermOccurrences));
    addFact(this.#driftStats, "Analysis chain", collection.analysisChainId || "Not reported");
    const coverage = Math.round(drift.vocabularyCoverage * 100);
    this.#coverageBar.style.width = `${coverage}%`;
    this.#coverageLabel.textContent = collection.vocabularyArtifactId
      ? `${coverage}% of term occurrences hit vocabulary '${collection.vocabularyArtifactId}'.`
      : "No vocabulary artifact is configured; every accreted term counts as new.";
    if (collection.termLedger.length === 0) {
      this.#ledger.append(emptyMessage("The member indexes hold no analyzable terms yet."));
      return;
    }
    for (const entry of collection.termLedger.slice(0, 40)) {
      const chip = document.createElement("span");
      chip.className = entry.inVocabulary ? "ledger-term is-known" : "ledger-term";
      chip.textContent = `${entry.term} ×${formatInteger(entry.occurrences)}`;
      chip.title = entry.inVocabulary
        ? "A row of the current vocabulary" : "Accreted outside the current vocabulary";
      this.#ledger.append(chip);
    }
    if (collection.termLedger.length > 40 || collection.omittedLedgerTerms > 0) {
      const rest = document.createElement("span");
      rest.className = "ledger-more";
      rest.textContent = `+${formatInteger(Math.max(0, collection.termLedger.length - 40)
        + collection.omittedLedgerTerms)} more`;
      this.#ledger.append(rest);
    }
  }

  private selectedIndex(): SearchIndex | undefined {
    return this.#indexes.find((index) => index.id === this.#indexSelect.value);
  }

  private async run(work: () => Promise<void>): Promise<void> {
    if (this.#busy) {
      return;
    }
    this.#busy = true;
    this.updateControls();
    try {
      await work();
    } catch (error) {
      this.setStatus(errorMessage(error, "The lifecycle request failed."), true);
    } finally {
      this.#busy = false;
      this.updateControls();
    }
  }

  private updateControls(): void {
    const hasIndex = Boolean(this.selectedIndex());
    this.#persistButton.disabled = this.#busy || !hasIndex;
    this.#sealButton.disabled = this.#busy || !hasIndex;
    this.#setAliasButton.disabled = this.#busy || !hasIndex;
    this.#reindexButton.disabled = this.#busy || !hasIndex;
    this.#collectionSaveButton.disabled = this.#busy;
    this.#collectionDeleteButton.disabled = this.#busy;
    this.#refreshButton.disabled = this.#busy;
  }

  private setStatus(message: string, error = false): void {
    this.#status.textContent = message;
    this.#status.classList.toggle("is-error", error);
  }
}

function addFact(list: HTMLDListElement, term: string, value: string): void {
  const container = document.createElement("div");
  const name = document.createElement("dt");
  name.textContent = term;
  const detail = document.createElement("dd");
  detail.textContent = value;
  container.append(name, detail);
  list.append(container);
}

function delay(millis: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, millis));
}
