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

/** @vitest-environment jsdom */

import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  base64,
  corpusDocuments,
  readDictionaryFormats,
  readImportedDictionary,
  readLearnedVocabulary,
  readStaticModels,
  readTeachers,
  readTrainedModel,
  VocabularyTrainerWorkbench,
  readVocabularies,
  vocabularyOptionLabels,
  type TrainedModelSummary,
  type TrainerApi,
} from "../src/vocabulary-trainer";

const MODEL: TrainedModelSummary = {
  artifactId: "static-model-1",
  displayName: "Legal static model",
  dimension: 3,
  termCount: 12,
  teacherId: "mini",
  family: "wordpiece",
  vocabularySize: 30_522,
  explainedVarianceRatio: 0.97,
  artifactHash: "abc",
  byteSize: 31_000_000,
  createdAt: "2026-08-20T14:00:00Z",
  teacherReference: "minishlab/potion-base-8M",
  licenseName: "MIT",
  languages: ["en"],
};

describe("trainer readers", () => {
  it("reads formats, teachers, and models defensively", () => {
    const formats = readDictionaryFormats({
      formats: [
        { format: { standard: "STANDARD_DICTIONARY_FORMAT_HEADWORD_LINES" }, displayName: "Headword lines" },
        { format: { custom: "my-format" } },
        { format: {} },
      ],
      writesEnabled: true,
    });
    expect(formats.writesEnabled).toBe(true);
    expect(formats.formats).toEqual([
      { id: "STANDARD_DICTIONARY_FORMAT_HEADWORD_LINES", label: "Headword lines", custom: false },
      { id: "my-format", label: "my-format", custom: true },
    ]);

    const teachers = readTeachers({
      teachers: [{ teacherId: "mini", displayName: "Mini", reference: "org/mini" }],
      writesEnabled: false,
    });
    expect(teachers.writesEnabled).toBe(false);
    expect(teachers.teachers).toEqual([{ id: "mini", label: "Mini", reference: "org/mini" }]);

    expect(readStaticModels({ models: [{
      artifactId: "static-model-1", displayName: "m", dimension: 3, termCount: "12", teacherId: "mini",
      family: "wordpiece", vocabularySize: 30_522, explainedVarianceRatio: 0.97,
      artifactHash: "abc", byteSize: "31000000", createdAt: "2026-08-20T14:00:00Z",
    }] })).toEqual([{
      artifactId: "static-model-1", displayName: "m", dimension: 3, termCount: 12, teacherId: "mini",
      family: "wordpiece", vocabularySize: 30_522, explainedVarianceRatio: 0.97,
      artifactHash: "abc", byteSize: 31_000_000, createdAt: "2026-08-20T14:00:00Z",
      teacherReference: "", licenseName: "", languages: [],
    }]);
    expect(() => readTrainedModel({})).toThrow(/invalid static model/);
    expect(readImportedDictionary({ artifactId: "dictionary-1", entryCount: 2 }))
      .toEqual({ artifactId: "dictionary-1", displayName: "dictionary-1", entryCount: 2 });
    expect(() => readLearnedVocabulary({})).toThrow(/invalid vocabulary/);
  });

  it("splits corpus text into blank-line-separated documents", () => {
    expect(corpusDocuments("First doc.\nstill first.\n\nSecond doc.\n\n\n")).toEqual([
      { docId: "trainer-doc-1", rawText: "First doc.\nstill first." },
      { docId: "trainer-doc-2", rawText: "Second doc." },
    ]);
    expect(corpusDocuments("   \n\n")).toEqual([]);
  });

  it("encodes bytes as protobuf JSON base64", () => {
    expect(base64(new TextEncoder().encode("headword\tdefinition\n").buffer as ArrayBuffer))
      .toBe(btoa("headword\tdefinition\n"));
  });
});

describe("trainer workbench", () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <p id="trainer-status"></p>
      <select id="trainer-format-select"></select>
      <input id="trainer-dictionary-name" />
      <input id="trainer-dictionary-file" type="file" />
      <button id="trainer-import-button"></button>
      <select id="trainer-dictionary-select"></select>
      <textarea id="trainer-corpus"></textarea>
      <p id="trainer-corpus-stats"></p>
      <input id="trainer-vocabulary-name" />
      <input id="trainer-min-frequency" value="1" />
      <input id="trainer-max-terms" value="100" />
      <button id="trainer-learn-button"></button>
      <select id="trainer-vocabulary-select"></select>
      <button id="trainer-download-tsv-button"></button>
      <select id="trainer-teacher-select"></select>
      <input id="trainer-model-name" />
      <input id="trainer-pca-dims" value="0" />
      <button id="trainer-train-button"></button>
      <div id="trainer-progress-log"></div>
      <div id="trainer-model-list"></div>`;
  });

  function stubApi(overrides: Partial<TrainerApi> = {}): TrainerApi {
    return {
      listDictionaryFormats: vi.fn(async () => ({
        formats: [{ id: "STANDARD_DICTIONARY_FORMAT_HEADWORD_LINES", label: "Headword lines", custom: false }],
        writesEnabled: true,
      })),
      importDictionary: vi.fn(async () => ({
        artifactId: "dictionary-1", displayName: "Legal dictionary", entryCount: 2,
      })),
      learnVocabulary: vi.fn(async () => ({
        artifactId: "vocabulary-1", displayName: "Legal vocabulary",
        termCount: 5, dictionaryTermCount: 2, corpusTermCount: 3,
      })),
      downloadVocabulary: vi.fn(async () => "liberty\t3\tcorpus\n"),
      listDictionaries: vi.fn(async () => []),
      listVocabularies: vi.fn(async () => []),
      listTeachers: vi.fn(async () => ({
        teachers: [{ id: "mini", label: "Mini", reference: "org/mini" }],
        writesEnabled: true,
      })),
      trainStaticModel: vi.fn(async (_request, onProgress) => {
        onProgress("resolving teacher");
        onProgress("distilling");
        return MODEL;
      }),
      listStaticModels: vi.fn(async () => [MODEL]),
      deleteStaticModel: vi.fn(async () => true),
      ...overrides,
    };
  }

  it("initializes the catalog and reports existing models", async () => {
    const onModelsChanged = vi.fn();
    const trainer = new VocabularyTrainerWorkbench(stubApi(), { onModelsChanged, onUseInAnalyze: vi.fn() });

    await trainer.initialize();

    const teacherSelect = document.getElementById("trainer-teacher-select") as HTMLSelectElement;
    expect(teacherSelect.options[0]?.value).toBe("mini");
    expect(onModelsChanged).toHaveBeenCalledWith([MODEL]);
    expect(document.getElementById("trainer-model-list")?.textContent).toContain("static-model-1");
    expect((document.getElementById("trainer-import-button") as HTMLButtonElement).disabled)
      .toBe(false);
  });

  it("names teachers without their filesystem reference, kept as a tooltip", async () => {
    const trainer = new VocabularyTrainerWorkbench(stubApi(), {
      onModelsChanged: vi.fn(), onUseInAnalyze: vi.fn(),
    });
    await trainer.initialize();

    const option = (document.getElementById("trainer-teacher-select") as HTMLSelectElement)
      .options[0]!;
    expect(option.textContent).toBe("Mini");
    expect(option.title).toBe("org/mini");
  });

  it("renders trained models with their name, training time, and a Use in Analyze action", async () => {
    const onUseInAnalyze = vi.fn();
    const trainer = new VocabularyTrainerWorkbench(stubApi(), {
      onModelsChanged: vi.fn(), onUseInAnalyze,
    });
    await trainer.initialize();

    const row = document.querySelector(".trainer-model-row")!;
    expect(row.querySelector("strong")?.textContent).toBe("Legal static model");
    expect(row.textContent).toContain("distilled 2026-08-20 14:00 UTC");
    expect(row.textContent).toContain("· MIT · en");
    expect(row.querySelector("code")?.textContent).toBe("static-model-1");
    const use = [...row.querySelectorAll("button")]
      .find((button) => button.textContent === "Use in Analyze")!;
    use.click();
    expect(onUseInAnalyze).toHaveBeenCalledWith(MODEL);
  });

  it("shows a waiting state and live corpus document and byte counts", async () => {
    const trainer = new VocabularyTrainerWorkbench(stubApi(), { onModelsChanged: vi.fn(), onUseInAnalyze: vi.fn() });
    await trainer.initialize();
    const corpus = document.getElementById("trainer-corpus") as HTMLTextAreaElement;

    expect(document.getElementById("trainer-corpus-stats")?.textContent)
      .toContain("Waiting for corpus input");
    corpus.value = "Liberty.\n\n😀 justice.";
    corpus.dispatchEvent(new Event("input"));

    expect(document.getElementById("trainer-corpus-stats")?.textContent)
      .toContain("2 documents");
    expect(document.getElementById("trainer-corpus-stats")?.textContent)
      .toContain("23 UTF-8 bytes");
  });

  it("disables distilling and points at the catalog when no teacher is installed", async () => {
    const api = stubApi({
      listDictionaryFormats: vi.fn(async () => ({ formats: [], writesEnabled: true })),
      listTeachers: vi.fn(async () => ({ teachers: [], writesEnabled: true })),
      listStaticModels: vi.fn(async () => []),
    });
    const trainer = new VocabularyTrainerWorkbench(api, { onModelsChanged: vi.fn(), onUseInAnalyze: vi.fn() });

    await trainer.initialize();

    const status = document.getElementById("trainer-status")!;
    expect(status.textContent).toContain("No teacher model is installed");
    expect(status.querySelector<HTMLElement>("[data-workbench-jump]")?.dataset.workbenchJump).toBe("models");
    expect((document.getElementById("trainer-train-button") as HTMLButtonElement).disabled).toBe(true);
    expect((document.getElementById("trainer-learn-button") as HTMLButtonElement).disabled).toBe(false);
  });

  it("disables training when the server has no artifact root", async () => {
    const api = stubApi({
      listDictionaryFormats: vi.fn(async () => ({ formats: [], writesEnabled: false })),
      listTeachers: vi.fn(async () => ({ teachers: [], writesEnabled: false })),
      listStaticModels: vi.fn(async () => []),
    });
    const trainer = new VocabularyTrainerWorkbench(api, { onModelsChanged: vi.fn(), onUseInAnalyze: vi.fn() });

    await trainer.initialize();

    expect(document.getElementById("trainer-status")?.textContent).toContain("artifact root");
    expect((document.getElementById("trainer-train-button") as HTMLButtonElement).disabled)
      .toBe(true);
  });

  it("learns a vocabulary and trains a model with streamed progress", async () => {
    const api = stubApi();
    const onModelsChanged = vi.fn();
    const trainer = new VocabularyTrainerWorkbench(api, { onModelsChanged, onUseInAnalyze: vi.fn() });
    await trainer.initialize();

    const dictionarySelect =
      document.getElementById("trainer-dictionary-select") as HTMLSelectElement;
    dictionarySelect.add(new Option("Legal dictionary", "dictionary-1"));
    dictionarySelect.value = "dictionary-1";
    (document.getElementById("trainer-corpus") as HTMLTextAreaElement).value =
      "Liberty matters.\n\nHabeas corpus endures.";
    (document.getElementById("trainer-learn-button") as HTMLButtonElement).click();
    await vi.waitFor(() => {
      expect(document.getElementById("trainer-status")?.textContent).toContain("Learned 5 terms");
    });
    const learnUpload = vi.mocked(api.learnVocabulary).mock.calls[0]?.[0];
    expect(learnUpload?.documents).toHaveLength(2);
    expect(learnUpload?.start.dictionaryArtifactId).toBe("dictionary-1");

    (document.getElementById("trainer-train-button") as HTMLButtonElement).click();
    await vi.waitFor(() => {
      expect(document.getElementById("trainer-status")?.textContent).toContain("static-model-1");
    });
    const log = document.getElementById("trainer-progress-log");
    expect(log?.textContent).toContain("resolving teacher");
    expect(log?.textContent).toContain("distilling");
    expect(log?.textContent).toContain("30,522 tokenizer rows");
    expect(log?.textContent).toContain("97.0% variance retained");
    expect(onModelsChanged).toHaveBeenLastCalledWith([MODEL]);
  });

  it("learns directly from the corpus when no optional dictionary is selected", async () => {
    const api = stubApi();
    const trainer = new VocabularyTrainerWorkbench(api, {
      onModelsChanged: vi.fn(), onUseInAnalyze: vi.fn(),
    });
    await trainer.initialize();
    (document.getElementById("trainer-corpus") as HTMLTextAreaElement).value =
      "Liberty matters.\n\nHabeas corpus endures.";

    (document.getElementById("trainer-learn-button") as HTMLButtonElement).click();

    await vi.waitFor(() => expect(api.learnVocabulary).toHaveBeenCalled());
    const start = vi.mocked(api.learnVocabulary).mock.calls[0]?.[0].start;
    expect(start?.dictionaryArtifactId).toBeUndefined();
    expect(document.getElementById("trainer-status")?.textContent).toContain("Learned 5 terms");
  });

  it("restores the Copy id label after confirming the copy", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText },
    });
    const trainer = new VocabularyTrainerWorkbench(stubApi(), {
      onModelsChanged: vi.fn(), onUseInAnalyze: vi.fn(),
    });
    await trainer.initialize();
    const copy = [...document.querySelectorAll(".trainer-model-row button")]
      .find((button) => button.textContent === "Copy id") as HTMLButtonElement;
    vi.useFakeTimers();
    try {
      copy.click();
      await Promise.resolve();
      await Promise.resolve();

      expect(writeText).toHaveBeenCalledWith("static-model-1");
      expect(copy.textContent).toBe("Copied");
      vi.advanceTimersByTime(1500);
      expect(copy.textContent).toBe("Copy id");
    } finally {
      vi.useRealTimers();
    }
  });

  it("disables the TSV export with a reason until a vocabulary is selected", async () => {
    const trainer = new VocabularyTrainerWorkbench(stubApi(), {
      onModelsChanged: vi.fn(), onUseInAnalyze: vi.fn(),
    });
    await trainer.initialize();
    const button = document.getElementById("trainer-download-tsv-button") as HTMLButtonElement;

    expect(button.disabled).toBe(true);
    expect(button.title).toContain("vocabulary");

    const vocabularySelect =
      document.getElementById("trainer-vocabulary-select") as HTMLSelectElement;
    vocabularySelect.add(new Option("Legal vocabulary", "vocabulary-1"));
    vocabularySelect.value = "vocabulary-1";
    vocabularySelect.dispatchEvent(new Event("change"));

    expect(button.disabled).toBe(false);
    expect(button.title).toBe("");
  });

  it("surfaces training failures in the status line", async () => {
    const api = stubApi({
      trainStaticModel: vi.fn(async () => {
        throw new Error("Unknown teacher 'other'");
      }),
    });
    const trainer = new VocabularyTrainerWorkbench(api, { onModelsChanged: vi.fn(), onUseInAnalyze: vi.fn() });
    await trainer.initialize();
    const vocabularySelect =
      document.getElementById("trainer-vocabulary-select") as HTMLSelectElement;
    vocabularySelect.add(new Option("Legal vocabulary", "vocabulary-1"));
    vocabularySelect.value = "vocabulary-1";

    (document.getElementById("trainer-train-button") as HTMLButtonElement).click();

    await vi.waitFor(() => {
      expect(document.getElementById("trainer-status")?.textContent)
        .toContain("Unknown teacher 'other'");
    });
  });
  it("lists the dictionaries and vocabularies already on the server at startup", async () => {
    const workbench = new VocabularyTrainerWorkbench(stubApi({
      listDictionaries: vi.fn(async () => [
        { artifactId: "dictionary-legal", displayName: "Legal dictionary", entryCount: 80 },
      ]),
      listVocabularies: vi.fn(async () => [
        { artifactId: "vocabulary-legal", displayName: "Legal vocabulary", termCount: 4812 },
      ]),
    }), { onModelsChanged: vi.fn(), onUseInAnalyze: vi.fn() });
    await workbench.initialize();

    const dictionaries = document.getElementById("trainer-dictionary-select") as HTMLSelectElement;
    expect(Array.from(dictionaries.options).map((option) => option.textContent))
      .toEqual(["Corpus terms only", "Legal dictionary (80 entries)"]);
    const vocabularies = document.getElementById("trainer-vocabulary-select") as HTMLSelectElement;
    expect(Array.from(vocabularies.options).map((option) => option.textContent))
      .toEqual(["Legal vocabulary (4812 terms) · legal"]);
    expect(vocabularies.disabled).toBe(false);
    // A vocabulary learned on an earlier run is exportable without learning it again.
    expect((document.getElementById("trainer-download-tsv-button") as HTMLButtonElement).disabled)
      .toBe(false);
  });
});

describe("vocabulary picker labels", () => {
  it("tells duplicate names apart by their artifact id, uses singular for one term, and lists the newest first", () => {
    const vocabularies = readVocabularies({
      vocabularies: [
        { artifactId: "vocabulary-older-000", displayName: "Same name", termCount: 1,
          createdAt: "2026-01-01T00:00:00Z" },
        { artifactId: "vocabulary-newer-111", displayName: "Same name", termCount: 12,
          createdAt: "2026-02-01T00:00:00Z" },
      ],
    });
    expect(vocabularyOptionLabels(vocabularies)).toEqual([
      "Same name (12 terms) · newer-111",
      "Same name (1 term) · older-000",
    ]);
  });
});
