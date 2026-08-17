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
import { buildDocumentGraph, buildHeatmapRows } from "../src/visualization-data";

const shape = readDocumentShape({
  document: {
    rawText: "OpenNLP works. Search is useful.",
    offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
    layers: {
      layers: [
        {
          id: "opennlp:sentence-embeddings",
          embeddingValues: {
            annotations: [
              { span: { start: 0, end: 14 }, modelId: "tiny-test", vector: [1, 0] },
              { span: { start: 15, end: 32 }, modelId: "tiny-test", vector: [0, 1] },
            ],
          },
        },
        {
          id: "opennlp:sentiment",
          categoryValues: {
            annotations: [
              { span: { start: 0, end: 14 }, label: "positive", score: 0.8 },
              { span: { start: 15, end: 32 }, label: "neutral", score: 0.1 },
            ],
          },
        },
      ],
    },
  },
});

describe("visualization data", () => {
  it("leaves semantic ranking to the server and reads typed sentiment rows", () => {
    const rows = buildHeatmapRows(shape);

    expect(rows.semantic).toEqual([]);
    expect(rows.sentiment).toMatchObject([
      { start: 0, end: 14, score: 0.8, category: "positive" },
      { start: 15, end: 32, score: 0.1, category: "neutral" },
    ]);
  });

  it("builds a bounded graph that preserves layer and annotation references", () => {
    const graph = buildDocumentGraph(shape, 3);

    expect(graph.nodes[0]).toMatchObject({ id: "document", kind: "document" });
    expect(graph.nodes.filter((node) => node.kind === "layer")).toHaveLength(2);
    expect(graph.nodes.filter((node) => node.kind === "annotation")).toHaveLength(3);
    expect(graph.links).toContainEqual({ source: "document", target: "layer:opennlp:sentiment" });
    expect(graph.truncated).toBe(true);
  });
});
