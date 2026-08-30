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

import type {
  ImportDictionaryUpload,
  LearnVocabularyUpload,
  TrainStaticModelRequest,
} from "./api";
import {
  ellipsizeCodePoints,
  formatInteger,
  splitBlankLineDocuments,
  timestampLabel,
} from "./text-utils";
import { flashButtonLabel, requiredElement } from "./ui-utils";

const CARRIAGE_RETURN = "\r";
const LINE_FEED = "\n";

export interface DictionaryFormatOption {
  id: string;
  label: string;
  custom: boolean;
}

export interface DictionaryArtifactSummary {
  artifactId: string;
  displayName: string;
  entryCount: number;
}

export interface VocabularyArtifactSummary {
  artifactId: string;
  displayName: string;
  termCount: number;
  /** The dictionary the vocabulary was seeded from, when it had one. */
  dictionaryArtifactId?: string;
}

export interface TeacherOption {
  id: string;
  label: string;
  reference: string;
}

export interface TrainedModelSummary {
  artifactId: string;
  displayName: string;
  dimension: number;
  termCount: number;
  teacherId: string;
  family: string;
  vocabularySize: number;
  explainedVarianceRatio: number;
  artifactHash: string;
  byteSize: number;
  /** ISO-8601 creation time from the server, or empty when not reported. */
  createdAt: string;
  /** Where the teacher came from, as the server recorded it at distillation. */
  teacherReference: string;
  /** The license inherited from the teacher, empty when the server did not know it. */
  licenseName: string;
  languages: string[];
}

export interface TrainerApi {
  listDictionaryFormats(): Promise<{ formats: DictionaryFormatOption[]; writesEnabled: boolean }>;
  listDictionaries(): Promise<DictionaryArtifactSummary[]>;
  listVocabularies(): Promise<VocabularyArtifactSummary[]>;
  importDictionary(upload: ImportDictionaryUpload): Promise<{ artifactId: string; displayName: string; entryCount: number }>;
  learnVocabulary(upload: LearnVocabularyUpload): Promise<{
    artifactId: string; displayName: string; termCount: number;
    dictionaryTermCount: number; corpusTermCount: number;
  }>;
  downloadVocabulary(artifactId: string): Promise<string>;
  listTeachers(): Promise<{ teachers: TeacherOption[]; writesEnabled: boolean }>;
  trainStaticModel(
    request: TrainStaticModelRequest,
    onProgress: (message: string) => void,
  ): Promise<TrainedModelSummary>;
  listStaticModels(): Promise<TrainedModelSummary[]>;
  deleteStaticModel(artifactId: string): Promise<boolean>;
}

export interface TrainerCallbacks {
  /** Fired after training or deletion changes the served model catalog. */
  onModelsChanged(models: TrainedModelSummary[]): void;
  /** Fired when the user asks to select a trained model on the Analyze tab. */
  onUseInAnalyze(model: TrainedModelSummary): void;
}

/**
 * The trainer workbench: import a dictionary, learn a vocabulary from pasted
 * documents, distill an operator-configured teacher into a static model, and hand
 * the served model id to the analysis and search workbenches.
 */
export class VocabularyTrainerWorkbench {
  readonly #api: TrainerApi;
  readonly #callbacks: TrainerCallbacks;

  readonly #status = requiredElement<HTMLElement>("trainer-status");
  readonly #formatSelect = requiredElement<HTMLSelectElement>("trainer-format-select");
  readonly #dictionaryName = requiredElement<HTMLInputElement>("trainer-dictionary-name");
  readonly #dictionaryFile = requiredElement<HTMLInputElement>("trainer-dictionary-file");
  readonly #importButton = requiredElement<HTMLButtonElement>("trainer-import-button");
  readonly #dictionarySelect = requiredElement<HTMLSelectElement>("trainer-dictionary-select");

  readonly #corpus = requiredElement<HTMLTextAreaElement>("trainer-corpus");
  readonly #corpusStats = requiredElement<HTMLElement>("trainer-corpus-stats");
  readonly #vocabularyName = requiredElement<HTMLInputElement>("trainer-vocabulary-name");
  readonly #minFrequency = requiredElement<HTMLInputElement>("trainer-min-frequency");
  readonly #maxTerms = requiredElement<HTMLInputElement>("trainer-max-terms");
  readonly #learnButton = requiredElement<HTMLButtonElement>("trainer-learn-button");
  readonly #vocabularySelect = requiredElement<HTMLSelectElement>("trainer-vocabulary-select");
  readonly #downloadTsvButton = requiredElement<HTMLButtonElement>("trainer-download-tsv-button");

  readonly #teacherSelect = requiredElement<HTMLSelectElement>("trainer-teacher-select");
  readonly #modelName = requiredElement<HTMLInputElement>("trainer-model-name");
  readonly #pcaDims = requiredElement<HTMLInputElement>("trainer-pca-dims");
  readonly #trainButton = requiredElement<HTMLButtonElement>("trainer-train-button");
  readonly #progressLog = requiredElement<HTMLElement>("trainer-progress-log");
  readonly #modelList = requiredElement<HTMLElement>("trainer-model-list");

  #writesEnabled = false;
  #hasTeacher = false;
  #busy = false;

  constructor(api: TrainerApi, callbacks: TrainerCallbacks) {
    this.#api = api;
    this.#callbacks = callbacks;
    this.#importButton.addEventListener("click", () => void this.importDictionary());
    this.#learnButton.addEventListener("click", () => void this.learnVocabulary());
    this.#downloadTsvButton.addEventListener("click", () => void this.downloadTsv());
    this.#trainButton.addEventListener("click", () => void this.train());
    this.#vocabularySelect.addEventListener("change", () => this.updateControls());
    this.#corpus.addEventListener("input", () => this.renderCorpusStats());
    this.renderCorpusStats();
  }

  /** Loads formats, teachers, and existing models; call once at startup. */
  async initialize(): Promise<void> {
    try {
      const [formats, teachers, models, dictionaries, vocabularies] = await Promise.all([
        this.#api.listDictionaryFormats(),
        this.#api.listTeachers(),
        this.#api.listStaticModels(),
        this.#api.listDictionaries(),
        this.#api.listVocabularies(),
      ]);
      this.#writesEnabled = formats.writesEnabled && teachers.writesEnabled;
      populate(this.#formatSelect, formats.formats.map((format) => ({
        value: format.id,
        label: format.label,
      })), "No formats available");
      populate(this.#teacherSelect, teachers.teachers.map((teacher) => ({
        value: teacher.id,
        label: teacher.label,
        title: teacher.reference,
      })), "No teachers configured");
      this.renderModels(models);
      this.#hasTeacher = teachers.teachers.length > 0;
      if (!this.#writesEnabled) {
        this.setStatus("Learning and distilling are off on this server: it has no writable "
          + "artifact root (vocabulary.artifact_root).", true);
      } else if (!this.#hasTeacher) {
        this.setStatus("No teacher model is installed, so nothing can be distilled yet. ", true);
        const jump = document.createElement("button");
        jump.type = "button";
        jump.className = "link-button";
        jump.dataset.workbenchJump = "models";
        jump.textContent = "Install a teacher on Models & data";
        this.#status.append(jump);
      } else {
        this.setStatus("Paste corpus text to learn a vocabulary. A dictionary is optional.");
      }
      this.renderDictionaryOptions(dictionaries);
      this.renderVocabularyOptions(vocabularies);
      this.updateControls();
    } catch (error) {
      this.setStatus(message(error, "Could not load the trainer catalog."), true);
    }
  }

  /** Offers every dictionary already on the server behind the corpus-only default. */
  private renderDictionaryOptions(dictionaries: DictionaryArtifactSummary[]): void {
    const selected = this.#dictionarySelect.value;
    this.#dictionarySelect.replaceChildren(new Option("Corpus terms only", ""));
    for (const dictionary of dictionaries) {
      this.#dictionarySelect.add(new Option(
        `${dictionary.displayName} (${dictionary.entryCount} entries)`, dictionary.artifactId));
    }
    this.#dictionarySelect.value = selected;
    if (this.#dictionarySelect.selectedIndex < 0) {
      this.#dictionarySelect.value = "";
    }
    this.#dictionarySelect.disabled = false;
  }

  /** Offers every vocabulary already on the server, so a restart does not hide them. */
  private renderVocabularyOptions(vocabularies: VocabularyArtifactSummary[]): void {
    const selected = this.#vocabularySelect.value;
    populate(this.#vocabularySelect, vocabularies.map((vocabulary) => ({
      value: vocabulary.artifactId,
      label: `${vocabulary.displayName} (${vocabulary.termCount} terms)`,
    })), "No vocabularies learned yet");
    if (vocabularies.some((vocabulary) => vocabulary.artifactId === selected)) {
      this.#vocabularySelect.value = selected;
    }
  }

  private async importDictionary(): Promise<void> {
    const file = this.#dictionaryFile.files?.[0];
    if (!file) {
      this.setStatus("Choose a dictionary file first.", true);
      return;
    }
    const displayName = this.#dictionaryName.value.trim() || file.name;
    await this.run("The server is importing the dictionary.", async () => {
      const upload: ImportDictionaryUpload = {
        start: {
          format: formatSelector(this.#formatSelect.value),
          displayName,
          provenanceSummary: `Uploaded through the trainer workbench as ${file.name}`,
        },
        data: base64(await file.arrayBuffer()),
      };
      const dictionary = await this.#api.importDictionary(upload);
      addOption(this.#dictionarySelect, dictionary.artifactId,
        `${dictionary.displayName} (${dictionary.entryCount} entries)`);
      this.setStatus(`Imported '${dictionary.displayName}' with ${dictionary.entryCount} entries.`);
    });
  }

  private async learnVocabulary(): Promise<void> {
    const dictionaryArtifactId = this.#dictionarySelect.value;
    const documents = corpusDocuments(this.#corpus.value);
    if (documents.length === 0) {
      this.setStatus("Paste at least one corpus document (blank lines separate documents).", true);
      return;
    }
    const displayName = this.#vocabularyName.value.trim() || "Trainer vocabulary";
    await this.run("The server is learning the vocabulary.", async () => {
      const vocabulary = await this.#api.learnVocabulary({
        start: {
          ...(dictionaryArtifactId ? { dictionaryArtifactId } : {}),
          displayName,
          minFrequency: boundedInt(this.#minFrequency.value, 1),
          maxTerms: boundedInt(this.#maxTerms.value, 10_000),
          provenanceSummary: "Learned through the trainer workbench",
        },
        documents,
      });
      addOption(this.#vocabularySelect, vocabulary.artifactId,
        `${vocabulary.displayName} (${vocabulary.termCount} terms)`);
      this.setStatus(`Learned ${vocabulary.termCount} terms `
        + `(${vocabulary.dictionaryTermCount} dictionary, ${vocabulary.corpusTermCount} corpus).`);
    });
  }

  private async downloadTsv(): Promise<void> {
    const artifactId = this.#vocabularySelect.value;
    if (!artifactId) {
      this.setStatus("Learn and select a vocabulary first.", true);
      return;
    }
    await this.run("Downloading the vocabulary TSV.", async () => {
      const tsv = await this.#api.downloadVocabulary(artifactId);
      saveTextFile(`${artifactId}.tsv`, tsv);
      this.setStatus(`Downloaded ${artifactId}.tsv.`);
    });
  }

  private async train(): Promise<void> {
    const vocabularyArtifactId = this.#vocabularySelect.value;
    const teacherId = this.#teacherSelect.value;
    if (!vocabularyArtifactId || !teacherId) {
      this.setStatus("Learn a vocabulary and select a teacher first.", true);
      return;
    }
    const displayName = this.#modelName.value.trim() || "Trainer static embedding model";
    this.#progressLog.replaceChildren();
    await this.run("The server is distilling the static embedding model.", async () => {
      const model = await this.#api.trainStaticModel({
        vocabularyArtifactId,
        teacherId,
        displayName,
        pcaDims: boundedInt(this.#pcaDims.value, 0),
        provenanceSummary: "Distilled through the trainer workbench",
      }, (progress) => this.appendProgress(progress));
      this.appendProgress(`Published ${model.artifactId} (dimension ${model.dimension}).`);
      this.appendProgress(`${formatInteger(model.vocabularySize)} tokenizer rows, `
        + `${formatInteger(model.termCount)} learned term rows, `
        + `${(model.explainedVarianceRatio * 100).toFixed(1)}% variance retained, `
        + `${formatInteger(model.byteSize)} bytes published.`);
      await this.refreshModels();
      this.setStatus(`Model '${model.displayName}' is serving as embedding model `
        + `'${model.artifactId}'. Select it in Analyze, then index and search with it.`);
    });
  }

  private async refreshModels(): Promise<void> {
    const models = await this.#api.listStaticModels();
    this.renderModels(models);
  }

  private renderModels(models: TrainedModelSummary[]): void {
    this.#modelList.replaceChildren();
    if (models.length === 0) {
      const empty = document.createElement("p");
      empty.className = "trainer-empty";
      empty.textContent = "No trained models yet.";
      this.#modelList.append(empty);
    }
    for (const model of models) {
      const row = document.createElement("div");
      row.className = "trainer-model-row";
      const identity = document.createElement("span");
      identity.className = "trainer-model-identity";
      const name = document.createElement("strong");
      name.textContent = model.displayName;
      const facts = document.createElement("small");
      const created = timestampLabel(model.createdAt);
      facts.textContent = `${model.dimension}-dim · ${formatInteger(model.termCount)} terms `
        + `· tokenizer ${model.family || "unknown"} · distilled from ${model.teacherId}`
        + (model.licenseName ? ` · ${model.licenseName}` : " · license not recorded")
        + (model.languages.length > 0 ? ` · ${model.languages.join(", ")}` : "")
        + (created ? ` · distilled ${created}` : "");
      const shortId = document.createElement("code");
      shortId.textContent = ellipsizeCodePoints(model.artifactId, 20);
      shortId.title = model.artifactId;
      identity.append(name, facts, shortId);
      const use = document.createElement("button");
      use.type = "button";
      use.textContent = "Use in Analyze";
      use.addEventListener("click", () => this.#callbacks.onUseInAnalyze(model));
      const copy = document.createElement("button");
      copy.type = "button";
      copy.className = "secondary-button";
      copy.textContent = "Copy id";
      copy.addEventListener("click", () => void copyText(copy, model.artifactId));
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "secondary-button";
      remove.textContent = "Delete";
      remove.addEventListener("click", () => void this.deleteModel(model.artifactId));
      row.append(identity, use, copy, remove);
      this.#modelList.append(row);
    }
    this.#callbacks.onModelsChanged(models);
  }

  private async deleteModel(artifactId: string): Promise<void> {
    await this.run("Deleting the model.", async () => {
      await this.#api.deleteStaticModel(artifactId);
      await this.refreshModels();
      this.setStatus(`Deleted ${artifactId}.`);
    });
  }

  private appendProgress(line: string): void {
    const entry = document.createElement("div");
    entry.textContent = line;
    this.#progressLog.append(entry);
    this.#progressLog.scrollTop = this.#progressLog.scrollHeight;
  }

  private async run(startMessage: string, work: () => Promise<void>): Promise<void> {
    if (this.#busy) {
      return;
    }
    this.#busy = true;
    this.updateControls();
    this.setStatus(startMessage);
    try {
      await work();
    } catch (error) {
      this.setStatus(message(error, "The trainer request failed."), true);
    } finally {
      this.#busy = false;
      this.updateControls();
    }
  }

  private updateControls(): void {
    const enabled = this.#writesEnabled && !this.#busy;
    this.#importButton.disabled = !enabled;
    this.#learnButton.disabled = !enabled;
    const vocabularySelected = this.#vocabularySelect.value !== "";
    this.#downloadTsvButton.disabled = !enabled || !vocabularySelected;
    this.#downloadTsvButton.title = vocabularySelected
      ? ""
      : "Learn and select a vocabulary first; the TSV export needs one.";
    this.#trainButton.disabled = !enabled || !this.#hasTeacher;
    this.#trainButton.title = this.#hasTeacher ? "" : "Install a teacher model first.";
  }

  private renderCorpusStats(): void {
    const stats = corpusStats(this.#corpus.value);
    this.#corpusStats.textContent = stats.documents === 0
      ? "Waiting for corpus input. Add text to preview the training batch."
      : `${stats.documents} ${stats.documents === 1 ? "document" : "documents"}, `
        + `${stats.codePoints} Unicode code points, ${stats.utf8Bytes} UTF-8 bytes ready.`;
  }

  private setStatus(text: string, isError = false): void {
    this.#status.textContent = text;
    this.#status.classList.toggle("is-error", isError);
  }
}

/** Reads the gateway's dictionary-formats JSON defensively. */
export function readDictionaryFormats(
  value: unknown,
): { formats: DictionaryFormatOption[]; writesEnabled: boolean } {
  const body = asRecord(value);
  const formats: DictionaryFormatOption[] = [];
  for (const entry of asArray(body.formats)) {
    const format = asRecord(entry);
    const selector = asRecord(format.format);
    const standard = typeof selector.standard === "string" ? selector.standard : "";
    const custom = typeof selector.custom === "string" ? selector.custom : "";
    const id = standard || custom;
    if (id) {
      formats.push({
        id,
        label: typeof format.displayName === "string" && format.displayName ? format.displayName : id,
        custom: !standard,
      });
    }
  }
  return { formats, writesEnabled: body.writesEnabled === true };
}

/** Reads imported dictionary artifacts available for paired vocabulary learning. */
export function readDictionaries(value: unknown): DictionaryArtifactSummary[] {
  return asArray(asRecord(value).dictionaries).flatMap((entry) => {
    const dictionary = asRecord(entry);
    if (typeof dictionary.artifactId !== "string" || !dictionary.artifactId) {
      return [];
    }
    return [{
      artifactId: dictionary.artifactId,
      displayName: typeof dictionary.displayName === "string" && dictionary.displayName
        ? dictionary.displayName : dictionary.artifactId,
      entryCount: asCount(dictionary.entryCount),
    }];
  });
}

/** Reads learned vocabulary artifacts available for distillation or a collection watch. */
export function readVocabularies(value: unknown): VocabularyArtifactSummary[] {
  return asArray(asRecord(value).vocabularies).flatMap((entry) => {
    const vocabulary = asRecord(entry);
    if (typeof vocabulary.artifactId !== "string" || !vocabulary.artifactId) {
      return [];
    }
    return [{
      artifactId: vocabulary.artifactId,
      displayName: typeof vocabulary.displayName === "string" && vocabulary.displayName
        ? vocabulary.displayName : vocabulary.artifactId,
      termCount: asCount(vocabulary.termCount),
      ...(typeof vocabulary.dictionaryArtifactId === "string" && vocabulary.dictionaryArtifactId
        ? { dictionaryArtifactId: vocabulary.dictionaryArtifactId } : {}),
    }];
  });
}

/** Reads the gateway's teachers JSON defensively. */
export function readTeachers(
  value: unknown,
): { teachers: TeacherOption[]; writesEnabled: boolean } {
  const body = asRecord(value);
  const teachers: TeacherOption[] = [];
  for (const entry of asArray(body.teachers)) {
    const teacher = asRecord(entry);
    if (typeof teacher.teacherId === "string" && teacher.teacherId) {
      teachers.push({
        id: teacher.teacherId,
        label: typeof teacher.displayName === "string" && teacher.displayName
          ? teacher.displayName : teacher.teacherId,
        reference: typeof teacher.reference === "string" ? teacher.reference : "",
      });
    }
  }
  return { teachers, writesEnabled: body.writesEnabled === true };
}

/** Reads one static model descriptor JSON defensively. */
export function readTrainedModel(value: unknown): TrainedModelSummary {
  const model = asRecord(value);
  if (typeof model.artifactId !== "string" || !model.artifactId) {
    throw new Error("The server returned an invalid static model descriptor.");
  }
  return {
    artifactId: model.artifactId,
    displayName: typeof model.displayName === "string" && model.displayName
      ? model.displayName : model.artifactId,
    dimension: asCount(model.dimension),
    termCount: asCount(model.termCount),
    teacherId: typeof model.teacherId === "string" ? model.teacherId : "",
    family: typeof model.family === "string" ? model.family : "",
    vocabularySize: asCount(model.vocabularySize),
    explainedVarianceRatio: asRatio(model.explainedVarianceRatio),
    artifactHash: typeof model.artifactHash === "string" ? model.artifactHash : "",
    byteSize: asCount(model.byteSize),
    createdAt: typeof model.createdAt === "string" ? model.createdAt : "",
    teacherReference: typeof model.teacherReference === "string" ? model.teacherReference : "",
    licenseName: typeof model.licenseName === "string" ? model.licenseName : "",
    languages: Array.isArray(model.languages)
      ? model.languages.filter((item): item is string => typeof item === "string") : [],
  };
}

/** Reads the gateway's import-dictionary response JSON defensively. */
export function readImportedDictionary(
  value: unknown,
): { artifactId: string; displayName: string; entryCount: number } {
  const dictionary = asRecord(value);
  if (typeof dictionary.artifactId !== "string" || !dictionary.artifactId) {
    throw new Error("The server returned an invalid dictionary descriptor.");
  }
  return {
    artifactId: dictionary.artifactId,
    displayName: typeof dictionary.displayName === "string" && dictionary.displayName
      ? dictionary.displayName : dictionary.artifactId,
    entryCount: asCount(dictionary.entryCount),
  };
}

/** Reads the gateway's learn-vocabulary response JSON defensively. */
export function readLearnedVocabulary(value: unknown): {
  artifactId: string; displayName: string; termCount: number;
  dictionaryTermCount: number; corpusTermCount: number;
} {
  const vocabulary = asRecord(value);
  if (typeof vocabulary.artifactId !== "string" || !vocabulary.artifactId) {
    throw new Error("The server returned an invalid vocabulary descriptor.");
  }
  return {
    artifactId: vocabulary.artifactId,
    displayName: typeof vocabulary.displayName === "string" && vocabulary.displayName
      ? vocabulary.displayName : vocabulary.artifactId,
    termCount: asCount(vocabulary.termCount),
    dictionaryTermCount: asCount(vocabulary.dictionaryTermCount),
    corpusTermCount: asCount(vocabulary.corpusTermCount),
  };
}

/** Reads the gateway's static-models JSON defensively. */
export function readStaticModels(value: unknown): TrainedModelSummary[] {
  return asArray(asRecord(value).models).map(readTrainedModel);
}

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null ? value as Record<string, unknown> : {};
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function asCount(value: unknown): number {
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;
}

function asRatio(value: unknown): number {
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isFinite(parsed) && parsed >= 0 && parsed <= 1 ? parsed : 0;
}

/** Splits pasted corpus text into documents on blank lines. */
export function corpusDocuments(text: string): Array<{ docId: string; rawText: string }> {
  return splitBlankLineDocuments(text).map((rawText, index) => (
    { docId: `trainer-doc-${index + 1}`, rawText }));
}

/** Computes the client-visible corpus totals used before server submission. */
export function corpusStats(text: string): {
  documents: number; codePoints: number; utf8Bytes: number;
} {
  let codePoints = 0;
  for (const ignored of text) {
    void ignored;
    codePoints++;
  }
  return {
    documents: corpusDocuments(text).length,
    codePoints,
    utf8Bytes: new TextEncoder().encode(text).byteLength,
  };
}



/** Encodes bytes as base64 for the protobuf JSON bytes field. */
export function base64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  const chunk = 0x8000;
  for (let offset = 0; offset < bytes.length; offset += chunk) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + chunk));
  }
  return btoa(binary);
}

function formatSelector(value: string): ImportDictionaryUpload["start"]["format"] {
  return value.startsWith("STANDARD_DICTIONARY_FORMAT_")
    ? { standard: value } : { custom: value };
}

function boundedInt(value: string, fallback: number): number {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

function populate(
  select: HTMLSelectElement,
  options: Array<{ value: string; label: string; title?: string }>,
  emptyLabel: string,
): void {
  select.replaceChildren();
  if (options.length === 0) {
    select.add(new Option(emptyLabel, ""));
    select.disabled = true;
    return;
  }
  for (const option of options) {
    const element = new Option(option.label, option.value);
    if (option.title) {
      element.title = option.title;
    }
    select.add(element);
  }
  select.disabled = false;
}

function addOption(select: HTMLSelectElement, value: string, label: string): void {
  if (select.options.length === 1 && !select.options[0]?.value) {
    select.replaceChildren();
  }
  select.add(new Option(label, value));
  select.value = value;
  select.disabled = false;
}

/** Copies text to the clipboard, reporting the outcome transiently on the pressed button. */
async function copyText(button: HTMLButtonElement, text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    flashButtonLabel(button, "Copied");
  } catch {
    flashButtonLabel(button, "Copy failed");
  }
}

function saveTextFile(name: string, text: string): void {
  const url = URL.createObjectURL(new Blob([text], { type: "text/tab-separated-values" }));
  try {
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = name;
    anchor.click();
  } finally {
    URL.revokeObjectURL(url);
  }
}

function message(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}
