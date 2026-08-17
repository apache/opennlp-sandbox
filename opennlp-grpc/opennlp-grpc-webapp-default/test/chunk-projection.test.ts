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

import { readChunkProjection } from "../src/chunk-projection";

describe("chunk projection", () => {
  it("keeps sentence and token strategies as distinct comparable groups", () => {
    const projection = readChunkProjection({
      document: {
        chunkEmbeddingGroups: [
          {
            groupId: "sentence-chunks",
            resultSetName: "Sentence chunks",
            strategy: { standard: "STANDARD_CHUNKING_STRATEGY_SENTENCE" },
            embeddingModelIds: ["legal-mini"],
            chunks: [
              {
                annotationSpan: { start: 0, end: 14 },
                textContent: "We the People.",
                embeddings: [{ route: { modelId: "legal-mini" } }],
              },
            ],
          },
          {
            groupId: "token-chunks",
            resultSetName: "Token windows",
            strategy: { standard: "STANDARD_CHUNKING_STRATEGY_TOKEN" },
            chunks: [
              {
                annotationSpan: { start: 0, end: 29 },
                textContent: "We the People of the United",
                embeddings: [],
              },
            ],
          },
        ],
      },
    });

    expect(projection).toEqual([
      {
        id: "sentence-chunks",
        title: "Sentence chunks",
        strategy: "Sentence",
        embeddingModelIds: ["legal-mini"],
        chunks: [{ index: 1, start: 0, end: 14, text: "We the People.", embeddingCount: 1 }],
      },
      {
        id: "token-chunks",
        title: "Token windows",
        strategy: "Token window",
        embeddingModelIds: [],
        chunks: [{ index: 1, start: 0, end: 29, text: "We the People of the United", embeddingCount: 0 }],
      },
    ]);
  });

  it("ignores malformed groups and preserves empty valid groups", () => {
    expect(readChunkProjection({
      document: {
        chunkEmbeddingGroups: [null, {}, {
          groupId: "sentence-chunks",
          strategy: { standard: "STANDARD_CHUNKING_STRATEGY_SENTENCE" },
          chunks: [],
        }],
      },
    })).toEqual([{
      id: "sentence-chunks",
      title: "sentence-chunks",
      strategy: "Sentence",
      embeddingModelIds: [],
      chunks: [],
    }]);
  });
});
