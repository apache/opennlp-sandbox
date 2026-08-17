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

import { describe, expect, it } from "vitest";

import { readDocumentShape } from "../src/document-shape";
import {
  cosineSimilarity,
  readEmbeddingVectors,
  representativeVectors,
  SessionVectorIndex,
} from "../src/embedding-workbench";

function embeddedDocument(text: string, documentVector: number[], sentenceVector: number[]) {
  return readDocumentShape({
    document: {
      rawText: text,
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      layers: {
        layers: [
          {
            id: "opennlp:document-embeddings",
            scope: "LAYER_SCOPE_DOCUMENT",
            embeddingValues: {
              annotations: [{ modelId: "tiny-test", granularity: "DOCUMENT", vector: documentVector }],
            },
          },
          {
            id: "opennlp:sentence-embeddings",
            scope: "LAYER_SCOPE_POSITIONAL",
            embeddingValues: {
              annotations: [{
                span: { start: 0, end: text.length },
                modelId: "tiny-test",
                granularity: "SENTENCE",
                vector: sentenceVector,
              }],
            },
          },
        ],
      },
    },
  });
}

describe("embedding workbench", () => {
  it("reads valid vectors only from typed document-shape embedding layers", () => {
    const shape = embeddedDocument("A short sentence.", [1, 0], [0.8, 0.2]);
    shape.layers[1]?.annotations.push({
      label: "invalid",
      source: { modelId: "tiny-test", vector: [1, Number.NaN] },
    });

    expect(readEmbeddingVectors(shape)).toMatchObject([
      { modelId: "tiny-test", granularity: "DOCUMENT", vector: [1, 0] },
      { modelId: "tiny-test", granularity: "SENTENCE", vector: [0.8, 0.2], start: 0, end: 17 },
    ]);
  });

  it("computes cosine similarity defensively", () => {
    expect(cosineSimilarity([1, 0], [1, 0])).toBeCloseTo(1);
    expect(cosineSimilarity([1, 0], [0, 1])).toBeCloseTo(0);
    expect(cosineSimilarity([1], [1, 0])).toBeUndefined();
    expect(cosineSimilarity([0, 0], [1, 0])).toBeUndefined();
  });

  it("recognizes lowercase ASCII document granularity", () => {
    const shape = embeddedDocument("A short sentence.", [1, 0], [0.8, 0.2]);
    shape.layers[0]!.annotations[0]!.source.granularity = "document";

    expect(representativeVectors(shape)).toMatchObject([{ granularity: "document", vector: [1, 0] }]);
  });

  it("ranks session documents with matching model vectors", () => {
    const index = new SessionVectorIndex();
    expect(index.add("doc-a", "Apache text", embeddedDocument("Apache text", [1, 0], [1, 0]))).toBe(true);
    expect(index.add("doc-b", "Weather text", embeddedDocument("Weather text", [0, 1], [0, 1]))).toBe(true);

    const hits = index.search(embeddedDocument("Apache", [0.9, 0.1], [0.9, 0.1]));

    expect(index.size).toBe(2);
    expect(hits.map((hit) => hit.document.id)).toEqual(["doc-a", "doc-b"]);
    expect(hits[0]?.modelId).toBe("tiny-test");
    expect(hits[0]?.score).toBeGreaterThan(hits[1]?.score ?? 1);
  });

  it("breaks equal-score ties by locale-independent document id order", () => {
    const index = new SessionVectorIndex();
    index.add("ä", "Unicode", embeddedDocument("Unicode", [1, 0], [1, 0]));
    index.add("z", "ASCII", embeddedDocument("ASCII", [1, 0], [1, 0]));

    expect(index.search(embeddedDocument("Query", [1, 0], [1, 0])).map((hit) => hit.document.id))
      .toEqual(["z", "ä"]);
  });

  it("does not index an all-zero embedding", () => {
    const index = new SessionVectorIndex();

    expect(index.add("zero", "Zero vector", embeddedDocument("Zero", [0, 0], [0, 0]))).toBe(false);
    expect(index.size).toBe(0);
  });
});
