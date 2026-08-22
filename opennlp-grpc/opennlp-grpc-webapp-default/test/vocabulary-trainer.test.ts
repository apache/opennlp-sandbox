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
      artifactHash: "abc", byteSize: "31000000",
    }] })).toEqual([{
      artifactId: "static-model-1", displayName: "m", dimension: 3, termCount: 12, teacherId: "mini",
      family: "wordpiece", vocabularySize: 30_522, explainedVarianceRatio: 0.97,
      artifactHash: "abc", byteSize: 31_000_000,
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
    const trainer = new VocabularyTrainerWorkbench(stubApi(), { onModelsChanged });

    await trainer.initialize();

    const teacherSelect = document.getElementById("trainer-teacher-select") as HTMLSelectElement;
    expect(teacherSelect.options[0]?.value).toBe("mini");
    expect(onModelsChanged).toHaveBeenCalledWith([MODEL]);
    expect(document.getElementById("trainer-model-list")?.textContent).toContain("static-model-1");
    expect((document.getElementById("trainer-import-button") as HTMLButtonElement).disabled)
      .toBe(false);
  });

  it("shows a waiting state and live corpus document and byte counts", async () => {
    const trainer = new VocabularyTrainerWorkbench(stubApi(), { onModelsChanged: vi.fn() });
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

  it("disables training when the server has no artifact root", async () => {
    const api = stubApi({
      listDictionaryFormats: vi.fn(async () => ({ formats: [], writesEnabled: false })),
      listTeachers: vi.fn(async () => ({ teachers: [], writesEnabled: false })),
      listStaticModels: vi.fn(async () => []),
    });
    const trainer = new VocabularyTrainerWorkbench(api, { onModelsChanged: vi.fn() });

    await trainer.initialize();

    expect(document.getElementById("trainer-status")?.textContent).toContain("disabled");
    expect((document.getElementById("trainer-train-button") as HTMLButtonElement).disabled)
      .toBe(true);
  });

  it("learns a vocabulary and trains a model with streamed progress", async () => {
    const api = stubApi();
    const onModelsChanged = vi.fn();
    const trainer = new VocabularyTrainerWorkbench(api, { onModelsChanged });
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

  it("surfaces training failures in the status line", async () => {
    const api = stubApi({
      trainStaticModel: vi.fn(async () => {
        throw new Error("Unknown teacher 'other'");
      }),
    });
    const trainer = new VocabularyTrainerWorkbench(api, { onModelsChanged: vi.fn() });
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
});
