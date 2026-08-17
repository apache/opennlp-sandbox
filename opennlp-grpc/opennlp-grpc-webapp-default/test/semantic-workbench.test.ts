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
 * language governing permissions and limitations
 * under the License.
 */

/** @vitest-environment jsdom */

import { beforeEach, describe, expect, it } from "vitest";

import { readDocumentShape } from "../src/document-shape";
import { SemanticWorkbench } from "../src/semantic-workbench";

function mountWorkbenchDom(): void {
  document.body.innerHTML = `
    <button id="add-to-index-button"></button>
    <button id="clear-index-button"></button>
    <form id="semantic-search-form"></form>
    <textarea id="semantic-query"></textarea>
    <button id="search-button"></button>
    <span id="index-count"></span>
    <p id="semantic-status"></p>
    <div id="search-results"></div>
    <div id="semantic-heatmap"></div>
    <div id="sentiment-heatmap"></div>
    <div id="document-graph"></div>
    <p id="graph-selection"></p>`;
}

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

function createWorkbench(): SemanticWorkbench {
  return new SemanticWorkbench({
    analyzeQuery: async () => embeddedDocument("Query", [1, 0], [1, 0]),
    openDocument: () => undefined,
    selectAnnotation: () => undefined,
  });
}

describe("semantic workbench heatmap", () => {
  beforeEach(() => {
    mountWorkbenchDom();
  });

  it("populates the semantic heatmap from the document's own embeddings on setDocument", () => {
    const workbench = createWorkbench();

    workbench.setDocument("Doc", embeddedDocument("A short sentence.", [1, 0], [0.8, 0.2]));

    const rows = workbench.heatmapRows().semantic;
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({ start: 0, end: 17, modelId: "tiny-test" });
    // Each positional embedding is scored against the document's representative vector.
    expect(rows[0]!.score).toBeCloseTo(0.8 / Math.hypot(0.8, 0.2), 6);
  });

  it("keeps the semantic heatmap empty when the document carries no embeddings", () => {
    const workbench = createWorkbench();

    workbench.setDocument("Doc", readDocumentShape({
      document: { rawText: "Plain text.", offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT" },
    }));

    expect(workbench.heatmapRows().semantic).toEqual([]);
  });
});
