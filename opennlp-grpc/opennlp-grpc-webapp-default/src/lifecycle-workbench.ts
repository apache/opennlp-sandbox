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
import {
  indexStateLabel,
  SCRATCH_INDEX_PREFIX,
  type IndexAliasView,
  type SearchIndex,
  type SearchProviderInstance,
  type SearchProviderListing,
} from "./search-adapter";
import { ellipsizeCodePoints, formatInteger } from "./text-utils";
import { addFact, emptyMessage, errorMessage, requiredElement } from "./ui-utils";
import {
  type DictionaryArtifactSummary,
  type TrainedModelSummary,
  type VocabularyArtifactSummary,
  vocabularyOptionLabel,
} from "./vocabulary-trainer";

export interface LifecycleApi {
  listIndexes(): Promise<SearchIndex[]>;
  listProviders(): Promise<SearchProviderListing>;
  listAliases(): Promise<IndexAliasView[]>;
  persist(indexId: string): Promise<SearchIndex | undefined>;
  seal(indexId: string): Promise<SearchIndex | undefined>;
  reindex(request: ReindexIndexRequest): Promise<SearchIndex | undefined>;
  setAlias(alias: string, indexId: string): Promise<void>;
  deleteAlias(alias: string): Promise<void>;
  listStaticModels(): Promise<TrainedModelSummary[]>;
  listDictionaries(): Promise<DictionaryArtifactSummary[]>;
  listVocabularies(): Promise<VocabularyArtifactSummary[]>;
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
  readonly #workspaceStatus = requiredElement<HTMLElement>("lifecycle-workspace-status");
  readonly #aliasStatus = requiredElement<HTMLElement>("lifecycle-alias-status");
  readonly #rebuildStatus = requiredElement<HTMLElement>("lifecycle-rebuild-status");
  readonly #collectionStatus = requiredElement<HTMLElement>("collection-status");
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
  readonly #collectionVocabulary = requiredElement<HTMLSelectElement>("collection-vocabulary-id");
  readonly #collectionDictionary = requiredElement<HTMLSelectElement>("collection-dictionary-id");
  readonly #collectionModel = requiredElement<HTMLSelectElement>("collection-model-id");
  readonly #collectionThreshold = requiredElement<HTMLInputElement>("collection-threshold");
  readonly #collectionSaveButton = requiredElement<HTMLButtonElement>("collection-save-button");
  readonly #collectionDeleteButton =
    requiredElement<HTMLButtonElement>("collection-delete-button");
  readonly #driftStats = requiredElement<HTMLDListElement>("collection-drift-stats");
  readonly #coverageBar = requiredElement<HTMLElement>("collection-coverage-bar");
  readonly #coverageLabel = requiredElement<HTMLElement>("collection-coverage-label");
  readonly #termStatistics = requiredElement<HTMLElement>("collection-term-statistics");
  readonly #watchStatus = requiredElement<HTMLElement>("collection-watch-status");
  readonly #eventLog = requiredElement<HTMLElement>("collection-event-log");

  #indexes: SearchIndex[] = [];
  #busy = false;
  #watchGeneration = 0;
  #listing: SearchProviderListing = {
    providers: [], dynamicIndexingEnabled: true, persistenceConfigured: false,
  };

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
      const [indexes, providers, aliases, models, collections, dictionaries, vocabularies] =
        await Promise.all([
          this.#api.listIndexes(),
          this.#api.listProviders(),
          this.#api.listAliases(),
          this.#api.listStaticModels(),
          this.#api.listCollections(),
          this.#api.listDictionaries(),
          this.#api.listVocabularies(),
        ]);
      // Read-only indexes stay listed: they are still searchable, can be aliased, and a
      // person who just made one read-only should not watch it vanish.
      this.#indexes = indexes.filter((index) => !index.label.startsWith(SCRATCH_INDEX_PREFIX));
      this.#listing = providers;
      this.renderIndexOptions();
      this.renderProviders(providers.providers);
      this.renderAvailability();
      this.renderAliases(aliases);
      this.renderModels(models);
      this.renderArtifactOptions(this.#collectionDictionary, "No dictionary",
        dictionaries.map((dictionary) => ({
          value: dictionary.artifactId,
          label: `${dictionary.displayName} (${dictionary.entryCount} entries)`,
        })));
      this.renderArtifactOptions(this.#collectionVocabulary, "No vocabulary (coverage not measured)",
        vocabularies.map((vocabulary) => ({
          value: vocabulary.artifactId,
          label: vocabularyOptionLabel(vocabulary),
        })));
      this.renderCollectionOptions(collections);
      if (this.#indexes.length === 0) {
        this.setStatus("No live indexes yet. Build one from your documents, or analyze a document "
          + "and add it to a live index. ");
        this.#status.append(jumpButton("workflows", "Open Build index"), " ",
          jumpButton("session-search", "Open Live index search"));
      } else {
        this.setStatus(`${this.#indexes.length} ${this.#indexes.length === 1 ? "index" : "indexes"} available.`);
      }
    } catch (error) {
      this.setStatus(errorMessage(error, "Could not load the lifecycle catalog."), true);
    }
    this.updateControls();
  }

  /** Fills one artifact picker from the server's list, keeping the current choice. */
  private renderArtifactOptions(
    select: HTMLSelectElement,
    noneLabel: string,
    options: Array<{ value: string; label: string }>,
  ): void {
    const selected = select.value;
    select.replaceChildren(new Option(noneLabel, ""));
    for (const option of options) {
      select.add(new Option(option.label, option.value));
    }
    selectArtifact(select, selected);
  }

  private renderIndexOptions(): void {
    const selected = this.#indexSelect.value;
    this.#indexSelect.replaceChildren();
    if (this.#indexes.length === 0) {
      this.#indexSelect.add(new Option("No live indexes", ""));
      this.#indexSelect.disabled = true;
    } else {
      for (const index of this.#indexes) {
        this.#indexSelect.add(new Option(
          `${index.label} (${index.id}) · ${indexStateLabel(index)}`, index.id));
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
    addFact(this.#indexFacts, "Vector space", ellipsizeCodePoints(index.vectorSpaceId, 24));
    addFact(this.#indexFacts, "Chunks", formatInteger(index.size ?? 0));
    addFact(this.#indexFacts, "State", indexStateLabel(index));
  }

  private renderProviders(providers: SearchProviderInstance[]): void {
    this.#providerList.replaceChildren();
    if (providers.length === 0) {
      this.#providerList.append(emptyMessage("No vector storage reported."));
    }
    const keepSource = this.#reindexProvider.value;
    this.#reindexProvider.replaceChildren(new Option("Keep the current vector storage", ""));
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
      remove.addEventListener("click", () => void this.run(this.#aliasStatus, async () => {
        await this.#api.deleteAlias(alias.alias);
        this.report(this.#aliasStatus, `Deleted alias '${alias.alias}'.`);
        await this.refresh();
      }));
      row.append(label, remove);
      this.#aliasList.append(row);
    }
  }

  private renderModels(models: TrainedModelSummary[]): void {
    const selectedReindex = this.#reindexModel.value;
    this.#reindexModel.replaceChildren();
    if (models.length === 0) {
      this.#reindexModel.add(new Option("No trained model yet: distill one on the Trainer tab", ""));
      this.#reindexModel.disabled = true;
    } else {
      this.#reindexModel.disabled = false;
    }
    const selectedCollection = this.#collectionModel.value;
    this.#collectionModel.replaceChildren(new Option("No model selected", ""));
    for (const model of models) {
      const label = `${model.displayName} (${ellipsizeCodePoints(model.artifactId, 20)})`;
      this.#reindexModel.add(new Option(label, model.artifactId));
      this.#collectionModel.add(new Option(label, model.artifactId));
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
      this.report(this.#workspaceStatus, "Select a live index first.", true);
      return;
    }
    await this.run(this.#workspaceStatus, async () => {
      const updated = seal
        ? await this.#api.seal(index.id)
        : await this.#api.persist(index.id);
      this.report(this.#workspaceStatus, seal
        ? `Made '${index.label}' read-only and saved it to disk. It stays searchable. `
        : `Saved '${index.label}' to disk (${formatInteger(updated?.size ?? 0)} chunks); `
          + "it now survives a server restart. ");
      this.#workspaceStatus.append(jumpButton("session-search", "Search it on Live index search"));
      await this.refresh();
    });
  }

  private async setAlias(): Promise<void> {
    const index = this.selectedIndex();
    const alias = this.#aliasInput.value.trim();
    if (!index || !alias) {
      this.report(this.#aliasStatus, "Select a live index and enter an alias name.", true);
      return;
    }
    await this.run(this.#aliasStatus, async () => {
      await this.#api.setAlias(alias, index.id);
      this.#aliasInput.value = "";
      this.report(this.#aliasStatus, `Alias '${alias}' now resolves to '${index.id}'.`);
      await this.refresh();
    });
  }

  private async reindexSelected(): Promise<void> {
    const index = this.selectedIndex();
    const modelId = this.#reindexModel.value;
    if (!index || !modelId) {
      this.report(this.#rebuildStatus,
        "Select a live index and a distilled model to rebuild it with.", true);
      return;
    }
    const provider = this.#reindexProvider.value;
    const alias = this.#reindexAlias.value.trim();
    await this.run(this.#rebuildStatus, async () => {
      this.report(this.#rebuildStatus,
        `The server is rebuilding '${index.label}' with '${modelId}'. `
        + "The current index keeps serving searches until the new one is ready.");
      const built = await this.#api.reindex({
        indexId: index.id,
        embedding: { modelId },
        ...(provider ? { provider: { custom: provider } } : {}),
        ...(alias ? { alias } : {}),
      });
      this.report(this.#rebuildStatus, built
        ? `Built '${built.id}' beside '${index.label}'`
          + `${alias ? `, and alias '${alias}' now points at the new index` : ""}.`
        : "The rebuild completed.");
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
      this.report(this.#collectionStatus, "");
      return;
    }
    try {
      const collection = await this.#api.getCollection(collectionId);
      if (!collection) {
        this.report(this.#collectionStatus, `Collection '${collectionId}' was not found.`, true);
        return;
      }
      this.fillEditor(collection);
      this.renderCollection(collection);
      this.startWatch(collection.id);
      this.report(this.#collectionStatus, `Opened collection '${collection.id}'.`);
    } catch (error) {
      this.report(this.#collectionStatus,
        errorMessage(error, "Could not load the collection."), true);
    }
  }

  private fillEditor(collection: CollectionView): void {
    this.#collectionId.value = collection.id;
    this.#collectionName.value = collection.displayName;
    selectArtifact(this.#collectionVocabulary, collection.vocabularyArtifactId ?? "");
    selectArtifact(this.#collectionDictionary, collection.dictionaryArtifactId ?? "");
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
      this.report(this.#collectionStatus, "A collection needs an id and a display name.", true);
      return;
    }
    const vocabulary = this.#collectionVocabulary.value.trim();
    const dictionary = this.#collectionDictionary.value.trim();
    const model = this.#collectionModel.value;
    const threshold = Number.parseInt(this.#collectionThreshold.value, 10);
    await this.run(this.#collectionStatus, async () => {
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
      this.report(this.#collectionStatus, `Saved collection '${collectionId}'.`);
      await this.refresh();
      this.#collectionSelect.value = collectionId;
      if (collection) {
        this.renderCollection(collection);
        this.startWatch(collection.id);
      }
    }, (message) => {
      // The picked vocabulary was deleted since the list loaded; say where a new one comes from.
      if (message.includes("vocabulary")) {
        this.#collectionStatus.append(" ", jumpButton("trainer", "Learn a vocabulary on the Trainer tab"));
      }
    });
  }

  private async deleteCollection(): Promise<void> {
    const collectionId = this.#collectionSelect.value || this.#collectionId.value.trim();
    if (!collectionId) {
      this.report(this.#collectionStatus, "Select a collection to delete.", true);
      return;
    }
    await this.run(this.#collectionStatus, async () => {
      this.stopWatch();
      const deleted = await this.#api.deleteCollection(collectionId);
      this.#collectionSelect.value = "";
      await this.refresh();
      await this.openSelectedCollection();
      this.report(this.#collectionStatus, deleted
        ? `Deleted collection '${collectionId}'.`
        : `Collection '${collectionId}' did not exist.`);
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
      ? `Coverage alert: ${formatInteger(event.collection.drift.newTerms)} new terms.`
      : event.kind === "index-persisted"
        ? `Index persisted: ${event.indexId ?? "unknown"}.`
        : `Model published: ${event.modelArtifactId ?? "unknown"}.`;
    entry.textContent = description;
    this.#eventLog.prepend(entry);
  }

  private renderCollection(collection: CollectionView | undefined): void {
    this.#driftStats.replaceChildren();
    this.#termStatistics.replaceChildren();
    if (!collection) {
      this.#coverageBar.style.width = "0%";
      this.#coverageLabel.textContent = "No collection selected.";
      this.#termStatistics.append(emptyMessage("Select or save a collection to see its term statistics."));
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
    this.#coverageBar.parentElement?.classList.toggle("is-unmeasured", !collection.vocabularyArtifactId);
    if (collection.vocabularyArtifactId) {
      this.#coverageLabel.textContent =
        `${coverage}% of term occurrences hit vocabulary '${collection.vocabularyArtifactId}'.`;
    } else {
      this.#coverageBar.style.width = "0%";
      this.#coverageLabel.textContent = "Not measured: this collection has no vocabulary artifact, "
        + "so there is nothing to cover. ";
      this.#coverageLabel.append(jumpButton("trainer", "Learn one on the Trainer tab"));
    }
    if (collection.termStatistics.length === 0) {
      this.#termStatistics.append(emptyMessage("The member indexes hold no analyzable terms yet."));
      return;
    }
    for (const entry of collection.termStatistics.slice(0, 40)) {
      const chip = document.createElement("span");
      chip.className = entry.inVocabulary ? "term-statistic is-known" : "term-statistic";
      chip.textContent = `${entry.term} ×${formatInteger(entry.occurrences)}`;
      chip.title = entry.inVocabulary
        ? "In the current vocabulary" : "Out of the current vocabulary";
      this.#termStatistics.append(chip);
    }
    if (collection.termStatistics.length > 40 || collection.omittedTermCount > 0) {
      const rest = document.createElement("span");
      rest.className = "statistics-more";
      rest.textContent = `+${formatInteger(Math.max(0, collection.termStatistics.length - 40)
        + collection.omittedTermCount)} more`;
      this.#termStatistics.append(rest);
    }
  }

  private selectedIndex(): SearchIndex | undefined {
    return this.#indexes.find((index) => index.id === this.#indexSelect.value);
  }

  /**
   * Runs one lifecycle action, reporting a failure to the status region that
   * sits next to the controls that started it.
   */
  private async run(
    region: HTMLElement,
    work: () => Promise<void>,
    onError?: (message: string) => void,
  ): Promise<void> {
    if (this.#busy) {
      return;
    }
    this.#busy = true;
    this.updateControls();
    try {
      await work();
    } catch (error) {
      const message = errorMessage(error, "The lifecycle request failed.");
      this.report(region, message, true);
      onError?.(message);
    } finally {
      this.#busy = false;
      this.updateControls();
    }
  }

  private updateControls(): void {
    const selected = this.selectedIndex();
    const hasIndex = Boolean(selected);
    const writable = hasIndex && !selected?.immutable;
    const enabled = this.#listing.dynamicIndexingEnabled;
    const canSave = this.#listing.persistenceConfigured;
    this.#persistButton.disabled = this.#busy || !writable || !enabled || !canSave;
    this.#sealButton.disabled = this.#busy || !writable || !enabled || !canSave;
    this.#setAliasButton.disabled = this.#busy || !hasIndex || !enabled;
    this.#reindexButton.disabled = this.#busy || !hasIndex || !enabled;
    const reason = !enabled
      ? "Live indexing is disabled by the server operator."
      : !canSave ? "Saving to disk is not configured on this server: set search.persist.root."
      : hasIndex && !writable ? "This index is already read-only." : "";
    this.#persistButton.title = reason;
    this.#sealButton.title = reason;
    this.#collectionSaveButton.disabled = this.#busy;
    this.#collectionDeleteButton.disabled = this.#busy;
    this.#refreshButton.disabled = this.#busy;
  }

  /**
   * Browns out the live-index panel when the server cannot serve it, saying why once
   * instead of failing on every click.
   */
  private renderAvailability(): void {
    const panel = this.#indexSelect.closest<HTMLElement>(".lifecycle-panel");
    const disabled = !this.#listing.dynamicIndexingEnabled;
    panel?.classList.toggle("is-unavailable", disabled);
    if (disabled) {
      this.report(this.#workspaceStatus,
        "Live indexing is disabled by the server operator, so nothing here can be saved, "
        + "made read-only, aliased, or rebuilt.", true);
    } else if (!this.#listing.persistenceConfigured) {
      this.report(this.#workspaceStatus,
        "Saving to disk is not configured on this server (search.persist.root), so live "
        + "indexes last until the next restart.");
    }
  }

  /** Reports catalog-level progress in the status line above both panels. */
  private setStatus(message: string, error = false): void {
    this.report(this.#status, message, error);
  }

  /** Writes one message into the given announced status region. */
  private report(region: HTMLElement, message: string, error = false): void {
    region.textContent = message;
    region.classList.toggle("is-error", error);
  }
}

function delay(millis: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, millis));
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

/**
 * Selects one artifact id in a picker. An id the server no longer lists, which a saved
 * collection can still carry, is kept as its own option so opening the collection does not
 * silently drop it.
 */
function selectArtifact(select: HTMLSelectElement, artifactId: string): void {
  select.value = artifactId;
  if (select.selectedIndex >= 0) {
    return;
  }
  if (!artifactId) {
    select.value = "";
    return;
  }
  select.add(new Option(`${artifactId} (not on this server)`, artifactId));
  select.value = artifactId;
}
