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
import { requiredElement } from "./ui-utils";

const CARRIAGE_RETURN = "\r";
const LINE_FEED = "\n";

export interface DictionaryFormatOption {
  id: string;
  label: string;
  custom: boolean;
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
}

export interface TrainerApi {
  listDictionaryFormats(): Promise<{ formats: DictionaryFormatOption[]; writesEnabled: boolean }>;
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
  #busy = false;

  constructor(api: TrainerApi, callbacks: TrainerCallbacks) {
    this.#api = api;
    this.#callbacks = callbacks;
    this.#importButton.addEventListener("click", () => void this.importDictionary());
    this.#learnButton.addEventListener("click", () => void this.learnVocabulary());
    this.#downloadTsvButton.addEventListener("click", () => void this.downloadTsv());
    this.#trainButton.addEventListener("click", () => void this.train());
  }

  /** Loads formats, teachers, and existing models; call once at startup. */
  async initialize(): Promise<void> {
    try {
      const [formats, teachers, models] = await Promise.all([
        this.#api.listDictionaryFormats(),
        this.#api.listTeachers(),
        this.#api.listStaticModels(),
      ]);
      this.#writesEnabled = formats.writesEnabled && teachers.writesEnabled;
      populate(this.#formatSelect, formats.formats.map((format) => ({
        value: format.id,
        label: format.label,
      })), "No formats available");
      populate(this.#teacherSelect, teachers.teachers.map((teacher) => ({
        value: teacher.id,
        label: `${teacher.label} (${teacher.reference})`,
      })), "No teachers configured");
      this.renderModels(models);
      if (!this.#writesEnabled) {
        this.setStatus(
          "Training is disabled: the server has no vocabulary artifact root or no teachers.",
          true,
        );
      } else if (teachers.teachers.length === 0) {
        this.setStatus("No teachers are configured; add training.teacher entries.", true);
      } else {
        this.setStatus("Import a dictionary to start the vocabulary-to-model flow.");
      }
      this.updateControls();
    } catch (error) {
      this.setStatus(message(error, "Could not load the trainer catalog."), true);
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
    if (!dictionaryArtifactId) {
      this.setStatus("Import and select a dictionary first.", true);
      return;
    }
    const documents = corpusDocuments(this.#corpus.value);
    if (documents.length === 0) {
      this.setStatus("Paste at least one corpus document (blank lines separate documents).", true);
      return;
    }
    const displayName = this.#vocabularyName.value.trim() || "Trainer vocabulary";
    await this.run("The server is learning the vocabulary.", async () => {
      const vocabulary = await this.#api.learnVocabulary({
        start: {
          dictionaryArtifactId,
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
    const displayName = this.#modelName.value.trim() || "Trainer static model";
    this.#progressLog.replaceChildren();
    await this.run("The server is distilling the static model.", async () => {
      const model = await this.#api.trainStaticModel({
        vocabularyArtifactId,
        teacherId,
        displayName,
        pcaDims: boundedInt(this.#pcaDims.value, 0),
        provenanceSummary: "Distilled through the trainer workbench",
      }, (progress) => this.appendProgress(progress));
      this.appendProgress(`Published ${model.artifactId} (dimension ${model.dimension}).`);
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
      const label = document.createElement("span");
      label.textContent = `${model.displayName} · ${model.artifactId} `
        + `· dim ${model.dimension} · ${model.termCount} terms · teacher ${model.teacherId}`;
      const remove = document.createElement("button");
      remove.type = "button";
      remove.textContent = "Delete";
      remove.addEventListener("click", () => void this.deleteModel(model.artifactId));
      row.append(label, remove);
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
    this.#downloadTsvButton.disabled = !enabled;
    this.#trainButton.disabled = !enabled;
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

/** Splits pasted corpus text into documents on blank lines. */
export function corpusDocuments(text: string): Array<{ docId: string; rawText: string }> {
  const documents: Array<{ docId: string; rawText: string }> = [];
  for (const block of corpusBlocks(text)) {
    const rawText = block.trim();
    if (rawText) {
      documents.push({ docId: `trainer-doc-${documents.length + 1}`, rawText });
    }
  }
  return documents;
}

function corpusBlocks(text: string): string[] {
  const blocks: string[] = [];
  let block = "";
  let cursor = 0;
  while (cursor <= text.length) {
    const start = cursor;
    while (cursor < text.length && text.charAt(cursor) !== LINE_FEED
        && text.charAt(cursor) !== CARRIAGE_RETURN) {
      cursor++;
    }
    const line = text.slice(start, cursor);
    if (isBlankCorpusLine(line)) {
      if (block) {
        blocks.push(block);
        block = "";
      }
    } else {
      block += block ? `\n${line}` : line;
    }
    if (cursor >= text.length) {
      break;
    }
    if (text.charAt(cursor) === CARRIAGE_RETURN && text.charAt(cursor + 1) === LINE_FEED) {
      cursor++;
    }
    cursor++;
  }
  if (block) {
    blocks.push(block);
  }
  return blocks;
}

function isBlankCorpusLine(line: string): boolean {
  for (const character of line) {
    if (character !== " " && character !== "\t") {
      return false;
    }
  }
  return true;
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
  options: Array<{ value: string; label: string }>,
  emptyLabel: string,
): void {
  select.replaceChildren();
  if (options.length === 0) {
    select.add(new Option(emptyLabel, ""));
    select.disabled = true;
    return;
  }
  for (const option of options) {
    select.add(new Option(option.label, option.value));
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
